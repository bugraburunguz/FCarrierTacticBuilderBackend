-- BE-SEC-06: e-posta doğrulaması. Mevcut kullanıcılar doğrulanmış sayılır; yeni kullanıcılar doğrulama açıksa false başlar.
ALTER TABLE app_user ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT true;

CREATE TABLE email_verification_token (
  token_hash TEXT PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_email_verification_user ON email_verification_token (user_id);
