-- KARIYER-UX FAZ 6a-E: anlık görüntü anındaki kullanıcı kadrosu (user_player_state oyuncuları). Zaman çizelgesi dünya oyuncu sayısı yerine
-- kadro sayısını ve bir önceki görüntüye göre giren/çıkan oyuncuları gösterir. Eski görüntülerde NULL = bilinmiyor (uydurulmaz).
ALTER TABLE career_snapshot ADD COLUMN squad_player_ids BIGINT[];
