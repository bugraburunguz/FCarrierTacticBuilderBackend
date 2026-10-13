-- BE-BUG-01: eski fiyat gözlemleri promo kartların fiyatını base karta (ea_asset_id) yazmıştı, tradepile/watchlist ve
-- LAST_SALE değerleri de market fiyatı sayılmıştı. Veri geri kazanılamayacak kadar yanlış eşlendiği için (Buğra onayı) silinir.
TRUNCATE price_obs, price_agg;
