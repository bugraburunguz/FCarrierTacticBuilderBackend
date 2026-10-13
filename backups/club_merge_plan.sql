-- Kulüp ikizi birleştirme (Buğra onayı: 2026-10-13). psql -v apply=0 → DRY-RUN (ROLLBACK), -v apply=1 → uygula (COMMIT).
-- Yalnız "a" satırı her yerde 0 bağlı olan çiftler işlenir; bağı olan "a" satırına dokunulmaz, raporlanır.
-- Bağ sayımı: player_rating, career_player_rating, career, user_player_state, roster_event (from/to). ut_item.club_id EA team id'sidir (club.id değil);
-- jsonb alanlarında (preset.settings/role_hints/slot_tags, user_save_snapshot.raw_meta, ...) club.id tutulmaz (preset.club yalnız ad metnidir).
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE orphan AS
SELECT c.id FROM club c
WHERE NOT EXISTS (SELECT 1 FROM player_rating WHERE club_id = c.id)
  AND NOT EXISTS (SELECT 1 FROM career_player_rating WHERE club_id = c.id)
  AND NOT EXISTS (SELECT 1 FROM career WHERE club_id = c.id)
  AND NOT EXISTS (SELECT 1 FROM user_player_state WHERE club_id = c.id)
  AND NOT EXISTS (SELECT 1 FROM roster_event WHERE from_club_id = c.id OR to_club_id = c.id);

CREATE TEMP TABLE plan (a_id int, b_id int, source text, make_alias boolean);
WITH norm AS (
  SELECT id, name, league_id, gender, ea_team_id,
         regexp_replace(lower(name), '[^a-z0-9]', '', 'g') AS n,
         lower(regexp_replace(regexp_replace(name, '[^A-Za-z0-9 ]', '', 'g'), '(\w)\w*\s*', '\1', 'g')) AS initials
  FROM club
), pairs AS (
  SELECT a.id AS a_id, b.id AS b_id,
         CASE WHEN a.n = b.n THEN 'encoding-repair'
              WHEN a.initials = b.n OR b.initials = a.n THEN 'manual-review'
              WHEN length(a.n) >= 4 AND length(b.n) >= 4 AND (a.n LIKE '%' || b.n || '%' OR b.n LIKE '%' || a.n || '%') THEN 'manual-review'
         END AS source
  FROM norm a JOIN norm b ON a.id < b.id AND a.league_id IS NOT DISTINCT FROM b.league_id AND a.gender IS NOT DISTINCT FROM b.gender
)
INSERT INTO plan
SELECT p.a_id, p.b_id, p.source, true FROM pairs p
WHERE p.source IS NOT NULL
  AND p.a_id IN (SELECT id FROM orphan)
  AND EXISTS (SELECT 1 FROM player_rating WHERE club_id = p.b_id)
  AND p.a_id NOT IN (196, 997, 375, 1019, 553, 384, 637, 200)
  AND p.b_id NOT IN (196, 997, 375, 1019, 553, 384, 637);
-- Elle kararlar: 541 = Estudiantes de Río Cuarto → 997; 27 Paris SG (kadın) → 996; 200 belirsiz: sil, alias yazma.
INSERT INTO plan VALUES (541, 997, 'manual-review', true), (27, 996, 'manual-review', true), (200, NULL, NULL, false);

\echo '--- DRY-RUN SAYIMLARI ---'
SELECT count(*) AS plan_satiri, count(*) FILTER (WHERE make_alias) AS yazilacak_alias, count(*) FILTER (WHERE a_id NOT IN (SELECT id FROM orphan)) AS bagli_a_satiri FROM plan;
SELECT p.a_id, a.name AS a_ad, p.b_id, b.name AS b_ad, p.source, (p.a_id IN (SELECT id FROM orphan)) AS a_yetim FROM plan p JOIN club a ON a.id = p.a_id LEFT JOIN club b ON b.id = p.b_id ORDER BY p.a_id;
SELECT 'club öncesi' AS ne, count(*) FROM club UNION ALL SELECT 'yetim öncesi', count(*) FROM orphan UNION ALL SELECT 'alias öncesi', count(*) FROM club_alias;

-- Bağı olan a satırı varsa uygulama durur (raporlanır, dokunulmaz)
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM plan WHERE a_id NOT IN (SELECT id FROM orphan)) THEN RAISE EXCEPTION 'bağlı a satırı var: plan geçersiz'; END IF;
END $$;

INSERT INTO club_alias (alias_name, league_id, gender, ea_team_id, club_id, source, verified)
SELECT a.name, a.league_id, a.gender, b.ea_team_id, p.b_id, p.source, true
FROM plan p JOIN club a ON a.id = p.a_id JOIN club b ON b.id = p.b_id WHERE p.make_alias
ON CONFLICT (lower(alias_name), COALESCE(league_id, 0), COALESCE(gender, 2)) DO NOTHING;
DELETE FROM club WHERE id IN (SELECT a_id FROM plan);

\echo '--- SONRA ---'
SELECT 'club sonrası' AS ne, count(*) FROM club UNION ALL SELECT 'alias sonrası', count(*) FROM club_alias;
SELECT 'kalan yetim', count(*) FROM club c WHERE NOT EXISTS (SELECT 1 FROM player_rating WHERE club_id = c.id) AND NOT EXISTS (SELECT 1 FROM career_player_rating WHERE club_id = c.id) AND NOT EXISTS (SELECT 1 FROM career WHERE club_id = c.id) AND NOT EXISTS (SELECT 1 FROM user_player_state WHERE club_id = c.id) AND NOT EXISTS (SELECT 1 FROM roster_event WHERE from_club_id = c.id OR to_club_id = c.id);

\if :apply
  COMMIT;
\else
  ROLLBACK;
\endif
