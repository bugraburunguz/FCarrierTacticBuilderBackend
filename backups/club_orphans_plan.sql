-- Kalan yetim kulüpler (V3-04.1, Buğra onayı): 0 bağı yeniden doğrula (FK'siz tablolar dahil), tanınabilir olanlara alias, hepsini sil.
-- psql -v apply=0 dry-run, -v apply=1 uygula. jsonb alanlarında club.id tutulmaz; ut_item.club_id EA team id'sidir.
\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE orphan AS
SELECT c.id, c.name, c.league_id, c.gender FROM club c
WHERE NOT EXISTS (SELECT 1 FROM player_rating WHERE club_id = c.id) AND NOT EXISTS (SELECT 1 FROM career_player_rating WHERE club_id = c.id)
  AND NOT EXISTS (SELECT 1 FROM career WHERE club_id = c.id) AND NOT EXISTS (SELECT 1 FROM user_player_state WHERE club_id = c.id)
  AND NOT EXISTS (SELECT 1 FROM roster_event WHERE from_club_id = c.id OR to_club_id = c.id)
  AND NOT EXISTS (SELECT 1 FROM club_alias WHERE club_id = c.id);
CREATE TEMP TABLE canon (a_id int, b_id int);
INSERT INTO canon VALUES (68,1066),(190,1004),(543,1021),(345,1014),(238,999),(337,1011),(321,1026),(383,1025),(600,1036),(639,1024),(729,1016),(167,1046);
\echo '--- DRY-RUN ---'
SELECT (SELECT count(*) FROM orphan) AS yetim, (SELECT count(*) FROM canon) AS alias_yazilacak, (SELECT count(*) FROM canon WHERE a_id NOT IN (SELECT id FROM orphan)) AS bagli_a;
DO $$ BEGIN IF EXISTS (SELECT 1 FROM canon WHERE a_id NOT IN (SELECT id FROM orphan)) THEN RAISE EXCEPTION 'bağlı a satırı var'; END IF; END $$;
INSERT INTO club_alias (alias_name, league_id, gender, ea_team_id, club_id, source, verified)
SELECT o.name, o.league_id, o.gender, b.ea_team_id, c.b_id, 'manual-review', true FROM canon c JOIN orphan o ON o.id = c.a_id JOIN club b ON b.id = c.b_id
ON CONFLICT (lower(alias_name), COALESCE(league_id, 0), COALESCE(gender, 2)) DO NOTHING;
DELETE FROM club WHERE id IN (SELECT id FROM orphan);
SELECT 'club sonrası' AS ne, count(*) FROM club UNION ALL SELECT 'alias sonrası', count(*) FROM club_alias;
\if :apply
  COMMIT;
\else
  ROLLBACK;
\endif
