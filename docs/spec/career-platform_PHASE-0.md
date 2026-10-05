# CAREER PLATFORM — PHASE-0 TASARIM (proje kickoff)
Offline FC kariyer/menajerlik companion web uygulaması. Tarih: 2026-10-03.
UT platformundan ayrı ürün; ortak altyapı, farklı hedef kitle (offline kariyer oynayanlar).
İşaretler: **VERIFIED** (doğrulandı) · **DECISION** (kararımız) · **UNKNOWN** (açık, uydurulmadı).

---

## 0. TEK CÜMLE
SoFIFA/cmtracker bir **veritabanı + hesaplayıcı**dır; biz onun üstüne **taktik-zekâ katmanı** koyarız:
"Oynamak istediğin futbolu söyle → taktiğini kur → kimi alacağını, nasıl oynayacağını ve takımının
bu taktiğe uyumunu **sebepleriyle** göster." Moat = tarz→taktik→rol-fit→öneri→açıklanabilirlik.

## 1. ÜRÜN & PAZAR
- **Hedef kitle:** offline Career/Manager oynayan, Transfermarkt-vari derinlik isteyen kesim.
- **Rakip haritası (VERIFIED — hepsi veritabanı/hesaplayıcı, taktik-fit YOK):**
  - **cmtracker.net** — en gelişmiş: career DB, potential/value/wage calculator, save-upload, API, abonelik, community, çok-yıllı. **Taktik-fit/öneri/açıklanabilirlik yok.** En yakın rakip + olası veri-partner.
  - **fccareer.ai** (açık: github/uncleleonfan/fccareer) — FC27 DB + comparison + squad builder + calculator.
  - **futship / Player Potentials** (App Store) — DB + potential arama.
- **Farklılaşma (bizim yapıp onların yapmadığı):** RoleFit skorlama · SquadFit (taktik uyumu) ·
  taktik artı/eksi/risk motoru · atak-paterni açıklaması · tarz-bazlı öneri. **Moat burada.**

## 2. VERİ MİMARİSİ (çözülen ana karar)

### Kaynaklar + statü
| Veri | Kaynak | Statü |
|---|---|---|
| OVR, attribute, pozisyon, PlayStyle, fiziksel | **EA drop-api** (resmi, public) | VERIFIED — ama **potential/value/wage YOK** |
| Potential, value, wage (global/free) | **Açık GitHub setleri** (mzafram2001/ea-fc; fc26-player-analysis) + **kendi modelimiz** | VERIFIED veri var; model DECISION |
| Potential/value (premium, gerçek) | **Kullanıcı save import (SQLite parse)** | DECISION — kullanıcının kendi verisi |
| Gerçek-dünya kulüp/transfer (ops.) | lisanslı feed (ileride) | UNKNOWN/ileride |

### DECISION — akış
1. **Attribute backbone → EA drop-api** (resmi, temiz). FC27 marka rollover'ı çıkış sonrası re-verify.
2. **Potential/value, FREE tier → kendi modelimiz** (açık setlerle eğitilip valide).
3. **Potential/value, PREMIUM → save import** (SQLite oku → şemaya map; GERÇEK değerler, kullanıcıya özel/private).

### Yasal çizgi (DB seviyesinde dayatılacak)
- **Public tabloda yalnızca `source=MODELED` potential/value görünür.** EA'nın gerçek potential'ı
  **asla public tutulmaz** — sadece kullanıcının kendi import'unda (`source=IMPORT`, private) yaşar.
- **Scrape YOK:** SoFIFA/FUTBIN/cmtracker siteleri (ToS ihlali). Açık GitHub setleri = lisanslarını teyit et (çoğu açık-data).
- Datamined DB = backbone yapma; sadece modeli **valide etmek** için referans (açık set zaten bunu sağlıyor).

### FC 27 UYARISI (VERIFIED)
FC 27 **potential sistemini değiştirdi** (yaşlı oyuncularda yüksek potential). Eski-yıl verisiyle eğitilen
model FC 27'ye birebir uymaz → **FC 27 verisiyle ayrı kalibrasyon** şart. UNKNOWN: yeni sistemin tam kuralı.

## 3. ÇEKİRDEK MOTOR / MATEMATİK (IP)
Bu konuşma boyunca elle kurduğumuz mantığın formülleşmiş hali.

### 3a. Role Library
Her rol: `{attribute ağırlıkları Σ=1, weapon attr + hard-min, öncelikli/bonus PlayStyle, AcceleRATE arketipi, pozisyon}`.
(4-3-3/4-2-2-2 vb. için her mevkinin bu tanımını zaten çıkardık — seed data olarak girilecek.)

### 3b. RoleFit(oyuncu, rol)
```
RoleFit = Σ_a w[rol,a]·norm(attr[a]) + WeaponBonus + PlayStyleBonus + AcceleRATEBonus − Penalties
```
- WeaponBonus: ⭐ stat eşiği aşarsa +, altındaysa SERT ceza (gate).
- PlayStyleBonus: öncelikli PS (+), PS+ ekstra.
- AcceleRATEBonus: doğru arketip +, yanlış −.
- Penalties: out-of-position, global taban (Stamina/Reactions/Composure) altı.

### 3c. SquadFit (taktik uyumu)
Kadroyu 11 rol slotuna **optimal atama** (Hungarian algoritması) → toplam fit %. En düşük slot-fit = "zayıf halka".

### 3d. Taktik Artı/Eksi/Risk (rules engine)
Kurallar, ör: `iki_bek_Attack ∧ CB_pace<eşik ∧ tek_pivot → kontra_riski=YÜKSEK` ·
`4_yaratıcı_rol ∧ kadro_ort_Vision<eşik → "teknik kalite yetersiz"`. (Bu konuşmadaki her tanı buradan otomatik.)

### 3e. Açıklanabilirlik (NLG)
Rol tanımı + atama grafiğinden şablon cümle: "RW Wide Playmaker → içe kıvrılır → sağ genişlik bindiren RB'den."

### 3f. Öneri
Zayıf slotun rol-filtresiyle DB sorgusu (= scout-link mantığının motorlaşmışı), **RoleFit'e göre sıralı + bütçe filtreli**.

## 4. VERİ MODELİ (şema)
- `game_version` (FC27, FC28…) — her şey yıl-boyutlu
- `base_player` (kimlik: isim, DOB, nationality, boy, ayak — sürümler arası sabit)
- `player_rating` (game_version: OVR, attribute'lar, pozisyon, PlayStyle — drop-api)
- `player_valuation` (game_version + **source** ∈ {MODELED, IMPORT, THIRD_PARTY}: potential, value, wage) ← provenance ŞART
- `club`, `league`, `nationality`
- `role`, `role_fit` (role library + skorlar)
- `career` (kullanıcı kariyeri: takım, sezon, bütçe)
- `user_save_snapshot` (kullanıcı + kariyer + tarih — her yükleme bir versiyon)
- `user_player_state` (o kariyerde oyuncu: takım, dynamic potential, value, forma) — event-sourced
- `roster_event` (transfer/satış/kiralık — event log; "artık benim oyuncum" bunun türevi)
- **İlke:** base DB **immutable**; kullanıcı aksiyonları ayrı katman (event-sourcing → geri-al, tarihçe, "ya şunu alsaydım" bedava).

## 5. FREE / PREMIUM / KREDİ
- **FREE:** global DB (drop-api + modellenmiş potential/value) · arama/filtre · taktik builder · RoleFit/SquadFit · temel açıklama · günlük sınırlı AI kredisi · reklam.
- **PREMIUM:** save import (gerçek potential/value/kadro) · sınırsız AI öneri/optimizasyon · reklamsız · gelişmiş analiz.
- Kredi: ağır AI (squad optimize, öneri refresh) kredi tüketir; DB/filtre/görüntüleme etmez. (UT product-model ile ortak.)

## 6. MVP KAPSAM
Ekranlar: Player DB (ara/filtrele) · Taktik Builder (formasyon+rol+focus) · Kadro (kur / in-app kariyer / import) ·
**Fit Engine** (rol+squad fit %) · **Açıklanabilirlik** (artı/eksi + atak paternleri) · **Öneri** (slot bazlı, bütçeli).
- **İki kadro giriş yolu (ikisi de aynı `user_player_state` modeline düşer):** (A) in-app kariyer (takım seç → transfer/sat) · (B) save import (premium).
- **Alınan oyuncu karşılaştırması** = yeni oyuncu RoleFit vs mevcut slot sahibi (öneri motorunun görünümü).
- Mimari: Spring Boot + Postgres + provider-abstraction + rules-engine (UT ile büyük ölçüde ortak); yeni = taktik modeli + assignment solver + NLG. Frontend React.

## 7. AÇIK UÇLAR / RISKLER (UNKNOWN)
- Save dosyası formatı (SQLite tablo şeması) — parser için **örnek dosya gerek** (base `squads` vs career save farkı).
- Açık GitHub setlerinin lisansı — production öncesi teyit (çoğu açık-data ama doğrula).
- FC 27 yeni potential sisteminin tam kuralı.
- drop-api FC27 branding rollover (çıkış sonrası re-verify).
- Potential modelinin indirgenemez hatası (EA'nın el-ayarlı/rastgele bileşeni) — MAE hedefi gerçekçi tutulacak.
- "Model vs gerçek" tutarlılığı: free'deki tahmin ile premium import farkı kullanıcıya **şeffaf** gösterilecek.

## 8. KAYNAKLAR
- EA drop-api (resmi ratings): drop-api.ea.com/rating/ea-sports-fc
- Açık setler: github.com/mzafram2001/ea-fc · github.com/anish2327/FT26_player_analysis ·
  github.com/ismailoksuz/EAFC26-DataHub · github.com/Metro-123/playerlab
- Rakipler: cmtracker.net (+ /api-access, /subscriptions) · fccareer.ai (github/uncleleonfan/fccareer)
- FC 27 potential değişimi: cmtracker haberi (2026-09)

---
### SONRAKİ ADIM (P0 implementasyon sırası önerisi)
1. Şema + Flyway (game_version, base_player, player_rating, player_valuation, role).
2. drop-api ingestion + açık-set potential/value seed.
3. **Role Library seed** (bu konuşmadaki rol tanımları) + **RoleFit** motoru + test.
4. SquadFit (Hungarian) + rules-engine (artı/eksi/risk).
5. Potential/value modeli (eğit + açık-setle valide, U21 MAE).
6. MVP frontend (DB → taktik builder → fit → öneri).
7. Premium: SQLite save parser (örnek dosya gelince).
