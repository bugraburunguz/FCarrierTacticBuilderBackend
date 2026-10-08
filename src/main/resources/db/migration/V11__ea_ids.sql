-- EA kimlikleri: SBC şartları kulüp/lig/ülkeyi EA kimliğiyle verir (CLUB_ID, LEAGUE_ID, NATION_ID).
ALTER TABLE club ADD COLUMN ea_team_id INT;
ALTER TABLE league ADD COLUMN ea_league_id INT;
ALTER TABLE nationality ADD COLUMN ea_nation_id INT;
CREATE INDEX ix_club_ea_team_id ON club (ea_team_id);

-- Kulüpler: kariyer içe aktarımındaki takım tablosu EA takım kimliği + ad + lig + cinsiyet taşır.
UPDATE club c
SET ea_team_id = m.team_id
FROM (
  SELECT DISTINCT ON (c2.id) c2.id AS club_id, ct.team_id
  FROM career_team ct
  JOIN club c2 ON lower(c2.name) = lower(ct.name) AND c2.gender = ct.gender
  JOIN league l ON l.id = c2.league_id AND l.name = ct.league_name
  ORDER BY c2.id, ct.team_id
) m
WHERE c.id = m.club_id AND c.ea_team_id IS NULL;

-- Büyük beş lig: EA lig kimlikleri (diğerleri yakalanan kartlardan öğrenilir).
UPDATE league SET ea_league_id = 13 WHERE name = 'Premier League' AND ea_league_id IS NULL;
UPDATE league SET ea_league_id = 53 WHERE name = 'LALIGA EA SPORTS' AND ea_league_id IS NULL;
UPDATE league SET ea_league_id = 19 WHERE name = 'Bundesliga' AND ea_league_id IS NULL;
UPDATE league SET ea_league_id = 31 WHERE name = 'Serie A Enilive' AND ea_league_id IS NULL;
UPDATE league SET ea_league_id = 16 WHERE name = 'Ligue 1 McDonald''s' AND ea_league_id IS NULL;
