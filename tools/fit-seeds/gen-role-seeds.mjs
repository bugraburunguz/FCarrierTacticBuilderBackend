// E3-4 (13 §3.2 madde 2-7): rol vektörleri v3 + temel attribute'lar + PlayStyle + aşinalık/ayak/fiziksel seed'leri.
// Girdi: seed/position-overall-weights.json (EA OVR regresyonu), role-calibration-v2.json (rol vurguları), roles.json, attr-norms.fc27.json.
// Çıktı: role-vectors / role-requirements / role-playstyles / role-modifiers (.fc27.json + .hash). Hepsi verified:false; her delta grubunda _basis var.
// Kullanım: node tools/fit-seeds/gen-role-seeds.mjs  (önce gen-attr-norms.mjs). Tüm sayılar DATA'dır; motor koduna gömülmez.
import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'

const dir = new URL('../../src/main/resources/seed/', import.meta.url)
const read = (n) => JSON.parse(readFileSync(new URL(n, dir), 'utf8'))
const overall = read('position-overall-weights.json')
const calib = read('role-calibration-v2.json')
const roles = read('roles.json')
const norms = read('attr-norms.fc27.json')

const COLUMN_TO_KEY = {
  acceleration: 'Acceleration', sprint_speed: 'SprintSpeed', agility: 'Agility', balance: 'Balance', reactions: 'Reactions', ball_control: 'BallControl', dribbling: 'Dribbling',
  composure: 'Composure', vision: 'Vision', short_passing: 'ShortPassing', long_passing: 'LongPassing', crossing: 'Crossing', curve: 'Curve', fk_accuracy: 'FKAccuracy',
  finishing: 'Finishing', shot_power: 'ShotPower', long_shots: 'LongShots', volleys: 'Volleys', penalties: 'Penalties', att_position: 'Positioning', heading: 'Heading',
  def_awareness: 'DefAwareness', standing_tackle: 'StandingTackle', sliding_tackle: 'SlidingTackle', interceptions: 'Interceptions', jumping: 'Jumping', stamina: 'Stamina',
  strength: 'Strength', aggression: 'Aggression', gk_diving: 'GKDiving', gk_handling: 'GKHandling', gk_kicking: 'GKKicking', gk_positioning: 'GKPositioning', gk_reflexes: 'GKReflexes',
}
const NORM_KEY = (k) => (k === 'FKAccuracy' ? 'freeKickAccuracy' : k.startsWith('GK') ? 'gk' + k.slice(2) : k[0].toLowerCase() + k.slice(1))

// ---- Ayarlanabilir (CI ile doğrulanan) katsayılar
const OVR_SHARE = 0.2 // EA pozisyon OVR regresyonunun ham kütlesi (nihai pay ≤ 0,5 olmak zorunda: CI)
const BASE_SHARE = 0.1 // v2 pozisyon taban profili
const K_EMPHASIS = Number(process.env.KE ?? 14)
const K_FOCUS = Number(process.env.KF ?? 20)

// ---- Rol vurguları: v2 (24 rol) + eksik 8 rol (EA rol açıklamasından elle, gözlem)
const EMPHASIS = { ...calib.roleEmphasis }
const EMPHASIS_BASIS = Object.fromEntries(Object.keys(EMPHASIS).map((k) => [k, 'role-calibration-v2.json (başlangıç sayıları)']))
const ADDED = {
  goalkeeper: [{}, 'EA rol açıklaması: temel kaleci, ek vurgu yok'],
  ball_playing_keeper: [{ GKKicking: 0.08, ShortPassing: 0.06, LongPassing: 0.04, Composure: 0.03, Vision: 0.03 }, 'EA rol açıklaması (gözlem): pasla oyun kuran kaleci'],
  inverted_wingback: [{ ShortPassing: 0.06, Composure: 0.04, Vision: 0.04, BallControl: 0.04 }, 'EA rol açıklaması (gözlem): içeri girip oyun kuran bek'],
  falseback: [{ ShortPassing: 0.07, DefAwareness: 0.04, Composure: 0.03, Interceptions: 0.05, Vision: 0.02 }, 'EA rol açıklaması (gözlem): stoper hattına çöken bek'],
  wide_back: [{ Acceleration: 0.06, SprintSpeed: 0.05, DefAwareness: 0.03, Stamina: 0.03, Crossing: 0.02 }, 'EA rol açıklaması (gözlem): kanatta oynayan stoper'],
  wide_half: [{ Stamina: 0.06, Crossing: 0.06, SprintSpeed: 0.03 }, 'EA rol açıklaması (gözlem): kanat koşucusu 6'],
  half_winger: [{ Dribbling: 0.06, Crossing: 0.05, Stamina: 0.04, ShortPassing: 0.04 }, 'EA rol açıklaması (gözlem): yarım kanat'],
  wide_midfielder: [{ Stamina: 0.06, DefAwareness: 0.04, Crossing: 0.05 }, 'EA rol açıklaması (gözlem): kanat orta saha'],
}
// Aynı pozisyondaki yakın rolleri ayıran ek vurgular (CI: roller arası kosinüs ≤ 0,90); EA rol açıklaması, gözlem
const ADJUST = {
  centre_half: [{ Strength: 0.03, Heading: 0.03, StandingTackle: 0.02, Aggression: 0.03 }, 'centre_half: savunma 6 numarası, hava ve güç'],
  deep_lying_playmaker: [{ LongPassing: 0.03, DefAwareness: 0.03, Interceptions: 0.02 }, 'deep_lying_playmaker: derinden uzun pasla oyun kurma (baskınlık sınırı sonrası playmaker ile ayrışma, E3-6b)'],
  box_to_box: [{ StandingTackle: 0.01, Interceptions: 0.01, Strength: 0.01, Aggression: 0.01 }, 'box_to_box: koşu + savunma katkısı (EA: stamina, top kazanma); Stamina %25 sınırına çekildiği için fazla, rol tanımındaki diğer attribute lara dağıtılır (E3-6b)'],
  holding: [{ Jumping: 0.05, Stamina: 0.02, BallControl: 0.01, ShortPassing: 0.01 }, 'holding: orta sahayı önde koruyan 6; hava düellosu (zıplama), koşu, ikincil pas. Altın set Khedira>Xhaka kesin çiftine göre ayarlandı (rapora yazıldı), rol kosinüsü ≤0,90'],
  wingback: [{ DefAwareness: 0.04, StandingTackle: 0.03, Interceptions: 0.02 }, 'wingback: bekin savunma yükü'],
  attacking_wingback: [{ Dribbling: 0.04, BallControl: 0.02, Finishing: 0.03, Positioning: 0.03, Crossing: 0.06 }, 'attacking_wingback: hücumda dripling ve bitiricilik'],
  advanced_forward: [{ Dribbling: 0.04, Acceleration: 0.03, SprintSpeed: 0.03, Vision: 0.02 }, 'advanced_forward: derinliğe koşan, çalım ve hız'],
  poacher: [{ Positioning: 0.05, Composure: 0.03, Volleys: 0.02 }, 'poacher: ceza sahası pozisyon alma ve soğukkanlılık'],
}
for (const [id, [vec, why]] of Object.entries(ADJUST)) {
  EMPHASIS[id] = { ...EMPHASIS[id] }
  for (const [a, v] of Object.entries(vec)) EMPHASIS[id][a] = (EMPHASIS[id][a] ?? 0) + v
  EMPHASIS_BASIS[id] += ' + ' + why
}
for (const [id, [vec, why]] of Object.entries(ADDED)) {
  EMPHASIS[id] = vec
  EMPHASIS_BASIS[id] = why
}
// Focus deltaları (rol bağımsız genel kural; _basis: EA focus açıklaması, gözlem)
const FOCUS = {
  Attack: { Finishing: 0.04, Positioning: 0.04, ShotPower: 0.02, Acceleration: 0.02 },
  Defend: { DefAwareness: 0.04, StandingTackle: 0.03, Interceptions: 0.03, Strength: 0.01 },
  Balanced: {},
  Support: { ShortPassing: 0.04, Vision: 0.03, BallControl: 0.02 },
  'Build-Up': { ShortPassing: 0.05, Vision: 0.04, Composure: 0.04, LongPassing: 0.03 },
  'Ball-Winning': { StandingTackle: 0.05, Interceptions: 0.04, Aggression: 0.04, Stamina: 0.03 },
  Roaming: { Stamina: 0.04, Agility: 0.03, Acceleration: 0.03, Dribbling: 0.02 },
  Wide: { Crossing: 0.05, SprintSpeed: 0.03, Heading: 0.02 },
  Versatile: { Stamina: 0.03, Agility: 0.02, Acceleration: 0.02, Vision: 0.02 },
  Aggressive: { Aggression: 0.07, StandingTackle: 0.04, Strength: 0.04 },
}
// Kaleci ve bek/kanat için focus'ın anlamı farklı: pozisyon grubuna özel üzerine yazma
const FOCUS_BY_GROUP = {
  GK: { Defend: { GKPositioning: 0.03, GKReflexes: 0.02 }, 'Build-Up': { GKKicking: 0.06, ShortPassing: 0.02, Composure: 0.02 }, Balanced: {} },
  FB: { Balanced: { Crossing: 0.03, DefAwareness: 0.02, Stamina: 0.02 }, Attack: { Crossing: 0.05, Acceleration: 0.03, Dribbling: 0.02 }, Support: { Crossing: 0.05, Stamina: 0.04, ShortPassing: 0.03 }, Defend: { DefAwareness: 0.05, StandingTackle: 0.04, Interceptions: 0.03 }, 'Build-Up': { ShortPassing: 0.05, Composure: 0.04, Vision: 0.03 } },
}

const add = (target, vec, k = 1) => {
  for (const [a, v] of Object.entries(vec)) target[a] = (target[a] ?? 0) + v * k
}
const norm1 = (vec) => {
  const sum = Object.values(vec).reduce((a, b) => a + b, 0)
  return Object.fromEntries(Object.entries(vec).map(([k, v]) => [k, v / sum]))
}
const cosine = (a, b) => {
  let dot = 0
  let na = 0
  let nb = 0
  for (const k of new Set([...Object.keys(a), ...Object.keys(b)])) {
    dot += (a[k] ?? 0) * (b[k] ?? 0)
    na += (a[k] ?? 0) ** 2
    nb += (b[k] ?? 0) ** 2
  }
  return dot / Math.sqrt(na * nb)
}
const round6 = (v) => Math.round(v * 1e6) / 1e6

// EA OVR regresyonu: pozitif ağırlıklar, normalize (13 §3.2: kütlenin ≤ %50'si)
const ovrOf = (position) => {
  const raw = overall[position]?.weights ?? {}
  const pos = {}
  for (const [col, w] of Object.entries(raw)) if (w > Number(process.env.OVR_MIN ?? 0.03) && COLUMN_TO_KEY[col]) pos[COLUMN_TO_KEY[col]] = w
  return norm1(pos)
}

// Tek özellik baskınlığı yok (Buğra kararı, E3-6b): hiçbir attribute vektör kütlesinin %25'ini geçemez. Fazla, vektördeki MEVCUT diğer attribute'lara orantılı dağıtılır
// (yeni attribute eklenmez); dağıtım sırasında yeni aşanlar da %25'e kırpılır (su doldurma).
export const MAX_SHARE = 0.25
// Kaleci ailesi muaf: kaleci vektörü 4-5 kaleci attribute'undan oluşur; %25 sınırı dördünü eşitler ve focus/rol ayrımını yok eder (kosinüs değişmezi ihlali). Eski vektör korunur, raporda belirtilir.
export const CAP_EXEMPT_GROUPS = ['GK']
export function capShare(vec, preferred = null) {
  const out = { ...vec }
  const capped = new Set()
  for (let i = 0; i < 100; i += 1) {
    const over = Object.keys(out).filter((k) => !capped.has(k) && out[k] > MAX_SHARE + 1e-12)
    if (!over.length) return out
    let excess = 0
    for (const k of over) { excess += out[k] - MAX_SHARE; out[k] = MAX_SHARE; capped.add(k) }
    // Fazla önce rolün TANIMINDAKİ (vurgu/focus) attribute'lara dağıtılır; bunlar yetmezse vektördeki diğerlerine.
    let rest = Object.keys(out).filter((k) => !capped.has(k) && (!preferred || preferred.has(k)))
    if (!rest.length) rest = Object.keys(out).filter((k) => !capped.has(k))
    const base = rest.reduce((s, k) => s + out[k], 0)
    if (!rest.length || base <= 0) throw new Error('baskınlık sınırı sağlanamadı (attribute sayısı < 4): ' + JSON.stringify(vec))
    for (const k of rest) out[k] += (excess * out[k]) / base
  }
  return out
}

/** (mevki, rol, focus) vektörü: taban + rol vurgusu + focus + EA OVR regresyonu. E3-4b öğrenici vurguyu `emphasis` ile değiştirerek aynı kuralı kullanır. */
export function assemble(position, role, focus, emphasis = EMPHASIS[role.id] ?? {}) {
  const raw = {}
  add(raw, norm1(calib.positionBaseline[role.weightGroup] ?? {}), BASE_SHARE)
  add(raw, emphasis, K_EMPHASIS)
  add(raw, FOCUS_BY_GROUP[role.weightGroup]?.[focus] ?? FOCUS[focus] ?? {}, K_FOCUS)
  // EA OVR regresyonu kütlenin en fazla %50'si: rol/focus kütlesi azsa OVR payı da küçülür (CI: ovrMass ≤ 0,5)
  const rest = Object.values(raw).reduce((a, b) => a + b, 0)
  const ovrShare = Math.min(OVR_SHARE, rest * 0.9)
  add(raw, ovrOf(position), ovrShare)
  const uncapped = norm1(raw)
  return { w: role.weightGroup === 'GK' ? uncapped : capShare(uncapped, new Set([...Object.keys(emphasis), ...Object.keys(FOCUS_BY_GROUP[role.weightGroup]?.[focus] ?? FOCUS[focus] ?? {})])), uncapped, rest, ovrShare, rawSum: rest + ovrShare, constant: constantPart(position, role, focus) }
}

/** Vurgudan bağımsız (ham) kütle: taban + focus + OVR payı (OVR payı vurgu kütlesine bağlıdır; bu yüzden öğrenici assemble'ı çağırır). */
function constantPart(position, role, focus) {
  const raw = {}
  add(raw, norm1(calib.positionBaseline[role.weightGroup] ?? {}), BASE_SHARE)
  add(raw, FOCUS_BY_GROUP[role.weightGroup]?.[focus] ?? FOCUS[focus] ?? {}, K_FOCUS)
  return raw
}

export { EMPHASIS, EMPHASIS_BASIS, FOCUS, FOCUS_BY_GROUP, K_EMPHASIS, K_FOCUS, OVR_SHARE, BASE_SHARE, roles, norms, calib, ovrOf, norm1, cosine, COLUMN_TO_KEY, NORM_KEY, ADJUST }
const IS_MAIN = process.argv[1]?.endsWith('gen-role-seeds.mjs')

const vectors = {}
const ovrMass = {}
const byPosition = {}
for (const role of roles) {
  for (const position of role.positions) {
    for (const focus of role.focuses) {
      const { w, ovrShare, rawSum } = assemble(position, role, focus)
      ovrMass[`${position}|${role.id}|${focus}`] = Math.round((ovrShare / rawSum) * 1000) / 1000
      const key = `${position}|${role.id}|${focus}`
      vectors[key] = Object.fromEntries(Object.entries(w).sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => [k, round6(v)]))
      ;(byPosition[position] ??= []).push({ key, role: role.id, focus, w })
    }
  }
}
// CI özeti (Java testi aynı değişmezleri doğrular): focus varyantları ≤ 0,95; aynı pozisyonda farklı roller ≤ 0,90
let maxFocus = 0
let maxRole = 0
const worst = []
for (const list of Object.values(byPosition)) {
  for (let i = 0; i < list.length; i += 1) {
    for (let j = i + 1; j < list.length; j += 1) {
      const c = cosine(list[i].w, list[j].w)
      worst.push([c, list[i].key, list[j].key, list[i].role === list[j].role])
      if (list[i].role === list[j].role) maxFocus = Math.max(maxFocus, c)
      else maxRole = Math.max(maxRole, c)
    }
  }
}
if (process.env.WORST) console.log(worst.sort((a, b) => b[0] - a[0]).filter((w) => w[0] > (w[3] ? 0.95 : 0.9)).slice(0, 30).map((w) => `${w[0].toFixed(3)} ${w[3] ? 'FOCUS' : 'ROL'} ${w[1]} ~ ${w[2]}`).join(String.fromCharCode(10)))
if (IS_MAIN) console.log(`vektör: ${Object.keys(vectors).length}; en yüksek focus kosinüsü ${maxFocus.toFixed(3)} (≤0,95), roller arası ${maxRole.toFixed(3)} (≤0,90)`)

// ---- Temel attribute'lar: roles.json silahları → pozisyona göre z (düzey bağımsız), ENG-18/Stamina kuralları uygulanır
const ATTACKING = new Set(['Finishing', 'Positioning', 'ShotPower', 'LongShots', 'Volleys', 'Penalties'])
const DEFENSIVE_GROUPS = new Set(['CB', 'GK'])
const HIGH_WORKLOAD = new Set(['box_to_box', 'wingback', 'attacking_wingback', 'half_winger'])
const requirements = {}
for (const role of roles) {
  const position = role.positions[0]
  const out = []
  for (const weapon of role.weapons) {
    if (DEFENSIVE_GROUPS.has(role.weightGroup) && ATTACKING.has(weapon.attr)) continue // ENG-18: savunma rolünde hücum attribute'u temel olamaz
    if (weapon.attr === 'Stamina' && !HIGH_WORKLOAD.has(role.id)) continue // 13 §3.2 madde 3: Stamina tabanı yalnız yüksek iş yüklü rollerde
    const n = norms.genders.men[position]?.attrs[NORM_KEY(weapon.attr)]
    if (!n) continue
    const z = (v) => Math.max(-1.5, Math.min(1.5, Math.round(((v - n.p50) / Math.max(n.sd, 3)) * 100) / 100))
    out.push({ attr: weapon.attr, minZ: z(weapon.hardMin), targetZ: z(weapon.target), tauZ: 1.0 })
  }
  if (out.length === 0 && role.weapons[0]) {
    // silahlar elendiyse rolün karakteristik tek temel attribute'u emphasis'in en büyüğü
    const top = Object.entries(EMPHASIS[role.id] ?? {}).sort((a, b) => b[1] - a[1])[0]
    if (top) out.push({ attr: top[0], minZ: -0.5, targetZ: 0.75, tauZ: 1.0 })
  }
  requirements[role.id] = out.slice(0, 4)
}

// ---- PlayStyle: rol başına öncelikli PS (PS+ = 2×, toplam ≤ 8 puan)
const playstyles = {}
for (const role of roles) {
  playstyles[role.id] = (role.priorityPlaystyles ?? []).map((ps) => ({ ps, weight: 4 }))
}

// ---- Aşinalık, ayak/kanat, fiziksel (config; verified:false)
const modifiers = {
  scoring: { baseline: 50, baseAttrPenaltyK: 3, _basis: '13 §3.2: fit = 50 + Σ w_i·100·(u_i − 0,5) − k·softplus((min − a)/τ − 2) + PS + FAM + PHYS + FOOT; k=3 elle (1 attribute puanı ≤ 1,5 puan)' },
  familiarity: { none: 0, rolePlus: 2, rolePlusPlus: 4, _basis: '13 §3.2 madde 5: ölçülene kadar f1=2, f2=4' },
  psCap: { maxTotal: 8, psPlusMultiplier: 2, _basis: '13 §3.2 madde 4' },
  foot: {
    cutInside: { roles: ['inside_forward', 'wide_playmaker', 'inverted_wingback', 'half_winger'], oppositeFootBonus: 2, _basis: 'içe kat eden roller karşı ayakta (13 §3.2 madde 7)' },
    crossing: { roles: ['winger', 'wingback', 'attacking_wingback'], sameFootBonus: 1.5, _basis: 'orta açan roller aynı ayakta' },
    weakFootScale: '(5 − weak_foot) / 4',
    skillMoves: { roles: ['inside_forward', 'wide_playmaker', 'winger', 'classic_10', 'half_winger'], perStar: 0.5, _basis: 'çalım rollerinde küçük terim' },
  },
  physical: {
    aerial: { roles: ['target_forward', 'defender', 'centre_half', 'stopper', 'ball_playing_defender', 'wide_back', 'goalkeeper', 'sweeper_keeper', 'ball_playing_keeper'], attrs: ['Heading', 'Jumping', 'Strength'], heightCmRef: 185, maxPoints: 3, _basis: 'hava = f(boy, Jumping, Heading, Strength) rol ağırlıklı' },
    accelerate: { maxPoints: 3, derivedFactor: 0.5, _basis: 'AcceleRATE ±3 puan; türetilmişse etkisi yarıya (13 §3.2 madde 6)' },
  },
}

const files = {
  'role-vectors.fc27.json': {
    _source: 'tools/fit-seeds/gen-role-seeds.mjs: 0,3·EA OVR regresyonu + 0,3·v2 pozisyon tabanı + rol vurgusu×k + focus×k, normalize (toplam 1)',
    verified: false,
    _verify: 'Rol/focus deltaları elle yazılmış başlangıç sayılarıdır (EA rol açıklaması, gözlem); golden set ile ayarlanır. CI: focus kosinüsü ≤ 0,95, roller arası ≤ 0,90, iki vektör aynı değil, OVR kütlesi ≤ %50.',
    _basis: { emphasis: EMPHASIS_BASIS, focus: 'EA focus açıklaması (gözlem), genel kural; kaleci ve bek grubunda özel', share: { ovr: OVR_SHARE, base: BASE_SHARE, emphasisK: K_EMPHASIS, focusK: K_FOCUS } },
    _stats: { vectors: Object.keys(vectors).length, maxFocusCosine: Math.round(maxFocus * 1000) / 1000, maxRoleCosine: Math.round(maxRole * 1000) / 1000 },
    ovrMass,
    positionVectors: Object.fromEntries(Object.keys(overall).map((p) => [p, Object.fromEntries(Object.entries(ovrOf(p)).sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => [k, round6(v)]))])),
    vectors,
  },
  'role-requirements.fc27.json': {
    _source: 'roles.json silahları, pozisyon normlarına göre z-skoruna çevrildi (düzey bağımsız); ENG-18 ve Stamina kuralları uygulanır',
    verified: false,
    _basis: 'hardMin/target v1 değerleri, attr-norms p50/sd ile z: minZ=(hardMin−p50)/sd, [−1,5; 1,5]; tauZ=1,0 (softplus yumuşaklığı; 1 attribute puanı etkisi ≤ 1,5 puan CI kuralı için)',
    requirements,
  },
  'role-playstyles.fc27.json': { _source: 'roles.json priorityPlaystyles', verified: false, _basis: 'öncelikli PS ağırlığı 4 (PS+ = 2× = 8 = tavan)', playstyles },
  'role-modifiers.fc27.json': { _source: '13 §3.2 madde 4-7', verified: false, ...modifiers },
}
for (const [name, doc] of IS_MAIN ? Object.entries(files) : []) {
  const text = JSON.stringify(doc, null, 2) + '\n'
  writeFileSync(new URL(name, dir), text)
  writeFileSync(new URL(name.replace('.json', '.hash'), dir), createHash('sha256').update(text).digest('hex') + '\n')
}
if (IS_MAIN) console.log('yazıldı:', Object.keys(files).join(', '))
