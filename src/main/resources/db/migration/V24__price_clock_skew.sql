-- Capture saati sunucudan 48 saatten uzak olan fiyat gözlemleri sunucu saatine alınır ve sayılır.
ALTER TABLE price_capture_stat ADD COLUMN clock_skew INT NOT NULL DEFAULT 0;
