-- OPS-02: Redis kaldırıldı. Yenileme ve eklenti bağlama token'ları Postgres'te (deploy'da oturumlar düşmesin).
-- auth_refresh_token: used_at doluysa token tüketilmiştir; satır expires_at'e kadar "kullanıldı" hatırası olarak kalır (yeniden kullanım tespiti).
CREATE TABLE auth_refresh_token (
  token_hash  TEXT PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  expires_at  TIMESTAMPTZ NOT NULL,
  used_at     TIMESTAMPTZ
);
CREATE INDEX ix_auth_refresh_token_user ON auth_refresh_token (user_id);
CREATE INDEX ix_auth_refresh_token_expires ON auth_refresh_token (expires_at);

-- ut_link_token: token düz metin saklanmaz (SHA-256 hex).
CREATE TABLE ut_link_token (
  token_hash  TEXT PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  mode        TEXT NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_ut_link_token_user ON ut_link_token (user_id);
CREATE INDEX ix_ut_link_token_expires ON ut_link_token (expires_at);
