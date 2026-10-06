// EA taktik kodu tersine mühendislik yardımcısı.
// Kullanım: node analyze.mjs [samples.json]
// Çıktı: alfabe adayları, her örnek çifti için "hangi karakter konumları + hangi ayarlar" farkı, ortak önek.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const file = process.argv[2] ?? path.join(here, 'samples.json')
const { samples } = JSON.parse(fs.readFileSync(file, 'utf8'))

const settingsOf = (s) => {
  const out = { formation: s.formation, buildUp: s.buildUp, defensiveApproach: s.defensiveApproach, lineHeight: s.lineHeight }
  s.slots.forEach((slot, i) => {
    out[`slot${i + 1}.${slot.slot}`] = `${slot.role} / ${slot.focus}`
  })
  return out
}

const chars = new Set(samples.flatMap((s) => [...s.code]))
console.log(`Örnek: ${samples.length} · kod uzunlukları: ${[...new Set(samples.map((s) => s.code.length))].join(', ')}`)
console.log(`Görülen karakterler (${chars.size}): ${[...chars].sort().join('')}`)
const symbols = [...chars].filter((c) => !/[A-Za-z0-9]/.test(c))
console.log(`Sembol karakterleri: ${symbols.join(' ')}  → alfabe en az ${62 + symbols.length} simge (tüm semboller görülmedi olabilir)`)
console.log('')

for (let i = 0; i < samples.length; i++) {
  for (let j = i + 1; j < samples.length; j++) {
    const a = samples[i]
    const b = samples[j]
    let prefix = 0
    while (prefix < a.code.length && a.code[prefix] === b.code[prefix]) {
      prefix++
    }
    const diffPositions = [...a.code].map((ch, k) => (ch === b.code[k] ? null : k + 1)).filter(Boolean)
    const sa = settingsOf(a)
    const sb = settingsOf(b)
    const diffSettings = Object.keys(sa).filter((k) => String(sa[k]) !== String(sb[k]))
    console.log(`${a.id} ↔ ${b.id}: ortak önek ${prefix} karakter · farklı konumlar [${diffPositions.join(', ')}] · farklı ayar sayısı ${diffSettings.length}`)
    if (diffSettings.length <= 6) {
      diffSettings.forEach((k) => console.log(`    ${k}: ${sa[k]}  →  ${sb[k]}`))
    }
  }
}
console.log('\nTek ayar farkı olan çiftler (en değerli örnekler):')
let found = 0
for (let i = 0; i < samples.length; i++) {
  for (let j = i + 1; j < samples.length; j++) {
    const sa = settingsOf(samples[i])
    const sb = settingsOf(samples[j])
    const diff = Object.keys(sa).filter((k) => String(sa[k]) !== String(sb[k]))
    if (diff.length === 1) {
      found++
      const diffPositions = [...samples[i].code].map((ch, k) => (ch === samples[j].code[k] ? null : k + 1)).filter(Boolean)
      console.log(`  ${samples[i].id} ↔ ${samples[j].id}: ${diff[0]} → karakter konumları [${diffPositions.join(', ')}]`)
    }
  }
}
if (found === 0) {
  console.log('  (henüz yok — kontrollü örnekler gerekli; bkz. PROJECT_FEATURES.md §3.2)')
}
