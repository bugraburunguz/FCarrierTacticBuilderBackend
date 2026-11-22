-- FCA-31: MODELED değerleme (POT, değer) kadro sürümüyle arşivlenir. En yeni sürüm canlı player_valuation'ı okur; eski sürüm kariyeri arşivi.
-- SADECE EKLEME: mevcut satırlar silinmez, değiştirilmez.
CREATE TABLE player_valuation_archive (
  squad_version_id BIGINT   NOT NULL REFERENCES squad_version(id) ON DELETE CASCADE,
  base_player_id   BIGINT   NOT NULL REFERENCES base_player(id),
  game_version_id  SMALLINT NOT NULL REFERENCES game_version(id),
  potential        SMALLINT,
  value_eur        BIGINT,
  wage_eur         BIGINT,
  model_version    TEXT,
  PRIMARY KEY (squad_version_id, base_player_id)
);

-- Geri doldurma: FCA-27'den sonra arşivlenmiş sürümlerin değerlemesi o an ayrılmadı; bugünkü canlı değerleme en iyi tahmindir
-- (arşivden önce kariyerler zaten canlıyı okuyordu). Arşivi olmayan sürüm (en yeni) doldurulmaz.
INSERT INTO player_valuation_archive (squad_version_id, base_player_id, game_version_id, potential, value_eur, wage_eur, model_version)
SELECT s.id, v.base_player_id, v.game_version_id, v.potential, v.value_eur, v.wage_eur, v.model_version
  FROM squad_version s
  JOIN player_valuation v ON v.game_version_id = s.game_version_id AND v.source = 'MODELED' AND v.owner_user_id IS NULL
  JOIN base_player b ON b.id = v.base_player_id AND b.owner_career_id IS NULL
 WHERE EXISTS (SELECT 1 FROM player_rating_archive a WHERE a.squad_version_id = s.id);
