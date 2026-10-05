-- Premium içe aktarma: kariyer bazlı oyuncu/takım verisi (Live Editor dökümünden)
CREATE TABLE career_import (
  career_id    BIGINT PRIMARY KEY REFERENCES career(id) ON DELETE CASCADE,
  imported_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  player_count INT NOT NULL,
  team_count   INT NOT NULL
);

CREATE TABLE career_player_data (
  career_id      BIGINT NOT NULL REFERENCES career(id) ON DELETE CASCADE,
  base_player_id BIGINT NOT NULL REFERENCES base_player(id),
  overall        SMALLINT NOT NULL,
  potential      SMALLINT NOT NULL,
  team_id        INT,
  free_agent     BOOLEAN NOT NULL DEFAULT false,
  PRIMARY KEY (career_id, base_player_id)
);
CREATE INDEX ix_cpd_team ON career_player_data(career_id, team_id);
CREATE INDEX ix_cpd_free ON career_player_data(career_id) WHERE free_agent;

CREATE TABLE career_team (
  career_id            BIGINT NOT NULL REFERENCES career(id) ON DELETE CASCADE,
  team_id              INT NOT NULL,
  name                 TEXT NOT NULL,
  league_name          TEXT,
  gender               SMALLINT,
  free_agent_pool      BOOLEAN NOT NULL DEFAULT false,
  overall              SMALLINT,
  attack               SMALLINT,
  midfield             SMALLINT,
  defence              SMALLINT,
  build_up_play        SMALLINT,
  defensive_depth      SMALLINT,
  youth_development    SMALLINT,
  domestic_prestige    SMALLINT,
  international_prestige SMALLINT,
  popularity           SMALLINT,
  club_worth           BIGINT,
  profitability        SMALLINT,
  rival_team_id        INT,
  PRIMARY KEY (career_id, team_id)
);
