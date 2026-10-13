# 13 · KARİYER MOTORU — teşhis + sıfırdan tasarım
İstek (Buğra): Oyuncu öneri sistemi ve taktik analiz sistemi doğru çalışmıyor. Kariyer tarafı mantıksal olarak doğru çalışsın diye baştan tasarlanmalı. Yeni görsel tasarımda bu hatalara yer olmayacak.

Yöntem: Uçtan uca kod izlendi. Java testleri çalıştırılamadı (Maven Central bu ortamdan erişilemedi), bu yüzden RoleFit / IntentFit formülleri ve rol kütüphanesi üreticisi koddan birebir Python'a çevrildi ve seed dosyaları üzerinden koşuldu. Aşağıdaki sayılar o hesaplardan. Hiçbir repo değiştirilmedi.
**Sorun değil (kontrol edildi):** seed'lerdeki tüm attribute anahtarları `Attr.java` ve DB kolonlarında var. Rol seti codec ile birebir. Potansiyel RoleFit'e sızmıyor. Hungarian çözücü doğru.

---
## 1. NEDEN YANLIŞ ÇALIŞIYOR — kök nedenler (önem sırasıyla)
`J/` = backend `src/main/java/com/fcareer/`.

| ID | Önem | Yer | Sorun → örnek → düzeltme |
|---|---|---|---|
| ENG-01 | Kritik | `J/catalog/repository/PlayerSearchRepository.java:171` (attr filtresi `r.<kolon>`), `:132` kulüp, `:137-141` lig, `:120` AcceleRATE, `:146-147` WF/SM, `:130-131` değer | **Kariyer aramasında filtreler, lig, kulüp ve değer base katalogdan geliyor, kariyerden değil.** Kariyerde Finishing'i 60 → 75 olan genç, katalog değeri 60 < 62 olduğu için Advanced Forward'dan eleniyor. Kariyerde kulüp değiştiren oyuncu eski katalog liginde/kulübünde aranıyor. Değerler kariyer yaşını yansıtmıyor. → Kariyer modunda **tek kariyer-durumu tablosu** (§3.1) |
| ENG-02 | Kritik | `J/career/service/CareerService.java:93` (bütçe varsayılanı 0), `RecommendationService.java:95`, `PlayerSearchRepository.java:130-131`, `ScoutingService.java:199-200` | **Bütçe girilmemiş kariyerde market boşalıyor.** `valueMax=0` → filtre `value IS NULL OR value<=0` → yalnız değeri olmayan (genelde silik/üretilmiş) oyuncular. Scouting'de bütçe ≤0 → oran `Double.MAX_VALUE` → her değerli aday UNREALISTIC, gizleniyor. → Boş/0 bütçe = "bilinmiyor"; market sorgusunda `includeUnvalued=false` |
| ENG-03 | Yüksek | `J/career/importer/CareerImportService.java:185-190`, `CareerService.java:199-206` | **Kiralıktaki oyuncular müsait sayılıyor.** `/fit/lineup`, `/fit/squad`, advisor, setup-check ve `squadCoreAverage`'a giriyor. Başka kulüpte kiralık oyuncu ilk 11'e seçilebiliyor, market bandını şişiriyor. Web yalnız rating gösteriminde filtreliyor → `squadPlayerIds` yalnız müsait oyuncular |
| ENG-04 | Yüksek | `PlayerMapper.java:62-64`, `PlayerSearchRepository.java:48,144-145,211`, `MlFeatureBuilder.java:71` (sabit `2026-10-05`) | **Oyun tarihi hiçbir yerde yok.** 4. sezonda (oyun tarihi ~2030) "≤21 genç" aslında 24-25; wonderkid listesi, değer yaş çarpanı, ML yaş özelliği yanlış → `career.game_date`, tüm yaşlar ondan |
| ENG-05 | Kritik | `role-weighting.json` (`positionBase` = EA OVR ağırlıkları), `RoleLibraryGenerator.java:73-87`; v2 kapalı: `FitEngineProperties.java:20` (`roleWeightsVersion="v1"`) | **RoleFit ≈ pozisyon OVR'si; focus neredeyse kozmetik.** v1, EA pozisyon ağırlıklarına weapon başına yalnız 0,07, focus başına 0,02-0,04 ekliyor. Ölçüm: v1 rol ağırlıkları ile EA pozisyon OVR ağırlıkları arası kosinüs 0,90-0,98. Inside Forward Balanced vs Attack kosinüs **0,992**. **Advanced Forward (Attack) ile Poacher (Attack) ağırlıkları birebir aynı** (kosinüs 1,000). Sentetik popülasyonda RoleFit ile ST OVR korelasyonu Poacher'da 0,89. IF testinde bitirici (Fin 88, Sta 62) ile motor (Fin 74, Sta 92) arası fark Balanced'da 0,1, Attack'ta 1,2 → **focus sıralamayı hiç değiştirmiyor.** Testlerin doğruladığı v2 kalibrasyonu üretimde kapalı |
| ENG-06 | Yüksek | `RoleFitService.java:225-255` (slot pozisyonuna göre kapı, −30), `:257-268` + `defaultGlobalFloor` (her saha rolünde Stamina 68 / Reactions 60 / Composure 60), `:198-205` AcceleRATE +4/−6 | **Sert uçurumlar skoru yönetiyor.** Regista (Vision 90, LP 92, Composure 88, DefAw 58) DLP Build-Up olarak **CDM'de 53,2, CM'de 83,2** (aynı rol!). Def Awareness 61 olan CB **28,1**, 62 olan **59,8** (tek puanla +31,7). 19 yaşında CB (DefAw 74, Str 80) Composure/Stamina tabanlarından 12,8 kaybediyor. AcceleRATE tek başına 10 puan oynatıyor; FC26 eşikleriyle *türetiliyor* ("FC27 için doğrulanmadı"), 3 tipe indirgenmiş, "Controlled Explosive" → EXPLOSIVE |
| ENG-07 | Yüksek | `FitPlayer.java` (ayak, zayıf ayak, beceri, boy, vücut, aşinalık yok), `roles.json` (rol başına tek öncelikli PlayStyle, `bonusPlaystyles` hepsinde null, 9 rolde hiç yok — tüm GK rolleri dahil) | **Oyuncu kalitesi sinyalleri eksik:** karşı ayak inside forward mantığı yok, Target Forward/CB için hava/boy terimi yok, WF/SM saklanıyor ama kullanılmıyor, Role+/++ aşinalığı (LE `role1..5`) hiç okunmuyor. PlayStyle etkisi en fazla +6 |
| ENG-08 | Yüksek | `RecommendationService.java:219-220` (`ORDER BY overall` 100), `TransferTargetService.java:44` (40), `ScoutingService.java:43` (40), sert weapon SQL filtresi `:208-210`, `reachableLeagues` `[]` döndürüyor `:192-194` | **Aday havuzu, uyum hesaplanmadan OVR'ye göre kesiliyor.** Simülasyon (3.000 sentetik ST, 40 satır sınırı): uyuma göre gerçek ilk 10'un **7-8'i hiç puanlanmıyor** (Poacher, AF, False 9). Sert weapon filtresi (örn. Defender Strength ≥70) oyuncuyu cezalandırmak yerine kalıcı eliyor. Kendi liginde 60+ oyuncu 100'den azsa lig filtresi sessizce kalkıyor, herkes "ulaşılabilir" |
| ENG-09 | Yüksek | `IntentFitService.java:52-54`, `FitTag.java:23`, `:29-35` | **IntentFit her etiketin yalnız ilk attribute'unu kullanıyor, oran 1'de tavanlı.** "Target man" yalnız Strength'e bakıyor: Heading 52 / Jumping 55 / Strength 82 olan forvet **100**, Heading 90 / Jumping 90 olan 97. Etiketsiz slot 100. `playstyleWeapons`, `accelerateHint`, `movementTags` hiç okunmuyor |
| ENG-10 | Yüksek | `SquadFitService.java:161-167`, `AdvisorService.java:391,198`, `TacticRecommenderService.java:115-120`, `FeasibilityService.java:156+` | **Taktik sıralaması etiketli presetlere yanlı.** Etiketli slot 0,7R+0,3I, etiketsiz R → +5..+9 puan avantaj (R72, I96 → 79,2 vs 72). Adaptif öneri (etiketsiz) ancak ~8 puan daha iyiyse kazanıyor. Fizibilite cezası yalnız 7 sabit preset id'sinde; 34 replikanın hiçbirinde yok |
| ENG-11 | Yüksek | `RuleMetric.java` (4 metrik), `tactic-rules.json` (6 kural), `RulesEngine.java:49-57` | **Kural motoru rol, focus ve kuruluma kör.** 6 kuralın 4'ü belirli etiket istiyor → yalnız rol/focus'la kurulan taktikte sadece 2 kural çalışabiliyor. "Yüksek hat + yavaş stoper" kuralı line height'a değil `cb_step_up` etiketine bakıyor: hat 90 + yavaş CB + etiket yok → sessiz |
| ENG-12 | Orta | `SetupModifier.java:16-18,46`, `SetupCheckService.java:27-29,85,91` vs web `wizard.ts:18-26` | **Hat bantları FC27 ve UI ile çelişiyor:** backend 35/65/85, FC27 ve UI 30/60/90. Yalnız CB'ler hat ayarı alıyor (bekler yok), "Balanced" build-up etkisiz. Setup-check **tüm kadroyu ana pozisyona göre** sayıyor, ilk 11'i değil → yedekteki 2 hızlı CB yavaş titulerleri YEŞİL gösteriyor |
| ENG-13 | Orta | `SquadFitService.java:106-109` | Boş slot ortalamaya girmiyor: 11 slotta 10 oyuncu 78 → squadFit **78,0** (doğrusu 70,9). Az oyuncu daha yüksek puan |
| ENG-14 | Orta | `PlayerMapper.java:39-41` vs `PlayerSearchRepository.java:45`; `CareerDeskService.java:126-141`; `DevelopmentService.java:39,220-222,252-254` | Gelişim ML potansiyeli kullanıyor, listeler kariyer potansiyelini. Elle OVR güncellemesi attribute'ları değiştirmiyor → RoleFit aynı kalıyor. Her attribute bağımsız headroom×1,5 kazanabiliyor |
| ENG-15 | Orta | `league.tier` hiç yazılmıyor, `ModeledValueFormula.java:14-15,62`, `MlFeatureBuilder.java:23-32` = `ml-service/app/features.py:25-32` | Her değer "bilinmeyen lig" ×0,7: OVR 85 / 25 yaş = 50,4 M € (tier 1'de 72 M), PL ile alt lig aynı. ML face stat'ları eşit ağırlıklı (PAC 50/50, doğrusu 45/55; DRI'da Reactions/Composure var; PHY 25×4, doğrusu 50/25/20/5). Kariyer değerleri bir kez set edilip hiç yaşlandırılmıyor |
| ENG-16 | Orta | `FitEngineProperties.java:101-104`, `RoleFitService.java:207-223` | Mevki dışı her durumda düz −15: RB'nin LB'de oynaması ile CB'nin CAM'de oynaması aynı ceza |
| ENG-17 | Orta | `TacticResolver.java:67-70`, `presets.json` | Preset/replikalarda rol yok (12 §1) |
| ENG-18 | Orta | `roles.json` centre_half weapon `Positioning` | "Positioning" = hücum pozisyon alma; savunma pozisyonu için kullanılmış (FC'de DefAwareness). `w_aerial`/`w_stay_high` Inside Forward Attack'a oy veriyor |
| ENG-19 | Orta | `TransferTargetService.java:80-84,137,169` | Aynı pozisyondaki ikinci rol düşüyor (farklı rollü iki CM → tek arama). Serbest/bant dışı her genç "KİRALIK" etiketli, fizibilite kontrolü yok |
| ENG-20 | Orta-düşük | `DevelopmentController.java:47`, `CareerService.clubGender` | Cinsiyet null → kadın/erkek karışıyor |
| ENG-21 | Orta-düşük | `messages.properties:111,122,129`, `SquadFitService.java:266-278` | **Metin sayılardan fazlasını iddia ediyor:** ~55 puanda "biçilmiş kaftan" rozeti; atak paterni metni etiket cümleleri, oyuncu yeteneği değil |
| ENG-22 | Düşük | `ReferenceDataSeeder.java:63-66` | Silinen rol/etiket/preset/kural DB'de kalıyor |
| ENG-23 | Orta (süreç) | `FitFixtures.java`, `FitEngineScenarioTest.java:166-177`, `RoleCalibrationScenarioTest` | **T1-T6 testleri üretimdeki v1 kütüphanesine hiç dokunmuyor.** Monotonluk, süreklilik, focus yönü, kariyer verisi, oyun tarihi, bütçe testi yok. Bütün hatalar varken testler geçiyor |

### 1.1 FC24/25 kalıntıları
`presets.json`'da `chanceCreation`, `defensiveStyle`, `offensiveWidth`, `playersInBox`; build-up değerleri "Direct", "Counter / Fast", "Balanced / Counter" (enum değil → BALANCED'a düşüyor); serbest metin `defensiveApproach` (43 kayıt, hiç parse edilmiyor); web sihirbazında "Genişlik" sorusu (`wizard.ts:4,112-122`, FC27'de genişlik ayarı yok); `career_team.build_up_play/defensive_depth` (V4, kullanılmıyor); FC26 AcceleRATE eşikleri (`AcceleRateDeriver.java`); 35/65/85 hat bantları; etiket tabanlı davranış dili (FC27 bunu rol + focus ile ifade ediyor).

---
## 2. TASARIM İLKELERİ (yeni motor)
1. **Tek doğruluk kaynağı:** kariyer modunda her sorgu yalnız kariyer durumunu okur. Base katalog yalnız kariyer dışında.
2. **FC27-native:** taktik = formasyon + build-up + defensive approach + hat + 11 (pozisyon, rol, focus). Etiketler motorun girdisi değil, yalnız açıklama/filtre etiketi.
3. **Ayrı çıktılar:** bugünkü uyum (`currentFit`), gelecek uyum (`futureFit(t)`), değer (`value`). Potansiyel bugünkü uyuma asla girmez.
4. **Uçurum yok:** her ceza yumuşak ve sınırlı. 1 attribute puanı skoru en fazla 1,5 oynatır.
5. **Açıklama = sayı:** her cümle hesaplanmış bir katkıdan üretilir. Sayısı olmayan iddia yok.
6. **Önce aday, sonra kesme yok:** OVR'ye göre kesme yasak. Havuz rol ön-skoruyla seçilir.
7. **Test edilebilirlik:** saf fonksiyon, versiyonlu (`engine_version`), altın veri seti + özellik testleri.

---
## 3. SIFIRDAN TASARIM
### 3.1 Veri modeli
```sql
career(id, user_id, game_version, user_team_id /* kariyer takım id'si, katalog kulübü değil */,
       game_date DATE NOT NULL, season INT, window TEXT CHECK (window IN ('SUMMER','WINTER','NONE')), window_ends_on DATE,
       transfer_budget_eur BIGINT NULL, wage_budget_eur BIGINT NULL,          -- NULL = bilinmiyor (0 değil!)
       engine_version TEXT NOT NULL, data_version BIGINT NOT NULL);          -- her import/düzenlemede artar → cache anahtarı
career_player(career_id, player_id, as_of DATE NOT NULL,
  team_id, loan_from_team_id, loan_until, contract_until, wage_eur, release_clause_eur,
  value_eur, value_source TEXT CHECK (value_source IN ('LE','MODELED','MANUAL')), squad_role, form, injury_until,
  available BOOLEAN GENERATED ALWAYS AS (loan_from_team_id IS NULL /* + sakatlık modellenince */) STORED,
  dob DATE, height_cm, weight_kg, body_type, preferred_foot CHAR(1), weak_foot SMALLINT, skill_moves SMALLINT,
  accelerate TEXT CHECK (accelerate IN ('EXPLOSIVE','MOSTLY_EXPLOSIVE','CONTROLLED_EXPLOSIVE','CONTROLLED','CONTROLLED_LENGTHY','MOSTLY_LENGTHY','LENGTHY')),
  accelerate_source TEXT CHECK (accelerate_source IN ('GAME','DERIVED')),
  ovr SMALLINT, pot SMALLINT, positions TEXT[] /* sıralı, sınırsız */, attrs SMALLINT[34],
  playstyles JSONB /* {"FINESSE_SHOT":2,"RAPID":1} 2=PS+ */,
  role_familiarity JSONB /* {"41":2} LE role1..5: code%100 = rol, ≥100 → ++ */,
  hidden_traits TEXT[],
  PRIMARY KEY (career_id, player_id));
career_player_history(snapshot_id, career_id, player_id, game_date, ...aynı kolonlar...);   -- gelişim eğrileri
user_tactic(id, user_id, career_id NULL, slot SMALLINT CHECK (slot BETWEEN 1 AND 5), name, formation,
            build_up, defensive_approach, line_height, slots JSONB /* 11×{slot,position,roleId,focus,playerId?} */, ea_code TEXT,
            source_replica_id TEXT NULL);
```
Kaynak: LE export v2 (07 §4) `context.gameDate`, `context.userTeamId`, `players` tüm kolonlar, `career_playercontract`, `playerloans`, `role1..5`, `trait2` gizli özellikler. LE'de olmayanlar (form, sakatlık, değer) için elle giriş ya da MODELED.

### 3.2 Puanlama modeli (saf fonksiyon)
```
currentFit(player, position, role, focus, ctx) ∈ [0,100]
  = 100 · clamp( Σ_i w[p,r,f]_i · u_i(attr_i)            -- (1)+(2) normalize edilmiş attribute faydası
               − Σ_e k_e · softplus((min_e − attr_e)/τ_e)  -- (3) temel attribute eksikliği, yumuşak
               + PS(player, role)                          -- (4) PlayStyle/PS+ bonusu, tavanlı
               + FAM(player, role)                         -- (5) Role+/++
               + PHYS(player, role)                        -- (6) hava + AcceleRATE
               + FOOT(player, position, role) )            -- (7) ayak/kanat
  · posDist[naturalPos][position]                          -- mevki dışı = basitleştirilmiş rol
```
1. **Attribute faydası:** `u_i = σ((a_i − p_i)/s_i)`; `p_i` = o pozisyonda listelenen oyuncularda attribute'un ~50. yüzdeliği, `s_i` ≈ 1 standart sapma (katalogdan hesaplanır, `seed/attr-norms.fc27.json`). 0-99 sıkışması kalkar, attribute'lar karşılaştırılabilir olur.
2. **Rol vektörleri** `w[(pozisyon, rol, focus)]`, toplam 1. EA pozisyon OVR regresyonu (`position-overall-weights.json`) kütlenin **en fazla %50**'si, kalanı rol deltası + focus deltası. CI değişmezleri: focus varyantları arası kosinüs ≤ 0,95; aynı pozisyonda farklı roller arası ≤ 0,90; iki rolün vektörü aynı olamaz. Face stat ağırlıkları (04 `faceStatWeights`) delta tasarımında referans.
3. **Temel attribute'lar:** rol başına 2-4 attribute `{min, target}`, yumuşak ceza `k·softplus((min−a)/τ)`. Slot bazlı kapı yok. Global taban yok; Stamina tabanı yalnız yüksek iş yüklü rollerde (Box-to-Box, Wingback, Attacking Wingback, Half Winger). Savunma rolünde hücum attribute'u (Positioning) temel olamaz (ENG-18).
4. **PlayStyle:** rol başına `{ps: ağırlık}` listesi, PS+ = 2×, toplam ≤ 8 puan. PS+ bir temel attribute'un `min`'ini düşürebilir (örn. Rapid+ → pace min −3).
5. **Aşinalık:** yok / Role+ / Role++ → `{0, +f1, +f2}` config (`verified:false`, ölçülene kadar f1=2, f2=4).
6. **Fiziksel:** `aerial = f(boy, Jumping, Heading, Strength)` rol ağırlıklı (Target Forward, CB, GK). AcceleRATE pace bileşenine sürekli çarpan (Explosive → kısa patlama rolleri, Lengthy → uzun koşu rolleri), ±3 puan. AcceleRATE oyundan geliyorsa `GAME`, türetilmişse etkisi yarıya.
7. **Ayak/kanat:** içe kat eden roller (Inside Forward, Wide Playmaker, Inverted Wingback, Half Winger) karşı ayakta bonus; orta açan roller (Winger, Wingback, Attacking Wingback) aynı ayakta bonus; `(5 − weak_foot)/4` ile ölçekle. Dribling rollerinde küçük skill moves terimi.
8. **Mevki uzaklığı** `posDist` (config): RB↔LB 0,97, CM↔CDM 0,93, CB→CDM 0,90, CAM↔ST 0,90, RW↔RM 0,97, diğerleri ≤ 0,75. Mevki dışında rol vektörü yerine pozisyonun genel vektörü kullanılır, aşinalık yok (FC27 "basitleştirilmiş rol").
9. **Katkı çıktısı:** her terim için `w_i·(u_i − ū_i)` + temel, PS, aşinalık, ayak, mevki etkileri. Bütün açıklama metni yalnız bunlardan üretilir.
10. **Ayrı:** `futureFit(t)` = attribute büyüme modeli (yaş eğrisi × potansiyel boşluğu × pozisyon ağırlıkları; pace erken gelişir, ~28 sonrası düşer) üstünden aynı fonksiyon; `value` = face stat özellikleri (04 ağırlıkları), lig tier'ı, kontrat, oyun tarihi.

### 3.3 Öneri hattı
1. **Bağlam:** `CareerContext(game_date, user_team, league, budget|null, wage_budget|null, available squad)`.
2. **Aday üretimi (SQL), yalnız sert kısıtlar:** cinsiyet, sahiplik, kullanıcı takımında değil, kullanıcıya kiralık değil, pozisyon uyumlu (`posDist ≥ 0,85`), oyun tarihine göre yaş, `value ≤ budget·α` VEYA serbest VEYA kontratı ≤ 6 ay (bütçe null ise değer filtresi yok), tier tablosundan lig ulaşılabilirliği. `ORDER BY role_prescore DESC LIMIT 2000`; `role_prescore = Σ w_i·attr_i` SQL'de nokta çarpımı (ağırlıklar bind param). **Hiçbir yerde `ORDER BY overall` yok.**
3. **Tam puanlama** (Java, vektörel) tüm havuzda.
4. **Fizibilite kovaları** (gizleme yok): ALINABİLİR / ZOR / HAYAL; değer/bütçe, takım tier farkı, kontrat, kulübündeki rolü.
5. **Çeşitlendirme:** kulüp, lig, yaş bandı, arketip üzerinde MMR; listede en az 1 serbest, 1 genç, 1 "hemen hazır".
6. **Açıklama:** en büyük 3 artı + 2 eksi katkı, temel attribute durumu, mevcut oyuncuya göre Δ, `futureFit(+1 sezon)`.

### 3.4 Taktik analizi (FC27-native)
Girdi: formasyon (x/y'ler `formations.json`'da), build-up, hat (bant: DEEP ≤30 / BALANCED ≤60 / HIGH ≤90 / AGGRESSIVE), 11 × (slot, rol, focus) + atanmış oyuncular. Her (rol, focus) için hareket profili (`seed/role-movement.fc27.json`): topla dx/dy, genişlik eğilimi, ileri koşu oranı, savunma görevi, pres yoğunluğu, stamina maliyeti. Hesaplanan takım metrikleri (FC27 oyun içi Tactical Summary'nin 6 boyutuyla aynı adlar):
| Metrik | Hesap |
|---|---|
| Hücum / Savunma | Σ hücum görevi vs Σ savunma görevi |
| Genişlik | Topla rol kaymalarından sonra ortalama \|x−50\|; sol/sağ kanat doluluğu |
| Uzunluk | Topla/topsuz y yayılımı, hat yüksekliğiyle düzeltilmiş |
| Dayanıklılık | Σ rol stamina maliyeti × bant pres çarpanı vs atanan oyuncuların Stamina'sı |
| Build-Up | GK+CB+CDM pas güvenliği (Short Passing, Composure, Ball Control, Vision) vs build-up tipi; Counter'da koşucuların pace + Positioning'i |
| Geri savunma (rest-defence) | Topun gerisinde görevi ≥θ olan oyuncu sayısı ve hat yüksekliğine göre hızları |
| Fırsat kanalları | Orta (orta kalitesi × hava hedefleri), içe kat (içe kat eden roller × Finishing × ayak), ara pas (Vision × koşucular), uzaktan şut |

Kurallar = bu metrikler + atanan oyuncular üzerinde predikat, her biri kanıt sayısıyla çıkar. Örnek: "Hat 82 (HIGH); CB hızları 64/61 < 70; rest-defence 2,1 oyuncu → kontra riski". Hungarian maliyeti: §3.2 `currentFit`, tüm slotlarda tek ölçek (etiket karışımı yok). Boş slot = 0. Ayna (sol/sağ) değerlendirmesi ayak verisiyle.

### 3.5 Test stratejisi
- **Altın veri seti:** Buğra ~60 FC27 oyuncusu seçer (arketip başına), her (pozisyon, rol, focus) için beklenen **sıralama**. Örn. Target Forward: uzun hava forveti > kısa hızlı forvet; IF-Attack: bitirici > motor; DLP: regista > yıkıcı. İkili sıra + Kendall τ ≥ 0,8.
- **Özellik testleri (jqwik):** pozitif ağırlıklı attribute +1 → skor düşmez; 1 puan değişim → skor ≤1,5 oynar; +Finishing IF-Attack'ı IF-Balanced'dan fazla artırır; kanat + ayak takası → aynı skor; potansiyel/OVR `currentFit`'i etkilemez; mevki dışı skor ≤ mevkideki skor.
- **Veri değişmezleri:** aynı rol vektörü yok; focus kosinüs sınırı; savunma temelinde hücum attribute'u yok; her preset/replika FC27 enum'larına parse olur (REP-02).
- **Kariyer testleri:** kariyer attribute'u filtrede katalogu ezer; yaş oyun tarihinden; kiralıktakiler hariç; null bütçe ≠ 0; kendi oyuncusu hedef olmaz; cinsiyet izolasyonu; elle OVR düzenlemesi ya yayılır ya işaretlenir.
- **Hat testleri:** "tüm tabloyu puanla" ile "SQL ön-skor top-K" arası recall@10 ≥ 0,95; hat bantları web ve backend'de aynı tablo (`ut-rules.tactics`).
- **Taktik metrik testleri:** iki Attacking Wingback'li 5'li savunma dar 4-4-2'den geniş; AGGRESSIVE + yavaş CB → rest-defence riski; etiketsiz, yalnız rollü taktik de bulgu üretir.

---
## 4. GEÇİŞ PLANI
| Adım | İş | Kabul |
|---|---|---|
| ENG-M1 | `career`/`career_player`/`user_tactic` şeması + LE export v2 import'u (07) + oyun tarihi | Kariyer sorgularında base katalog join'i yok (arch testi: SQL'de `player_rating r` kariyer modunda geçmez) |
| ENG-M2 | `attr-norms`, rol vektörleri v3, temel attribute'lar, PS, aşinalık, ayak, mevki uzaklığı seed'leri + CI değişmezleri | §3.5 veri değişmezleri yeşil |
| ENG-M3 | `FitEngineV3` saf fonksiyon + katkı çıktısı; v1/v2 kodu `engine_version` bayrağı arkasında, sonra silinir | Altın set τ ≥ 0,8, özellik testleri yeşil |
| ENG-M4 | Öneri hattı (§3.3) + fizibilite kovaları + çeşitlendirme | recall@10 ≥ 0,95; null bütçe testi |
| ENG-M5 | Taktik analizi (§3.4) + `role-movement` seed + kanıtlı kurallar; eski etiket kuralları kaldırılır | Etiketsiz taktik bulgu üretir |
| ENG-M6 | NLG: yalnız katkılardan metin; "biçilmiş kaftan" rozeti yalnız ≥85 | Her cümlenin sayısal kaynağı log'lanır |
| ENG-M7 | Replikalar (12) ve 5 taktik slotu bu motora bağlanır | REP-07 golden test |
| ENG-M8 | Web: yeni ekranlar (11 §6) yalnız v3 API'yi kullanır; eski uçlar `Deprecation` başlığıyla 1 sürüm yaşar | Eski uçlara çağrı yok |

Kural: v3 açılmadan önce ENG-01..04 (kariyer verisi, bütçe, kiralık, oyun tarihi) **mevcut motorda da** düzeltilir. Bunlar puanlama modelinden bağımsız veri hataları ve kullanıcıyı bugün yanlış yönlendiriyor.
