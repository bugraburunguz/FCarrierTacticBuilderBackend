-- BE-BUG-18: kariyerin oyun içi tarihi. Yaş, kariyer sorgularında gerçek tarihe değil bu tarihe göre hesaplanır.
-- NULL = bilinmiyor (gerçek tarih kullanılır). LE export v2 context.gameDate ya da elle (PATCH /careers/{id} gameDate) ile dolar.
ALTER TABLE career ADD COLUMN current_game_date DATE;
