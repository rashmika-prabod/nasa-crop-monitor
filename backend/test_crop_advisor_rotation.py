import unittest
from unittest.mock import patch

import pandas as pd

import crop_advisor


class RotationPlantingWindowTests(unittest.TestCase):
    def setUp(self):
        self.start = pd.Timestamp("2025-01-01")
        self.weather = pd.DataFrame(
            {
                "rain_mm": [10.0 * month for month in range(1, 13)],
                "temp_mean_c": [20.0] * 12,
            },
            index=pd.date_range(self.start, periods=12, freq="MS"),
        )

    @staticmethod
    def crop(crop_id, season_month):
        return {
            "crop_id": crop_id,
            "crop_name": crop_id,
            "crop_family": "test family",
            "crop_group": "test group",
            "season_start_month": season_month,
            "season_end_month": season_month,
            "rain_cycle_ideal_min_mm": 50.0,
            "rain_cycle_ideal_max_mm": 300.0,
            "temp_ideal_min_c": 15.0,
            "temp_ideal_max_c": 30.0,
        }

    def rank(self, crops, weather, n_start_months=4):
        with (
            patch.object(crop_advisor, "CROPS", pd.DataFrame(crops)),
            patch.object(crop_advisor, "crop_duration", return_value=(121.6, False)),
            patch.object(
                crop_advisor,
                "suitability",
                side_effect=lambda crop, stats, static, irrigated: (0.9, "", set()),
            ),
            patch.object(
                crop_advisor,
                "expected_economics",
                return_value={
                    "net": 100.0,
                    "gross": 120.0,
                    "confidence": "test",
                    "basis": "test",
                },
            ),
        ):
            options = {
                "return_all": True,
                "selection_metric": "suitability",
            }
            if n_start_months is not None:
                options["n_start_months"] = n_start_months
            return crop_advisor.rank_crops(weather, self.start, {}, **options)

    def test_four_month_window_limits_planting_dates_not_crop_cycle_weather(self):
        candidates = [
            self.crop("april_crop", 4),
            self.crop("may_crop", 5),
        ]

        ranked = self.rank(candidates, self.weather)

        self.assertEqual(ranked.crop_id.tolist(), ["april_crop"])
        self.assertEqual(ranked.iloc[0].plant_in, "Apr 2025")
        # A crop planted in April is scored through July, after the last allowed
        # planting month (April), rather than being scored on April's rain alone.
        self.assertEqual(ranked.iloc[0].forecast_rain_mm, 220.0)
        self.assertEqual(ranked.iloc[0].harvest_by, "Jul 2025")

    def test_crop_is_excluded_when_full_cycle_exceeds_forecast_coverage(self):
        short_forecast = self.weather.loc[:"2025-04-01"]

        ranked = self.rank([self.crop("april_crop", 4)], short_forecast)

        self.assertTrue(ranked.empty)

    def test_general_crop_advisor_default_still_searches_twelve_months(self):
        december_crop = [self.crop("december_crop", 12)]
        extended_weather = pd.DataFrame(
            {
                "rain_mm": [
                    10.0 * (month % 12 + 1)
                    for month in range(24)
                ],
                "temp_mean_c": [20.0] * 24,
            },
            index=pd.date_range(self.start, periods=24, freq="MS"),
        )

        ranked = self.rank(december_crop, extended_weather, n_start_months=None)

        self.assertEqual(ranked.iloc[0].plant_in, "Dec 2025")


class RotationRecommendationApiTests(unittest.TestCase):
    def call_api(self, rotation_window_months=None):
        current_month = pd.Timestamp.today().normalize().replace(day=1)
        history = pd.DataFrame(
            {
                "rain_mm": [100.0] * 60,
                "temp_mean_c": [25.0] * 60,
            },
            index=pd.date_range(end=current_month, periods=60, freq="MS"),
        )
        future_index = pd.date_range(
            current_month + pd.DateOffset(months=1),
            periods=crop_advisor.FUTURE_MONTHS,
            freq="MS",
        )
        future_weather = pd.DataFrame(
            {"yhat": [100.0] * len(future_index)},
            index=future_index,
        )
        forecast = {
            "future": future_weather,
            "mae": 12.0,
            "skill_vs_climatology": 0.1,
            "model": "test model",
        }
        ranked_crop = pd.DataFrame([{
            "crop_id": "test_crop",
            "crop": "Test Crop",
            "suitability": 0.9,
            "main_issue": "",
            "notes": "",
            "risk_adj_return_lkr_ha": 100.0,
            "net_return_lkr_ha": 120.0,
            "profit_basis": "test",
            "profit_confidence": "test",
            "plant_in": "Feb 2027",
            "harvest_by": "Jun 2027",
            "forecast_rain_mm": 220.0,
            "forecast_temp_c": 25.0,
        }])
        crops = pd.DataFrame([{
            "crop_id": "test_crop",
            "crop_family": "Fabaceae",
            "crop_group": "Legume",
            "water_requirement": "Moderate",
            "rain_cycle_ideal_min_mm": 100.0,
            "rain_cycle_ideal_max_mm": 300.0,
            "temp_ideal_min_c": 20.0,
            "temp_ideal_max_c": 30.0,
        }])
        with (
            patch.object(crop_advisor, "_validated_field_boundary", return_value=(None, None, None)),
            patch.object(crop_advisor, "get_location_data", return_value=(history, {"district": "Test"})),
            patch.object(crop_advisor, "forecast_all", return_value={
                "rain_mm": forecast,
                "temp_mean_c": forecast,
            }),
            patch.object(crop_advisor, "rank_crops", return_value=ranked_crop) as rank,
            patch.object(crop_advisor, "recommendation_backtest", return_value=None),
            patch.object(crop_advisor, "CROPS", crops),
            patch.object(crop_advisor, "crop_duration", return_value=(120.0, False)),
        ):
            result = crop_advisor.advisor_api_result(
                6.0,
                80.0,
                rotation_window_months=rotation_window_months,
            )
        return result, rank

    def test_rotation_api_returns_four_month_climate_only_candidates(self):
        result, rank = self.call_api(rotation_window_months=4)

        self.assertEqual(result["plantingWindowMonths"], 4)
        self.assertEqual(
            [recommendation["rankingMode"] for recommendation in result["recommendations"]],
            ["rotation"],
        )
        self.assertEqual(rank.call_count, 1)
        self.assertEqual(rank.call_args.kwargs["n_start_months"], 4)
        self.assertEqual(result["rainfallMaeMm"], 12.0)
        self.assertEqual(result["rainfallSkillVsClimatology"], 0.1)

    def test_default_api_keeps_twelve_month_climate_and_profit_rankings(self):
        result, rank = self.call_api()

        self.assertIsNone(result["plantingWindowMonths"])
        self.assertEqual(
            [recommendation["rankingMode"] for recommendation in result["recommendations"]],
            ["climate", "profit"],
        )
        self.assertEqual(rank.call_count, 2)
        self.assertEqual(rank.call_args_list[0].kwargs["n_start_months"], 12)


if __name__ == "__main__":
    unittest.main()
