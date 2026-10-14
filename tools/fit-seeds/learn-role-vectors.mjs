// E3-4b: zayıf rollerin rol-tabanı vurgusunu EA Role+/++ etiketlerinden öğrenir (oyuncu İÇİ sıralama, pairwise lojistik kayıp).
// Seed'e YAZMAZ: aday vektörleri target/role-candidates/ altına yazar; gerçek motorla ölçüm RoleFamiliarityValidationTest (-Drole.candidates=...) içindedir.
//
// Model (gen-role-seeds.mjs ile aynı kural, `assemble`): raw = BASE·taban(mevki grubu) + K_E·E_rol + K_F·focus + OVR_payı·EA-OVR(mevki); w = raw / Σraw.
// SABİT: pozisyon tabanı, focus deltaları, EA OVR payı. ÖĞRENİLEN: yalnız E_rol (rol vurgusu): ≥ 0, ≤ 8 aktif attribute, kütlesi (Σ) eski vurguyla aynı.
// Hedef: aynı oyuncunun aynı mevkideki Role+/++ rolleri, rolsüz rollerinden yüksek (levelFit'e yakın vekil: rol/mevki içinde standartlaştırılmış çekirdek skor).
// Bölme: oyuncu kimliğine sabit karma (split()), 50/50; öğrenme yalnız eğitim yarısında. Kullanım: node tools/fit-seeds/learn-role-vectors.mjs [--lambda 1] [--roles a,b]
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { assemble, EMPHASIS, K_EMPHASIS, K_FOCUS, BASE_SHARE, OVR_SHARE, FOCUS, FOCUS_BY_GROUP, roles, norms, calib, ovrOf, norm1, cosine, COLUMN_TO_KEY, NORM_KEY } from './gen-role-seeds.mjs'

const ROOT = new URL('../../', import.meta.url)
const args = process.argv.slice(2)
const arg = (name, def) => (args.includes('--' + name) ? args[args.indexOf('--' + name) + 1] : def)
const LAMBDA = Number(arg('lambda', 1))
const ITER = Number(arg('iter', 500))
const MAX_ACTIVE = 8
const MASS_MIN = 0.25 // vurgu kütlesi eski kütlenin [0,25×, 4×] aralığında serbest; taban/focus/OVR payı sabit (OVR payı kütlede ≤ %50 kalır)
const MASS_MAX = 4
const WEAK_AUC = 0.65
const POSITION_CODES = { 0: 'GK', 3: 'RB', 5: 'CB', 7: 'LB', 10: 'CDM', 12: 'RM', 14: 'CM', 16: 'LM', 18: 'CAM', 23: 'RW', 25: 'ST', 27: 'LW' }
const ATTR_KEYS = Object.values(COLUMN_TO_KEY) // Attr enum sırası (COLUMN_TO_KEY ekleme sırası = Attr.values())
const DUMP_COLUMN = (key) => ({ FKAccuracy: 'freekickaccuracy', Heading: 'headingaccuracy', DefAwareness: 'defensiveawareness' }[key] ?? key.toLowerCase())
const ATTACKING = new Set(['Finishing', 'Positioning', 'ShotPower', 'LongShots', 'Volleys', 'Penalties'])
const DEFENSIVE_GROUPS = new Set(['CB', 'GK'])

// Sabit karma ile oyuncu bölmesi (Java testindeki splitHalf ile birebir): ((id * 2654435761) >>> 13) & 1, 32 bit.
export const half = (id) => Number((((BigInt(id) * 2654435761n) & 0xffffffffn) >> 13n) & 1n)

// ---- Veri
function loadPlayers() {
  const lines = readFileSync(new URL('ml-service/data/fc27-dump/players.csv', ROOT), 'utf8').split(/\r?\n/)
  const header = lines[0].split(',')
  const idx = Object.fromEntries(header.map((k, i) => [k, i]))
  const codes = JSON.parse(readFileSync(new URL('src/main/resources/seed/le-role-codes.json', ROOT), 'utf8')).codes
  const normalize = (s) => s.toLowerCase().replace(/[^a-z0-9]/g, '')
  const idByName = Object.fromEntries(roles.map((r) => [normalize(r.name), r.id]))
  const players = []
  for (const line of lines.slice(1)) {
    if (!line) continue
    const f = line.split(',')
    const positions = []
    for (let i = 1; i <= 7; i += 1) {
      const p = POSITION_CODES[Number(f[idx['preferredposition' + i]])]
      if (p && !positions.includes(p)) positions.push(p)
    }
    if (!positions.length) continue
    const attrs = ATTR_KEYS.map((k) => Number(f[idx[DUMP_COLUMN(k)]]))
    const positive = new Set()
    for (let i = 1; i <= 9; i += 1) {
      const code = Number(f[idx['role' + i]])
      if (!code) continue
      const def = codes[String(code % 100)]
      const roleId = def && idByName[normalize(def.role)]
      if (roleId) positive.add(def.position + '|' + roleId)
    }
    players.push({ id: Number(f[idx.playerid]), gender: Number(f[idx.gender]), ovr: Number(f[idx.overallrating]), positions, attrs, positive })
  }
  return players
}

const utility = (gender, position, attrs) => {
  const group = (norms.genders[gender === 1 ? 'women' : 'men'] ?? {})[position] ?? norms.genders.men[position]
  return ATTR_KEYS.map((k, i) => {
    const n = group?.attrs?.[NORM_KEY(k)] ?? norms.genders.men[position]?.attrs?.[NORM_KEY(k)]
    return n ? 1 / (1 + Math.exp(-(attrs[i] - n.p50) / Math.max(n.sd, 3))) : 0.5
  })
}

// ---- Rol başına sabit parçalar: s = A + B·(E·x), x = u − 0.5 (çekirdek skor; cezalar/PS/aşinalık sabit eklemelerdir ve öğrenmeye girmez)
const emphasisVec = (id) => ATTR_KEYS.map((k) => EMPHASIS[id]?.[k] ?? 0)
const DEFAULT_MASS = (() => {
  const masses = roles.map((r) => emphasisVec(r.id).reduce((a, b) => a + b, 0)).filter((m) => m > 0).sort((a, b) => a - b)
  return masses[Math.floor(masses.length / 2)]
})()

/** Focus başına sabit ham kütle C (taban + focus + EA OVR payı) ve Zc = Σ C; vektör = (C + K·E) / (Zc + K·ΣE). OVR payı eski kütleyle (≈ 0,2) sabit alınır; son vektör assemble() ile tam kuralla üretilir. */
function roleParts(position, role, mass) {
  const group = role.weightGroup
  return role.focuses.map((focus) => {
    const raw = {}
    const base = norm1(calib.positionBaseline[group] ?? {})
    for (const [a, v] of Object.entries(base)) raw[a] = (raw[a] ?? 0) + v * BASE_SHARE
    for (const [a, v] of Object.entries(FOCUS_BY_GROUP[group]?.[focus] ?? FOCUS[focus] ?? {})) raw[a] = (raw[a] ?? 0) + v * K_FOCUS
    const rest = Object.values(raw).reduce((a, b) => a + b, 0) + K_EMPHASIS * mass
    const ovrShare = Math.min(OVR_SHARE, rest * 0.9)
    for (const [a, v] of Object.entries(ovrOf(position))) raw[a] = (raw[a] ?? 0) + v * ovrShare
    const C = Float64Array.from(ATTR_KEYS.map((k) => raw[k] ?? 0))
    return { C, Zc: C.reduce((a, b) => a + b, 0) }
  })
}

// ---- Eğitim / değerlendirme örnekleri
function buildSamples(players) {
  const samples = []
  for (const p of players) {
    for (const position of p.positions) {
      const here = roles.filter((r) => r.positions.includes(position))
      const pos = here.filter((r) => p.positive.has(position + '|' + r.id))
      const neg = here.filter((r) => !p.positive.has(position + '|' + r.id))
      if (!pos.length || !neg.length) continue
      samples.push({ half: half(p.id), id: p.id, position, x: utility(p.gender, position, p.attrs).map((u) => u - 0.5), pos: pos.map((r) => r.id), neg: neg.map((r) => r.id), here: here.map((r) => r.id) })
    }
  }
  return samples
}

function main() {
  const players = loadPlayers()
  const samples = buildSamples(players)
  const byId = Object.fromEntries(roles.map((r) => [r.id, r]))
  console.log(`oyuncu ${players.length}, oyuncu-mevki örneği ${samples.length}, eğitim ${samples.filter((s) => s.half === 0).length} / test ${samples.filter((s) => s.half === 1).length}`)

  // Kütle ve başlangıç: eski vurgu (kütlesi 0 olan rol için ortanca kütle)
  const mass = {}
  const Eold = {}
  for (const r of roles) {
    const e = emphasisVec(r.id)
    const m = e.reduce((a, b) => a + b, 0)
    mass[r.id] = m > 0 ? m : DEFAULT_MASS
    Eold[r.id] = m > 0 ? Float64Array.from(e) : new Float64Array(ATTR_KEYS.length)
  }

  // Parçalar (mevki, rol)
  const parts = {}
  const partsFor = (position, id) => (parts[position + '|' + id] ??= roleParts(position, byId[id], mass[id]))
  const score = (s, id, E) => {
    const focuses = partsFor(s.position, id)
    let sumE = 0
    let ex = 0
    for (let i = 0; i < s.x.length; i += 1) { sumE += E[i]; ex += E[i] * s.x[i] }
    let total = 0
    for (const { C, Zc } of focuses) {
      let cx = 0
      for (let i = 0; i < s.x.length; i += 1) cx += C[i] * s.x[i]
      total += (100 * (cx + K_EMPHASIS * ex)) / (Zc + K_EMPHASIS * sumE)
    }
    return total / focuses.length
  }
  /** d skor / d E_j (focus ortalaması): 100·K·(x_j − S_f/100) / Z_f */
  const addScoreGrad = (s, id, E, coef, out) => {
    const focuses = partsFor(s.position, id)
    let sumE = 0
    let ex = 0
    for (let i = 0; i < s.x.length; i += 1) { sumE += E[i]; ex += E[i] * s.x[i] }
    for (const { C, Zc } of focuses) {
      let cx = 0
      for (let i = 0; i < s.x.length; i += 1) cx += C[i] * s.x[i]
      const Z = Zc + K_EMPHASIS * sumE
      const sf = (100 * (cx + K_EMPHASIS * ex)) / Z
      for (let i = 0; i < s.x.length; i += 1) out[i] += (coef * 100 * K_EMPHASIS * (s.x[i] - sf / 100)) / (Z * focuses.length)
    }
  }

  // Öğrenilecek roller: --roles verilmişse o, yoksa proxy ile (eğitim yarısı) zayıf olanlar
  const aucBy = (E, which) => {
    const stat = {}
    for (const s of samples.filter((q) => q.half === which)) {
      const sc = {}
      for (const id of s.here) sc[id] = score(s, id, E[id])
      for (const rp of s.pos) for (const rn of s.neg) {
        const k = s.position + '|' + rp
        const st = (stat[k] ??= [0, 0])
        st[0] += sc[rp] > sc[rn] ? 1 : sc[rp] === sc[rn] ? 0.5 : 0
        st[1] += 1
      }
    }
    return stat
  }
  // standartlaştırma: (mevki, rol) başına eğitim ortalaması/sapması
  const standardize = (E, which) => {
    const acc = {}
    for (const s of samples.filter((q) => q.half === which)) for (const id of s.here) {
      const k = s.position + '|' + id
      const a = (acc[k] ??= [0, 0, 0])
      const v = score(s, id, E[id])
      a[0] += v; a[1] += v * v; a[2] += 1
    }
    return Object.fromEntries(Object.entries(acc).map(([k, [sum, sq, n]]) => [k, { mu: sum / n, sd: Math.sqrt(Math.max(sq / n - (sum / n) ** 2, 1e-6)) }]))
  }
  const proxyAuc = (E, which) => {
    const st = standardize(E, 0) // standartlaştırma her zaman eğitim yarısı istatistiğiyle
    const stat = {}
    for (const s of samples.filter((q) => q.half === which)) {
      const z = {}
      for (const id of s.here) { const t = st[s.position + '|' + id]; z[id] = (score(s, id, E[id]) - t.mu) / t.sd }
      for (const rp of s.pos) for (const rn of s.neg) {
        const k = s.position + '|' + rp
        const a = (stat[k] ??= [0, 0])
        a[0] += z[rp] > z[rn] ? 1 : z[rp] === z[rn] ? 0.5 : 0
        a[1] += 1
      }
    }
    return Object.fromEntries(Object.entries(stat).map(([k, [w, n]]) => [k, w / n]))
  }

  const base = proxyAuc(Eold, 0)
  const only = arg('roles', '')
  const weakRoles = only ? only.split(',') : roles.map((r) => r.id).filter((id) => Object.entries(base).some(([k, v]) => k.endsWith('|' + id) && v < WEAK_AUC))
  console.log('öğrenilecek roller (proxy eğitim AUC < ' + WEAK_AUC + '):', weakRoles.join(', '))

  // ---- Optimizasyon (Adam, simplex izdüşümü), e = E / kütle
  const E = Object.fromEntries(roles.map((r) => [r.id, Float64Array.from(Eold[r.id])]))
  const train = samples.filter((q) => q.half === 0)
  // Alan kısıtı (futbol akla yatkınlığı; ilk deneme forvete kaleci attribute'u öğretti): saha oyuncusu rolünde kaleci attribute'u yok; kaleci rolünde yalnız kaleci + oyun kurma/refleks attribute'ları.
  const GK_ROLE_OK = new Set(['Reactions', 'Composure', 'ShortPassing', 'LongPassing', 'Vision', 'Agility', 'Jumping', 'Strength'])
  const allowed = (id) => ATTR_KEYS.map((k) => {
    const keeperRole = byId[id].weightGroup === 'GK'
    if (keeperRole) return k.startsWith('GK') || GK_ROLE_OK.has(k)
    return !k.startsWith('GK') && !(DEFENSIVE_GROUPS.has(byId[id].weightGroup) && ATTACKING.has(k))
  })
  const m1 = {}
  const m2 = {}
  weakRoles.forEach((id) => { m1[id] = new Float64Array(ATTR_KEYS.length); m2[id] = new Float64Array(ATTR_KEYS.length) })
  const project = (id, mask) => {
    const e = E[id]
    let sum = 0
    for (let i = 0; i < e.length; i += 1) { e[i] = mask[i] ? Math.max(e[i], 0) : 0; sum += e[i] }
    const lo = MASS_MIN * mass[id]
    const hi = MASS_MAX * mass[id]
    if (sum === 0) { const k = mask.filter(Boolean).length; for (let i = 0; i < e.length; i += 1) e[i] = mask[i] ? lo / k : 0 }
    else if (sum < lo || sum > hi) { const f = (sum < lo ? lo : hi) / sum; for (let i = 0; i < e.length; i += 1) e[i] *= f }
  }
  const lr = Number(arg("lr", 0.01))
  const fit = (ids, masks, iters) => {
    for (let it = 1; it <= iters; it += 1) {
      const st = standardize(E, 0)
      const grad = Object.fromEntries(ids.map((id) => [id, new Float64Array(ATTR_KEYS.length)]))
      for (const s of train) {
        const z = {}
        for (const id of s.here) { const t = st[s.position + '|' + id]; z[id] = (score(s, id, E[id]) - t.mu) / t.sd }
        const dz = {}
        for (const rp of s.pos) for (const rn of s.neg) {
          const g = 1 / (1 + Math.exp(z[rp] - z[rn]))
          dz[rp] = (dz[rp] ?? 0) - g
          dz[rn] = (dz[rn] ?? 0) + g
        }
        for (const [id, d] of Object.entries(dz)) {
          if (!grad[id]) continue
          const t = st[s.position + '|' + id]
          addScoreGrad(s, id, E[id], d / t.sd, grad[id])
        }
      }
      for (const id of ids) {
        const n = train.length
        for (let i = 0; i < ATTR_KEYS.length; i += 1) {
          const g = (grad[id][i] / n) * mass[id] + (2 * LAMBDA * (E[id][i] - Eold[id][i])) / mass[id]
          m1[id][i] = 0.9 * m1[id][i] + 0.1 * g
          m2[id][i] = 0.999 * m2[id][i] + 0.001 * g * g
          const step = (lr * (m1[id][i] / (1 - 0.9 ** it))) / (Math.sqrt(m2[id][i] / (1 - 0.999 ** it)) + 1e-8)
          E[id][i] -= step * mass[id]
        }
        project(id, masks[id])
      }
    }
  }
  const masks = Object.fromEntries(weakRoles.map((id) => [id, allowed(id)]))
  // Roller birbirinin negatifi olduğundan eşzamanlı öğrenme birbirini bozar: sırayla, her rol için diğerleri sabitken öğren; küresel eğitim AUC'si
  // (tüm Role+/++ anahtarlarının ortalaması) iyileşmezse o rolün eski vurgusuna dönülür.
  const globalMean = (m) => { const v = Object.values(m); return v.reduce((a, b) => a + b, 0) / v.length }
  const reverted = []
  let current = globalMean(proxyAuc(E, 0))
  for (const id of weakRoles) {
    m1[id].fill(0); m2[id].fill(0)
    fit([id], masks, ITER)
    const order = [...E[id].keys()].sort((a, b) => E[id][b] - E[id][a])
    const keep = new Set(order.slice(0, MAX_ACTIVE))
    masks[id] = masks[id].map((ok, i) => ok && keep.has(i))
    project(id, masks[id])
    fit([id], masks, Math.round(ITER / 4))
    const after = globalMean(proxyAuc(E, 0))
    if (after < current + 0.0003) { E[id] = Float64Array.from(Eold[id]); reverted.push(id) } else current = after
    console.log(`  ${id}: küresel eğitim AUC ${after.toFixed(4)} ${reverted.includes(id) ? '(iyileşmedi → eski vurgu)' : '(kabul)'}`)
  }
  // ---- Çıktı: öğrenilen vurgular + aday vektör dosyaları
  const dir = new URL('target/role-candidates/', ROOT)
  mkdirSync(dir, { recursive: true })
  const seedDoc = JSON.parse(readFileSync(new URL('src/main/resources/seed/role-vectors.fc27.json', ROOT), 'utf8'))
  const learned = {}
  const toObj = (e) => Object.fromEntries(ATTR_KEYS.map((k, i) => [k, Math.round(e[i] * 1e6) / 1e6]).filter(([, v]) => v > 0))
  const candidateDoc = (ids) => {
    const doc = JSON.parse(JSON.stringify(seedDoc))
    for (const id of ids) {
      const role = byId[id]
      for (const position of role.positions) for (const focus of role.focuses) {
        const { w } = assemble(position, role, focus, toObj(E[id]))
        doc.vectors[`${position}|${id}|${focus}`] = Object.fromEntries(Object.entries(w).sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => [k, Math.round(v * 1e6) / 1e6]))
      }
    }
    return doc
  }
  const violations = (doc, id) => {
    const out = []
    const role = byId[id]
    for (const position of role.positions) {
      const mine = role.focuses.map((f) => doc.vectors[`${position}|${id}|${f}`])
      const others = Object.entries(doc.vectors).filter(([k]) => k.startsWith(position + '|') && k.split('|')[1] !== id)
      for (let i = 0; i < mine.length; i += 1) {
        for (let j = i + 1; j < mine.length; j += 1) if (cosine(mine[i], mine[j]) > 0.95) out.push(`focus kosinüsü ${position}|${id} ${cosine(mine[i], mine[j]).toFixed(3)} > 0,95`)
        for (const [k, v] of others) if (cosine(mine[i], v) > 0.9) out.push(`rol kosinüsü ${position}|${id} ~ ${k} ${cosine(mine[i], v).toFixed(3)} > 0,90`)
      }
    }
    return out
  }
  const proxyTrain = proxyAuc(E, 0)
  const proxyTest = proxyAuc(E, 1)
  const baseTest = proxyAuc(Eold, 1)
  for (const id of weakRoles) {
    const doc = candidateDoc([id])
    if (!reverted.includes(id)) writeFileSync(new URL(`candidate-${id}.json`, dir), JSON.stringify(doc))
    learned[id] = { reverted: reverted.includes(id), mass: mass[id], old: toObj(Eold[id]), new: toObj(E[id]), active: Object.keys(toObj(E[id])).length, violations: violations(doc, id),
      proxy: Object.fromEntries(roles.find((r) => r.id === id).positions.map((p) => [p, { trainBefore: base[p + '|' + id], trainAfter: proxyTrain[p + '|' + id], testBefore: baseTest[p + '|' + id], testAfter: proxyTest[p + '|' + id] }])) }
  }
  writeFileSync(new URL('candidate-all.json', dir), JSON.stringify(candidateDoc(weakRoles)))
  writeFileSync(new URL('learned.json', dir), JSON.stringify({ lambda: LAMBDA, iter: ITER, weakRoles, learned }, null, 1))
  for (const id of weakRoles) {
    const p = Object.values(learned[id].proxy)
    const avg = (key) => (p.reduce((a, b) => a + (b[key] ?? 0), 0) / p.length).toFixed(3)
    console.log(`${id.padEnd(24)} proxy test ${avg('testBefore')} → ${avg('testAfter')} | eğitim ${avg('trainBefore')} → ${avg('trainAfter')} | aktif ${learned[id].active}${learned[id].violations.length ? ' | İHLAL ' + learned[id].violations.length : ''}`)
  }
}

if (process.argv[1]?.endsWith('learn-role-vectors.mjs')) main()
