-- ACADEMY-a: kariyerin altyapı takımı (export'tan veriyle bulunur, her içe aktarmada yeniden hesaplanır; NULL = bulunamadı) ve
-- career_youthplayers alanları. Alanların anlamı doğrulanmadı (verified:false); arayüz yalnız güvenle söylenebileni gösterir.
ALTER TABLE career ADD COLUMN academy_team_id INTEGER;

CREATE TABLE career_youth_player (
  career_id           BIGINT   NOT NULL REFERENCES career(id) ON DELETE CASCADE,
  base_player_id      BIGINT   NOT NULL REFERENCES base_player(id) ON DELETE CASCADE,
  months_in_squad     SMALLINT,
  player_tier         SMALLINT,
  potential_variance  SMALLINT,
  swing_low_potential SMALLINT,
  PRIMARY KEY (career_id, base_player_id)
);
