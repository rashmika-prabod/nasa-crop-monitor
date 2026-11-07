# Frontend Module
This folder contains the Android application and other frontend-related assets.

## Hosted API and Crop Advisor

The Android app uses `NetworkConfig.BASE_URL` for telemetry and location
profiles. Set the Gradle property `BACKEND_BASE_URL` when building a shared APK:

```powershell
.\gradlew.bat :frontend:app:assembleDebug "-PBACKEND_BASE_URL=https://YOUR-SERVICE.onrender.com/"
```

The Crop Advisor endpoint was migrated from `backend_1.ipynb` to
`backend/crop_advisor.py`; it serves the forecast, crop-ranking, profit, and
image fields from the same FastAPI service. Its input datasets are packaged
under `backend/data`. Configure `CROP_ADVISOR_API_KEY` as a secret in the
hosting dashboard and enter that value in the Crop Advisor connection panel.
The service uses Earth Engine datasets for the selected coordinates.

`render.yaml` describes a free Render web service. Connect the repository in
Render and deploy the blueprint, then set `EE_SERVICE_ACCOUNT_JSON` to the
Earth Engine service-account JSON in Render's environment settings. Never put
that credential in the repository or APK. The local
`backend/service_account.json` is gitignored and must remain untracked. The
Render free instance sleeps after inactivity, uses
ephemeral disk for its location cache, and can take about a minute to wake; it
is intended for a no-cost demo, not always-on production use.

The Google Maps API key is injected locally by the Gradle secrets plugin from
`local.properties` and is included in the APK. Restrict it to this app's
package/signing certificate and the Maps SDK; do not commit the properties file.

`backend_1.ipynb` remains the analysis notebook, but the shared APK should use
the hosted API rather than a temporary Colab/ngrok URL.

The Crop Advisor offers two ranking modes. **Climate Resilience** ranks
eligible crops by their suitability across the forecast crop cycle, selecting
the best planting window. **Maximum Profit** ranks crops by historical
risk-unadjusted net return per hectare per crop day and does not filter by
climate suitability. These are historical estimates, not guaranteed returns.

Crop photos are served from Cloudinary and cached on the device. The image
banner and Cloudinary derivative use a consistent 16:9 aspect ratio. By default,
the API expects each asset's public ID to be `crops/<crop_id>` (for example,
`crops/carrot`). Assets with a different public ID can be added to the
`CLOUDINARY_PUBLIC_IDS` override map in `backend/crop_advisor.py`. Set
`CLOUDINARY_CLOUD_NAME` to your Cloudinary cloud name. The API generates
optimized image URLs that preserve the full crop photo inside a consistent
banner, filling extra space with an automatically selected background color.
No Cloudinary API secret is needed. When an image is unavailable, the app shows
a crop-name placeholder.

## Rotation planning prototype

The app's Rotation Plans screen compares a farmer-reported baseline with two
Crop Advisor-generated candidate sets. The highest-climate-fit option uses the
climate-ranked candidate list; the mixed-category option chooses highly ranked
candidates from different categories where available. The prototype does not
validate crop-family breaks, pest-cycle interruption, or whether successive
candidate planting windows form a feasible multi-season rotation. Treat these
as candidates to review, not planting recommendations.

Farm history, soil-test results, water access, constraints, and priorities are
optional. A farmer-provided soil pH is passed to the Crop Advisor API and can
replace the coarse SoilGrids pH value in its existing crop pH suitability factor.
Other entered nutrients are retained as profile context but are not used in
scoring because the current crop model has no matching nutrient scoring inputs.
The API response reports which pH source it used. Update the API cell in the
live Colab notebook to accept `soil_ph` and return `soilPHUsed`/`soilPHSource`;
until then, the app will show that the API did not report the source and the
manual pH will not be applied by that old server. The current crop and economics
datasets are scoped to the Uma Oya / Sri Lankan prototype and should not be
treated as globally representative.

## Agro-climatic location profile

The map selection screen requests `/location-profile` for the selected pin.
The backend returns an administrative district from FAO GAUL 2025 and elevation
sampled from USGS SRTM GL1 (approximately 30 m). Pin changes are debounced and
previous lookups are cancelled in the app. A Sri Lankan agro-ecological zone
(AEZ) or rainfall regime is not inferred from broad coordinate boxes: no
authoritative AEZ boundary layer is currently connected, so the UI explicitly
shows the zone as not mapped. The API returns unavailable values for locations
outside the configured Sri Lanka district lookup coverage.