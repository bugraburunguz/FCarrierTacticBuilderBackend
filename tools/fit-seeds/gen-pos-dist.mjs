// seed/pos-dist.fc27.json üreticisi (E3-3, 13 §3.2 madde 8): mevki uzaklığı çarpanı posDist[doğal][hedef]. Değerler ELLE belirlenmiş kurallardan açık matrise yazılır
// (koda gömülü değil, veri); verified:false. Kaynak: 13 §3.2 madde 8 listesi + bölge kuralı (aynı bölge ≤ 0,75; komşu bölge 0,60; savunma↔hücum 0,45; kaleci takas edilmez 0).
import { createHash } from 'node:crypto'
import { writeFileSync } from 'node:fs'

const POSITIONS = ['GK', 'CB', 'LB', 'RB', 'CDM', 'CM', 'CAM', 'LM', 'RM', 'LW', 'RW', 'ST']
const ZONE = { CB: 'def', LB: 'def', RB: 'def', CDM: 'mid', CM: 'mid', CAM: 'mid', LM: 'mid', RM: 'mid', LW: 'att', RW: 'att', ST: 'att' }
// 13 §3.2 madde 8'de açıkça yazılanlar (çift yönlü, aksi belirtilmedikçe)
const EXPLICIT = [
  ['RB', 'LB', 0.97, 'both', '13 §3.2 madde 8'], ['CM', 'CDM', 0.93, 'both', '13 §3.2 madde 8'], ['CB', 'CDM', 0.9, 'forward', '13 §3.2 madde 8 (CB→CDM)'],
  ['CAM', 'ST', 0.9, 'both', '13 §3.2 madde 8'], ['RW', 'RM', 0.97, 'both', '13 §3.2 madde 8'],
  ['LW', 'LM', 0.97, 'both', 'RW↔RM simetrisi (tahmin)'], ['LM', 'RM', 0.97, 'both', 'tahmin'], ['LW', 'RW', 0.97, 'both', 'RB↔LB simetrisi (tahmin)'], ['CM', 'CAM', 0.93, 'both', 'CM↔CDM benzeri (tahmin)'],
]
const matrix = {}
for (const a of POSITIONS) {
  matrix[a] = {}
  for (const b of POSITIONS) {
    let v
    if (a === b) v = 1
    else if (a === 'GK' || b === 'GK') v = 0
    else if (ZONE[a] === ZONE[b]) v = 0.75
    else if (new Set([ZONE[a], ZONE[b]]).has('mid')) v = 0.6
    else v = 0.45
    matrix[a][b] = v
  }
}
const basis = []
for (const [a, b, v, dir, why] of EXPLICIT) {
  matrix[a][b] = v
  if (dir === 'both') matrix[b][a] = v
  basis.push({ from: a, to: b, value: v, direction: dir, basis: why })
}
const doc = {
  _source: '13_KARIYER_MOTORU_YENIDEN.md §3.2 madde 8 + bölge kuralı; elle belirlenmiş, ölçülmemiş',
  verified: false,
  _verify: 'FC27 mevki geçiş ceza değerleri ölçülmedi; golden set ikinci turunda (mevki dışı çiftler) doğrulanır. Mevki dışında rol vektörü yerine pozisyonun genel vektörü kullanılır (FC27 basitleştirilmiş rol).',
  _rules: { sameZone: 0.75, adjacentZone: 0.6, defenceToAttack: 0.45, goalkeeper: 0 },
  _explicit: basis,
  positions: POSITIONS,
  matrix,
}
const text = JSON.stringify(doc, null, 2) + '\n'
writeFileSync(new URL('../../src/main/resources/seed/pos-dist.fc27.json', import.meta.url), text)
writeFileSync(new URL('../../src/main/resources/seed/pos-dist.fc27.hash', import.meta.url), createHash('sha256').update(text).digest('hex') + '\n')
console.log('pos-dist yazıldı:', POSITIONS.length, 'mevki')
