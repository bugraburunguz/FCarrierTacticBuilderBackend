-- Kariyer içe aktarmasında gerçek oyuncuların güncel mevki/özellik/playstyle değerleri (antrenman, gelişim)
CREATE TABLE career_player_rating (LIKE player_rating INCLUDING DEFAULTS);
ALTER TABLE career_player_rating DROP COLUMN id;
ALTER TABLE career_player_rating ADD COLUMN id BIGSERIAL PRIMARY KEY;
ALTER TABLE career_player_rating ADD COLUMN career_id BIGINT NOT NULL REFERENCES career(id) ON DELETE CASCADE;
CREATE UNIQUE INDEX ux_career_player_rating ON career_player_rating(career_id, base_player_id, game_version_id);
