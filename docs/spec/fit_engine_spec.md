# FIT ENGINE SPEC — RoleFit · IntentFit · SquadFit (v1)
Ürünün beyni. Bir oyuncunun bir role/davranışa uyumunu skorlar, kadroyu taktiğe optimal yerleştirir,
zayıf halkaları ve "bu kadro bu taktiği oynar mı" sorusunu açıklanabilir biçimde cevaplar.
Onaylanan kararlar gömülü: (A) hibrit ağırlık, (B) RoleFit% + projectedRating, (C) weapon gate = ceza (elenmez, rozet),
(D) PS+ weapon eşiğini −4 telafi eder, (1) weapon cezası −1.5/puan + rozet, (2) IntentFit ayrı skor.

---

## 1. VERİ YAPILARI

```ts
// --- Normalizasyon: tüm attribute 0-99 → 0-1
norm(v) = clamp(v,0,99)/99

Attr = "Acceleration" | "SprintSpeed" | ... | "GKReflexes"   // 35 attribute
PlayStyle = "FinesseShot" | "WhippedPass" | ...               // + "+X" = PS+

Role {
  id: string                     // "inside_forward_attack"
  position: Pos                  // LW/RW/...
  focus: Focus
  attrWeights: Map<Attr, number> // Σ = 1  (hibrit: pozisyon OVR tabanı + rol bükümü)
  weapons: Weapon[]              // rolü tanımlayan ⭐ statlar
  playstyles: { priority: PlayStyle[], bonus: PlayStyle[] }
  acceleRATE: Archetype[]        // izinli: ["Explosive"] vb.
  globalFloor: { Stamina:68, Reactions:60, Composure:60 }  // override edilebilir
  movementTags: string[]         // NLG + atak paterni
}
Weapon { attr: Attr, hardMin: number, target: number }  // ör. {Finishing, 66, 78}

Player {
  id, name, age, positions: Pos[], preferredFoot
  attrs: Map<Attr, number>
  playstyles: Map<PlayStyle, 1|2>   // 1=PS, 2=PS+
  acceleRATE: Archetype             // türetilmiş (boy+AGI+STR+ACC)
  potential, value                  // source'lu (MODELED/IMPORT)
}

// Kullanıcının davranış seçiminden türeyen, role uygulanan kişiselleştirme
IntentProfile {
  slot: SlotId                   // taktikteki pozisyon
  tags: BehaviorTag[]            // seçilen davranışlar (behavior_tag_library)
  // tag'lerden türetilen:
  weaponBoost: Map<Attr, number> // seçilen tag'lerin weapon attr'leri (+ağırlık)
  resolvedRole: Role             // tag oylarından seçilen FC27 rol+focus
}
```

---

## 2. ROLEFIT (oyuncu × rol) — attribute uyumu

```
function RoleFit(player, role, intent?) -> { score0_100, projectedRating, badge, reasons }

  // --- (a) Ağırlıklı taban (0..1)
  base = Σ_a  role.attrWeights[a] * norm(player.attrs[a])

  // --- (b) IntentProfile varsa ağırlıkları kaydır (davranış kişiselleştirmesi)
  if intent:
     weights' = renormalize(role.attrWeights + intent.weaponBoost)   // seçilen davranışların statlarını yukarı tart
     base     = Σ_a weights'[a] * norm(player.attrs[a])

  // --- (c) Weapon gate (karar 1 + C + D)
  weaponPenalty = 0 ; worstWeaponState = GREEN
  for w in role.weapons:
     effMin = w.hardMin - (hasRelevantPSplus(player,w) ? 4 : 0)   // PS+ telafisi (karar D)
     v = player.attrs[w.attr]
     if v >= w.target:                      state = GREEN
     elif v >= effMin:                      state = YELLOW ; weaponPenalty += (w.target - v)*0.4
     else:                                  state = RED    ; weaponPenalty += (w.target - effMin)*0.4 + (effMin - v)*1.5
     worstWeaponState = worse(worstWeaponState, state)
     reasons.add(weaponReason(w, v, state))   // "bitiricilik zayıf (64/78)"

  // --- (d) PlayStyle bonus
  psBonus = 0
  for ps in role.playstyles.priority: psBonus += player.has(ps)? (player.isPlus(ps)? 6:3) : 0
  for ps in role.playstyles.bonus:    psBonus += player.has(ps)? (player.isPlus(ps)? 3:1.5) : 0

  // --- (e) AcceleRATE
  acr = role.acceleRATE.includes(player.acceleRATE) ? +4 : -6

  // --- (f) Cezalar: out-of-position + global floor
  oop   = player.positions.includes(role.position) ? 0 : -15      // sarı ünlem
  floor = Σ_f max(0, role.globalFloor[f] - player.attrs[f]) * 0.8  // Sta/Rea/Cmp altı

  raw = base*100 + psBonus + acr - weaponPenalty - oop - floor
  score = clamp(raw, 0, 100)

  // projectedRating: "bu rolde kaç oynar" — weapon tavanı kırpılır (karar B+C)
  projectedRating = round( base*99 + psBonus*0.4 + acr*0.3 )
  if worstWeaponState == RED:    projectedRating = min(projectedRating, 74)   // silahı eksikse tavan
  if worstWeaponState == YELLOW: projectedRating = min(projectedRating, 82)

  badge = worstWeaponState    // 🟢 GREEN / 🟡 YELLOW / 🔴 RED
  return { score, projectedRating, badge, reasons }   // reasons = düz-Türkçe NLG cümleleri
```

**Rozet → kullanıcı dili (NLG):** 🟢 "Bu rol için biçilmiş kaftan" · 🟡 "İş görür ama {weapon} zayıf — {NLG sonucu}" (ör. "plase/falso golü bekleme") · 🔴 "Rolün özünü oynayamaz: {weapon} çok düşük."

---

## 3. INTENTFIT (oyuncu × seçilen davranışlar) — "istediğini yapabilir mi"
Rakiplerin yapmadığı skor. Kullanıcının o slota seçtiği her davranış için, o davranışın weapon'ını oyuncu karşılıyor mu?

```
function IntentFit(player, intent) -> { score0_100, perTag: [{tag, ok, note}] }
  total = 0
  for tag in intent.tags:
     w = primaryWeaponOf(tag)                       // ör. "orta açsın" → Crossing
     ratio = clamp(norm(player.attrs[w]) / norm(tagExpected[tag]), 0, 1)   // beklenen eşiğe oran
     perTag.add({tag, ok: ratio>=0.85, note: ratioNote(tag,w,ratio)})
     total += ratio
  score = round(100 * total / intent.tags.length)
  return { score, perTag }
```
> Örnek: kullanıcı kanada *orta açsın* dedi, oyuncunun Crossing'i düşük → o tag "yapamaz (🟡/🔴)" işaretlenir, IntentFit düşer. **Bu, "seçtiğin oyun bu oyuncuyla oynanmıyor" uyarısının motoru.**

---

## 4. SQUADFIT (kadro × taktik) — optimal yerleştirme + zayıf halka
11 slot var; her slotun bir resolvedRole'u (preset/davranış'tan) ve IntentProfile'ı var.

```
function SquadFit(squad, tactic) -> { assignment, squadFit%, intentFit%, weakLinks[], explain }
  // (a) maliyet matrisi: her oyuncu × her slot
  for p in squad, s in tactic.slots:
     rf = RoleFit(p, s.role, s.intent).score
     if_ = IntentFit(p, s.intent).score
     combined[p][s] = 0.7*rf + 0.3*if_          // yerleştirmede RoleFit ağırlıklı, intent destek
  // (b) optimal atama: her slota 1 oyuncu, toplam maksimum (Hungarian / macar algoritması)
  assignment = Hungarian(maximize combined, slots=11)
  squadFit  = avg(RoleFit over assigned)
  intentFit = avg(IntentFit over assigned)
  // (c) zayıf halkalar: en düşük slot skorları + rozeti 🔴/🟡 olanlar
  weakLinks = slots.sortAsc(byAssignedScore).where(badge != GREEN).top(3)
  // (d) açıklama (NLG): atama + weakLinks + atak paterni (movementTags birleşimi)
  return ...
```

**Yedek/derinlik:** her slot için atanandan sonraki en iyi 2 oyuncu = "yedek kim kimin" (depth chart motoru — bu konuşmadaki depth chart'ın otomatik hali).

---

## 5. TAKTİK ARTI/EKSİ/RİSK (rules engine)
Attribute/atama durumundan otomatik tanı. Kurallar deklaratif (seed), motor uygular:
```
RULE kontra_riski_yuksek:
  WHEN tactic has (≥2 fullbacks intent "sürekli bindirsin") AND assignedCB.avgPace < 66 AND pivotCount == 1
  THEN risk("Kontra riski YÜKSEK", "Bekler uçuyor + stoperler yavaş + tek çapa — geç dönüşte açık.", fix:"2. pivot ekle / hat düşür / hızlı CB")
RULE teknik_kalite_yetersiz:
  WHEN tactic.creativeRoleCount ≥ 3 AND squad.avgVisionInCreativeSlots < 68
  THEN warn("Yaratıcılık yetersiz","4 yaratıcı rol var ama vizyon düşük; zayıf takımları ezer, iyilerde tıkanır.")
RULE santrfor_izolasyonu:
  WHEN formation.strikers == 1 AND wingers all intent "içe kessin" AND noLateRunner
  THEN warn("Santrfor yalnız","Tek forvet destek görmüyor; kutuya geç gelen ekle veya 2. forvet.")
```
(Bu konuşmadaki her tanı — kontra, bitirici eksikliği, izolasyon — buradan deklaratif çıkar.)

---

## 6. ÇIKTI (kullanıcıya)
- Taktik görünümü: her slot → oyuncu + RoleFit% + projectedRating + 🟢🟡🔴 rozet + 1 cümle NLG.
- Üst bar: **SquadFit %** (attribute uyumu) + **IntentFit %** (seçtiğin oyunu oynayabilme).
- Zayıf halkalar paneli (en düşük 3) + her biri için transfer-filtresi linki (scout mantığı, RoleFit'e göre sıralı).
- Taktik artı/eksi/risk kartları (rules engine).
- Atak paterni açıklaması (movementTags → NLG): "Sağdan RB bindirir, RW içe kıvrılıp 10 ile kombine, arka direğe orta..."

---

## 7. TEST SENARYOLARI (deterministik, birim test)
**T1 — Weapon gate (D telafisi):** LW Inside Forward, iki özdeş oyuncu Finishing=64 (hardMin 66). A'da Finesse Shot+ var.
→ A: effMin=62 → YELLOW değil, 64≥62 ama <target → **YELLOW**, penalty küçük, projected daha yüksek. B: 64<66 → **RED**, projected ≤74. *(Doğrula: PS+ olan belirgin üstte.)*

**T2 — IntentFit düşer:** Kanat oyuncusu Crossing=55; kullanıcı *orta açsın* seçti (tagExpected 68).
→ ratio≈0.80 <0.85 → tag "ok=false", IntentFit düşük; RoleFit yüksek olsa bile üst barda "seçtiğin oyunu tam oynayamaz" sinyali. *(RoleFit≠IntentFit ayrışması kanıtlanır.)*

**T3 — SquadFit atama:** 11 oyuncu, 11 slot; bir oyuncu iki slotta da en iyi → Hungarian tek slota atar, diğerine 2. en iyi gider; toplam maksimize. *(Greedy'nin yanıldığı, Hungarian'ın doğru atadığı fikstür.)*

**T4 — Kontra riski kuralı:** iki AWB-Attack + CB pace 58/61 + tek pivot → RULE tetiklenir, risk kartı + fix önerisi döner. *(Senin Port Vale tanının otomatik üretimi.)*

**T5 — AcceleRATE:** aynı statlı iki kanat, biri Explosive biri Controlled → RoleFit'te +4 vs −6 farkı (10 puan). *(Arketip ağırlığı.)*

**T6 — Out-of-position:** CB'yi CAM slotuna koy → −15 + weapon RED → 🔴 ve dipte. *(Kullanıcı yanlış yerleştirmeyi görür.)*

---

## 8. AÇIK UÇLAR (kalibrasyon)
- `weaponPenalty` katsayıları (0.4 / 1.5) ve projected tavanları (74/82) → gerçek kart verisiyle (açık GitHub set) kalibre.
- `tagExpected[tag]` eşikleri → behavior_tag_library ile hizalı tablo (seed).
- `combined` ağırlığı (0.7/0.3) → kullanıcı testinde ayar.
- Hungarian yerine çok-slot/çok-oyuncu esnekliği gerekirse min-cost-flow'a genişletilebilir.
- AcceleRATE türetme formülü (boy+AGI+STR+ACC eşikleri) config (FC27'de teyit).
