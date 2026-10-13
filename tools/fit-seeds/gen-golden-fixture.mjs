// src/test/resources/golden-set.fc27.json üreticisi (E3-5): docs/review/v2/golden_set.md'deki oyuncuların attribute'larını DB'den dondurur.
// Kesin çiftler (strict) build'i kırar; gözlem çiftleri (observe) yalnız raporlanır. Kullanım: node tools/fit-seeds/gen-golden-fixture.mjs
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'

const BACKEND = new URL('../../', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const psql = (sql) => execFileSync('docker', ['compose', 'exec', '-T', 'postgres', 'sh', '-c', 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -F"|"'], { cwd: BACKEND, input: sql, encoding: 'utf8' }).trim().split('\n').filter(Boolean)
const KEYS = ['Acceleration', 'SprintSpeed', 'Agility', 'Balance', 'Reactions', 'BallControl', 'Dribbling', 'Composure', 'Vision', 'ShortPassing', 'LongPassing', 'Crossing', 'Curve', 'FKAccuracy', 'Finishing', 'ShotPower', 'LongShots', 'Volleys', 'Penalties', 'Positioning', 'Heading', 'DefAwareness', 'StandingTackle', 'SlidingTackle', 'Interceptions', 'Jumping', 'Stamina', 'Strength', 'Aggression', 'GKDiving', 'GKHandling', 'GKKicking', 'GKPositioning', 'GKReflexes']
const COLS = ['acceleration', 'sprint_speed', 'agility', 'balance', 'reactions', 'ball_control', 'dribbling', 'composure', 'vision', 'short_passing', 'long_passing', 'crossing', 'curve', 'fk_accuracy', 'finishing', 'shot_power', 'long_shots', 'volleys', 'penalties', 'att_position', 'heading', 'def_awareness', 'standing_tackle', 'sliding_tackle', 'interceptions', 'jumping', 'stamina', 'strength', 'aggression', 'gk_diving', 'gk_handling', 'gk_kicking', 'gk_positioning', 'gk_reflexes']

// ad → id (katalog); Crewe oyuncuları kariyer 4'ün overlay'inden (career_player_rating)
const NAMES = ['Cristiano Ronaldo', 'Lucas Passerini', 'Alexander Hofleitner', 'Hugo Ekitiké', 'Matthis Abline', 'Alieu Njie', 'Antoine Griezmann', 'Alexis Sánchez', 'Filippo Distefano', 'Yan Diomande', 'Ismael Díaz', 'Abdul Abdulmalik', 'Adama Traoré', 'Bradley Barcola', 'Emilio Rodríguez', 'Kevin De Bruyne', 'Dominik Szoboszlai', 'Nikolas Nartey', 'Anastasios Bakasetas', 'Isaque', 'Scott McTominay', 'Eduard Löwen', 'Kim Dong Hyun', 'Luka Modrić', 'Riqui Puig', 'Granit Xhaka', 'Nicolò Fagioli', 'Jonas Martin', 'Moisés Caicedo', 'Rani Khedira', 'Leonardo Freijão', 'Aymeric Laporte', 'Gautier Lloris', 'Uroš Radaković', 'Bright Arrey-Mbi', 'Pau Cubarsí', 'Kenan Toibibou', 'Marcos Llorente', 'Álvaro Angulo', 'Henry Rorig', 'Cucurella', 'Bernardo', 'Craig Forsyth', 'Marco Carnesecchi', 'Colin Coosemans', 'Simon Ngapandouetnbu', 'Mikel Santos', 'Owen Jochmans']
const CREWE = ['Keisuke Osako', 'Ian Lawlor', 'Jesse Eugen Bisiwu', 'Jordan Gibson', 'Givairo Read', 'Lewis Billington', 'Iván Román', 'Mickey Demetriou', 'Joe White', 'Joel Tabiner']
const DIAG_CATALOG = ['Robert Kampa', 'Dominik Reimann', 'Torben Müsel', 'Thibaut Courtois', 'Kylian Mbappé']
const players = {}
const fetchPlayer = (name, career) => {
  const esc = name.replace(/'/g, "''")
  const table = career ? 'career_player_rating' : 'player_rating'
  const extra = career ? ' and pr.career_id = 4' : ' and bp.owner_career_id is null and bp.gender = 0'
  const rows = psql(`select bp.id, bp.full_name, pr.overall, array_to_string(pr.positions, ','), pr.accelerate, bp.preferred_foot, ${career ? 'pr.weak_foot, pr.skill_moves' : 'pr.weak_foot, pr.skill_moves'}, bp.height_cm, pr.playstyles::text, ${COLS.map((c) => 'pr.' + c).join(', ')} from ${table} pr join base_player bp on bp.id = pr.base_player_id where bp.full_name = '${esc}'${extra} order by pr.overall desc limit 1;`)
  if (!rows[0]) throw new Error('oyuncu yok: ' + name)
  const f = rows[0].split('|')
  const attrs = {}
  KEYS.forEach((k, i) => (attrs[k] = Number(f[10 + i])))
  return { id: Number(f[0]), name: f[1], ovr: Number(f[2]), positions: f[3].split(','), acceleRate: f[4] || null, foot: f[5] || null, weakFoot: f[6] ? Number(f[6]) : null, skillMoves: f[7] ? Number(f[7]) : null, heightCm: f[8] ? Number(f[8]) : null, playstyles: JSON.parse(f[9] || '{}'), attrs }
}
for (const n of NAMES) players[n] = fetchPlayer(n, false)
for (const n of CREWE) players[n] = fetchPlayer(n, true)
for (const n of DIAG_CATALOG) players[n] = fetchPlayer(n, false)

const blocks = [
  { id: 'TF', label: 'Target Forward · Attack', role: 'target_forward', focus: 'Attack', strict: [['Cristiano Ronaldo', 'Lucas Passerini'], ['Lucas Passerini', 'Alexander Hofleitner'], ['Cristiano Ronaldo', 'Alexander Hofleitner'], ['Lucas Passerini', 'Alieu Njie'], ['Alexander Hofleitner', 'Alieu Njie'], ['Cristiano Ronaldo', 'Hugo Ekitiké']], observe: [['Matthis Abline', 'Alexander Hofleitner']] },
  { id: 'PO', label: 'Poacher · Attack', role: 'poacher', focus: 'Attack', strict: [['Antoine Griezmann', 'Alexis Sánchez'], ['Alexis Sánchez', 'Filippo Distefano'], ['Antoine Griezmann', 'Filippo Distefano']], observe: [['Filippo Distefano', 'Alexander Hofleitner']], extra: ['Alexander Hofleitner'] },
  { id: 'IF', label: 'Inside Forward · Attack', role: 'inside_forward', focus: 'Attack', strict: [['Yan Diomande', 'Ismael Díaz'], ['Ismael Díaz', 'Abdul Abdulmalik'], ['Yan Diomande', 'Abdul Abdulmalik'], ['Ismael Díaz', 'Adama Traoré']], observe: [] },
  { id: 'WG', label: 'Winger · Balanced', role: 'winger', focus: 'Balanced', strict: [['Bradley Barcola', 'Emilio Rodríguez'], ['Adama Traoré', 'Ismael Díaz']], observe: [['Bradley Barcola', 'Adama Traoré']] },
  { id: 'PM', label: 'Playmaker · Balanced (CAM)', role: 'playmaker', focus: 'Balanced', strict: [['Kevin De Bruyne', 'Isaque'], ['Nikolas Nartey', 'Isaque'], ['Kevin De Bruyne', 'Dominik Szoboszlai']], observe: [['Anastasios Bakasetas', 'Nikolas Nartey']] },
  { id: 'B2B', label: 'Box-to-Box · Balanced (CM)', role: 'box_to_box', focus: 'Balanced', strict: [['Scott McTominay', 'Eduard Löwen'], ['Eduard Löwen', 'Kim Dong Hyun'], ['Scott McTominay', 'Kim Dong Hyun'], ['Scott McTominay', 'Luka Modrić'], ['Kim Dong Hyun', 'Riqui Puig']], observe: [['Luka Modrić', 'Eduard Löwen']] },
  { id: 'DLP', label: 'Deep-Lying Playmaker · Build-Up', role: 'deep_lying_playmaker', focus: 'Build-Up', strict: [['Granit Xhaka', 'Nicolò Fagioli'], ['Nicolò Fagioli', 'Jonas Martin'], ['Granit Xhaka', 'Jonas Martin']], observe: [['Luka Modrić', 'Granit Xhaka'], ['Riqui Puig', 'Jonas Martin']] },
  { id: 'HOLD', label: 'Holding · Defend (CDM)', role: 'holding', focus: 'Defend', strict: [['Moisés Caicedo', 'Rani Khedira'], ['Rani Khedira', 'Leonardo Freijão'], ['Moisés Caicedo', 'Leonardo Freijão'], ['Rani Khedira', 'Granit Xhaka']], observe: [] },
  { id: 'DEF', label: 'Defender · Defend (CB)', role: 'defender', focus: 'Defend', strict: [['Aymeric Laporte', 'Gautier Lloris'], ['Gautier Lloris', 'Uroš Radaković'], ['Aymeric Laporte', 'Uroš Radaković'], ['Gautier Lloris', 'Bright Arrey-Mbi']], observe: [] },
  { id: 'BPD', label: 'Ball-Playing Defender · Build-Up (CB)', role: 'ball_playing_defender', focus: 'Build-Up', strict: [['Aymeric Laporte', 'Uroš Radaković'], ['Kenan Toibibou', 'Uroš Radaković'], ['Pau Cubarsí', 'Aymeric Laporte']], observe: [] },
  { id: 'AWB', label: 'Attacking Wingback · Attack', role: 'attacking_wingback', focus: 'Attack', strict: [['Marcos Llorente', 'Álvaro Angulo'], ['Álvaro Angulo', 'Henry Rorig'], ['Henry Rorig', 'Craig Forsyth']], observe: [] },
  { id: 'FB', label: 'Fullback · Defend', role: 'fullback', focus: 'Defend', strict: [['Cucurella', 'Bernardo'], ['Bernardo', 'Craig Forsyth'], ['Craig Forsyth', 'Henry Rorig'], ['Bernardo', 'Álvaro Angulo']], observe: [] },
  { id: 'GK', label: 'Goalkeeper · Defend', role: 'goalkeeper', focus: 'Defend', strict: [['Marco Carnesecchi', 'Colin Coosemans'], ['Colin Coosemans', 'Simon Ngapandouetnbu'], ['Simon Ngapandouetnbu', 'Mikel Santos'], ['Mikel Santos', 'Owen Jochmans']], observe: [] },
  { id: 'K-GK', label: 'Kariyer (Crewe) Goalkeeper · Defend', role: 'goalkeeper', focus: 'Defend', strict: [['Keisuke Osako', 'Ian Lawlor']], observe: [] },
  { id: 'K-IF', label: 'Kariyer (Crewe) Inside Forward · Attack', role: 'inside_forward', focus: 'Attack', strict: [['Jesse Eugen Bisiwu', 'Jordan Gibson']], observe: [] },
  { id: 'K-WB', label: 'Kariyer (Crewe) Wingback · Support', role: 'wingback', focus: 'Support', strict: [['Givairo Read', 'Lewis Billington']], observe: [] },
]
// Test her oyuncuyu kendi doğal mevkisinde koşturur (ayak/yan etkisi karışmasın); blok mevkileri golden_set.md başlıklarındandır.
const BLOCK_POSITIONS = { TF: ['ST'], PO: ['ST'], IF: ['LW', 'RW'], WG: ['LW', 'RW'], PM: ['CAM'], B2B: ['CM'], DLP: ['CDM'], HOLD: ['CDM'], DEF: ['CB'], BPD: ['CB'], AWB: ['LB', 'RB'], FB: ['LB', 'RB'], GK: ['GK'], 'K-GK': ['GK'], 'K-IF': ['LW', 'LM'], 'K-WB': ['RB', 'LB'] }
blocks.forEach((b) => (b.positions = BLOCK_POSITIONS[b.id]))
const names = new Set(blocks.flatMap((b) => [...b.strict.flat(), ...b.observe.flat()]))
const missing = [...names].filter((n) => !players[n])
if (missing.length) throw new Error('fixture eksik: ' + missing.join(', '))
// Teşhis tablosu (eng_audit_E3.md §3): v1 skorları o gün canlı POST /fit/role ile ölçüldü.
const diagnosis = [
  { name: 'Robert Kampa', position: 'GK', role: 'goalkeeper', focus: 'Defend', v1: 2.1 },
  { name: 'Dominik Reimann', position: 'GK', role: 'goalkeeper', focus: 'Defend', v1: 51.1 },
  { name: 'Torben Müsel', position: 'CDM', role: 'holding', focus: 'Defend', v1: 7.5 },
  { name: 'Ian Lawlor', position: 'GK', role: 'goalkeeper', focus: 'Defend', v1: 25.0 },
  { name: 'Iván Román', position: 'CB', role: 'defender', focus: 'Defend', v1: 9.7 },
  { name: 'Joe White', position: 'CM', role: 'box_to_box', focus: 'Balanced', v1: 36.0 },
  { name: 'Thibaut Courtois', position: 'GK', role: 'goalkeeper', focus: 'Defend', v1: 83.7 },
  { name: 'Kylian Mbappé', position: 'ST', role: 'advanced_forward', focus: 'Attack', v1: 94.6 },
]
writeFileSync(new URL('../../src/test/resources/golden-set.fc27.json', import.meta.url), JSON.stringify({ _source: 'docs/review/v2/golden_set.md (KESİN sürüm); attribute değerleri 2026-10-14 DB dökümü', players, blocks, diagnosis }, null, 1) + String.fromCharCode(10))
console.log('oyuncu:', Object.keys(players).length, 'blok:', blocks.length)
