// E3-7: `seed/role-movement.fc27.json` üreticisi (+ onay tablosu `E3-7_role_movement_tablo.md`).
// Sayılar EA rol tanımından (roles.json movementTags) TASLAKTIR; oyun içi gözlem yoktur, hepsi verified:false.
// Güvence değerlerde değil SIRADADIR: RoleMovementInvariantsTest. Kullanım: node tools/fit-seeds/gen-role-movement.mjs [--table <yol.md>]
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../../', import.meta.url))
const roles = JSON.parse(readFileSync(`${root}src/main/resources/seed/roles.json`, 'utf8'))

// [rol, focus, dxIn, dyFwd, w, r, d, p, s, cross, cutIn, runBehind, longShot]
// dxIn: topla ortaya kayma (−15…30; <0 = kanada açılır) · dyFwd: topla ileri kayma (−12…35; <0 = geriye düşer) · w genişlik · r ileri koşu · d savunma görevi · p pres · s stamina maliyeti · cross · cutIn · runBehind · longShot (0-1)
const ROWS = [
  ['goalkeeper', 'Defend', 0, 0, 0, 0, 1.0, 0.05, 0.10, 0, 0, 0, 0],
  ['goalkeeper', 'Balanced', 0, 1, 0, 0, 0.98, 0.08, 0.12, 0, 0, 0, 0],
  ['sweeper_keeper', 'Balanced', 0, 4, 0, 0, 0.92, 0.15, 0.20, 0, 0, 0, 0],
  ['sweeper_keeper', 'Build-Up', 0, 3, 0, 0, 0.90, 0.12, 0.20, 0, 0, 0, 0],
  ['ball_playing_keeper', 'Build-Up', 0, 2, 0, 0, 0.92, 0.08, 0.15, 0, 0, 0, 0],

  ['fullback', 'Defend', 1, 2, 0.80, 0.15, 0.90, 0.45, 0.45, 0.10, 0, 0.05, 0.02],
  ['fullback', 'Balanced', 2, 8, 0.75, 0.30, 0.75, 0.45, 0.55, 0.30, 0, 0.10, 0.05],
  ['fullback', 'Versatile', 4, 10, 0.65, 0.35, 0.70, 0.45, 0.60, 0.30, 0.05, 0.12, 0.05],
  ['wingback', 'Balanced', 0, 18, 0.90, 0.55, 0.55, 0.50, 0.75, 0.50, 0.05, 0.35, 0.05],
  ['wingback', 'Support', 0, 14, 0.90, 0.45, 0.60, 0.50, 0.70, 0.45, 0.05, 0.25, 0.05],
  ['attacking_wingback', 'Support', 0, 22, 0.95, 0.75, 0.40, 0.55, 0.90, 0.65, 0.05, 0.45, 0.05],
  ['attacking_wingback', 'Attack', 0, 28, 1.00, 0.90, 0.30, 0.55, 0.95, 0.75, 0.05, 0.60, 0.05],
  ['inverted_wingback', 'Build-Up', 14, 8, 0.25, 0.25, 0.65, 0.45, 0.55, 0.05, 0.25, 0.05, 0.05],
  ['inverted_wingback', 'Attack', 14, 14, 0.25, 0.40, 0.55, 0.45, 0.65, 0.05, 0.35, 0.15, 0.10],
  ['falseback', 'Defend', 12, 0, 0.30, 0.05, 0.92, 0.40, 0.35, 0, 0, 0, 0],
  ['falseback', 'Balanced', 12, 3, 0.30, 0.10, 0.85, 0.40, 0.40, 0.05, 0, 0, 0],

  ['defender', 'Defend', 0, 0, 0.10, 0.05, 0.95, 0.35, 0.35, 0, 0, 0, 0],
  ['defender', 'Balanced', 0, 2, 0.10, 0.08, 0.90, 0.40, 0.40, 0, 0, 0, 0],
  ['stopper', 'Balanced', 0, 4, 0.10, 0.10, 0.85, 0.60, 0.50, 0, 0, 0, 0],
  ['stopper', 'Aggressive', 0, 6, 0.10, 0.12, 0.80, 0.75, 0.55, 0, 0, 0, 0],
  ['ball_playing_defender', 'Defend', 0, 1, 0.10, 0.05, 0.93, 0.35, 0.35, 0, 0, 0, 0],
  ['ball_playing_defender', 'Build-Up', 0, 4, 0.10, 0.08, 0.88, 0.40, 0.40, 0.02, 0, 0, 0.02],
  ['ball_playing_defender', 'Aggressive', 0, 5, 0.10, 0.10, 0.82, 0.65, 0.50, 0, 0, 0, 0.02],
  ['wide_back', 'Defend', -2, 1, 0.45, 0.08, 0.92, 0.40, 0.45, 0.05, 0, 0, 0],
  ['wide_back', 'Support', -6, 5, 0.55, 0.20, 0.80, 0.45, 0.55, 0.20, 0, 0.05, 0],
  ['wide_back', 'Aggressive', -5, 7, 0.50, 0.25, 0.75, 0.65, 0.60, 0.10, 0, 0.05, 0],

  ['holding', 'Defend', 0, 0, 0.10, 0.05, 0.95, 0.35, 0.40, 0, 0, 0, 0.02],
  ['holding', 'Roaming', 0, 4, 0.15, 0.15, 0.85, 0.45, 0.55, 0, 0, 0.05, 0.05],
  ['holding', 'Ball-Winning', 0, 2, 0.15, 0.10, 0.90, 0.70, 0.55, 0, 0, 0, 0.02],
  ['deep_lying_playmaker', 'Defend', 0, 1, 0.10, 0.10, 0.80, 0.30, 0.40, 0, 0, 0.02, 0.08],
  ['deep_lying_playmaker', 'Roaming', 0, 5, 0.15, 0.20, 0.70, 0.40, 0.55, 0.05, 0, 0.05, 0.12],
  ['deep_lying_playmaker', 'Build-Up', 0, -4, 0.10, 0.12, 0.75, 0.30, 0.45, 0.02, 0, 0.03, 0.10],
  ['centre_half', 'Defend', 0, 0, 0.05, 0.02, 0.98, 0.25, 0.35, 0, 0, 0, 0],
  ['wide_half', 'Defend', -6, 1, 0.45, 0.10, 0.85, 0.40, 0.50, 0.15, 0, 0.03, 0],
  ['wide_half', 'Build-Up', -8, 3, 0.50, 0.15, 0.75, 0.35, 0.50, 0.20, 0, 0.05, 0.03],
  ['box_crasher', 'Balanced', 0, 12, 0.20, 0.70, 0.30, 0.50, 0.75, 0.05, 0, 0.40, 0.35],

  ['box_to_box', 'Balanced', 0, 8, 0.20, 0.60, 0.55, 0.65, 0.95, 0.05, 0, 0.25, 0.20],
  ['box_to_box', 'Ball-Winning', 0, 6, 0.20, 0.50, 0.65, 0.80, 0.95, 0, 0, 0.15, 0.12],
  ['playmaker', 'Balanced', 0, 6, 0.20, 0.35, 0.30, 0.45, 0.60, 0.05, 0.05, 0.15, 0.15],
  ['playmaker', 'Attack', 0, 10, 0.20, 0.50, 0.20, 0.40, 0.65, 0.05, 0.10, 0.30, 0.20],
  ['playmaker', 'Roaming', 4, 8, 0.25, 0.40, 0.28, 0.45, 0.65, 0.05, 0.10, 0.20, 0.15],
  ['playmaker', 'Build-Up', 0, 4, 0.15, 0.25, 0.35, 0.40, 0.50, 0.02, 0.02, 0.08, 0.12],
  ['half_winger', 'Balanced', 0, 12, 0.55, 0.55, 0.40, 0.50, 0.75, 0.40, 0.20, 0.30, 0.12],
  ['half_winger', 'Attack', 0, 15, 0.60, 0.65, 0.30, 0.50, 0.80, 0.45, 0.25, 0.40, 0.15],
  ['half_winger', 'Support', 0, 10, 0.50, 0.45, 0.45, 0.50, 0.70, 0.40, 0.15, 0.20, 0.10],
  ['half_winger', 'Roaming', 4, 12, 0.50, 0.50, 0.38, 0.50, 0.75, 0.35, 0.20, 0.25, 0.12],

  ['classic_10', 'Attack', 0, 8, 0.15, 0.50, 0.12, 0.30, 0.55, 0.10, 0.15, 0.40, 0.28],
  ['classic_10', 'Wide', 0, 6, 0.45, 0.40, 0.18, 0.30, 0.55, 0.35, 0.15, 0.25, 0.15],
  ['classic_10', 'Versatile', 0, 6, 0.20, 0.45, 0.20, 0.30, 0.55, 0.12, 0.15, 0.35, 0.22],
  ['shadow_striker', 'Attack', 0, 16, 0.20, 0.90, 0.10, 0.35, 0.70, 0, 0.05, 0.85, 0.30],

  ['winger', 'Balanced', 0, 14, 0.90, 0.60, 0.30, 0.45, 0.75, 0.75, 0.10, 0.30, 0.10],
  ['winger', 'Attack', 0, 18, 0.92, 0.75, 0.20, 0.40, 0.80, 0.75, 0.12, 0.40, 0.12],
  ['winger', 'Versatile', 3, 14, 0.80, 0.60, 0.28, 0.45, 0.75, 0.60, 0.20, 0.30, 0.12],
  ['wide_playmaker', 'Attack', 12, 12, 0.55, 0.55, 0.20, 0.40, 0.70, 0.30, 0.45, 0.30, 0.12],
  ['wide_playmaker', 'Build-Up', 12, 8, 0.55, 0.40, 0.28, 0.40, 0.60, 0.30, 0.40, 0.15, 0.08],
  ['wide_midfielder', 'Support', 0, 8, 0.75, 0.35, 0.65, 0.55, 0.70, 0.45, 0.05, 0.12, 0.05],
  ['wide_midfielder', 'Defend', 0, 4, 0.70, 0.20, 0.80, 0.55, 0.65, 0.30, 0, 0.05, 0.03],
  ['wide_midfielder', 'Build-Up', 3, 6, 0.60, 0.30, 0.60, 0.50, 0.65, 0.30, 0.10, 0.10, 0.05],
  ['inside_forward', 'Balanced', 16, 10, 0.35, 0.70, 0.25, 0.35, 0.65, 0.15, 0.75, 0.35, 0.35],
  ['inside_forward', 'Attack', 20, 12, 0.35, 0.85, 0.15, 0.35, 0.70, 0.10, 0.90, 0.45, 0.40],
  ['inside_forward', 'Roaming', 22, 12, 0.30, 0.75, 0.20, 0.35, 0.70, 0.10, 0.85, 0.40, 0.38],

  ['advanced_forward', 'Attack', 0, 10, 0.30, 0.95, 0.05, 0.30, 0.60, 0, 0, 0.95, 0.15],
  ['advanced_forward', 'Versatile', 0, 8, 0.30, 0.85, 0.08, 0.30, 0.60, 0, 0, 0.80, 0.15],
  ['advanced_forward', 'Support', 0, 6, 0.30, 0.70, 0.15, 0.35, 0.60, 0, 0, 0.60, 0.12],
  ['poacher', 'Attack', 0, 4, 0.15, 0.80, 0.05, 0.25, 0.45, 0, 0, 0.55, 0.10],
  ['poacher', 'Support', 0, 3, 0.15, 0.65, 0.10, 0.28, 0.45, 0, 0, 0.40, 0.08],
  ['poacher', 'Versatile', 0, 3, 0.18, 0.70, 0.08, 0.28, 0.45, 0, 0, 0.45, 0.10],
  ['target_forward', 'Attack', 0, 2, 0.25, 0.60, 0.10, 0.40, 0.55, 0, 0, 0.20, 0.08],
  ['target_forward', 'Balanced', 0, 1, 0.25, 0.50, 0.12, 0.40, 0.55, 0, 0, 0.10, 0.06],
  ['target_forward', 'Wide', -12, 2, 0.60, 0.45, 0.12, 0.40, 0.60, 0, 0, 0.10, 0.04],
  ['false_9', 'Build-Up', 0, -8, 0.20, 0.35, 0.25, 0.35, 0.50, 0, 0.10, 0.15, 0.15],
  ['false_9', 'Attack', 0, -5, 0.20, 0.45, 0.20, 0.35, 0.55, 0, 0.10, 0.30, 0.18],
]

const FIELDS = ['dxIn', 'dyFwd', 'w', 'r', 'd', 'p', 's', 'cross', 'cutIn', 'runBehind', 'longShot']
// İşaretli hareket düzeltmesi (Buğra/Claude incelemesi) yapılan satırlar
const SIGNED_FIX = new Set(['false_9_build_up', 'false_9_attack', 'deep_lying_playmaker_build_up', 'target_forward_wide', 'wide_back_defend', 'wide_back_support', 'wide_back_aggressive', 'wide_half_defend', 'wide_half_build_up'])
const slug = (focus) => focus.toLowerCase().replace(/-/g, '_')
const byRole = new Map(roles.map((r) => [r.id, r]))
const out = ROWS.map((row) => {
  const [roleId, focus, ...values] = row
  const role = byRole.get(roleId)
  if (!role || !role.focuses.includes(focus)) {
    throw new Error(`roles.json'da yok: ${roleId} ${focus}`)
  }
  return {
    id: `${roleId}_${slug(focus)}`, role: roleId, focus, positions: role.positions,
    ...Object.fromEntries(FIELDS.map((f, i) => [f, values[i]])),
    verified: false,
    _basis: `EA rol açıklaması (${role.movementTags.join(', ')}) ve genel futbol okuması; oyun içi gözlem YOK${SIGNED_FIX.has(`${roleId}_${slug(focus)}`) ? '; işaretli hareket düzeltmesi, Buğra/Claude incelemesi' : ''}`,
  }
})
const expected = roles.reduce((n, r) => n + r.focuses.length, 0)
if (out.length !== expected) {
  throw new Error(`satır sayısı ${out.length} ≠ rol×focus ${expected}`)
}
const seed = {
  _basis: 'EA rol tanımı (roles.json movementTags) ve genel futbol okuması; oyun içi gözlem YOK, değerler taslaktır. Güvence değerlerde değil rollerin birbirine göre sırasındadır (RoleMovementInvariantsTest).',
  verified: false,
  version: 'fc27-draft-3',
  fields: {
    dxIn: 'topla yatay kayma (−15…30 birim; + ortaya, − kanada açılır; ayna otomatik)', dyFwd: 'topla dikey kayma (−12…35 birim; + ileri, − geriye düşer; y azalır = ileri)', w: 'genişlik eğilimi 0-1', r: 'ileri koşu oranı 0-1', d: 'savunma görevi 0-1', p: 'pres yoğunluğu 0-1', s: 'stamina maliyeti 0-1',
    cross: 'orta eğilimi 0-1', cutIn: 'içe kat edip şut/pas 0-1', runBehind: 'savunma arkasına (son hatta) koşu 0-1; r\'den ayrı', longShot: 'uzaktan şut eğilimi 0-1',
  },
  lineShiftByBand: { _basis: 'topsuz hat kayması (birim ileri), bant: DEEP ≤30 / BALANCED ≤60 / HIGH ≤90 / AGGRESSIVE', verified: false, DEEP: -6, BALANCED: 0, HIGH: 8, AGGRESSIVE: 14 },
  roles: out,
}
writeFileSync(`${root}src/main/resources/seed/role-movement.fc27.json`, JSON.stringify(seed, null, 2) + '\n')

const args = process.argv.slice(2)
if (args.includes('--table')) {
  const GROUPS = [['Kaleci', ['GK']], ['Bek', ['RB', 'LB']], ['Stoper', ['CB']], ['Defansif orta saha', ['CDM']], ['Orta saha', ['CM']], ['Ofansif orta saha', ['CAM']], ['Kanat', ['LM', 'RM', 'LW', 'RW']], ['Forvet', ['ST']]]
  const first = (r) => r.positions[0]
  const groupOf = (r) => GROUPS.find(([, ps]) => ps.includes(first(r)))?.[0] ?? 'Diğer'
  let md = '# E3-7 — `role-movement.fc27.json` tablosu (onay bekliyor; motor koduna BAĞLANMADI)\n\nAlanlar: dxIn · dyFwd (birim) | w · r · d · p · s | cross · cutIn · runBehind · longShot (0-1). Hepsi `verified:false`; `_basis`: EA rol açıklaması + futbol okuması, **oyun içi gözlem yok**. ' + `${out.length} satır (roles.json'daki rol × focus kombinasyonlarının tamamı).\n\n`
  for (const [title] of GROUPS) {
    const rows = out.filter((r) => groupOf(r) === title)
    md += `## ${title} (${rows.length})\n\n| Rol · focus | Mevki | dxIn | dyFwd | w | r | d | p | s | cross | cutIn | runBehind | longShot |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|\n`
    for (const r of rows) {
      md += `| ${byRole.get(r.role).name} · ${r.focus} | ${r.positions.join('/')} | ${FIELDS.map((f) => r[f]).join(' | ')} |\n`
    }
    md += '\n'
  }
  writeFileSync(args[args.indexOf('--table') + 1], md)
}
console.log(`role-movement.fc27.json: ${out.length} satır`)
