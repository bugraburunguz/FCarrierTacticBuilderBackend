# FC CAREER PLATFORM — BİRLEŞİK BUILD SPEC (koda hazır) · v1.0
Offline EA FC kariyer/menajerlik companion web uygulaması. Bu dosya **tek başına kodlamaya başlanabilir**
şekilde yazıldı. Büyük seed veriler companion dosyalarda (aşağıda manifest).

## 0. BU SPEC NASIL KULLANILIR
- **Proje konumları (kesin):**
  - Local kök: `/Users/user/Desktop/Projects/FCarrierTacticBuilder` — iki repo bunun altına klonlanır.
  - **Backend repo (private):** `https://github.com/bugraburunguz/FCarrierTacticBuilderBackend.git` → `...\FCarrierTacticBuilder\FCarrierTacticBuilderBackend` (Java/Spring Boot; Python ML servisi `/ml-service` alt klasörü).
  - **Web repo (private):** `https://github.com/bugraburunguz/FCarrierTacticBuilderWeb.git` → `...\FCarrierTacticBuilder\FCarrierTacticBuilderWeb` (React + TypeScript).
  - Docker Compose local kökte (`docker-compose.yml`), iki repoyu + pg/redis/ml'i ayağa kaldırır.
- **Git / commit:** Kod ajanı, makinedeki **mevcut git kimliğiyle (Buğra'nın user'ı) commit atar ve bu private repolara push eder.** Gerekirse `git config user.name "Buğra Emin Bürüngüz"` + `user.email` Buğra'nın GitHub e-postası. Anlamlı, küçük commit'ler (Conventional Commits öneri: `feat:`, `fix:`, `refactor:` — refactor'da davranış değişikliği commit mesajında açıkça belirtilir).
- **Hâlâ gerekli (Buğra verecek):** `{{DB_URL}}` (Postgres bağlantısı) ve `{{SAMPLE_EXPORT_PATH}}` (premium import parser için — bir mod aracından dışa aktarılmış **örnek CSV/SQLite export dosyası**; §11. Tek gerçek blocker.).
- **NOT:** Bu sohbet Buğra'nın GitHub'ına veya C: diskine erişemez; klonlama/build/commit/push işlerini **kod ajanı (Claude Code vb.) Buğra'nın makinesinde** yapar.
- **Companion seed dosyaları (bununla birlikte Sonnet'e verilecek):**
  `fit_engine_spec.md` · `behavior_tag_library.md` · `preset_and_replica_library.md` · `sofifa_scout_links.md` · `career-platform_PHASE-0.md`.
- **UNKNOWN/CONFIG** etiketli her şey flag/config olarak implemente edilir — **uydurma sabit yok**, varsayılan + "calibrate" notu.
- Dil: kod/identifier İngilizce; kullanıcıya görünen metinler Türkçe (i18n hazır).

## 1. ÜRÜN ÖZETİ
SoFIFA/cmtracker bir DB+hesaplayıcı; biz üstüne **taktik-zekâ katmanı** koyuyoruz: kullanıcı oynamak istediği
futbolu **davranışlarla** seçer → motor rolü/taktiği türetir → kadrosunun uyumunu (RoleFit/IntentFit/SquadFit)
**sebepleriyle** gösterir, zayıf halkaları + öneri + atak paternini açıklar. Hedef: offline kariyer oynayanlar.

## 2. TECH STACK
- Backend: **Java 17 + Spring Boot 3.x + Jakarta** (web, data-jpa, security, validation), **Lombok**, **Maven**. (Buğra'nın house-style baseline'ı.)
- DB: **PostgreSQL 16** (+ Flyway migration). Cache: **Redis** (hot read + rate-limit).
- ML servis: **Python 3.11 + FastAPI** (potential/value modeli; XGBoost/sklearn). Backend ↔ ML REST.
- Optimizasyon: Hungarian atama (Java: kendi impl / basit kütüphane; §8).
- **Kafka/OpenFeign:** Buğra'nın iş stack'inde var ama bu ürün tek backend + ML (REST). Gerekmedikçe EKLENMEZ (house-style §1.6: gereksiz teknoloji yok).
- Frontend: **React + TypeScript + Vite + TailwindCSS**. State: TanStack Query.
- Auth: JWT (access+refresh), bcrypt. Ödeme: provider-abstraction (MVP'de stub).
- Deploy: Docker Compose (app, pg, redis, ml). Repo kökü: `/Users/user/Desktop/Projects/FCarrierTacticBuilder`.

## 3. MİMARİ (katmanlar)
```
EA drop-api + Açık Datasetler + ML Model + Save-Import
   → Data Ingestion (provider-abstraction) → Catalog (base_player/rating/valuation)
   → Role & Tactic Layer (role lib, behavior tags, presets/replicas)
   → Fit Engine (RoleFit · IntentFit · SquadFit · RulesEngine · NLG)
   → User Career Layer (career, user_player_state, roster_event — event-sourced)
   → Subscription/Credit Layer → FREE + PREMIUM Product (+ Ads flag)
```
Modüller (Spring): `catalog`, `ingestion`, `roles`, `tactics`, `fitengine`, `career`, `recommendation`, `auth`, `billing`, `ml-client`.

## 4. VERİ KAYNAKLARI & PIPELINE
| Veri | Kaynak | Statü | Not |
|---|---|---|---|
| OVR/attribute/pozisyon/PlayStyle/fiziksel | EA **drop-api** (resmi public) | VERIFIED | potential/value YOK |
| potential/value (global/FREE) | **Açık GitHub** (mzafram2001/ea-fc, fc26-player-analysis) + **kendi modelimiz** | VERIFIED veri / model | §5 |
| potential/value (PREMIUM, gerçek) | **save import (SQLite)** | DECISION | §11 |
**Yasal çizgi (DB'ye gömülü):** public sorgu yalnızca `valuation.source='MODELED'`. EA'nın gerçek potential'ı
sadece `source='IMPORT'` + ilgili user'a private. **Scrape yok** (SoFIFA/FUTBIN/cmtracker). Açık set lisansı: CONFIG (teyit).
Pipeline: `IngestionJob` → drop-api pull → upsert `base_player`+`player_rating`; açık dataset CSV → `player_valuation(source=MODELED-SEED)`; ML model → `valuation(source=MODELED)`. Provider interface: `ExternalDataProvider` + `MockProvider` (dev).

## 5. POTENTIAL / VALUE MODELİ (ML servis)
**Potential** (regresyon, hedef 0-99):
- Features: `age, overall, position_group, league_tier, intl_reputation, pace,shooting,passing,dribbling,defending,physical (grup ort.), weak_foot, skill_moves, is_gk`.
- Model: **XGBoost regressor**. Train: açık dataset (çok-yıllı). Split: stratified by age-band.
- Metrik: **MAE** (genel + **U21 kohortu ayrı** — asıl para orada). Hedef U21 MAE ≤ ~3.
- **FC27 kalibrasyonu (UNKNOWN):** FC27 potential sistemi değişti (yaşlıda yüksek pot). FC27 verisi geldiğinde ayrı model/offset; o ana dek FC26 modeli + `fc27_pot_flag`.
- Dürüst sınır: EA'nın el-ayarlı/rastgele bileşeni → indirgenemez hata; çıktı "tahmin" olarak etiketlenir.

**Value** (daha deterministik):
- Features: `overall, potential, age, league_tier, intl_reputation, contract_years`. Model: gradient boosting / kapalı-form regresyon. MAE düşük beklenir.
- Her ikisi `player_valuation(source='MODELED', model_version)` olarak yazılır.

ML API: `POST /ml/predict` {players[]} → {player_id, potential, value, model_version}. Batch + cache.

## 6. VERİTABANI ŞEMASI (Postgres DDL — Flyway V1)
```sql
-- ===== Katalog (immutable base) =====
CREATE TABLE game_version (
  id            SMALLINT PRIMARY KEY,          -- 27 = FC27
  code          TEXT NOT NULL UNIQUE,          -- 'FC27'
  released_on   DATE
);
CREATE TABLE nationality (id SERIAL PRIMARY KEY, name TEXT UNIQUE NOT NULL);
CREATE TABLE league (id SERIAL PRIMARY KEY, name TEXT NOT NULL, country TEXT, tier SMALLINT);
CREATE TABLE club (id SERIAL PRIMARY KEY, name TEXT NOT NULL, league_id INT REFERENCES league(id));

CREATE TABLE base_player (
  id            BIGSERIAL PRIMARY KEY,
  ea_resource_id BIGINT,                       -- drop-api id (sürümler arası eşleme)
  full_name     TEXT NOT NULL,
  dob           DATE,
  nationality_id INT REFERENCES nationality(id),
  height_cm     SMALLINT, weight_kg SMALLINT,
  preferred_foot TEXT CHECK (preferred_foot IN ('Left','Right')),
  UNIQUE (ea_resource_id)
);

CREATE TABLE player_rating (                   -- game_version başına stat snapshot (drop-api)
  id            BIGSERIAL PRIMARY KEY,
  base_player_id BIGINT NOT NULL REFERENCES base_player(id),
  game_version_id SMALLINT NOT NULL REFERENCES game_version(id),
  club_id       INT REFERENCES club(id),
  overall       SMALLINT NOT NULL,
  positions     TEXT[] NOT NULL,               -- ['RW','RM']
  accelerate    TEXT,                           -- türetilmiş arketip
  weak_foot     SMALLINT, skill_moves SMALLINT,
  intl_reputation SMALLINT,
  att_wr TEXT, def_wr TEXT, body_type TEXT,
  -- 35 attribute (scout filtreleri için indekslenebilir kolonlar)
  acceleration SMALLINT, sprint_speed SMALLINT, agility SMALLINT, balance SMALLINT,
  reactions SMALLINT, ball_control SMALLINT, dribbling SMALLINT, composure SMALLINT,
  vision SMALLINT, short_passing SMALLINT, long_passing SMALLINT, crossing SMALLINT,
  curve SMALLINT, fk_accuracy SMALLINT, finishing SMALLINT, shot_power SMALLINT,
  long_shots SMALLINT, volleys SMALLINT, penalties SMALLINT, att_position SMALLINT,
  heading SMALLINT, def_awareness SMALLINT, standing_tackle SMALLINT, sliding_tackle SMALLINT,
  interceptions SMALLINT, jumping SMALLINT, stamina SMALLINT, strength SMALLINT, aggression SMALLINT,
  gk_diving SMALLINT, gk_handling SMALLINT, gk_kicking SMALLINT, gk_positioning SMALLINT, gk_reflexes SMALLINT,
  playstyles JSONB NOT NULL DEFAULT '{}',       -- {"FinesseShot":1,"QuickStep":2}  (2=PS+)
  UNIQUE (base_player_id, game_version_id)
);
CREATE INDEX ix_rating_gv ON player_rating(game_version_id);
CREATE INDEX ix_rating_pos ON player_rating USING GIN (positions);
CREATE INDEX ix_rating_ps ON player_rating USING GIN (playstyles);
-- scout filtreleri için sık kullanılan weapon kolonlarına btree:
CREATE INDEX ix_rating_fin ON player_rating(finishing);
CREATE INDEX ix_rating_vis ON player_rating(vision);
CREATE INDEX ix_rating_cro ON player_rating(crossing);
CREATE INDEX ix_rating_acc ON player_rating(acceleration);

CREATE TABLE player_valuation (
  id BIGSERIAL PRIMARY KEY,
  base_player_id BIGINT NOT NULL REFERENCES base_player(id),
  game_version_id SMALLINT NOT NULL REFERENCES game_version(id),
  source TEXT NOT NULL CHECK (source IN ('MODELED','IMPORT','THIRD_PARTY_SEED')),
  owner_user_id BIGINT,                          -- IMPORT ise private sahibi; MODELED ise NULL
  potential SMALLINT, value_eur BIGINT, wage_eur BIGINT,
  model_version TEXT,
  created_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (base_player_id, game_version_id, source, owner_user_id)
);
CREATE INDEX ix_val_public ON player_valuation(game_version_id) WHERE source='MODELED';

-- ===== Roller & Taktik =====
CREATE TABLE role (
  id TEXT PRIMARY KEY,                -- 'inside_forward_attack'
  name TEXT NOT NULL, position TEXT NOT NULL, focus TEXT NOT NULL,
  attr_weights JSONB NOT NULL,        -- {"Finishing":0.20,...}  Σ=1
  weapons JSONB NOT NULL,             -- [{"attr":"Finishing","hardMin":66,"target":78},...]
  playstyles JSONB NOT NULL,          -- {"priority":[...],"bonus":[...]}
  accelerate TEXT[] NOT NULL,
  global_floor JSONB NOT NULL DEFAULT '{"Stamina":68,"Reactions":60,"Composure":60}',
  movement_tags TEXT[] NOT NULL DEFAULT '{}'
);
CREATE TABLE behavior_tag (
  id TEXT PRIMARY KEY,                -- 'cross_from_wide'
  position TEXT NOT NULL, label_tr TEXT NOT NULL,
  weapon_attrs TEXT[] NOT NULL, role_vote TEXT,   -- rol eğilimi
  conflicts TEXT[] NOT NULL DEFAULT '{}',
  nlg_tr TEXT, accelerate_hint TEXT
);
CREATE TABLE preset (                 -- stil preset + efsane replika
  id TEXT PRIMARY KEY, kind TEXT CHECK (kind IN ('STYLE','REPLICA')),
  name TEXT NOT NULL, formation TEXT, settings JSONB,
  signature_tr TEXT, role_hints JSONB,            -- {slot: "profil ipucu"}
  slot_tags JSONB,                                -- {slot: [behavior_tag_id,...]}
  source_note TEXT
);

-- ===== Kullanıcı & Kariyer (event-sourced) =====
CREATE TABLE app_user (
  id BIGSERIAL PRIMARY KEY, email TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL,
  subscription_type TEXT DEFAULT 'FREE' CHECK (subscription_type IN ('FREE','PREMIUM')),
  credit_balance INT DEFAULT 0, daily_credit_limit INT DEFAULT 20, last_credit_refresh DATE,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE subscription (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT REFERENCES app_user(id),
  plan TEXT, status TEXT, started_at TIMESTAMPTZ, expires_at TIMESTAMPTZ,
  auto_renew BOOL, payment_provider TEXT, external_subscription_id TEXT
);
CREATE TABLE credit_transaction (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT REFERENCES app_user(id),
  type TEXT, amount INT, reason TEXT, created_at TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE career (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT REFERENCES app_user(id),
  game_version_id SMALLINT REFERENCES game_version(id),
  club_id INT REFERENCES club(id), season INT, budget_eur BIGINT,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE TABLE user_save_snapshot (     -- her yüklenen save = bir versiyon
  id BIGSERIAL PRIMARY KEY, career_id BIGINT REFERENCES career(id),
  taken_at TIMESTAMPTZ, source_file TEXT, raw_meta JSONB
);
CREATE TABLE user_player_state (
  id BIGSERIAL PRIMARY KEY, career_id BIGINT REFERENCES career(id),
  base_player_id BIGINT REFERENCES base_player(id),
  club_id INT, dynamic_potential SMALLINT, value_eur BIGINT, form SMALLINT,
  acquired_at TIMESTAMPTZ, updated_at TIMESTAMPTZ
);
CREATE TABLE roster_event (           -- transfer/satış/kiralık — event log
  id BIGSERIAL PRIMARY KEY, career_id BIGINT REFERENCES career(id),
  base_player_id BIGINT REFERENCES base_player(id),
  type TEXT CHECK (type IN ('BUY','SELL','LOAN_IN','LOAN_OUT','PROMOTE')),
  fee_eur BIGINT, from_club_id INT, to_club_id INT, created_at TIMESTAMPTZ DEFAULT now()
);
```

## 7. DOMAIN SEED (role library 37 rol — koda hazır seed)
`role.attr_weights` türetme (karar A hibrit): **taban = pozisyonun EA-OVR ağırlıkları** (açık datasetten regresyonla öğrenilir — `playerlab` yöntemi), **üstüne weapon attr'lerine +0.05–0.10 büküm**, sonra Σ=1'e normalize. Calibrate: CONFIG.
Aşağıdaki tablo her rolün **weapon + PS + AcceleRATE**'ini sabitler (bunlar kararlaştırıldı); attr_weights yukarıdaki kuralla üretilir. Kaynak: `behavior_tag_library` + `sofifa_scout_links` + FC27 rol referansı.

| role_id | Pos | Focus(lar) | Weapon [hardMin/target] | Priority PS | AcceleRATE |
|---|---|---|---|---|---|
| goalkeeper | GK | Defend/Balanced | Reflexes 68/82, GKPositioning 66/80 | — | — |
| sweeper_keeper | GK | Balanced/Build-Up | Reflexes 66/80, GKPositioning 68/82, Kicking 60/75 | — | — |
| ball_playing_keeper | GK | Build-Up | Kicking 66/80, Composure 60/75 | — | — |
| fullback | RB/LB | Defend/Balanced | DefAwareness 60/78, StandingTackle 62/80 | Jockey | Ctrl/Lengthy |
| wingback | RB/LB | Balanced/Support | Stamina 74/86, Crossing 60/78, DefAwareness 58 | Whipped Pass | Explosive |
| attacking_wingback | RB/LB | Support/Attack | Crossing 64/82, Stamina 78/88, Sprint 78/88 | Whipped Pass | Explosive |
| inverted_wingback | RB/LB | Build-Up/Attack | ShortPassing 66/80, Composure 62/78 | — | Controlled |
| falseback | RB/LB | Defend/Balanced | ShortPassing 64/78, DefAwareness 64/80 | — | Controlled |
| defender | CB | Defend/Balanced | DefAwareness 64/82, StandingTackle 66/82, Strength 74/86 | Anticipate | Lengthy/Ctrl |
| stopper | CB | Balanced/Aggressive | Aggression 70/84, StandingTackle 66/82, Acceleration 66/80 | Bruiser | Controlled |
| ball_playing_defender | CB | Defend/Build-Up/Aggressive | ShortPassing 64/80, Composure 66/82, Acceleration 66/80 | Long Ball Pass | Lengthy/Ctrl |
| wide_back | CB | Defend/Support/Aggressive | Acceleration 68/82, DefAwareness 62/78 | Jockey | Explosive/Ctrl |
| holding | CDM | Defend/Roaming/Ball-Winning | DefAwareness 66/82, Interceptions 66/82, Stamina 78/88 | Intercept | Ctrl/Lengthy |
| deep_lying_playmaker | CDM/CM | Defend/Roaming/Build-Up | Vision 70/86, LongPassing 70/84, Composure 70/85 | Long Ball Pass | Controlled |
| centre_half | CDM | Defend | DefAwareness 68/82, Positioning 64/80 | Anticipate | Lengthy/Ctrl |
| wide_half | CDM | Defend/Build-Up | Stamina 76/86, Crossing 58/74 | — | Explosive/Ctrl |
| box_crasher | CDM | Balanced | Positioning 66/82, LongShots 66/84, ShotPower 70/86 | Power Shot | Explosive/Ctrl |
| box_to_box | CM | Balanced/Ball-Winning | Stamina 78/88, Interceptions 64/80 (BW) / Positioning 66/80 (att) | Relentless | Ctrl/Explosive |
| playmaker | CM/CAM | Attack/Roaming/Balanced/Build-Up | Vision 70/86, ShortPassing 70/85, Composure 66/82 | Incisive Pass | Explosive/Ctrl |
| half_winger | CM/CAM/LM/RM | Balanced/Attack/Support/Roaming | Dribbling 72/84, Crossing 62/78 | — | Explosive |
| classic_10 | CAM | Attack/Wide/Versatile | Vision 70/86, BallControl 70/85, Composure 68/84 | Incisive Pass | Explosive/Ctrl |
| shadow_striker | CAM | Attack | Positioning 66/82, Finishing 64/80, Acceleration 80/90 | Finesse Shot | Explosive |
| winger | LM/RM/LW/RW | Balanced/Attack/Versatile | Crossing 64/80, Acceleration 80/90 | Whipped Pass | Explosive |
| wide_playmaker | LM/RM/LW/RW | Attack/Build-Up | Dribbling 74/86, Vision 68/82, Agility 78/88 | Technical | Explosive |
| wide_midfielder | LM/RM | Support/Defend/Build-Up | Stamina 76/86, DefAwareness 60/76 | — | Ctrl/Explosive |
| inside_forward | LM/RM/LW/RW | Balanced/Attack/Roaming | Finishing 64/80, Agility 78/88, Dribbling 72/84 | Finesse Shot | Explosive |
| advanced_forward | ST | Attack/Versatile/Support | Finishing 66/84, Positioning 68/84, Composure 64/82 | Finesse Shot | Ctrl/Explosive |
| poacher | ST | Attack/Support/Versatile | Finishing 68/86, Positioning 68/86, Composure 64/82 | Chip Shot | Ctrl/Explosive |
| target_forward | ST | Attack/Balanced/Wide | Heading 70/84, Strength 76/88, Jumping 76/88 | Precision Header | Lengthy/Ctrl |
| false_9 | ST | Build-Up/Attack | Vision 68/84, BallControl 72/86, ShortPassing 70/84 | Incisive Pass | Explosive/Ctrl |
> (37'ye ulaşmak için fullback/holding/playmaker/box-to-box gibi çok-focus roller her focus'ta ayrı `role` satırı olur — aynı weapon, farklı attr_weights büküm. Seeder bunu focus listesinden üretir.)

Diğer seed'ler **companion dosyalardan**: behavior_tag (`behavior_tag_library.md`), preset/replica (`preset_and_replica_library.md`), scout filtre kodları (`sofifa_scout_links.md`).

## 8. FIT ENGINE
Tam mantık + formüller + sözde-kod + test senaryoları: **`fit_engine_spec.md`** (RoleFit · IntentFit · SquadFit · RulesEngine · NLG). Java servisleri: `RoleFitService`, `IntentFitService`, `SquadFitService` (Hungarian), `RulesEngine` (deklaratif kurallar `rule` tablosu/JSON), `NlgService`. Onaylı kararlar gömülü (weapon −1.5/puan, PS+ −4, projected tavan 74/82, RoleFit%+IntentFit% ayrı).

## 9. API (REST v1, `/api/v1`)
```
# Katalog / arama
GET  /players?gv=27&pos=RW&finishing_min=66&ps=FinesseShot&sort=overall&order=asc&page=
GET  /players/{id}?gv=27
POST /players/compare            {ids:[], roleId}           -> RoleFit karşılaştırma
# Roller / taktik / preset
GET  /roles ; GET /behavior-tags?pos=RW ; GET /presets?kind=REPLICA
# Fit
POST /fit/role                   {playerId, roleId, intent?}        -> RoleFit
POST /fit/squad                  {careerId, tactic}                 -> SquadFit+IntentFit+weakLinks+rules+NLG  [CREDIT: heavy]
POST /recommend/slot             {careerId, slotRole, intent, budgetMax}  -> scored shortlist [CREDIT]
# Kariyer
POST /careers {gameVersion,clubId}; GET /careers/{id}; GET /careers/{id}/squad
POST /careers/{id}/events {type,playerId,fee,...}      # transfer/sat → event-sourced
POST /careers/{id}/import (multipart save file)        # PREMIUM, §11
# Auth / billing
POST /auth/register|login|refresh ; GET /me ; POST /billing/subscribe (stub)
```
Kredi akışı (product-model §4): auth → subscription → (PREMIUM sınırsız / FREE credit kontrol) → credit düş → işlem → teknik hata ise iade. Backend-enforced (`@PreAuthorize`).

## 10. AUTH + FREE/PREMIUM + KREDİ
- FREE: global DB (MODELED pot/value), arama, taktik builder, RoleFit/SquadFit, temel NLG, **günlük kredi** (ağır AI: SquadFit solve, slot recommend), reklam flag.
- PREMIUM: save import (gerçek pot/value), sınırsız ağır AI, reklamsız, gelişmiş analiz.
- `showAds` flag FE'de; gerçek sağlayıcı sonra.

## 11. SAVE-IMPORT (PREMIUM) — oyuncunun kariyer verisi
Kayıt yeri (PC): `C:\Users\<user>\AppData\Local\EA SPORTS FC 27\settings\`.
**Önemli gerçek (düzeltme):** FC'nin ham kariyer-save dosyası **tescilli/ikili (proprietary) formattır — düz SQLite değildir.** Doğrudan parse reverse-engineering ister ve her patch'te kırılabilir (cmtracker bunu yapar; maliyetli/kırılgan).
**v1 kararı (önerilen):** ham save yerine, kullanıcının **mod aracı export'unu** yüklemesi — FC/FIFA **Editor Tool** veya **Live Editor** ile dışa aktarılan **CSV/SQLite** (kullanıcının daha önce tarif ettiği "modcuların o anki DB'yi CSV verdiği" akış). Temiz, dokümante, parse'ı kolay, yasal (kullanıcının kendi verisi).
Akış: upload (CSV/SQLite export) → `SaveImportParser` (şemaya map) → `user_save_snapshot` + `user_player_state` (+ `player_valuation source='IMPORT', owner_user_id`, **private**). Versiyonlu (her yükleme yeni snapshot).
- **BLOCKER — örnek dosya:** parser mapping'i, kullanıcıların yükleyeceği **gerçek export dosyasının bir örneği** gelince kesinleşir. O ana dek `ImportProvider` interface + mock fixture.
- İleri (v2, kapsam dışı): ham proprietary save'in doğrudan reverse-engineering'i.

## 12. FRONTEND / MVP EKRANLARI
`/players` (filtre+scout) · `/players/{id}` · `/compare` · `/tactics/builder` (formasyon seç → slot'a **davranış tag** seç; çelişen tag **grileşir/kilitli**; preset/replika tek tık) · `/squad` (in-app kariyer: takım seç, transfer/sat) · `/fit` (SquadFit% + IntentFit% + 11 slot RoleFit/projected/🟢🟡🔴/NLG + zayıf halka + risk kartları + atak paterni) · `/recommend` (slot öneri, bütçeli) · `/career/import` (PREMIUM) · `/profile` `/subscription`.
UX kuralı: davranış seçimi FM-tarzı; uyumsuz tag seçilemez (client).

## 13. BUILD SIRASI (Sonnet için faz sırası)
1. Repo iskelet (`/Users/user/Desktop/Projects/FCarrierTacticBuilder`) + Docker Compose (app/pg/redis/ml) + Flyway V1 (§6 DDL).
2. Catalog entity/repo + `ExternalDataProvider`/Mock + drop-api ingestion + açık-dataset seed.
3. Role library seeder (§7) + behavior_tag/preset seed (companion dosyalardan).
4. **Fit Engine** (`fit_engine_spec.md`) + birim testler (6 senaryo) — ilk ve en kritik iş.
5. RulesEngine + NLG.
6. ML servis (potential/value) + backend ml-client + açık-setle validasyon (U21 MAE).
7. Core API (§9) + Redis + auth/entitlement + credit.
8. Frontend MVP (§12).
9. PREMIUM save-import (örnek dosya gelince).
> Gate: Fit Engine testleri yeşil olmadan ileri gidilmez.

## 14. TEST
Gömülü Postgres (zonky embedded-postgres; Testcontainers kullanılmaz), Redis yerine bellek içi test bean'leri. Yüksek coverage: Fit Engine (deterministik fixture), RulesEngine, model validasyon (MAE eşiği), credit (transactional, refund), import parser (mock fixture). Solver için deterministik atama testi (§fit_engine_spec T3).

## 15. CONFIG / UNKNOWN FLAGLARI (uydurma yok)
- `fc27_potential_model` (FC27 pot sistemi değişti → ayrı kalibrasyon) — CONFIG.
- `save_schema_mapping` (SQLite tablo eşlemesi) — örnek dosya BLOCKER.
- `open_dataset_license` — teyit edilecek CONFIG.
- `accelerate_thresholds` (boy+AGI+STR+ACC) — FC27'de teyit, config.
- Fit kalibrasyon katsayıları (weapon 0.4/1.5, projected 74/82, combined 0.7/0.3, tagExpected) — açık-setle tune, config.
- `drop_api_fc27_rollover` — çıkış sonrası endpoint/branding re-verify.

## 16. COMPANION DOSYA MANİFESTİ (Sonnet'e bununla birlikte verilecek)
- `fit_engine_spec.md` — motor (zorunlu)
- `behavior_tag_library.md` — davranış tag seed (zorunlu)
- `preset_and_replica_library.md` — preset + efsane replika seed (zorunlu)
- `sofifa_scout_links.md` — scout filtre parametre sözlüğü (öneri motoru)
- `career-platform_PHASE-0.md` — gerekçeler + kaynaklar (referans)

## 17. CODING CONVENTIONS — Buğra'nın house-style'ı (AUTORİTE)
**Otorite kaynak = Buğra'nın yüklediği üç dosya.** Kod ajanı backend'e başlamadan **önce bunları okur ve birebir uyar:**
- `CLAUDE.md` (backend repo **kökü**) · `docs/backend-standard.md` · `docs/collaboration.md` (backend repo **/docs**).
- Kurulum: bu üç dosya `FCarrierTacticBuilderBackend` repo'suna yukarıdaki yerlere konur; CLAUDE.md diğer ikisini `@import` eder.

### Greenfield'e TAŞINAN yazma-modu kuralları (backend-standard §1.6, §19–23 + Don'ts)
- **Constructor injection** (Lombok `@RequiredArgsConstructor`). ❌ field/`@Autowired` injection.
- **Katmanlar:** Controller (iş mantığı/mapping/try-catch YOK) → Service (`@Transactional` servis seviyesinde) → Repository. Mapping ayrı **Mapper** (`@Component @RequiredArgsConstructor`) — **manuel mapper, MapStruct değil.**
- **DTO/Entity ayrımı:** entity dışarı sızmaz. ❌ `@Data` JPA entity'de. Response DTO: `@Data @Builder @NoArgsConstructor @AllArgsConstructor` + `@JsonInclude(NON_NULL)`. Duplicate DTO açma; mantıklı model sahipliği.
- **Braces zorunlu** (brace-less `if` yok). Açık naming.
- **Constants** `*Constants` + static-import; magic string/number yok. **Enum:** backing value + `@Getter @AllArgsConstructor`.
- **Logging:** `@Slf4j`, `log.info("... id : {}", id)`; info=akış/outcome, warn=beklenen hata, error=throwable. ❌ PII/secret/token loglama.
- **Money:** içeride `BigDecimal`; display/wire **2 dp `RoundingMode.HALF_UP`**. (Transfer bütçesi/değer gösteriminde geçerli.)
- **Redis:** `RedisTemplate<String,String>`, key prefix Constants'tan, **write'ta TTL**, değer JSON.
- **Over-engineering freni:** gereksiz pattern/factory/strategy/interface/abstraction/wrapper/util AÇMA. Refactor **kod sadeleşince** başarılı, sınıf sayısı artınca değil. Proje mimarisi teorik saflıktan üstün (DRY/KISS/SRP dogmatik değil).
- **Davranış korunur / açık etiketleme:** mevcut davranışı değiştiren her öneri açıkça belirtilir; sessizce uygulanmaz. (Greenfield'de yeni yazıyoruz; ama refactor turlarında §1 review-sözleşmesi geçerli: SAFE REFACTOR uygula, gerisini `POTENTIAL LOGIC ISSUE`/`API CONTRACT CHANGE`/`ARCHITECTURAL RECOMMENDATION` raporla.)
- **Güvenlik:** bcrypt, JWT access+refresh, server-side entitlement, rate-limit, bind-param. **EA kimlik bilgisi asla istenmez/loglanmaz; kullanıcı save'i private.**
- **Test:** yeni-feature modunda service'ler unit-test (idempotency/concurrency/validation/guard). Review modunda söylenmedikçe test yazma.
- **Commit:** anlamlı her aşamada; mesaj `branch-adı : yapılan iş kısaca` (ya da Conventional Commits) — refactor'da davranış değişimi mesajda açık.

### Azerlottery'ye ÖZEL — bu ürüne TAŞINMAZ (greenfield, tek backend)
`com.misli.*` namespace · `/api/web/v2` & `/api/mobile/v2` kanal şeması (biz `/api/v1`, tek kanal) · aggregator + **OpenFeign + PHP→Java migration** · `user-model`/`user-admin` twin-service kuralı · `/1000` para formatlama · spesifik JSON kısa key'ler (`cta`,`bai`,`tgi`) · Kafka (gerekmedikçe). Bunlar iş-bağlamı; yeni üründe yok.

### Açık (Buğra netleştirecek)
- **Base package:** `com.fcareer` (karar verildi). Alt paketler feature-bazlı: `com.fcareer.catalog | .roles | .tactics | .fitengine | .career | .recommendation | .auth | .billing | .mlclient | .common`.
- `collaboration.md` iş-akışı dokümanı (ticket/Jira formatı) — repo'da durur, ama bu ürün solo olduğundan Jira-yorum formatı opsiyonel.

