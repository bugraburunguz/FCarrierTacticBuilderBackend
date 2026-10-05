-- ===== Katalog (immutable base) =====
CREATE TABLE game_version (
  id            SMALLINT PRIMARY KEY,
  code          TEXT NOT NULL UNIQUE,
  released_on   DATE
);
CREATE TABLE nationality (id SERIAL PRIMARY KEY, name TEXT UNIQUE NOT NULL);
CREATE TABLE league (id SERIAL PRIMARY KEY, name TEXT NOT NULL UNIQUE, country TEXT, tier SMALLINT);
CREATE TABLE club (
  id SERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  league_id INT REFERENCES league(id),
  UNIQUE (name, league_id)
);

CREATE TABLE base_player (
  id             BIGSERIAL PRIMARY KEY,
  ea_resource_id BIGINT,
  full_name      TEXT NOT NULL,
  dob            DATE,
  nationality_id INT REFERENCES nationality(id),
  height_cm      SMALLINT,
  weight_kg      SMALLINT,
  preferred_foot TEXT CHECK (preferred_foot IN ('Left','Right')),
  UNIQUE (ea_resource_id)
);

CREATE TABLE player_rating (
  id              BIGSERIAL PRIMARY KEY,
  base_player_id  BIGINT NOT NULL REFERENCES base_player(id),
  game_version_id SMALLINT NOT NULL REFERENCES game_version(id),
  club_id         INT REFERENCES club(id),
  overall         SMALLINT NOT NULL,
  positions       TEXT[] NOT NULL,
  accelerate      TEXT,
  weak_foot SMALLINT, skill_moves SMALLINT,
  intl_reputation SMALLINT,
  att_wr TEXT, def_wr TEXT, body_type TEXT,
  acceleration SMALLINT, sprint_speed SMALLINT, agility SMALLINT, balance SMALLINT,
  reactions SMALLINT, ball_control SMALLINT, dribbling SMALLINT, composure SMALLINT,
  vision SMALLINT, short_passing SMALLINT, long_passing SMALLINT, crossing SMALLINT,
  curve SMALLINT, fk_accuracy SMALLINT, finishing SMALLINT, shot_power SMALLINT,
  long_shots SMALLINT, volleys SMALLINT, penalties SMALLINT, att_position SMALLINT,
  heading SMALLINT, def_awareness SMALLINT, standing_tackle SMALLINT, sliding_tackle SMALLINT,
  interceptions SMALLINT, jumping SMALLINT, stamina SMALLINT, strength SMALLINT, aggression SMALLINT,
  gk_diving SMALLINT, gk_handling SMALLINT, gk_kicking SMALLINT, gk_positioning SMALLINT, gk_reflexes SMALLINT,
  playstyles JSONB NOT NULL DEFAULT '{}',
  UNIQUE (base_player_id, game_version_id)
);
CREATE INDEX ix_rating_gv  ON player_rating(game_version_id);
CREATE INDEX ix_rating_pos ON player_rating USING GIN (positions);
CREATE INDEX ix_rating_ps  ON player_rating USING GIN (playstyles);
CREATE INDEX ix_rating_fin ON player_rating(finishing);
CREATE INDEX ix_rating_vis ON player_rating(vision);
CREATE INDEX ix_rating_cro ON player_rating(crossing);
CREATE INDEX ix_rating_acc ON player_rating(acceleration);
CREATE INDEX ix_rating_ovr ON player_rating(game_version_id, overall);

-- owner_user_id NULL'ları için NULLS NOT DISTINCT: MODELED satırı oyuncu+sürüm başına tek kalır (PG15+).
CREATE TABLE player_valuation (
  id              BIGSERIAL PRIMARY KEY,
  base_player_id  BIGINT NOT NULL REFERENCES base_player(id),
  game_version_id SMALLINT NOT NULL REFERENCES game_version(id),
  source          TEXT NOT NULL CHECK (source IN ('MODELED','IMPORT','THIRD_PARTY_SEED')),
  owner_user_id   BIGINT,
  potential       SMALLINT,
  value_eur       BIGINT,
  wage_eur        BIGINT,
  model_version   TEXT,
  created_at      TIMESTAMPTZ DEFAULT now(),
  CONSTRAINT uq_valuation UNIQUE NULLS NOT DISTINCT (base_player_id, game_version_id, source, owner_user_id),
  CONSTRAINT ck_valuation_owner CHECK ((source = 'IMPORT') = (owner_user_id IS NOT NULL))
);
CREATE INDEX ix_val_public ON player_valuation(game_version_id) WHERE source = 'MODELED';
CREATE INDEX ix_val_owner  ON player_valuation(owner_user_id) WHERE source = 'IMPORT';

-- ===== Roller & Taktik =====
-- Not (spec sapması): role.position -> positions TEXT[] (çok-pozisyonlu roller: RB/LB, LM/RM/LW/RW),
-- base_role eklendi (aynı rolün focus varyantlarını gruplar).
CREATE TABLE role (
  id            TEXT PRIMARY KEY,
  base_role     TEXT NOT NULL,
  name          TEXT NOT NULL,
  positions     TEXT[] NOT NULL,
  focus         TEXT NOT NULL,
  attr_weights  JSONB NOT NULL,
  weapons       JSONB NOT NULL,
  playstyles    JSONB NOT NULL,
  accelerate    TEXT[] NOT NULL,
  global_floor  JSONB NOT NULL DEFAULT '{"Stamina":68,"Reactions":60,"Composure":60}',
  movement_tags TEXT[] NOT NULL DEFAULT '{}'
);
CREATE INDEX ix_role_base ON role(base_role);

-- Not (spec sapması): expected_min (IntentFit tagExpected) ve playstyle_weapons eklendi.
CREATE TABLE behavior_tag (
  id                TEXT PRIMARY KEY,
  position          TEXT NOT NULL,
  label_tr          TEXT NOT NULL,
  weapon_attrs      TEXT[] NOT NULL,
  playstyle_weapons TEXT[] NOT NULL DEFAULT '{}',
  expected_min      SMALLINT NOT NULL,
  role_vote         TEXT,
  conflicts         TEXT[] NOT NULL DEFAULT '{}',
  nlg_tr            TEXT,
  accelerate_hint   TEXT,
  sort_order        SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_tag_pos ON behavior_tag(position);

CREATE TABLE preset (
  id           TEXT PRIMARY KEY,
  kind         TEXT CHECK (kind IN ('STYLE','REPLICA')),
  name         TEXT NOT NULL,
  formation    TEXT,
  settings     JSONB,
  signature_tr TEXT,
  role_hints   JSONB,
  slot_tags    JSONB,
  source_note  TEXT
);

CREATE TABLE tactic_rule (
  id          TEXT PRIMARY KEY,
  severity    TEXT NOT NULL CHECK (severity IN ('RISK','WARN','PLUS')),
  title_tr    TEXT NOT NULL,
  message_tr  TEXT NOT NULL,
  fix_tr      TEXT,
  conditions  JSONB NOT NULL,
  enabled     BOOLEAN NOT NULL DEFAULT true
);

-- ===== Kullanıcı & Kariyer (event-sourced) =====
CREATE TABLE app_user (
  id                  BIGSERIAL PRIMARY KEY,
  email               TEXT UNIQUE NOT NULL,
  password_hash       TEXT NOT NULL,
  subscription_type   TEXT NOT NULL DEFAULT 'FREE' CHECK (subscription_type IN ('FREE','PREMIUM')),
  credit_balance      INT NOT NULL DEFAULT 0,
  daily_credit_limit  INT NOT NULL DEFAULT 20,
  last_credit_refresh DATE,
  created_at          TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE subscription (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES app_user(id),
  plan TEXT, status TEXT, started_at TIMESTAMPTZ, expires_at TIMESTAMPTZ,
  auto_renew BOOL, payment_provider TEXT, external_subscription_id TEXT
);
CREATE TABLE credit_transaction (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES app_user(id),
  type TEXT, amount INT, reason TEXT,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX ix_credit_tx_user ON credit_transaction(user_id);

CREATE TABLE career (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES app_user(id),
  game_version_id SMALLINT REFERENCES game_version(id),
  club_id INT REFERENCES club(id),
  season INT,
  budget_eur BIGINT,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX ix_career_user ON career(user_id);

CREATE TABLE user_save_snapshot (
  id BIGSERIAL PRIMARY KEY,
  career_id BIGINT REFERENCES career(id),
  taken_at TIMESTAMPTZ,
  source_file TEXT,
  raw_meta JSONB
);
CREATE TABLE user_player_state (
  id BIGSERIAL PRIMARY KEY,
  career_id BIGINT REFERENCES career(id),
  base_player_id BIGINT REFERENCES base_player(id),
  club_id INT,
  dynamic_potential SMALLINT,
  value_eur BIGINT,
  form SMALLINT,
  on_loan BOOLEAN NOT NULL DEFAULT false,
  acquired_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ,
  UNIQUE (career_id, base_player_id)
);
CREATE TABLE roster_event (
  id BIGSERIAL PRIMARY KEY,
  career_id BIGINT REFERENCES career(id),
  base_player_id BIGINT REFERENCES base_player(id),
  type TEXT CHECK (type IN ('BUY','SELL','LOAN_IN','LOAN_OUT','PROMOTE')),
  fee_eur BIGINT,
  from_club_id INT,
  to_club_id INT,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX ix_roster_event_career ON roster_event(career_id);

INSERT INTO game_version (id, code, released_on) VALUES
  (25, 'FC25', DATE '2024-09-27'),
  (26, 'FC26', DATE '2025-09-26'),
  (27, 'FC27', NULL);
