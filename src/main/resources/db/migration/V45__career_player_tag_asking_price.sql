-- KARIYER-UX FAZ 5 (15 §7.2): satış planı kalıcılığı. Etiket (FOR_SALE / LOAN_OK) + istenen bedel yeter; ayrı tablo yok (Q3).
ALTER TABLE career_player_tag ADD COLUMN asking_price_eur BIGINT NULL CHECK (asking_price_eur IS NULL OR asking_price_eur >= 0);
