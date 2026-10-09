-- UT bilgi tabanı (03 §4): global (kişisel olmayan) kart/SBC/objective/evo/fiyat tanımları + katkıcı konsensüsü + ingestion işleri.

CREATE TABLE card_design (
  id          SERIAL PRIMARY KEY,
  code        TEXT UNIQUE NOT NULL,
  frame_shape TEXT NOT NULL DEFAULT 'shield',
  layers      JSONB NOT NULL DEFAULT '[]',
  text_color  TEXT NOT NULL DEFAULT '#000000',
  accent_color TEXT NOT NULL DEFAULT '#000000',
  rating_color TEXT NOT NULL DEFAULT '#000000',
  foil        TEXT NOT NULL DEFAULT 'none' CHECK (foil IN ('none', 'shine', 'holo'))
);

CREATE TABLE ut_promo (
  id              SERIAL PRIMARY KEY,
  game_version_id SMALLINT NOT NULL REFERENCES game_version(id),
  code            TEXT NOT NULL,
  name            TEXT NOT NULL,
  ea_rarity_id    INT,
  starts_at       TIMESTAMPTZ,
  ends_at         TIMESTAMPTZ,
  card_design_id  INT REFERENCES card_design(id),
  UNIQUE (game_version_id, code)
);

CREATE TABLE ut_item (
  id               BIGSERIAL PRIMARY KEY,
  game_version_id  SMALLINT NOT NULL REFERENCES game_version(id),
  ea_asset_id      BIGINT NOT NULL,
  ea_resource_id   BIGINT NOT NULL,
  base_player_id   BIGINT REFERENCES base_player(id),
  promo_id         INT REFERENCES ut_promo(id),
  name             TEXT NOT NULL,
  rating           SMALLINT NOT NULL,
  position         TEXT NOT NULL,
  alt_positions    TEXT[] NOT NULL DEFAULT '{}',
  club_id          INT,
  league_id        INT,
  nation_id        INT,
  card_type        TEXT NOT NULL DEFAULT 'NORMAL' CHECK (card_type IN ('NORMAL', 'ICON', 'HERO', 'EVO')),
  is_rare          BOOLEAN NOT NULL DEFAULT false,
  face_stats       JSONB NOT NULL DEFAULT '{}',
  attrs            JSONB,
  playstyles       JSONB NOT NULL DEFAULT '{}',
  roles_plus       JSONB NOT NULL DEFAULT '{}',
  weak_foot        SMALLINT,
  skill_moves      SMALLINT,
  accelerate       TEXT,
  untradeable_only BOOLEAN NOT NULL DEFAULT false,
  source           TEXT NOT NULL CHECK (source IN ('EA_SITE', 'EA_STATIC', 'CONTRIB', 'ADMIN', 'PARTNER')),
  status           TEXT NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('PENDING', 'CONFIRMED', 'RETIRED')),
  content_hash     TEXT,
  released_at      TIMESTAMPTZ,
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (game_version_id, ea_resource_id)
);
CREATE INDEX ix_ut_item_rating ON ut_item (game_version_id, rating DESC);
CREATE INDEX ix_ut_item_links ON ut_item (league_id, nation_id, club_id);
CREATE INDEX ix_ut_item_asset ON ut_item (ea_asset_id);

CREATE TABLE sbc_set (
  id               BIGINT PRIMARY KEY,
  game_version_id  SMALLINT NOT NULL REFERENCES game_version(id),
  name             TEXT NOT NULL,
  description      TEXT,
  category         TEXT NOT NULL DEFAULT 'OTHER',
  repeatable       INT,
  refresh_interval_hours INT,
  starts_at        TIMESTAMPTZ,
  ends_at          TIMESTAMPTZ,
  reward           JSONB NOT NULL DEFAULT '[]',
  status           TEXT NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('PENDING', 'CONFIRMED', 'RETIRED')),
  content_hash     TEXT NOT NULL,
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_sbc_set_ends ON sbc_set (game_version_id, ends_at);

CREATE TABLE sbc_challenge (
  id                BIGINT PRIMARY KEY,
  set_id            BIGINT NOT NULL REFERENCES sbc_set(id) ON DELETE CASCADE,
  name              TEXT NOT NULL,
  formation         TEXT,
  kind              TEXT NOT NULL DEFAULT 'SQUAD' CHECK (kind IN ('SQUAD', 'ITEM_SCORE')),
  requirements      JSONB NOT NULL DEFAULT '[]',
  item_score_target INT,
  reward            JSONB,
  content_hash      TEXT NOT NULL
);
CREATE INDEX ix_sbc_challenge_set ON sbc_challenge (set_id);

CREATE TABLE objective_group (
  id              BIGINT PRIMARY KEY,
  game_version_id SMALLINT NOT NULL REFERENCES game_version(id),
  name            TEXT NOT NULL,
  game_mode       TEXT,
  starts_at       TIMESTAMPTZ,
  ends_at         TIMESTAMPTZ,
  reward          JSONB,
  objectives      JSONB NOT NULL DEFAULT '[]',
  status          TEXT NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('PENDING', 'CONFIRMED', 'RETIRED')),
  content_hash    TEXT NOT NULL
);

CREATE TABLE evolution (
  id              BIGINT PRIMARY KEY,
  game_version_id SMALLINT NOT NULL REFERENCES game_version(id),
  name            TEXT NOT NULL,
  cost            JSONB,
  requirements    JSONB NOT NULL DEFAULT '{}',
  levels          JSONB NOT NULL DEFAULT '[]',
  starts_at       TIMESTAMPTZ,
  ends_at         TIMESTAMPTZ,
  status          TEXT NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('PENDING', 'CONFIRMED', 'RETIRED')),
  content_hash    TEXT NOT NULL
);

-- Ham fiyat gözlemleri (aylık bölüm; kalan satırlar varsayılan bölümde toplanır)
CREATE TABLE price_obs (
  ut_item_id     BIGINT NOT NULL,
  platform       TEXT NOT NULL CHECK (platform IN ('CONSOLE', 'PC')),
  observed_at    TIMESTAMPTZ NOT NULL,
  kind           TEXT NOT NULL CHECK (kind IN ('BIN', 'LAST_SALE', 'BID')),
  value          INT NOT NULL CHECK (value > 0),
  contributor_id BIGINT NOT NULL
) PARTITION BY RANGE (observed_at);
CREATE TABLE price_obs_default PARTITION OF price_obs DEFAULT;
CREATE INDEX ix_price_obs_item ON price_obs (ut_item_id, platform, observed_at);

CREATE TABLE price_agg (
  ut_item_id BIGINT NOT NULL,
  platform   TEXT NOT NULL,
  bucket     TIMESTAMPTZ NOT NULL,
  p10        INT,
  p50        INT,
  min_bin    INT,
  samples    INT NOT NULL,
  PRIMARY KEY (ut_item_id, platform, bucket)
);

CREATE TABLE ut_rules (
  id          TEXT PRIMARY KEY,
  payload     JSONB NOT NULL,
  verified    BOOLEAN NOT NULL DEFAULT false,
  source_note TEXT
);

-- Katkıcılar (08 D-20): bayrak açıkken kullanıcı PREMIUM haklarını alır; değişiklikler denetim tablosuna yazılır.
CREATE TABLE contributor (
  id        BIGSERIAL PRIMARY KEY,
  user_id   BIGINT UNIQUE NOT NULL REFERENCES app_user(id),
  trust     INT NOT NULL DEFAULT 5,
  banned    BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE contributor_audit (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  action     TEXT NOT NULL CHECK (action IN ('GRANTED', 'REVOKED')),
  actor      TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Katkı olayları ve konsensüs: aynı natural_key için ≥2 bağımsız katkıcı aynı hash'i gönderirse CONFIRMED (03 §3)
CREATE TABLE contrib_event (
  id             BIGSERIAL PRIMARY KEY,
  contributor_id BIGINT NOT NULL REFERENCES contributor(id),
  kind           TEXT NOT NULL,
  natural_key    TEXT NOT NULL,
  content_hash   TEXT NOT NULL,
  payload        JSONB NOT NULL,
  received_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_contrib_key ON contrib_event (kind, natural_key, content_hash);

CREATE TABLE contrib_queue (
  id          BIGSERIAL PRIMARY KEY,
  kind        TEXT NOT NULL,
  natural_key TEXT NOT NULL,
  status      TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'REJECTED')),
  candidates  JSONB NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_contrib_queue_open ON contrib_queue (kind, natural_key) WHERE status = 'OPEN';

-- BUG-11: kullanıcı capture'ından öğrenilen EA kimlik eşleşmeleri öneri olarak tutulur, onaylanınca kalıcı yazılır
CREATE TABLE ea_id_suggestion (
  id         BIGSERIAL PRIMARY KEY,
  kind       TEXT NOT NULL CHECK (kind IN ('LEAGUE', 'NATION', 'CLUB')),
  ea_id      INT NOT NULL,
  name       TEXT NOT NULL,
  votes      INT NOT NULL DEFAULT 0,
  voters     BIGINT[] NOT NULL DEFAULT '{}',
  applied    BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (kind, ea_id, name)
);

CREATE UNIQUE INDEX ux_league_ea_id ON league (ea_league_id) WHERE ea_league_id IS NOT NULL;
CREATE UNIQUE INDEX ux_nationality_ea_id ON nationality (ea_nation_id) WHERE ea_nation_id IS NOT NULL;

-- BUG-09: ingestion koşuları kalıcı; kesilen koşu kaldığı offset'ten devam eder
CREATE TABLE ingestion_run (
  id          BIGSERIAL PRIMARY KEY,
  provider    TEXT NOT NULL,
  status      TEXT NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING', 'DONE', 'FAILED')),
  next_offset INT NOT NULL DEFAULT 0,
  total       INT,
  created     INT NOT NULL DEFAULT 0,
  updated     INT NOT NULL DEFAULT 0,
  attempts    INT NOT NULL DEFAULT 0,
  last_error  TEXT,
  started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at TIMESTAMPTZ
);

-- Çözücü ve extension için tek-kullanımlık bağlama (EXT-01) Redis'te tutulur; burada yalnız ayar yok.
