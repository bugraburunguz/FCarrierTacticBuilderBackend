# 01 · MEVCUT DURUM DENETİMİ (audit)
Kaynak: backend + web zip (main), statik okuma. Yollar:
- BE = `FCarrierTacticBuilderBackend/src/main/java/com/fcareer/`
- WEB = `FCarrierTacticBuilderWeb/src/`
Tüm REST uçları `/api/v1` altındadır.

---

## 1. YAPILMIŞ OLANLAR (envanter)

### 1.1 Backend modülleri
| Paket | Ne yapıyor | Uçlar | Durum |
|---|---|---|---|
| `fitengine` | RoleFit / IntentFit / SquadFit (Hungarian, dikdörtgen), RulesEngine, NLG, setup-check, sol/sağ aynalama | `POST /fit/role` · `/players/compare` · `/fit/squad` (kredi) · `/fit/lineup` · `/fit/setup-check` | ✅ Spec §2–§5 formülleriyle birebir; T1–T6 testleri var (`src/test/.../FitEngineScenarioTest.java`) |
| `roles` | 30 temel rol → focus başına genişletme (`RoleLibraryGenerator`), tag/preset seed | `GET /roles` · `/behavior-tags` | ✅ |
| `tactics` | Formasyon (29 adet, `seed/formations.json`), preset/replika, taktik çözümleyici, `tactic_rule` tablosu | `GET /formations` · `/presets[/{id}]` · `POST /tactics/resolve` | ✅ |
| `tacticcode` | EA taktik paylaşım kodu encode/decode | `POST /tactics/export-code` · `/import-code` | ✅ (rakiplerde de var, iyi) |
| `catalog` | Oyuncu arama (dinamik SQL, bind-param'lı), detay, benzer oyuncu, lookup | `GET /players` · `/players/{id}` · `/{id}/roles` · `/{id}/similar` · `/clubs` · `/leagues` · `/nationalities` | ✅ |
| `ingestion` | EA ratings sitesi `_next/data` JSON okuma (`EaSiteProvider`), mock, Live Editor base import, CSV enrichment, açık dataset | `POST /admin/ingestion/*` (X-Admin-Key) | ⚠️ çalışıyor, kırılgan (bkz. BUG-09) |
| `mlclient` + `ml-service` | FastAPI potential/value, `MODELED` valuation yazımı | `POST /admin/ml/valuations` · ML: `/ml/predict` `/ml/reload` `/health` | ⚠️ ML uçları auth'suz; test sentetik veriyle |
| `career` | Kariyer, kadro, roster event, Live Editor log/CSV import, takım içgörüleri | `/careers/**` | ⚠️ "event-sourced" değil (state doğrudan mutasyon), import yıkıcı |
| `recommendation` | Slot önerisi, transfer hedefleri, scouting | `/recommend/*` · `/scouting/{careerId}/search` | ⚠️ kredi bypass (SEC-02) |
| `advisor` | Kadro/taktik tavsiyesi, mevki tavsiyesi, fizibilite | `/advisor/*` | ✅ |
| `development` | Gelişim planı, wonderkids | `POST /players/{id}/development` · `GET /wonderkids` | ⚠️ IDOR (SEC-03) |
| `auth` | JWT access+refresh (rotation, GETDEL), bcrypt, Redis refresh store, rate limit | `/auth/*` · `/me` | ⚠️ (SEC-06) |
| `billing` | Kredi (pessimistic lock + hata halinde iade), entitlement, stub ödeme | `POST /billing/subscribe` | ❌ herkes PREMIUM alır (SEC-01) |
| `ut` | Extension capture işleme, streamlined SBC knapsack çözücü, SBC oyları, UT meta/tier, kart chem-style, kadro değerlendirme | `/ut/*` | ⚠️ yarım (bkz. §3) |
| `versioning` | Dataset versiyonları + oyuncu diff | `GET /versions` · `/versions/latest/changes` | ✅ |
| `gameconfig` | EA key-attributes seed | `GET /config/key-attributes` | ✅ |

### 1.2 Frontend ekranları
| Route | Amaç | Veri | Kalite |
|---|---|---|---|
| `/` | Hero + 4 özellik kartı | statik | ❌ placeholder; UT modunda da kariyer içeriği |
| `/players`, `/players/:id` | Arama, radar, roller, chem-style (UT), gelişim (kariyer) | API | ✅ |
| `/compare`, `/wonderkids` | Karşılaştırma, genç yetenek | API + localStorage | ✅ |
| `/tactics/wizard` | 5 soru → preset | API | ✅ |
| `/squad/builder` | Saha + slot modal (oyuncu/rol/davranış/öneri) + EA kodu + AutoFit + FitResults | API | ✅ ama ağır: kadro başına 11 ayrı `/fit/role` çağrısı (`pages/SquadBuilderPage.tsx:78-88`) |
| `/squad`, `/scouting`, `/recommend`, `/teams` | Kariyer CRUD, transfer, scouting, öneri | API (auth) | ✅ |
| `/career/import` | Live Editor Lua adımları + log/CSV upload | API | ⚠️ README "kapalı" diyor, sayfa açık; Lua başlığı ile adımlar çelişiyor |
| `/profile` | Plan/kredi, "PREMIUM'u etkinleştir (test)" butonu | API | ❌ herkese PREMIUM |
| `/ut/squad` | UT kadro kurucu + kimya + meta + taktik kodu | localStorage + capture | ⚠️ squad rating yok, menajer yok, yedek yok, kart versiyonu yok |
| `/ut/club` | Kulüp kartları grid/tablo | capture | ✅ (capture varsa) |
| `/ut/meta` | Meta formasyon + tier list | seed | ⚠️ sadece base kart |
| `/ut/sbc`, `/ut/sbc/:setId/:challengeId` | SBC listesi + streamlined çözücü + geleneksel beam çözücü | capture + catalog | ⚠️ extension yoksa liste boş; çözücüde doğruluk hataları (BUG-01..03) |
| `/ut/objectives` | Objective planlayıcı | localStorage + capture | ⚠️ |
| `/ut/import` | Extension JSON yükle | API → localStorage | ⚠️ quota sessizce dolar (BUG-05) |

### 1.3 Araçlar
- `tools/live-editor/*.lua` — FC27 Live Editor ile base katalog ve kariyer export (çalışan, değerli bir moat).
- `tools/tactic-code/*` — EA taktik kodu tersine mühendislik verisi.
- `ml-service/tools/*` — FC27 dump'tan eğitim seti üretimi.

---

## 2. SPEC'E GÖRE BOŞLUKLAR
| Spec maddesi | Durum | Not |
|---|---|---|
| BUILD_SPEC §4 "drop-api" | Sapma | Kod EA ratings sitesinin Next.js `buildId`'sini regex'le okuyor (`ingestion/provider/EaSiteProvider.java:33,93-99`). Resmi API değil; site güncellemesinde kırılır. **Spec'teki "Scrape yok" ilkesi fiilen esnetilmiş** → 03 §2'de yazılı veri politikası öner. |
| §6 `user_save_snapshot` / `user_player_state` / `player_valuation(IMPORT)` | Kısmi | Import `career_player_data`, `career_team`, `career_player_rating`'e yazıyor, her import öncekini siliyor (`career/importer/CareerImportService.java:104`). Versiyonlu snapshot yok; `user_save_snapshot` hiç yazılmıyor. |
| §6 `att_wr`, `def_wr`, `body_type` | Eksik | Kolon var, hiçbir şey yazmıyor. Sim oyunu (06) için gerekli. |
| §9 `@PreAuthorize` | Sapma | Path-bazlı kurallar (`common/configuration/SecurityConfig.java:35-45`). |
| §14 Testcontainers (PG+Redis) | Sapma | Embedded PG + in-memory refresh store; **Redis hiç test edilmiyor**. |
| §17 Money `BigDecimal` | Sapma | `long` euro. |
| UT modülü | Spec yok | `UtCaptureService` Javadoc'u `ut_capture_mapping.md`'yi referans veriyor, dosya yok. → 03/04 bu boşluğu dolduruyor. |
| `CLAUDE.md` | **Yanlış proje** | Backend kökündeki `CLAUDE.md` "Azerlottery Backend / com.misli / Kafka / Feign / PHP→Java" anlatıyor; `docs/backend-standard.md` da Azerlottery başlıklı. BUILD_SPEC §17 bunların taşınmayacağını söylüyor ama ajan her oturumda bunu okuyor. → `07_CLAUDE_MD_FCAREER.md` ile değiştir. |

---

## 3. UT MODÜLÜ — DURUM ÖZETİ
- **Tüm UT verisi kullanıcının extension yakalamasına bağlı.** `POST /ut/capture` gelen JSON'u işler, sonucu **geri döner; hiçbir şeyi kalıcılaştırmaz** (yalnızca league/nation EA-id öğrenme + SBC oyları). Kartlar/SBC'ler/objective'ler tarayıcı localStorage'ında yaşar.
- **Global UT kataloğu yok:** özel kart (TOTW/promo/Icon/Hero/Evo) tablosu, SBC seti/challenge tablosu, objective, evolution, fiyat zaman serisi, promo tablosu yok. "Kart veritabanı" UT modunda aslında kariyer base-kart DB'si.
- **SBC çözücü (backend)** yalnızca "streamlined / item-score" tipi: (rating, rarity, adet, untradeable, fiyat) havuzundan hedef puana en ucuz kombinasyon — 0/1 knapsack, 60M state üstünde greedy fallback (`ut/service/SbcSolverService.java`). Doğru ve iyi; ama public, auth'suz, istek başına ~60M işlem.
- **Geleneksel SBC çözücü (frontend)** beam search, ana thread'de, kimya tahmini lineer (`lib/sbcTraditional.ts`). Hatalar için BUG-01..03.
- **Kimya** sadece frontend'de (`lib/chemistry.ts`); backend hiç hesaplamıyor. Kural değerleri FC27 için doğru (08 D-21).
- **Squad rating** üç farklı yerde üç farklı şekilde (BUG-01).

---

## 4. DOĞRULANMIŞ HATALAR (dosya:satır)

### 4.1 Güvenlik / para (P0)
| ID | Sorun | Yer | Düzeltme | Kabul kriteri |
|---|---|---|---|---|
| **SEC-01** | `StubPaymentProvider` koşulsuz `@Component`; `subscribe` ödeme doğrulamadan PREMIUM veriyor, tekrar abonelik kontrolü yok. UI'da "PREMIUM'u etkinleştir (test)" butonu herkese açık. | BE `billing/provider/StubPaymentProvider.java:7`, `billing/service/BillingService.java:36-53`; WEB `pages/ProfilePage.tsx:40-44` | `@Profile("dev")` veya `@ConditionalOnProperty(fcareer.billing.stub-enabled=true)`; prod'da gerçek provider yoksa `subscribe` 503 dönsün (prod sağlayıcı: iyzico, 08 D-02); aktif abonelik varken 409. Butonu `import.meta.env.DEV` arkasına al. | Prod profilinde `POST /billing/subscribe` PREMIUM vermiyor (entegrasyon testi). |
| **SEC-02** | Ücretli işin bedava kopyaları: `/fit/lineup` tam `solveBest` (kurallar+NLG+zayıf halka) çalıştırıyor, kredi düşmüyor; `/recommend/slot-view` ücretli `/recommend/slot`'un 8 sonuçlu bedava hali. | BE `fitengine/service/FitService.java:120-124`, `recommendation/service/RecommendationService.java:118` | **Onaylı (08 D-01): kırp.** lineup = atama + RoleFit% + rozet; slot-view = ilk 3 aday, gerekçesiz. | Kredi testi: lineup/slot-view çağrısı ya kredi düşürür ya da ücretli alanları döndürmez. |
| **SEC-03** | IDOR: `POST /players/{id}/development` public; body'deki `careerId` sahiplik kontrolü olmadan `fitPlayers(..., careerId)`'ye gidiyor → başka kullanıcının kariyer reytingleri okunur. | BE `development/service/DevelopmentService.java:58`, `common/configuration/SecurityConfig.java:40` | `careerId != null` ise auth zorunlu + `careerService.requireOwned(userId, careerId)`. | Başka kullanıcının careerId'siyle 403. |
| **SEC-04** | Kariyer-üretimi oyuncu sızıntısı: `idFilter`/`ids` verilince `owner_career_id` filtresi atlanıyor; `PlayerService.fitPlayers` sahip filtrelemiyor. | BE `catalog/repository/PlayerSearchRepository.java:110-111`, `catalog/service/PlayerService.java:92` | Filtreyi her zaman uygula. | `GET /players/{id}` başka kariyerin generated oyuncusu için 404. |
| **SEC-05** | `/ut/capture` istek boyutu sınırsız (2000 capture, body string limitsiz); definitions iki kez parse + regex → OOM riski. | BE `ut/model/UtCaptureModels.java:22`, `ut/service/UtCaptureService.java:259-277` | `spring.servlet.multipart`/Tomcat `max-http-form-post-size` + Jackson `StreamReadConstraints` + capture başına 2 MB, toplam 25 MB; tek parse. | 30 MB istek 413 döner. |
| **SEC-06** | Rate limit: `getRemoteAddr()` proxy arkasında herkes tek kova; Redis düşünce fail-open; `INCR`+`EXPIRE` atomik değil; admin-key filtresi rate limiter'dan önce → admin key brute-force sınırsız. | BE `auth/security/RateLimitFilter.java:68-84`, `SecurityConfig.java:64-65`, `ingestion/security/AdminKeyFilter.java:42-44` | `server.forward-headers-strategy=framework` + trusted proxy; Lua script ile atomik; admin path'ine ayrı sıkı limit; auth uçlarında fail-closed. | Testler: X-Forwarded-For ayrımı, admin 10 yanlış denemede 429. |
| **SEC-07** | ML servis uçları auth'suz (`/ml/reload` dahil). | `ml-service/app/main.py:54-67` | Sadece iç ağ (compose network) + shared secret header. | Dışarıdan erişim yok. |
| SEC-08 (P1) | Login'de kullanıcı yoksa bcrypt atlanıyor (timing), register e-posta varlığını sızdırıyor; refresh token reuse detection yok. | `auth/*` | Dummy hash ile sabit süre; genel mesaj; token family revoke. | — |

### 4.2 Doğruluk (P0/P1)
| ID | Sorun | Yer | Düzeltme | Kabul |
|---|---|---|---|---|
| **BUG-01** (P0) | Squad/SBC rating 3 farklı yerde 3 farklı: `sbcTraditional.teamRating` = `Math.round(total/n)` (yanlış yuvarlama), `squadRating.ts` = `floor(round(total)/n)` ama **18 oyuncu** üzerinden, backend `UtSquadService` = düz ortalama. Örnek 8×84+3×83: kod 84 der, oyun 83 der (`VERIFY` ama yaygın kabul edilen formül floor). Çözücü EA'nın reddedeceği kadroları "uygun" gösterir. | WEB `lib/sbcTraditional.ts:124-131`, `lib/squadRating.ts:6-20`; BE `ut/service/UtSquadService.java:53` | Tek kaynak: `ratingRules.squadRating(ratings, {size:11})` — formül config'ten; backend'de aynı fonksiyon + paylaşılan test vektörleri (`seed/ut-rules.fc27.json` içindeki `ratingTestVectors`). | 8×84+3×83 → 83; 11×84 → 84; backend ve frontend aynı vektörlerle yeşil. |
| **BUG-02** (P0) | Geleneksel çözücü aynı gerçek oyuncuyu iki kez seçebiliyor (kulüp kopyası + katalog kopyası / iki duplicate). EA buna izin vermez. | WEB `components/TraditionalSbc.tsx:101-108`, `lib/sbcTraditional.ts:215` | `used` set'ini `basePlayerId`/`assetId` ile tut. | Duplicate fixture testi. |
| **BUG-03** (P0) | Çözücü performansı: rating filtresi olmadan ~3000 kulüp + ~800 aday × beam 300 × 11 adım, her state'te `Set` klonu, ana thread → donma/OOM. | WEB `TraditionalSbc.tsx:105`, `lib/sbcTraditional.ts:205-255` | Havuzu buda (rating penceresi, mevki/rating başına top-K), immutable bitset, **Web Worker**; uzun vadede backend CP-SAT (04 §3). | 3000 kartlık fixture < 1.5 sn, UI donmaz. |
| **BUG-04** (P1) — ✅ **YANLIŞ ALARMDI** (08 D-21): FC27 kaynağı kodun değerlerini doğruluyor (kulüp 2/4/7, Icon ülke +1 & her lig +1, Hero/Hall of FUT ülke +1 & lig +1, menajer +1 tek). Aşağıdaki ilk tespit FC24–26 bilgisine dayanıyordu ve **geçersiz**; yalnız "testler +1/+2 ayırt edemiyor", "menajer bağlı değil" ve "CF↔ST" notları geçerli. İlk tespit: Kimya kuralları kaynaksız: kulüp eşiği `[2,4,7]`, Icon ülkeye +1, Hero lige +1. FC24–26'da bilinen: kulüp 2/5/7, Icon ülkeye +2 & her lige +1, Hero lige +2 (& ülke +1). Kod yorumu "FC27 kuralları" diyor ama kaynak yok; testler +1/+2 ayırt edemiyor. `HOF` kart tipi bilinen bir kimya kategorisi değil. Menajer parametresi hiç bağlanmamış (ölü kod). Mevki string'i birebir karşılaştırılıyor (CF ↔ ST). | WEB `lib/chemistry.ts:26,52-86`, `lib/chemistry.test.ts:8-9,39-59` | Kuralları `ut-rules.fc27.json`'a taşı (`VERIFY` ile işaretli), oyun içi 5 test kadrosuyla doğrula, testleri ayırt edici fixture'la yeniden yaz; backend'de de aynı motor (kadro değerlendirme için). | 5 oyun-içi doğrulanmış kadro birebir eşleşir. |
| **BUG-05** (P1) | localStorage: `utCaptureStore.set` dinleyicileri persist'ten **önce** bilgilendiriyor; quota hatasında UI veri gösterir, reload'da kaybolur; `utCardStore` 3000 kartta sessizce keser; şema versiyonu yok; sekmeler arası senkron yok. | WEB `state/utCaptureStore.ts:43-49`, `state/utCardStore.ts:23`, `pages/UtImportPage.tsx:51-52` | IndexedDB (Dexie) + `schemaVersion` + hata UI'da görünür + `storage` event. | 10k kartlık capture kayıpsız saklanır. |
| **BUG-06** (P1) | Capture index kayması: `raw` listesine parse'tan önce ekleniyor, `bodies`'e yalnızca başarılı parse; `sbcChallenges`/`sbcDetails` `raw.get(i)` ↔ `bodies.get(i)` eşliyor → tek bozuk SBC gövdesi sonraki tüm setId'leri yanlış gövdeyle eşler. | BE `ut/service/UtCaptureService.java:125-131, 542-546, 557-558` | `record Parsed(Capture raw, JsonNode body)` listesi tut. | Bozuk gövdeli fixture testi. |
| BUG-07 (P1) | CORS `PUT` yok → tarayıcıdan `PUT /ut/sbc/votes/{setId}` çalışmaz. | BE `common/configuration/CorsConfig.java:17` | `PUT` ekle. | CorsConfigTest. |
| BUG-08 (P1) | SBC oy: eşzamanlı ilk oy unique constraint → 500; `totals()` her çağrıda tüm tabloyu topluyor; `setId` doğrulanmıyor. | BE `ut/service/SbcVoteService.java:25-45` | `INSERT ... ON CONFLICT`, set bazlı agregasyon tablosu/materialized view. | — |
| BUG-09 (P1) | Ingestion: retry/backoff yok; tek sayfa hatası koşuyu yarıda bırakır; sayfa-numarasıyla canlı liste gezme (kayma/duplicate); siteden kalkan oyuncu silinmez; HTTP thread'inde senkron + sleep; kulüp N+1; tek-instance `AtomicBoolean` kilit. | BE `ingestion/service/IngestionService.java:85-174`, `ingestion/provider/EaSiteProvider.java:93-103` | Async job tablosu (`ingestion_run`), resume offset, exponential backoff, `ShedLock`/advisory lock, sayfa başı kulüp cache. | Ortada kesilen koşu kaldığı yerden devam eder. |
| BUG-10 (P1) | İş hataları HTTP 200 + `success:false` (not found, kredi yetersiz, yanlış şifre dahil). | BE `common/advice/GlobalExceptionAdvice.java:42` | 404/402/409/401 eşle (**API CONTRACT CHANGE** — frontend `api/client.ts` aynı commit'te güncellenmeli). | — |
| BUG-11 (P1) | Kullanıcı capture'ından paylaşılan `league`/`nationality` tablolarına kalıcı EA-id yazımı (ilk yazan kazanır, düzeltme yolu yok, `ea_league_id` unique index yok). | BE `ut/service/UtCaptureService.java:515-516`, `ut/repository/EaIdRepository.java:43-57` | Öneri tablosu (`ea_id_suggestion`) + N≥3 eşleşen bağımsız kullanıcı veya admin onayı; unique index. | — |
| BUG-12 (P1) | SBC günlük yenileme saati sabit 20:00 TRT. İngiltere 18:00 = TRT 20:00 yalnız BST'de; kışın 21:00. (`VERIFY`: EA içerik saati) | WEB `lib/coinPrice.ts:31-37` | `Europe/London` 18:00 hesapla (Intl). | Ocak tarihi testi. |
| BUG-13 (P1) | Token: refresh ağ hatasında başarısız sayılıp `tokens.clear()` → anlık kopmada logout; iki sekme rotating refresh token'ı yarıştırır; 204/HTML yanıtları "geçersiz yanıt" fırlatır; `careerForbidden` HTTP katmanında global state değiştiriyor. | WEB `api/client.ts:57-122`, `api/tokens.ts:31-35` | Ağ hatasında token silme; `BroadcastChannel` ile tek refresher; 204 toleransı. | — |
| BUG-14 (P1) | GitHub Pages base path'te kırık mutlak URL'ler. | WEB `pages/ImportPage.tsx:92` (`/fc27_career_export.lua`), `pages/UtSbcPage.tsx:76` (`<a href="/ut/import">`) | `import.meta.env.BASE_URL` + `<Link>`. | — |
| BUG-15 (P2) | `CareerService` bütçe null ise NPE; kiralıktaki oyuncunun satışını geri alınca `loan=false` ile geri ekliyor. | BE `career/service/CareerService.java:222,256,266` | — | — |
| BUG-16 (P2) | Arama: `ILIKE %q%` `%`/`_` kaçırmıyor, trigram index yok, sınırsız sayfa (derin OFFSET), her aramada 8 JOIN'li `count(*)`. Kariyer override'lı kolonlarla filtre kolonları tutarsız. | BE `catalog/service/PlayerService.java:64`, `PlayerSearchRepository` | `pg_trgm` + unaccent (Türkçe karakter!), keyset pagination, count'u ayrı/cache. | — |
| BUG-17 (P2) | `TacticResolver.slotIntent` her çağrıda tüm rol+tag'leri DB'den yüklüyor; `UtSquadService` slot başına çağırıyor (14×). | BE `tactics/service/TacticResolver.java:93` | Referans cache'i kullan. | — |
| BUG-18 (P2) | "system" tema `prefers-color-scheme` değişimini dinlemiyor; Inter font tanımlı ama hiç yüklenmiyor (sistem fontu görünüyor). | WEB `state/themeStore.ts:23-26`, `index.css:27`, `index.html` | 02'deki font kararıyla birlikte. | — |

### 4.3 Kod sağlığı / tekrar
- **İki FutCard:** `components/FutCard.tsx` (kullanılan) ve `components/futcard/FutCard.tsx` (yalnız kendi testi import ediyor = ölü kod), iki farklı tier paleti (`lib/futCard.ts` `TIER_STYLE` vs `futcard/FutCard.tsx` `RARITY`). → DS-05.
- **İki Modal:** `components/ui.tsx:140-160` (focus trap yok) ve `components/Modal.tsx` (focus trap var).
- **Üç taktik kodu UI'ı:** `EaTacticCode`, `squadBuilder/EaCodeCard`, `UtSquadPage` içindeki `UtTacticCode`.
- `coins/shortCoins` 3 yerde, `TIER_TONE` 2 yerde kopya.
- Dev bileşenler: `UtSquadPage.tsx` 551 satır, `SquadBuilderPage.tsx` 378, `SquadDensity.tsx` 296, `TraditionalSbc.tsx` 257 (veri çekme + havuz + çözüm iç içe).
- Route bazlı code-splitting yok (22 sayfa eager import).
- Ölü kod: `lib/coinPrice.ts` `clubCardCost`, `CLUB_OPPORTUNITY_WEIGHT`.
- Eski/çelişen metinler: `TraditionalSbc.tsx:166` ("fiyat verisi yok… kulüp havuzu henüz yok" — ikisi de var), `UtObjectivesPage.tsx:62` (veri akışı yok diyor, `/ut/import` var), web README "import kapalı".
- Backend: Dockerfile root ile çalışıyor; Swagger prod'da açık; hiçbir `@Scheduled` iş yok (fiyat/SBC/promo tazeleme için gerekecek); WebSocket bağımlılığı yok (06 için gerekecek).

### 4.4 Test durumu
- BE: 33 test dosyası (~3.3k satır) / ~21.6k satır ana kod. Fit engine, seed bütünlüğü, ingestion (mock), ML client, API flow (auth+refresh+kredi+iade), UT capture (6), SBC solver (6), codec, CORS, rate limit iyi. **Eksik:** eşzamanlı kredi, PREMIUM süre bitimi, arama filtre kombinasyonları, recommendation/scouting/advisor/development, NLG, Hungarian (oyuncu < slot), solver greedy fallback, capture bozuk gövde, oy servisi, gerçek Redis.
- WEB: 27 test dosyası, çoğu `lib/*`. **Sayfa/entegrasyon testi yok** (hiçbir `/ut/*` sayfası). Testler bazı hataları **kilitliyor** (kimya 2/4/7). CI'da `oxlint` koşmuyor.

---

## 5. ÖNCELİKLİ DÜZELTME LİSTESİ (ajan sırası)
```
P0  SEC-01  stub ödeme → prod'da kapalı
P0  SEC-03  development IDOR
P0  SEC-04  owner_career_id her zaman
P0  SEC-05  capture boyut limiti
P0  SEC-02  kredi bypass → kırp (08 D-01)
P0  SEC-06  rate limit + admin brute-force
P0  SEC-07  ml-service iç ağ
P0  BUG-01  tek squad-rating fonksiyonu + paylaşılan test vektörleri
P0  BUG-02  çözücüde aynı oyuncu iki kez
P0  BUG-03  çözücü Web Worker + budama
P0  OPS-01  CLAUDE.md değiştir (07)
P1  BUG-04  kimya kuralları config + doğrulama
P1  BUG-05  IndexedDB
P1  BUG-06  capture index kayması
P1  BUG-07  CORS PUT
P1  BUG-09  ingestion job/resume
P1  BUG-10  HTTP status (API contract change)
P1  BUG-11..14
P1  OPS-02  CI: oxlint + backend test workflow + Testcontainers Redis
P1  OPS-03  Dockerfile non-root, prod'da swagger kapalı
P2  geri kalan + §4.3 temizlikleri
```
