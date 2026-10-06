// tools/tactic-code/ea-model-data.json -> src/main/resources/seed/ea-tactic-codec.json
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const data = JSON.parse(fs.readFileSync(path.join(here, 'ea-model-data.json'), 'utf8'))

// EA rolleri (id, ad, mevki, odaklar) — easysbc /base-data gözlemi.
const roleText = `1 Goalkeeper GK Balanced/Defend
2 Sweeper Keeper GK Balanced/Build-Up
3 Fullback RB Balanced/Defend/Versatile
4 Falseback RB Balanced/Defend
5 Wingback RB Balanced/Support
6 Attacking Wingback RB Support/Attack
7 Fullback LB Balanced/Defend/Versatile
8 Falseback LB Balanced/Defend
9 Wingback LB Balanced/Support
10 Attacking Wingback LB Support/Attack
11 Defender CB Defend/Balanced
12 Stopper CB Balanced/Aggressive
13 Ball-Playing Defender CB Defend/Build-Up/Aggressive
14 Holding CDM Defend/Ball-Winning/Roaming
15 Centre-Half CDM Defend
16 Deep-Lying Playmaker CDM Defend/Roaming/Build-Up
17 Wide Half CDM Defend/Build-Up
18 Box-to-Box CM Balanced/Ball-Winning
19 Holding CM Defend/Ball-Winning
20 Deep-Lying Playmaker CM Defend/Build-Up
21 Playmaker CM Roaming/Attack
22 Half-Winger CM Balanced/Attack/Support
23 Winger RM Balanced/Attack
24 Wide Midfielder RM Support/Defend/Build-Up
25 Wide Playmaker RM Attack/Build-Up
26 Inside Forward RM Balanced/Attack
27 Winger LM Balanced/Attack
28 Wide Midfielder LM Support/Defend/Build-Up
29 Wide Playmaker LM Attack/Build-Up
30 Inside Forward LM Balanced/Attack
31 Playmaker CAM Roaming/Balanced/Build-Up
32 Shadow Striker CAM Attack
33 Half-Winger CAM Balanced/Attack/Roaming
34 Classic 10 CAM Attack/Wide/Versatile
35 Winger RW Balanced/Attack/Versatile
36 Inside Forward RW Balanced/Attack/Roaming
37 Wide Playmaker RW Attack/Build-Up
38 Winger LW Balanced/Attack/Versatile
39 Inside Forward LW Balanced/Attack/Roaming
40 Wide Playmaker LW Attack/Build-Up
41 Advanced Forward ST Attack/Support/Versatile
42 Poacher ST Attack/Support/Versatile
43 False 9 ST Build-Up/Attack
44 Target Forward ST Attack/Balanced/Wide
45 Ball-Playing Keeper GK Build-Up
46 Inverted Wingback RB Build-Up/Attack
47 Inverted Wingback LB Build-Up/Attack
48 Wide Back CB Defend/Aggressive/Support
49 Box Crasher CDM Balanced`

const roles = roleText.split('\n').map((line) => {
  const parts = line.split(' ')
  const id = Number(parts[0])
  const focuses = parts[parts.length - 1].split('/')
  const position = parts[parts.length - 2]
  const name = parts.slice(1, parts.length - 2).join(' ')
  return { id, name, position, focuses }
})

const formations = Object.entries(data.formations).map(([id, [label, positions, roleIds, base]]) => ({
  id,
  label,
  positions: positions.split('.'),
  roleIds: roleIds.split('.').map(Number),
  base: base.split(',').map(Number),
}))

const out = {
  alphabet: data.alphabet,
  substitutions: data.substitutions,
  positionTables: data.positionTables,
  roles,
  formations,
}
const target = path.join(here, '..', '..', 'src', 'main', 'resources', 'seed', 'ea-tactic-codec.json')
fs.writeFileSync(target, JSON.stringify(out, null, 1) + '\n')
console.log('roles', roles.length, 'formations', formations.length, '->', target)
