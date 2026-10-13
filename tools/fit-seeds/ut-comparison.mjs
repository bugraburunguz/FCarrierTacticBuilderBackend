// E3-5b UT karşılaştırması: sık 4 slot için katalogdaki en yüksek OVR'li 15 erkek kart, v1 ve v3 sırası (yerel backend :8081, /fit/role engineVersion geçersiz kılma).
// Kullanım: node tools/fit-seeds/ut-comparison.mjs <çıktı.md>
import { writeFileSync } from 'node:fs'

const API = 'http://localhost:8081/api/v1'
const SLOTS = [
  { pos: 'ST', roleId: 'advanced_forward_attack', label: 'ST · Advanced Forward · Attack' },
  { pos: 'CAM', roleId: 'playmaker_balanced', label: 'CAM · Playmaker · Balanced' },
  { pos: 'CDM', roleId: 'holding_defend', label: 'CDM · Holding · Defend' },
  { pos: 'CB', roleId: 'defender_defend', label: 'CB · Defender · Defend' },
]
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const get = async (path) => {
  let body = await (await fetch(API + path)).json()
  for (let attempt = 0; !body.success && attempt < 8; attempt++) {
    await sleep(15000)
    body = await (await fetch(API + path)).json()
  }
  return body
}
const fit = async (playerId, slot, engineVersion) => {
  const res = await fetch(API + '/fit/role', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ playerId, roleId: slot.roleId, position: slot.pos, engineVersion }) })
  let body = await res.json()
  for (let attempt = 0; !body.success && attempt < 6 && JSON.stringify(body).includes("0013"); attempt++) {
    await sleep(15000)
    body = await (await fetch(API + "/fit/role", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ playerId, roleId: slot.roleId, position: slot.pos, engineVersion }) })).json()
  }
  await sleep(300)
  if (!body.success) throw new Error(`${slot.roleId} ${playerId} ${engineVersion}: ${JSON.stringify(body)}`)
  return body.data.roleFit
}
const ranks = (values) => values.map((v) => values.filter((w) => w > v).length + 1)
const spearman = (a, b) => {
  const ra = ranks(a)
  const rb = ranks(b)
  const n = a.length
  return 1 - (6 * ra.reduce((s, r, i) => s + (r - rb[i]) ** 2, 0)) / (n * (n * n - 1))
}
const fmtContrib = (c) => c.map((x) => `${x.key} ${x.points > 0 ? '+' : ''}${x.points}`).join(', ')

const NL = String.fromCharCode(10)
const out = ['# E3-5b — UT karşılaştırma örneği (v1 ↔ v3)', '', 'Her slot için katalogdaki en yüksek OVR\'li 15 erkek kart (ST/CAM/CDM/CB, o mevkide doğal). Sıralar 1 = en yüksek skor. Kart sırası = OVR sırası. Bu örnek **karar için**; UT yolu v1\'de kaldı.', '']
for (const slot of SLOTS) {
  const players = (await get(`/players?pos=${slot.pos}&gender=0&sort=overall&order=desc&size=15`)).data.items.filter((p) => p.positions[0] === slot.pos || p.positions.includes(slot.pos)).slice(0, 15)
  const rows = []
  for (const p of players) {
    const v1 = await fit(p.id, slot, 'v1')
    const v3 = await fit(p.id, slot, 'v3')
    rows.push({ p, v1, v3 })
  }
  const r1 = ranks(rows.map((r) => r.v1.score))
  const r3 = ranks(rows.map((r) => r.v3.currentFit))
  out.push(`## ${slot.label}`, '', `Spearman(v1, v3) = ${spearman(rows.map((r) => r.v1.score), rows.map((r) => r.v3.currentFit)).toFixed(2)} · v1 en iyi: ${rows[r1.indexOf(1)].p.name} · v3 en iyi: ${rows[r3.indexOf(1)].p.name}`, '',
    '| Kart (OVR) | v1 skor | v1 sıra | v3 currentFit | v3 sıra | levelFit | etiket | v3 ilk 3 katkı |', '|---|---|---|---|---|---|---|---|')
  rows.forEach((r, i) => out.push(`| ${r.p.name} (${r.p.overall}) | ${r.v1.score} | ${r1[i]} | ${r.v3.currentFit} | ${r3[i]} | ${r.v3.levelFit ?? '—'} | ${r.v3.fitLabel ?? '—'} | ${fmtContrib(r.v3.contributions ?? [])} |`))
  out.push('')
}
writeFileSync(process.argv[2], out.join(NL) + NL)
console.log(out.join(NL))
