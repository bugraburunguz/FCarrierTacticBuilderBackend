// LE trait/icontrait çözümünü katalog (EA ratings sitesi) PlayStyle listesiyle karşılaştırır. Kullanım: node tools/fit-seeds/verify-le-playstyle.mjs [rapor.md]
import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'

const BACKEND = new URL('../../', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const psql = (sql) => execFileSync('docker', ['compose', 'exec', '-T', 'postgres', 'sh', '-c', 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -F"|"'], { cwd: BACKEND, input: sql, encoding: 'utf8', maxBuffer: 1 << 28 }).trim().split(String.fromCharCode(10))
const bits = JSON.parse(readFileSync(new URL('../../src/main/resources/seed/le-playstyle-bits.json', import.meta.url), 'utf8')).fields

// ea_resource_id → {name, playstyles}
const catalog = new Map()
for (const row of psql("select bp.ea_resource_id, bp.full_name, pr.playstyles::text from player_rating pr join base_player bp on bp.id = pr.base_player_id where bp.owner_career_id is null;")) {
  const [id, name, ps] = [row.slice(0, row.indexOf('|')), row.slice(row.indexOf('|') + 1, row.lastIndexOf('|')), row.slice(row.lastIndexOf('|') + 1)]
  catalog.set(id, { name, ps: JSON.parse(ps) })
}
const lines = readFileSync(new URL('../../ml-service/data/fc27-dump/players.csv', import.meta.url), 'utf8').split(/\r?\n/)
const header = lines[0].split(',')
const idx = Object.fromEntries(header.map((k, i) => [k, i]))

export function decode(row) {
  const out = {}
  const bit = (value, n) => (BigInt(value) >> BigInt(n)) & 1n
  for (const [field, plusField] of [['trait1', 'icontrait1'], ['trait2', 'icontrait2']]) {
    for (const [n, def] of Object.entries(bits[field])) {
      if (def.kind !== 'playstyle') continue
      const plus = bit(row[idx[plusField]], n) === 1n
      const base = bit(row[idx[field]], n) === 1n
      if (plus) out[def.name] = 2
      else if (base) out[def.name] = 1
    }
  }
  return out
}

let matched = 0, exact = 0, setDiff = 0, levelDiff = 0
const mismatches = []
const byId = new Map()
for (const line of lines.slice(1)) {
  if (!line) continue
  const row = line.split(',')
  const id = row[idx.playerid]
  const cat = catalog.get(id)
  if (!cat) continue
  matched++
  const got = decode(row)
  const a = JSON.stringify(Object.entries(got).sort()), b = JSON.stringify(Object.entries(cat.ps).sort())
  if (a === b) exact++
  else {
    const gk = Object.keys(got).sort().join() !== Object.keys(cat.ps).sort().join()
    gk ? setDiff++ : levelDiff++
    mismatches.push({ id, name: cat.name, got, want: cat.ps })
  }
  byId.set(id, { name: cat.name, got, want: cat.ps })
}
const NL = String.fromCharCode(10)
const fmt = (o) => Object.entries(o).sort().map(([k, v]) => k + (v === 2 ? '+' : '')).join(', ') || '—'
const KNOWN = ['231747', '239085', '203376', '231866', '158023', '192119', '256630', '252371', '246669', '239200']
const md = [`LE çözümü ↔ katalog: ${matched} eşleşen oyuncu, **tam eşit ${exact} (${((100 * exact) / matched).toFixed(2)}%)**, farklı PS kümesi ${setDiff}, aynı küme farklı seviye (PS/PS+) ${levelDiff}.`, '',
  '| Oyuncu | LE çözümü | Katalog (ratings sitesi) | Eşit |', '|---|---|---|---|']
for (const id of KNOWN) {
  const r = byId.get(id)
  if (r) md.push(`| ${r.name} | ${fmt(r.got)} | ${fmt(r.want)} | ${JSON.stringify(Object.entries(r.got).sort()) === JSON.stringify(Object.entries(r.want).sort()) ? '✓' : '✗'} |`)
}
md.push('', 'İlk uyuşmazlıklar:', ...mismatches.map((m) => `- ${m.name} (${m.id}): LE ${fmt(m.got)} ↔ katalog ${fmt(m.want)}`))
console.log(md.join(NL))
if (process.argv[2]) writeFileSync(process.argv[2], md.join(NL) + NL)
const ids = mismatches.map((m) => Number(m.id)).sort((a, b) => a - b)
console.log('uyuşmazlık id aralığı', ids[0], ids[ids.length - 1], 'n', ids.length, '70000 altı', ids.filter((i) => i < 70000).length, 'LE fazla', mismatches.filter((m) => Object.keys(m.got).length > Object.keys(m.want).length).length, 'katalog fazla', mismatches.filter((m) => Object.keys(m.got).length < Object.keys(m.want).length).length)
