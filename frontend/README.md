# Frontend Module
This folder contains the Android application and other frontend-related assets.

## Early deployment: hosted API and APK

The Render blueprint in `render.yaml` deploys the complete FastAPI backend,
including location/terrain APIs and the migrated Crop Advisor endpoint. The
deployed app uses one `BACKEND_BASE_URL` automatically for all of these
features; farmers do not enter a service URL or API key. The deployed Android
app uses the Render-hosted model and does not depend on a running Colab runtime.

Before deploying, push the intended code and data files to the Git branch
connected to Render. The backend needs the datasets under `backend/data`.
Render reads the Python version from `backend/.python-version` and installs
`backend/requirements.txt`.

In the Render dashboard, set these environment variables for the service:

- `EE_PROJECT`: the Google Cloud project enabled for Earth Engine.
- `EE_SERVICE_ACCOUNT_JSON`: the complete service-account JSON as a secret.
  Ensure that account has Earth Engine access to the required datasets.
- `CROP_ADVISOR_API_KEY`: a long random key used by the backend and injected
  into the app at build time.

Never commit the Earth Engine credentials, API key, Maps key, or signing
credentials. The local `backend/service_account.json` is gitignored and must
remain untracked. The Android Maps key is read from `local.properties`; restrict
it to this app's package/signing certificate and the Maps SDK.

After deployment, check `https://YOUR-SERVICE.onrender.com/health`. It should
return `{"status":"ok"}`. This health check also verifies Earth Engine
initialization, so an error requires inspecting the Render logs and fixing the
project/service-account setup before testing the app.

Set the same HTTPS Render URL and API key in the build environment, then build
a private test APK from the repository root in PowerShell:

```powershell
$env:BACKEND_BASE_URL = "https://YOUR-SERVICE.onrender.com/"
$env:CROP_ADVISOR_API_KEY = "THE-SAME-KEY-CONFIGURED-IN-RENDER"
.\gradlew.bat :frontend:app:assembleDebug
```

The APK is at
`frontend\app\build\outputs\apk\debug\app-debug.apk`. Install it on a test
device. App screens use the configured Render URL automatically, and Crop
Advisor sends the configured key automatically. Release builds reject missing
or non-HTTPS backend configuration. The repository has no release signing
configuration; create a signed release APK through Android Studio only after
setting up a private signing key.

**Prototype security limitation:** the Crop Advisor key is compiled into the
APK, where it can be extracted. It is not a production secret or robust user
authentication. Limit distribution to trusted testers, monitor API usage, and
rotate the key in Render and in every newly built APK if it is exposed. A
production release should replace this shared-key scheme with user
authentication and server-side authorization.

The free Render service may sleep after inactivity, causing cold starts, and
uses ephemeral disk for caches. It is suitable for a no-cost early demo, not
reliable always-on service.

The Google Maps API key is injected locally by the Gradle secrets plugin from
`local.properties` and is included in the APK. Restrict it to this app's
package/signing certificate and the Maps SDK; do not commit the properties file.

`backend_1.ipynb` remains available for model development and experimentation.
Its temporary Colab/ngrok URL is not used by the deployed APK. Notebook edits do
not automatically update Render; port desired model changes into
`backend/crop_advisor.py`, test them, then push and redeploy.

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
Crop Advisor-generated candidate sets. Both candidate sequences use the highest
climate-ranked recommendations; the second sequence is illustrative and does
not enforce crop-category diversity. The prototype does not validate
crop-family breaks, pest-cycle interruption, or whether successive candidate
planting windows form a feasible multi-season rotation. Treat these as
candidates to review, not planting recommendations.

Farm history, soil-test results, water access, constraints, and priorities are
optional. A farmer-provided soil pH is passed to the Crop Advisor API and can
replace the coarse SoilGrids pH value in its existing crop pH suitability factor.
Other entered nutrients are retained as profile context but are not used in
scoring because the current crop model has no matching nutrient scoring inputs.
The API response reports which pH source it used. The current crop and
economics datasets are scoped to the Uma Oya / Sri Lankan prototype and should
not be treated as globally representative.

## My Farm

My Farm stores one active farm's coordinates, optional field boundary, existing
farm-profile details, and soil-test point records in a local Room database on
the Android device. With no boundary, the field-model panel prompts the farmer
to draw one. For a saved boundary, the app requests `/farm-terrain` from the
backend. Earth Engine clips a Sentinel-2 surface-reflectance composite to that
polygon and samples USGS SRTM GL1 elevation for a bounded height grid. Android
renders the imagery over an OpenGL ES terrain mesh with polygon-clipped
textures, raised soil-colored sides, orbit, zoom, and long-press sample entry.
No Google Map tiles, map labels, or markers are rendered in this model.

The SRTM source is approximately 30 m and is not fine enough to represent
small within-field features. Vertical relief is automatically exaggerated so
subtle elevation differences are visible; it is a visualization, not a
survey-grade elevation product. A valid Earth Engine deployment is required to
load the model. Field area is calculated from the saved outline. Editing the
location or boundary retains soil-test records, and the screen warns when
saved sample points fall outside the current outline.

Farmers can long-press inside the outline to add a dated sample with location,
depth, pH, nitrogen, phosphorus, potassium, and test source. These records are
currently informational and do not feed crop scoring. Crop Advisor continues
to use the separate farm-profile pH field as its optional manual override.
Farm data is device-local and is not synced to the backend or another device.

## Agro-climatic location profile

The map selection screen requests `/location-profile` for the selected pin.
The backend returns an administrative district from FAO GAUL 2025 and elevation
sampled from USGS SRTM GL1 (approximately 30 m). Pin changes are debounced and
previous lookups are cancelled in the app. A Sri Lankan agro-ecological zone
(AEZ) or rainfall regime is not inferred from broad coordinate boxes: no
authoritative AEZ boundary layer is currently connected, so the UI explicitly
shows the zone as not mapped. The API returns unavailable values for locations
outside the configured Sri Lanka district lookup coverage.

Farmers can optionally outline a field on the satellite map (3–50 boundary
points, up to 5,000 ha). The app sends its GeoJSON polygon with the Crop Advisor
request; the backend uses Earth Engine region means for elevation, SoilGrids
pH, CHIRPS rainfall, ERA5-Land weather, and WaPOR ET; it keeps the existing
pin-only behavior when no boundary is supplied. The displayed field area is
approximate.
Weather datasets have kilometre-scale native pixels, so a boundary does not
provide within-field weather detail beyond those source resolutions. Restart or
redeploy the backend when updating it to this version; no new API key or Earth
Engine permission is required beyond the existing service configuration.