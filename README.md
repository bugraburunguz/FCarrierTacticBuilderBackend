# FC Assist — Backend

Java 17 / Spring Boot 3 / PostgreSQL + Python ML servisi (`ml-service/`). Mimari ve kurallar: `docs/fcareer-standard.md`, spesifikasyon: `docs/spec/`.

## Çalıştırma

```bash
# Tam yığın (Docker): kök dizindeki docker-compose.yml + .env
docker compose up --build

# Docker olmadan geliştirme (kalıcı embedded Postgres + mock veri)
mvn test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test
INGESTION_RUN_ON_STARTUP=true java -cp "target/classes;target/test-classes;$(cat target/cp.txt)" com.fcareer.DevApplication
```

`DevApplication` yalnızca test kaynağındadır; üretimde kullanılmaz.

## Ortam değişkenleri

| Değişken | Açıklama |
|---|---|
| `JWT_SECRET` | **Zorunlu**, ≥ 32 byte. Varsayılanı yok. |
| `ADMIN_KEY` | `/api/v1/admin/**` için `X-Admin-Key`. Boşsa admin uçları kapalı. |
| `DB_URL` `DB_USERNAME` `DB_PASSWORD` | PostgreSQL |
| `INGESTION_PROVIDER` | `mock` (varsayılan) veya `ea-site` (EA ratings sayfası, FC27) |
| `INGESTION_GAME_VERSION` | Yalnızca `27` (FC27). Eski sürüm desteklenmez. |
| `ML_BASE_URL` | ML servisi |

## Veri hattı

1. `POST /api/v1/admin/ingestion/run` — `ea-site` (veya mock) ile FC27 kataloğu. Sayfa HTML'inden `buildId` okunur; başlık FC27 değilse ingestion durur.
2. ML modelini eğit (`ml-service/app/train.py`, kolon eşlemesi gerekir — `ml-service/data/mapping.example.json`).
3. `POST /api/v1/admin/ml/valuations` — `MODELED` potential/değer üretir.

Public sorgular yalnızca `valuation.source = 'MODELED'` okur; gerçek EA potential'ı yalnızca ileride `IMPORT` + ilgili kullanıcıya özeldir.

## Test

```bash
mvn test                      # birim + embedded Postgres entegrasyon testleri
mvn test -Dea.live=true       # canlı EA ratings sayfası sözleşmesi (opt-in)
mvn test -Dml.live=true       # çalışan ml-service ile sözleşme (opt-in)
cd ml-service && python -m pytest
```
