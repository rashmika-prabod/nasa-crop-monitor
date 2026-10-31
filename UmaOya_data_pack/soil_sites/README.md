# Uma Oya field cover, slake and NIR-carbon data, October 2026, with the satellite cover-factor calibration and model reproduction kit

**Version 1.0 (draft, prepared 19 Oct 2026, reproduction kit extended 20 Oct 2026; not yet deposited).**
Companion data set to: Anderson, S. (in prep.) *What fourteen sites buy a basin with no data* (paper 02,
target: International Journal of Applied Earth Observation and Geoinformation).

Uma Oya basin (Mahaweli tributary above Rantambe reservoir, Uva Province, Sri Lanka, ~745 km2). Fieldwork
5-7 October 2026: 14 sites, 7 managed vs less-disturbed pairs, chosen on the ground (a convenience sample,
not a designed one). At each site: a 30-point cover tape, 18-ped slake test, a handheld NIR scanner at four
depths, and phone photos (ground at every tape point, canopy where present). The sites calibrate a
Sentinel-2 NDVI cover factor (C) that moves a satellite-only sediment-yield model of the basin from
119 to 2,073 t km-2 yr-1.

## Contents

```
README.md                  this file (data dictionary, methods, licences)
LICENSE                    CC-BY-4.0 (data, photos, calibration) - see "Licences" for the exceptions
CITATION.cff               how to cite
MANIFEST.csv               every file, its size and SHA-256
data/
  sites.csv                one row per site (14): labels, generalised position, terrain, tape cover, C, NDVI, slake, NIR
  sites_generalised.geojson the same 14 sites as points (generalised positions)
  tape_points.csv          30-point tape, one row per point (416)
  slake_peds.csv           slake test, one row per ped (251)
  nir_scans.csv            NIR scanner predictions, one row per scan (55)
  nir_carbon_stocks.csv    0-30 cm organic-carbon stock per site (assumed bulk density)
  cover_photos.csv         photo-derived cover, one row per photo
  cover_photos_sites.csv   photo-derived cover, site means beside the tape
calibration/
  site_c_calibration.csv   field C vs Sentinel-2 record-median NDVI (14 sites)
  c_ndvi_fit.json          the fitted curve: C = exp(0.3404 - 3.8457 NDVI), NDVI clamped to 0.407-0.848
  site_cover_factors.csv   per-site AH 703 sub-factors and field C (carbon-fixed)
model/
  reproduce.py             one-command reproduction of 2,073 / 1,850 / 119 t km-2 yr-1, Table 2, the curve's 90%
                           band and the reconciled level 1,045-1,830 (median 1,381; Table S5 main row)
  requirements.txt         numpy, rasterio
  LICENSES.md              per-file licences of the engine and its input rasters
  engine_2dcdd09/          frozen Storm Model engine (uma_oya commit 2dcdd09), only the modules and data it needs
  inputs/
    olm_soc_raw.tif        OpenLandMap SOC on the 90 m model grid (stored units of 5 g/kg)
    s2_record.tif          Sentinel-2 record-median NDVI on the 90 m model grid
    c02_fit_boot_ab.npy    the 5,000 site-pair bootstrap (a, b) curves of the C-NDVI fit (curve 90% band)
    structure_runs.npz     stored engine outputs of the 8,192-structure ensemble (knobs, basin yields, Somasiri-
                           window means, Welimada quickflow); per-structure hindcasts need daily gauge rain,
                           which cannot be redistributed
    rantambe_draws.npz     Rantambe draws: deposit 1990-2013 (MCM), bulk density, Uma Oya share, Brune TE
    welimada_table2.csv    Diyabalanage et al. (2017) Table 2, Upper Uma Oya at Welimada (published values,
                           transcribed)
photos_cover_2026.zip      627 cover photos: photos/site_NN/siteNN_<up|down>_<seq>.jpg, metadata stripped, <= 2000 px
                           (zipped because a Zenodo record holds at most 100 files)
```

## Reproduce the model numbers

```
cd model
pip install -r requirements.txt      # numpy, rasterio (Python >= 3.10)
python reproduce.py
```
Prints the refitted C-NDVI curve and four yields (exact to 0.01 %), then the extended checks below, and exits 0 if
every number reproduces (`--quick` skips the 500-curve bootstrap):

| run | meaning | t km-2 yr-1 |
|---|---|---|
| S1 | taught C: field-calibrated curve applied to Sentinel-2 NDVI, carbon-fixed K, grassland class added (headline) | 2,072.5 |
| E1 | field-class C: tape-measured tree and cropland classes, textbook C elsewhere, carbon-fixed K, grassland added | 1,850.3 |
| S0 | satellite-only: the dashboard's MODIS van der Knijff C, carbon-fixed K, grassland added | 118.6 |
| E0 | as first published (field classes, no grassland, unfixed K), for reference | 1,721.4 |

Extended checks (stated tolerances for rounded inputs):

| check | paper | value |
|---|---|---|
| rho(tape C, S2 record-median NDVI), 14 sites | Section 3.1 | -0.93 |
| S1 / E1 / S0 at Welimada (Upper Uma Oya sub-catchment) | Table 2 | 2,109 / 1,780 / 254 |
| S1 curve 90% (500 bootstrap curves, seed 5) | Section 3.2 | 1,698-2,672 |
| reconciled level, 5-50-95% of accepted structures | Table S5 main row | 1,045-1,381-1,830 |
| acceptance rate; P(level >= 2,073) | Table S5 | 3.7%; 0.4% |

The reconciled level uses `structure_runs.npz`, `rantambe_draws.npz` (deposit / 23 yr / Brune TE) and
`welimada_table2.csv`, with the analysis's seed and draw order.

Runs in a few seconds (about 5 s with the bootstrap); no Earth Engine account needed. Engine arithmetic: gauge-pinned SCS runoff, MUSLE per subbasin
over the 1991-2014 storm-day bins (6 h storms), sediment shared within subbasins by runoff^1.12 x K LS C P. Only
K LS C P changes between runs. The carbon fix: OpenLandMap stores organic carbon in units of 5 g/kg (percent = stored/2);
the engine as frozen read stored/50, so K is corrected cell by cell by EPIC_C(OC/2)/EPIC_C(OC/50).

## Methods summary

- **Sites.** 7 pairs, each a managed member (A) and a less-disturbed member (B), 60-630 m apart, chosen in the field
  where access was given. "Undisturbed" (wild) means less disturbed or more protected, not pristine. Two pairs are not
  clean farm/undisturbed contrasts: House (sites 7-8, 650-750 m outside the basin divide) and Bomburu (site 11 is a
  mature turpentine, *Syncarpia glomulifera*, plantation by field identification, not a farm). Site 4 is a
  eucalypt-type plantation regrown since c. 2000 (Landsat NDVI). All 14 sites enter the C calibration.
- **Cover tape.** 30 m tape, a point every 1 m: P plant (height cm), L litter (depth cm), M mulch (depth cm),
  BS bare soil, R rock. Site 12: 26 points (four abandoned at a wasp nest).
- **Slake test.** 18 air-dried surface peds per site in a field slake kit, stability classes 1-6 (Herrick et al. 2001).
- **NIR.** Handheld AgroCares scanner, one read at 0, 10, 20 and 30 cm in a small pit face, field-moist. Values are the
  vendor's global-calibration predictions, not laboratory chemistry. No bulk density was measured.
- **Photos.** One downward ground photo per tape point; upward canopy photos where there was canopy. Phone orientation
  (gravity vector) sorted up from down, then every frame at the canopied sites was checked by eye (42 frames at
  sites 6, 8, 10 and 13 re-labelled, 20 Oct 2026); canopy % = non-sky share; ground classes by colour and texture
  (see `cover_photos.csv` header for the classifier and its errors).
- **Field C.** Product of the AH 703 sub-factors (Renard et al. 1997): canopy 1 - Fc exp(-0.1 H) (H fall height, ft);
  plants > 10 cm as low canopy falling from their own height; ground exp(-0.035 x ground cover %), ground cover =
  low plants + litter + mulch + rock.
- **Satellite NDVI.** Every clear Sentinel-2 L2A pass 2023-01-01 to 2026-10-11 (Cloud Score+ cs_cdf >= 0.6), NDVI at
  the 10 m pixel over the site, record median (98-147 passes per site). Google Earth Engine.
- **Calibration.** ln C_field = a + b NDVI_median by least squares, n = 14: a = 0.3404, b = -3.8457, R2 = 0.57 on ln C.
  Apply only with NDVI clamped to the calibration range 0.407-0.848.

## Privacy and what is withheld

- **Positions are generalised.** Most sites are on private smallholdings. Each site is published as the centre of the
  0.002-degree grid cell (~221 m x 221 m, ~4.9 ha) that contains it: the published point is within ~160 m of the
  plot (median shift 90 m, max 122 m). Rounding to 0.001 degree (+-55 m) would leave a 30 m tape plot identifiable on
  imagery, because a 0.001-degree cell (~1.2 ha) is about the size of one smallholding in this landscape; a
  0.002-degree cell (~4.9 ha) spans several holdings. Pair members may share a cell. Elevations are rounded to 10 m, slope to 1 degree and aspect to 10 degrees
  so that terrain values cannot pin a site back down inside its cell. Satellite values (NDVI) were read at the true
  positions before generalisation and are published as values.
- **Withheld:** exact GPS fixes (site, scan and photo), photo EXIF (GPS, time, device), the scanner's serial number and
  order ids, raw vendor exports, field sheet images, names of landholders (never recorded) and of field staff.
- **Not part of this data set:** any workshop participant data (intake forms, agency data shared by participants,
  surveys). None was used.
- **Photos** are re-encoded from pixels only (no EXIF/XMP/GPS), downsized to <= 2000 px, renamed by site and sequence.
  All 629 were checked on contact sheets for people: only the surveyors' feet, legs and shadows appear. Two frames
  that showed the handwritten tape sheet (filed as ground photos by the orientation sorter: site 10 down 32, site 11
  down 31) are left out; their numbers are gaps. They are not in the per-site photo means; nor are four repeats or copies of a point photo (`in_site_means`).

## Data dictionary

Missing values are blank. Never 0 for "not measured". Text encoding UTF-8; lines starting with `#` are header notes.

### data/sites.csv
| column | unit | description |
|---|---|---|
| site | - | site number 1-14 |
| pair | - | pair name (place name of the pair; House = pair by a house) |
| member | - | managed (A) / undisturbed (B); site 11: managed (A): plantation |
| land_use_code | - | field-day code: farm / wild / plantation |
| label_19oct | - | land use or cover as found, with the decided labels for sites 4 and 11 |
| vegetation_class | - | cover class used by the cover model (vegetables, tea, broadleaf_forest, eucalyptus) |
| clean_pair | - | yes = inside the basin and managed member cultivated (crop or tea); reason if no or flagged |
| date | YYYY-MM-DD | survey date |
| lat_gen, lon_gen | degrees (WGS84) | GENERALISED position: centre of the 0.002-degree cell holding the site |
| in_basin | - | inside the Uma Oya divide (False for House, 650-750 m outside) |
| subbasin | - | subbasin id on the model's 90 m flow graph (blank outside) |
| elev_gps_m | m | median phone-GPS altitude of the site's photos, rounded to 10 m |
| elev_glo30_m | m | Copernicus GLO-30 DEM at the site, rounded to 10 m |
| slope30_deg, aspect30_deg | degrees | GLO-30 30 m, 3x3 window; rounded to 1 and 10 degrees |
| wc_class_used, wc_name_used | - | ESA WorldCover class of the site's 90 m cell as used by the model (7 cells corrected from photos) |
| tape_points | count | tape points recorded |
| canopy_pct | % | canopy cover from upward photos (0 where no canopy) |
| plant_pct, litter_pct, rock_pct, bare_pct | % of tape points | P, L+M, R, BS shares |
| C_tape | - | field cover factor (AH 703 sub-factor product) |
| ndvi_s2_record_median | NDVI | Sentinel-2 record median at the site's 10 m pixel |
| slake_peds | count | peds tested |
| slake_mean | class 1-6 | mean slake stability class |
| nir_depths | cm | depths scanned |
| surface_oc_pct_nir | % | NIR-predicted surface organic carbon (field-moist) |
| photos_down, photos_up | count | ground and canopy photos |
| flag | - | caveats |

### data/tape_points.csv
site; point (1-30, as written); cover (P, L, M, BS, R); plant_height_cm (cm, P only); depth_cm (cm, litter or mulch
depth, L and M only); note (transcription notes; "unclear" = hard to read on the sheet photo, value as typed).

### data/slake_peds.csv
site; pair; member (A/B); land_use (field-day code); ped (1-18); stability_class (1 falls apart in water ... 6 stable).

### data/nir_scans.csv
site; pair; member; land_use; depth_cm (0 surface, 10, 20, 30); date; ph_water (pH, 1:1 water); om_pct (% organic
matter); oc_pct (% organic carbon, = om/~1.6 in the vendor model); ecec_meq100g (meq/100 g); ca_exch_ppm,
mg_exch_ppm (mg/kg); fe_total_gkg, al_total_gkg (g/kg); moisture_pct (%); qc (flag). All vendor predictions.

### data/nir_carbon_stocks.csv
site; pair; land_use; mean_oc_0_30_pct (%); oc_stock_t_ha (t C/ha, 0-30 cm, bulk density ASSUMED 1.2 t/m3).

### data/cover_photos.csv
site; photo_file (path in this package); date; look (up/down); tilt_deg (degrees from vertical); canopy_pct,
canopy_pct_pale (% non-sky, up photos); green_pct, litter_pct, bare_pct, rock_pct (% of valid pixels, down photos);
valid_pct (% of the central crop left after masking tape, feet and deep shadow); in_site_means (False for repeats or
copies of a point photo).

### data/cover_photos_sites.csv
site; pair; land_use; n_down, n_up, n_other (photo counts); green_pct_mean, green_pct_sd, litter_pct_mean,
bare_pct_photo_mean, rock_pct_mean (%, down photos); canopy_pct_mean, canopy_pct_sd, canopy_pct_pale_mean
(%, up photos); n_points; P, L, M, BS, R (tape counts); covered_pct, bare_pct, plant_pct (% of tape points).

### calibration/site_c_calibration.csv
site; pair; land_use; ndvi_median (Sentinel-2 record median); c_field (tape C); c_fit (curve); c_oldcurve (van der
Knijff MODIS curve at the same NDVI); field_over_oldcurve (ratio).

### calibration/site_cover_factors.csv
Per site: inputs (canopy, fall height, cover shares, field and OpenLandMap OC %, slake), the sub-factors cc,
sc_plants, sc_litter, k_ratio (EPIC carbon term, field over OpenLandMap at stored/2), c_field, c_satellite (dashboard
C at the 90 m cell), and left_*_pct (share of bare-soil erosion left after each protective layer).

## Licences

- `data/`, `calibration/`, `photos_cover_2026.zip`, documentation: **CC-BY-4.0** (`LICENSE`).
  `ndvi_*` values contain modified Copernicus Sentinel data (2023-2026); `oc_openlandmap_pct` values are from
  OpenLandMap (CC-BY-SA 4.0, Hengl 2018).
- `model/` code: **MIT** (`model/engine_2dcdd09/LICENSE`).
- `model/` rasters and storm aggregates carry their sources' terms: see `model/LICENSES.md`
  (OpenLandMap-derived K -> CC-BY-SA 4.0; MERIT Hydro used under ODbL 1.0; storm-frequency aggregates derived from
  Irrigation Department of Sri Lanka gauges, credited, no records).
- No Planet, EarthDEM or other commercially licensed imagery is included.

## Third-party data credits
Copernicus Sentinel-2 (ESA); Copernicus GLO-30 DEM (ESA, DLR, Airbus); ESA WorldCover 2021 (Zanaga et al. 2022);
OpenLandMap soil layers (Hengl 2018); MERIT Hydro (Yamazaki et al. 2019); NASA SRTM; CHIRPS (Funk et al. 2015);
MODIS MOD13Q1 (Didan 2015); Hansen Global Forest Change (Hansen et al. 2013); Irrigation Department of Sri Lanka
(aggregated storm frequencies, Badulu Oya runoff calibration); NASA IMERG. Earth Engine (Gorelick et al. 2017).

## References
- Herrick, J.E. et al. (2001) Field soil aggregate stability kit for soil quality and rangeland health evaluations. Catena 44, 27-35.
- Renard, K.G. et al. (1997) Predicting soil erosion by water: RUSLE. USDA Agriculture Handbook 703.
- Williams, J.R. (1975) Sediment-yield prediction with universal equation using runoff energy factor. ARS-S-40.
- Williams, J.R. (1995) The EPIC model. In: Computer Models of Watershed Hydrology.
- Van der Knijff, J.M., Jones, R.J.A., Montanarella, L. (2000) Soil erosion risk assessment in Europe. EUR 19044.

## Contact
Sam Anderson, University of Arizona. Contact via the Zenodo record.
