// E3-4b raporu: öğrenici çıktısı (learned.json) + gerçek motor ölçümü (report.json, RoleVectorCandidateReportTest) → E3-4b_rol_vektorleri.md. Seed'e yazmaz.
// Kullanım: node tools/fit-seeds/compose-role-report.mjs <çıktı.md>
import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'

const ROOT = new URL('../../', import.meta.url)
const BACKEND = ROOT.pathname.replace(/^\/([A-Za-z]:)/, '$1')
const dir = new URL('target/role-candidates/', ROOT)
const learned = JSON.parse(readFileSync(new URL('learned.json', dir), 'utf8'))
const report = JSON.parse(readFileSync(new URL('report.json', dir), 'utf8'))
const seed = JSON.parse(readFileSync(new URL('src/main/resources/seed/role-vectors.fc27.json', ROOT), 'utf8'))
const roles = JSON.parse(readFileSync(new URL('src/main/resources/seed/roles.json', ROOT), 'utf8'))
const psql = (sql) => execFileSync('docker', ['compose', 'exec', '-T', 'postgres', 'sh', '-c', 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -A -F"|"'], { cwd: BACKEND, input: sql, encoding: 'utf8' }).trim()
const NL = String.fromCharCode(10)
const f3 = (v) => (v === null || v === undefined || Number.isNaN(v) ? '—' : Number(v).toFixed(3).replace('.', ','))
const f1 = (v) => Number(v).toFixed(1).replace('.', ',')
const OVERFIT = 0.05

const top = (vec, n = 8) => Object.entries(vec).sort((a, b) => b[1] - a[1]).slice(0, n)
const fmtVec = (vec) => top(vec).map(([k, v]) => `${k} ${v.toFixed(3).replace('.', ',')}`).join(', ')
const candidateDocs = {}
const candidateVector = (id, key) => (candidateDocs[id] ??= JSON.parse(readFileSync(new URL(`candidate-${id}.json`, dir), 'utf8'))).vectors[key]
const keyOf = (role) => {
  const position = role.positions[0]
  const focus = role.focuses.includes('Balanced') ? 'Balanced' : role.focuses[0]
  return `${position}|${role.id}|${focus}`
}

const names = new Map()
const ids = [...new Set(Object.values(report.roles).flatMap((r) => r.top.map((t) => t.playerId)))]
if (ids.length) for (const row of psql(`select ea_resource_id, full_name from base_player where ea_resource_id in (${ids.join(',')}) and owner_career_id is null`).split(NL).filter(Boolean)) { const [id, ...n] = row.split('|'); names.set(Number(id), n.join('|')) }

const reading = (id) => {
  const l = learned.learned[id]
  const delta = Object.fromEntries([...new Set([...Object.keys(l.old), ...Object.keys(l.new)])].map((k) => [k, (l.new[k] ?? 0) - (l.old[k] ?? 0)]))
  const up = Object.entries(delta).filter(([, v]) => v > 0.002).sort((a, b) => b[1] - a[1]).slice(0, 3).map(([k]) => k)
  const down = Object.entries(delta).filter(([, v]) => v < -0.002).sort((a, b) => a[1] - b[1]).slice(0, 3).map(([k]) => k)
  return `${up.length ? up.join(' + ') + ' ağırlığı arttı' : 'artan attribute yok'}${down.length ? `; ${down.join(', ')} düştü` : ''}.`
}

const out = []
const keyAucs = (obj, half) => Object.entries(obj.keys).map(([k, v]) => [k, v[half]])
const countAtLeast = (obj, thr) => keyAucs(obj, 'test').filter(([, v]) => v >= thr).length
const all = report.roles.all
out.push('# E3-4b — Zayıf rol vektörleri: EA Role+/++ etiketlerinden öğrenme (RAPOR — seed\'e YAZILMADI)', '',
  '> Durum: yalnız rapor. Hiçbir seed dosyası değişmedi. Onayladığınız roller `_basis: "learned-ea-roles-holdout"` ile yazılır, sonra golden + özellik testleri yeniden koşar.', '',
  '## Yöntem (özet)',
  `- Hedef: oyuncu İÇİ sıralama (aynı oyuncunun aynı mevkideki Role+/++ rolleri, rolsüz rollerinden yüksek): pairwise lojistik kayıp; ölçüt aynı: oyuncu içi AUC (levelFit üzerinden, gerçek motor).`,
  `- Veri: LE dökümü, 21.619 oyuncu. Bölme: oyuncu kimliğine sabit karma (\`RoleSplit.half\`), 50/50; öğrenme **yalnız eğitim yarısında**; bu rapordaki "test" sayıları test yarısındandır (PlayStyle AÇIK, aşinalık KAPALI).`,
  '- Sabit: pozisyon tabanı (BASE_SHARE), focus deltaları (K_FOCUS×), EA OVR payı (0,2; vektör kütlesinde ≤ %50). Öğrenilen: yalnız rol vurgusu E_rol: ≥ 0, ≤ 8 aktif attribute, kütlesi eski kütlenin [0,25×, 4×] aralığında serbest (kütle sabit tutulunca kapasite çok düşüktü). CB/GK rollerinde hücum attribute\'u yasak.',
  `- Öğrenme sırayla, rol rol: bir rol öğrenilirken diğerleri sabit; küresel eğitim AUC'si iyileşmezse o rol eski vurgusuna döner (rapora "değişmedi"). λ=${learned.lambda} (eski vurguya yakınlık), ${learned.iter} adım.`,
  '- Roller birbirinin negatifi olduğu için bir rolün değişimi başkalarının AUC\'sini de oynatır; aşağıda hem rolün kendi anahtarları hem "yalnız bu rol değişirse" küresel test AUC\'si verilir.', '')

out.push('## Genel tablo', '', '| Ölçüt | Önce | Sonra (tüm öğrenilen roller birlikte) |', '|---|---|---|',
  `| Oyuncu içi AUC, çift-ağırlıklı, **test yarısı** | ${f3(report.baseline.testGlobal)} | ${f3(all.testGlobal)} |`,
  `| Oyuncu içi AUC, eğitim yarısı | ${f3(report.baseline.trainGlobal)} | ${f3(all.trainGlobal)} |`,
  `| Test AUC ≥ 0,70 olan (mevki·rol) anahtarı | ${countAtLeast(report.baseline, 0.7)} / ${Object.keys(report.baseline.keys).length} | ${countAtLeast(all, 0.7)} / ${Object.keys(all.keys).length} |`,
  `| Test AUC < 0,65 olan anahtar | ${keyAucs(report.baseline, 'test').filter(([, v]) => v < 0.65).length} | ${keyAucs(all, 'test').filter(([, v]) => v < 0.65).length} |`,
  `| Altın set kesin çiftleri (tüm öğrenilen roller birlikte) | %100 (51/51 + kalibrasyon) | ${all.goldenStrictFailures.length ? all.goldenStrictFailures.length + ' kırık' : `%100 (${all.goldenStrictTotal}/${all.goldenStrictTotal})`}${all.goldenCalibrationFailures.length ? ' · kalibrasyon çifti KIRIK' : ''} |`, '')

out.push('## Özet tablo (rol başına)', '', '| Rol | Durum | Test AUC önce → sonra (rolün kendi anahtarları, ort.) | Yalnız bu rol değişirse küresel test AUC | Eğitim−test farkı | Golden | Değişmez | Öneri |', '|---|---|---|---|---|---|---|---|')
const detail = []
for (const id of learned.weakRoles) {
  const l = learned.learned[id]
  const r = report.roles[id]
  const role = roles.find((x) => x.id === id)
  if (l.reverted || !r) {
    out.push(`| ${id} | değişmedi (eğitim AUC iyileşmedi) | — | — | — | — | — | dokunma |`)
    detail.push(`### ${id}${NL}${NL}Öğrenici eğitim yarısında iyileşme bulamadı; eski vurgu korunuyor. Rolün test AUC'si (mevcut): ${role.positions.map((p) => `${p} ${f3(report.baseline.keys[`${p}|${id}`]?.test)}`).join(', ')}.${NL}`)
    continue
  }
  const keys = role.positions.map((p) => `${p}|${id}`).filter((k) => r.keys[k])
  const avg = (obj, half) => keys.reduce((s, k) => s + obj.keys[k][half], 0) / keys.length
  const before = avg(report.baseline, 'test')
  const after = avg(r, 'test')
  const gap = avg(r, 'train') - avg(r, 'test')
  const overfit = gap > OVERFIT
  const goldenText = r.goldenStrictTotal === 0 ? 'altın sette bu rol yok' : r.goldenStrictFailures.length ? `KIRIK ${r.goldenStrictFailures.length}/${r.goldenStrictTotal}` : `%100 (${r.goldenStrictTotal}/${r.goldenStrictTotal})`
  const inv = l.violations.length ? `İHLAL (${l.violations.length})` : 'ok'
  const advice = r.goldenStrictFailures.length || l.violations.length ? 'DEĞİŞTİRME (golden/değişmez)' : overfit ? 'aşırı uyum işareti: dikkat' : after - before >= 0.03 ? 'onaya aday' : 'kazanç küçük'
  out.push(`| ${id} | öğrenildi (${l.active} attr) | ${f3(before)} → ${f3(after)} | ${f3(report.baseline.testGlobal)} → ${f3(r.testGlobal)} | ${f3(gap)}${overfit ? ' ⚠' : ''} | ${goldenText} | ${inv} | ${advice} |`)
  const key = keyOf(role)
  detail.push([`### ${id}`, '',
    `- **Eski vurgu E:** ${fmtVec(l.old) || '(boş)'}`, `- **Yeni vurgu E:** ${fmtVec(l.new)}`,
    `- **Eski vektör** (${key}): ${fmtVec(seed.vectors[key])}`, `- **Yeni vektör** (${key}): ${fmtVec(candidateVector(id, key))}`,
    '', '| Mevki·rol | Test AUC önce | sonra | Eğitim AUC önce | sonra | Test çifti |', '|---|---|---|---|---|---|',
    ...keys.map((k) => `| ${k.replace('|', ' · ')} | ${f3(report.baseline.keys[k].test)} | ${f3(r.keys[k].test)} | ${f3(report.baseline.keys[k].train)} | ${f3(r.keys[k].train)} | ${Math.round(r.keys[k].testPairs)} |`), '',
    `- **Golden:** ${goldenText}${r.goldenCalibrationFailures.length ? ' · kalibrasyon çifti kırık: ' + r.goldenCalibrationFailures.join('; ') : ''}${r.goldenStrictFailures.length ? ' · ' + r.goldenStrictFailures.join('; ') : ''}`,
    `- **Değişmezler:** ${l.violations.length ? l.violations.join('; ') : 'focus ≤ 0,95 ve roller arası ≤ 0,90 sağlanıyor'}`,
    `- **En çok değişen 3 oyuncu (test yarısı, OVR ≥ 55; levelFit önce → sonra):** ${r.top.map((t) => `${names.get(t.playerId) ?? t.playerId} (${t.key.replace('|', ' · ')}) ${f1(t.before)} → ${f1(t.after)}`).join('; ')}`,
    `- **Okuma:** ${reading(id)}`, ''].join(NL))
}
out.push('', '## Rol rol ayrıntı', '', ...detail)
if (process.argv[2]) writeFileSync(process.argv[2], out.join(NL) + NL)
console.log(out.slice(0, 40).join(NL))
