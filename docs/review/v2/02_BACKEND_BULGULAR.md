# 02 · BACKEND BULGULARI (FCarrierTacticBuilderBackend@4d2f8ae)
Statik okuma. `J/` = `src/main/java/com/fcareer/`, `R/` = `src/main/resources/`. Satır numaraları commit `4d2f8ae`'ye göre. Satır kaymışsa sembol adıyla bul.
Her madde: **Sorun → Düzeltme → Kabul**. Davranış değiştiren her düzeltmede commit mesajına `BEHAVIOR CHANGE:` yaz. Sözleşme değiştirenlerde `API CONTRACT CHANGE` yaz ve web'i aynı PR serisinde güncelle.

---
## P0

### BE-SEC-01 · `/recommend/preview` ücretli analizi anonim veriyor
`J/recommendation/service/RecommendationService.java:107-117`, `J/common/configuration/SecurityConfig.java:42`
- **Sorun:** `preview()` `rank(...)` çıktısını filtresiz dönüyor: tam `RoleFitResponse` (reasons, weaponChecks), `intentFit`, setupDelta, 8 oyuncuya kadar. Giriş yapmış kullanıcıya bedava verilen `slot-view`'dan bile fazla. D-01'e aykırı.
- **Düzeltme:** `return withoutPaidInsights(rank(...))` ve limit `SLOT_VIEW_LIMIT` (3). İstersen login zorunlu yap. `/players/compare` ve `/fit/role` ücretli sayılıyorsa aynı kırpmayı uygula.
- **Kabul:** Anonim preview yanıtında `reasons`, `weaponChecks`, `intentFit` yok, en fazla 3 öğe. Entegrasyon testi.

### BE-BUG-01 · Promo kart fiyatı base karta yazılıyor (veri bozulması)
`J/ut/kb/KnowledgeBaseRepository.java:180-184` (`itemIdByAsset`), `J/ut/kb/ContributionService.java:250`, `J/ut/service/UtCaptureService.java:438`
- **Sorun:** Fiyat `ea_asset_id` ile eşleniyor (oyuncunun tüm versiyonlarında aynı), `ORDER BY (rating = :r) DESC, id`. Capture `resourceId`/`definitionId`'yi hiç tutmuyor. TOTW fiyatı base item'a düşüyor.
- **Düzeltme:**
  1. `CaptureCard` ve `MarketPrice` modellerine `resourceId` (EA `resourceId`/`definitionId`) ve `rareflag` ekle.
  2. Fiyatı yalnız `(game_version_id, ea_resource_id)` ile eşle. Bilinmeyen item'ı atla ve sayaçla (`skipped_unknown_item`).
  3. `price_obs` ve `price_agg`'yi temizle (migration: `TRUNCATE price_obs, price_agg;` + not).
- **Kabul:** Base kartı `ut_item`'da olan bir oyuncunun TOTW müzayedesini içeren capture → base item'a 0 gözlem. TOTW item varsa ona yazılır.

### BE-BUG-02 · Fiyat agregasyonu aşağı yanlı
`J/ut/kb/KnowledgeBaseRepository.java:221-226`
- **Sorun:** Katkıcı başına %30 sınırı `row_number() OVER (PARTITION BY contributor ORDER BY value)` + `rn <= ceil(total*0.3)` ile yapılıyor. Tek katkıcıda gözlemlerin **en ucuz %30'u** kalıyor, p50 minimuma çöküyor. `KnowledgeBaseIntegrationTest.java:153-160` testi 1000/1100/1200 ile geçiyor çünkü beklenen aralık 1000'i içeriyor.
- **Düzeltme:** Önce katkıcı×bucket başına medyan, sonra katkıcılar arası yüzdelik (her katkıcının ağırlığı ≤ %30). <2 katkıcıda gözlem bazlı yüzdelik + `low_confidence=true`. Testin beklenen aralığını daralt.
- **Kabul:** Tek katkıcı {1000,1100,1200} → p50 = 1100. İki katkıcı {1000×10} ve {2000×1} → p50 1000-2000 arası, tek katkıcı baskın değil.

### BE-BUG-03 · Kullanıcının kendi listeleri "market fiyatı" sayılıyor
`J/ut/service/UtCaptureService.java:163-166,438-439`, `J/ut/kb/ContributionService.java:246-262`
- **Sorun:** Tradepile, watchlist, trade-status kayıtları `BIN` gözlemi olarak yazılıyor. Tradepile'da kullanıcının **kendi** istediği fiyat var. `item.lastSalePrice` (sahibinin bir zamanlar ödediği) sunucu saatiyle `LAST_SALE` yazılıyor. Sunucu kendi `capture-rules.json` `personalOnly` listesini uygulamıyor.
- **Düzeltme:** Yalnız `TRANSFER_MARKET` arama sonuçları (`expires > 0`) fiyat olur. `observed_at` = capture `ts`. Item verisinden `LAST_SALE` çıkar. `capture-rules.json`'u `ContributionService`'te sunucu tarafında uygula.
- **Kabul:** Yalnız tradepile/watchlist içeren katkı → 0 fiyat gözlemi.

---
## P1 · Güvenlik / kötüye kullanım

### BE-SEC-02 · İstek gövdesi çoğu uçta sınırsız
`RequestSizeLimitConfig.java:19-28`, `application.yml:24-27`
- **Sorun:** Chunked istek Content-Length kapısını atlıyor. `/ut/contrib/capture` ve diğer JSON uçları sınırsız. Multipart premium import 150/190 MB ve tamamı `List<Map>` olarak belleğe okunuyor.
- **Düzeltme:** `/api/**` için servlet filtresi: input stream'i byte sayan stream'le sar, yol bazlı bütçe (capture/contrib 60 MB, multipart 25 MB, diğer 1 MB). Jackson `StreamReadConstraints.maxDocumentLength`. Multipart limitlerini düşür.
- **Kabul:** `/ut/contrib/capture`'a 70 MB chunked POST → 413, heap büyümüyor.

### BE-SEC-03 · Streamlined SBC çözücü hâlâ public
`SecurityConfig.java:42`, `J/ut/service/SbcSolverService.java:90-91`, `J/ut/model/SbcModels.java:17-18`
- **Sorun:** D-11 auth + rate limit istiyordu, yok. Tek çağrı ~60M DP adımı ve ~56 MB ayırabiliyor. Ortak limit 120/dk/IP.
- **Düzeltme:** Login zorunlu, ayrı bucket 10/dk, `MAX_DP_WORK` 10M.
- **Kabul:** Anonim → 401. Dakikadaki 11. çağrı → 429.

### BE-SEC-04 · Fiyat job kilidi sızabilir, job sessizce durur
`J/ut/kb/UtScheduledJobs.java:28-38`
- **Sorun:** `pg_try_advisory_lock`/`unlock` havuzlu `JdbcTemplate` ile. Lock ve unlock farklı bağlantıda çalışabilir, kilit bir havuz bağlantısında asılı kalır, sonraki çalışmalar hiç kilit alamaz.
- **Düzeltme:** `TransactionTemplate.execute` içinde `pg_try_advisory_xact_lock`, ya da ShedLock (JDBC). Bütün `@Scheduled` job'lar için aynı yöntem.
- **Kabul:** Hikari pool ≥2 iken art arda iki çalışmanın ikisi de agregasyon yapar.

### BE-SEC-05 · Spoiler modu (CAR-32) tam POT'u sızdırıyor
- **Sorun:** Snapshot diff ve oyuncu geçmişi (`J/career/desk/CareerDeskService.java:80-121`), geliştirme planı (`DevelopmentController.java:33-40`), recommend/scouting/transfer-targets/advisor/team-profile yanıtları maskesiz. `pot_min`/`pot_max`/`sort=potential` ile ikili aramayla tam POT bulunabiliyor (`PlayerSearchRepository.java:53,128-129`).
- **Düzeltme:** Tek yerde maskeleme: careerId alan servis seviyesi yardımcı (`SpoilerPolicy.apply(careerId, dto)`), `potential` alanı olan tüm DTO'lara uygulanır. SCOUT modunda potansiyel filtrelerini 5'lik bantlara yuvarla ya da reddet.
- **Kabul:** SCOUT_RANGE açıkken listelenen hiçbir yanıtta tam POT yok. `pot_min=87` ve `88` aynı kümeyi döner.

### BE-SEC-06 · Sınırsız bedava hesap = kredi çiftliği + yanlış EA id
`J/auth/service/AuthService.java:65-86`, `EaIdRepository.java:~44`
- **Sorun:** Kayıt doğrulamasız, her hesap günde 20 kredi. 3 hesap bir lig/ülke EA id'sini kalıcı yazabiliyor.
- **Düzeltme:** Kredi yalnız e-posta doğrulamasından sonra. IP/gün kayıt limiti. EA-id oyları yalnız katkıcılardan sayılsın ya da eşik yükselsin + admin onayı.
- **Kabul:** Doğrulanmamış hesap 0 kredi. Katkıcı olmayanın oyu uygulanmaz.

### BE-SEC-07 · Admin = tarayıcıdaki paylaşılan statik anahtar (DN-06)
`AdminKeyFilter.java:67-74`, web `pages/AdminUtPage.tsx:35`, `J/ut/kb/UtAdminController.java:32` (`ACTOR="admin-key"`)
- **Sorun:** Redis kapalıyken kilit fail-open. Anahtar SPA'da (sessionStorage). CORS başlığı zaten engelliyor. Audit log adminleri ayırt edemiyor.
- **Düzeltme:** `app_user.role` (USER/ADMIN), JWT'de rol, `/admin/**` ADMIN rolüne. Anahtar yalnız makineden makineye ingestion için kalsın. Kilit fail-closed. `contributor_audit.actor` = user id.
- **Kabul:** Admin sayfası JWT ile çalışır. Audit satırında user id.

### BE-SEC-08 (P2) · Token/şifre kenar durumları
- Logout token'ı "used" işaretliyor → sonra replay edilirse kullanıcının tüm oturumları iptal (`RedisRefreshTokenStore.java:48`). **Düzelt:** logout ayrı `delete` kullansın.
- `RefreshRequest`'te `@Size` yok. **Düzelt:** `@Size(max=128)`.
- Şifre 72 **karakter** sınırı, bcrypt sınırı 72 **byte**. Türkçe çok baytlı 72 byte üstü şifre Spring Security 6.5 bcrypt'te exception → 500 (`RegisterRequest.java:23`). **Düzelt:** UTF-8 byte uzunluğunu doğrula. **Kabul:** 2 byte'lık 40 karakter şifre → 400.

### BE-SEC-09 (P2) · Kariyer varlığı durum koduyla sızıyor
`CareerService.java:392-405`: olmayan kariyer 404, başkasınınki 403. **Düzelt:** ikisi de 404.

### BE-SEC-10 (P2) · Link token tekrar kullanılabilir, mod kontrolsüz
`RedisLinkTokenStore.java:42-53`, `LinkTokenFilter.java:33`: 15 dk tekrar kullanılabilir (V13 yorumu tek kullanımlık diyor). PERSONAL token `/ut/contrib/*`'da kabul ediliyor. Gövdedeki `CaptureRequest.linkToken` yok sayılıyor. **Düzelt:** contrib ucunda `fc.linkMode == CONTRIB` zorunlu. Gövde alanını ya uygula ya kaldır.

### BE-SEC-11 (P2) · Yüz proxy cache'i takedown ve bellekle çelişiyor
`FaceImageController.java:31`, `FaceImageService.java:27-28,51-69`
- 30 gün `immutable` public cache ↔ 48 saat takedown sözü. 400 giriş × 2 MB = 800 MB heap riski. Miss cache'lenmiyor, EA'ya istek yükseltiyor.
- **Düzelt:** `Cache-Control: public, max-age=86400` (immutable değil). Disk/Redis cache, anahtar `(kind, id, locale, template_version)`, negatif cache 1 saat, toplam byte sınırı (64 MB). Content-Type upstream'den. Detay: 06 §3.

---
## P1 · Doğruluk

### BE-BUG-04 · Squad rating birleştirmesi backend'e hiç ulaşmamış
`UtSquadService.java:173-175`, `SquadRatingCalculator.java:11`, `R/seed/ut-rules.fc27.json:5-8`
- **Düzeltme:** İmza `squadRating(List<Integer> ratings, int size)`. Boyutlar kurallardan: `{"squad":18,"sbc":11}` (RULE-02). `evaluate()` XI + seçilen yedekler (18'e kadar) üzerinden hesaplar, kulübün en iyi 18'inden değil. Yanıta `averageOverall` ekle.
- **Kabul:** Paylaşılan test vektörleri iki tarafta yeşil, endpoint `squadRating` alanını döner.

### BE-BUG-05 · SBC gereksinim parser'ı bilgi kaybediyor
`J/ut/service/SbcChallengeParser.java:56-72,77-99`
- **Sorun:** Bir grupta ana tip + `PLAYER_COUNT` varsa (örn. rarity grubu count 1 = "en az 1 TOTW") count düşüyor. `PLAYER_RARITY`, `PLAYER_RARITY_GROUP`, `PLAYER_MIN_OVR`, `PLAYER_EXACT_OVR`, `PLAYER_TRADABILITY`, `TEAM_STAR_RATING`, slot başına `CHEMISTRY_POINTS` → `UNKNOWN:*`. Aynı gruptaki ikinci ana tip birinciyi eziyor.
- **Düzeltme:** Çıktı `{kind, op, value, count, refs, raw}`. Tam enum eşlemesi 05 §3.2'de. Ham `elgReq`'i sakla.
- **Kabul:** `PLAYER_RARITY_GROUP=4, PLAYER_COUNT=1, SCOPE=0` fixture'ı → kind `RARITY_GROUP`, value 4, count 1, op MIN.

### BE-BUG-06 · Katkı hattı alan düşürüyor
`J/ut/kb/ContributionService.java`
- SBC set ödülü hep `[]`: payload'da yalnız `awardCount` var, `apply()` `p.reward` okuyor (`:162-163,191-197`).
- `itemScoreTarget` hiç doldurulmuyor (`:169` vs `:199-212`).
- Tekrar sayısı -1/null'a çöküyor (`:163`).
- `ends_at` capture zamanı yerine sunucu alım zamanından hesaplanıyor (`:197,281-283`) ve içerik değişmediyse güncellenmiyor.
- **Düzeltme:** Ödül listesi, hedef skor, tekrar sayısını payload'a taşı. Bitişi capture `ts`'den hesapla. Hash aynı olsa da `ends_at` ve `last_seen_at` güncelle.
- **Kabul:** Set ödüllü fixture → `reward` boş değil.

### BE-BUG-07 · Lokalize metin konsensüsü bozuyor
`ContributionService.java:191-238`, `KnowledgeBaseRepository.java:255-258`
- **Sorun:** İçerik hash'i lokalize ad/açıklamayı içeriyor → TR ve EN katkıcı sonsuza kadar çelişir. Kuyrukta `DO UPDATE SET candidates = EXCLUDED` önceki adayları eziyor.
- **Düzeltme:** Hash yalnız yapı (id, gereksinim, ödül, sayı, formasyon). Adlar locale bazlı `kb_text(kind, natural_key, locale, name, description)` tablosunda. Kuyrukta `candidates || EXCLUDED.candidates`.
- **Kabul:** Aynı set TR ve EN capture → aynı hash.

### BE-BUG-08 · Kart tipi İngilizce ada bağlı
`UtCaptureService.java:64,383-385,410-425`, V13 satır 40
- **Sorun:** Hero ve Hall of FUT İngilizce alt dizeyle tespit ediliyor (ASCII ad regex'i arkasında). Türkçe istemcide hiç tespit edilmez → kimya bozulur. `"HOF"` `ut_item.card_type` CHECK'inde yok.
- **Düzeltme:** Kart tipini rarity id'den (`rareflag`) eşle; eşleme tablosu `ut_rules.rarities` (04 §2). CHECK'e `HALL_OF_FUT` ekle.
- **Kabul:** TR locale capture, Hero rarity id → `cardType=HERO`.

### BE-BUG-09 · Base item senkronu yanlış yazıyor
`KnowledgeBaseRepository.java:186-197`
- `is_rare = overall >= 65` yanlış (FC27'de base kartta rare/common ayrımı yok).
- Kulüp/lig/ülke id'leri **iç** id, SBC gereksinimleri **EA** id kullanıyor.
- Conflict update ad, WF, SM, AcceleRATE'i yenilemiyor.
- **Düzeltme:** `is_rare`'i kaldır ya da EA rarity'den doldur. EA takım/lig/ülke id'lerini sakla (`ea_team_id`, `ea_league_id`, `ea_nation_id` üzerinden join). Türetilmiş `quality` (BRONZE ≤64 / SILVER 65-74 / GOLD ≥75). Conflict'te tüm alanları güncelle.

### BE-BUG-10 · Formasyon tanımları EA ile uyuşmuyor
`R/seed/formations.json` (5-3-2), `J/tacticcode/service/TacticCodeService.java:145-149`
- Bizim 5-3-2 = 3×CM. EA `f532` = CDM + 2×CM. 5-3-2 preset'inin (`presets.json:1481`) kodunu export etmek "formation not available" atıyor.
- Etiketler: bizim "4-3-3" = EA "4-3-3 (2)", bizim "4-3-3 (3 CM)" = EA "4-3-3", bizim "4-5-1" = EA "4-5-1 (2)", bizim "4-1-2-1-2" = EA "4-1-2-1-2 (2)", bizim "4-4-1-1" = EA "4-4-1-1 (2)". `ut-meta.json` olmayan "4-4-1-1 (2)"'yi referans ediyor.
- FUT.GG adlandırması (yüklenen taktik sayfası) EA adlarının oyun içi karşılığını da veriyor: "4-1-2-1-2 Narrow" = (2), "4-3-3 Holding" = (2), "4-3-3 Defend" = (3), "4-3-3 Attack" = (4), "4-4-2 Holding" = (2), "4-5-1 Attack" = (2), "4-2-3-1 Wide" = (2). Görüntü adı olarak da bunları kullan.
- **Düzeltme:** `formations.json`'a `eaFormationId` ve `displayNameTr/En` ekle, id'leri EA etiketine çevir, 5-3-2 slotlarını düzelt. Tam liste 04 §3.
- **Kabul:** 5-3-2 preset export → `f532`'ye decode olan kod.

### BE-BUG-11 · Framework exception'ları 500 oluyor
`GlobalExceptionAdvice.java:74-78`. Açık handler ekle: `NoResourceFoundException`→404, `HttpRequestMethodNotSupportedException`→405, `HttpMediaTypeNotSupportedException`→415, `HandlerMethodValidationException`→400, `DataIntegrityViolationException`→409, `PlayerSearchRepository.java:169`'daki `Attr.fromKey` `IllegalArgumentException`→400. **Kabul:** `GET /api/v1/nope` → 404.

### BE-BUG-12 · Yedek geri yükleme doğrulamasız
`CareerDeskController.java:105-107`, `CareerDeskService.java:231-249`: `@RequestBody Backup`'ta `@Valid` yok. Kötü tag/spoilerMode → DB CHECK → 500. Liste boyutları sınırsız. **Düzelt:** `@Valid`, listelere `@Size`, tag için `TagRequest` kalıbı. **Kabul:** `tags=[{"tag":"X"}]` → 400.

### BE-BUG-13 · Tekrar import elle kurulan kadroyu siliyor
`J/career/importer/CareerImportService.java:152-160,183-191`
- **Sorun:** Her import'tan sonra takım adı tahmin edilebiliyorsa `rebuildSquad` tüm `user_player_state` satırlarını siliyor. Kadro olay günlüğü kadroyla uyuşmaz hâle geliyor.
- **Düzeltme:** Kullanıcı takımı LE export'undan (`context.userTeamId`, 07 §3) gelir, adla tahmin edilmez. Otomatik rebuild yalnız kadro boşken. Aksi halde `guessedTeamId` dön, kullanıcı `/import/squad`'ı açıkça çağırsın ya da birleştir ve PROMOTE/SELL olaylarını yaz.
- **Kabul:** İkinci import elle eklenen oyuncuyu korur.

### BE-BUG-14 · Snapshot budanıyor ve çok ince
`CareerDeskService.java:36,54-55`; V14 satır 26-33. 30'dan eski snapshot siliniyor ("hiçbir şey kaybolmaz" ilkesine aykırı). Snapshot yalnız OVR/POT/takım tutuyor. **Düzelt:** saklama süresi config (varsayılan sınırsız), `attrs JSONB`, `positions`, `value`, `wage`, `age`, `game_date` kolonları.

### BE-BUG-15 · Ingestion kulübü ad + lig ile tanıyor
`IngestionService.java:201-215`, `J/ingestion/provider/RatingItemMapper.java:52`
- Kulüp küme düşünce/çıkınca ligi değişir → kopya kulüp satırı. EA `team.id` parse ediliyor ama kullanılmıyor. Her oyuncu için kulüp lookup + save (N+1).
- **Düzelt:** Kulübü `ea_team_id` ile upsert et (`team.id`'den), sayfa başına cache, lig değişince güncelle. **Kabul:** Küme değiştiren kulüp yeniden ingest'te aynı `club.id`.

### BE-BUG-16 · BUG-09'un kalanı
`IngestionService.java:57-112`, `IngestionRunRepository.java:23`: HTTP thread'inde senkron (~180 sayfa, dakikalar). Kilit tek JVM `AtomicBoolean`. İkinci instance RUNNING satırı eşzamanlı "resume" edebilir. `totalItems` yoksa bir sayfa sonra DONE. Siteden kalkan oyuncu emekliye ayrılmıyor. **Düzelt:** arka plan executor + runId dön, DB kilidi (advisory xact lock/ShedLock), `heartbeat_at` (eskiyse resume edilebilir), DONE yalnız `fetched >= total > 0`, tam çalışmada görülmeyenleri RETIRED.

### BE-BUG-17 · Lig seviyesi (tier) hiç set edilmiyor
`J/catalog/entity/LeagueEntity.java:27`; `J/mlclient/service/MlValuationService.java:113-124`, `ModeledValueFormula.java:15,60-65`. ML `league_tier` hep null, modellenmiş değer herkes için 0.7 "bilinmiyor" çarpanı. **Düzelt:** `ea_league_id`'ye göre tier seed'i (aşağıda), ya da LE export'undan `leagues.leaguelevel` (07). Başlangıç seed'i (Buğra'nın `leagues.csv` id'leriyle):
```json
{"1":2,"4":2,"10":1,"13":1,"14":2,"16":1,"17":2,"19":1,"20":2,"31":1,"32":2,"39":2,"41":3,"50":2,"53":1,"54":2,"56":3,"60":3,"61":4,"65":4,"66":3,"68":2,"80":3,"83":3,"189":3,"308":2,"330":3,"341":2,"350":2,"351":3,"353":2,"2012":3,"2076":3,"2149":4}
```
(1 = top-5, 2 = güçlü ikinci grup, 3-4 = alt. `VERIFY` kalibrasyonu ML ile.)

### BE-BUG-18 · Yaş gerçek tarihe göre, oyun tarihine göre değil
`PlayerSearchRepository.java:48,211`, `MlFeatureBuilder.java:67-72`: ikinci oyun sezonundan sonra `maxAge=21` wonderkid araması yanlış. **Düzelt:** `career.current_game_date` (LE export'tan `context.gameDate` ya da elle), careerId varken yaşı buna göre hesapla.

---
## P2

| ID | Yer | Sorun | Düzeltme |
|---|---|---|---|
| BE-BUG-19 | V11 migration 6-24 | Veri UPDATE'leri (big-five EA lig id, kulüp `ea_team_id`) veri yokken çalışıyor → yeni ortamda kalıcı no-op | Ingestion sonrası çalışan repeatable seed'e taşı |
| BE-BUG-20 | `J/fitengine/model/TeamSetup.java:20-22`, `TacticCodeService.java:189-194` | `depth` 0'a izin veriyor, codec min 1 → sessiz clamp. `DefensiveApproach` modeli yok | Enum + aralıklar (04 RULE-06), import'ta approach etiketi dön |
| BE-BUG-21 | `KnowledgeBaseRepository.java:116,123-133` | Set başına challenge sayısı/listesi ayrı sorgu (N+1). `sbcSet(id)`/`item(id)` sürüm ve statüyü yok sayıyor. 'stub' setler public | Tek gruplu sorgu, `status <> 'RETIRED' AND content_hash <> 'stub'` |
| BE-BUG-22 | `CareerService.java:174-178`, `J/career/desk/SpoilerPolicy.java:24-29` | `mask` ve `ranged` oyuncu başına sorgu, `toResponse` sayım için tüm kadroyu yüklüyor | Spoiler modunu istek başına bir kez oku, `countByCareerId` |
| BE-BUG-23 | `J/ut/kb/ContributorService.java:49,57` | Revoke `banned=true` yapıyor, grant temizliyor → kötüye kullanımdan banlanan kişi grant ile sessizce açılıyor | Ayrı `active` ve `banned` kolonları |
| BE-BUG-24 | `J/roles/seed/ReferenceDataSeeder.java` (~63) | Yalnız upsert, seed'den kalkan rol/preset/kural DB'de kalıyor, Redis rol cache'i (6 sa) temizlenmiyor | Seed'de olmayan id'leri sil (PERSONAL preset hariç), cache evict |
| BE-BUG-25 | `SbcSolverService.java:68-78`, `SbcModels.java:23-25` | Özel rarity tablosunda olmayan rating regular'a düşüyor → monoton değil: TOTW 92 = 20000 ama TOTW 91 = 23750; Icon 96 = 55000 ama Icon 95 = 60000 | Fallback `max(special(r-1), regular(r))` + "rating artınca skor azalmaz" testi. Tablolar 04 RULE-04 |
| BE-BUG-26 | `TacticCodeService.java:158-171` | Aynı pozisyonlu slotlar sağdan sola x'e göre eşleniyor (C3), asimetrik formasyonlarda doğrulanmadı | Asimetrik formasyon başına tek değişiklikli oyun içi test çifti (TAC-01) |

---
## Mimari

### BE-ARCH-01 (P1) · House style importu kodla çelişiyor
`CLAUDE.md:14-18`, `README.md:3` Azerlottery `backend-standard.md`'yi gösteriyor: v2 path, iş hatası HTTP 200, "test ekleme", `/1000` para formatı. Ajan bunu okuyunca yanlış kod üretir. **Düzelt:** `docs/fcareer-standard.md` yaz (constructor injection, Lombok kuralları, `@Transactional` serviste, log placeholder, magic number yok, HTTP status kodları D-09, `/api/v1`, testler zorunlu), `@` importunu kaldır, Azerlottery dosyasını `docs/archive/`'a taşı.

### BE-ARCH-02 (P1) · İçerik takvimi ve saat dilimi yok
Tek zamanlanmış job fiyat job'u. Günlük 18:00, haftalık Perşembe 18:00, sezon 08:00 (Europe/London) sınırlarını hesaplayan hiçbir şey yok. SBC/objective bitişi güvenilmez `ends_at`'e dayanıyor. `Clock.systemDefaultZone()` davranışı host saat dilimine bağlıyor.
**Düzelt:** `ContentCalendarService(ZoneId.of("Europe/London"))`, kurallar `ut-rules.contentCalendar`'dan (04 §2). `ends_at < now` → RETIRED yapan job. `Clock.systemUTC()`. Kredi sıfırlama saat dilimi config (Europe/Istanbul). Detay 05 §5.

### BE-ARCH-03 (P1) · Oyun kurallarının üç kaynağı var
`ut_rules` tablosu hiç yazılmıyor/okunmuyor. `/ut/rules` classpath dosyasını sunuyor. Web kendi kopyasını tutuyor (`src/lib/utRules.fc27.json`) ve `/ut/rules`'u hiç çağırmıyor. Backend'de kimya hesaplayıcı yok.
**Düzelt:** Tek versiyonlu kural dokümanı (tablo; seed dosyadan yüklenir), ETag'le sun, web fetch etsin (paketli kopya yalnız fallback). Backend'de web ile aynı test vektörlerini paylaşan kimya motoru (`ChemistryCalculator`).

### BE-ARCH-04 (P2) · Katmanlama tutarsız
Servis/policy içinde ham JDBC SQL (`ContributorService`, `SpoilerPolicy`, `CareerDeskService`), satır içi tam nitelikli sınıf adları, controller'da mantık (`DevelopmentController` maskeleme, `PlayerController` attribute parse, `UtKnowledgeController.compareVersions`), enum yerine string "kind". **Düzelt:** repository + enum.

### BE-ARCH-05 (P2) · Test boşlukları
Eksik: Testcontainers ile gerçek Redis (Lua sayaç, reuse tespiti, kilit), advisory lock, preview kırpma, spoiler sızıntı yolları, lokalizasyon/konsensüs, fiyat kaynağı filtresi, parser count, çoklu instance ingestion. ML CI "test yok"u tolere ediyor.

### BE-ARCH-06 (P2) · Operasyon
README'nin referans verdiği `docker-compose.yml` yok (yaz: postgres, redis, backend, ml-service iç ağda, ml portu yayımlanmaz). Dockerfile'da HEALTHCHECK ve `-XX:MaxRAMPercentage=75` yok. `ClientHttpRequestFactories`/`ClientHttpRequestFactorySettings.DEFAULTS` Boot 3.4+'ta deprecated (`FaceImageService.java:42-43`, `EaSiteClientConfiguration.java:24-27`). `docs/spec.zip` (= `docs/spec/` kopyası) ve `tools/incareer.png` commit'li, `tools/` altında takip edilmeyen bir ekran görüntüsü var → sil, `.gitignore`'a ekle.

### BE-ARCH-07 (P2) · Veri politikası kodun yapmadığını vaat ediyor
`DELETE /me` ve veri export yok (`docs/DATA_POLICY.md:39`). `career.user_id`'de ON DELETE yok (V1) → kullanıcı silinemez. Bahsedilen `takedown_log` tablosu (`DATA_POLICY.md:52`) yok. **Düzelt:** hesap silme + export uçları, cascade, `takedown_log` + admin ucu.

### BE-ARCH-08 (P2) · Uzun transaction'lar
`ContributionService.contribute` 40M karakter parse ederken tek transaction. `CareerImportService.importData` 150 MB CSV parse ederken tek transaction. **Düzelt:** transaction dışında parse, kısa transaction'larla yaz.

### BE-ARCH-09 (P2) · Public uçlarda cache yok
KB uçlarında Redis cache/ETag yok, yüz cache'i JVM başına. **Düzelt:** `@Cacheable` + `ETag`/`If-None-Match` (içerik hash'inden), `Cache-Control: public, max-age=300` liste uçlarında.

---
## Veri
- **BE-DATA-01 (P1) · `ut_item`'a hiç promo kart girmiyor.** Katkılar yalnız SBC/objective/evolution satırı üretiyor (`ContributionService.java:187-241`). `ut_item`'da yalnız sync-base'den gelen base kartlar var → versiyon şeridi (`versionsOf`, `KnowledgeBaseRepository.java:175-178`) ve promo fiyatı çalışamaz. Model + normalizer: 06 §4.
- **BE-DATA-02 (P2) · Java'ya gömülü sabitler.** Serbest oyuncu lig adı `'Free Agents'` (`PlayerSearchRepository.java:46`; Türkçe export'ta "Serbest" → eşleşmez, id 382 kullan), meta tier'ları, chem-style boost'u kimya seviyesinden bağımsız uygulanıyor; yakalanan `styleAttribMods` (kimya seviyesi başına) yok sayılıyor (`J/ut/service/UtMetaService.java:144-151`).
- **BE-DATA-03 (P2) · Rol id'leri pozisyonu yok sayıyor.** `holding_defend` CDM ve CM için aynı satır, `winger_*` RM ve RW için aynı; EA bunları farklı rol id'leri (örn. 23 vs 35) ve farklı davranışla tutuyor. **Düzelt:** anahtar `(position, role, focus)` ya da pozisyon bazlı kalibrasyon override'ı. Otoriter set: 04 §4.

---
## Endpoint envanteri (`/api/v1`)
**Public:** GET `/players`, `/players/{id}`, `/players/{id}/roles`, `/players/{id}/similar` (`career` param sahiplik ister), `/clubs`, `/leagues`, `/nationalities`, `/roles`, `/behavior-tags`, `/formations`, `/presets`, `/presets/{id}`, `/config/key-attributes`, `/wonderkids`, `/ut/meta/formations`, `/ut/meta/tier`, `/ut/card/{id}/upgrades`, `/ut/sbc/score-table`, `/img/face/{id}`, `/ut/rules`, `/ut/capture/rules`, `/ut/kb/sbc`, `/ut/kb/sbc/{id}`, `/ut/kb/objectives`, `/ut/kb/evolutions`, `/ut/kb/promos`, `/ut/kb/items`, `/ut/kb/items/{id}`, `/ut/kb/items/{id}/prices`, `/tactics/ea-roles`, `/actuator/health` · POST `/auth/register|login|refresh|logout`, `/tactics/resolve`, `/tactics/export-code`, `/tactics/import-code`, `/fit/role`, `/players/compare`, `/recommend/preview` (**BE-SEC-01**), `/players/{id}/development`, `/ut/card/{id}/chemstyle`, `/ut/sbc/streamlined` (**BE-SEC-03**), `/ut/squad/evaluate`.
**JWT:** GET `/me` · POST `/billing/subscribe` · POST `/fit/lineup` (bedava, kırpık), `/fit/setup-check`, `/fit/squad` (ücretli) · POST `/recommend/slot-view` (3), `/recommend/transfer-targets` (ücretsiz), `/recommend/slot` (ücretli), `/scouting/{careerId}/search` · POST `/advisor/squad`, `/advisor/tactic` (ücretli), GET `/advisor/positions/{careerId}` · `/careers` CRUD, `/careers/{id}/squad`, `/events`, `/import` (premium multipart), `/import/squad`, `/teams` · Masa: `/careers/{id}/snapshots`, `snapshots/diff`, `players/{pid}/history`, `players/ratings`, `tags`, `shortlist`, `seasons`, `backup` · UT: `/ut/sbc/votes`, `/ut/capture`, `/ut/capture/link`, `/ut/contrib/capture`.
**Admin (`X-Admin-Key`):** `ingestion/run|enrich-dump|import-base|open-dataset`, `ml/valuations`, `ut/ingestion-runs`, `ut/ea-ids` (+apply), `ut/items/sync-base`, `ut/queue` (+resolve), `ut/contributors/{uid}/grant|revoke`, `ut/prices/aggregate`, PUT `ut/{kind}/{id}`, `ut/promos`.

**Web ↔ backend:** Web'in çağırdığı tüm uçlar var. Uyuşmazlıklar: admin `X-Admin-Key` CORS'ta izinli değil; `KbItem` yalnız iç kulüp/lig/ülke id'si dönüyor (ad yok, görsel URL yok); web `/ut/rules`'u çağırmıyor; backend'de web'in kullanmadığı `/advisor/squad`, `/versions` var. **Hiç olmayan:** CP-SAT `/ut/sbc/solve`, `/ut/squad/complete`, `DELETE /me`, veri export.
