# CLAUDE.md — FC Assist Backend (FCarrierTacticBuilderBackend)

EA SPORTS FC 27 için offline Kariyer + Ultimate Team companion uygulamasının backend'i.
Ürün özeti ve mimari: `docs/spec/BUILD_SPEC.md`. Motor: `docs/spec/fit_engine_spec.md`.
İnceleme/yol haritası paketi: `docs/review/00_INDEX_READ_FIRST.md` (varsa önce oku).

## Stack (gerçek olan)
- Java 17, Spring Boot 3, Jakarta, Maven, Lombok
- PostgreSQL 16 + Flyway (`src/main/resources/db/migration`), Redis (refresh token, rate limit, referans cache)
- Python 3.11 FastAPI ML servisi: `ml-service/` (potential/value), backend → REST
- Base package: `com.fcareer` — feature paketleri: advisor, auth, billing, career, catalog, common, development,
  fitengine, gameconfig, ingestion, mlclient, recommendation, roles, tacticcode, tactics, ut, versioning
- Tek kanal: `/api/v1`. **Kafka, OpenFeign, aggregator, mobile/web kanal ayrımı YOK.**

## House style
Kurallar: @docs/fcareer-standard.md (yalnız bu projede uygulanan kurallar; HTTP status kodları D-09, yol `/api/v1`, testler zorunlu).
Eski Azerlottery standardı `docs/archive/backend-standard.md` altında, referans için; import edilmez.

## Bu projeye özel kurallar
- Oyun kuralları (kimya eşikleri, squad rating, fiyat aralıkları, içerik yenileme saati) koda gömülmez:
  `seed/ut-rules.*.json` / `ut_rules` tablosu. Değer FC27 için doğrulanmadıysa `verified:false`.
- Fit engine katsayıları config'te (`fcareer.fit.*`); değişiklik = `BEHAVIOR CHANGE` + senaryo testleri (T1–T6) yeşil.
- Public sorgular yalnız `player_valuation.source='MODELED'` okur; `IMPORT` verisi yalnız sahibine.
- Kullanıcının kariyer/UT kulüp verisi başka kullanıcıya sızmaz: careerId alan her uçta `requireOwned`.
- EA hesap kimlik bilgisi asla istenmez, saklanmaz, loglanmaz. Capture gövdeleri loglanmaz.
- Veri kaynakları `docs/DATA_POLICY.md`'ye uymak zorunda; yeni dış kaynak = Buğra onayı.
- Ücretli (kredi) iş üreten her uç `CreditGuard` arkasında; bedava önizleme uçları ücretli alanı döndürmez.

## Çalıştırma / test
- `mvn test`: entegrasyon testleri zonky gömülü Postgres ile (Docker gerekmez, Testcontainers yok); Redis yerine bellek içi bean'ler (`TestSupportConfiguration`). Opt-in: `-Dea.live=true`, `-Dml.live=true`. ML: `cd ml-service && python -m pytest`.
- Dev: README'deki `DevApplication` akışı.

## Çalışma biçimi
- Doğrudan ve analitik; yanlış yaklaşıma gerekçeli itiraz.
- Refactor'da davranış değişmez; değişen davranış commit mesajında `BEHAVIOR CHANGE:` ile açıkça yazılır.
- API sözleşmesi değişikliği (`API CONTRACT CHANGE`) web repo ile aynı PR serisinde yapılır.
- Gereksiz abstraction yok; refactor kod sadeleşince başarılıdır.

## Çalışma kuralları
- Dosya düzenlemeleri Edit aracıyla yapılır; `sed` veya heredoc ile kaynak dosya düzenlenmez.
- Önce kırmızı test, sonra kod.
- Her task sonunda: testler yeşil + push + progress güncellemesi.
- Secret'lar commit'e, loga, rapora, dokümana girmez; `.env` okunmaz. Anahtar varlık kontrolü yalnız:
  `grep -E '^(DB_PASSWORD|JWT_SECRET|ML_SHARED_SECRET)=' .env | cut -d= -f1`
- Veri silme protokolü: dry-run → git dışında yedek (`../backups`) → tek transaction → kullanıcı onayı.
  `docker compose down -v`, `DROP`, `TRUNCATE`, `DELETE` onaysız çalıştırılmaz.
- Ortam: macOS (Apple Silicon, arm64). Proje kökü `/Users/user/Desktop/Projects/FCarrierTacticBuilder`
  (backend `main`, web `v3`, yedekler `backups/` git dışı). JDK 17: `~/.zshrc` jenv ile `JAVA_HOME`'u ayarlar;
  `.zshrc` okumayan shell'de `JAVA_HOME=$(/usr/libexec/java_home -v 17)` ver.
