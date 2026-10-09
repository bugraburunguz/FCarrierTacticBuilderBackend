# 06 · MAÇ SİMÜLASYON OYUNU — "OSM × FM" arası online 2D taktik menajerlik
Kod adı: **PITCHMIND** (geçici). Bu doküman kod ajanının bir **spike → MVP** inşa edebileceği seviyede yazıldı. Tüm katsayılar `CALIBRATE` = başlangıç değeri, ayarlanacak; `engine-params.v1.json`'da yaşar, koda gömülmez.

---

## 0. VİZYON (tek paragraf)
OSM'nin **sosyal/online döngüsü** (lig, maç günü, rakip insanlar, transfer) + FM'nin **2D maç izleme ve canlı müdahalesi**, ama FM'nin karmaşıklığı olmadan. Oyuncular küçük yuvarlaklar; değer **taktikte**: kendi taktiğinin ve rakibin karşı hamlelerinin sahadaki etkisini canlı görürsün, anlık müdahale edersin; güçlü yönünü kullanan kazanır, "hurra atak" yapan kontraya yakalanır. Mevcut FCareer motorunun (roller, davranış tag'leri, RoleFit, rules engine, NLG) doğal devamı: **tag'ler artık sahada hareket olarak oynanır**.

Benzersiz değer önerisi:
1. **Açıklanabilir sim**: "Gol yedin çünkü sağ bekin bindirdi, tek pivot vardı, stoper yavaş" — rules engine'in canlı versiyonu.
2. **Davranış = hareket**: "Sürekli bindirsin" seçtiğin bek gerçekten bindirir, bunu sahada görürsün.
3. **Sis perdesi (fog of war) taktik okuma**: rakibin ayarlarını görmezsin, **hareketini** görürsün; analist paneli "rakip hattı 10 m yükseltti, arkası boş" diye çıkarım yapar.

---

## 1. VERİ VE LİSANS (✅ onaylı → 08 D-14: online = L1, özel kariyer maçı = L2; L3/L4 yok)
EA FC reytingleri/oyuncu adları/yüzleriyle **ticari** bir oyun yapmak = EA verisinin ve oyuncu kişilik haklarının (FIFPro/kulüp lisansları) izinsiz ticari kullanımı. Companion app (bugünkü ürün) ile **oyun** arasındaki risk farkı büyük.
| Seçenek | Açıklama | Risk | Öneri |
|---|---|---|---|
| L1 | Kendi evren: üretilmiş (fictional) oyuncular, kendi attribute ölçeğimiz, kendi kulüp/lig adları | Düşük | ✅ **Online oyun için varsayılan** |
| L2 | Kullanıcının kendi Live Editor import'u ile **özel, tek oyunculu** maçlar (kendi verisi, kişisel kullanım) | Düşük-orta | ✅ Kariyer modu entegrasyonu (CAR-01) |
| L3 | Gerçek isimler, EA reytingleri olmadan (kendi scout reytingimiz) | Orta-yüksek (isim/kişilik hakkı) | ⏳ Lisans görüşmesi sonrası |
| L4 | EA reytingleri + gerçek isimler, online ticari | **Yüksek** | ❌ |
Teknik sonuç: Motor **soyut bir attribute şeması** (`SimAttributes`) tüketir; EA 35-attribute şemasından eşleme yalnız bir **adapter** (`FcAttributeAdapter`) — L2 için. L1 için `PlayerGenerator` (yaş, mevki arketipi, potansiyel dağılımlarından oyuncu üretir).

---

## 2. OYUN DÖNGÜSÜ
```
Kulüp al (lig 16 takım: insan + AI) → kadro (25) → antrenman/taktik → MAÇ GÜNÜ (günde 1-2 maç, sabit saat)
   → canlı izle + müdahale  (izlemezsen: önceden kurduğun "koşullu talimatlar" ile AI yönetir)
   → maç raporu (xG, ısı haritası, oyuncu notları, "neden" açıklamaları)
   → transfer pazarı (kullanıcılar arası + AI), finans, moral, sakatlık → sonraki maç günü
Sezon ≈ 15 maç günü × 2 = 2-3 hafta (OSM ritmi). Yükselme/düşme.
```
MVP kapsamı: **AI'ya karşı hazırlık maçı (canlı) + 8 takımlı async lig**. Ekonomi/transfer sonraki faz.

---

## 3. MOTOR MİMARİSİ (sunucu-otoriter, deterministik)

### 3.1 Temel parametreler
| Parametre | Değer |
|---|---|
| Koordinat | Metre, saha 105 × 68, orijin sol alt köşe; atak yönü devre arasında döner |
| Zaman adımı | Sabit `dt = 0.1 s` (10 Hz) → 90 dk ≈ 54 000 tick (+ uzatma) |
| Rastgelelik | Maç başına `seed`; `SplittableRandom` (tek kaynak, tick sırasıyla tüketilir) |
| Determinizm | `seed + engineVersion + paramsVersion + commandLog` → birebir aynı maç (replay, hata ayıklama, hile denetimi) |
| Performans hedefi | Headless tam maç < 150 ms (tek çekirdek) → Monte Carlo 1000 maç < 3 dk; canlı modda bir thread ≥ 50 maç |
| Dil | Java 17 (backend ile aynı toolchain; 08 D-15), ayrı Maven modülü `fcareer-match-engine` (Spring'siz saf kütüphane) + `fcareer-match-service` (Spring Boot: zamanlama, WebSocket, kalıcılık) |

### 3.2 Durum (state)
```java
record Vec2(float x, float y) {}
final class BallState  { Vec2 pos; float z; Vec2 vel; float vz; Integer ownerId; int lastTouchTeam; }
final class PlayerState{ int id, team, slotIndex; Vec2 pos, vel; float facing; float stamina /*0..1*/; float fatigueDebt;
                         PlayerIntent intent; int cooldownTicks; boolean onPitch; int yellow; }
final class MatchState { int tick; int[] score; Phase phase; Restart restart; BallState ball; PlayerState[] players;
                         TeamTactic[] tactics; Momentum momentum; List<MatchEvent> events; }
enum Phase { IN_POSSESSION, OUT_OF_POSSESSION, ATTACK_TRANSITION, DEFENCE_TRANSITION, SET_PIECE, DEAD }   // takım bakış açısından
```

### 3.3 Tick döngüsü (sıra sabit — determinizm için)
```
for each tick:
  1. applyCommands(tick)                 // kuyruktaki kullanıcı komutları (bkz. §5)
  2. updatePhase()                       // top sahibi değişti mi → transition sayaçları (ATTACK/DEFENCE_TRANSITION ~4 s)
  3. computeTeamShape(team) x2           // §3.4 — her oyuncu için "çapa" hedef nokta
  4. for player in order(id):            // sıralı, paralel değil
        if player == ballOwner: decideOnBall()      // §3.5 (her tick değil: karar aralığı 0.3-0.8 s, Reactions'a bağlı)
        else if team in possession: offBallMovement()   // §3.6 koşu desenleri
        else: defend()                               // §3.7 pres/markaj/kapama
  5. steer(players)                      // §3.8 hedefe hız/ivme sınırlarıyla hareket, çarpışma ayrıştırma
  6. ballPhysics()                       // §3.9
  7. resolveContacts()                   // top kontrolü, ikili mücadele, top kesme, faul (§3.10)
  8. stamina/fatigue update              // §3.11
  9. emit snapshot (her tick) + events
```

### 3.4 Takım şekli (taktiğin sahaya dönüşümü)
Her slot için **çapa** (anchor) = formasyon koordinatı (`formations.json` x,y — 0-100 ölçeği zaten var) → metreye çevir, sonra dönüştür:
```
anchor = formationPos(slot)
anchor.y  = lerp(lineLow, lineHigh, defensiveDepth/100)      // savunma hattı yüksekliği (FC27 "Defensive Depth" — TeamSetup'ta var)
          + phaseShift(phase, slot.group)                     // topa sahipken bekler/ortalar yukarı, savunmada geri
anchor.x  = centerX + (anchor.x - centerX) * widthFactor(phase, setup.width)
anchor   += ballShift(ball.pos, compactness)                 // blok topa doğru kayar: x'te %35, y'de %25 (CALIBRATE)
anchor   += roleOffset(slot.role, slot.tags, phase, ball)    // §3.6: davranış tag'leri burada hareket olur
clamp(anchor, offsideLine rules)
```
Kompaktlık: savunmada takımın en geri–en ileri oyuncu mesafesi hedefi `compactLen = 28–40 m` (blok tipine göre).

### 3.5 Top sahibinin kararı (utility AI)
Aday aksiyonlar ve beklenen değer:
```
EV(action) = P(success) * value(after) - (1 - P(success)) * danger(turnover at location)
value(pos) = xT(pos)            // 12x8 "expected threat" grid (kamuya açık xT yaklaşımı; kendi sim verimizle yeniden öğrenilir)
danger(pos)= xT_opp(pos)        // rakip için tehlike
```
| Aksiyon | P(success) girdileri | Not |
|---|---|---|
| Pas (her takım arkadaşına) | mesafe, pas çizgisine en yakın rakiplerin kesme olasılığı (rakip hız/Interceptions/Reactions), alıcının boşluğu, Short/LongPassing, Vision (görebilme: Vision düşükse uzak/ters açılı adaylar elenir), ayak/zayıf ayak | Ara pas = alıcının **koşu hedefine** pas (§3.6) |
| Dribbling (5-8 yön) | en yakın rakip mesafesi/açısı, Dribbling/Agility/Balance/BallControl vs rakip DefAwareness/StandingTackle, hız farkı | Sonuç ikili mücadelede çözülür (§3.10) |
| Şut | **xG** (mesafe, açı, baskı, vücut parçası, asist tipi) × bitiricilik çarpanı (Finishing/ShotPower/LongShots/Composure, PlayStyle Finesse/Power) | |
| Orta | kanatta + kutuda hedef sayısı (setup "players in box"), Crossing/Curve; hedef = arka/ön direk bölgesi | |
| Topu sakla / geri pas / uzaklaştır | baskı seviyesi, skor/dakika (zaman geçirme talimatı) | |
Seçim: `softmax(EV / T)`; sıcaklık `T = T0 * (1.3 - composure*0.6) * fatigueFactor` — soğukkanlı oyuncu en iyi seçeneği daha tutarlı seçer. Taktik ağırlıkları: build-up `Short` → kısa pas EV × 1.15, `Counter` → ileri pas/koşu EV × 1.25 (CALIBRATE).

### 3.6 Topsuz hareket = davranış tag'leri (mevcut `behavior_tag_library` → hareket desenleri)
Her tag bir **MovementPattern**: `trigger` (ne zaman), `target` (nereye), `duration`, `cooldown`, `cost` (stamina).
| Tag (mevcut) | Pattern | Tetik | Hedef |
|---|---|---|---|
| Bek: *Sürekli bindirsin* | `OVERLAP` | topa sahip + top aynı kanatta orta sahada + önündeki kanat içeride | kanat çizgisi, rakip kanat oyuncusunun arkası |
| Bek: *İçeri katsın* | `INVERT` | topa sahip, build-up fazı | 6 numaranın yanına (half-space) |
| Bek: *Sadece savunsun* | `HOLD_LINE` | her zaman | hat çapası |
| CB: *Öne çıkıp bassın* | `STEP_OUT` | rakip 10/forvet sırtı dönük top alıyor, mesafe < 12 m | alıcıya bas |
| CDM: *Stopere düşsün* | `DROP_BETWEEN_CB` | build-up | iki stoper arası |
| CM: *Geç kutuya dalsın* | `LATE_BOX_RUN` | top son 3'te kanatta/orta öncesi | penaltı noktası çevresi, gecikmeli (1–2 s) |
| CAM: *Forvetin ötesine koşsun* | `RUN_BEYOND` | 9 numara düşüyor veya topçu Vision yüksek | savunma arkası |
| Kanat: *İçe kessin* | `CUT_INSIDE` | topu aldığında | half-space → şut/pas bölgesi |
| Kanat: *Orta açsın* | `HUG_LINE` | her zaman (sahiplikte) | çizgi |
| ST: *Arkaya koşsun* | `RUN_IN_BEHIND` | rakip hattı yüksek ∧ topçunun önü açık | ofsayt çizgisi + 2 m (zamanlama: Positioning) |
| ST: *Derine gelip bağlasın* | `DROP_DEEP` | build-up / rakip blok derin | 10 numara bölgesi |
| ST: *Hava kulesi* | `HOLD_UP_BOX` | orta tetiklenince | ön direk/merkez |
| ST: *Baskı yapsın* | `PRESS_TRIGGER_LEADER` | rakip stoper topu aldı | stopere, pas yolunu kapatarak |
Zamanlama kalitesi = `Positioning`/`Reactions`/`Vision` (ofsayt riski bunlarla düşer). Bu tablo `seed/movement-patterns.json` olur; mevcut tag ID'leriyle eşlenir (yeni tag eklemek = yeni pattern satırı).

### 3.7 Savunma
- **Mod**: `defensiveApproach` (Deep / Balanced / High / Aggressive — FC27 setup) → hat yüksekliği + pres başlangıç çizgisi + pres yoğunluğu.
- **Pres tetikleri**: geri pas, kötü ilk dokunuş, kenara sıkışma (touchline trap), sırtı dönük alıcı. Tetik olunca en yakın 1–3 oyuncu `PRESS`, diğerleri pas yollarını kapatır (`COVER_SHADOW`).
- **Markaj**: bölgesel; her savunmacı kendi bölgesindeki en tehlikeli rakibe (xT × yakınlık) yönelir; ceza sahasında adama daha sıkı.
- **Kontra-pres** (gegenpress): top kaybından sonra `ATTACK→DEFENCE_TRANSITION` ilk 5 s'de yakın oyuncular agresif; stamina maliyeti yüksek.

### 3.8 Hareket (steering)
- `maxSpeed = lerp(6.2, 9.6, SprintSpeed/99)` m/s; `accel = lerp(2.5, 6.0, Acceleration/99)` m/s² ; AcceleRATE eğrisi: Explosive → ilk 1.5 s'de +%20 ivme, Lengthy → 3 s sonrası +%4 tepe hız (CALIBRATE).
- Agility → dönüş hızı (rad/s); Balance → temasta yavaşlama azalır.
- Fatigue: `maxSpeed *= 1 - 0.18*fatigue`.
- Ayrıştırma: oyuncular arası min 0.8 m (basit itme).

### 3.9 Top fiziği (2.5D)
- Yer: `vel *= (1 - 0.6*dt)` (sürtünme, CALIBRATE), Havada: yerçekimi `vz -= 9.81*dt`, sekme kaybı 0.5.
- Pas hızı = mesafe ve tipe göre (yerden 12–22 m/s, havadan parabolik), hata = `N(0, σ)`; `σ = σ0 * (1.4 - passing/99) * pressureFactor * (weakFoot? wfPenalty)`.

### 3.10 Çözümleme formülleri (başlangıç, hepsi CALIBRATE)
`σ(z) = 1/(1+e^-z)`; attribute'lar 0–99 → `a/99`.
| Olay | Olasılık |
|---|---|
| Pas tamamlanır | Uçuştaki top fiziği + kesme: her tick'te top yolundaki rakip için `P(intercept) = σ(k1*(reachMargin) + k2*(interceptions - 0.6))` |
| İlk dokunuş temiz | `σ(3.0*(ballControl - 0.55) - 2.0*incomingSpeedNorm - 1.5*pressure)` |
| Dribbling ikili mücadele | `P(atk) = σ(2.5*(0.5*drib + 0.3*agi + 0.2*bal) - 2.5*(0.6*defAw + 0.4*stTackle) + 0.8*speedDiff)` |
| Faul | mücadele başına `p = 0.04 + 0.10*aggression*(1-defAw)`; ceza sahasında → penaltı |
| Şut isabet + gol | `xG_base(dist, angle, pressure, header)` × `finishMult = 0.75 + 0.5*finishing*composure`; kaleci: `P(save | onTarget) = σ(k*(gk - shotQuality))`, `gk = 0.4*reflexes + 0.3*diving + 0.3*positioning` |
| Kafa | `σ(2.0*(0.4*jumping + 0.3*heading + 0.3*strength) + 0.02*(heightCm-180) - rivalTerm)` |
| PlayStyles | Çarpan/eşik bonusları: Finesse Shot (ceza yayından plase xG ×1.15), Power Header, Intercept (kesme yarıçapı +0.4 m), Press Proven (pres staminası −%25), Quick Step (ilk 1 s ivme +%15). PS+ = bonus ×1.6. Tablo `seed/playstyle-effects.json`. |

### 3.11 Yorgunluk, moral, kartlar
- Stamina: `dStamina = -(baseDrain + sprintDrain*speed²) * (1.25 - stamina_attr) * workRateFactor * pressIntensity`. Devre arası +%12 toparlanma.
- Moral/momentum: gol/kaçan büyük şans/kart → takım `momentum` (−1..1) → karar sıcaklığı ve pres isteği üzerinde küçük etki (±%5).
- Kartlar/sakatlık: faul şiddeti → sarı/kırmızı; sakatlık olasılığı yorgunlukla artar (düşük base).

### 3.12 Duran toplar (MVP basit)
Korner/serbest vuruş/penaltı/taç/aut: sabit dizilim şablonları (`set-pieces.json`) + "players in box" ayarı; korner hedefi (ön direk/arka direk/kısa) kullanıcı seçimi.

---

## 4. TAKTİK MODELİ (mevcut FCareer verisinin yeniden kullanımı)
```json
TeamTactic {
  "formationId": "4-2-3-1",                         // seed/formations.json (29 formasyon)
  "slots": [{ "slotId": "RB", "roleId": "attacking_wingback_attack", "tags": ["fb_overlap","fb_early_cross"] }, ...], // roles.json + behavior-tags.json
  "setup": { "buildUp": "Short|Balanced|Counter", "defensiveApproach": "Deep|Balanced|High|Aggressive",
             "defensiveDepth": 0-100, "width": 0-100, "playersInBox": 3-8, "corners": 1-4, "freeKicks": 1-4 },  // mevcut kodda yalnız buildUp + depth var (web/src/components/TeamSetup.tsx); diğerleri yeni — FC27 Team Tactics alan listesi VERIFY
  "shouts": ["PRESS_MORE","SLOW_TEMPO"],          // canlı, süreli
  "conditionals": [{ "if": "minute>=70 && goalDiff<0", "then": { "setup.defensiveApproach":"High", "shouts":["ATTACK"] } }]
}
```
- EA taktik kodu import (mevcut `tacticcode` modülü; codec bugün formasyon + rol/focus çözüyor — `seed/ea-tactic-codec.json`) → kullanıcı FC27'deki dizilişini tek kodla sim'e taşır, setup değerleri sim'de tamamlanır. **Güçlü köprü.**
- Mevcut **RulesEngine** maç öncesi "risk kartları"nı üretir ("kontra riski YÜKSEK"); maç sonunda gerçek olaylarla eşleştirilir ("Uyarı doğru çıktı: 2 kontra golü").

---

## 5. CANLI MÜDAHALE & RAKİBİ OKUMA
| Komut | Etki gecikmesi (gerçekçilik) | Sınır |
|---|---|---|
| Shout (pres artır, tempo düşür, kanatlara oyna, kontra, zaman geçir, sakin ol) | Hemen, etki 20 sn'de rampalanır, 5 dk sürer | Aynı anda 2 |
| Setup slider'ları (hat, genişlik, pres, kutudaki oyuncu) | 20 sn "iletişim gecikmesi" veya ilk ölü top (hangisi önce) | — |
| Rol/tag değişimi, formasyon | İlk ölü topta | Devre başına 3 "taktik penceresi" (CALIBRATE) |
| Oyuncu değişikliği | İlk ölü topta | 5 oyuncu / 3 pencere (gerçek kural) |
| Devre arası | Her şey serbest | — |
Komut protokolü: `{matchId, issuedAtTick, type, payload}` → sunucu doğrular, `applyAtTick` atar, log'a yazar (replay).

**Rakibi okuma (fog of war):** Rakibin ayarlarını görmezsin. Analist paneli tracking verisinden **çıkarım** üretir:
- Ortalama hat yüksekliği/ genişlik / blok uzunluğu değişimi ("Rakip hattı son 5 dk'da 9 m yükseldi → arkası boş, *Arkaya koşsun* değerli").
- Pres yoğunluğu (PPDA), en çok topun geldiği koridor, rakibin en tehlikeli oyuncusu (xT katkısı).
- Öneri motoru = mevcut rules engine'in canlı versiyonu (`live-rules.json`): `WHEN oppLineHeight > 60 AND ourST.pace > 85 THEN suggest("Arkaya oyna")`.
- Sen de rakibe "okunursun": sürekli aynı kanattan oynarsan AI rakip (veya insan) bunu görür.

**AI menajer** (AI takımlar + izlemeyen kullanıcılar): her 5 maç dakikasında durum değerlendirmesi (skor, dakika, xG farkı, stamina, kartlar) → kural tablosundan aksiyon; zorluk seviyesi = okuma doğruluğu + tepki gecikmesi.

---

## 6. CANLI ANALİTİK & GÖRSELLEŞTİRME (istemci)
- **Renderer:** MVP'de Canvas2D; performans gerekirse PixiJS (08 D-15). Katmanlar: saha (statik), oyuncular (daire r=0.9 m görsel, takım rengi, forma no, seçili halka, stamina yayı), top (z'ye göre ölçek + gölge), overlay (koşu okları, pas çizgileri, pres alanları, ofsayt çizgisi).
- 10 Hz snapshot → 60 fps **interpolasyon**; 200 ms jitter buffer.
- Hız: canlı lig maçı sabit hız (1 maç dakikası ≈ 8 sn → ~13 dk maç; CALIBRATE ürün kararı); tek oyunculu maçta 1×/2×/4×/"önemli anlar".
- Paneller: skor + xG yarışı grafiği, momentum barı, olay akışı (Türkçe NLG yorum: mevcut NlgService şablon yaklaşımı), oyuncu notları (canlı), stamina listesi, ısı haritası (devre/toplam), pas ağı, şut haritası, "neden" kartları ("Gol: sağ kanat bindirmesi → arka direk; rakip sol beki tek kaldı").
- Mobil: saha üstte, komut paneli alttan bottom-sheet; shout butonları tek dokunuş.
- Maç sonrası: tam replay (seed + command log → istemci tarafında yeniden simüle **etmez**; sunucu snapshot dosyası `zstd` sıkıştırılmış ~1–2 MB/maç), highlight kesitleri (xG > 0.15 olaylar).

---

## 7. ONLINE MİMARİ
```
web (React)  ──WS──►  match-service (Spring Boot, WebSocket)  ──►  match-engine (saf Java lib)
     │                      │ zamanlayıcı: fixture kickoff → engine worker (Postgres SKIP LOCKED kuyruk)
     └──REST──► fcareer-backend (auth, kulüp, kadro, transfer, lig)   ◄── ortak Postgres + Redis (pub/sub: canlı maç kanalları)
```
- **Sunucu-otoriter**: istemci yalnız komut gönderir; hile yüzeyi = komut doğrulama.
- Snapshot protokolü (binary, little-endian): `tick:u32, ball:{x,y,z:i16 (cm)}, 22×{x,y:i16, flags:u8, stamina:u8}` ≈ 150 B/tick → 1.5 KB/s/izleyici; her 5 s tam keyframe, arada delta. Yeniden bağlanınca son keyframe.
- İzleyen yoksa maç **hızlı sim** (headless, ms'ler) ve replay olarak saklanır; kullanıcı sonra izler. Canlı izleyen varsa gerçek zamanlı akış.
- Ölçek: 1 vCPU ≈ 50+ eşzamanlı canlı maç (10 Hz × 22 ajan hafif); async maçlar toplu.
- Kalıcılık şeması (Flyway `V20__sim.sql` taslak):
```sql
CREATE TABLE sim_club (id BIGSERIAL PRIMARY KEY, owner_user_id BIGINT REFERENCES app_user(id), name TEXT NOT NULL, colors JSONB, budget BIGINT, reputation INT);
CREATE TABLE sim_player (id BIGSERIAL PRIMARY KEY, club_id BIGINT REFERENCES sim_club(id), name TEXT NOT NULL, birth_year INT,
  positions TEXT[] NOT NULL, attrs JSONB NOT NULL, playstyles JSONB NOT NULL DEFAULT '{}', accelerate TEXT, height_cm SMALLINT,
  preferred_foot TEXT, weak_foot SMALLINT, work_rate TEXT, potential SMALLINT, morale SMALLINT, fitness SMALLINT, injury_until DATE,
  source TEXT NOT NULL CHECK (source IN ('GENERATED','USER_IMPORT')));
CREATE TABLE sim_league (id BIGSERIAL PRIMARY KEY, name TEXT, tier INT, season INT, status TEXT);
CREATE TABLE sim_fixture (id BIGSERIAL PRIMARY KEY, league_id BIGINT REFERENCES sim_league(id), round INT, home_club_id BIGINT, away_club_id BIGINT,
  kickoff_at TIMESTAMPTZ NOT NULL, status TEXT NOT NULL DEFAULT 'SCHEDULED');
CREATE TABLE sim_match (id BIGSERIAL PRIMARY KEY, fixture_id BIGINT UNIQUE REFERENCES sim_fixture(id), seed BIGINT NOT NULL,
  engine_version TEXT NOT NULL, params_version TEXT NOT NULL, home_tactic JSONB NOT NULL, away_tactic JSONB NOT NULL,
  score_home SMALLINT, score_away SMALLINT, stats JSONB, replay_uri TEXT, finished_at TIMESTAMPTZ);
CREATE TABLE sim_match_command (match_id BIGINT REFERENCES sim_match(id), seq INT, issued_tick INT, apply_tick INT, club_id BIGINT, type TEXT, payload JSONB,
  PRIMARY KEY (match_id, seq));
CREATE TABLE sim_match_event (match_id BIGINT, tick INT, type TEXT, team SMALLINT, player_id BIGINT, data JSONB);
CREATE TABLE sim_player_match (match_id BIGINT, player_id BIGINT, minutes SMALLINT, rating NUMERIC(3,1), stats JSONB, PRIMARY KEY (match_id, player_id));
```

---

## 8. GERÇEKÇİLİK KALİBRASYONU (kabul testleri)
Hedef dağılımlar (üst lig futbolu için yaygın aralıklar; kesin değil → `CALIBRATE`):
| Metrik | Hedef aralık (maç başına) |
|---|---|
| Toplam gol | 2.5 – 2.9 ort.; 0-0 oranı %6–9 |
| Şut / takım | 10 – 15; isabet %30–38 |
| Gol/şut | %9 – 12 |
| Pas isabeti | %72 – 90 (stile göre) |
| Topa sahip olma | çoğu maç 35–65 |
| Faul / takım | 9 – 13; sarı kart ≈ 2 |
| Korner / takım | 4 – 6 |
| Ev sahibi avantajı | ev %43–46 / beraberlik %24–27 / dep %28–31 (eşit güç) |
**Taktik akıl testleri** (her biri 2000 maçlık A/B; anlamlı fark beklenir):
1. Yüksek hat + yavaş stoper vs hızlı forvet → arkaya atılan gol oranı ↑.
2. Gegenpress → rakip yarı sahada top kazanma ↑, 70'+ stamina ↓, geç gol yeme ↑.
3. Alçak blok + kontra vs topa sahip olan → sahip olma düşük ama xG/şut yüksek.
4. *Sürekli bindirsin* iki bek + tek pivot → yenilen kontra xG ↑ (= mevcut RULE `kontra_riski_yuksek` sim'de doğrulanır).
5. Güç farkı: ortalama OVR +5 → galibiyet %≈60–70 (çok deterministik olmamalı; sürpriz şansı korunmalı).
6. Aynı seed + aynı komutlar → bit-bit aynı sonuç (determinizm testi).
Araç: `sim-bench` CLI — `--home tacticA.json --away tacticB.json --n 2000 --params v1` → markdown istatistik tablosu; CI'da küçük n ile regresyon.

---

## 9. FAZLAR (ajan için sıra + kabul kriteri)
| Faz | Kapsam | Kabul kriteri |
|---|---|---|
| **SIM-0 Spike** (2 hf) | `match-engine` modülü: state, tick döngüsü, şekil (sadece formasyon + hat), pas/dribbling/şut, top fiziği, gol/aut/korner basit; headless CLI | 1000 maç: çökme yok, gol ort. 1.5–4, determinizm testi yeşil, maç < 300 ms |
| **SIM-1 Taktik** (2-3 hf) | roller + tag→MovementPattern (§3.6), setup parametreleri, pres/markaj, stamina, PlayStyle etkileri, `engine-params.v1.json`, `sim-bench` | §8 hedef aralıklarının ≥ %70'i tutuyor; akıl testleri 1-4'ten ≥ 3'ü anlamlı |
| **SIM-2 Görselleştirme** (1-2 hf) | Replay dosyası formatı + web viewer (PixiJS/Canvas), olay akışı, istatistik paneli | 60 fps masaüstü, ≥ 45 fps orta mobil |
| **SIM-3 Canlı tek oyunculu** (2 hf) | `match-service` WebSocket, AI'ya karşı canlı maç, komutlar (§5), analist paneli (çıkarımlar), AI menajer | Komut → sahada etki ≤ 20 sn; yeniden bağlanma çalışır |
| **SIM-4 Online lig** (3-4 hf) | `sim_club/player/league/fixture`, kayıt/kulüp alma, zamanlanmış maç günleri, async hızlı sim + replay, iki insan canlı H2H, koşullu talimatlar | 8 takımlı lig bir sezonu insan müdahalesiz tamamlar |
| **SIM-5 Menajerlik katmanı** | transfer (insanlar arası + AI), antrenman/gelişim (mevcut development motoru!), finans, moral, sakatlık, yükselme/düşme, sezon ödülleri | — |
| **SIM-6 FCareer köprüsü** | CAR-01 "ya şunu alsaydım" Monte Carlo; kariyer import'uyla özel maç (L2) | — |
Gate: SIM-1 akıl testleri geçmeden SIM-3'e geçme — "gerçekçi olmalı" şartı burada ölçülür.

---

## 10. OYUN TASARIMI NOTLARI
- **Pay-to-win yok**: premium = gelişmiş analiz (pas ağı, rakip raporu), ekstra lig, kozmetik (forma/stadyum), replay arşivi. Oyuncu satın alma gerçek parayla yok.
- **Çoklu hesap/istismar**: kullanıcılar arası transferde fiyat bandı (oyuncu değerinin 0.5×–2×), aynı IP/cihaz uyarısı, transfer gecikmesi.
- **Erişim**: maç saatini kaçıran için koşullu talimat + bildirim (web push: "Maçın 10 dk sonra, rakip 3-5-2'ye geçti").
- **Sosyal**: lig sohbeti (moderasyon şart), maç sonrası "taktik raporu" paylaşımı (görsel).
- **Onboarding**: ilk maçta 3 adımlı rehber — "hattını yükselt → rakip forvet arkaya koşar → bak, gol yedin → geri çek". Taktik dersi oyunun kendisi.

## 11. RİSKLER
| Risk | Etki | Azaltma |
|---|---|---|
| Lisans (L4'e kayma) | Kapanma/dava | §1 kararı yazılı; varsayılan L1 |
| "Gerçekçi değil" algısı | Oyunun özü | §8 metrikleri + akıl testleri, oyuncu beta'sı, parametre versiyonlama |
| Determinizm kaybı (float, eşzamanlılık) | Replay/hata ayıklama | Tek thread/maç, sabit sıra, `StrictMath` gerekiyorsa, test |
| Canlı maç sunucu maliyeti | Para | Async hızlı sim varsayılan; canlı yalnız izleyen varken |
| Kapsam şişmesi (FM olmaya çalışmak) | Gecikme | Faz gate'leri; SIM-5'e kadar ekonomi yok |
