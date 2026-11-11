-- FCA-22 (Maç Merkezi 1. adım): LE 2.0.5 export'undaki maç notları ve sezon istatistikleri.
-- Maç notu: oyun career_playermatchratinghistory'yi oyuncu başına en çok 15 maçlık kayan pencere olarak tutar; içe aktarmalar boyunca
-- (kariyer, oyuncu, maç tarihi) tekil biriktirilir, hiçbir içe aktarma silmez. position_code oyunun ham mevki kodu (28/29 gibi bilinmeyenler var).
CREATE TABLE career_player_match_rating (
  career_id     BIGINT      NOT NULL REFERENCES career(id) ON DELETE CASCADE,
  player_id     BIGINT      NOT NULL REFERENCES base_player(id) ON DELETE CASCADE,
  match_date    DATE        NOT NULL,
  minutes       SMALLINT,
  position_code SMALLINT,
  rating        SMALLINT    NOT NULL,
  first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (career_id, player_id, match_date)
);

-- Sezon istatistiği: son içe aktarmanın görüntüsü (bütün takımlar, turnuva ayrı). rating_total oyunun "avg" kolonu: toplam puan;
-- maç notu = rating_total / appearances / 10. Tablo export'ta yoksa ya da boşsa önceki görüntü korunur.
CREATE TABLE career_player_season_stat (
  career_id      BIGINT   NOT NULL REFERENCES career(id) ON DELETE CASCADE,
  player_id      BIGINT   NOT NULL REFERENCES base_player(id) ON DELETE CASCADE,
  team_id        INTEGER  NOT NULL,
  competition_id INTEGER  NOT NULL,
  appearances    SMALLINT NOT NULL,
  goals          SMALLINT NOT NULL,
  assists        SMALLINT NOT NULL,
  rating_total   INTEGER  NOT NULL,
  yellow_cards   SMALLINT NOT NULL,
  red_cards      SMALLINT NOT NULL,
  clean_sheets   SMALLINT NOT NULL,
  PRIMARY KEY (career_id, player_id, team_id, competition_id)
);
CREATE INDEX ix_career_season_stat_competition ON career_player_season_stat (career_id, competition_id);
-- base_player silinince (kariyerde üretilen oyuncular) cascade satır başına tam tarama yapmasın.
CREATE INDEX ix_career_match_rating_player ON career_player_match_rating (player_id);
CREATE INDEX ix_career_season_stat_player ON career_player_season_stat (player_id);
