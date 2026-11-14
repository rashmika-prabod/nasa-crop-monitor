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

The Android build defaults to `https://nasa-crop-monitor.onrender.com/`, so
farmers do not configure a URL. Before building an APK, set the same API key
that is configured in Render in your local PowerShell session, then build from
the repository root:

```powershell
$secureKey = Read-Host 'Enter the Render CROP_ADVISOR_API_KEY' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $env:CROP_ADVISOR_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    .\gradlew.bat :frontend:app:assembleRenderDebug
    if ($LASTEXITCODE -ne 0) { throw "Render APK build failed." }
} finally {
    Remove-Item Env:CROP_ADVISOR_API_KEY -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
}
```

If you use a different backend, also set `BACKEND_BASE_URL` to its HTTPS URL.
Render debug APK builds require an HTTPS URL and all APK builds require an API
key. The two local demo variants described below allow HTTP only for their
private-network endpoints. Ordinary compile tasks can still run without the
key. The key is never prompted from farmers, and must not be committed or
pasted into source files.

The APK is at
`frontend\app\build\outputs\apk\render\debug\app-render-debug.apk`. Install it
on a test device. App screens use the configured Render URL automatically,
and Crop Advisor sends the configured key automatically. Release builds
require an HTTPS backend URL. The repository has no release signing
configuration; create a signed release APK through Android Studio only after
setting up a private signing key.

To make APK builds from Android Studio work without setting a PowerShell
environment variable each time, store the key in your **user-level** Gradle
properties file. On Windows, open or create
`%USERPROFILE%\.gradle\gradle.properties` and add:

```properties
CROP_ADVISOR_API_KEY=your-demo-key
```

Replace the example with the same key configured on the backend. This file is
outside the repository and is not committed. Do not put the key in tracked
project files. Android Studio uses this Gradle property for APK builds; after
adding it, sync the project and run the build again. Alternatively, set the
`CROP_ADVISOR_API_KEY` environment variable in the environment that launches
Gradle.

On startup, the app opens the location picker only when no farm location is
saved. Updating or reinstalling over an existing app can preserve its local
farm database, in which case it opens the saved farm directly; use **My Farm >
Edit Field** to change its location. Clearing the app's storage also shows the
picker next launch, but permanently deletes farm/profile and soil-sample data
stored on that phone.

**Prototype security limitation:** the Crop Advisor key is compiled into the
APK, where it can be extracted. It is not a production secret or robust user
authentication. Limit distribution to trusted testers, monitor API usage, and
rotate the key in Render and in every newly built APK if it is exposed. A
production release should replace this shared-key scheme with user
authentication and server-side authorization.

The free Render service may sleep after inactivity, causing cold starts, and
uses ephemeral disk for caches. It is suitable for a no-cost early demo, not
reliable always-on service.

## Local laptop demo for judges

For an in-person demo, the FastAPI backend can run on the laptop instead of
Colab or Render. Crop Advisor analysis runs automatically when the app calls
`/recommend-crops`; no notebook needs to be open. The first request for a
location fetches historical data from Earth Engine and can take several
minutes. The laptop keeps that location cache in the ignored
`backend\.demo-cache` folder, so later requests can reuse it.
Climate datasets have much coarser pixels than a farm plot: very small drawn
fields use the climate pixel at the field centroid, while larger fields use a
field-area mean.

The laptop needs Python 3.11 or 3.13, internet access, and the ignored
`backend\service_account.json` file with Earth Engine access. In PowerShell
from the repository root, start the API with:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-demo-backend.ps1
```

The script prefers Python 3.11 and falls back to 3.13 if 3.11 is not
installed. It creates a local virtual environment and installs backend
dependencies on the first run, asks for the demo `CROP_ADVISOR_API_KEY` without
echoing it, and starts the server. Keep that PowerShell window open and prevent
the laptop from sleeping for the entire demo. Check
`http://127.0.0.1:8000/health` on the laptop; it should return
`{"status":"ok"}`.

The app accepts HTTP only for loopback and private LAN addresses used by the
local demo variants; deployed backend URLs must use HTTPS.

If you also want a Wi-Fi APK, build it with your private LAN address. Use a
demo-only key, and enter the same key when the backend launcher prompts and
during the APK build; do not put it in `local.properties`, source code, or a
message. The USB helper below builds and installs its APK automatically.

```powershell
$secureKey = Read-Host 'Enter the demo CROP_ADVISOR_API_KEY' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try {
    $env:CROP_ADVISOR_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $env:DEMO_LAN_HOST = '192.168.1.25'
    .\gradlew.bat :frontend:app:assembleDemoWifiDebug
    if ($LASTEXITCODE -ne 0) { throw "Wi-Fi APK build failed." }
    .\gradlew.bat :frontend:app:assembleDemoUsbDebug
    if ($LASTEXITCODE -ne 0) { throw "USB APK build failed." }
} finally {
    Remove-Item Env:CROP_ADVISOR_API_KEY -ErrorAction SilentlyContinue
    Remove-Item Env:DEMO_LAN_HOST -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
}
```

Replace `192.168.1.25` with the laptop's private IPv4 address shown by
`ipconfig` for the Wi-Fi network used by the phones. If the laptop's address
changes, rebuild the Wi-Fi APK with the new address. The APKs are
`frontend\app\build\outputs\apk\demoWifi\debug\app-demoWifi-debug.apk` and
`frontend\app\build\outputs\apk\demoUsb\debug\app-demoUsb-debug.apk`.

**Wi-Fi alternative:** connect every phone and the laptop to the same trusted
private Wi-Fi or laptop hotspot. If Windows asks, mark that network
**Private**. In an elevated PowerShell, allow the backend port only on
Private networks:

```powershell
New-NetFirewallRule -DisplayName "Crop Expert Demo API (Private)" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8000 -Profile Private
```

Install the Wi-Fi APK on the judges' phones and verify the backend is ready
before opening Crop Advisor. Guest Wi-Fi may block devices from reaching one
another; use a private hotspot instead. Do not configure router port
forwarding or expose this demo server to the public internet.

**USB/ADB (recommended at a venue with public Wi-Fi):** the phone talks to the
laptop through USB, so the venue network, client isolation, and the laptop's
changing Wi-Fi address do not matter. Enable Developer options and USB
debugging on each phone, connect it to the laptop, and accept the phone's
"Allow USB debugging" prompt. Start the backend first, then run this from the
repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-demo-usb.ps1
```

The script checks that the backend is healthy and a phone is authorized,
prompts for the same demo API key used by the backend, builds the USB APK,
sets up `adb reverse`, and installs the app on every connected authorized
phone. Keep the backend running and the USB cable attached during the demo.
Android SDK Platform-Tools (`adb`) must be installed; if it is not on `PATH`,
the script checks the standard Android SDK locations.

Before judging, call Crop Advisor once at the intended demo location and leave
the laptop backend running until the calculation finishes. Verify the
recommendations, then reopen that same location for the warm-cache demo.
Earth Engine quota, network speed, or dataset access can still cause failures.
After the event, remove the temporary firewall rule with
`Remove-NetFirewallRule -DisplayName "Crop Expert Demo API (Private)"`.

These are demo APKs: the shared API key is embedded in each APK and can be
extracted. Use a demo-only key, share APKs only with the judges, and rotate the
key after the hackathon. Never copy Earth Engine credentials to a phone or
APK. The laptop must remain awake and on the network; this is not a public,
always-on deployment.

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
backend. Earth Engine samples USGS SRTM GL1 elevation for a bounded height
grid and, when permitted, clips a Sentinel-2 surface-reflectance composite to
the field polygon. Android renders the imagery as a polygon-clipped texture
when available; if Earth Engine thumbnail permission or imagery retrieval
fails, it still renders the shaded elevation mesh with raised soil-colored
sides, orbit, zoom, and long-press sample entry. The backend logs the imagery
failure and reports its unavailable status. No Google Map tiles, map labels,
or markers are rendered in this model.

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