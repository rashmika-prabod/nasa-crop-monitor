import unittest
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

from fastapi import HTTPException

import main
from main import TerrainCoordinate, _validate_terrain_boundary


class FarmTerrainBoundaryTests(unittest.TestCase):
    def test_accepts_small_valid_field(self):
        points = [
            TerrainCoordinate(lat=6.0, lon=80.0),
            TerrainCoordinate(lat=6.0, lon=80.0002),
            TerrainCoordinate(lat=6.0002, lon=80.0002),
            TerrainCoordinate(lat=6.0002, lon=80.0),
        ]
        _validate_terrain_boundary(points)

    def test_normalizes_closed_ring(self):
        points = [
            TerrainCoordinate(lat=6.0, lon=80.0),
            TerrainCoordinate(lat=6.0, lon=80.0002),
            TerrainCoordinate(lat=6.0002, lon=80.0002),
            TerrainCoordinate(lat=6.0002, lon=80.0),
            TerrainCoordinate(lat=6.0, lon=80.0),
        ]
        self.assertEqual(len(_validate_terrain_boundary(points)), 4)

    def test_rejects_too_few_points(self):
        with self.assertRaises(HTTPException) as error:
            _validate_terrain_boundary([
                TerrainCoordinate(lat=6.0, lon=80.0),
                TerrainCoordinate(lat=6.0002, lon=80.0002),
            ])
        self.assertEqual(error.exception.status_code, 422)

    def test_rejects_self_intersecting_field(self):
        with self.assertRaises(HTTPException) as error:
            _validate_terrain_boundary([
                TerrainCoordinate(lat=6.0, lon=80.0),
                TerrainCoordinate(lat=6.0002, lon=80.0002),
                TerrainCoordinate(lat=6.0002, lon=80.0),
                TerrainCoordinate(lat=6.0, lon=80.0001),
            ])
        self.assertEqual(error.exception.status_code, 422)

    @patch("main.urllib.request.urlopen")
    @patch("main.ee.ImageCollection")
    @patch("main.ee.Image")
    @patch("main.ee.Geometry.Polygon")
    def test_endpoint_clips_dem_and_satellite_texture(
        self,
        polygon_factory,
        image_factory,
        collection_factory,
        urlopen,
    ):
        geometry = MagicMock()
        polygon_factory.return_value = geometry
        geometry.bounds.return_value = MagicMock()

        dem = MagicMock()
        image_factory.return_value.select.return_value = dem
        clipped_dem = dem.clip.return_value
        grid = clipped_dem.resample.return_value.reproject.return_value
        grid.sampleRectangle.return_value.getInfo.return_value = {
            "properties": {"elevation": [[12, 13], [14, 15]]}
        }

        collection = collection_factory.return_value
        visualized = (
            collection.filterBounds.return_value
            .filterDate.return_value
            .filter.return_value
            .median.return_value
            .select.return_value
            .visualize.return_value
        )
        satellite = visualized.clip.return_value
        satellite.getThumbURL.return_value = "https://earthengine.example/thumbnail"
        image_response = MagicMock()
        image_response.status = 200
        image_response.__enter__.return_value = image_response
        image_response.read.return_value = b"\x89PNG\r\n\x1a\nterrain"
        urlopen.return_value = image_response

        request = SimpleNamespace(
            app=SimpleNamespace(
                state=SimpleNamespace(ee_initialization_error=None)
            )
        )
        body = main.FarmTerrainRequest(boundary=[
            TerrainCoordinate(lat=6.0, lon=80.0),
            TerrainCoordinate(lat=6.0, lon=80.0002),
            TerrainCoordinate(lat=6.0002, lon=80.0002),
            TerrainCoordinate(lat=6.0002, lon=80.0),
        ])

        with patch("main.ee.Filter.lt", return_value=MagicMock()):
            result = main.get_farm_terrain(request, body)

        dem.clip.assert_called_once_with(geometry)
        visualized.clip.assert_called_once_with(geometry)
        self.assertEqual(result["elevationGrid"], [[12, 13], [14, 15]])
        self.assertEqual(result["bounds"], [80.0, 6.0, 80.0002, 6.0002])
        self.assertTrue(result["texturePngBase64"])


if __name__ == "__main__":
    unittest.main()
