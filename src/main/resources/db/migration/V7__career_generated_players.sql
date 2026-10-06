-- Kariyerde üretilmiş (regen/altyapı) oyuncular: kataloğa değil, ilgili kariyere aittir
ALTER TABLE base_player ADD COLUMN owner_career_id BIGINT REFERENCES career(id) ON DELETE CASCADE;
ALTER TABLE base_player ADD COLUMN career_source_id BIGINT;
CREATE UNIQUE INDEX ux_base_player_career_source ON base_player(owner_career_id, career_source_id) WHERE owner_career_id IS NOT NULL;
CREATE INDEX ix_base_player_owner ON base_player(owner_career_id) WHERE owner_career_id IS NOT NULL;
