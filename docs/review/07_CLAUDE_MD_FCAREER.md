# 07 · BACKEND REPO İÇİN YENİ `CLAUDE.md`
**Sorun (`VERIFIED`):** `FCarrierTacticBuilderBackend/CLAUDE.md` başlığı "CLAUDE.md — Azerlottery Backend"; `com.misli.*`, `/api/web/v2` + `/api/mobile/v2`, Kafka, OpenFeign, PHP→Java taşıma, NSoft/Sportradar entegrasyonlarını anlatıyor. Import ettiği `docs/backend-standard.md` de "Azerlottery — Backend House Style" başlıklı ve aggregator/Feign/Kafka/`/1000` formatlama bölümleri içeriyor. BUILD_SPEC §17 bunların bu ürüne taşınmayacağını söylüyor, ama kod ajanı her oturumda yanlış proje bağlamıyla başlıyor.

**Yapılacak (OPS-01):**
1. Aşağıdaki içeriği backend repo köküne `CLAUDE.md` olarak yaz (eskisinin yerine).
2. `docs/backend-standard.md` dosyasını silme; ama başına aşağıdaki "Uygulanabilirlik" notu zaten CLAUDE.md'de var. İleride `docs/backend-standard-fcareer.md` olarak kırpılmış kopya üretilebilir (§13 Feign, §17 Kafka, §22 aggregator/PHP, §18 `/1000` çıkarılır).
3. Web repo köküne de kısa bir `CLAUDE.md` koy (en altta).

---

## Backend `CLAUDE.md` (kopyala)
```markdown
# CLAUDE.md — FCareer Backend (FCarrierTacticBuilderBackend)

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
Kurallar: @docs/backend-standard.md
Uygulanabilirlik notu: o dosya başka bir projeden (Azerlottery) geliyor. **Uygulanır:** §1 review sözleşmesi,
§5–§12 (house style, naming, DI, Lombok, controller, envelope, service, exception), §14–§16, §19–§21, §23.
**Uygulanmaz:** `com.misli`, `/api/web|mobile/v2`, §13 Feign, §17 Kafka, §18'deki `/1000` formatlama,
§22 aggregator & PHP→Java, `user-model/user-admin` twin kuralı, kısa JSON key'ler.

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
- `mvn test` (embedded PG). Opt-in: `-Dea.live=true`, `-Dml.live=true`. ML: `cd ml-service && python -m pytest`.
- Dev: README'deki `DevApplication` akışı.

## Çalışma biçimi
- Doğrudan ve analitik; yanlış yaklaşıma gerekçeli itiraz.
- Refactor'da davranış değişmez; değişen davranış commit mesajında `BEHAVIOR CHANGE:` ile açıkça yazılır.
- API sözleşmesi değişikliği (`API CONTRACT CHANGE`) web repo ile aynı PR serisinde yapılır.
- Gereksiz abstraction yok; refactor kod sadeleşince başarılıdır.
```

---

## Web repo `CLAUDE.md` (kopyala)
```markdown
# CLAUDE.md — FCareer Web (FCarrierTacticBuilderWeb)

React 19 + TypeScript + Vite + Tailwind v4 + TanStack Query. Backend: `/api/v1` (FCarrierTacticBuilderBackend).
Tasarım sistemi kuralları: `docs/review/02_DESIGN_SYSTEM_REDESIGN.md` (varsa önce oku).

## Kurallar
- Renk/ölçü için yalnız semantik token (`bg-panel`, `text-sub`, `border-line` ...). Yeni kodda Tailwind
  `emerald/teal/slate/sky` sınıfları ve hex yok (kart tasarım config'i hariç).
- Emoji ile durum gösterme yok; `FitBadge`, `ChemDiamonds`, ikon seti kullan.
- Oyuncu kartı yalnız `components/card/PlayerCard` ile; saha yalnız `components/pitch/Pitch` ile.
- Oyun kuralları (`ut-core`) backend'in `GET /ut/rules` config'inden; sabit gömme yok.
- Büyük kullanıcı verisi (kulüp kartları, capture) IndexedDB'de; localStorage yalnız küçük tercih.
- Ağır hesap (SBC çözücü, delta önizleme) Web Worker'da.
- Mutlak URL yok: `import.meta.env.BASE_URL` / `<Link>` (GitHub Pages alt yolu).
- Her PR: `npm test`, `npm run lint`, `npm run build` yeşil; UI değişikliğinde Playwright görsel snapshot.
- Kullanıcıya görünen metin Türkçe, kısa; açıklama paragrafı yerine `HelpPopover`.
```
