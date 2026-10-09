-- Kariyer "Menajer Masası" (13 §4): import geçmişi, kullanıcı etiketleri, kısa liste, sezon kayıtları, spoiler tercihi.
-- Canlı tablolar (career_player_data ...) önceki davranışla aynı kalır; her import / elle güncelleme ayrıca bir snapshot olarak saklanır.

CREATE TABLE career_snapshot (
  id              BIGSERIAL PRIMARY KEY,
  career_id       BIGINT NOT NULL REFERENCES career(id) ON DELETE CASCADE,
  taken_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  season          INT,
  transfer_window TEXT,
  source          TEXT NOT NULL CHECK (source IN ('IMPORT', 'MANUAL', 'OCR')),
  player_count    INT NOT NULL DEFAULT 0,
  note            TEXT
);
CREATE INDEX ix_career_snapshot ON career_snapshot (career_id, id DESC);

CREATE TABLE career_snapshot_player (
  snapshot_id    BIGINT NOT NULL REFERENCES career_snapshot(id) ON DELETE CASCADE,
  base_player_id BIGINT NOT NULL,
  overall        SMALLINT NOT NULL,
  potential      SMALLINT NOT NULL,
  team_id        INT,
  free_agent     BOOLEAN NOT NULL DEFAULT false,
  PRIMARY KEY (snapshot_id, base_player_id)
);
CREATE INDEX ix_csp_player ON career_snapshot_player (base_player_id, snapshot_id);

ALTER TABLE career ADD COLUMN spoiler_mode TEXT NOT NULL DEFAULT 'SHOW' CHECK (spoiler_mode IN ('SHOW', 'SCOUT_RANGE'));
ALTER TABLE career ADD COLUMN wage_budget_eur BIGINT;
ALTER TABLE career ADD COLUMN window_ends_on DATE;
ALTER TABLE career ADD COLUMN challenge_id TEXT;

CREATE TABLE career_player_tag (
  career_id      BIGINT NOT NULL REFERENCES career(id) ON DELETE CASCADE,
  base_player_id BIGINT NOT NULL,
  tag            TEXT NOT NULL CHECK (tag IN ('LOCKED', 'ROTATION', 'DEVELOPING', 'FOR_SALE', 'LOAN_OK')),
  PRIMARY KEY (career_id, base_player_id)
);

CREATE TABLE career_shortlist (
  career_id      BIGINT NOT NULL REFERENCES career(id) ON DELETE CASCADE,
  base_player_id BIGINT NOT NULL,
  note           TEXT,
  max_fee_eur    BIGINT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (career_id, base_player_id)
);

CREATE TABLE career_season_record (
  career_id       BIGINT NOT NULL REFERENCES career(id) ON DELETE CASCADE,
  season          INT NOT NULL,
  league_position INT,
  trophies        TEXT[] NOT NULL DEFAULT '{}',
  top_scorer_id   BIGINT,
  note            TEXT,
  PRIMARY KEY (career_id, season)
);
