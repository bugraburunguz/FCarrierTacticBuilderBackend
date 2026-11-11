-- FCA-23 (Maç Merkezi Faz A): rakip raporu için takım başına diziliş, duran top atıcıları ve kaptan; kullanıcı takımı.
-- Her içe aktarmada yeniden yazılır (son export'un görüntüsü). positions oyunun mevki kodları (0 GK … 27 LW, sağ/sol ayrı),
-- roles oyunun ham rol değerleri (çözülmedi). Atıcı/kaptan base_player kimliği; FK yok (tablo her içe aktarmada yeniden yazılır,
-- ad okumada çözülür; oyuncu silinirse ad boş gelir). Böylece base_player silinirken bu tablo taranmaz.
ALTER TABLE career ADD COLUMN user_team_id INTEGER;

CREATE TABLE career_team_match_info (
  career_id              BIGINT    NOT NULL REFERENCES career(id) ON DELETE CASCADE,
  team_id                INTEGER   NOT NULL,
  formation_name         TEXT,
  formation_positions    SMALLINT[],
  formation_roles        INTEGER[],
  captain_id             BIGINT,
  penalty_taker_id       BIGINT,
  free_kick_taker_id     BIGINT,
  left_corner_taker_id   BIGINT,
  right_corner_taker_id  BIGINT,
  matchday_overall       SMALLINT,
  matchday_attack        SMALLINT,
  matchday_midfield      SMALLINT,
  matchday_defence       SMALLINT,
  PRIMARY KEY (career_id, team_id)
);
