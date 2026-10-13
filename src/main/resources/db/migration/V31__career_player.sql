-- E3-1 (13 §3.1): kariyerin tek doğruluk kaynağı. SADECE EKLEME: mevcut tablolar (career_player_data, career_player_rating, base_player, player_rating) değişmez/silinmez.
-- Doldurma ve okuma geçişi E3-2'de (geri alınabilir: eski tablolar yerinde kalır).

ALTER TABLE career ADD COLUMN engine_version TEXT NOT NULL DEFAULT 'v3';
-- Her içe aktarma/elle düzenlemede artar; önbellek ve öneri sonuçlarının anahtarıdır.
ALTER TABLE career ADD COLUMN data_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE career_player (
  career_id          BIGINT   NOT NULL REFERENCES career(id) ON DELETE CASCADE,
  player_id          BIGINT   NOT NULL REFERENCES base_player(id) ON DELETE CASCADE,
  as_of              DATE     NOT NULL,
  team_id            INTEGER,
  free_agent         BOOLEAN  NOT NULL DEFAULT FALSE,
  loan_from_team_id  INTEGER,
  loan_until         DATE,
  contract_until     DATE,
  wage_eur           BIGINT,
  release_clause_eur BIGINT,
  value_eur          BIGINT,
  value_source       TEXT CHECK (value_source IN ('LE', 'MODELED', 'MANUAL')),
  squad_role         TEXT,
  form               SMALLINT,
  injury_until       DATE,
  -- Kiralıkta (başka kulüpten bize ya da bizden dışarı) oyuncu ilk kadroya alınamaz; sakatlık modellenince genişler.
  available          BOOLEAN GENERATED ALWAYS AS (loan_from_team_id IS NULL) STORED,
  dob                DATE,
  height_cm          SMALLINT,
  weight_kg          SMALLINT,
  body_type          TEXT,
  preferred_foot     CHAR(1) CHECK (preferred_foot IN ('R', 'L')),
  weak_foot          SMALLINT,
  skill_moves        SMALLINT,
  accelerate         TEXT CHECK (accelerate IN ('EXPLOSIVE', 'MOSTLY_EXPLOSIVE', 'CONTROLLED_EXPLOSIVE', 'CONTROLLED', 'CONTROLLED_LENGTHY', 'MOSTLY_LENGTHY', 'LENGTHY')),
  accelerate_source  TEXT CHECK (accelerate_source IN ('GAME', 'DERIVED')),
  ovr                SMALLINT NOT NULL,
  pot                SMALLINT,
  positions          TEXT[]   NOT NULL,
  -- 34 değer, sıra = fitengine Attr enum sırası (Attr.values()); sıra sözleşmesi test ile korunur (E3-2).
  attrs              SMALLINT[] NOT NULL,
  playstyles         JSONB    NOT NULL DEFAULT '{}'::jsonb,
  -- {"41": 2}: LE role1..9; kod % 100 = rol, kod >= 100 → Role++ (2), değilse Role+ (1)
  role_familiarity   JSONB    NOT NULL DEFAULT '{}'::jsonb,
  hidden_traits      TEXT[],
  run_style1         SMALLINT NOT NULL DEFAULT 0,
  run_style2         SMALLINT NOT NULL DEFAULT 0,
  face_stats         SMALLINT[],
  PRIMARY KEY (career_id, player_id)
);
CREATE INDEX ix_career_player_team ON career_player (career_id, team_id);
CREATE INDEX ix_career_player_free ON career_player (career_id) WHERE free_agent;

-- Kullanıcının kayıtlı taktikleri (5 slot, replika bağlama E3-9'da). Şimdilik yalnız şema.
CREATE TABLE user_tactic (
  id                 BIGSERIAL PRIMARY KEY,
  user_id            BIGINT   NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  career_id          BIGINT   REFERENCES career(id) ON DELETE CASCADE,
  slot               SMALLINT NOT NULL CHECK (slot BETWEEN 1 AND 5),
  name               TEXT     NOT NULL,
  formation          TEXT     NOT NULL,
  build_up           TEXT,
  defensive_approach TEXT,
  line_height        SMALLINT,
  slots              JSONB    NOT NULL DEFAULT '[]'::jsonb,
  ea_code            TEXT,
  source_replica_id  TEXT
);
CREATE UNIQUE INDEX ux_user_tactic_slot ON user_tactic (user_id, COALESCE(career_id, 0), slot);
