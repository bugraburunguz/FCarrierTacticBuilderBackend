CREATE TABLE dataset_version (
  id              BIGSERIAL PRIMARY KEY,
  scope           VARCHAR(16) NOT NULL CHECK (scope IN ('CATALOG', 'CAREER')),
  career_id       BIGINT NULL REFERENCES career (id) ON DELETE CASCADE,
  game_version_id SMALLINT NOT NULL REFERENCES game_version (id),
  version_no      INT NOT NULL,
  source          VARCHAR(24) NOT NULL,
  imported_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  label           VARCHAR(80),
  change_count    INT NOT NULL DEFAULT 0,
  is_full         BOOLEAN NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX ux_dataset_version_scope ON dataset_version (scope, COALESCE(career_id, 0), game_version_id, version_no);

CREATE TABLE player_version_diff (
  id             BIGSERIAL PRIMARY KEY,
  version_id     BIGINT NOT NULL REFERENCES dataset_version (id) ON DELETE CASCADE,
  base_player_id BIGINT NOT NULL REFERENCES base_player (id),
  change_type    VARCHAR(16) NOT NULL,
  ovr_old        SMALLINT,
  ovr_new        SMALLINT,
  pot_old        SMALLINT,
  pot_new        SMALLINT,
  accel_old      VARCHAR(16),
  accel_new      VARCHAR(16),
  positions_old  VARCHAR(40),
  positions_new  VARCHAR(40),
  club_old       INT,
  club_new       INT,
  attr_diff      JSONB,
  ps_diff        JSONB,
  UNIQUE (version_id, base_player_id)
);
CREATE INDEX ix_pvd_player ON player_version_diff (base_player_id, version_id DESC);
