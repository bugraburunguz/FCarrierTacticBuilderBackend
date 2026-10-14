// E3-9 (REP-02): Plan B değişiklik biçimini tek biçime çeker: "SLOT: Rol/Focus (oyuncu)".
// "SLOT (POZ): Rol/Focus — not" -> "SLOT: Rol/Focus (not)";  "SLOT (POZ): Rol/Focus" -> "SLOT: Rol/Focus". Kullanım: node tools/replicas/normalize-planb.mjs
import { readFileSync, writeFileSync } from 'node:fs'

const path = new URL('../../src/main/resources/seed/replicas.fc27.json', import.meta.url)
const file = decodeURIComponent(path.pathname).replace(/^\/([A-Za-z]:)/, '$1')
const data = JSON.parse(readFileSync(file, 'utf8'))
let changed = 0
for (const r of data) {
  const changes = r.fc27?.planB?.changes
  if (!changes) continue
  r.fc27.planB.changes = changes.map((c) => {
    const withNote = /^([A-Z0-9]+) \([A-Z]+\): (.+?\/[^—(]+?)\s+—\s+(.+)$/.exec(c)
    if (withNote) {
      changed++
      return `${withNote[1]}: ${withNote[2].trim()} (${withNote[3].trim()})`
    }
    const plain = /^([A-Z0-9]+) \([A-Z]+\): (.+\/.+)$/.exec(c)
    if (plain) {
      changed++
      return `${plain[1]}: ${plain[2].trim()}`
    }
    return c
  })
}
writeFileSync(file, JSON.stringify(data, null, 1) + '\n')
console.log('normalize edilen satır:', changed)
