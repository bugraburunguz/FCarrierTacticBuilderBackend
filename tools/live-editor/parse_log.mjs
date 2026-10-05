// Live Editor log'undaki FCDUMP satirlarini tablo basina CSV'ye cevirir.
// Kullanim: node parse_log.mjs <live_editor_*.log> <cikti_klasoru>
import { createReadStream, mkdirSync, writeFileSync } from 'node:fs'
import { createInterface } from 'node:readline'

const [logPath, outDir] = process.argv.slice(2)
if (!logPath || !outDir) {
  console.error('node parse_log.mjs <log> <outDir>')
  process.exit(1)
}
mkdirSync(outDir, { recursive: true })

const MARKER = 'FCDUMP\t'
const tables = new Map()
const summary = []

const rl = createInterface({ input: createReadStream(logPath, { encoding: 'utf8' }), crlfDelay: Infinity })
for await (const line of rl) {
  const at = line.indexOf(MARKER)
  if (at < 0) continue
  const [kind, table, ...rest] = line.slice(at + MARKER.length).split('\t')
  const payload = rest.join('\t')
  if (kind === 'HEADER') {
    tables.set(table, { header: payload, rows: [] })
  } else if (kind === 'ROW') {
    tables.get(table)?.rows.push(payload)
  } else if (kind === 'END' || kind === 'BEGIN' || kind === 'ERROR' || kind === 'SKIP') {
    summary.push(`${kind} ${table} ${payload}`.trim())
  }
}

for (const [table, { header, rows }] of tables) {
  writeFileSync(`${outDir}/${table}.csv`, [header, ...rows].join('\n') + '\n', 'utf8')
  console.log(`${table}: ${rows.length} rows -> ${outDir}/${table}.csv`)
}
console.log(summary.join('\n'))
