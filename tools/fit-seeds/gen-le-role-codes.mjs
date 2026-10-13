// LE yerelleştirme dosyasından (loc/eng_us/localize.json) rol kodu → (mevki, rol adı) tablosu. Kod 1-52 = Role+, +100 = Role++.
// Kullanım: node tools/fit-seeds/gen-le-role-codes.mjs  (localize.json yerel LE klasöründe, git dışı)
import { readFileSync, writeFileSync } from 'node:fs'

const loc = JSON.parse(readFileSync(new URL('../../src/test/resources/FC 27 LE v27.1.0/loc/eng_us/localize.json', import.meta.url), 'utf8')).translations
const codes = {}
for (let code = 1; code <= 52; code++) {
  const plus = loc['player_role_' + code]
  const m = /^(\w+) (.+?)\s*\+$/.exec(plus)
  if (!m) throw new Error('ayrıştırılamadı: ' + plus)
  codes[code] = { position: m[1], role: m[2].trim() }
}
const out = { verified: true, _basis: 'FC 27 Live Editor v27.1.0 loc/eng_us/localize.json player_role_N dizgeleri (gözlem); kod 1-52 = Role+, kod+100 = Role++', codes }
writeFileSync(new URL('../../src/main/resources/seed/le-role-codes.json', import.meta.url), JSON.stringify(out, null, 1) + String.fromCharCode(10))
console.log(Object.keys(codes).length, 'kod')
