# Replica entry schema v2 (FC27) — one JSON object per entry, all free text in Turkish
{
  "id": "coach_club_YYYY",                 // e.g. "guardiola_barcelona_2011" (season end year)
  "kind": "REPLICA",
  "category": "UCL_WINNER | LEGENDARY_SQUAD | LEGENDARY_COACH | UNDERRATED_COACH | TURKISH_BIG4 | TURKISH_CHAMPION",
  "club": "FC Barcelona", "coach": "Pep Guardiola", "season": "2010-11",
  "honours": ["Şampiyonlar Ligi", "La Liga"],
  "whyIconic": "1-2 cümle",
  "realShape": { "inPossession": "örn. 3-4-3 / 2-3-5", "outOfPossession": "örn. 4-1-4-1 orta blok", "keyMechanisms": ["false 9", "3. adam", "..."] },
  "fc27": {
    "formation": "<EA etiketi, aşağıdaki 29'dan biri>",
    "buildUp": "SHORT_PASSING | BALANCED | COUNTER",
    "defensiveApproach": "DEEP | BALANCED | HIGH | AGGRESSIVE",
    "lineHeight": 1-100 (approach aralığında: DEEP 1-30, BALANCED 31-60, HIGH 61-90, AGGRESSIVE 91-100),
    "slots": [ // tam 11 slot, formasyon sırasıyla, GK ilk
      { "slot": "GK", "position": "GK", "role": "Sweeper Keeper", "focus": "Build-Up",
        "profile": "Valdés tipi: ayakla oyun kuran kaleci", "keyAttributes": ["GK Kicking","Reactions"], "keyPlayStyles": ["Footwork","Rush Out"] }
    ],
    "planB": { "label": "Önde koruma / geride arama", "formation": "...", "buildUp": "...", "defensiveApproach": "...", "lineHeight": 0, "changes": ["RB: Fullback/Defend", "..."] },
    "smartTactics": ["Tactical Focus: Hücum → hangi focus nereye kayar ve ne zaman kullanılır", "Quick Tactic önerisi"],
    "tacticCode": null
  },
  "replicaFidelity": { "score": 1-5, "cannotReplicate": ["FC27'de genişlik ayarı yok; genişlik rollerden gelir", "..."] },
  "behaviorTags": ["gk_build_up","fb_invert", "..."],   // serbest ama snake_case
  "sources": ["kaynak adı/URL ya da 'model bilgisi — VERIFY'"],
  "verify": ["emin olunmayan maddeler"]
}

## FC27 rules you MUST respect
- Roles/focus are keyed by (position, role). ONLY these are valid (order = EA order):
GK: Goalkeeper[Defend,Balanced] · Sweeper Keeper[Balanced,Build-Up] · Ball-Playing Keeper[Build-Up]
RB/LB: Fullback[Balanced,Defend,Versatile] · Wingback[Balanced,Support] · Falseback[Balanced,Defend] · Attacking Wingback[Support,Attack] · Inverted Wingback[Build-Up,Attack]
CB: Defender[Defend,Balanced] · Stopper[Balanced,Aggressive] · Ball-Playing Defender[Defend,Build-Up,Aggressive] · Wide Back[Defend,Support,Aggressive]
CDM: Holding[Defend,Roaming,Ball-Winning] · Deep-Lying Playmaker[Defend,Roaming,Build-Up] · Centre Half[Defend] · Wide Half[Defend,Build-Up] · Box Crasher[Balanced]
CM: Box-to-Box[Balanced,Ball-Winning] · Holding[Defend,Ball-Winning] · Deep-Lying Playmaker[Defend,Build-Up] · Playmaker[Attack,Roaming] · Half Winger[Balanced,Attack,Support]
CAM: Playmaker[Balanced,Roaming,Build-Up] · Half Winger[Balanced,Attack,Roaming] · Shadow Striker[Attack] · Classic 10[Attack,Wide,Versatile]
RM/LM: Winger[Balanced,Attack] · Wide Playmaker[Attack,Build-Up] · Wide Midfielder[Support,Defend,Build-Up] · Inside Forward[Balanced,Attack]
RW/LW: Winger[Balanced,Attack,Versatile] · Wide Playmaker[Attack,Build-Up] · Inside Forward[Balanced,Attack,Roaming]
ST: Advanced Forward[Attack,Versatile,Support] · Target Forward[Attack,Balanced,Wide] · False 9[Build-Up,Attack] · Poacher[Attack,Support,Versatile]
- FC27 has NO width / players-in-box / chance creation sliders. Only: formation, buildUp, defensiveApproach, lineHeight, 11 roles+focus. Width, overlaps, inverted fullbacks, back-3-in-possession etc. must be produced via roles (e.g. Falseback / Inverted Wingback = inverting FB; Wide Back = back-3 shifting CB; Centre Half = CDM dropping between CBs; Attacking Wingback = overlap; Half Winger = half-space; Box Crasher = late box runs).
- Tactical Focus (in-match): Attacking shifts buildUp and defensiveApproach +1 step and focuses Defend→Balanced, Balanced→Attack, Defend→Build-Up, Defend→Roaming, Balanced→Roaming (CM Playmaker Roaming→Attack; RW/LW Inside Forward Attack→Roaming). Defending does the reverse (also Wide→Balanced). Only if target focus exists for that role.
- Quick tactics: Team Press (20s, 100s recharge), Offside Trap, Overload Set Pieces, Get In Box. 5 custom tactic slots exist → planB.
- Popular meta (FUT.GG FC27): CB Defender-Defend, CDM DLP Build-Up, CM Box-to-Box Balanced, CAM Playmaker Balanced, wide Inside Forward Balanced, ST Advanced Forward Attack. Not relevant for replicas but don't confuse.
- PlayStyles available: Acrobatic, Aerial Fortress, Anticipate, Block, Bruiser, Chip Shot, Cross Claimer, Dead Ball, Deflector, Enforcer, Far Reach, Far Throw, Finesse Shot, First Touch, Footwork, Game Changer, Incisive Pass, Intercept, Inventive, Jockey, Long Ball Pass, Long Throw, Low Driven Shot, Pinged Pass, Power Shot, Precision Header, Press Proven, Quick Step, Rapid, Relentless, Rush Out, Slide Tackle, Technical, Tiki Taka, Trickster, Whipped Pass.
- Attributes: Acceleration, Sprint Speed, Positioning, Finishing, Shot Power, Long Shots, Volleys, Penalties, Vision, Crossing, Free Kick Accuracy, Short Passing, Long Passing, Curve, Agility, Balance, Reactions, Ball Control, Dribbling, Composure, Interceptions, Heading Accuracy, Defensive Awareness, Standing Tackle, Sliding Tackle, Jumping, Stamina, Strength, Aggression, GK Diving/Handling/Kicking/Positioning/Reflexes.

## 29 FC27 formations (EA labels) and slot positions
4-2-1-3 · 4-4-1-1 (2) · 4-3-1-2 · 4-1-2-1-2 (2) [narrow diamond] · 4-2-3-1 (2) [wide: CDM CDM LM CAM RM ST] · 4-3-3 (4) [attack: CM CM CAM] · 4-4-2 · 4-3-3 (2) [holding: CDM CM CM] · 4-1-2-1-2 [wide diamond: CDM LM CAM RM] · 4-2-3-1 [CDM CDM CAM CAM CAM ST] · 4-4-2 (2) [CDM CDM LM RM] · 4-1-4-1 · 4-3-3 (3) [defend: CDM CDM CM] · 4-3-2-1 · 4-2-2-2 · 4-3-3 [CM CM CM, LW RW ST] · 4-5-1 [flat: LM CM CM CM RM? or CAM CAM — use positions as given in formations_current.txt when unsure] · 4-2-4 · 4-1-3-2 · 4-5-1 (2) · 3-5-2 · 3-4-2-1 · 5-2-1-2 · 3-4-1-2 · 5-3-2 [CDM + 2 CM] · 5-4-1 · 5-2-3 · 3-4-3 · 3-1-4-2
Exact slot positions per formation (current repo seed, mostly right; but 5-3-2 should be LWB LCB CB RCB RWB? in FC: 5-at-back uses LB CB CB CB RB with Wingback roles; midfield CDM CM CM): see formations_current.txt in the same folder. Repo labels differ from EA labels — use EA labels in "formation".
