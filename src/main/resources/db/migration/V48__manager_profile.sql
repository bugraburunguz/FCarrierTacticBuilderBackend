-- MGR-a: katalog düzeyinde kulüp profili (kariyer yokken kulüp önerisi için) ve kullanıcı başına menajer profili.
-- Kaynak: LE ana menü export'u, teams + leagues + leagueteamlinks (katalog içe aktarması doldurur; yeniden içe aktarmada güncellenir).
--   domestic_prestige, international_prestige, popularity: 1–10 (ligine göreli); youth_development: 0–10;
--   club_worth_eur: export'taki clubworth bin € → €; league_level: leagues.level (1 en üst).
--   transfer_budget_eur: export'ta yok (NULL); öneride tahmin kullanılır (en iyi 16 oyuncunun modellenmiş değeri × %10).
CREATE TABLE club_profile (
  club_id                INTEGER  NOT NULL REFERENCES club(id) ON DELETE CASCADE,
  game_version_id        SMALLINT NOT NULL REFERENCES game_version(id),
  domestic_prestige      SMALLINT,
  international_prestige SMALLINT,
  popularity             SMALLINT,
  transfer_budget_eur    BIGINT,
  club_worth_eur         BIGINT,
  youth_development      SMALLINT,
  league_level           SMALLINT,
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (club_id, game_version_id)
);

-- Kullanıcı başına bir profil; bütün kariyerlerde kullanılır. Değerler enum adları (uygulama doğrular).
CREATE TABLE manager_profile (
  user_id      BIGINT   PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  name         TEXT,
  licence      TEXT     NOT NULL,
  background   TEXT     NOT NULL,
  formation    TEXT,
  style        TEXT     NOT NULL,
  priorities   TEXT[]   NOT NULL DEFAULT '{}',
  difficulty   TEXT     NOT NULL,
  league_id    INTEGER  REFERENCES league(id) ON DELETE SET NULL,
  gender       SMALLINT CHECK (gender IN (0, 1)),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
