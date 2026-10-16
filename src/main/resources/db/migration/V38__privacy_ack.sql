-- PROD-PREP 3: aydınlatma metni bilgilendirme onayı (açık rıza değil). Kayıtta zorunlu; mevcut kullanıcılarda boş kalır
-- ve bir sonraki girişte tek seferlik bilgilendirme bandı gösterilir. Veri silinmez.
ALTER TABLE app_user ADD COLUMN privacy_ack_at timestamptz;
ALTER TABLE app_user ADD COLUMN privacy_ack_version integer;
