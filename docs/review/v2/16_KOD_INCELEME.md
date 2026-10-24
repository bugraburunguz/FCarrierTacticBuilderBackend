# 16 — Kod incelemesi (REVIEW, 2026-10-24)

Kapsam: backend `main` (604abd8) ve web `v3` (e22ee31). Bu dosyanın iki repoda da aynı kopyası var.
Yöntem: önce otomatik taramalar, sonra alan alan elle okuma (güvenlik, backend doğruluk/performans, web doğruluk/performans, sadeleştirme, test, doküman). Her bulgu düzeltilmeden önce kod okunarak ikinci kez doğrulandı. Doğrulanamayanlar **olası** diye işaretli ve düzeltilmedi.

## 1. Otomatik taramalar

| Tarama | Web | Backend |
|---|---|---|
| Test | 1531 geçti | 673 test, 0 hata, 5 atlandı (CI'da 6: LE dump'ı gitignore'da) |
| Tip / derleme | `tsc` temiz | derleme temiz; 19 deprecation uyarısı (Jackson `JsonNode.fields()`, Spring `ClientHttpRequestFactories`) |
| Lint | 0 hata, 35 uyarı (exhaustive-deps) | — |
| Bağımlılık açığı | `npm audit` 0 | OWASP dependency-check kurulu değil; güncellemeler: postgresql 42.7.7→42.7.14, lombok, jjwt 0.13, flyway 13, springdoc 3 |
| Ölü kod (knip) | `PitchView.tsx`, v2 `AboutPage.tsx`, `@radix-ui/react-tooltip`; "kullanılmayan script" listesi yanlış alarm | — |
| Bundle | ilk yük 558 KB (gzip 181 KB, 250 KB bütçenin altında) | — |
| Gitleaks (`--redact`) | 1 yanlış alarm (`scripts/deploy/smoke.mjs:97` sahte şifre), gerçek sızıntı yok | 2 yanlış alarm (`ErrorCodes.java:30-31` mesaj anahtarı), gerçek sızıntı yok |

## 2. Sayılar (önem × alan)

| Alan | Kritik | Yüksek | Orta | Düşük | Düzeltildi |
|---|---|---|---|---|---|
| Güvenlik (F) | 0 | 0 | 7 | 3 | 4 |
| Backend doğruluk (K) | 1 | 1 | 8 | 3 | 4 |
| Backend performans (P) | 0 | 3 | 4 | 2 | 2 |
| Web doğruluk/perf (Y, O, W) | 0 | 6 | 14 | 10 | 9 |
| Sadeleştirme (D, S) | 0 | 0 | 6 | 12 | 2 |
| Test (T) | 0 | 2 | 6 | 5 | 5 |
| Doküman (M) | 0 | 0 | 0 | 19 | 15 |

## 3. Bulgular

Durum: **düzeltildi (dal)** · **açık** · **karar** (kullanıcıya soru) · **6a** (FAZ 6a'ya devredildi) · **olası** (doğrulanamadı).

### Güvenlik (backend)

| ID | Alan | Dosya:satır | Önem | Tetikleyen senaryo | Öneri | Durum |
|---|---|---|---|---|---|---|
| F1 | sahiplik | `career/service/CareerService.java` `requirePlayerInCatalog` | Orta | B kullanıcısı A'nın kariyerinde üretilmiş oyuncu id'siyle `POST /careers/{B}/events` atar; 200 ve A'nın oyuncu adı döner, id'ler sıralı olduğu için taranabilir | Başka kariyere ait üretilmiş oyuncuyu `findForeignOwnedIds` ile 404'le reddet | düzeltildi (review/guvenlik) |
| F2 | spoiler | `replica/ReplicaApplyService.java:94` | Orta | SCOUT_RANGE kariyerde replika uygula; kesin potansiyel döner | `spoilerPolicy.maskAll` | düzeltildi (review/guvenlik) |
| F3 | ücretli içerik (D-01) | `tacticanalysis/TacticAnalysisController`, `ReplicaApplyService` | Orta | Ücretsiz `/fit/lineup` + `/tactics/analysis` → `/fit/squad`'ın 5 krediye verdiği bulgular bedava | Politika kararı: metni kırp ya da ücretlendir | karar |
| F4 | kaynak tüketimi | `CareerService.create:77`, `CareerPlayerWriter.ensureSeeded` | Orta | Ücretsiz kullanıcı döngüyle kariyer açıp her birinde arama yapar; her kariyer katalog kopyalar | Kullanıcı başına kariyer üst sınırı | karar |
| F5 | görsel vekili | `UpstreamImageCache.get`, `CardImageRepository.java:35` | Orta | Rastgele `/img/card/{id}` istekleri: her biri EA'ya giden istek + DB satırı | Önce id'nin katalogda olduğunu doğrula, `tryAcquire`, ıskaları saklama | açık |
| F6 | hız sınırı | `ClientIp.of`, `ProdConfigGuard` | Orta | Proxy arkasında `FCAREER_TRUST_PROXY=false` ise tüm anonimler tek kovada | ProdConfigGuard trust-proxy=true istesin | karar (olası: prod kurulumuna bağlı) |
| F7 | yetki | `auth/service/AuthService.java:90-92` | Orta | Saldırgan bootstrap e-postasıyla önce kayıt olur, doğrulamadan ADMIN alır | ADMIN yalnız doğrulamadan sonra | düzeltildi (review/guvenlik) |
| F8 | hız sınırı | `RateLimitFilter` | Düşük | Pahalı ücretsiz uçlar (apply, lineup, preview) genel 300/dk kovada | Analiz kovasına al; import'a ayrı kova | karar (değerler) |
| F9 | hata eşleme | `fitengine/service/FitService.java:140` | Düşük | `careerId` olmadan `/fit/lineup` → 500 | Açık 400 | düzeltildi (review/guvenlik) |
| F10 | yetki | `JwtService:63` | Düşük | Rolü düşürülen admin 15 dk erişimini korur | Rolü DB'den oku | açık |

### Backend doğruluk

| ID | Alan | Dosya:satır | Önem | Tetikleyen senaryo | Öneri | Durum |
|---|---|---|---|---|---|---|
| K1 | kariyer havuzu | `ingestion/base/CareerPlayerWriter.java:50-52`, `CareerDeskService.java:73` | Kritik | Konsol kariyerinde 3 oyuncunun OVR'si elle güncellenir; bu satırlar "live" olur, havuz 3 oyuncuya iner, öneriler ve kulüp gücü boş, oyuncular kulübünü kaybeder | `live` yalnız import satırı (`team_id NOT NULL`); anlık görüntü filtresi cpd varlığına göre | düzeltildi (review/dogruluk). Not: daha önce bozulmuş prod satırları yeni tanımı yalnız yeniden senkronda alır; tek seferlik backfill/betik prod verisi demek → karar |
| K2 | kredi | `billing/service/CreditGuard.java:18-36` | Yüksek | İade sırasında DB hatası asıl hatayı gizler ve kredi kaybolur; zaman aşımında tekrar deneme iki kez ücretler | İadeyi try/log ile sar (yapıldı); idempotency anahtarı API değişikliği | kısmen düzeltildi (review/dogruluk); idempotency: karar |
| K3 | eşzamanlılık | `usertactic/UserTacticRepository.java:60-63` | Orta | Çift tıklama / iki eşzamanlı `setMain` → sahte 409 | `pg_advisory_xact_lock` | düzeltildi (review/dogruluk) |
| K4 | para | `RosterEventRequest.feeEur` | Orta | SELL `feeEur=Long.MAX_VALUE` → `budget+fee` taşar | `@Max(10 Mrd)` | düzeltildi (review/dogruluk) |
| K5 | import | `career/importer/CareerImportService.java:51-56` | Orta | 64 MB ayrıştırma açık transaction içinde; iki eşzamanlı import PK çakışması (409) | Ayrıştırmayı tx dışına al, kariyer satırını kilitle | açık |
| K6 | önbellek | `advisor/service/StarterTacticService.java:62` | Orta | Elle OVR, `syncSquad` ya da geri alma sonrası eski başlangıç taktiği | Anahtara `data_version` | açık |
| K7 | yedek | `CareerDeskService.java:299-301` | Orta | Yedekte tek emekli oyuncu tüm geri yüklemeyi geri alır | Bilinmeyeni atla ve raporla | açık |
| K8 | önbellek | `CareerService.applyEvent/undoLastEvent` | Orta | BUY/SELL sonrası `CareerPlayerList`/`ClubStrength` 5 dk eski | `data_version` artır | açık |
| K9 | bağlantı havuzu | `CareerPlayerWriter.java:117-123` | Orta | `ensureSeeded` REQUIRES_NEW; 10 eşzamanlı ilk okuma havuzu kilitleyebilir | Kariyer oluşturma/import'ta tohumla | olası |
| K10 | hata eşleme | `GlobalExceptionAdvice:137` | Orta | İç `IllegalArgumentException` hataları 400 olarak gizlenir | Yalnız doğrulama hatalarını 400'e eşle | açık |
| K11 | posta | `OutboxVerificationMailer.java:20` | Düşük | `MAIL_HOST` boşken sınırsız liste | Sınır koy | açık |
| K12 | saat dilimi | `PlayerMapper.java:113`, `MlFeatureBuilder.java:77`, `ClockConfiguration.java:13` | Düşük | Gece yarısı Java ve SQL yaşları bir gün ayrışır; doküman UTC diyor, kod `systemDefaultZone` | `Clock` enjekte et, `:today` parametresi | karar (UTC) |
| K13 | doğrulama | `CareerRecommendationRequest.budgetEur` | Düşük | Negatif bütçe boş havuz | `@PositiveOrZero` | açık |

### Backend performans

| ID | Alan | Dosya:satır | Önem | Tetikleyen senaryo | Öneri | Durum |
|---|---|---|---|---|---|---|
| P1 | N+1 | `career/desk/SpoilerPolicy.java:22-27` | Yüksek | 400 satırlık diff → 800 sorgu | `ranged` istek başına bir kez | düzeltildi (review/performans) |
| P2 | bellek | `recommendation/pipeline/ClubStrengthService.java:25` | Yüksek | Sınırsız harita; ~1000 kariyerde ~150 MB | LRU 64 | düzeltildi (review/performans) |
| P3 | bellek | `recommendation/pipeline/CareerPlayerList.java:167-172` | Yüksek | Rol sıralı liste tüm havuzu (≤22k) `fitPlayers`'a sokar; istek başına 25-100 MB | Hafif JDBC geçişi, 1000'lik parçalar, single-flight | açık (ölçümlü refactor gerekir) |
| P4 | sorgu | `catalog/service/PlayerService.java:64` | Orta | İç çağıranlar kullanmadığı `count(*)`'ı öder | `withCount=false` varyantı | açık |
| P5 | N+1 | `CareerService.list:152-154` | Orta | Kariyer başına kulüp + tüm kadro | Tek toplu sorgu | açık |
| P6 | depolama | `CareerDeskProperties.snapshotRetention` | Orta | Her import ~22k satır kopyalar, saklama sınırsız | Varsayılan 20 | karar (veri silme) |
| P7 | indeks | `base_player.full_name ILIKE '%q%'` | Orta | Katalog aramasında seq scan | `pg_trgm` GIN (V46+) | açık |
| P8 | sorgu | `CareerPlayerRepository.extras:139-140` | Düşük | Her sayfada tüm havuzda `rank() OVER` | Takımlarla sınırla | açık |
| P9 | N+1 | `KnowledgeBaseRepository.sbcSets:144-151`, `TransferTargetService.forSlot:94-97` | Düşük | Set/slot başına ek sorgular | Toplu sorgu | açık |

### Web doğruluk ve performans

| ID | Alan | Dosya:satır | Önem | Tetikleyen senaryo | Öneri | Durum |
|---|---|---|---|---|---|---|
| Y1 | istemci | `src/api/client.ts:144-150` | Yüksek | Kariyer modunda `/players/99999999` → 404 → seçili kariyer kalıcı silinir | Yalnız kariyer kullanıcının listesinde yoksa temizle | düzeltildi (review/dogruluk) |
| Y2 | önbellek | `features/import/CareerImportPage.tsx:85-93` | Yüksek | Import sonrası bütçe, oyun tarihi, anlık görüntüler, uyum eski | Merkezi `invalidateCareerScope` | düzeltildi (review/dogruluk) |
| Y3 | önbellek | `features/careerSquad/useCareerSquadData.ts:12-15` | Yüksek | BUY sonrası bütçe, `inSquad`, `slot-fit` eski | Aynı kapsam fonksiyonu | düzeltildi (review/dogruluk) |
| Y4 | dayanıklılık | `App.tsx:131-171` | Yüksek | Yayın sonrası eski chunk 404 ya da render hatası → beyaz ekran | Rota başına `RouteErrorBoundary` | düzeltildi (review/dogruluk-2) |
| Y5 | hata durumu | `DashboardPage.tsx:107-119`, `CareerSquadPage.tsx:59-72`, `TransferCenterPage.tsx:105-115` | Yüksek | Ağ hatası "Henüz kariyerin yok" / "Kadro boş" olarak görünür | `ErrorState` | düzeltildi (review/dogruluk-2) — Lab, Gelişim, Ana sayfa, CompareTab açık |
| Y6 | durum kaybı | `design/components/primitives.tsx:308-313` | Yüksek | Ücretli öneri sonucu sekme değişince kaybolur, tekrar ödeme | `TabPanel keepMounted` | düzeltildi (review/dogruluk-2) |
| O1 | para girişi | `components/MoneyInput.tsx:43-52` | Orta | Geçersiz tutarla blur pazarlık tavanını siler | Geçersizken commit yok | düzeltildi (review/dogruluk) |
| O2 | para girişi | `components/TransferDialog.tsx:33,63`, `squad/kadro/MovesTab.tsx:65,73` | Orta | "15" yazan 15 € satar; "2,5" → 0 € | `MoneyInput` + geçerlilik kapısı | düzeltildi (review/dogruluk) |
| O3 | ayarlar | `dashboard/CareerSettings.tsx:21-37` | Orta | Bütçe temizlenir, "Kaydedildi." yazar ama backend yok sayar | Bütçeyi "bilinmiyor" yapmanın açık yolu | açık |
| O4 | silme | `components/DeleteCareerButton.tsx:23-26` | Orta | Silinen id ile sorgular 404 → Y1; seçim boş kalır | Sonraki kariyeri seç, `removeCareerScope` | düzeltildi (review/dogruluk) |
| O5 | import | `CareerImportPage.tsx:71-73,121` | Orta | A'nın sonucu B seçilince kalır, B A'nın takımıyla kurulur | `key={careerId}` | açık |
| O6 | oluşturma | `career/NewCareerPage.tsx:34-45` | Orta | PATCH hatasında tekrar deneme ikinci kariyer açar | gameDate'i create isteğine ekle | açık |
| O7 | ana taktik | `career/MakeMainDialog.tsx:15-29` | Orta | Slotlar yüklenirken dolu slotun üstüne onaysız yazılır | `isSuccess` olmadan kaydet pasif | açık |
| O8 | satış planı | `transfer/SellPlanTab.tsx:62-77` | Orta | Hata gösterilmez, hızlı tıklama sırayı bozar | Satır bazında pending + hata | açık |
| O9 | oyun tarihi | `DashboardPage.tsx:98`, `CareerSquadPage.tsx:77`, `TransferCenterPage.tsx:122` | Orta | Sayfalar farklı tarih kaynağı kullanır | Tek `useGameDate` | açık |
| O10 | karşılaştırma | `compare/CompareV3Page.tsx:186-195` | Orta | Oyuncu değişince eski uyum sırası kalır | Girdi değişince sıfırla | açık |
| O11 | performans | `catalog/CatalogShell.tsx:45-52` vd. | Orta | Her tuşta istek ve iskelet | 250 ms debounce, `keepPreviousData` | açık |
| O12 | performans | `WonderkidsV3Page.tsx:87-89`, `squad/pick/CareerPickList.tsx:34-41` | Orta | Wonderkids çift istek; slot paneli başına ≤40 `fitRole` | `enabled` kapısı; toplu uç | açık |
| O13 | açılış | `main.tsx:21-23` | Orta | Render Dexie hidrasyonunu bekler; IDB "blocked"ta beyaz ekran | Dexie ve rules'u UT rotalarına lazy al | açık |
| O14 | Lab | `TacticsLabPage.tsx:57`, `useCareerSquadBuilder.tsx:25` | Orta | Store boşken taslak çubuğu ile kurucu farklı kariyer gösterir (kısa süreli) | id'yi prop geçir | açık |
| W1 | URL | `CareerPlayerPage.tsx:34-49`, `useWonderkids.ts:45,52` | Düşük | `/players/abc` sonsuz iskelet; `butce=abc` NaN | `Number.isFinite` kontrolü | açık |
| W2 | tarih | `SellPlanTab.tsx:34`, `CareerPlayerTabs.tsx:11` | Düşük | Tutarsız tarih ayrıştırma (bugün zararsız) | Ortak yardımcı | açık |
| W3 | depolama | `state/migrations.ts:116` | Düşük | Depolaması engelli tarayıcıda modül yüklenirken atabilir | try içine al | açık |
| W4 | sorgu anahtarı | `useCompare.ts:29`, `RolePicker.tsx:29` | Düşük | Aynı uca farklı anahtar; `['tags']` iki farklı veriyi vurur | `['behavior-tags', group]` | açık |
| W5 | Lab | `TacticsLabPage.tsx:71`, `DraftBar.tsx:52` | Düşük | Aynı `?slot=` ikinci kez yok sayılır; yeniden adlandırma taslağı kirli yapar | — | açık |
| W6 | ana taktik | `career/activeTactic.ts:94-105` | Düşük | setMain hatasında slot yazılmış ama tazelenmemiş | Tek uç ya da tazeleme | açık |
| W7 | başlangıç taktiği | `StarterTacticPanel.tsx:61-66` | Düşük | 11'den az oyuncuda genel hata metni | 422'ye özel metin | açık |
| W8 | erişilebilirlik | `ShortlistButton.tsx:19-22`, `CompareButton.tsx:13`, `Pitch.tsx:274-279`, `NewCareerPage.tsx:59-69` | Düşük | Aynı adlı düzinelerce düğme, `aria-pressed` eksik/yanlış, combobox klavyesiz | Adlara oyuncu adı, doğru roller | açık |
| W10 | yerleşim | Kadro › Liste › Kartlar (`careerSquad` kart bölümleri) | Düşük | 375 px'te kart bölümleri 388 px'e taşar (yatay kaydırma); REVIEW ekran çekiminde bulundu, diyalogla ilgisiz | `min-w-0` / sarma | açık |
| W9 | v2 | `useAutoLineup.ts:16-19` | Düşük | İptalsiz async patch (yalnız v2) | v2 ile birlikte gider | açık |

### Sadeleştirme

| ID | Alan | Dosya:satır | Önem | Tetikleyen senaryo | Öneri | Durum |
|---|---|---|---|---|---|---|
| D1 | kural | `lib/fitLabels.ts:10` ↔ `dashboard/dashboardRules.ts:14,57` | Orta | Transfer "İhtiyaçlar" AT_LEVEL slotları içerir, "3 iş" içermez | Tek `weakRows` | 6a (G) |
| D3 | kopya | `deskRules.ts:213`, `dashboardRules.ts:147,178`, `careerSquadRules.ts:101` | Orta | Sözleşme bitiyor koşulu 5 yerde | `lib/contract.ts` `expiringMonths` | açık |
| D4 | anlam | `PlayerSearchRepository.java:137,170` ↔ `lib/contract.ts:17` | Düşük | Takvim ayı vs 30 gün; sınırda rozet ile filtre ayrışır | Birini seç ya da belgele | açık |
| D5 | etiket | `useSlotFits.ts:56`, `LabBuilder.tsx:83,93`, `ApplyTab.tsx:103` | Orta | "Kadro uyumu" iki farklı metriği adlandırır | Ayrı etiket, tek hesap | açık |
| D6/D7 | kopya | `useLineupFits.ts:28-42`, `labSources.ts:13,26` | Düşük | Aynı eşleme iki yerde | Saf yardımcıya çıkar | açık |
| D8 | kural | mevki grupları / derinlik | Orta | Birden çok tanım | Tek tanım | 6a (B) |
| D9 | tutarsızlık | `squad/kadro/DepthTab.tsx:84-119` | Orta | Yaş histogramı kiralıkları sayar, diğerleri saymaz | Tek kaynak | 6a/6b |
| D10/D11 | kopya | tarih ve para biçimleyicileri | Düşük | Kopyala-yapıştır yardımcılar | `lib/format` | açık |
| D12-D14 | kopya | Timeline, Settings (v2), BE SQL sahiplik kontrolleri | Düşük | — | v2 ile birlikte / ortak yardımcı | açık |
| S1 | ölü kod | `components/PitchView.tsx`, `package.json` `@radix-ui/react-tooltip`, `endpoints.ts` (`kbPromos`, `deleteUserTactic`, `seasons`, `putSeason`), `formatScore`, `sbcSize`, `fitRoleName` | Düşük | Hiç kullanılmıyor | Sil | düzeltildi (review/sadelestirme) |
| S2 | ölü config | `pruneBelow`, `minPasswordLength`, `IngestionProperties.runOnStartup` | Düşük | Bağlı ama okunmuyor | Sil | açık |
| S3 | eski öneri kümesi | `AdvisorService.java:92-307` (`/advisor/squad`) ve eski öneri uçları (~900-1100 satır) | Orta | Yalnız kullanılmayan uçlardan erişiliyor | Kaldır (API değişikliği) | karar |
| S4 | script | web `scripts/` (8 hiç referanssız, ~25 tek seferlik) | Düşük | — | Arşivle | açık |
| S5 | uzun birim | `BaseCatalogImporter.java` (872), `UtCaptureService.java` (720), `Pitch.tsx` | Düşük | — | Bölme önerileri (davranış korur) | açık |

### Testler

| ID | Alan | Dosya:satır | Önem | Tetikleyen senaryo | Öneri | Durum |
|---|---|---|---|---|---|---|
| T1 | sahiplik | ~35 kariyer kapsamlı uç | Yüksek | Yabancı → 404 testi yok | Parametreli matris testi | düzeltildi (review/testler, 37 uç) |
| T2 | eşzamanlılık | `UserTacticRepository` | Yüksek | Eşzamanlı setMain testi yok | 16 paralel çağrı testi | düzeltildi (review/dogruluk) |
| T3 | zayıf iddia | `TacticAnalysisIntegrationTest.java:98-103`, `CareerImportIntegrationTest.java:77-78` | Orta | `success==false` 400/500'ü de geçirir | Hata kodu iddiası | düzeltildi (review/testler) |
| T4 | uyuyan test | `squadCore.test.tsx:112`, `useUtBinding.test.tsx:80`, `CareerSelect.test.tsx:26` | Orta | Sabit bekleme + negatif iddia yavaş CI'da yanlış geçer | Pozitif sinyal + mikro görev boşaltma | düzeltildi (review/testler); v2 `UtSquadPage.test` ve dashboard döngü testi açık |
| T5 | CI | `RoleSignalRegressionTest.java:43` | Orta | Dump gitignore'da, CI'da sessizce atlanır | Kırpılmış fikstür ya da CI'da hata | açık |
| T6 | paylaşılan durum | `BaseItemSyncIntegrationTest.java:54-56`, `findAll().get(0)` kalıbı | Orta | Test sırası "ilk kulübü" değiştirir | `ORDER BY id`, geri yükleme | açık (matris testinde açık seçim yapıldı) |
| T7 | zayıf iddia | `RoleAvailabilityTest.java:86` | Orta | Adı grubu doğruladığını söyler, doğrulamaz | Grup iddiası | açık |
| T8 | kredi | `CreditGuard` | Orta | İade yolu testsiz | Birim testi | düzeltildi (review/dogruluk) |
| T9 | web eksik | `tacticsLabPage.test.tsx:169`, `importPage.test.tsx` | Orta | Ana taktik ve import hata yolları testsiz | Hata testleri | açık |
| T10 | saat | `CareerGameDateIntegrationTest.java:59`, `ApiFlowIntegrationTest.java:100-126` | Düşük | 2031'de patlar; 21:00 UTC'de kredi sıfırlanır | Sabit `Clock` | açık |
| T11 | web saat | `sbcListPage.test.tsx:35`, `sbcSolvePage.test.tsx:44` | Düşük | Sınırda yuvarlama | Sistem zamanını sabitle | açık |
| T12 | CI | `backend-test.yml` `pytest \|\| [ $? -eq 5 ]` | Düşük | Test toplanmazsa geçer | Kaldır | açık |
| T13 | iddia | `GoldenSetTest.java:112`, `UserTacticIntegrationTest.java:102`, `LegacyRecommendationDeprecationIntegrationTest.java:62` | Düşük | iddiasız / etkisiz iddia | — | açık |

### Doküman sapmaları

| ID | Dosya | Sapma | Durum |
|---|---|---|---|
| M1 | BE `CLAUDE.md` | paket listesi eksik (`replica`, `tacticanalysis`, `usertactic`) | düzeltildi (review/dokuman) |
| M2 | BE `CLAUDE.md` | opt-in bayrak listesi eksik; CI'da 6 atlama | düzeltildi |
| M3 | BE `CLAUDE.md` | "fit katsayıları `fcareer.fit.*`'de" — bir kısmı `SetupModifier`/`SetupCheckService`'te sabit | karar |
| M4 | BE `CLAUDE.md`, `fcareer-standard.md` | `requireOwned` controller'da değil serviste | düzeltildi |
| M5 | BE `CLAUDE.md` | "her ücretli uçta CreditGuard" → 5 uç listelendi | düzeltildi |
| M6 | `fcareer-standard.md:14` | `Clock.systemUTC()` iddiası, kod `systemDefaultZone` | karar (K12) |
| M7 | `fcareer-standard.md:11` | "serviste ham SQL yok" — 12 serviste JdbcTemplate | karar |
| M8 | `08_DECISIONS_LOG.md` D-22 | `patches/` yolu yok | düzeltildi (iki repo) |
| M9 | `08_DECISIONS_LOG.md` D-02 | stub ödeme profil değil bayrakla | düzeltildi (iki repo) |
| M10 | `RAILWAY.md:156` | 39 migration → 45 | düzeltildi |
| M11 | `RAILWAY.md:156` | ProdConfigGuard'ın gerçekte denetlediği değişkenler | düzeltildi |
| M12 | web `CLAUDE.md:4` | 02 yerine 11 tek kaynak | düzeltildi |
| M13 | web `CLAUDE.md:7` | token örnekleri tanımsız | düzeltildi |
| M14 | web `CLAUDE.md:14` | GitHub Pages hâlâ var mı? | karar |
| M15 | web `CLAUDE.md:15` | "Playwright görsel snapshot" yok; elle çekim scriptleri var | düzeltildi |
| M16 | `11_...:160` | uPlot/Recharts yok, el yazımı SVG | düzeltildi |
| M17 | `11_...:173-220` | CI kapıları (stylelint, Ladle, axe, bundle) CI'da değil | karar |
| M18 | `11_...:75` | font listesi `tokens.css` ile ufak fark | açık |
| M19 | `15_...:898` | `useSlotFits` modülünün yardımcıları v3'te kullanılıyor | düzeltildi |

## 4. Dallar ve PR'lar

| Repo | Dal | Bulgular | Davranış değişikliği | API değişikliği |
|---|---|---|---|---|
| backend | review/guvenlik | F1, F2, F7, F9 | evet (yabancı oyuncu 404, maskeleme, admin doğrulama sonrası, 500→400) | hayır (yalnız hata durumları) |
| backend | review/dogruluk | K1, K2 (kısmi), K3, K4, T2, T8 | evet | hayır (fee üst sınırı 400) |
| backend | review/performans | P1, P2 | hayır | hayır |
| backend | review/testler | T1, T3 | hayır | hayır |
| backend | review/dokuman | M1-M2, M4-M5, M8-M11 + bu dosya | hayır | hayır |
| web | review/dogruluk | Y1, Y2, Y3, O1, O2, O4 | evet | hayır |
| web | review/dogruluk-2 | Y4, Y5, Y6 | evet (hata ekranları) | hayır |
| web | review/testler | T4 | hayır | hayır |
| web | review/sadelestirme | S1 | hayır | hayır |
| web | review/dokuman | M8-M9, M12-M13, M15-M16, M19 + bu dosya | hayır | hayır |

## 5. Test sayıları

| | Önce | Sonra (dal bazında) |
|---|---|---|
| Web | 1531 | dogruluk 1538 · dogruluk-2 1540 · testler 1531 · sadelestirme 1531 |
| Backend | 673 (5 atlama) | guvenlik 677 · dogruluk 676 · performans 676 · testler 674 (CI benzeri ağaçta 6 atlama) |

PR öncesi bağımsız inceleme (fc-reviewer) iki Orta bulgu verdi, ikisi de aynı dallarda düzeltildi: kariyer önbellek kapsamı kimliği her konumda arıyordu (ilgisiz önbellekleri siliyordu), ve verisi olan sorgunun başarısız yenilemesi sayfayı tam hata ekranına düşürüyordu.

Not: bazı kırmızı adımlar yalnız derleme/eksik modül hatası olarak görüldü (performans testleri, `careerQueries`); bu dürüstçe kayıtlı.

## 6. Açık sorular (kullanıcı kararı)

1. v2'nin kaldırılması (D2, D12-D13, W9 ve v2'ye bağlı modüller onunla gider).
2. `v3`→`main` birleşmesi; Railway ve GitHub Pages hangi daldan yayınlar (M14).
3. Hız sınırı değerleri (F8) ve prod'da trust-proxy zorunluluğu (F6).
4. D-01: `/tactics/analysis` ve replika uygulamanın ücretsiz bulguları (F3).
5. Kullanıcı başına kariyer üst sınırı (F4).
6. CreditGuard idempotency anahtarı (K2, API değişikliği).
7. Saat: UTC mi, sistem dilimi mi (K12, M6); "serviste ham SQL yok" kuralı (M7); fit katsayıları iddiası (M3); CI kapıları (M17).
8. Eski öneri kümesi ve `/advisor/squad` kaldırma (S3, API değişikliği).
9. `CareerPlayerList` ölçümlü refactor (P3) ve anlık görüntü saklama sınırı (P6, veri silme).
10. K1 sonrası prod'daki eski "live" satırları için tek seferlik yeniden senkron (backfill bayrağı bir açılışlık ya da dry-run'lı betik); ayrıca prod'da `role='ADMIN' AND NOT email_verified` salt-okunur kontrolü (F7 öncesi kayıt olasılığı).
