-- Kart görseli kalıcı önbelleği (EA reyting görseli, sunucu üzerinden): kartlar değişmez, var olan görsel için kaynağa yeniden gidilmez.
-- missing = kaynakta yok; fetched_at'ten 7 gün sonra yeniden denenir. Yalnız istenen kartlar yazılır.
CREATE TABLE card_image (
  ea_id        BIGINT PRIMARY KEY,
  content_type TEXT,
  bytes        BYTEA,
  missing      BOOLEAN NOT NULL DEFAULT false,
  fetched_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
