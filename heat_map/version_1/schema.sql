-- Suggested backend schema (PostgreSQL). The front end's DB object maps 1:1 onto these tables.

-- Columns a user may choose to record (the catalog). Add a row here to offer a new measurement.
CREATE TABLE variables (
  key TEXT PRIMARY KEY, label TEXT NOT NULL, unit TEXT,
  valid_min REAL, valid_max REAL, step REAL
);

CREATE TABLE crops (id TEXT PRIMARY KEY, name TEXT NOT NULL, emoji TEXT, color TEXT);

-- What each crop needs. The data scientist edits THESE rows.
-- fit = 1 inside [ideal_min, ideal_max], falling linearly to 0 at `tolerance` outside it.
-- weight = importance of this variable in the "Overall" layer.
CREATE TABLE crop_requirements (
  crop_id TEXT REFERENCES crops(id), variable_key TEXT REFERENCES variables(key),
  ideal_min REAL NOT NULL, ideal_max REAL NOT NULL, tolerance REAL NOT NULL CHECK (tolerance > 0),
  weight REAL NOT NULL DEFAULT 1,
  PRIMARY KEY (crop_id, variable_key)
);

-- Which columns a given field/user has switched on.
CREATE TABLE field_columns (
  field_id UUID NOT NULL, variable_key TEXT REFERENCES variables(key),
  PRIMARY KEY (field_id, variable_key)
);

-- One row per point the user clicked on the map.
CREATE TABLE readings (
  id UUID PRIMARY KEY, field_id UUID NOT NULL,
  taken_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  lat DOUBLE PRECISION NOT NULL, lon DOUBLE PRECISION NOT NULL,
  radius_m REAL NOT NULL CHECK (radius_m > 0)
);

-- One row per (point, variable). value NULL = column recorded but not measured (e.g. broken pH meter).
CREATE TABLE reading_values (
  reading_id UUID REFERENCES readings(id) ON DELETE CASCADE,
  variable_key TEXT REFERENCES variables(key),
  value REAL,
  PRIMARY KEY (reading_id, variable_key)
);
