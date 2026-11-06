import logging
import os
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import List, Optional

import ee
from fastapi import FastAPI, HTTPException, Query, Request
from pydantic import BaseModel

logger = logging.getLogger(__name__)

EE_ACCOUNT = os.getenv(
    "EE_SERVICE_ACCOUNT",
    "gee-crop-expert@umaoyaproject.iam.gserviceaccount.com",
)
EE_PROJECT = os.getenv("EE_PROJECT")
EE_CREDENTIALS_FILE = Path(__file__).with_name("service_account.json")


@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        if not EE_CREDENTIALS_FILE.is_file():
            raise FileNotFoundError(
                f"Earth Engine credentials file not found: {EE_CREDENTIALS_FILE}"
            )
        credentials = ee.ServiceAccountCredentials(
            EE_ACCOUNT, str(EE_CREDENTIALS_FILE)
        )
        ee.Initialize(credentials, project=EE_PROJECT)
        app.state.ee_initialization_error = None
        logger.info("Google Earth Engine initialized")
    except Exception as error:
        app.state.ee_initialization_error = error
        logger.exception("Google Earth Engine initialization failed")
    yield


app = FastAPI(lifespan=lifespan)


class TelemetryResponse(BaseModel):
    district: str
    agroZone: str
    elevation: str
    soilMoisture: str
    soilPH: str
    ambientTemp: str
    upcomingRain: str
    sourceMetadata: str
    soilMoistureObservedAt: Optional[int] = None
    ambientTempObservedAt: Optional[int] = None
    forecastIssuedAt: Optional[int] = None
    soilMoistureHistory: List[float]
    soilPHDepthProfile: List[float]
    ambientTempHistory: List[float]
    rainForecast: List[float]


def _sample_value(image, point, band: str, scale: int):
    return image.select(band).reduceRegion(
        reducer=ee.Reducer.first(),
        geometry=point,
        scale=scale,
        maxPixels=16,
    ).get(band)


def _series_values(collection, band: str, point, scale: int, count: int):
    samples = (
        collection.sort("system:time_start", False)
        .limit(count)
        .sort("system:time_start", True)
        .map(
            lambda image: image.set(
                "chart_value",
                image.select(band)
                .reduceRegion(
                    reducer=ee.Reducer.first(),
                    geometry=point,
                    scale=scale,
                    maxPixels=16,
                )
                .get(band),
            )
        )
        .aggregate_array("chart_value")
    )
    return samples


def _numeric_series(values) -> List[float]:
    return [float(value) for value in (values or []) if value is not None]


def _format_sample(value, formatter) -> str:
    if value is None:
        return "Unavailable"
    return formatter(value)


@app.get("/analyze-farm", response_model=TelemetryResponse)
def get_telemetry(
    request: Request,
    lat: float = Query(..., ge=-90, le=90),
    lon: float = Query(..., ge=-180, le=180),
):
    if request.app.state.ee_initialization_error is not None:
        raise HTTPException(
            status_code=503,
            detail="Earth Engine is unavailable. Check the server credentials and configuration.",
        )

    point = ee.Geometry.Point([lon, lat])

    now = datetime.now(timezone.utc)
    start_date = now - timedelta(days=30)

    soil_collection = (
        ee.ImageCollection("NASA/SMAP/SPL4SMGP/008")
        .filterBounds(point)
        .filterDate(start_date.isoformat(), now.isoformat())
    )
    soil_image = (
        soil_collection
        .limit(1, "system:time_start", False)
        .first()
        .select("sm_rootzone")
    )

    soil_ph_image = ee.Image("OpenLandMap/SOL/SOL_PH-H2O_USDA-4C1A2A_M/v02")

    temperature_collection = (
        ee.ImageCollection("ECMWF/ERA5_LAND/HOURLY")
        .filterBounds(point)
        .filterDate(start_date.isoformat(), now.isoformat())
    )
    temperature_image = (
        temperature_collection
        .limit(1, "system:time_start", False)
        .first()
        .select("temperature_2m")
    )

    forecast_collection = (
        ee.ImageCollection("NOAA/GFS0P25")
        .filterBounds(point)
        .filter(
            ee.Filter.gte(
                "creation_time",
                int((now - timedelta(days=2)).timestamp() * 1000),
            )
        )
        .filter(ee.Filter.lte("creation_time", int(now.timestamp() * 1000)))
    )
    latest_forecast_cycle = forecast_collection.filter(
        ee.Filter.eq("creation_time", forecast_collection.aggregate_max("creation_time"))
    )
    six_hour_precipitation = latest_forecast_cycle.filter(
        ee.Filter.inList("forecast_hours", [6, 12, 18, 24])
    )
    rain_24h_image = ee.Image(
        ee.Algorithms.If(
            six_hour_precipitation.size().eq(4),
            six_hour_precipitation.select("total_precipitation_surface")
            .sum()
            .rename("rain_24h"),
            ee.Image.constant(0)
            .rename("rain_24h")
            .updateMask(ee.Image.constant(0)),
        )
    )

    districts = (
        ee.FeatureCollection("FAO/GAUL/2025/level2")
        .filter(ee.Filter.eq("ISO3_CODE", "LKA"))
        .filterBounds(point)
        .limit(1)
    )

    try:
        values = ee.Dictionary(
            {
                "elevation": _sample_value(
                    ee.Image("USGS/SRTMGL1_003"), point, "elevation", 30
                ),
                "soil_moisture": _sample_value(
                    soil_image, point, "sm_rootzone", 11000
                ),
                "temperature": _sample_value(
                    temperature_image, point, "temperature_2m", 11132
                ),
                "soil_moisture_observed_at": soil_image.get("system:time_start"),
                "temperature_observed_at": temperature_image.get(
                    "system:time_start"
                ),
                "forecast_issued_at": latest_forecast_cycle.aggregate_max(
                    "creation_time"
                ),
                "soil_ph": soil_ph_image.select("b0").reduceRegion(
                    reducer=ee.Reducer.first(),
                    geometry=point,
                    scale=250,
                    maxPixels=16,
                ).get("b0"),
                "rain_24h": _sample_value(rain_24h_image, point, "rain_24h", 27830),
                "district": ee.Algorithms.If(
                    districts.size().gt(0),
                    districts.first().get("GAUL2_NAME"),
                    "Unavailable",
                ),
                "soil_moisture_history": _series_values(
                    soil_collection, "sm_rootzone", point, 11000, 8
                ),
                "soil_ph_depth_profile": ee.List(
                    [
                        soil_ph_image.select(band)
                        .reduceRegion(
                            reducer=ee.Reducer.first(),
                            geometry=point,
                            scale=250,
                            maxPixels=16,
                        )
                        .get(band)
                        for band in ["b0", "b10", "b30", "b60", "b100", "b200"]
                    ]
                ),
                "temperature_history": _series_values(
                    temperature_collection, "temperature_2m", point, 11132, 8
                ),
                "rain_forecast": _series_values(
                    six_hour_precipitation.sort("forecast_hours"),
                    "total_precipitation_surface",
                    point,
                    27830,
                    4,
                ),
            }
        ).getInfo()
    except Exception as error:
        logger.exception("Earth Engine telemetry query failed")
        raise HTTPException(
            status_code=502,
            detail="Could not retrieve telemetry from Earth Engine. Please try again.",
        ) from error

    elevation = _format_sample(values.get("elevation"), lambda value: f"{value:g} m")
    soil_moisture = _format_sample(
        values.get("soil_moisture"),
        lambda value: f"{float(value) * 100:.0f}%",
    )
    ambient_temperature = _format_sample(
        values.get("temperature"),
        lambda value: f"{float(value) - 273.15:.1f}°C",
    )
    soil_ph = _format_sample(
        values.get("soil_ph"),
        lambda value: f"{float(value) / 10:.1f}",
    )
    upcoming_rain = _format_sample(
        values.get("rain_24h"),
        lambda value: f"{float(value):.1f} mm (24h)",
    )
    district = values.get("district") or "Unavailable"

    return TelemetryResponse(
        district=district,
        agroZone="Unavailable",
        elevation=elevation,
        soilMoisture=soil_moisture,
        soilPH=soil_ph,
        ambientTemp=ambient_temperature,
        upcomingRain=upcoming_rain,
        sourceMetadata=(
            "Sources: FAO GAUL 2025, OpenLandMap, NASA SMAP, SRTM, "
            "ERA5-Land, and NOAA GFS"
        ),
        soilMoistureObservedAt=values.get("soil_moisture_observed_at"),
        ambientTempObservedAt=values.get("temperature_observed_at"),
        forecastIssuedAt=values.get("forecast_issued_at"),
        soilMoistureHistory=_numeric_series(values.get("soil_moisture_history")),
        soilPHDepthProfile=[
            float(value) / 10
            for value in values.get("soil_ph_depth_profile", [])
            if value is not None
        ],
        ambientTempHistory=[
            float(value) - 273.15
            for value in _numeric_series(values.get("temperature_history"))
        ],
        rainForecast=_numeric_series(values.get("rain_forecast")),
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
