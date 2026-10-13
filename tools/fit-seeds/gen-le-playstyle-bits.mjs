// LE trait1/trait2/icontrait1/icontrait2 bit maskelerini PlayStyle adlarına çeviren tablo (seed/le-playstyle-bits.json).
// Kaynak: LE loc/eng_us/localize.json trait1_N / trait2_N / icontrait1_N / icontrait2_N dizgeleri (bit N). Kanıtsız eşleme yok: dizgesi olmayan bit çalışma zamanında `unknown_<alan>_<N>` olarak saklanır.
// Katalog (EA ratings sitesi) adlandırması: boşluksuz CamelCase; "Game Changer" → Gamechanger, GK PS'lerinde "GK " öneki yok.
import { readFileSync, writeFileSync } from 'node:fs'

const loc = JSON.parse(readFileSync(new URL('../../src/test/resources/FC 27 LE v27.1.0/loc/eng_us/localize.json', import.meta.url), 'utf8')).translations
const ALIAS = { GameChanger: 'Gamechanger' }
const canonical = (name) => {
  const base = name.replace(/\+$/, '').replace(/^GK /, '').replace(/[^A-Za-z0-9]/g, '')
  return ALIAS[base] ?? base
}
// trait1: bit 0-29 saha PS; trait2: bit 0-5 kaleci PS, 6-7 CPU yapay zekâ, 8-13 gizli özellikler (localize adları).
const section = (field) => {
  const bits = {}
  for (const [key, value] of Object.entries(loc)) {
    const m = new RegExp('^' + field + '_([0-9]+)$').exec(key)
    if (m) bits[m[1]] = value
  }
  return bits
}
const KIND_TRAIT2 = (bit) => (bit <= 5 ? 'playstyle' : bit <= 7 ? 'ai' : 'hidden')
const out = { verified: true, _basis: 'FC 27 Live Editor v27.1.0 loc/eng_us/localize.json trait1_N, trait2_N, icontrait1_N, icontrait2_N dizgeleri (gözlem); katalog adlandırması 22.314 oyuncuda doğrulandı (docs/review/v2/E3-2_playstyle.md)', fields: {} }
for (const field of ['trait1', 'trait2', 'icontrait1', 'icontrait2']) {
  const names = section(field)
  out.fields[field] = Object.fromEntries(Object.entries(names).map(([bit, name]) => [bit, { name: canonical(name), kind: field.endsWith('2') ? KIND_TRAIT2(Number(bit)) : 'playstyle' }]))
}
writeFileSync(new URL('../../src/main/resources/seed/le-playstyle-bits.json', import.meta.url), JSON.stringify(out, null, 1) + String.fromCharCode(10))
console.log(Object.fromEntries(Object.entries(out.fields).map(([f, b]) => [f, Object.keys(b).length])))
