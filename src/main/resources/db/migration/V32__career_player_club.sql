-- E3-2: kariyer oyuncusunun katalog kulübü (gösterim/lig için). LE takımı (team_id) kariyer takım tablosuna, club_id katalog kulübüne çözülür;
-- LE'de olmayan (içe aktarılmamış) oyuncularda club_id katalog kulübüdür, team_id boştur.
ALTER TABLE career_player ADD COLUMN club_id INTEGER;
-- LE oyun verisi (career_player_data satırı) olan oyuncu = kayıt dosyasında canlı durumu olan; anlık görüntü ve masa yalnız bunları kapsar.
ALTER TABLE career_player ADD COLUMN live BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX ix_career_player_club ON career_player (career_id, club_id);
