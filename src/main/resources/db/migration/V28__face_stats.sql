-- F10: EA ratings sitesinden gelen resmî yüz istatistikleri (pac, sho, pas, dri, def, phy; kalecide DIV/HAN/KIC/REF/SPD/POS aynı sıra).
-- NULL = resmî değer yok (hesaplanır, FaceStatsService). Eski satırlar sonraki ingestion koşusunda dolar; veri silinmez.
ALTER TABLE player_rating ADD COLUMN face_stats SMALLINT[];
ALTER TABLE career_player_rating ADD COLUMN face_stats SMALLINT[];
