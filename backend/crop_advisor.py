"""Crop Advisor analysis migrated from the Colab notebook for persistent API hosting."""
import hmac
import json
import math
import os
import re
import warnings
from pathlib import Path
from urllib.parse import quote

import numpy as np
import pandas as pd
from scipy.stats import spearmanr
from statsmodels.tsa.holtwinters import ExponentialSmoothing

try:
    import ee
except ImportError:
    ee = None

from fastapi import Header, HTTPException, Query

# ---------------------------------------------------------------- settings
DATA_DIR   = Path(__file__).with_name("data")
required_data = ["crop_requirements_clean.csv", "sri_lanka_crop_profit_with_estimates.csv"]
missing_data = [name for name in required_data if not (DATA_DIR / name).is_file()]
if missing_data or not list(DATA_DIR.glob("crop-economics*.csv")):
    raise FileNotFoundError(
        f"Place {required_data} and the crop-economics*.csv files in {DATA_DIR}. "
        "See the notebook introduction for the complete data checklist."
    )
CACHE_DIR  = Path(os.environ.get("ADVISOR_CACHE_DIR", "/tmp/crop-advisor-cache"))
START_YEAR = 1981            # CHIRPS rainfall starts in 1981
TEST_MONTHS, VAL_MONTHS = 24, 24      # most recent 24 months = test; the 24 before = model selection
FUTURE_MONTHS   = 24         # how far ahead to forecast (beyond the next planting month)
LAPSE_C_PER_KM  = 6.0        # temperature drop per km of elevation (pin vs ~11 km weather cell)
MIN_SUITABILITY = 0.40       # crops scoring below this are not recommended

# Used ONLY where crop_requirements_clean.csv has blanks. All are assumptions to replace with sourced values.
ASSUMED_TEMP_MARGIN_C  = 6.0          # survivable range = ideal range +/- this
ASSUMED_PH_MARGIN      = 1.0
ASSUMED_RAIN_LOW, ASSUMED_RAIN_HIGH = 0.5, 1.5   # survivable rain = ideal_min*0.5 .. ideal_max*1.5
DEFAULT_DURATION_DAYS  = {"Vegetable": 100, "Root/tuber": 180, "Cereal/millet": 110,
                          "Legume/pulse": 85, "Bulb crop": 120, "Oilseed/legume": 105, "Condiment": 150}
DURATION_OVERRIDE_DAYS = {"ash_plantain": 300, "gotukola": 60, "mukunuwenna": 45, "kangkung": 45,
                          "radish": 45, "leeks": 150, "bell_pepper_capsicum": 150, "cassava_manioc": 270,
                          "kiri_ala": 240, "innala": 210, "raja_ala": 270, "potato": 100}

# crop_id (requirements table) -> crop name used in the Dept. of Agriculture economics files
ECON_NAME = {
    "carrot": "Carrot", "cabbage": "Cabbage", "bell_pepper_capsicum": "Capsicum", "brinjal": "Brinjal",
    "pumpkin": "Pumpkin", "tomato": "Tomato", "bitter_gourd": "Bitter Gourd", "cassava_manioc": "Manioc",
    "sweet_potato": "Sweet Potato", "potato": "Potato", "maize": "Maize", "finger_millet_kurakkan": "Kurakkan",
    "green_gram_mung_bean": "Green Gram", "cowpea": "Cowpea", "black_gram": "Black Gram",
    "soybean": "Soya Bean", "groundnut_peanut": "Ground Nut", "big_onion": "Big Onion",
    "red_onion": "Red Onion", "green_dried_chilies": "Green Chilli",
}

# ---------------------------------------------------------------- load data
def read_csv_safe(path, **kw):
    for enc in ("utf-8-sig", "latin-1"):
        try:
            return pd.read_csv(path, encoding=enc, **kw)
        except UnicodeDecodeError:
            continue
    raise ValueError(f"Could not decode {path}")

def load_tables():
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    crops = read_csv_safe(DATA_DIR / "crop_requirements_clean.csv")
    crops = crops[crops["included_in_engine"].astype(str).str.lower().eq("true")].reset_index(drop=True)

    # The five crop-economics files are copies of the same 2021-2025 table, so duplicates are dropped.
    econ = pd.concat([read_csv_safe(f, comment="#") for f in sorted(DATA_DIR.glob("crop-economics*.csv"))])
    econ = econ.drop_duplicates(subset=["period", "column", "indicator"]).rename(columns={"column": "location"})
    pat = re.compile(r"^(?P<crop>.+?) - (?P<water>IR|RF) - (?P<metric>.+) - (?P<season>Maha|Yala)$")
    econ = pd.concat([econ, econ["indicator"].str.extract(pat)], axis=1).dropna(subset=["crop"])

    net = econ[econ.metric == "Net Return Rs/ha - Including farmer owned labour"].copy()
    key = ["crop", "water", "season", "location", "period"]
    yld = econ[econ.metric == "Yield kg/ha"][key + ["value"]].rename(columns={"value": "yield_kg_ha"})
    prc = econ[econ.metric == "Farmgate Price Rs/kg"][key + ["value"]].rename(columns={"value": "price"})
    rev = yld.merge(prc, on=key)
    rev["gross"] = rev["yield_kg_ha"] * rev["price"]

    profit = read_csv_safe(DATA_DIR / "sri_lanka_crop_profit_with_estimates.csv").set_index("crop")
    return crops, net, rev[key + ["gross"]], profit

CROPS, NET_DF, REV_DF, PROFIT_DF = load_tables()


# ====================================================== Earth Engine data fetch
def _monthly_series(collection_id, bands, reducer, pt, scale):
    """Monthly sum/mean in bounded Earth Engine batches to limit memory use."""
    ic = ee.ImageCollection(collection_id).select(bands)
    latest = pd.Timestamp(
        ee.Date(ic.aggregate_max("system:time_start")).format("YYYY-MM").getInfo() + "-01"
    )
    first = pd.Timestamp(year=START_YEAR, month=1, day=1)
    month_count = (latest.year - first.year) * 12 + latest.month - first.month + 1
    rows = []

    for offset in range(0, month_count, 120):
        batch_start = first + pd.DateOffset(months=offset)
        batch_count = min(120, month_count - offset)
        start = ee.Date.fromYMD(batch_start.year, batch_start.month, 1)

        def per_month(i):
            s = start.advance(ee.Number(i), "month")
            img = getattr(ic.filterDate(s, s.advance(1, "month")), reducer)()
            vals = img.reduceRegion(ee.Reducer.first(), pt, scale)
            return ee.Feature(None, vals).set("t", s.millis())

        features = ee.FeatureCollection(
            ee.List.sequence(0, batch_count - 1).map(per_month)
        ).getInfo()["features"]
        rows.extend(feature["properties"] for feature in features)

    df = pd.DataFrame(rows)
    df.index = pd.to_datetime(df.pop("t"), unit="ms").dt.to_period("M").dt.to_timestamp()
    return df.iloc[:-1]          # drop the newest month: it can be incomplete


def fetch_static(lat, lon):
    """Elevation, soil pH and district for the pin. Anything that fails is returned as None."""
    pt = ee.Geometry.Point([lon, lat])
    out = {"lat": lat, "lon": lon, "elev_m": None, "cell_elev_m": None, "soil_ph": None, "district": None}
    try:
        srtm = ee.Image("USGS/SRTMGL1_003")
        out["elev_m"] = srtm.reduceRegion(ee.Reducer.first(), pt, 30).get("elevation").getInfo()
        box = pt.buffer(5500).bounds()    # roughly one ERA5-Land cell
        out["cell_elev_m"] = srtm.reduceRegion(ee.Reducer.mean(), box, 250, maxPixels=1e9).get("elevation").getInfo()
    except Exception as e:
        print("  elevation failed:", e)
    try:
        bands = ["phh2o_0-5cm_mean", "phh2o_5-15cm_mean", "phh2o_15-30cm_mean"]
        vals = ee.Image("projects/soilgrids-isric/phh2o_mean").select(bands) \
                 .reduceRegion(ee.Reducer.first(), pt, 250).getInfo()
        vals = [v for v in vals.values() if v is not None]
        ph = float(np.mean(vals))
        out["soil_ph"] = ph / 10 if ph > 14 else ph      # SoilGrids stores pH x 10
    except Exception as e:
        print("  soil pH failed:", e)
    try:
        f = ee.FeatureCollection("FAO/GAUL/2015/level2").filterBounds(pt).first()
        out["district"] = f.get("ADM2_NAME").getInfo()
    except Exception as e:
        print("  district failed:", e)
    return out


def fetch_history(lat, lon):
    """Monthly weather history: rain (CHIRPS), temperature + evaporation (ERA5-Land), actual ET (WaPOR)."""
    pt = ee.Geometry.Point([lon, lat])
    rain = _monthly_series("UCSB-CHG/CHIRPS/DAILY", ["precipitation"], "sum", pt, 5566)
    rain.columns = ["rain_mm"]

    era = "ECMWF/ERA5_LAND/DAILY_AGGR"
    tmp = _monthly_series(era, ["temperature_2m", "temperature_2m_min", "temperature_2m_max"], "mean", pt, 11132)
    tmp = tmp - 273.15
    tmp.columns = ["temp_mean_c", "temp_min_c", "temp_max_c"]
    pet = _monthly_series(era, ["potential_evaporation_sum"], "sum", pt, 11132).abs() * 1000   # m (negative) -> mm
    pet.columns = ["pet_mm"]

    df = rain.join(tmp, how="outer").join(pet, how="outer")

    # WaPOR actual ET: 10-day values, scaled by 0.1 -> mm/day, averaged to months (2018 onwards only)
    try:
        ic = ee.ImageCollection("projects/UNFAO/wapor/v3/L1-AETI-D").filterDate("2018-01-01", "2030-01-01")
        rows = ic.getRegion(pt, 300).getInfo()
        w = pd.DataFrame(rows[1:], columns=rows[0])
        w["date"] = pd.to_datetime(w["time"], unit="ms")
        w["et"] = w["L1-AETI-D"] * 0.1
        et = w.dropna(subset=["et"]).set_index("date")["et"].resample("MS").mean()
        df["et_mm_day"] = et.iloc[:-1]
    except Exception as e:
        print("  WaPOR ET failed (continuing without it):", e)
    return df


def get_location_data(lat, lon, refresh=False):
    """Fetch once, cache in Drive. Use refresh=True roughly once a month."""
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    key = f"{lat:.3f}_{lon:.3f}"
    fw, fs = CACHE_DIR / f"{key}_weather.csv", CACHE_DIR / f"{key}_static.json"
    if fw.exists() and fs.exists() and not refresh:
        weather = pd.read_csv(fw, index_col=0, parse_dates=True)
        static = json.loads(fs.read_text())
        print("loaded cached data for", key)
        return weather, static

    print("fetching from Earth Engine (the first run takes a few minutes)...")
    static = fetch_static(lat, lon)
    weather = fetch_history(lat, lon)

    # ERA5-Land temperatures describe an ~11 km cell, not the pin. Correct for the elevation difference.
    adj = 0.0
    if static["elev_m"] is not None and static["cell_elev_m"] is not None:
        adj = LAPSE_C_PER_KM * (static["cell_elev_m"] - static["elev_m"]) / 1000
        for c in ("temp_mean_c", "temp_min_c", "temp_max_c"):
            weather[c] = weather[c] + adj
    static["temp_adjust_c"] = adj

    weather.to_csv(fw)
    fs.write_text(json.dumps(static))
    return weather, static

# ====================================================== forecasting each variable
VARIABLES = {   # column -> (label, unit)
    "rain_mm":     ("Rainfall", "mm/month"),
    "temp_mean_c": ("Mean temperature", "°C"),
    "temp_min_c":  ("Night-time (min) temperature", "°C"),
    "temp_max_c":  ("Daytime (max) temperature", "°C"),
    "pet_mm":      ("Reference evaporation", "mm/month"),
    "et_mm_day":   ("Actual ET (WaPOR)", "mm/day"),
}
NON_NEGATIVE = {"rain_mm", "pet_mm", "et_mm_day"}

def _month_means(train, idx):
    m = train.groupby(train.index.month).mean()
    return np.array([m.get(d.month, train.mean()) for d in idx])

# Candidate models. The best one per variable is chosen on the validation window, never on the test window.
def f_climatology(train, idx):      # average for that calendar month, all years
    return _month_means(train, idx)

def f_recent_climatology(train, idx):   # same, last 10 years only (captures recent shifts)
    return _month_means(train.iloc[-120:], idx)

def f_climatology_trend(train, idx):    # monthly average + linear drift of the anomalies
    anom = (train - train.groupby(train.index.month).transform("mean")).values
    slope, intercept = np.polyfit(np.arange(len(train)), anom, 1)
    h = np.arange(len(train), len(train) + len(idx))
    return _month_means(train, idx) + intercept + slope * h

def f_ets(train, idx):              # Holt-Winters with damped trend and yearly seasonality
    fit = ExponentialSmoothing(train.values, trend="add", damped_trend=True, seasonal="add",
                               seasonal_periods=12, initialization_method="estimated").fit()
    return fit.forecast(len(idx))

CANDIDATES = {"climatology": f_climatology, "recent_climatology": f_recent_climatology,
              "climatology+trend": f_climatology_trend, "holt_winters": f_ets}

def _tolerance(name, actual):
    """What counts as 'close enough' for a farmer."""
    a = np.asarray(actual, float)
    if name.startswith("temp"):
        return np.full_like(a, 1.0)               # within 1 °C
    if name == "rain_mm":
        return np.maximum(0.30 * a, 25.0)         # within 30% or 25 mm, whichever is larger
    return np.maximum(0.20 * a, 1e-9)             # evaporation / ET: within 20%

def _run(name, fn, train, idx):
    try:
        pred = np.asarray(fn(train, idx), float)
        if not np.isfinite(pred).all():
            raise ValueError("non-finite forecast")
    except Exception:
        pred = f_climatology(train, idx)
    return np.clip(pred, 0, None) if name in NON_NEGATIVE else pred

def evaluate_variable(series, name, horizon):
    """Select a model on validation months, score it on the held-out TEST months, forecast the future."""
    s = series.copy()
    s.index = pd.to_datetime(s.index)
    s = s.dropna().asfreq("MS").interpolate(limit=3).dropna()
    n = len(s)
    test, val = min(TEST_MONTHS, n // 4), min(VAL_MONTHS, n // 4)
    if n - test - val < 36:
        return None                                # too short to model honestly
    train, valid, tst = s.iloc[: n - test - val], s.iloc[n - test - val: n - test], s.iloc[n - test:]

    val_mae = {k: float(np.mean(np.abs(valid.values - _run(name, fn, train, valid.index))))
               for k, fn in CANDIDATES.items()}
    best = min(val_mae, key=val_mae.get)

    trainval = s.iloc[: n - test]                  # test months are still unseen here
    pred = _run(name, CANDIDATES[best], trainval, tst.index)
    clim = _run(name, f_climatology, trainval, tst.index)
    err, err_clim = tst.values - pred, tst.values - clim
    mae, mae_clim = float(np.mean(np.abs(err))), float(np.mean(np.abs(err_clim)))
    rmse = float(np.sqrt(np.mean(err ** 2)))
    out = {
        "name": name, "model": best, "n_months": n, "n_test": test,
        "mae": mae, "rmse": rmse, "mae_climatology": mae_clim,
        "skill_vs_climatology": 1 - mae / mae_clim if mae_clim > 0 else np.nan,
        "hit_rate": float(np.mean(np.abs(err) <= _tolerance(name, tst.values))),
        "corr": float(np.corrcoef(tst.values, pred)[0, 1]) if np.std(pred) > 0 else np.nan,
        "val_mae": val_mae,
        "history": s,
        "test": pd.DataFrame({"actual": tst.values, "forecast": pred, "climatology": clim}, index=tst.index),
    }
    # final model: refit on everything and forecast ahead (+/- 1.28 x test RMSE is a rough 80% band)
    fut_idx = pd.date_range(s.index[-1] + pd.offsets.MonthBegin(1), periods=horizon, freq="MS")
    yhat = _run(name, CANDIDATES[best], s, fut_idx)
    out["future"] = pd.DataFrame({"yhat": yhat, "lo": yhat - 1.28 * rmse, "hi": yhat + 1.28 * rmse}, index=fut_idx)
    if name in NON_NEGATIVE:
        out["future"]["lo"] = out["future"]["lo"].clip(lower=0)
    return out

def forecast_all(weather, horizon):
    results = {}
    for col in VARIABLES:
        if col not in weather or weather[col].dropna().empty:
            continue
        r = evaluate_variable(weather[col], col, horizon)
        if r is None:
            print(f"  skipped {col}: not enough history")
        else:
            results[col] = r
    return results

def accuracy_table(results):
    rows = []
    for col, r in results.items():
        label, unit = VARIABLES[col]
        rows.append({"variable": label, "unit": unit, "model chosen": r["model"],
                     "test months": r["n_test"], "avg error": round(r["mae"], 2),
                     "avg error of plain monthly average": round(r["mae_climatology"], 2),
                     "skill vs monthly average": f"{r['skill_vs_climatology']:+.0%}",
                     "months 'close enough'": f"{r['hit_rate']:.0%}"})
    return pd.DataFrame(rows)

def plain_language_accuracy(results):
    lines = []
    for col, r in results.items():
        label, unit = VARIABLES[col]
        better = r["skill_vs_climatology"] > 0.02
        lines.append(f"- {label}: on the last {r['n_test']} months the forecast was off by {r['mae']:.1f} {unit} on average and "
                     f"'close enough' in {r['hit_rate']:.0%} of months. "
                     + ("It beat" if better else "It did NOT beat") + " simply using the average for that calendar month.")
    return "\n".join(lines)

def plot_forecasts(results, history_months=60):
    cols = list(results)
    fig, axes = plt.subplots(len(cols), 1, figsize=(11, 2.6 * len(cols)), squeeze=False)
    for ax, col in zip(axes[:, 0], cols):
        r = results[col]; label, unit = VARIABLES[col]
        hist = r["history"].iloc[-history_months:]
        ax.plot(hist.index, hist.values, color="0.6", lw=1, label="observed")
        ax.plot(r["test"].index, r["test"]["actual"], color="k", lw=1.5, label="test actual")
        ax.plot(r["test"].index, r["test"]["forecast"], color="tab:red", lw=1.5, label="test forecast")
        f = r["future"]
        ax.plot(f.index, f["yhat"], color="tab:blue", lw=1.5, label="future forecast")
        ax.fill_between(f.index, f["lo"], f["hi"], color="tab:blue", alpha=0.15)
        ax.set_ylabel(unit); ax.set_title(label, fontsize=10, loc="left")
    axes[0, 0].legend(ncol=4, fontsize=8, loc="upper left")
    plt.tight_layout(); plt.show()

# ====================================================== crop suitability + profit
def season_of(month):                       # Maha ~ Sep-Feb, Yala ~ Mar-Aug (as defined in the crop table)
    return "Maha" if month in (9, 10, 11, 12, 1, 2) else "Yala"

def planting_months(c):
    a, b = str(c.season_start_month).strip().lower(), str(c.season_end_month).strip().lower()
    if a in ("any", "nan", "") or b in ("any", "nan", ""):
        return list(range(1, 13))
    a, b = int(float(a)), int(float(b))
    months, m = [a], a
    while m != b:
        m = m % 12 + 1
        months.append(m)
    return months

def crop_duration(c):
    d = c.get("crop_duration_days")
    if pd.notna(d):
        return float(d), False
    if c.crop_id in DURATION_OVERRIDE_DAYS:
        return float(DURATION_OVERRIDE_DAYS[c.crop_id]), True
    return float(DEFAULT_DURATION_DAYS.get(c.crop_group, 100)), True

def cycle_weather(weather, start, days):
    """Rain total and mean temperature over one crop cycle starting at `start`."""
    n_full, rem = int(days // 30.4), days - int(days // 30.4) * 30.4
    months = pd.date_range(start, periods=n_full + (1 if rem > 1 else 0), freq="MS")
    if len(months) == 0 or not months.isin(weather.index).all():
        return None                          # cycle runs past the forecast horizon
    w = np.ones(len(months))
    if rem > 1:
        w[-1] = rem / 30.4
    sub = weather.loc[months]
    return {"rain_mm": float((sub["rain_mm"] * w).sum()),
            "temp_c": float(np.average(sub["temp_mean_c"], weights=w)),
            "end": months[-1]}

def trapezoid(x, lo0, lo1, hi1, hi0):
    """1 inside the ideal range [lo1, hi1], falling linearly to 0 at the survivable limits lo0 / hi0."""
    if x <= lo0 or x >= hi0:
        return 0.0
    if x < lo1:
        return (x - lo0) / max(lo1 - lo0, 1e-9)
    if x > hi1:
        return (hi0 - x) / max(hi0 - hi1, 1e-9)
    return 1.0

def suitability(c, st, static, irrigated):
    flags = set()
    t0 = c.temp_survivable_min_c if pd.notna(c.temp_survivable_min_c) else c.temp_ideal_min_c - ASSUMED_TEMP_MARGIN_C
    t1 = c.temp_survivable_max_c if pd.notna(c.temp_survivable_max_c) else c.temp_ideal_max_c + ASSUMED_TEMP_MARGIN_C
    r0 = c.rain_cycle_survivable_min_mm if pd.notna(c.rain_cycle_survivable_min_mm) else c.rain_cycle_ideal_min_mm * ASSUMED_RAIN_LOW
    r1 = c.rain_cycle_survivable_max_mm if pd.notna(c.rain_cycle_survivable_max_mm) else c.rain_cycle_ideal_max_mm * ASSUMED_RAIN_HIGH
    if pd.isna(c.temp_survivable_min_c) or pd.isna(c.temp_survivable_max_c):
        flags.add("assumed temperature limits")
    if pd.isna(c.rain_cycle_survivable_min_mm) or pd.isna(c.rain_cycle_survivable_max_mm):
        flags.add("assumed rainfall limits")

    s_t = trapezoid(st["temp_c"], t0, c.temp_ideal_min_c, c.temp_ideal_max_c, t1)
    s_r = trapezoid(st["rain_mm"], r0, c.rain_cycle_ideal_min_mm, c.rain_cycle_ideal_max_mm, r1)
    if irrigated and st["rain_mm"] < c.rain_cycle_ideal_min_mm:
        s_r = 1.0                            # shortfall assumed covered by irrigation

    ph = static.get("soil_ph")
    if ph is None or pd.isna(c.ph_ideal_min):
        s_p = 1.0
        flags.add("soil pH unknown, not scored")
    else:
        p0 = c.ph_survivable_min if pd.notna(c.ph_survivable_min) else c.ph_ideal_min - ASSUMED_PH_MARGIN
        p1 = c.ph_survivable_max if pd.notna(c.ph_survivable_max) else c.ph_ideal_max + ASSUMED_PH_MARGIN
        s_p = trapezoid(ph, p0, c.ph_ideal_min, c.ph_ideal_max, p1)
        if pd.isna(c.ph_survivable_min):
            flags.add("assumed pH limits")

    elev, emax = static.get("elev_m"), c.elev_max_m
    if pd.notna(emax) and elev is not None and elev > emax:
        s_e = 0.0
        flags.add(f"above elevation limit ({emax:.0f} m)")
    else:
        s_e = 1.0

    score = s_e * (s_t ** 0.4) * (s_r ** 0.4) * (s_p ** 0.2)
    parts = {"temperature": s_t, "rainfall": s_r, "soil pH": s_p}
    limiting = min(parts, key=parts.get)
    why = ""
    if parts[limiting] < 0.8:
        if limiting == "temperature":
            why = "too cold" if st["temp_c"] < c.temp_ideal_min_c else "too hot"
        elif limiting == "rainfall":
            why = "too dry" if st["rain_mm"] < c.rain_cycle_ideal_min_mm else "too wet"
        else:
            why = "soil too acidic" if ph < c.ph_ideal_min else "soil too alkaline"
    return score, why, flags

def expected_economics(crop_id, season, irrigated, district):
    """Latest observed net return (Rs/ha, incl. farmer-owned labour), with how trustworthy it is."""
    name = ECON_NAME.get(crop_id)
    if name is not None:
        w1, w2 = ("IR", "RF") if irrigated else ("RF", "IR")
        s2 = "Yala" if season == "Maha" else "Maha"
        for k, (w, s) in enumerate([(w1, season), (w2, season), (w1, s2), (w2, s2)]):
            sub = NET_DF[(NET_DF.crop == name) & (NET_DF.water == w) & (NET_DF.season == s)]
            if sub.empty:
                continue
            latest = sub.sort_values("period").groupby("location").tail(1)
            own = latest[latest.location.str.lower() == str(district).lower()] if district else latest.iloc[0:0]
            if len(own):
                use, scope, conf = own, f"{district} observed", "high"
            else:
                use = latest
                conf = "medium" if len(latest) >= 3 else "low"      # one or two other districts is thin evidence
                scope = f"median of {len(latest)} other location(s)"
            net = float(use["value"].median())
            yr = int(use["period"].max())
            g = use.merge(REV_DF, on=["crop", "water", "season", "location", "period"], how="left")["gross"]
            gross = float(g.median()) if g.notna().any() else np.nan
            if k > 0:
                conf = "low"
            mismatch = []
            if s != season: mismatch.append(f"{s} data used for {season} planting")
            if w != w1:     mismatch.append(f"{'irrigated' if w == 'IR' else 'rainfed'} data used")
            basis = f"{scope}, {yr}" + (" (" + "; ".join(mismatch) + ")" if mismatch else "")
            return {"net": net, "gross": gross, "basis": basis, "confidence": conf}
    # fall back to the 2021/22 benchmark table (single location, or a proxy from a similar crop)
    crop_name = CROPS.loc[CROPS.crop_id == crop_id, "crop_name"].iloc[0]
    if crop_name in PROFIT_DF.index and pd.notna(PROFIT_DF.loc[crop_name, "net_profit_lkr_per_hectare"]):
        r = PROFIT_DF.loc[crop_name]
        official = str(r["data_status"]).startswith("OFFICIAL")
        return {"net": float(r["net_profit_lkr_per_hectare"]), "gross": np.nan,
                "basis": f"2021/22 benchmark, {r['benchmark_location_or_sample']}",
                "confidence": "low" if official else "very low"}
    return {"net": np.nan, "gross": np.nan, "basis": "no profit data", "confidence": "none"}

def risk_adjusted(net, gross, s):
    """Scale revenue by suitability (a crude yield-risk proxy) but never let a bad fit improve a loss."""
    if np.isnan(net):
        return np.nan
    g = gross if not np.isnan(gross) else max(net, 0.0)
    return net - (1 - s) * g

def rank_crops(weather, start_from, static, irrigated=False, previous_crop=None, district=None,
               min_suitability=MIN_SUITABILITY, n_start_months=12, return_all=False,
               selection_metric="risk_adjusted"):
    """Choose each crop's planting month by climate fit, profit/day, or risk-adjusted return."""
    blocked_family = None
    if previous_crop:
        blocked_family = CROPS.loc[CROPS.crop_id == previous_crop, "crop_family"].iloc[0]
    rows = []
    for _, c in CROPS.iterrows():
        days, dur_assumed = crop_duration(c)
        allowed, best = planting_months(c), None
        for off in range(n_start_months):
            start = start_from + pd.DateOffset(months=off)
            if start.month not in allowed:
                continue
            st = cycle_weather(weather, start, days)
            if st is None:
                continue
            suit, why, flags = suitability(c, st, static, irrigated)
            eco = expected_economics(c.crop_id, season_of(start.month), irrigated, district)
            adj = risk_adjusted(eco["net"], eco["gross"], suit)
            cand = {
                "crop_id": c.crop_id, "crop": c.crop_name, "family": c.crop_family,
                "plant_in": start.strftime("%b %Y"), "harvest_by": st["end"].strftime("%b %Y"),
                "season": season_of(start.month), "suitability": round(suit, 2), "main_issue": why,
                "forecast_rain_mm": round(st["rain_mm"]), "rain_wanted_mm": f"{c.rain_cycle_ideal_min_mm:.0f}-{c.rain_cycle_ideal_max_mm:.0f}",
                "forecast_temp_c": round(st["temp_c"], 1), "temp_wanted_c": f"{c.temp_ideal_min_c:.0f}-{c.temp_ideal_max_c:.0f}",
                "net_return_lkr_ha": eco["net"], "risk_adj_return_lkr_ha": adj,
                "profit_confidence": eco["confidence"], "profit_basis": eco["basis"],
                "notes": "; ".join(sorted(flags | ({"assumed crop duration"} if dur_assumed else set()))),
                "blocked_by_rotation": bool(blocked_family and c.crop_family == blocked_family),
            }
            if selection_metric == "suitability":
                key = (suit, -np.inf if np.isnan(adj) else adj)
            elif selection_metric == "profit_per_day":
                per_day = eco["net"] / days if not np.isnan(eco["net"]) else -np.inf
                key = (per_day, suit)
            else:
                key = (-np.inf if np.isnan(adj) else adj, suit)
            if best is None or key > best[0]:
                best = (key, cand)
        if best:
            rows.append(best[1])
    df = pd.DataFrame(rows)
    if df.empty or return_all:
        return df
    df = df[(df.suitability >= min_suitability) & ~df.blocked_by_rotation & df.risk_adj_return_lkr_ha.notna()]
    df = df.sort_values("risk_adj_return_lkr_ha", ascending=False).reset_index(drop=True)
    df.insert(0, "rank", df.index + 1)
    return df

def recommendation_backtest(results, static, irrigated=False, district=None):
    """Would the crop ranking change if the true weather had been known? Compares forecast vs actual on the test months."""
    need = ["rain_mm", "temp_mean_c"]
    if not all(v in results for v in need):
        return None
    fc = pd.DataFrame({v: results[v]["test"]["forecast"] for v in need})
    ac = pd.DataFrame({v: results[v]["test"]["actual"] for v in need})
    t0 = fc.index[0]
    kw = dict(static=static, irrigated=irrigated, district=district, n_start_months=12)
    a_all = rank_crops(ac, t0, return_all=True, **kw)
    f_all = rank_crops(fc, t0, return_all=True, **kw)
    m = a_all.merge(f_all, on="crop_id", suffixes=("_act", "_fc"))
    if len(m) < 5:
        return None
    ok_a = m[(m.suitability_act >= MIN_SUITABILITY) & m.risk_adj_return_lkr_ha_act.notna()]
    ok_f = m[(m.suitability_fc >= MIN_SUITABILITY) & m.risk_adj_return_lkr_ha_fc.notna()]
    top_a = set(ok_a.nlargest(5, "risk_adj_return_lkr_ha_act").crop_id)
    top_f = set(ok_f.nlargest(5, "risk_adj_return_lkr_ha_fc").crop_id)
    rho = spearmanr(m.suitability_act, m.suitability_fc).correlation
    return {"top5_overlap": len(top_a & top_f), "top5_actual": sorted(top_a), "top5_forecast": sorted(top_f),
            "suitability_rank_corr": float(rho), "n_crops": len(m), "test_start": t0}

CLOUDINARY_CLOUD_NAME = os.environ.get("CLOUDINARY_CLOUD_NAME", "tsdjmul7").strip()
# Most images use crops/<crop_id>. Add entries here when an asset has a different public ID.
CLOUDINARY_PUBLIC_IDS = {
    "carrot": "Carrot-fb",
    "cabbage": "Starr_070730-7852_Brassica_oleracea_var._capitata",
    "beetroot": "Beta_vulgaris__San_Francisco_farmers_market",
    "leeks": "download",
    "radish": "radish",
    "knol_khol": "knol_khol",
    "bell_pepper_capsicum": "bell_pepper-capsicum",
    "brinjal": "brinjal",
    "pumpkin": "pumpkin",
    "okra": "okra",
    "tomato": "tomato",
    "bitter_gourd": "bitter_gourd",
    "snake_gourd": "snake_gourd",
    "ridge_gourd": "ridge_gourd",
    "cucumber": "cucumber",
    "ash_plantain": "ash_plantain",
    "long_beans_yard_long_bean": "long_beans",
    "winged_beans": "winged_beans",
    "gotukola": "gotukola",
    "mukunuwenna": "mukunuwenna",
    "kangkung": "kangkung",
    "cassava_manioc": "cassava_manioc",
    "sweet_potato": "sweet_potato",
    "potato": "potato",
    "kiri_ala": "kiri_ala",
    "innala": "innala",
    "raja_ala": "raja_ala",
    "maize": "maize",
    "finger_millet_kurakkan": "finger_millet_kurakkan",
    "sorghum": "sorghum",
    "foxtail_millet": "foxtail_millet",
    "kodo_millet": "kodo_millet",
    "green_gram_mung_bean": "green_gram_mung_bean",
    "cowpea": "cowpea",
    "black_gram": "black_gram",
    "soybean": "soybean",
    "groundnut_peanut": "groundnut_peanut",
    "big_onion": "big_onion",
    "red_onion": "red_onion",
    "green_dried_chilies": "green_dried_chilies",
}

def _crop_image_url(crop_id):
    if not CLOUDINARY_CLOUD_NAME:
        return None
    from urllib.parse import quote
    cloud_name = quote(CLOUDINARY_CLOUD_NAME, safe="")
    public_id = CLOUDINARY_PUBLIC_IDS.get(str(crop_id), f"crops/{crop_id}")
    public_id = quote(public_id, safe="_-/")
    return (
        f"https://res.cloudinary.com/{cloud_name}/image/upload/"
        f"f_auto,q_auto,w_720,h_405,c_pad,b_auto/{public_id}"
    )


def _api_number(value):
    try:
        number = float(value)
        return number if math.isfinite(number) else None
    except (TypeError, ValueError):
        return None


def _crop_category(group):
    group = str(group).lower()
    if "legume" in group or "pulse" in group:
        return "Legumes"
    if "cereal" in group or "millet" in group:
        return "Grains"
    if "vegetable" in group:
        return "Vegetables"
    return "Other"


def advisor_api_result(lat, lon, soil_ph=None):
    weather, location_static = get_location_data(lat, lon, refresh=False)
    static = dict(location_static)
    if soil_ph is not None:
        static["soil_ph"] = soil_ph
    district = static.get("district")
    observed = weather[["rain_mm", "temp_mean_c"]].dropna()
    if observed.empty:
        raise ValueError("Earth Engine returned no rainfall and temperature history")

    last_observed = observed.index[-1]
    next_month = pd.Timestamp.today().normalize().replace(day=1) + pd.DateOffset(months=1)
    start_from = max(next_month, last_observed + pd.DateOffset(months=1))
    gap = (start_from.year - last_observed.year) * 12 + start_from.month - last_observed.month
    horizon = gap + FUTURE_MONTHS - 1
    results = forecast_all(weather, horizon)
    if not all(name in results for name in ("rain_mm", "temp_mean_c")):
        raise ValueError("Not enough monthly weather history to validate crop forecasts")

    future = pd.DataFrame({
        name: results[name]["future"]["yhat"]
        for name in ("rain_mm", "temp_mean_c")
    })
    climate_ranked = rank_crops(
        future,
        start_from,
        static,
        irrigated=False,
        previous_crop=None,
        district=district,
        selection_metric="suitability",
    )
    profit_ranked = rank_crops(
        future,
        start_from,
        static,
        irrigated=False,
        previous_crop=None,
        district=district,
        return_all=True,
        selection_metric="profit_per_day",
    )
    backtest = recommendation_backtest(results, static, irrigated=False, district=district)
    correlation = _api_number(backtest.get("suitability_rank_corr")) if backtest else None
    top_five_overlap = int(backtest["top5_overlap"]) if backtest else None
    rain_mae = _api_number(results["rain_mm"]["mae"])
    temp_mae = _api_number(results["temp_mean_c"]["mae"])
    mae_parts = []
    if rain_mae is not None:
        mae_parts.append(f"Rain MAE {rain_mae:.0f} mm")
    if temp_mae is not None:
        mae_parts.append(f"Temp MAE {temp_mae:.1f} C")

    recommendations = []
    for ranking_mode, ranked in (("climate", climate_ranked), ("profit", profit_ranked)):
        for _, row in ranked.iterrows():
            if pd.isna(row["net_return_lkr_ha"]):
                continue
            crop = CROPS.loc[CROPS.crop_id == row["crop_id"]].iloc[0]
            reasons = [row["main_issue"]] if row["main_issue"] else ["Within forecast climate range"]
            if row["notes"]:
                reasons.append(row["notes"])
            duration_days = int(crop_duration(crop)[0])
            recommendation = {
                "rankingMode": ranking_mode,
                "cropId": str(row["crop_id"]),
                "cropName": str(row["crop"]),
                "localName": None,
                "category": _crop_category(crop["crop_group"]),
                "suitabilityScore": int(round(float(row["suitability"]) * 100)),
                "expectedProfit": _api_number(row["risk_adj_return_lkr_ha"]),
                "profitPerDay": _api_number(row["net_return_lkr_ha"] / duration_days),
                "profitBasis": str(row["profit_basis"]),
                "profitConfidence": str(row["profit_confidence"]),
                "confidenceLevel": " | ".join(mae_parts) if mae_parts else None,
                "imageUrl": _crop_image_url(row["crop_id"]),
                "waterRequirement": str(crop["water_requirement"]),
                "waterRequirementVsPaddy": "Unquantified vs Paddy",
                "durationDays": duration_days,
                "suitabilityReason": "; ".join(reasons),
                "plantIn": str(row["plant_in"]),
                "harvestBy": str(row["harvest_by"]),
                "forecastRainMm": float(row["forecast_rain_mm"]),
                "idealRainMinMm": float(crop["rain_cycle_ideal_min_mm"]),
                "idealRainMaxMm": float(crop["rain_cycle_ideal_max_mm"]),
                "forecastTemperatureC": float(row["forecast_temp_c"]),
                "idealTemperatureMinC": float(crop["temp_ideal_min_c"]),
                "idealTemperatureMaxC": float(crop["temp_ideal_max_c"]),
                "rank": len(recommendations) + 1,
                "suitabilityRankCorrelation": correlation,
                "topFiveOverlap": top_five_overlap,
            }
            recommendations.append(recommendation)

    return {
        "district": district or "Unavailable",
        "agroZone": "Unavailable",
        "plantingWindow": start_from.strftime("%b %Y"),
        "rainfallMaeMm": rain_mae,
        "rainfallSkillVsClimatology": _api_number(results["rain_mm"]["skill_vs_climatology"]),
        "rainfallModel": str(results["rain_mm"]["model"]),
        "temperatureMaeC": temp_mae,
        "temperatureSkillVsClimatology": _api_number(results["temp_mean_c"]["skill_vs_climatology"]),
        "temperatureModel": str(results["temp_mean_c"]["model"]),
        "suitabilityRankCorrelation": correlation,
        "topFiveOverlap": top_five_overlap,
        "soilPHUsed": _api_number(static.get("soil_ph")),
        "soilPHSource": "Farmer-provided soil test" if soil_ph is not None else ("SoilGrids estimate" if static.get("soil_ph") is not None else "Unavailable"),
        "recommendations": recommendations,
    }


CROP_ADVISOR_API_KEY = os.environ.get("CROP_ADVISOR_API_KEY", "").strip()


def require_crop_advisor_key(api_key: str = Header(..., alias="X-API-Key")):
    if not CROP_ADVISOR_API_KEY:
        raise HTTPException(status_code=503, detail="Crop Advisor service is not configured")
    if not hmac.compare_digest(api_key, CROP_ADVISOR_API_KEY):
        raise HTTPException(status_code=401, detail="Invalid API key")


def recommend_crops_endpoint(
    lat: float = Query(..., ge=-90, le=90),
    lon: float = Query(..., ge=-180, le=180),
    soil_ph: float | None = Query(None, ge=0, le=14),
    api_key: str = Header(..., alias="X-API-Key"),
):
    require_crop_advisor_key(api_key)
    try:
        return advisor_api_result(lat, lon, soil_ph=soil_ph)
    except HTTPException:
        raise
    except Exception as error:
        import logging
        logging.getLogger(__name__).exception("Crop recommendation request failed")
        raise HTTPException(status_code=502, detail="Crop analysis failed. Please retry.") from error
