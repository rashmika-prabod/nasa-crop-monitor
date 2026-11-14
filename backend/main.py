import base64
import logging
import math
import os
import json
import urllib.request
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import List, Optional

import ee
import google.auth
from google.oauth2 import service_account
from fastapi import FastAPI, HTTPException, Query, Request
from pydantic import BaseModel

from crop_advisor import recommend_crops_endpoint

logger = logging.getLogger(__name__)

EE_PROJECT = os.getenv("EE_PROJECT")
EE_CREDENTIALS_FILE = os.getenv("EE_SERVICE_ACCOUNT_FILE") or str(
    Path(__file__).with_name("service_account.json")
)
EE_CREDENTIALS_JSON = os.getenv("EE_SERVICE_ACCOUNT_JSON")


@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        if EE_CREDENTIALS_JSON:
            credentials = service_account.Credentials.from_service_account_info(
                json.loads(EE_CREDENTIALS_JSON),
                scopes=["https://www.googleapis.com/auth/earthengine"],
            )
            project = EE_PROJECT or credentials.project_id
        elif Path(EE_CREDENTIALS_FILE).is_file():
            credentials = service_account.Credentials.from_service_account_file(
                EE_CREDENTIALS_FILE,
                scopes=["https://www.googleapis.com/auth/earthengine"],
            )
            project = EE_PROJECT or credentials.project_id
        else:
            credentials, project = google.auth.default(
                scopes=["https://www.googleapis.com/auth/earthengine"]
            )
            project = EE_PROJECT or project
        if not project:
            raise RuntimeError(
                "Earth Engine project is unset; configure EE_PROJECT or provide "
                "service-account credentials with a project ID."
            )
        ee.Initialize(credentials=credentials, project=project)
        app.state.ee_initialization_error = None
        logger.info("Google Earth Engine initialized")
    except Exception as error:
        app.state.ee_initialization_error = error
        logger.exception("Google Earth Engine initialization failed")
    yield


app = FastAPI(lifespan=lifespan)


@app.get("/")
def root():
    return {
        "service": "NASA Crop Monitor API",
        "message": "Backend is running.",
        "docs": "/docs",
        "health": "/health",
    }


app.add_api_route(
    "/recommend-crops",
    recommend_crops_endpoint,
    methods=["GET"],
    name="recommend_crops",
)


@app.get("/health")
def health(request: Request):
    if request.app.state.ee_initialization_error is not None:
        raise HTTPException(status_code=503, detail="Earth Engine is unavailable")
    return {"status": "ok"}


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


class LocationProfileResponse(BaseModel):
    district: str
    agroZone: str
    elevationMeters: Optional[float] = None
    source: str


class TerrainCoordinate(BaseModel):
    lat: float
    lon: float


class FarmTerrainRequest(BaseModel):
    boundary: List[TerrainCoordinate]


def _validate_terrain_boundary(points: List[TerrainCoordinate]) -> List[TerrainCoordinate]:
    if len(points) > 3 and points[0] == points[-1]:
        points = points[:-1]
    if not 3 <= len(points) <= 50:
        raise HTTPException(status_code=422, detail="A field boundary needs 3 to 50 points.")
    if any(not math.isfinite(point.lat) or not math.isfinite(point.lon) for point in points):
        raise HTTPException(status_code=422, detail="Boundary coordinates must be finite.")
    if any(
        point.lat < -90
        or point.lat > 90
        or point.lon < -180
        or point.lon > 180
        for point in points
    ):
        raise HTTPException(status_code=422, detail="Boundary coordinates are out of range.")

    longitudes = [point.lon for point in points]
    latitudes = [point.lat for point in points]
    if max(longitudes) - min(longitudes) > 2 or max(latitudes) - min(latitudes) > 2:
        raise HTTPException(status_code=422, detail="The field boundary is too large for terrain rendering.")

    latitude = math.radians(sum(latitudes) / len(latitudes))
    x = [math.radians(lon) * 6_371_008.8 * math.cos(latitude) for lon in longitudes]
    y = [math.radians(lat) * 6_371_008.8 for lat in latitudes]
    area = abs(sum(
        x[index] * y[(index + 1) % len(points)] -
        x[(index + 1) % len(points)] * y[index]
        for index in range(len(points))
    )) / 2
    if area < 200 or area > 50_000_000:
        raise HTTPException(
            status_code=422,
            detail="Field area must be between 0.02 and 5,000 hectares.",
        )

    def orientation(a, b, c):
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])

    def on_segment(a, b, point):
        return (
            min(a[0], b[0]) - 1e-6 <= point[0] <= max(a[0], b[0]) + 1e-6
            and min(a[1], b[1]) - 1e-6 <= point[1] <= max(a[1], b[1]) + 1e-6
        )

    vertices = list(zip(x, y))
    for first in range(len(vertices)):
        a, b = vertices[first], vertices[(first + 1) % len(vertices)]
        for second in range(first + 1, len(vertices)):
            if second == first or second == (first + 1) % len(vertices) or (first == 0 and second == len(vertices) - 1):
                continue
            c, d = vertices[second], vertices[(second + 1) % len(vertices)]
            o1, o2 = orientation(a, b, c), orientation(a, b, d)
            o3, o4 = orientation(c, d, a), orientation(c, d, b)
            if (
                (o1 * o2 < 0 and o3 * o4 < 0)
                or (abs(o1) <= 1e-6 and on_segment(a, b, c))
                or (abs(o2) <= 1e-6 and on_segment(a, b, d))
                or (abs(o3) <= 1e-6 and on_segment(c, d, a))
                or (abs(o4) <= 1e-6 and on_segment(c, d, b))
            ):
                raise HTTPException(status_code=422, detail="Field boundary must not cross itself.")
    return points


@app.post("/farm-terrain")
def get_farm_terrain(request: Request, body: FarmTerrainRequest):
    if request.app.state.ee_initialization_error is not None:
        raise HTTPException(
            status_code=503,
            detail="Earth Engine is unavailable. Check the server credentials and configuration.",
        )
    points = _validate_terrain_boundary(body.boundary)

    ring = [[point.lon, point.lat] for point in points]
    ring.append(ring[0])
    geometry = ee.Geometry.Polygon([ring], proj="EPSG:4326", geodesic=False)
    bounds = geometry.bounds(maxError=1)
    try:
        dem = ee.Image("USGS/SRTMGL1_003").select("elevation")
        # Bound the returned height grid while retaining bilinear elevation relief.
        lons = [point.lon for point in points]
        lats = [point.lat for point in points]
        width_m = (max(lons) - min(lons)) * 111_320 * math.cos(
            math.radians(sum(lats) / len(lats))
        )
        height_m = (max(lats) - min(lats)) * 111_320
        scale_m = max(10, max(width_m, height_m) / 120)
        grid = dem.clip(geometry).resample("bilinear").reproject(
            crs=dem.projection().atScale(scale_m)
        )
        elevation_result = grid.sampleRectangle(
            region=bounds,
        ).getInfo()
        elevation_grid = elevation_result["properties"]["elevation"]
        if not elevation_grid or not elevation_grid[0]:
            raise ValueError("Earth Engine returned an empty elevation grid.")
        if not any(
            value is not None and math.isfinite(value)
            for row in elevation_grid
            for value in row
        ):
            mean_elevation = dem.reduceRegion(
                reducer=ee.Reducer.mean(),
                geometry=geometry,
                scale=30,
                maxPixels=4096,
            ).get("elevation").getInfo()
            if mean_elevation is None:
                raise ValueError("No SRTM elevation samples intersect this field.")
            elevation_grid = [[mean_elevation, mean_elevation], [mean_elevation, mean_elevation]]
        if len(elevation_grid) == 1:
            elevation_grid = [elevation_grid[0], elevation_grid[0]]
        if any(len(row) == 1 for row in elevation_grid):
            elevation_grid = [row * 2 if len(row) == 1 else row for row in elevation_grid]

        one_year_ago = datetime.now(timezone.utc) - timedelta(days=365 * 3)
        imagery = (
            ee.ImageCollection("COPERNICUS/S2_SR_HARMONIZED")
            .filterBounds(geometry)
            .filterDate(one_year_ago.date().isoformat(), datetime.now(timezone.utc).date().isoformat())
            .filter(ee.Filter.lt("CLOUDY_PIXEL_PERCENTAGE", 50))
            .median()
            .select(["B4", "B3", "B2"])
            .visualize(min=0, max=3000)
            .clip(geometry)
        )
        thumbnail_url = imagery.getThumbURL({
            "region": bounds,
            "dimensions": 512,
            "format": "png",
        })
        with urllib.request.urlopen(thumbnail_url, timeout=60) as response:
            texture_png = response.read(2_000_001)
            if (
                response.status != 200
                or len(texture_png) > 2_000_000
                or not texture_png.startswith(b"\x89PNG\r\n\x1a\n")
            ):
                raise ValueError("Earth Engine returned an invalid or oversized texture.")
    except Exception as error:
        logger.exception("Earth Engine farm terrain request failed")
        raise HTTPException(
            status_code=502,
            detail="Could not retrieve the clipped field terrain. Please try again.",
        ) from error

    return {
        "bounds": [min(lons), min(lats), max(lons), max(lats)],
        "elevationGrid": elevation_grid,
        "texturePngBase64": base64.b64encode(texture_png).decode("ascii"),
        "elevationSource": "USGS SRTM GL1 · approximately 30 m",
        "imagerySource": "Sentinel-2 surface reflectance · 3-year median",
    }


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


@app.get("/location-profile", response_model=LocationProfileResponse)
def get_location_profile(
    request: Request,
    lat: float = Query(..., ge=-90, le=90),
    lon: float = Query(..., ge=-180, le=180),
):
    if request.app.state.ee_initialization_error is not None:
        raise HTTPException(
            status_code=503,
            detail="Earth Engine is unavailable. Check the server credentials and configuration.",
        )

    try:
        point = ee.Geometry.Point([lon, lat])
        districts = (
            ee.FeatureCollection("FAO/GAUL/2025/level2")
            .filter(ee.Filter.eq("ISO3_CODE", "LKA"))
            .filterBounds(point)
            .limit(1)
        )
        values = ee.Dictionary(
            {
                "elevation": _sample_value(
                    ee.Image("USGS/SRTMGL1_003"), point, "elevation", 30
                ),
                "district": ee.Algorithms.If(
                    districts.size().gt(0),
                    districts.first().get("GAUL2_NAME"),
                    "Unavailable",
                ),
            }
        ).getInfo()
    except Exception as error:
        logger.exception("Earth Engine location profile query failed")
        raise HTTPException(
            status_code=502,
            detail="Could not retrieve location intelligence. Please try again.",
        ) from error

    return LocationProfileResponse(
        district=values.get("district") or "Unavailable",
        agroZone="Not mapped",
        elevationMeters=values.get("elevation"),
        source="District: FAO GAUL 2025 · Elevation: USGS SRTM GL1 (approximately 30 m)",
    )


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
        agroZone="Not mapped",
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
