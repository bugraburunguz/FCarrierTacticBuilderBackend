-- V3-05d-c: fiyat gözlemi tekrarı engellenir. Doğal anahtar: katkıcı + kart + platform + tür + fiyat + gözlem zamanı.
-- Bölümlü tabloda benzersiz dizin bölüm anahtarını (observed_at) içerir. Mevcut tekrarlar bu migration'dan ÖNCE onaylı veri silme protokolüyle
-- temizlendi (yerel veritabanı; dry-run → ../backups yedeği → tek transaction); temiz tabloda dizin hata vermez.
CREATE UNIQUE INDEX ux_price_obs_natural ON price_obs (contributor_id, ut_item_id, platform, kind, value, observed_at);
