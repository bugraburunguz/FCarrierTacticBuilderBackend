-- V3-05c: SBC "Yeni" / "Değişti" rozetlerinin verisi. Yalnız sütun ekleme; veri silinmez.
-- first_seen_at: set ilk kez görüldüğünde. Mevcut satırlar için updated_at (eski kayıtlar "Yeni" görünmesin).
-- content_changed_at: içerik özeti (content_hash) değiştiğinde; mevcut satırlarda NULL.
ALTER TABLE sbc_set ADD COLUMN first_seen_at TIMESTAMPTZ;
UPDATE sbc_set SET first_seen_at = updated_at;
ALTER TABLE sbc_set ALTER COLUMN first_seen_at SET NOT NULL;
ALTER TABLE sbc_set ALTER COLUMN first_seen_at SET DEFAULT now();
ALTER TABLE sbc_set ADD COLUMN content_changed_at TIMESTAMPTZ;
