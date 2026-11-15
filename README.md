# NASA Crop Monitor

NASA Crop Monitor is an Android decision-support prototype that helps farmers
explore crop choices and rotation strategies using farm context, climate
information, and Earth-observation data. It combines location and field mapping
with crop suitability information to make relevant environmental signals easier
to understand and use in planning.

## Problem statement

Farmers around the world face shifting temperatures, changing rainfall,
water shortages, extreme weather, and declining soil health. These pressures
make it difficult to choose crop rotations that protect fields, conserve water,
and support long-term resilience. This project explores a decision-support tool
that brings together NASA Earth observations, local soil information, crop
characteristics, and farmer priorities so farmers can compare rotation
strategies and make more informed decisions as conditions change.

## Challenges this tool addresses

- **Climate uncertainty:** Presents historical climate patterns and forecast
  information to help assess whether a crop may fit local conditions.
- **Water planning:** Surfaces rainfall and soil-moisture context to support
  water-aware crop planning.
- **Soil and field context:** Lets farmers save farm details, outline a field,
  view terrain, and record soil-test information.
- **Crop and rotation decisions:** Helps explore crop recommendations and
  alternative crops for a following season.
- **Hard-to-access environmental data:** Brings location-specific earth and
  climate data into a mobile interface instead of requiring farmers to
  interpret raw datasets themselves.

## Features

- Android app for farm setup, field mapping, crop advice, and rotation planning.
- Crop recommendations with climate-resilience and historical-profit ranking
  options.
- Farm terrain visualization and location context.
- Local storage for a farm profile and soil-test records.
- Backend APIs for crop recommendations, location profiles, and field terrain.

For detailed deployment and demo instructions, see
[frontend/README.md](frontend/README.md).

## Languages, tools, and data sources

| Category | Technologies |
|---|---|
| Android app | Kotlin, Jetpack Compose, Room |
| Backend | Python, FastAPI |
| Climate and geospatial processing | Google Earth Engine |
| NASA POWER | Used in the crop-fit and climate-trend research notebooks for gridded weather data and model exploration; it is not the live source for the deployed crop-advisor endpoint. |
| NASA SRTM | USGS SRTM GL1 elevation data, accessed through Earth Engine, supports elevation profiles and field terrain visualization. |
| Other Earth Engine datasets | The current backend uses sources including CHIRPS rainfall, ERA5-Land weather, SoilGrids soil pH, and WaPOR evapotranspiration. |

### AI-assisted development tools

- ChatGPT
- Claude
- Gemini

## Android APK

**Install App(apk):** https://drive.google.com/drive/folders/1CBBTgwZkPvXeO4sTx4_84sHHvBT4iKqs?usp=drive_link

## Future work: machine-learning analysis

Further work is planned to develop and evaluate machine-learning approaches
that can improve crop and climate analysis as more suitable data and training
resources become available. One current challenge is the lack of access to a
GPU for training machine-learning models, which limits the size and speed of
experiments. Future work can investigate lighter-weight models, efficient
training strategies, and access to suitable compute resources, while validating
results with local agricultural data.

link: 
[machine-learning future-work analysis](https://drive.google.com/file/d/16IfSxgktz7HJwzQqDi5aSsEWFL1S747M/view?usp=sharing).

## Contributors

- Rashmika Thathsara
- Rashmika Prabod
- Dr. Sam Anderson
- Venuka Dinujaya
- Pasindu Sirithunga
- Kaveesha Anuhas
