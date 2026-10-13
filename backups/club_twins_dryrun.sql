-- Kulüp ikizleri DRY-RUN (yalnız SELECT; veri değiştirmez). Çalıştırma: docker compose exec -T postgres psql -U fcareer -d fcareer -f ...
WITH norm AS (
  SELECT id, name, league_id, gender, ea_team_id,
         regexp_replace(lower(name), '[^a-z0-9]', '', 'g') AS n,
         lower(regexp_replace(regexp_replace(name, '[^A-Za-z0-9 ]', '', 'g'), '(\w)\w*\s*', '\1', 'g')) AS initials
  FROM club
), pairs AS (
  SELECT a.id AS a_id, a.name AS a_name, b.id AS b_id, b.name AS b_name, a.league_id, a.gender,
         CASE WHEN a.ea_team_id IS NOT NULL AND a.ea_team_id = b.ea_team_id THEN 'aynı ea_team_id'
              WHEN a.n = b.n THEN 'normalize aynı ad + aynı lig'
              WHEN a.initials = b.n OR b.initials = a.n THEN 'kısa ad ↔ tam ad (baş harfler)'
              WHEN length(a.n) >= 4 AND length(b.n) >= 4 AND (a.n LIKE '%' || b.n || '%' OR b.n LIKE '%' || a.n || '%') THEN 'ad içerme (aday)'
         END AS reason
  FROM norm a JOIN norm b ON a.id < b.id AND a.league_id IS NOT DISTINCT FROM b.league_id AND a.gender IS NOT DISTINCT FROM b.gender
)
SELECT p.reason, p.a_id, p.a_name, p.b_id, p.b_name, p.league_id, p.gender,
       (SELECT count(*) FROM player_rating WHERE club_id = p.a_id) AS a_rating, (SELECT count(*) FROM player_rating WHERE club_id = p.b_id) AS b_rating,
       (SELECT count(*) FROM career_player_rating WHERE club_id = p.a_id) AS a_career_rating, (SELECT count(*) FROM career_player_rating WHERE club_id = p.b_id) AS b_career_rating,
       (SELECT count(*) FROM career WHERE club_id = p.a_id) AS a_career, (SELECT count(*) FROM career WHERE club_id = p.b_id) AS b_career,
       (SELECT count(*) FROM user_player_state WHERE club_id = p.a_id) AS a_ups, (SELECT count(*) FROM user_player_state WHERE club_id = p.b_id) AS b_ups,
       (SELECT count(*) FROM roster_event WHERE from_club_id = p.a_id OR to_club_id = p.a_id) AS a_roster, (SELECT count(*) FROM roster_event WHERE from_club_id = p.b_id OR to_club_id = p.b_id) AS b_roster
FROM pairs p WHERE p.reason IS NOT NULL ORDER BY p.reason, p.a_name;
