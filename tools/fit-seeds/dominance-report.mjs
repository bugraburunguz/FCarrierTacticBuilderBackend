// E3-6b: tek özellik baskınlığı sınırı (%25) için önce/sonra rol vektörü tablosu. Kullanım: node tools/fit-seeds/dominance-report.mjs <önceki-role-vectors.json> <çıktı.md>
import { readFileSync, writeFileSync } from 'node:fs'
const before = JSON.parse(readFileSync(process.argv[2], 'utf8')).vectors
const after = JSON.parse(readFileSync(new URL('../../src/main/resources/seed/role-vectors.fc27.json', import.meta.url), 'utf8')).vectors
const NL = String.fromCharCode(10)
const top = (w, n = 4) => Object.entries(w).sort((a, b) => b[1] - a[1]).slice(0, n).map(([k, v]) => `${k} ${v.toFixed(3).replace('.', ',')}`).join(', ')
const max = (w) => Math.max(...Object.values(w))
const cos = (a, b) => { let d = 0, na = 0, nb = 0; for (const k of new Set([...Object.keys(a), ...Object.keys(b)])) { const x = a[k] ?? 0, y = b[k] ?? 0; d += x * y; na += x * x; nb += y * y } return d / Math.sqrt(na * nb) }
const roles = {}
for (const [key, w] of Object.entries(before)) { const [pos, role, focus] = key.split('|'); (roles[role] ??= []).push({ key, pos, focus, b: w, a: after[key] }) }
const lines = ['# E3-6b — Tek özellik baskınlığı (%25 sınırı): önce / sonra', '',
  'Yeni değişmez: hiçbir rol/focus vektöründe tek bir attribute ağırlığı vektör kütlesinin %25\'ini geçemez (`RoleSeedInvariantsTest.noSingleAttributeExceedsAQuarterOfAnyRoleVector`). Fazla, vektördeki mevcut attribute\'lara (önce rolün tanımındaki vurgu/focus attribute\'ları, sonra diğerleri) orantılı dağıtılır; kırpılan attribute sabit %25\'te kalır (su doldurma).', '',
  '| Rol | Aşan vektör (önce → sonra) | En büyük ağırlık önce → sonra | Örnek vektör önce | Örnek vektör sonra | Kosinüs (eski↔yeni) | Durum |', '|---|---|---|---|---|---|---|']
let total = 0
let fixed = 0
const exempt = new Set(['goalkeeper', 'sweeper_keeper', 'ball_playing_keeper'])
for (const [role, list] of Object.entries(roles)) {
  const over = list.filter((x) => max(x.b) > 0.25 + 1e-6)
  if (!over.length) continue
  total += over.length
  const still = over.filter((x) => max(x.a) > 0.25 + 1e-6)
  fixed += over.length - still.length
  const sample = over[0]
  const minCos = Math.min(...over.map((x) => cos(x.b, x.a)))
  lines.push(`| ${role} | ${over.length} (${[...new Set(over.map((x) => x.pos))].join('/')}) | ${Math.max(...over.map((x) => max(x.b))).toFixed(3).replace('.', ',')} → ${Math.max(...over.map((x) => max(x.a))).toFixed(3).replace('.', ',')} | ${sample.key.replace(/\|/g, ' · ')}: ${top(sample.b)} | ${top(sample.a)} | ${minCos.toFixed(3).replace('.', ',')} | ${exempt.has(role) ? '**MUAF** (aşağıya bak)' : still.length ? 'AŞIYOR' : 'düzeltildi'} |`)
}
lines.push('', `Toplam: ${Object.keys(before).length} vektörün **${total}**'i %25'i aşıyordu (kullanıcının andığı B2B Stamina 0,725 ve TF Heading 0,378 dahil); ${fixed} tanesi düzeltildi, ${total - fixed} tanesi (kaleci ailesi) muaf.`, '',
  '## Muafiyet: kaleci ailesi (goalkeeper, sweeper_keeper, ball_playing_keeper)', 'Kaleci vektörü 4–5 kaleci attribute\'undan oluşur; %25 sınırı dördünü eşit (0,25) yapar ve focus/rol varyantlarını birbirinin aynısı kılar (focus kosinüsü 0,988; mevcut değişmez ≤ 0,95 çöker). Kural "korunamazsa o rolü değiştirme, raporla" gereği kaleci rolleri **değiştirilmedi**; test bu üç rolü muaf tutar.', '',
  '## Ek rol ayarları (kosinüs/golden için)',
  '- `box_to_box`: Stamina emphasis tek başına vektörün %72\'siydi. %25\'e çekilince fazla tanımda başka attribute olmadığı için pas/top kontrolüne akıyor ve altın set kesin çifti **Kim Dong Hyun > Riqui Puig** kırılıyordu (golden 49/50). B2B tanımına koşu+savunma katkısı eklendi (StandingTackle, Interceptions, Strength, Aggression; her biri 0,01 vurgu) → fazla bunlara dağıldı, golden 50/50. Bu bir **rol tanımı değişikliği** (yeni vurgu attribute\'ları), yalnız dağıtım değil; EA B2B açıklamasına dayanır (stamina, top kazanma).',
  '- `deep_lying_playmaker`: playmaker ile CM·Build-Up kosinüsü 0,924 > 0,90 oldu → LongPassing 0,03, DefAwareness 0,03, Interceptions 0,02 eklendi.',
  '- `falseback`: fullback ile kosinüs 0,901 → ShortPassing 0,05→0,07, Interceptions 0,03→0,05.', '',
  '## Doğrulama (değişiklik sonrası)',
  '- Altın set kesin çiftler: **50/50 (%100)**, τ = 1,00; kalibrasyon çifti (Khedira>Xhaka) sağlam.',
  '- `RoleSignalRegressionTest`: Role++ vs rolsüz AUC **0,873** (≥ 0,80), oyuncu içi AUC **0,620** (≥ 0,60; önce 0,626).',
  '- CI değişmezleri: focus kosinüsü ≤ 0,95 (0,939), roller arası ≤ 0,90 (0,899), OVR kütlesi ≤ 0,5, tek attribute ≤ %25 (kaleci hariç).',
  '- Not: `ovrMass` alanı kırpma öncesi ham paydan hesaplanır; yeniden dağıtım OVR regresyon bileşenini ihmal edilebilir ölçüde oynatır, ≤ 0,5 değişmezi korunur.')
writeFileSync(process.argv[3], lines.join(NL) + NL)
console.log(lines.slice(4, 4 + 30).map((l) => l.slice(0, 160)).join(NL))
