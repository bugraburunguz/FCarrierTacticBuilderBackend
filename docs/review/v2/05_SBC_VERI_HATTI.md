# 05 · SBC VERİ HATTI — SBC'leri detaylarıyla çekmek, her gün yenilemek
Soru (Buğra): "FC27 SBC görevlerini detaylarıyla birlikte nasıl çekebiliriz, her gün saat 8'de yenileniyor."
Kısa cevap: **EA'nın SBC verisi için giriş gerektirmeyen bir kaynağı yok.** SBC set/challenge/gereksinim/ödül verisi yalnız EA Web App'e giriş yapmış bir oturumun EA'dan aldığı yanıtlarda bulunuyor. Onaylı yol (D-03, D-18, D-20): **katkıcıların extension'ı** bu yanıtları normal gezinirken pasif yakalar → backend yapı-hash'i + konsensüsle birleştirir → herkes extension'sız görür. Yedek: **admin CMS**. Hepsi Europe/London 18:00 takvimine bağlanır.

---
## 1. YENİLEME SAATİ — "saat 8" = akşam 20:00 (kışın 21:00)
EA günlük içeriği **18:00 İngiltere saatiyle** bırakır (`VERIFY` FC27 için; haftalık objective'lerin Perşembe 18:00 UK sıfırlandığı yüklenen rehberde yazılı). Türkiye'de yaz saati uygulaması yok (hep UTC+3), İngiltere'de var. Bu yüzden TR saati yılda iki kez değişir:

| Dönem | 18:00 Londra | İstanbul |
|---|---|---|
| … **Cmt 2026-10-24**'e kadar (BST) | 17:00 UTC | **20:00** |
| **Paz 2026-10-25** … Cmt 2027-03-27 (GMT) | 18:00 UTC | **21:00** |
| Paz 2027-03-28 … (BST) | 17:00 UTC | 20:00 |

**Kural:** Kodda asla "20:00" yazma. Her zaman `ZonedDateTime.of(date, LocalTime.of(18,0), ZoneId.of("Europe/London"))` hesapla, UTC sakla, `Europe/Istanbul` göster. Kaynak: `ut-rules.contentCalendar` (04 §2).

Diğer takvim noktaları (hepsi 18:00 UK, `VERIFY` FC27): Çarşamba TOTW, Perşembe haftalık objective (doğrulandı) + Marquee Matchups, Cuma yeni promo. Sezon sınırları 08:00 UK (04 §2'deki tarihler). Squad Battles haftası Pazar→Pazar, ödül Pazar 08:05 UK.

---
## 2. EA NE DÖNDÜRÜYOR (açık kaynaktan, `VERIFY`)
Hepsi `X-UT-SID` oturum başlığı ister → **bizim sunucumuz bunları asla çağırmaz** (D-03 S6 reddi). Yalnız katkıcının tarayıcısının zaten yaptığı isteklerin yanıtları yakalanır.

| Uç (taban `https://{utas}/ut/game/fc27/`) | İçerik | Kaynak |
|---|---|---|
| `GET sbs/sets` | Kategoriler + tüm setler (id, ad, açıklama, `endTime`, `repeatabilityMode`, `repeats`, `challengesCount`, ödüller, kategori, görsel asset id) | github.com/futapi/fut `fut/core.py` (~1396-1615) |
| `GET sbs/setId/{setId}/challenges` | Setin challenge'ları (id, ad, `formation`, `elgReq`/`eligibilityRequirements`, `elgOperation`, ödüller, `status`, streamlined için `scoreRequirement`) | aynı |
| `GET sbs/challenge/{id}/squad` | O challenge'daki kullanıcının kadrosu (**kişisel, yakalama**) | aynı |
| `GET user/dynamicobjectives?scope=all` | Objective grupları (tanım kısmı global, ilerleme kişisel) | aynı |
| Streamlined uygun-item yanıtı | Her item için `sbsScore` | github.com/icysymmetra/AutoPilot-SBC `page/ea-data-bridge.js`, `solver/points-solver.js` ("skoru OVR'den türetme, EA yanıtından al") |

Yakalanmayacak, kişisel alanlar: `timesCompleted`, set/challenge `status`, kullanıcının kadrosu, persona, coin. Extension katkı modunda bunları göndermeden önce siler, sunucu da siler (EXT-11).

SBC görseli (`VERIFY`): `https://www.ea.com/ea-sports-fc/ultimate-team/web-app/content/{GUID}/2027/fut/sbc/companion/challenges/images/sbc_challenge_image_{assetId}.png`. `{GUID}` sabit değil, sezon/patch'le değişebilir → extension sayfadaki herhangi bir `img[src*="/content/"]`'den GUID'i okuyup `capture-rules` keşif kanalıyla raporlar (EXT-06), backend config'te tutar.

### 2.1 Gereksinim anahtarları (EA `eligibilityKey` enum'u)
Kaynak: AutoPilot-SBC `LOCAL_SBC_ELIGIBILITY_KEY_ENUM` (FC27 v1.12.3). `VERIFY`: değerleri FC27 capture'ıyla teyit et.
```
0 TEAM_STAR_RATING   2 PLAYER_COUNT      3 PLAYER_QUALITY     4 SAME_NATION_COUNT  5 SAME_LEAGUE_COUNT
6 SAME_CLUB_COUNT    7 NATION_COUNT      8 LEAGUE_COUNT       9 CLUB_COUNT        10 NATION_ID
11 LEAGUE_ID        12 CLUB_ID          13 SCOPE             15 LEGEND_COUNT      16 NUM_TROPHY_REQUIRED
17 PLAYER_LEVEL     18 PLAYER_RARITY    19 TEAM_RATING       21 PLAYER_COUNT_COMBINED
25 PLAYER_RARITY_GROUP  26 PLAYER_MIN_OVR  27 PLAYER_EXACT_OVR  28 PLAYER_MAX_OVR
30 FIRST_OWNER_PLAYERS_COUNT  33 PLAYER_TRADABILITY  35 CHEMISTRY_POINTS  36 ALL_PLAYERS_CHEMISTRY_POINTS
-1 PLAYERS_IN_SQUAD
Scope: 0 MIN · 1 MAX · 2 EXACT · 3 RANGE
```
Ham şekil: `eligibilityRequirements[] = { scope, count, kvPairs: { <key>: [values] } }` (Auto-SBC FC26). Örnek: `PLAYER_RARITY_GROUP=[23], count=1, scope=MIN` = "en az 1 TOTW". `PLAYER_QUALITY` değerleri 1=bronz, 2=gümüş, 3=altın (`VERIFY`).

### 2.2 Normalize gereksinim (parser çıktısı, BE-BUG-05)
```json
{ "kind": "RARITY_GROUP", "op": "MIN", "count": 1, "values": [23], "refs": [{"type":"rarityGroup","id":23,"label":"TOTW"}], "raw": { "...": "ham elgReq" } }
```
`kind` listesi: `TEAM_RATING, TEAM_STAR_RATING, CHEMISTRY_TOTAL, CHEMISTRY_PER_PLAYER, PLAYER_COUNT, QUALITY, RARITY, RARITY_GROUP, SAME_NATION, SAME_LEAGUE, SAME_CLUB, NATION_COUNT, LEAGUE_COUNT, CLUB_COUNT, NATION_ID, LEAGUE_ID, CLUB_ID, MIN_OVR, MAX_OVR, EXACT_OVR, FIRST_OWNER, TRADABILITY, LEGEND_COUNT, UNKNOWN`. `UNKNOWN` gelirse ham hâli sakla, admin kuyruğuna "yeni anahtar" uyarısı düşür.

---
## 3. ŞEMA (mevcut V13 `sbc_set`/`sbc_challenge`'a eklenecekler)
Flyway `V15__sbc_pipeline.sql`:
```sql
ALTER TABLE sbc_set
  ADD COLUMN ea_category_id INT,
  ADD COLUMN category_name TEXT,
  ADD COLUMN image_asset_id BIGINT,
  ADD COLUMN repeatability_mode TEXT CHECK (repeatability_mode IN ('NON_REPEATABLE','FINITE','UNLIMITED')),
  ADD COLUMN repeat_count INT,
  ADD COLUMN repeat_refresh_interval_hours INT,
  ADD COLUMN challenges_count INT,
  ADD COLUMN released_at TIMESTAMPTZ,
  ADD COLUMN first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ADD COLUMN last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  ADD COLUMN ends_at_source TEXT CHECK (ends_at_source IN ('CAPTURE_MEDIAN','ADMIN','UNKNOWN')),
  ADD COLUMN is_new BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE sbc_challenge
  ADD COLUMN ea_type TEXT CHECK (ea_type IN ('TRADITIONAL','STREAMLINED')),
  ADD COLUMN elg_operation TEXT,
  ADD COLUMN score_requirement INT,
  ADD COLUMN selection_limit INT,
  ADD COLUMN sort_order INT,
  ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN ends_at TIMESTAMPTZ;
CREATE TABLE sbc_requirement (
  challenge_id BIGINT NOT NULL REFERENCES sbc_challenge(id) ON DELETE CASCADE,
  slot SMALLINT NOT NULL, kind TEXT NOT NULL, op TEXT NOT NULL CHECK (op IN ('MIN','MAX','EXACT','RANGE')),
  count INT, values INT[] NOT NULL DEFAULT '{}', raw JSONB NOT NULL,
  PRIMARY KEY (challenge_id, slot)
);
CREATE TABLE sbc_award (
  id BIGSERIAL PRIMARY KEY, owner_type TEXT NOT NULL CHECK (owner_type IN ('SET','CHALLENGE')), owner_id BIGINT NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('ITEM','PACK','COINS','PICK','OTHER')),
  value BIGINT, count INT NOT NULL DEFAULT 1, untradeable BOOLEAN, ea_resource_id BIGINT, pack_name TEXT
);
CREATE TABLE sbc_set_version (       -- değişiklik geçmişi, sessiz ezme yok
  set_id BIGINT NOT NULL, version INT NOT NULL, content_hash TEXT NOT NULL, payload JSONB NOT NULL,
  seen_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (set_id, version)
);
CREATE TABLE kb_text (               -- lokalize ad/açıklama hash'ten ayrı (BE-BUG-07)
  kind TEXT NOT NULL, natural_key TEXT NOT NULL, locale TEXT NOT NULL, name TEXT, description TEXT,
  PRIMARY KEY (kind, natural_key, locale)
);
CREATE TABLE sbc_solution_cache (    -- çözücü sonuçları, kullanıcıya göre değil global market havuzuna göre
  challenge_id BIGINT NOT NULL, platform TEXT NOT NULL, computed_at TIMESTAMPTZ NOT NULL, cost INT, squad JSONB NOT NULL,
  PRIMARY KEY (challenge_id, platform)
);
CREATE INDEX ix_sbc_set_active ON sbc_set(game_version_id, status, ends_at);
```
`content_hash = sha256(canonical({requirements, awards, scoreRequirement, repeatabilityMode, repeatCount, formation, challenges:[ids]}))`. **Adlar, açıklamalar, `endTime` hash'e girmez.**

---
## 4. HAT (pipeline)
```
Katkıcı tarayıcısı (EA Web App, kendi oturumu)
  └─ Extension KATKI modu: sbs/sets, sbs/setId/*/challenges, dynamicobjectives, streamlined eligible yanıtı
       • kişisel alanları sil (timesCompleted, status, squad, persona)
       • {capturedAt, extVersion, rulesVersion, locale, body}
  └─ POST /ut/contrib/capture   (X-Link-Token, mode=CONTRIB)            ← BE-SEC-10
Backend
  1. Şema doğrula (JSON Schema per endpoint), boyut sınırı (BE-SEC-02)
  2. Normalize (SbcChallengeParser v2) → set, challenge, requirement, award, text(locale)
  3. ends_at = capturedAt + endTime(saniye)   → set başına katkıcılar arası medyan, dakikaya yuvarla
  4. Konsensüs: D-20 trusted katkıcı tek başına CONFIRMED; trusted değilse ≥2 farklı katkıcı aynı hash
  5. Upsert:  INSERT … ON CONFLICT (game_version_id, id) DO UPDATE
               SET … WHERE sbc_set.content_hash <> EXCLUDED.content_hash
     • hash değiştiyse sbc_set_version'a satır + SBC_CHANGED olayı
     • hash aynıysa yalnız last_seen_at / ends_at güncelle                ← BE-BUG-06
  6. Olaylar: SBC_NEW, SBC_CHANGED, SBC_EXPIRING_24H, SBC_EXPIRED → bildirim kuyruğu
```

### 4.1 Zamanlayıcı (Spring, hepsi DB kilidiyle — BE-SEC-04)
```java
@Scheduled(cron = "0 0 18 * * *", zone = "Europe/London")        // günlük drop penceresi açılır
void openDropWindow()        { calendar.openWindow(today()); }
@Scheduled(cron = "0 5,15,30 18 * * *", zone = "Europe/London")
@Scheduled(cron = "0 0 19 * * *",        zone = "Europe/London")
void dropWatch()             { if (!kb.hasNewSince(windowStart())) alerts.notifyContributors("Yeni SBC bekleniyor: SBC sekmesini aç"); }
@Scheduled(fixedDelay = 300_000)                                   // 5 dk
void expireSweep()           { kb.retireWhere("ends_at < now()"); kb.retireNotSeenFor(Duration.ofHours(48)); }
@Scheduled(cron = "0 0 18 * * THU", zone = "Europe/London")
void weeklyObjectives()      { calendar.rollWeek(); }
@Scheduled(cron = "0 0 8 * * *",  zone = "Europe/London")
void seasonBoundary()        { calendar.rollSeasonIfNeeded(); }
```
Tarih/saat değerleri kodda değil, `ut-rules.contentCalendar`'dan okunur. Cron ifadesi config'e alınabilir (`fcareer.calendar.daily-cron`).

### 4.2 Katkıcı rozeti (DN-03: pasif kalır)
Drop penceresinden 5-30 dk sonra veri gelmediyse extension popup'ı ve site rozet gösterir: "Yeni SBC'ler düştü. EA Web App'te SBC sekmesini açman yeterli." Extension EA'ya **kendi isteğini atmaz**, sekme açmaz. Kullanıcı tıklar, EA sayfası kendi isteğini atar, extension yanıtı görür.

### 4.3 Admin CMS yedeği (S4)
Katkıcı yoksa ya da çelişki varsa: admin `/admin/ut/sbc/new` sayfasında EA ekranındaki metni yapıştırır → `sbcText.ts`'in sunucu kopyası (parser) gereksinimleri önerir → admin düzeltir → `source='ADMIN'`, `ends_at_source='ADMIN'`. Hedef: 1 SBC < 2 dk.

### 4.4 Bildirim
`notification_subscription(user_id, kind, filter jsonb)`: "yeni SBC", "favori setim değişti", "24 saat kaldı". Kanallar: web push (VAPID), e-posta (opsiyonel), Discord webhook (DATA-13). Tekilleştirme anahtarı `(user, set_id, event)`.

### 4.5 Streamlined (skor) SBC'ler
- `score_requirement` ve item başına `sbsScore` EA yanıtından gelir. `ut-rules.sbcScore` yalnız EA skoru bilinmediğinde tahmin ve gösterim içindir.
- Çözücü (`/ut/sbc/streamlined`): kullanıcının kulüp kartlarından (IndexedDB) en ucuz skor kombinasyonu; kulübünde yoksa market rating-taban fiyatı (D-24 §4.1). Login + rate limit (BE-SEC-03).
- UI: kalan skor barı, "kulübünden şu 6 kartı ver" listesi, kısmi ilerleme (EA kısmi gönderime izin veriyor).

---
## 5. API (web'in kullanacağı, `/ut/kb/*` mevcut adlarla)
| Uç | Dönüş | Not |
|---|---|---|
| `GET /ut/kb/sbc?category=&status=ACTIVE&sort=new\|expiring\|cost&q=` | `[{id, name, category, imageUrl, endsAt, repeatability, challengesCount, reward[], isNew, estCost{console,pc}, voteUp/Down}]` | ETag + `max-age=60` |
| `GET /ut/kb/sbc/{setId}` | set + challenges + requirements (normalize + label) + awards | |
| `GET /ut/kb/sbc/{setId}/history` | `sbc_set_version` listesi | "değişti" rozeti için |
| `GET /ut/kb/calendar` | `{now, nextDailyDrop, nextWeekly, season:{n,start,end}, tzDisplay:"Europe/Istanbul"}` | Sayaçlar için |
| `POST /ut/sbc/{challengeId}/solve` (JWT) | Geleneksel: beam (web worker) şimdilik, uzun vadede CP-SAT (D-11) | |

---
## 6. SEÇENEKLER ve RİSK
| Seçenek | Kapsam | Tazelik | Efor | Risk | Durum |
|---|---|---|---|---|---|
| **S3 katkıcı extension (pasif)** | Tam (gereksinim, ödül, skor) | Katkıcı aktifse dakikalar | Orta | Düşük-orta (katkıcının hesabı için gri alan; biz istek atmıyoruz) | **Önerilen omurga** (D-03/D-20) |
| S4 admin CMS (EA X/@EASFCDirect, ea.com haberleri, oyun içi ekran) | Kısmi, elle | Saatler | Sürekli insan emeği | Düşük | Yedek |
| S5 lisanslı API (FutDB vb.) | FC27 desteği `VERIFY` | ? | Düşük | Şartlar izin verirse düşük | DATA-12 araştırması: Buğra futdb.app'e bakmalı |
| S5 ortaklık (futbin/fut.gg/EasySBC) | Tam | Dakikalar | İş görüşmesi | Sözleşmeyle düşük | Ticari ölçekte |
| Fan sitesi API'lerini çekmek | Tam | Dakikalar | Düşük-orta | **Yüksek** (ToS, IP engeli, D-03 S7) | **Reddedildi** (DN-02) |
| Bot hesapla EA'ya giriş | Tam | Gerçek zamanlı | Orta | **Çok yüksek** (EA ToS, ban) | **Reddedildi** |

**Uyarı:** npm `@aphrody/fut` paketi (2026-09-17) içinde kimlik bilgisiyle giriş yapan bot, otomatik 2FA, "antiDetection" modülü ve gömülü cookie dosyaları var; "resmi EA FC Community API (Temmuz 2026)" iddiası doğrulanmamış. **Bağımlılık olarak ekleme, kodunu kopyalama.**

---
## 7. GÖREVLER
| ID | Pri | Görev | Kabul |
|---|---|---|---|
| SBC-01 | P1 | `SbcChallengeParser` v2: tam enum, `{kind,op,count,values,refs,raw}`, ana tip + PLAYER_COUNT birlikte (BE-BUG-05) | Fixture: rarity grubu + count 1 → doğru normalize |
| SBC-02 | P1 | V15 migration (§3) + entity/repo | Flyway temiz |
| SBC-03 | P1 | Yapı-hash'i + `kb_text` (BE-BUG-07) | TR ve EN capture aynı hash |
| SBC-04 | P1 | Ödül, skor hedefi, tekrar sayısı payload'a (BE-BUG-06); `sbc_award` dolumu | Set ödülü boş değil |
| SBC-05 | P1 | `ends_at` medyanı capture zamanından; hash aynıysa `last_seen_at`/`ends_at` güncelle | 3 katkıcı farklı saniyelerle → tek dakikalık `ends_at` |
| SBC-06 | P1 | `ContentCalendarService` + §4.1 job'ları + DB kilidi | Testte `Clock` sabitlenip BST/GMT geçişinde doğru UTC |
| SBC-07 | P1 | Expire sweep + 48 sa görülmeyen → RETIRED | `ends_at` geçmiş set listede yok |
| SBC-08 | P1 | `sbc_set_version` + SBC_CHANGED olayı | Gereksinim değişince versiyon 2 |
| SBC-09 | P2 | Bildirim aboneliği + web push + Discord webhook | Yeni set → push |
| SBC-10 | P2 | Admin CMS "metin yapıştır → öneri" | 1 SBC < 2 dk |
| SBC-11 | P2 | Extension: katkı modu MATCH listesine `sbs/*`, `dynamicobjectives`, streamlined eligible; kişisel alan temizliği (EXT-10/11); GUID keşfi (EXT-06) | Kişisel alan içeren fixture global tabloya yazılmaz |
| SBC-12 | P2 | Web: SBC liste kartları + geri sayım + "değişti/yeni" rozeti + kalan skor barı (08 §4) | Ekran onayı |
