# 04 · OYUN KURALLARI — FC27 doğrulaması + `ut-rules.fc27.json` v2
Kaynak: Buğra'nın yüklediği rehberler (fifauteam.com FC27 sayfaları, FUT.GG taktik sayfası, FUTBIN rol/focus tablosu = `roles_focus_authoritative.md`) + repodaki 64 gerçek taktik kodu. Kaynak belirtilmeyen değer `VERIFY`.
Kural (CLAUDE.md): bunların hiçbiri koda gömülmez. Hepsi tek versiyonlu kural dokümanında durur (BE-ARCH-03), backend ve web aynı dokümanı ve aynı test vektörlerini kullanır.

---
## 1. KURAL GÖREVLERİ
| ID | Pri | Konu | Şu an | Yapılacak |
|---|---|---|---|---|
| RULE-01 | P1 | Kimya | Yalnız 3 eşik dizisi config'te, backend motoru yok, web adla anahtarlıyor | §2 `chemistry` bloğunun tamamı + backend `ChemistryCalculator` + id ile anahtarlama + ortak vektörler (§5) |
| RULE-02 | P1 | Squad rating | Backend düz ortalama; config 11, web 18 | `squadRating.sizes = {squad:18, sbc:11}`, formül §2, vektör §5 |
| RULE-03 | P1 | Kalite | Backend `is_rare = ovr>=65` | `quality` türet: BRONZE ≤64, SILVER 65-74, GOLD ≥75. Base kartlarda rare/common yok |
| RULE-04 | P2 | SBC skoru | Regular/TOTW/Icon/Hero tabloları doğru, fallback monoton değil | §2 `sbcScore` + `max()` fallback + monotonluk testi |
| RULE-05 | P1 | İçerik takvimi | Yalnız web'de günlük 18:00 | §2 `contentCalendar` tamamı + backend `ContentCalendarService` |
| RULE-06 | P2 | Taktik | Approach modeli yok, slider 0-100 | §2 `tactics` + Tactical Focus kaydırma tablosu |
| RULE-07 | P1 | Rol/focus | Backend seed doğru (FUTBIN ile birebir), web fallback'i birleşik liste gösteriyor, id'ler pozisyonsuz | §4 tablosu tek kaynak; `(position, role, focus)` anahtarı |
| RULE-08 | P1 | Formasyon | 29 doğru ama 5-3-2 şekli ve EA etiketleri yanlış | §3 tablosu |
| RULE-09 | P2 | Rarity → kart tipi | İngilizce ad eşleşmesi | `rarities` bloğu, id ile; değerleri katkıcı capture'larından doldur (`VERIFY`) |
| RULE-10 | P2 | Face stat ağırlıkları | Yok | §2 `faceStatWeights`: kariyer oyuncularında 34 attribute'tan PAC/SHO/... hesapla (LE export'unda yalnız attribute var) |

---
## 2. ÖNERİLEN `seed/ut-rules.fc27.json` (v2, tam)
Yorumlar yerine her bölümde `_source` / `_verify` alanı var. Gerçek dosyaya bu hâliyle koy.
```json
{
  "id": "fc27-v2",
  "gameVersion": 27,
  "chemistry": {
    "_source": "fifauteam.com/fc-27-chemistry (yüklenen: FC_27_Chemistry_Explained.md)",
    "verified": true,
    "thresholds": { "club": [2, 4, 7], "league": [3, 5, 8], "nation": [2, 5, 8] },
    "maxPerPlayer": 3,
    "maxSquad": 33,
    "outOfPositionChem": 0,
    "outOfPositionCountsForThresholds": false,
    "substitutesCount": false,
    "icon": { "alwaysMaxInPosition": true, "nationIncrement": 1, "everyLeagueInXiIncrement": 1 },
    "hero": { "alwaysMaxInPosition": true, "nationIncrement": 1, "leagueIncrement": 1 },
    "hallOfFut": { "sameAs": "hero" },
    "manager": { "bonus": 1, "match": "NATION_OR_LEAGUE", "maxPerPlayer": 1, "leagueConsumableOverrides": true },
    "gender": { "linkByNation": true, "linkByAffiliatedClub": true, "linkByLeague": false,
                "_verify": "kadın-erkek kulüp eşlemesi (affiliatedClubs) EA team id ile tutulmalı; kaynak tablo yok, katkı/admin ile doldur" },
    "affiliatedClubs": [],
    "specialItemsBoostedChem": { "_verify": "FC27'de kampanya kartlarına seçici uygulanıyor; kart bazında capture'dan gelir" }
  },
  "squadRating": {
    "_source": "fifauteam.com/fc-27-squad-rating-guide",
    "verified": false,
    "sizes": { "squad": 18, "sbc": 11 },
    "formula": "FLOOR( ROUND(sum + Σ max(0, r - avg)) / n ), avg yuvarlanmaz",
    "starRating": [
      { "min": 83, "stars": 5.0 }, { "min": 79, "stars": 4.5 }, { "min": 75, "stars": 4.0 },
      { "min": 71, "stars": 3.5 }, { "min": 69, "stars": 3.0 }, { "min": 67, "stars": 2.5 },
      { "min": 65, "stars": 2.0 }, { "min": 63, "stars": 1.5 }, { "min": 60, "stars": 1.0 },
      { "min": 2, "stars": 0.5 }, { "min": 0, "stars": 0.0 }
    ]
  },
  "quality": {
    "_source": "fifauteam.com/fc-27-player-items",
    "bronze": [0, 64], "silver": [65, 74], "gold": [75, 99],
    "baseRareCommonSplit": false
  },
  "sbcScore": {
    "_source": "fifauteam.com/fc-27-sbc-score (EA Pitch Notes FUT Deep Dive'a atıf). EA yakalanan sbsScore'u döndürüyorsa o kazanır",
    "regular": { "99": 100000, "98": 90000, "97": 85000, "96": 55000, "95": 40000, "94": 30000, "93": 25000,
                 "92": 20000, "91": 19000, "90": 14000, "89": 11000, "88": 8300, "87": 5500, "86": 4100,
                 "85": 2100, "84": 830, "83": 410, "82": 340, "81": 280, "80": 180, "79": 160, "78": 140,
                 "77": 120, "76": 100, "75": 90, "65-74": 35, "45-64": 20 },
    "totw":   { "91": 23750, "90": 16500, "89": 13750, "88": 10375, "87": 7688, "86": 5125, "85": 2625,
                "84": 1038, "83": 625, "82": 425, "81": 338, "80": 225 },
    "hero":   { "87": 6875, "86": 5125 },
    "icon":   { "95": 60000, "94": 45000, "93": 37500, "92": 30000, "91": 28500, "90": 21000, "89": 16500, "88": 12450 },
    "fallback": "max(table[r] if var, regular[r], table[r-1 en yakın alt])  // monoton artış garantisi"
  },
  "contentCalendar": {
    "_source": "fifauteam.com weekly objectives + squad battles sayfaları",
    "tz": "Europe/London",
    "dailyRefresh": { "time": "18:00", "verified": false },
    "weeklyObjectives": { "day": "THURSDAY", "time": "18:00", "verified": true },
    "milestones": { "note": "Perşembe sabahı ya da 18:00, düzensiz" },
    "squadBattles": { "week": "SUNDAY_TO_SUNDAY", "rewardsAt": { "day": "SUNDAY", "time": "08:05" }, "maxMatches": 18 },
    "championsFinals": { "window": "FRIDAY..MONDAY", "matches": 15, "_verify": "FC27 tam saatler" },
    "seasons": [
      { "n": 1, "start": "2026-09-16T08:00", "end": "2026-10-22T08:00" },
      { "n": 2, "start": "2026-10-22T08:00", "end": "2026-12-03T08:00" },
      { "n": 3, "start": "2026-12-03T08:00", "end": "2027-01-14T08:00" },
      { "n": 4, "start": "2027-01-14T08:00", "end": "2027-02-25T08:00" },
      { "n": 5, "start": "2027-02-25T08:00", "end": "2027-04-08T08:00" },
      { "n": 6, "start": "2027-04-08T08:00", "end": "2027-05-06T08:00" },
      { "n": 7, "start": "2027-05-06T08:00", "end": "2027-06-17T08:00" },
      { "n": 8, "start": "2027-06-17T08:00", "end": "2027-07-15T08:00" },
      { "n": 9, "start": "2027-07-15T08:00", "end": "2027-09-23T08:00" }
    ],
    "displayTz": "Europe/Istanbul"
  },
  "tactics": {
    "_source": "fifauteam.com/fc-27-tactics + smart tactics",
    "formationsCount": 29,
    "customTacticSlots": 5,
    "tacticCode": { "length": 12, "_note": "rehber 11 diyor; repodaki 64 gerçek FC27 kodunun hepsi 12 karakter" },
    "buildUp": ["SHORT_PASSING", "BALANCED", "COUNTER"],
    "defensiveApproach": [
      { "id": "DEEP",       "lineHeight": [1, 30],   "default": 25, "pressure": "NONE",    "runTracking": "DROPS_WITH_RUNS" },
      { "id": "BALANCED",   "lineHeight": [31, 60],  "default": 50, "pressure": "NONE",    "runTracking": "FLEXIBLE" },
      { "id": "HIGH",       "lineHeight": [61, 90],  "default": 70, "pressure": "MINIMAL", "runTracking": "RARELY" },
      { "id": "AGGRESSIVE", "lineHeight": [91, 100], "default": 95, "pressure": "AFTER_POSSESSION_LOSS", "runTracking": "STEPS_UP", "extraStaminaDrain": true }
    ],
    "tacticalFocus": {
      "attacking": { "buildUpStep": 1, "defensiveApproachStep": 1,
                     "focusShift": [["DEFEND","BALANCED"],["BALANCED","ATTACK"],["DEFEND","BUILD_UP"],["DEFEND","ROAMING"],["BALANCED","ROAMING"]],
                     "special": [{"pos":"CM","role":"PLAYMAKER","from":"ROAMING","to":"ATTACK"},{"pos":"RW|LW","role":"INSIDE_FORWARD","from":"ATTACK","to":"ROAMING"}] },
      "defending": { "buildUpStep": -1, "defensiveApproachStep": -1,
                     "focusShift": [["BALANCED","DEFEND"],["ATTACK","BALANCED"],["BUILD_UP","DEFEND"],["ROAMING","DEFEND"],["ROAMING","BALANCED"],["WIDE","BALANCED"]],
                     "special": [{"pos":"CM","role":"PLAYMAKER","from":"ATTACK","to":"ROAMING"}] },
      "rule": "Hedef focus o (pozisyon, rol) için yoksa focus değişmez",
      "_verify": "rehberdeki 'Complete' focus'u hiçbir rolde yok, yok sayıldı"
    },
    "quickTactics": { "teamPress": { "durationSec": 20, "rechargeSec": 100 } }
  },
  "positions": {
    "list": ["GK","RB","CB","LB","CDM","CM","CAM","RM","LM","RW","LW","ST"],
    "maxSecondary": null,
    "workRatesInUt": false,
    "positionModifiers": false,
    "roleFamiliarity": ["BASE","PLUS","PLUS_PLUS"],
    "specialItemsRolePlusPlusAllRoles": true,
    "outOfPosition": "simplified role, 0 chem"
  },
  "faceStatWeights": {
    "_source": "fifauteam.com/fc-27-attributes",
    "PAC": { "acceleration": 45, "sprintSpeed": 55 },
    "SHO": { "finishing": 45, "longShots": 20, "shotPower": 20, "positioning": 5, "volleys": 5, "penalties": 5 },
    "PAS": { "shortPassing": 35, "vision": 20, "crossing": 20, "longPassing": 15, "curve": 5, "freeKickAccuracy": 5 },
    "DRI": { "dribbling": 50, "ballControl": 35, "agility": 10, "balance": 5 },
    "DEF": { "defensiveAwareness": 30, "standingTackle": 30, "interceptions": 20, "headingAccuracy": 10, "slidingTackle": 10 },
    "PHY": { "strength": 50, "stamina": 25, "aggression": 20, "jumping": 5 },
    "_verify": "EA yuvarlama kuralı; 10 bilinen kartla test et (Mbappé 96/91/80/92/29/76 ratings sitesinde)"
  },
  "holographic": { "regularMaxOvr": 87, "pristineMinOvr": 88 },
  "transferTax": 0.05,
  "rarities": { "_verify": "rarity id → {cardType, designCode, chemKind} katkıcı capture'ından (rareflag) doldurulacak. Bilinen: PLAYER_RARITY_GROUP 23 = TOTW (Auto-SBC FC26)" },
  "cardDesign": { "mode": "EA_RENDER", "fallback": "OWN", "renderTemplate": "https://ratings-images-prod.pulse.ea.com/FC27/components/items/{assetId}_{locale}.webp", "locale": "tr-TR" }
}
```

---
## 3. FORMASYONLAR (29) — EA etiketi ↔ FUT.GG adı ↔ kullanım
FUT.GG verisi yüklenen "Best EA FC 27 Tactics" sayfasından (topluluk % / pro %). `eaFormationId` codec id'si `ea-tactic-codec.json`'dan alınmalı; aşağıdaki id'ler isim kalıbıdır (`VERIFY`: codec'teki gerçek id ile eşle).
| # | EA etiketi | FUT.GG adı | Topluluk / Pro | Not |
|---|---|---|---|---|
| 1 | 4-2-1-3 | 4-2-1-3 | 29% / 21% | Meta #1 |
| 2 | 4-4-1-1 (2) | 4-4-1-1 | 22% / 18% | Bizde "4-4-1-1" → yeniden adlandır |
| 3 | 4-3-1-2 | 4-3-1-2 | 10% / 15% | |
| 4 | 4-1-2-1-2 (2) | 4-1-2-1-2 Narrow | 7% / 12% | Bizde "4-1-2-1-2" → (2) |
| 5 | 4-2-3-1 (2) | 4-2-3-1 Wide | 7% / 8% | |
| 6 | 4-3-3 (4) | 4-3-3 Attack | 7% / 2% | |
| 7 | 4-4-2 | 4-4-2 | 6% / 8% | |
| 8 | 4-3-3 (2) | 4-3-3 Holding | 3% / 3% | Bizde "4-3-3" → (2) |
| 9 | 4-1-2-1-2 | 4-1-2-1-2 Wide | 1% / 2% | |
| 10 | 4-2-3-1 | 4-2-3-1 | 1% / 2% | |
| 11 | 4-4-2 (2) | 4-4-2 Holding | <1% / 3% | |
| 12 | 4-1-4-1 | 4-1-4-1 | <1% | |
| 13 | 4-3-3 (3) | 4-3-3 Defend | <1% | |
| 14 | 4-3-2-1 | 4-3-2-1 | <1% / 2% | |
| 15 | 4-2-2-2 | 4-2-2-2 | <1% | |
| 16 | 4-3-3 | 4-3-3 | <1% | Bizde "4-3-3 (3 CM)" → "4-3-3" |
| 17 | 4-5-1 | 4-5-1 Flat | <1% | |
| 18 | 4-2-4 | 4-2-4 | <1% | |
| 19 | 4-1-3-2 | 4-1-3-2 | <1% | |
| 20 | 4-5-1 (2) | 4-5-1 Attack | <1% | Bizde "4-5-1" → (2) |
| 21 | 3-5-2 | 3-5-2 | <1% | |
| 22 | 3-4-2-1 | 3-4-2-1 | <1% | |
| 23 | 5-2-1-2 | 5-2-1-2 | <1% / 2% | |
| 24 | 3-4-1-2 | 3-4-1-2 | <1% | |
| 25 | 5-3-2 | 5-3-2 | <1% | **Şekil: CDM + 2×CM** (bizde 3×CM, BE-BUG-10) |
| 26 | 5-4-1 | 5-4-1 | <1% | |
| 27 | 5-2-3 | 5-2-3 | <1% | |
| 28 | 3-4-3 | 3-4-3 | 0% | |
| 29 | 3-1-4-2 | 3-1-4-2 | 0% | |
`formations.json`'a ekle: `eaLabel`, `futggName`, `displayNameTr`, `usage:{community, pro, asOf:"2026-10"}`. UT meta sayfası bu yüzdeleri gösterebilir (kaynak: FUT.GG, tarihli; otomatik çekme yok, admin günceller).

En çok kullanılan rol (FUT.GG, mevki başına): GK Goalkeeper-Defend 66%, RB/LB Fullback-Balanced 45/44%, CB Defender-Defend 92%, CDM Deep-Lying Playmaker-Build-Up 47%, CM Box-to-Box-Balanced 43%, CAM Playmaker-Balanced 45%, RM/LM Inside Forward-Balanced 69/72%, RW/LW Inside Forward-Balanced 64/65%, ST Advanced Forward-Attack 70%. → Taktik sihirbazında "meta varsayılan" önerisi olarak kullan.

---
## 4. ROL / FOCUS — OTORİTER TABLO ve KAYNAK ÇELİŞKİLERİ
**Kazanan kaynak:** FUTBIN (`roles_focus_authoritative.md`). Sebep: backend `ea-tactic-codec.json` bununla 49 rol id'sinin hepsinde birebir uyuşuyor ve codec 64 gerçek FC27 koduyla doğrulandı. Liste sırası = EA sırası = varsayılan focus ilk eleman.

| Pozisyon | Rol → focus seti |
|---|---|
| GK | Goalkeeper [Defend, Balanced] · Sweeper Keeper [Balanced, Build-Up] · Ball-Playing Keeper [Build-Up] |
| RB/LB | Fullback [Balanced, Defend, Versatile] · Wingback [Balanced, Support] · Falseback [Balanced, Defend] · Attacking Wingback [Support, Attack] · Inverted Wingback [Build-Up, Attack] |
| CB | Defender [Defend, Balanced] · Stopper [Balanced, Aggressive] · Ball-Playing Defender [Defend, Build-Up, Aggressive] · Wide Back [Defend, Support, Aggressive] |
| CDM | Holding [Defend, Roaming, Ball-Winning] · Deep-Lying Playmaker [Defend, Roaming, Build-Up] · Centre Half [Defend] · Wide Half [Defend, Build-Up] · Box Crasher [Balanced] |
| CM | Box-to-Box [Balanced, Ball-Winning] · Holding [Defend, Ball-Winning] · Deep-Lying Playmaker [Defend, Build-Up] · Playmaker [Attack, Roaming] · Half Winger [Balanced, Attack, Support] |
| CAM | Playmaker [Balanced, Roaming, Build-Up] · Half Winger [Balanced, Attack, Roaming] · Shadow Striker [Attack] · Classic 10 [Attack, Wide, Versatile] |
| RM/LM | Winger [Balanced, Attack] · Wide Playmaker [Attack, Build-Up] · Wide Midfielder [Support, Defend, Build-Up] · Inside Forward [Balanced, Attack] |
| RW/LW | Winger [Balanced, Attack, Versatile] · Wide Playmaker [Attack, Build-Up] · Inside Forward [Balanced, Attack, Roaming] |
| ST | Advanced Forward [Attack, Versatile, Support] · Target Forward [Attack, Balanced, Wide] · False 9 [Build-Up, Attack] · Poacher [Attack, Support, Versatile] |

**fifauteam sayfasıyla çelişkiler** (fifauteam yanlış/eksik, kullanma): Stopper "Balanced | Defend" (doğrusu Balanced, Aggressive) · Wingback "Balanced | Versatile" (doğrusu Balanced, Support) · CDM Holding Ball-Winning eksik · CDM DLP Build-Up eksik · CM Holding Ball-Winning eksik · CM DLP Build-Up eksik · CAM Playmaker Build-Up eksik · Wide Playmaker Build-Up eksik · Advanced Forward Support eksik · Poacher Support eksik · BPD Aggressive eksik. "Smart Tactics" sayfasındaki "Complete" focus'u hiçbir rolde yok.

Rol ailesi bilgisi (Role+ / Role++): özel UT kartları kendi pozisyonlarındaki **tüm** rollerde Role++ alır. Base kartlarda rol başına Base/+/++ katkıcı capture'ından ya da EA ratings `playerAbilities`'ten gelir (`VERIFY` alan adı). LE tarafında `role1..role5` kodları: 1-52 arası rol, +100 = "++" (örn. 41 = ST Advanced Forward+, 141 = ++) (07 §2).

Rol açıklama metinleri (Defend focus = "Stays Back · Defending · Position Discipline · ..." gibi 4'lü etiketler) iki rehberde tam var → `roles.json`'a `traitsEn` alanı olarak koy, Türkçe çevirisi i18n'de. HelpPopover'da göster.

---
## 5. ORTAK TEST VEKTÖRLERİ (backend + web aynı dosyayı okur: `seed/ut-rules.fc27.vectors.json`)
```json
{
  "squadRating": [
    { "name": "fifauteam örneği", "size": 18, "ratings": [80,80,80,80,80,80,80,80,80,80,80,80,80,85,85,85,85,85], "expected": 82 },
    { "name": "SBC 11", "size": 11, "ratings": [84,84,84,84,84,84,84,84,83,83,83], "expected": 83, "_verify": "oyun içi" }
  ],
  "chemistry": [
    { "name": "kulüp 2 → +1", "xi": "2 aynı kulüp, diğerleri bağsız, hepsi pozisyonda", "expectEach": 1 },
    { "name": "lig 3 → +1, 5 → +2, 8 → +3", "_todo": "somut kart listesiyle yaz" },
    { "name": "mevki dışı oyuncu sayılmaz", "_todo": "" },
    { "name": "Icon her lige +1", "_todo": "" },
    { "name": "menajer ülke+lig eşleşse de +1", "_todo": "" },
    { "name": "yedek sayılmaz", "_todo": "" },
    { "name": "kadın-erkek lig üzerinden bağlanmaz", "_todo": "" },
    { "name": "aynı ad farklı kulüp (Nacional POR/URU) bağlanmaz", "_todo": "" }
  ],
  "sbcScore": [ { "rarity": "TOTW", "ovr": 92, "expectAtLeast": 23750 }, { "rarity": "ICON", "ovr": 96, "expectAtLeast": 60000 } ],
  "defensiveApproach": [ { "lineHeight": 30, "expect": "DEEP" }, { "lineHeight": 31, "expect": "BALANCED" }, { "lineHeight": 91, "expect": "AGGRESSIVE" } ]
}
```
`_todo` satırlarını ajan somut kart listeleriyle doldursun; her vektör iki tarafta çalışmalı.
Hesap kontrolü (fifauteam örneği): toplam 1465, ort 81,389, CF = 5×3,611 = 18,06, 1483,06 → 1483, /18 = 82,39 → **82**.
