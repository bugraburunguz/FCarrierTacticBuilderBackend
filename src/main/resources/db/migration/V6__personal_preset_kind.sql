ALTER TABLE preset DROP CONSTRAINT IF EXISTS preset_kind_check;
ALTER TABLE preset ADD CONSTRAINT preset_kind_check CHECK (kind IN ('STYLE', 'REPLICA', 'PERSONAL'));
