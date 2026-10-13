-- BE-BUG-06: içerik değişmese de katkı geldikçe son görülme zamanı ve bitiş güncellenir (süresi dolan içeriği emekliye ayırmak için).
ALTER TABLE sbc_set ADD COLUMN last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE objective_group ADD COLUMN last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE evolution ADD COLUMN last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now();
