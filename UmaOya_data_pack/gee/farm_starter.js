// NASA data for one field, Uma Oya / Welimada
// Paste into code.earthengine.google.com and hit Run. Charts show up in the Console.

// Site 14 (carrots, Perawela). To use your own field, draw a polygon and use: var field = geometry;
var field = ee.Geometry.Point([80.845, 6.939]).buffer(60).bounds();
Map.centerObject(field, 16);
Map.setOptions('SATELLITE');
Map.addLayer(field, {color: 'yellow'}, 'field');

var start = '2023-01-01', end = '2026-10-01';

// NDVI from HLS (Sentinel-2 + Landsat, 30 m)
function mask(img) {
  return img.updateMask(img.select('Fmask').bitwiseAnd(14).eq(0));  // Fmask bits 1-3: cloud, adjacent, shadow
}
var s30 = ee.ImageCollection('NASA/HLS/HLSS30/v002').filterBounds(field).filterDate(start, end)
  .filter(ee.Filter.stringStartsWith('MGRS_TILE_ID', '44'))  // filterBounds also returns tiles from across the dateline
  .map(mask)
  .map(function (i) { return i.normalizedDifference(['B8A', 'B4']).rename('NDVI').copyProperties(i, ['system:time_start']); });
var l30 = ee.ImageCollection('NASA/HLS/HLSL30/v002').filterBounds(field).filterDate(start, end)
  .map(mask)
  .map(function (i) { return i.normalizedDifference(['B5', 'B4']).rename('NDVI').copyProperties(i, ['system:time_start']); });
var ndvi = s30.merge(l30);
print(ui.Chart.image.series(ndvi, field, ee.Reducer.mean(), 30)
  .setOptions({title: 'NDVI (HLS)', lineWidth: 0, pointSize: 3}));

// Monthly rain, GPM IMERG (mm/hr -> mm/month)
var rain = ee.ImageCollection('NASA/GPM_L3/IMERG_MONTHLY_V07').filterDate(start, end).map(function (i) {
  var hrs = i.date().advance(1, 'month').difference(i.date(), 'hour');
  return i.select('precipitation').multiply(hrs).rename('rain_mm').copyProperties(i, ['system:time_start']);
});
print(ui.Chart.image.series(rain, field.centroid(1), ee.Reducer.mean(), 10000)
  .setChartType('ColumnChart').setOptions({title: 'Rain (mm/month), IMERG'}));

// Surface soil moisture, SMAP L4 (9 km), one value a day
var sm = ee.ImageCollection('NASA/SMAP/SPL4SMGP/008').filterDate('2024-01-01', end)
  .filter(ee.Filter.calendarRange(4, 4, 'hour')).select('sm_surface');
print(ui.Chart.image.series(sm, field.centroid(1), ee.Reducer.first(), 9000)
  .setOptions({title: 'Soil moisture (m3/m3), SMAP'}));

// Elevation and slope, NASADEM
var dem = ee.Image('NASA/NASADEM_HGT/001').select('elevation');
print('elevation, slope', dem.addBands(ee.Terrain.slope(dem)).reduceRegion(ee.Reducer.mean(), field, 30));
