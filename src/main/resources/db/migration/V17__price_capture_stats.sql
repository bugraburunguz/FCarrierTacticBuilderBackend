CREATE TABLE price_capture_stat (
  day                   DATE PRIMARY KEY,
  recorded              INT NOT NULL DEFAULT 0,
  skipped_unknown_item  INT NOT NULL DEFAULT 0,
  skipped_no_resource   INT NOT NULL DEFAULT 0
);
