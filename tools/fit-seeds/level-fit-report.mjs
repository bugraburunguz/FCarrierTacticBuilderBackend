// target/fit-report.json (FitReportExportTest) + yerel DB fit_level_norms → levelFit ve v1→v3 karşılaştırma tablosu (markdown).
// Kullanım: node tools/fit-seeds/level-fit-report.mjs [çıktı.md]
import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'

const BACKEND = new URL('../../', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const psql = (sql) => execFileSync('docker', ['compose', 'exec', '-T', 'postgres', 'sh', '-c', 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -F"|"'], { cwd: BACKEND, input: sql, encoding: 'utf8' }).trim()
const report = JSON.parse(readFileSync(new URL('../../target/fit-report.json', import.meta.url), 'utf8'))


// E3-5c: 50 + 50·tanh((fit − medyan) / max(IQR, 8)); LevelFitCalculator.smooth ile aynı.
const smooth = (q, fit) => 50 + 50 * Math.tanh((fit - q[50]) / Math.max(q[75] - q[25], 8))
const levelOf = (e) => {
  const row = psql(`select band_lo, band_hi, sample_count, quantiles::text from fit_level_norms where engine_version = 'v3' and gender = ${e.gender} and position = '${e.position}' and role_id = '${e.role}' and focus = '${e.focus}' order by abs(ovr - ${e.ovr}) limit 1;`)
  if (!row) return null
  const [lo, hi, n, qs] = row.split('|')
  const q = qs.replace(/[{}]/g, '').split(',').map(Number)
  return { band: `${lo}-${hi}`, n: Number(n), level: Math.round(smooth(q, e.currentFit) * 10) / 10 }
}

const fmt = (e) => e.top3.map((c) => `${c.key} ${c.points > 0 ? '+' : ''}${c.points}`).join(', ')
const lines = []
lines.push('## 1. Teşhis oyuncuları: v1 → v3', '', '| Oyuncu (mevki · rol) | OVR | v1 skor | v3 currentFit | v3 levelFit (bant, n) | v3 ilk 3 katkı |', '|---|---|---|---|---|---|')
for (const e of report.filter((r) => r.kind === 'diagnosis')) {
  const l = levelOf(e)
  lines.push(`| ${e.name} (${e.position} · ${e.role} ${e.focus}) | ${e.ovr} | ${e.v1} | ${e.currentFit} | ${l ? `${l.level} (OVR ${l.band}, n=${l.n})` : '—'} | ${fmt(e)} |`)
}
lines.push('', '## 2. Altın set oyuncuları: currentFit ve levelFit', '', '| Blok | Oyuncu | OVR | currentFit | levelFit |', '|---|---|---|---|---|')
const seen = new Set()
for (const e of report.filter((r) => r.kind === 'golden')) {
  const k = e.block + e.name
  if (seen.has(k)) continue
  seen.add(k)
  const l = levelOf(e)
  lines.push(`| ${e.block} | ${e.name} | ${e.ovr} | ${e.currentFit} | ${l ? l.level : '—'} |`)
}
const out = lines.join(String.fromCharCode(10)) + String.fromCharCode(10)
if (process.argv[2]) writeFileSync(process.argv[2], out)
console.log(out)

// levelFit kabul kontrolleri (golden_set.md): Kampa ve Lawlor ≥ 30; kendi seviyesinde tipik profiller [35, 65]. Yerel DB'ye bağlıdır (CI'da koşmaz).
const levelByName = Object.fromEntries(report.filter((r) => r.kind === 'diagnosis' || r.block === 'K-GK' || r.block === 'K-WB').map((e) => [e.name, levelOf(e)?.level]))
const checks = [
  ['Robert Kampa levelFit ≥ 30', levelByName['Robert Kampa'] >= 30],
  ['Ian Lawlor levelFit ≥ 30', levelByName['Ian Lawlor'] >= 30],
  ...['Keisuke Osako', 'Ian Lawlor', 'Iván Román', 'Joe White', 'Dominik Reimann'].map((n) => [`${n} levelFit ∈ [35, 65]`, levelByName[n] >= 35 && levelByName[n] <= 65]),
]
for (const [label, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}`)
if (checks.some(([, ok]) => !ok)) process.exit(1)
