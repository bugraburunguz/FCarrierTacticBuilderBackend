-- PROD-PREP 4 (KVKK hesap silme): bilgi tabanı katkıları silinmez, hesapla bağı kaldırılır. Katkıcı kaydı silinince
-- contrib_event / price_obs satırlarının contributor_id'si NULL olur (anonim katkı). Veri silinmez; yalnız NOT NULL kısıtı kalkar.
ALTER TABLE contrib_event ALTER COLUMN contributor_id DROP NOT NULL;
ALTER TABLE price_obs ALTER COLUMN contributor_id DROP NOT NULL;
