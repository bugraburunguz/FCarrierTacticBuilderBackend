-- Kadro hareketleri (ana kayda göre): oyuncunun oyunun ana kaydındaki (temel katalog) kulübü, kariyer satırı yazılırken kopyalanır.
-- Kariyer okumaları katalog tablosuna (player_rating) dokunmaz (mimari kural); değer bir sonraki içe aktarma/tazelemede dolar. Veri silinmez.
ALTER TABLE career_player ADD COLUMN base_club_id integer;
