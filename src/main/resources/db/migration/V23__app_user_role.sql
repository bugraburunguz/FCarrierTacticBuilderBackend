-- BE-SEC-07: admin yetkisi paylaşılan anahtar yerine kullanıcı rolüyle (JWT role claim). İlk admin FCAREER_BOOTSTRAP_ADMIN_EMAIL ile atanır.
ALTER TABLE app_user ADD COLUMN role TEXT NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN'));
