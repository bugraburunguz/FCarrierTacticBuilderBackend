// seed/attr-norms.fc27.json üreticisi (E3-3, 13 §3.2 madde 1): her (cinsiyet, ana mevki) için 34 attribute'un p10/p50/p90, ortalama ve sd değeri.
// Kaynak: yerel docker DB `player_rating` (FC27, kariyer üretimi olmayan oyuncular). Kullanım: node tools/fit-seeds/gen-attr-norms.mjs
// Yalnız okuma. Çıktı yeniden üretilebilir (aynı veri → aynı dosya); değişince hash değişir.
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { writeFileSync } from 'node:fs'

const COMPOSE = new URL('../../docker-compose.yml', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const ATTRS = [
  ['acceleration', 'acceleration'], ['sprintSpeed', 'sprint_speed'], ['agility', 'agility'], ['balance', 'balance'], ['reactions', 'reactions'],
  ['ballControl', 'ball_control'], ['dribbling', 'dribbling'], ['composure', 'composure'], ['vision', 'vision'], ['shortPassing', 'short_passing'],
  ['longPassing', 'long_passing'], ['crossing', 'crossing'], ['curve', 'curve'], ['freeKickAccuracy', 'fk_accuracy'], ['finishing', 'finishing'],
  ['shotPower', 'shot_power'], ['longShots', 'long_shots'], ['volleys', 'volleys'], ['penalties', 'penalties'], ['positioning', 'att_position'],
  ['heading', 'heading'], ['defAwareness', 'def_awareness'], ['standingTackle', 'standing_tackle'], ['slidingTackle', 'sliding_tackle'],
  ['interceptions', 'interceptions'], ['jumping', 'jumping'], ['stamina', 'stamina'], ['strength', 'strength'], ['aggression', 'aggression'],
  ['gkDiving', 'gk_diving'], ['gkHandling', 'gk_handling'], ['gkKicking', 'gk_kicking'], ['gkPositioning', 'gk_positioning'], ['gkReflexes', 'gk_reflexes'],
]
const POSITIONS = ['GK', 'CB', 'LB', 'RB', 'CDM', 'CM', 'CAM', 'LM', 'RM', 'LW', 'RW', 'ST']
const MIN_N = 100

const psql = (sql) => execFileSync('docker', ['compose', '-f', COMPOSE, 'exec', '-T', 'postgres', 'sh', '-c', 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -F"|"'], { input: sql, encoding: 'utf8' }).trim()
const stats = ATTRS.map(([, col]) => `percentile_cont(0.1) within group (order by pr.${col}), percentile_cont(0.5) within group (order by pr.${col}), percentile_cont(0.9) within group (order by pr.${col}), avg(pr.${col}), stddev_pop(pr.${col})`).join(', ')
const round = (x) => Math.round(Number(x) * 100) / 100

const result = {}
for (const [genderKey, gender] of [['men', 0], ['women', 1]]) {
  result[genderKey] = {}
  for (const pos of POSITIONS) {
    const row = psql(`select count(*), ${stats} from player_rating pr join base_player bp on bp.id = pr.base_player_id where bp.gender = ${gender} and bp.owner_career_id is null and pr.acceleration is not null and pr.positions[1] = '${pos}';`).split('|')
    const n = Number(row[0])
    if (n < MIN_N) {
      continue
    }
    const attrs = {}
    ATTRS.forEach(([key], i) => {
      const o = 1 + i * 5
      attrs[key] = { p10: round(row[o]), p50: round(row[o + 1]), p90: round(row[o + 2]), mean: round(row[o + 3]), sd: round(row[o + 4]) }
    })
    result[genderKey][pos] = { n, attrs }
  }
}
const doc = {
  _source: 'FC27 kataloğu (EA ratings sitesi, 19.789 resmî kart + LE içe aktarımı); kariyer üretimi oyuncular hariç. tools/fit-seeds/gen-attr-norms.mjs ile üretildi.',
  _basis: 'catalog-computed',
  verified: false,
  _verify: `Her (cinsiyet, ana mevki) için n ≥ ${MIN_N}; daha az oyunculu gruplar yazılmaz (yükleyici erkek değerine düşer). 13 §3.2 madde 1: u = σ((a − p50)/sd).`,
  minPlayers: MIN_N,
  genders: result,
}
const text = JSON.stringify(doc, null, 2) + '\n'
writeFileSync(new URL('../../src/main/resources/seed/attr-norms.fc27.json', import.meta.url), text)
writeFileSync(new URL('../../src/main/resources/seed/attr-norms.fc27.hash', import.meta.url), createHash('sha256').update(text).digest('hex') + '\n')
console.log(Object.entries(result).map(([g, m]) => `${g}: ${Object.entries(m).map(([p, v]) => `${p}=${v.n}`).join(' ')}`).join('\n'))
