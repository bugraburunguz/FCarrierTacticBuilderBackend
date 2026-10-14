// Canlı (yerel) DB'de eski okuma ↔ career_player eşitliği: oyuncu sayısı, OVR, pozisyon, 34 attribute. Kullanım: node tools/fit-seeds/career-player-equivalence.mjs
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'

const BACKEND = new URL('../../', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const psql = (sql) => execFileSync('docker', ['compose', 'exec', '-T', 'postgres', 'sh', '-c', 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -F"|"'], { cwd: BACKEND, input: sql, encoding: 'utf8' }).trim()
const attr = readFileSync(new URL('../../src/main/java/com/fcareer/fitengine/model/Attr.java', import.meta.url), 'utf8')
const columns = [...attr.matchAll(/^\s+[A-Z_]+\("[A-Za-z]+", "([a-z_]+)"\)[,;]/gm)].map((m) => m[1])
if (columns.length !== 34) throw new Error('Attr sütunları: ' + columns.length)
const legacyAttrs = columns.map((c) => `COALESCE(cpr.${c}, r.${c})`).join(', ')
const careers = psql('select id, name from career order by id').split(String.fromCharCode(10)).filter(Boolean).map((l) => l.split('|'))
for (const [id, name] of careers) {
  const sql = `
  with legacy as (
    select b.id, COALESCE(cpd.overall, r.overall) ovr, array_to_string(COALESCE(cpr.positions, r.positions), ',') pos, ARRAY[${legacyAttrs}]::smallint[] attrs
    from player_rating r join base_player b on b.id = r.base_player_id
    left join career_player_data cpd on cpd.base_player_id = r.base_player_id and cpd.career_id = ${id}
    left join career_player_rating cpr on cpr.base_player_id = r.base_player_id and cpr.career_id = cpd.career_id and cpr.game_version_id = r.game_version_id
    where r.game_version_id = (select game_version_id from career where id = ${id}) and (b.owner_career_id is null or b.owner_career_id = ${id}) and b.retired_at is null),
  cur as (select player_id id, ovr, array_to_string(positions, ',') pos, attrs from career_player where career_id = ${id})
  select (select count(*) from legacy), (select count(*) from cur),
         (select count(*) from legacy l join cur c using (id) where l.ovr <> c.ovr), (select count(*) from legacy l join cur c using (id) where l.pos <> c.pos),
         (select count(*) from legacy l join cur c using (id) where l.attrs <> c.attrs), (select count(*) from legacy l left join cur c using (id) where c.id is null),
         (select count(*) from cur c left join legacy l using (id) where l.id is null),
         (select count(*) from career_player where career_id = ${id} and live), (select count(*) from career_player_data where career_id = ${id});`
  const [legacy, current, ovr, pos, attrs, missing, extra, live, cpd] = psql(sql).split('|').map(Number)
  console.log(`kariyer ${id} (${name}): eski ${legacy} ↔ yeni ${current} | OVR farkı ${ovr} | pozisyon farkı ${pos} | attribute farkı ${attrs} | eksik ${missing} | fazla ${extra} | live ${live} / career_player_data ${cpd}`)
}
