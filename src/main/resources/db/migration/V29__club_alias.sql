-- Kulüp alias tablosu: eşleşmeyen ad/lig/cinsiyet → kanonik club satırı. Tohum: seed/club-aliases.json (ClubAliasSeedLoader).
CREATE TABLE club_alias (
  id         SERIAL PRIMARY KEY,
  alias_name TEXT     NOT NULL,
  league_id  INTEGER  REFERENCES league(id),
  gender     SMALLINT CHECK (gender IN (0, 1)),
  ea_team_id INTEGER,
  club_id    INTEGER  NOT NULL REFERENCES club(id) ON DELETE CASCADE,
  source     TEXT     NOT NULL,
  verified   BOOLEAN  NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX ux_club_alias ON club_alias (lower(alias_name), COALESCE(league_id, 0), COALESCE(gender, 2));
