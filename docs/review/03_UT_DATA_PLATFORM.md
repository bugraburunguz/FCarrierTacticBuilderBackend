# 03 · UT VERİ PLATFORMU — extension'sız global kart / SBC / objective / evo / fiyat / görsel
Problem (Buğra): Bugün tüm UT verisi **her kullanıcının** extension ile kendi web-app trafiğini yakalayıp yüklemesine bağlı. futbin / fut.gg / EasySBC'de kullanıcı hiçbir şey kurmadan SBC listesini, yeni kartları, kart görsellerini, fiyatları görüyor. Biz de görmeliyiz.

Bu doküman: (1) bu siteler veriyi nasıl elde ediyor (bilinen/kuvvetle muhtemel), (2) bizim seçeneklerimiz + hukuki risk, (3) önerilen hibrit mimari, (4) şema, (5) görseller, (6) görevler.

---

## 1. GERÇEKÇİ RESİM — rakipler veriyi nereden alıyor
| Veri | Bilinen/muhtemel kaynak | Not |
|---|---|---|
| Base kart statları, yüz | EA'nın herkese açık ratings sitesi + web-app'in **statik içerik CDN'i** (oyuncu listesi JSON, portre PNG'leri) | Bizim `EaSiteProvider` + `faceUrlTemplate` zaten bunu yapıyor (`catalog/configuration/CatalogProperties.java:13`). |
| Özel kartlar (TOTW, promo, Icon, Hero, Evo) | Web-app / companion app içinden **giriş yapılmış hesaplarla** görülen item tanımları + market | Kimlik doğrulamalı EA uçları. |
| SBC / objective / evolution tanımları | Aynı şekilde giriş yapılmış hesap trafiği + editör ekibi + kullanıcı uygulamaları/eklentileri | Bir SBC tanımı **global**dir (her kullanıcı aynı gereksinimi görür) — kişisel veri değil. |
| Fiyatlar | Market araması/izleme yapan hesaplar + kullanıcı eklentilerinden toplanan gözlemler | En maliyetli ve en riskli veri. |
| Kart görselleri | EA CDN'den hotlink/mirror + kendi render ettikleri çerçeveler | |
| SBC çözümleri | Kendi çözücüleri (ILP) + topluluk gönderimi | |

`LEGAL-RISK`: EA hesap kimlik bilgileriyle otomatik (bot) bağlanmak EA Kullanıcı Sözleşmesi'ne aykırıdır, hesap ban'i ve hukuki yaptırım riski taşır. **Bu doküman o yolun uygulama detayını içermez ve önermez.** Ayrıca BUILD_SPEC §17 "EA kimlik bilgisi asla istenmez/loglanmaz" kuralı korunur.

---

## 2. SEÇENEKLER VE KARAR MATRİSİ (✅ 2026-10-09 onaylandı → 08 D-03)
| # | Kaynak | Kapsam | Maliyet | Risk | Öneri |
|---|---|---|---|---|---|
| S1 | **EA public ratings sitesi** (mevcut) | Base kartlar, statlar, PS, AcceleRATE, portre | Düşük (var) | Düşük-orta (resmi API değil, kırılgan) | ✅ Devam; BUG-09 düzeltmeleri |
| S2 | **EA web-app statik içerik** (auth'suz CDN dosyaları: oyuncu adı/id listesi, lokalizasyon, görsel yolları) | İsim↔assetId eşlemesi, portreler, kulüp/lig/ülke rozetleri | Düşük | Orta (`VERIFY`: FC27 yolları; resmi değil) | ✅ Sadece auth gerektirmeyen dosyalar; agresif cache, düşük frekans |
| S3 | **Katkıcı (contributor) programı** — mevcut extension'ı **sadece gönüllü katkıcılar** kullanır; global (kişisel olmayan) tanımlar sunucuda konsensüsle birleşir | SBC set/challenge/gereksinim, objective grupları, evolution tanımları, özel kart tanımları (item definitions), chem-style bonusları, fiyat gözlemleri | Orta | Orta (`LEGAL-RISK` düşük-orta: kullanıcı kendi oturumunu normal kullanıyor, biz kimlik bilgisi görmüyoruz; ama EA verisinin yeniden yayımı) | ✅ **Ana omurga.** Son kullanıcı extension kurmaz; 5–20 aktif katkıcı yeter |
| S4 | **Editör/Admin CMS** — günlük 5–20 yeni SBC/promo'yu elle/yarı-otomatik girme (EA'nın resmi duyuruları, oyun içi ekran) | Her şey (yavaş) | İnsan zamanı | Düşük | ✅ S3'ün yedeği ve doğrulayıcısı |
| S5 | **Üçüncü taraf API/lisans** (ör. FUTDB benzeri açık API'ler, futbin/fut.gg ile veri ortaklığı) | Değişken | Para/ilişki | Lisansa bağlı (`VERIFY` FC27'de hangileri yaşıyor + şartlar) | ⏳ Araştır; ticari ölçekte en temiz yol |
| S6 | Giriş yapılmış bot hesaplarla otomatik çekim | Her şey | Orta | **Yüksek** (`LEGAL-RISK`, ban, ToS) | ❌ Önerilmez |
| S7 | Diğer fan sitelerini scrape etmek | Her şey | Düşük | **Yüksek** (onların ToS'u + telif; BUILD_SPEC "scrape yok") | ❌ |

**Önerilen hibrit:** `S1 + S2` (base katman, otomatik) + `S3` (canlı UT içeriği, konsensüs) + `S4` (admin doğrulama/manuel giriş) + `S5` araştırması paralel.
→ Onaylandı: (a) S3 ✅, (b) veri politikası taslağı `09_DATA_POLICY_DRAFT.md` → `docs/DATA_POLICY.md` ✅, (c) S5 araştırması DATA-12 olarak devam.

---

## 3. MİMARİ — "UT Knowledge Base"
```
            ┌──────────── S1/S2 scheduled ingest (günlük + promo günlerinde saatlik) ────────────┐
            │                                                                                    ▼
Katkıcı extension ──► POST /ut/contrib (imzalı, rate-limit) ──► contrib_raw (TTL 30g, şifreli) ──► NORMALIZER
Admin CMS ─────────────────────────────────────────────────────────────────────────────────┐   │ (UtCaptureService'in
                                                                                             ▼   ▼  parser'ları yeniden kullanılır)
                                                                         candidate_* tabloları (öneri)
                                                                                     │ CONSENSUS (N≥2 bağımsız katkıcı
                                                                                     │  aynı hash  ∨ trusted ∨ admin)
                                                                                     ▼
                     ut_item · ut_promo · card_design · sbc_set · sbc_challenge · objective_group · evolution · price_obs
                                                                                     │
                                       ┌─────────────────────────────────────────────┼──────────────────────┐
                                       ▼                                             ▼                      ▼
                           Public API (cache'li, Redis)                 SBC çözücü / squad builder     Bildirim (yeni SBC/promo)
```
Kurallar:
- **Kişisel veri asla global tabloya girmez:** kulüp envanteri, coin, persona, transfer listesi = kullanıcıya özel (IndexedDB veya `user_*` tablolar). Normalizer'da whitelist (yalnız tanım tipleri: `SBC_HUB`, `SBC_DETAIL`, `OBJECTIVES` tanım kısmı, item definitions, config/styleMods, market **fiyat** alanları — satıcı/alıcı kimliği atılır).
- Katkıcı kimliği: hesap bazlı, IP değil; her katkı `contributor_id` + imzalı token (extension'a gömülü uzun ömürlü anahtar değil, kullanıcıya özel token).
- Konsensüs: içerik `sha256(normalize(json))`; aynı `natural_key` için ≥2 farklı katkıcı aynı hash'i gönderirse `CONFIRMED`; tek katkı = `PENDING` (UI'da "doğrulanmamış" rozetiyle gösterilebilir); çelişki = admin kuyruğu. Trust score: onaylanan katkı +1, çelişen −3; skor eşiği üstü katkıcı tek başına onaylar.
- Mevcut `learnLeagues/learnNations` doğrudan yazımı (BUG-11) bu konsensüs kuyruğuna taşınır.
- Zamanlama: `@Scheduled` + ShedLock. EA içerik saati `VERIFY` (Europe/London 18:00 kabul edilen); promo günlerinde S1/S2 sıklığı artar.

---

## 4. ŞEMA (Flyway `V13__ut_knowledge_base.sql` — taslak)
```sql
-- Kart sürümleri: bir base_player'ın UT'deki her versiyonu (base, TOTW, promo, icon, hero, evo şablonu)
CREATE TABLE ut_promo (
  id            SERIAL PRIMARY KEY,
  game_version_id SMALLINT NOT NULL REFERENCES game_version(id),
  code          TEXT NOT NULL,            -- 'TOTW', 'FUTURE_STARS', 'POTM_EPL' ...
  name          TEXT NOT NULL,
  ea_rarity_id  INT,                      -- web-app rarity id (VERIFY)
  starts_at     TIMESTAMPTZ, ends_at TIMESTAMPTZ,
  card_design_id INT,                     -- görsel tema (aşağıda)
  UNIQUE (game_version_id, code)
);
CREATE TABLE card_design (                -- bizim çizdiğimiz çerçeve/tema; EA görseli DEĞİL
  id SERIAL PRIMARY KEY, code TEXT UNIQUE NOT NULL,
  frame_shape TEXT NOT NULL,              -- 'shield','classic','icon',...
  layers JSONB NOT NULL,                  -- [{type:'linear',stops:[...]}, {type:'pattern',svg:'...'}]
  text_color TEXT NOT NULL, accent_color TEXT NOT NULL, rating_color TEXT NOT NULL,
  foil TEXT NOT NULL DEFAULT 'none'
);
CREATE TABLE ut_item (
  id            BIGSERIAL PRIMARY KEY,
  game_version_id SMALLINT NOT NULL REFERENCES game_version(id),
  ea_asset_id   BIGINT NOT NULL,          -- oyuncu kimliği (base ile ortak)
  ea_resource_id BIGINT NOT NULL,         -- kart versiyonu kimliği (VERIFY alan adı)
  base_player_id BIGINT REFERENCES base_player(id),
  promo_id      INT REFERENCES ut_promo(id),
  rating        SMALLINT NOT NULL,
  position      TEXT NOT NULL, alt_positions TEXT[] NOT NULL DEFAULT '{}',
  club_id INT, league_id INT, nation_id INT,
  card_type     TEXT NOT NULL CHECK (card_type IN ('NORMAL','ICON','HERO','EVO')),
  is_rare       BOOLEAN NOT NULL DEFAULT false,
  face_stats    JSONB NOT NULL,           -- {"PAC":95,...} (GK ayrı anahtarlar)
  attrs         JSONB,                    -- 29+5 detay
  playstyles    JSONB NOT NULL DEFAULT '{}', roles_plus JSONB NOT NULL DEFAULT '{}',
  weak_foot SMALLINT, skill_moves SMALLINT, accelerate TEXT,
  untradeable_only BOOLEAN NOT NULL DEFAULT false,  -- SBC/objective ödülü
  source        TEXT NOT NULL CHECK (source IN ('EA_SITE','EA_STATIC','CONTRIB','ADMIN','PARTNER')),
  status        TEXT NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('PENDING','CONFIRMED','RETIRED')),
  released_at   TIMESTAMPTZ, updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (game_version_id, ea_resource_id)
);
CREATE INDEX ix_item_rating ON ut_item(game_version_id, rating DESC);
CREATE INDEX ix_item_links  ON ut_item(league_id, nation_id, club_id);

CREATE TABLE sbc_set (
  id BIGINT PRIMARY KEY,                  -- EA setId
  game_version_id SMALLINT NOT NULL, name TEXT NOT NULL, description TEXT,
  category TEXT NOT NULL,                 -- PLAYERS/UPGRADES/CHALLENGES/FOUNDATIONS/ICONS/...
  repeatable INT,                         -- null=1 kez, -1=sınırsız
  refresh_interval_hours INT,
  starts_at TIMESTAMPTZ, ends_at TIMESTAMPTZ,
  reward JSONB NOT NULL,                  -- [{type:'item',utItemId:..}|{type:'pack',name:..}|{type:'coins',value:..}]
  status TEXT NOT NULL DEFAULT 'CONFIRMED', content_hash TEXT NOT NULL, updated_at TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE sbc_challenge (
  id BIGINT PRIMARY KEY, set_id BIGINT NOT NULL REFERENCES sbc_set(id),
  name TEXT NOT NULL, formation TEXT, kind TEXT NOT NULL CHECK (kind IN ('SQUAD','ITEM_SCORE')),
  requirements JSONB NOT NULL,            -- SbcChallengeParser çıktısı (normalize): [{kind,op,value,refs[]}]
  item_score_target INT,                  -- streamlined/item-score SBC
  reward JSONB, content_hash TEXT NOT NULL
);
CREATE TABLE objective_group (
  id BIGINT PRIMARY KEY, game_version_id SMALLINT NOT NULL, name TEXT NOT NULL, game_mode TEXT,
  starts_at TIMESTAMPTZ, ends_at TIMESTAMPTZ, reward JSONB, objectives JSONB NOT NULL, content_hash TEXT NOT NULL
);
CREATE TABLE evolution (
  id BIGINT PRIMARY KEY, game_version_id SMALLINT NOT NULL, name TEXT NOT NULL,
  cost JSONB,                             -- coin/points/ücretsiz
  requirements JSONB NOT NULL,            -- {maxOvr, positions[], maxPace, maxPlaystyles, ...}
  levels JSONB NOT NULL,                  -- [{level, tasks[], upgrades:{ovr:+2, attrs:{...}, playstyles:[...], rolesPlus:[...]}}]
  starts_at TIMESTAMPTZ, ends_at TIMESTAMPTZ, content_hash TEXT NOT NULL
);
CREATE TABLE price_obs (                  -- ham gözlem (partition by month)
  ut_item_id BIGINT NOT NULL, platform TEXT NOT NULL CHECK (platform IN ('CONSOLE','PC')),
  observed_at TIMESTAMPTZ NOT NULL, kind TEXT NOT NULL CHECK (kind IN ('BIN','LAST_SALE','BID')),
  value INT NOT NULL, contributor_id BIGINT NOT NULL
) PARTITION BY RANGE (observed_at);
CREATE TABLE price_agg (                  -- saatlik agregasyon (UI bunu okur)
  ut_item_id BIGINT NOT NULL, platform TEXT NOT NULL, bucket TIMESTAMPTZ NOT NULL,
  p10 INT, p50 INT, min_bin INT, samples INT NOT NULL, PRIMARY KEY (ut_item_id, platform, bucket)
);
CREATE TABLE ut_rules (                   -- oyun kuralları versiyonlu (kimya, rating, fiyat aralıkları, chem-style)
  id TEXT PRIMARY KEY,                    -- 'fc27-v1'
  payload JSONB NOT NULL, verified BOOLEAN NOT NULL DEFAULT false, source_note TEXT
);
CREATE TABLE contrib_event (id BIGSERIAL PRIMARY KEY, contributor_id BIGINT NOT NULL, kind TEXT NOT NULL,
  natural_key TEXT NOT NULL, content_hash TEXT NOT NULL, received_at TIMESTAMPTZ DEFAULT now());
CREATE TABLE contributor (id BIGSERIAL PRIMARY KEY, user_id BIGINT UNIQUE REFERENCES app_user(id),
  trust INT NOT NULL DEFAULT 0, banned BOOLEAN NOT NULL DEFAULT false);
```
Fiyat agregasyonu: aykırı değer filtresi (gözlem p50'nin 0.4×–3× aralığı dışı at), tek katkıcının bucket ağırlığı ≤ %30, `samples < 3` ise UI "az veri" gösterir. Fiyatlar EA fiyat adımlarına (`VERIFY` aralık tablosu `ut_rules`'ta) yuvarlanır.

### 4.1 Fiyat kapsamı — bot olmadan nasıl yeterli olur (08 D-24)
22 bin kartın çoğunun fiyatına gerek yok:
- **Satılamaz/ödül kartları** fiyatsızdır (`untradeable_only`).
- **SBC yemi** için tek tek kart fiyatı değil **rating başına taban fiyat** gerekir (75–91 arası ~17 değer × 2 platform). Katkıcı Web App'te "altın, rating X, en düşük fiyat" aramasını elle yaptıkça dolar.
- Gerçekten tek tek fiyat gereken kartlar: meta/özel kartlar (tahmini 1–2 bin). Bunlar için:
  - **DATA-15 "Fiyatı eskiyenler" listesi:** katkıcı panelinde, en çok görüntülenen/kadrolara eklenen ama fiyatı N saatten eski kartlar sıralı. Katkıcı oyuna girmişken birkaçını elle arar, extension yakalar. Otomatik arama yok; tıklayan insan.
  - Tazelik kuralı: meta kart 6 saat, diğer tradeable 48 saat, rating tabanı 3 saat. Süresi geçen fiyat UI'da "~ eski" etiketiyle gösterilir (uydurma taze fiyat yok).
  - Kulüp import'u (kişisel mod) sırasında web-app'in zaten gösterdiği fiyat alanları (varsa) da gözlem olarak kullanılır.
- Kapsam ölçümü: admin panelinde "son 24 saatte fiyatı olan meta kart oranı" metriği; %80 altındaysa katkıcı sayısı artırılır.

---

## 5. GÖRSELLER — oyuncu fotoğrafı + UT kart görünümü
| Katman | Kaynak | Yaklaşım |
|---|---|---|
| Oyuncu yüzü (cutout) | EA ratings / web-app portre CDN (mevcut template) | **Hotlink + bizim image proxy'miz** (`/img/face/{assetId}.webp`): boyut küçültme, webp, 30 gün cache, `referrerPolicy=no-referrer`. Toplu rehost etme. `LEGAL-RISK` orta → veri politikasında yaz, kaldırma talebine 48 saatte uy. |
| Özel kart portresi (promo'ya özel yüz render'ı varsa) | Aynı CDN, item/resource id'ye göre (`VERIFY` FC27 yolu) | Aynı proxy; yoksa base yüze düş. |
| Kart çerçevesi/arka planı | **Bizim `card_design` SVG'lerimiz** (02 §3.1) | EA'nın kart arka plan PNG'leri kopyalanmaz. Promo başına renk/desen admin panelden; renkler EA'nınkine "benzer ama aynı değil". |
| Kulüp/lig/ülke rozeti | Ülke: açık lisanslı bayrak seti (ör. `flag-icons`, MIT); kulüp/lig: EA CDN hotlink (proxy) | Kulüp armaları ticari marka — `LEGAL-RISK`; alternatif: kulüp renklerinden üretilen monogram rozet (offline/fallback). |
| PlayStyle / rol ikonları | **Kendi SVG setimiz** (02 §2.3) | Telif riski yok, tutarlı. |

Önemli: "EA kart görseli kullanılmaz" politikası korunur. Yüzler için proxy-cache (≤ 30 gün, toplu arşiv yok) onaylı → 08 D-04, veri politikası §6.

---

## 6. OYUN KURALLARI CONFIG'İ (`ut_rules` / `seed/ut-rules.fc27.json`)
Kodda dağınık tüm `VERIFY` kurallar tek dosyada, backend ve frontend aynı JSON'u kullanır (frontend `GET /ut/rules`).
Aşağıdaki blok **jsonc** (yorumlar açıklama içindir); gerçek dosyada yorum yerine her bölüme `"_verify": "kaynak/ not"` alanı koy.
```jsonc
{
  "id": "fc27-v1", "verified": false,
  "chemistry": {
    "club":   [2,4,7],  "league": [3,5,8],  "nation": [2,5,8],    // KAYNAK: fifauteam.com/fc-27-chemistry (2026-10-09). Kod doğru.
    "maxPerPlayer": 3, "maxSquad": 33,
    "icon":  { "alwaysMaxInPosition": true, "nationWeight": 1, "allLeaguesWeight": 1 },  // KAYNAK aynı: FC27'de FC26'daki +2 → +1'e düştü
    "hero":  { "alwaysMaxInPosition": true, "leagueWeight": 1, "nationWeight": 1 },     // KAYNAK aynı: FC26 +2 → FC27 +1; Hall of FUT = Hero kuralı
    "manager": { "mode": "FLAT_BONUS", "weight": 1, "maxPerPlayer": 1 },                // KAYNAK aynı: ülke VEYA lig eşleşirse +1, üst üste binmez
    "outOfPosition": 0, "positionEquivalents": { "CF": ["ST"] }                          // VERIFY
  },
  "squadRating": { "size": 11, "formula": "FLOOR_OF_ROUNDED_TOTAL", "testVectors": [
      { "ratings": [84,84,84,84,84,84,84,84,83,83,83], "expected": 83 } ] },               // VERIFY oyun içi
  "contentRefresh": { "tz": "Europe/London", "hour": 18 },                                 // VERIFY
  "priceRanges": "…EA adım tablosu…",                                                      // VERIFY
  "chemStyles": "capture config.styleMods'tan konsensüsle doldurulur",
  "itemScore": "seed/sbc-score.json (mevcut, 'fc27-launch')"
}
```
Kimya bölümü 2026-10-09'da kaynakla doğrulandı (fifauteam.com/fc-27-chemistry) → `chemistry.verified:true`. Squad rating, içerik saati ve fiyat aralıkları hâlâ `VERIFY`.

---

## 7. GÖREVLER (DATA)
| ID | Pri | Görev | Kabul kriteri |
|---|---|---|---|
| DATA-01 | P0 | `09_DATA_POLICY_DRAFT.md`'yi `docs/DATA_POLICY.md` olarak kopyala (onaylı; yer tutucuları Buğra doldurur) | Doküman repoda |
| DATA-02 | P1 | `ut_rules` config + `GET /ut/rules`; `lib/chemistry.ts`, `squadRating`, `coinPrice` refresh saatini config'ten okuyacak şekilde değiştir; backend'de aynı motor | Paylaşılan test vektörleri iki tarafta yeşil |
| DATA-03 | P1 | V13 şeması + JPA entity/repo + manuel mapper | Flyway temiz migrate |
| DATA-04 | P1 | Ingestion'ı job modeline al (BUG-09) + S2 statik içerik provider'ı (`VERIFY` yollar config'te) | Resume testi |
| DATA-05 | P1 | `POST /ut/contrib` + `contributor`/`contrib_event` + normalizer (UtCaptureService parser'larını yeniden kullan, **yalnız whitelist tipler**) | Kişisel alan içeren fixture → hiçbir global tabloya yazılmaz (test) |
| DATA-06 | P1 | Konsensüs servisi + admin onay kuyruğu (`/admin/contrib/queue`) | 2 katkıcı aynı hash → CONFIRMED; çelişki → kuyruk |
| DATA-07 | P1 | Admin CMS (basit React sayfası, `ADMIN` rolü): SBC seti/challenge/promo/evo/card_design ekle-düzenle; EA'nın SBC ekranı metnini yapıştır → parser öneri doldursun | 1 SBC'yi 2 dk'da girebilme |
| DATA-08 | P1 | Public uçlar: `GET /ut/items` (filtre: rating, mevki, lig, ülke, kulüp, promo, PS, fiyat), `/ut/items/{id}` (versiyonlar), `/ut/sbc` (liste+filtre), `/ut/sbc/{setId}`, `/ut/objectives`, `/ut/evolutions`, `/ut/promos` — Redis cache, ETag | Extension olmadan `/ut/sbc` dolu |
| DATA-09 | P1 | Image proxy (`/img/face/{id}`): boyutlandırma, webp, disk/CDN cache, rate limit, 404 fallback siluet | — |
| DATA-10 | P2 | Fiyat pipeline'ı: `price_obs` partition + saatlik agregasyon job'u + aykırı filtre + `GET /ut/items/{id}/prices?platform=&range=` | Grafik verisi |
| DATA-11 | P2 | Extension'ı iki moda ayır: **Kişisel** (kulüp import — bugünkü akış) ve **Katkı** (opt-in, tanım+fiyat). Extension repo'su ayrı; `docs/ut_capture_mapping.md` yaz (eksik referans) | — |
| DATA-12 | P2 | S5 araştırması: FC27'de çalışan açık/ücretli UT veri API'leri ve şartları; tablo halinde Buğra'ya | Karar dokümanı |
| DATA-13 | P2 | Bildirimler: yeni promo/SBC → web push + (ops.) Discord webhook | — |
