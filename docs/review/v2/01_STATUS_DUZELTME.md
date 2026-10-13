# 01 · STATUS DÜZELTMESİ — v1 iddiaları ↔ kod (2026-10-12)
Kural: Aşağıdaki "Gerçek" sütununa göre iki repodaki `docs/review/STATUS.md`'yi güncelle. Kısaltmalar: `J/` = `src/main/java/com/fcareer/`, `R/` = `src/main/resources/`, `W/` = web `src/`.

## 1. BACKEND
| ID | STATUS diyor | Gerçek | Kanıt | Yeni durum |
|---|---|---|---|---|
| SEC-01 | DONE | Doğru. Küçük boşluk: iki eşzamanlı subscribe çağrısı "aktif abonelik var mı" kontrolünü birlikte geçebilir (kilit yok) | `J/billing/provider/StubPaymentProvider.java:10`, `J/billing/service/BillingService.java` | DONE + P2 not |
| SEC-02 | DONE | **Eksik.** `/fit/lineup` ve `/recommend/slot-view` kırpılmış ama **anonim** `/recommend/preview` tam sıralamayı (reasons, weaponChecks, intentFit) 8 oyuncuya kadar döndürüyor | `J/recommendation/service/RecommendationService.java:107-117`, `J/common/configuration/SecurityConfig.java:42` | PARTIAL → BE-SEC-01 (P0) |
| SEC-03 | DONE | Sahiplik doğru. Ama geliştirme planı spoiler modunda bile tam POT dönüyor | `J/development/controller/DevelopmentController.java:33-40` | DONE + BE-SEC-05 |
| SEC-04 | DONE | Doğru | `J/catalog/repository/PlayerSearchRepository.java:110` | DONE |
| SEC-05 | DONE (sapma) | **Eksik.** Yalnız Content-Length'e bakıyor, chunked istek geçiyor. Yalnız `/ut/capture`'ı kapsıyor (`/ut/contrib/capture` sınırsız). 8M/40M kontrolü gövde belleğe okunduktan sonra | `J/common/configuration/RequestSizeLimitConfig.java:19-28` | PARTIAL → BE-SEC-02 |
| SEC-06 | DONE | Redis kapalıyken admin kilidi her şeyi geçiriyor (fail-open), yalnız IP bazlı | `J/ingestion/security/AdminKeyFilter.java:67-74` | PARTIAL → BE-SEC-07 |
| SEC-07 | DONE | Header var ama ml-service secret tanımlı değilse herkesi kabul ediyor. README'nin dayandığı `docker-compose.yml` **repoda yok** | `ml-service/app/main.py:20-23`, `README.md:8-9` | PARTIAL → BE-ARCH-06 |
| SEC-08 | PARTIAL ("reuse TODO") | **STALE.** Reuse tespiti ve aile iptali yapılmış. Yeni sorun: logout token'ı "kullanıldı" işaretliyor, sonra o token tekrar gönderilirse kullanıcının tüm oturumları düşüyor | `J/auth/service/AuthService.java:101-119`, `J/auth/security/RedisRefreshTokenStore.java:41-53` | PARTIAL → BE-SEC-08 |
| BUG-01 | DONE | **Backend yarısı yanlış.** `SquadRatingCalculator` yalnız testlerde kullanılıyor, `/ut/squad/evaluate` düz ortalama dönüyor. Kural dosyası `size:11`, web 18 | `J/ut/service/UtSquadService.java:173-175`, `J/ut/service/SquadRatingCalculator.java:11`, `R/seed/ut-rules.fc27.json:5-8` | PARTIAL → BE-BUG-04, RULE-02 |
| BUG-04 | PARTIAL | Yalnız 3 eşik dizisi config'te. Icon/Hero/HoF/menajer/mevki dışı/kadın-erkek kuralları config'te yok. Backend'de kimya motoru hiç yok | `R/seed/ut-rules.fc27.json:9-15` | PARTIAL → RULE-01 |
| BUG-07 | DONE | `PUT` var ama CORS izinli başlıklarında `X-Admin-Key`, `X-Link-Token`, `X-Ext-Version` yok → admin sayfası farklı origin'den preflight'ta düşer | `J/common/configuration/CorsConfig.java:18` | PARTIAL |
| BUG-08 | DONE | Upsert düzeldi. Özet hâlâ her çağrıda tüm tabloyu topluyor, `setId` `sbc_set`'e karşı doğrulanmıyor | `J/ut/service/SbcVoteService.java:22-25` | PARTIAL |
| BUG-09 | TODO | **STALE, kısmen yapıldı.** Backoff + `ingestion_run` resume var. Eksik: async çalışma, çoklu instance kilidi, heartbeat, siteden kalkan oyuncuyu emekliye ayırma | `J/ingestion/service/IngestionService.java:57-136` | PARTIAL → BE-BUG-16 |
| BUG-10 | DONE | Çoğunlukla. Bilinmeyen path, yanlış method, yanlış media type, path-variable validation, data-integrity ve `IllegalArgumentException` → **500** | `J/common/advice/GlobalExceptionAdvice.java:74-78` | PARTIAL → BE-BUG-11 |
| BUG-11 | TODO | **STALE, yapıldı** (`ea_id_suggestion`, eşik 3, admin apply). Yeni zayıflık: 3 bedava hesap yanlış EA id yazabiliyor | `J/ut/repository/EaIdRepository.java:~44-83` | DONE + BE-SEC-06 |
| BUG-12 | DONE (VERIFY) | Yalnız web'de. Backend'de içerik takvimi yok, günlük kredi sıfırlaması container saat diliminde gece yarısı | `J/common/configuration/ClockConfiguration.java:12`, `J/billing/service/CreditService.java:72-81` | PARTIAL → BE-ARCH-02 |
| BUG-15 | TODO | Kısmen (null bütçe). LOAN_OUT/SELL geri alınca oyuncu `loan=false` ile dönüyor | `J/career/service/CareerService.java:276-283` | PARTIAL |
| BUG-16 | TODO | Kısmen (LIKE kaçışı). Trigram index yok, keyset paging yok, attribute/WF/SM filtreleri kariyer override'larını görmüyor | `PlayerSearchRepository.java:112-113,146-147,171` | PARTIAL |
| BUG-17 | TODO | **STALE, yapıldı** (`TtlMemo`). `getFitRole` her çağrıda DB'ye gidiyor | `J/roles/service/RoleService.java:40-56` | DONE + P2 not |
| OPS-01 | DONE | CLAUDE.md değişti ama hâlâ Azerlottery standardını `@`-import ediyor (§12 "iş hatası HTTP 200", §9 `/api/web/v2` → D-09 ve kodla çelişiyor) | `CLAUDE.md:14-18` | PARTIAL → BE-ARCH-01 |
| OPS-02 | DONE | ML job test yokken de geçiyor (`\|\| [ $? -eq 5 ]`). Redis hiç test edilmiyor | `.github/workflows/backend-test.yml` | PARTIAL |
| OPS-03 | DONE | Doğru. HEALTHCHECK ve JVM bellek bayrağı yok | `Dockerfile` | DONE + P2 not |
| TAC (C2) | PARTIAL | `focusExcludePositions` ile 11 geçersiz (pozisyon,rol,focus) kombinasyonu ayıklanmış = **C2 tamam**. Kalan: BE-BUG-10 (formasyon eşlemesi), BE-BUG-20 | `R/seed/roles.json:41,44,61,64,75,84` | PARTIAL (dar) |
| D-04 | PARTIAL | Portre template'i hâlâ 403. Proxy yalnız bellekte, "30 gün" sadece header, her yanıt `image/png` etiketli, 404'ler cache'lenmiyor | `J/catalog/configuration/CatalogProperties.java:15`, `J/catalog/service/FaceImageService.java:27-28,51-69` | PARTIAL → CARD-01..03 |
| DATA-08 | (örtük DONE) | Uçlar `/ut/kb/*` altında, spec'teki `/ut/items`, `/ut/sbc` değil. Redis cache/ETag yok. Konsensüs var. Fiyat agregasyonu **yanlış** | `J/ut/kb/UtKnowledgeController.java:77-124` | PARTIAL → BE-BUG-02, BE-ARCH-09 |

## 2. WEB
| ID | STATUS diyor | Gerçek | Kanıt | Yeni durum |
|---|---|---|---|---|
| SEC-01 | DONE | Doğru | `W/pages/ProfilePage.tsx:39` | DONE |
| BUG-01 | DONE | Tek formül var (`squadRating.ts:8-16`) ama `utRules.fc27.json:7` `size:11`, `squadRating.ts:6` `SQUAD_SIZE=18`. UT kurucu yalnız XI'i hesaplıyor, yedek yok | `W/pages/UtSquadPage.tsx:201` | PARTIAL → RULE-02, WEB-UX-03 |
| BUG-02 | DONE (VERIFY) | **Yalnız çözücüde.** UT kurucu kart anahtarıyla engelliyor (`c:instanceId`/`p:id`), aynı oyuncunun iki versiyonu XI'e girebiliyor | `W/lib/utCard.ts:39`, `W/pages/UtSquadPage.tsx:323` | PARTIAL → WEB-BUG-08 |
| BUG-03 | DONE | Doğru. Worker iptal edilemiyor | `W/lib/sbcTraditional.ts:217-249` | DONE + WEB-PERF-02 |
| BUG-04 | PARTIAL | **Hâlâ TODO.** `squadChemistry` hiç menajerle çağrılmıyor, menajer UI'ı yok | `W/pages/UtSquadPage.tsx:107-118` | TODO → WEB-UX-03 |
| BUG-05 | DONE | Dexie + taşıma + BroadcastChannel var. `schemaVersion` yazılıyor ama hiç okunmuyor (migration yolu yok). Sayfalar store'u `useMemo([])` ile bir kez okuyor → sekmeler arası güncelleme ekrana yansımıyor | `W/state/bigStore.ts` | PARTIAL → WEB-BUG-23, WEB-ARCH-03 |
| BUG-12 | DONE | Doğru (Intl + Europe/London) | `W/lib/coinPrice.ts:52-64` | DONE |
| BUG-13 | DONE | Çoğunlukla. `/auth/refresh` 429 dönerse "geçersiz" sayılıp kullanıcı çıkışa atılıyor | `W/api/client.ts:69-77` | PARTIAL → WEB-BUG-19 |
| BUG-14 | DONE | **Eksik.** `PlayerAvatar` göreli `/api/v1/img/face/{id}` kullanıyor | `W/components/PlayerAvatar.tsx:19` | PARTIAL → WEB-BUG-12 |
| DS-01 | PARTIAL ("yalnız .matchday") | **STALE.** Token'lar artık global (`index.css:15-79`), `.matchday` için CSS kuralı yok. Kalıntılar: slate/teal/emerald (PositionCard, PlayerAvatar, SquadDensity), 90 eski `Pill tone=`, kart config'i dışında 44 hex | — | PARTIAL → WEB-DS-06 |
| DS-03 | — | İki modal var (`ui.tsx:189` + `components/Modal.tsx:16`), Radix yok (D-06 ile çelişiyor) | — | TODO → WEB-DS-05 |
| DS-04 | PARTIAL | Glif durum ikonları duruyor (↑↓↕⇄▲━┅×) | `PlayerDiffPanel.tsx:11-14` vb. | PARTIAL → WEB-DS-07 |
| DS-05/11 | DONE | Tek PlayerCard doğru. Ama EA_ASSET yalnız build-time env (`VITE_CARD_MODE`+`VITE_CARD_BG_BASE`) ile açılıyor, ikisi de deploy'da set edilmemiş → hep OWN. D-23 "EA_ASSET varsayılan, runtime bayrak" diyor | `W/lib/cardDesign.ts:59-64` | PARTIAL → CARD-04 |
| DS-08 | — | Yapılmamış: 125 açıklama paragrafı, 2 `HelpPopover` kullanımı | — | TODO |
| DS-12 | PARTIAL | `letterGrade`/`ratingTone` hiçbir yerde kullanılmıyor | — | PARTIAL |
| D-04 | PARTIAL | FC27 portre 403 → siluet. Ratings sitesi kart render'ı (`faceAssetId` = `ea_id`) kullanılmıyor | `PlayerSearchRepository.java:228` | PARTIAL → CARD-* |
| D-08 IA | — | **Yapılmamış.** `/squad`, `/squad/builder`, `/tactics/wizard`, `/recommend`, `/scouting`, `/teams`, `/players` hâlâ kökte. `/ut/players` yok. "Kart veritabanı" etiketi duruyor | `W/App.tsx:53-69`, `W/components/Layout.tsx:63` | TODO → WEB-ARCH-01 |
| CAR-20/21/22/23/24/29 | DONE | Ekranlar var, hatalı: WEB-BUG-01 (P0), 06, 09, 10, 13 | — | DONE + buglar |
| UT-16/UT-19 | (Faz D) | Evo sayfası yalnız liste. UT oyuncu sayfası / versiyon şeridi yok; `kbItem` versiyon dönüyor ama `kbItems/kbItem/kbPrices/kbPromos` web'de hiç kullanılmıyor | `W/api/endpoints.ts:140` | TODO → WEB-UX-01, CARD-06 |

## 3. STATUS.md'ye eklenecek blok (kopyala)
```md
## v2 inceleme (2026-10-12) — docs/review/v2/
- P0 açık: BE-SEC-01, BE-BUG-01, BE-BUG-02, BE-BUG-03, WEB-BUG-01
- STALE düzeltildi: SEC-08 (reuse yapılmış), BUG-09 (kısmi), BUG-11 (yapılmış), BUG-17 (yapılmış), DS-01 (token'lar global)
- Geri alınan DONE: SEC-02, SEC-05, BUG-01 (backend), BUG-14, DS-05 (EA_ASSET kapalı)
```
