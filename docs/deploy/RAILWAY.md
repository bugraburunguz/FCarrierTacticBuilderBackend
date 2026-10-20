# FC Assist — Railway'e çıkış runbook'u (DEPLOY-01)

Kurulumu Buğra yapar (panel). Bu belge sıralı adımları, her adımın doğrulamasını ve geri dönüşü verir.
Railway iddiaları kaynaklı; **tüm bağlantılara erişim tarihi: 16.10.2026**. Dokümanda bulunamayan her şey **[doğrulanmadı]** işaretli.

Kararlar: bölge EU West (Amsterdam), adres `*.up.railway.app`, iki repo `main`'den dağıtılır, ücretsiz beta.

## 0. Önemli: Config as Code kullanılmıyor

Railway'in `railway.json` / `railway.toml` dosyası (Config as Code) **kullanımdan kaldırıldı ve yeni servisler kullanamıyor**: "Config as Code is deprecated", "New services cannot opt into Config as Code", mevcut dosyalar 2026-12-01'e kadar çalışır ([Config as Code](https://docs.railway.com/config-as-code)). Yerine geçen Infrastructure as Code (`.railway/railway.ts`, CLI ile `railway config plan/apply`) Dockerfile yolu, watch paths ve yeniden başlatma politikası seçeneklerini belgelemiyor ([IaC reference](https://docs.railway.com/infrastructure-as-code/reference)); dosyada olmayan değişkenlerin silinip silinmediği de yazmıyor **[doğrulanmadı]**. Bu yüzden servis ayarları **panelden** yapılır; her servisin ayar tablosu aşağıda (3. adım). IaC'ye geçiş ayrı görev olabilir.

## Mimari

```
İnternet ──HTTPS──▶ Railway edge ──▶ web (nginx, tek public domain)
                                      │  /api/*  → http://backend.railway.internal:8080  (private)
                                      ▼
                                   backend (Spring, private) ──▶ Postgres (Railway, private)
                                      └──▶ ml (FastAPI, private; yalnız yönetici değerleme ucu)
```

| Karar | Dayanak |
|---|---|
| Yalnız web'e domain; backend/ml/DB'ye domain yok | "Railway services don't obtain a domain automatically" — domain Settings → Networking → Generate Domain ile açılır ([domains](https://docs.railway.com/networking/domains/working-with-domains)). Domainsiz servisin dışarıdan **hiç** trafik almadığı açıkça yazmıyor **[doğrulanmadı]** → duman testinde ölçülür. "Databases are deployed private by default" ([Postgres](https://docs.railway.com/databases/postgresql)) |
| Private adres `SERVİS.railway.internal`, port açıkça yazılır | Adres biçimi ve örnek `http://api.railway.internal:PORT` ([private networking](https://docs.railway.com/networking/private-networking)). Portun zorunlu olduğu açıkça yazmıyor **[doğrulanmadı]**; biz her zaman yazıyoruz |
| IPv4/IPv6 | 16.10.2025 sonrası oluşturulan ortamlarda private DNS "both internal IPv4 and IPv6", eski ortamlarda yalnız IPv6 ([how it works](https://docs.railway.com/networking/private-networking/how-it-works)). Yeni proje → çift yığın |
| Bağlanma adresleri | Spring/Tomcat joker adrese bağlanır; yerelde `[::1]` ve `127.0.0.1` ikisi de 200 (ölçüldü). uvicorn `0.0.0.0` (dokümandaki örnek); `--host ::` uvicorn'da **yalnız IPv6** dinliyor (yerelde ölçüldü), bu yüzden varsayılan değil; yalnız-IPv6 eski ortamda `ML_BIND_HOST=::`. nginx `listen PORT` + `listen [::]:PORT`. Java istemcisi varsayılan IPv4'ü tercih eder. Framework'e özel Railway rehberi yok **[doğrulanmadı]** |
| nginx resolver | Railway private DNS adresi dokümanda yok **[doğrulanmadı]** → sabit yazılmaz; nginx imajının `NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1` özelliği konteynerin `/etc/resolv.conf`'undaki sunucuyu kullanır (IPv6 köşeli parantezli). Değişkenli `proxy_pass` her istekte çözer (`valid=10s`) |
| PORT | Railway `PORT`'u enjekte eder ve public domain hedef portunu algılar ([application failed to respond](https://docs.railway.com/networking/troubleshooting/application-failed-to-respond), [domains](https://docs.railway.com/networking/domains/working-with-domains)). Private servislerde değeri biz sabitleriz: backend `PORT=8080`, ml `PORT=8000` (referanslar sabit kalsın diye) |
| Healthcheck yalnız dağıtım anında | "only called at the start of the deployment", hostname `healthcheck.railway.app`, `PORT` üzerinden, varsayılan zaman aşımı 300 sn ([healthchecks](https://docs.railway.com/deployments/healthchecks)) |
| İstemci IP'si | Edge `X-Real-IP` verir ([specs & limits](https://docs.railway.com/networking/public-networking/specs-and-limits)); istemcinin gönderdiğini ezip ezmediği yazmıyor **[doğrulanmadı]** → 9. adımda duman testi kesinleştirir. web `TRUST_EDGE_REAL_IP=1`, backend `FCAREER_TRUST_PROXY=true`; backend dışarıdan erişilemediği için başlığı yalnız nginx verebilir |

## Ön koşullar

- GitHub'da iki repo (ikisi de **public**): `bugraburunguz/FCarrierTacticBuilderBackend`, `bugraburunguz/FCarrierTacticBuilderWeb`; CI `main` push'unda koşar (Backend Test, Web CI).
- Brevo hesabı: doğrulanmış gönderici adresi + SMTP anahtarı (Brevo panelindeki SMTP sekmesi; sunucu/port değerini oradan al).
- Yerelde docker compose yığını çalışıyor (veri taşıma kaynağı) ve `node` 22.
- ML modelleri kararı (DEPLOY-01 raporu). Karar verilene kadar ml servisi modelsiz çalışır: kullanıcı akışı etkilenmez (değerlemeler DB'de), yalnız yönetici "ML değerleme" ucu 503 döner.

## 1. Proje ve bölge

1. Railway → New Project → Empty Project. Ad: `fc-assist`.
2. Ortam `production` (varsayılan).
- **Görmelisin:** boş tuval.
- Bölge servis başına seçilir: EU West Metal (Amsterdam) = `europe-west4-drams3a`; servis ayarlarındaki bölge seçiciyle; birimi (volume) olmayan serviste bölge değişikliği kesinti yaratmaz ([regions](https://docs.railway.com/deployments/regions)). Postgres/Redis gibi volume'lu servislerde bölgeyi **ilk kurulumda** seç.

## 2. Veritabanları

1. `+ New` → Database → **PostgreSQL**. Servis adı `Postgres` (referanslar bu adı kullanır). Bölge: EU West.
- Redis **gerekmez** (OPS-02): token'lar Postgres'te, hız sınırı ve önbellek backend belleğinde. Eski kurulumda Redis servisi varsa
  yeni backend dağıtıldıktan sonra silinebilir; backend'in `REDIS_URL` değişkeni de kaldırılır (kullanılmaz).
- **Görmelisin:** Postgres değişkenleri `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`, `DATABASE_URL` ([Postgres](https://docs.railway.com/databases/postgresql)).
- Postgres sürümü dokümanda yazmıyor **[doğrulanmadı]**; taşıma betiği hedef sürümü yazdırır. Yerel döküm 16'dan; hedef ≥ 16 olmalı.
- Backend JDBC'yi `PG*` değişkenlerinden kurar (`DATABASE_URL` JDBC biçiminde değil). Hangisinin "önerilen" olduğu dokümanda açıkça yazmıyor **[doğrulanmadı]**; ikisi de aynı private bağlantıyı tarif ediyor.

## 3. Uygulama servisleri

> **Önce 8. adımdaki veri taşımasını yap, sonra backend servisini oluştur.** GitHub repodan oluşturulan servis hemen dağıtılır
> (dağıtımı oluşturmadan erteleme seçeneği **[doğrulanmadı]**); boş DB'de Flyway şemayı sıfırdan kurarsa taşıma betiği "Hedef boş değil"
> diye durur. Bu olursa: backend'i durdur, Postgres servisini silip yeniden oluştur (2. adım), taşımayı yap, backend'i yeniden dağıt.
> ml ve web servislerinin sırası serbest.

Her biri `+ New` → GitHub Repo. **Domain üretme** (yalnız web'e, 7. adımda). Builder: kökte `Dockerfile` olduğundan Dockerfile ile derlenir ([dockerfiles](https://docs.railway.com/builds/dockerfiles); özel yol gerekirse `RAILWAY_DOCKERFILE_PATH`).

| Ayar | ml | backend | web |
|---|---|---|---|
| Repo / dal | Backend repo / `main` | Backend repo / `main` | Web repo / `main` |
| Root Directory | `/ml-service` | `/` | `/` |
| Dockerfile | `ml-service/Dockerfile` | `Dockerfile` | `Dockerfile` |
| Healthcheck Path | `/health` | `/actuator/health` | `/` |
| Restart Policy | On Failure, en çok 10 | On Failure, en çok 10 | On Failure, en çok 10 |
| Watch Paths | `/ml-service/**` | `/src/**`, `/pom.xml`, `/Dockerfile` | (boş: her değişiklik) |
| Public domain | yok | yok | **var** (7. adım) |
| Bölge | EU West | EU West | EU West |
| Wait for CI | açık | açık | açık |

- Root directory: "Railway will only pull down files from that directory" ([monorepo](https://docs.railway.com/deployments/monorepo)). Watch paths'in kök dizine göre mi repo köküne göre mi olduğu yazmıyor; dokümandaki örnekler repo kökünden (`/packages/backend/**`) **[doğrulanmadı]**. Watch paths yanlışsa yalnız gereksiz/eksik yeniden dağıtım olur; emin değilsen boş bırak.
- Restart policy değerleri ON_FAILURE / ALWAYS / NEVER ve en çok deneme sayısı Config as Code şemasında var ([reference](https://docs.railway.com/config-as-code/reference)); panelde aynı adlarla bulunduğu **[doğrulanmadı]**.
- **Wait for CI:** servis ayarında "Wait for CI" açılınca dağıtım GitHub Actions bitene kadar `WAITING` kalır, başarısız koşuda atlanır; iş akışı `push` tetikleyicisi içermeli ([GitHub autodeploys](https://docs.railway.com/deployments/github-autodeploys)). İki repodaki iş akışı `push: branches: [main]` içeriyor.

## 4. Gizli değerler (yeni üretilir, yerel değerler kullanılmaz)

Değeri ekrana basmadan doğrudan panoya al ve panele yapıştır; hiçbir dosyaya, commit'e, sohbete yazma:

```bash
openssl rand -base64 48 | tr -d '\n' | pbcopy
```
(JWT_SECRET; ≥ 32 bayt) — yapıştırdıktan sonra panoyu temizle:

```bash
printf '' | pbcopy
```

```bash
openssl rand -hex 32 | tr -d '\n' | pbcopy
```
(ML_SHARED_SECRET ve ADMIN_KEY için ayrı ayrı çalıştır.)

- Panelde **Sealed** işaretle: değer "never visible in the UI nor can it be retrieved via the API", geri açılamaz ([variables](https://docs.railway.com/variables)). Kopya ortamlara/PR ortamlarına geçmez.
- `ML_SHARED_SECRET` iki serviste aynı olmalı: proje **Shared Variable** olarak tanımla, servislerde `${{shared.ML_SHARED_SECRET}}` ([variables](https://docs.railway.com/variables)). Shared değişkenin sealed yapılabildiği **[doğrulanmadı]**; yapılamıyorsa iki servise ayrı ayrı sealed değişken olarak aynı değeri yapıştır.

## 5. Değişkenler (servis başına; değer yok)

Referans sözdizimi `${{SERVİS.DEĞİŞKEN}}`, `${{shared.X}}` ([variables](https://docs.railway.com/variables)).

**backend**

| Değişken | Değer / kaynak |
|---|---|
| `SPRING_PROFILES_ACTIVE` | sabit `prod` (application-prod.yml + ProdConfigGuard) |
| `PORT` | sabit `8080` |
| `PGHOST` `PGPORT` `PGDATABASE` `PGUSER` `PGPASSWORD` | referans `${{Postgres.PGHOST}}` … (beşi de) |
| `JWT_SECRET` | openssl, sealed |
| `ADMIN_KEY` | openssl, sealed |
| `ML_SHARED_SECRET` | `${{shared.ML_SHARED_SECRET}}` |
| `ML_BASE_URL` | `http://${{ml.RAILWAY_PRIVATE_DOMAIN}}:${{ml.PORT}}` |
| `WEB_BASE_URL` | 7. adımdan sonra `https://<web domain>` (sabit; CORS ve doğrulama bağlantısı) |
| `MAIL_HOST` `MAIL_PORT` | Brevo SMTP (port 587, STARTTLS) |
| `MAIL_USERNAME` `MAIL_PASSWORD` | Brevo SMTP kullanıcısı / anahtarı (sealed) |
| `MAIL_FROM` | Brevo'da doğrulanmış gönderici |
| `FCAREER_FREE_BETA` | sabit `true` |
| `FCAREER_DAILY_CREDITS` | sabit `30` |
| `FCAREER_TRUST_PROXY` | sabit `true` |
| `BILLING_STUB_ENABLED` | sabit `false` |
| `FCAREER_BOOTSTRAP_ADMIN_EMAIL` | senin hesabının e-postası (yönetici rolü) |
| `INGESTION_PROVIDER` | sabit `ea-site` (yml varsayılanı `mock`; compose ile aynı olsun diye) |
| `INGESTION_GAME_VERSION` | sabit `27` |
| `FCAREER_RATELIMIT_REGISTER_DAILY_PER_IP` | sabit `5` |

**ml**

| Değişken | Değer / kaynak |
|---|---|
| `PORT` | sabit `8000` |
| `ML_SHARED_SECRET` | `${{shared.ML_SHARED_SECRET}}` |
| `ML_BIND_HOST` | verme (`0.0.0.0`); yalnız IPv6 private ağlı eski ortamda `::` |
| `ML_MODEL_DIR` | imaj varsayılanı `/srv/models` (model kararına göre) |

**web**

| Değişken | Değer / kaynak |
|---|---|
| `PORT` | Railway enjekte eder (verme) |
| `BACKEND_URL` | `http://${{backend.RAILWAY_PRIVATE_DOMAIN}}:${{backend.PORT}}` |
| `TRUST_EDGE_REAL_IP` | sabit `1` |

`RAILWAY_PRIVATE_DOMAIN` referansta kullanılabilen Railway değişkeni olarak listeleniyor ([variables](https://docs.railway.com/variables)); `${{backend.PORT}}` gibi kullanıcı tanımlı değişkene servisler arası referans da aynı sözdizimi.

- **Görmelisin:** her serviste değişkenler; referanslar panelde çözülmüş görünür.

## 6. İlk dağıtım

Sıra: Postgres (hazır) → ml → backend → web.

1. ml: Deploy. **Görmelisin:** healthcheck `/health` 200 (`model_loaded:false` modelsizken normal).
2. backend: 8. adımdaki taşıma bitmiş olmalı (3. adımdaki not). Deploy. **Görmelisin:** loglarda Flyway `Successfully validated 39 migrations` / "Schema … is up to date" (taşınan `flyway_schema_history` doğrulanır, yeni migration yok), `Started FcareerApplication`, healthcheck 200. ProdConfigGuard eksik değişkeni adıyla söyler ve açılmaz.
3. web: Deploy. **Görmelisin:** healthcheck `/` 200; log satırları IP'nin son bölümü 0 / `::` ile.

## 7. Domain

1. Yalnız **web** → Settings → Networking → Public Networking → **Generate Domain** ([domains](https://docs.railway.com/networking/domains/working-with-domains)). Hedef port otomatik algılanır.
2. Domain'i backend'in `WEB_BASE_URL` değişkenine yaz (`https://…up.railway.app`) → backend yeniden dağıtılır.
3. **Görmelisin:** backend, ml, Postgres servislerinde public domain **yok**; Postgres'te Public Access (TCP proxy) kapalı (8. adım hariç).

## 8. Veri taşıma (yerel → Railway Postgres)

1. Postgres → Settings → Networking → **Public Access** ekle: TCP proxy açılır ve `DATABASE_PUBLIC_URL` dolar; çıkış trafiği ücretlidir ([Postgres](https://docs.railway.com/databases/postgresql), [TCP proxy](https://docs.railway.com/networking/tcp-proxy)).
2. Değeri panodan bu terminal oturumunda env olarak ver (ekrana basmadan):
   ```bash
   read -rs TARGET_DATABASE_URL && export TARGET_DATABASE_URL
   ```
3. Backend repo kökünde:
   ```bash
   scripts/deploy/restore-to-railway.sh
   ```
   - Hedefte tablo varsa durur ("Hedef boş değil"); hiçbir şey yazmaz.
   - Yerel dökümü `../backups/railway_migrate_<zaman>.dump`'a alır (git dışı), `pg_restore --no-owner --no-privileges --single-transaction` ile yükler.
   - **Görmelisin:** `5/5 Karşılaştırma … kaynak = hedef` (yerel denemede 54 tablo, 306.973 satır).
4. Bitince:
   ```bash
   unset TARGET_DATABASE_URL
   ```
   ve **TCP proxy'yi kaldır** (Settings → Networking → çöp kutusu; "You must remove the TCP Proxy (click the Trashcan icon)", [domains](https://docs.railway.com/networking/domains/working-with-domains)). Kaldırınca `DATABASE_PUBLIC_URL`'in silinip silinmediği yazmıyor **[doğrulanmadı]**; kalırsa etkisiz (proxy yok).
5. 3. adıma dön (backend servisini oluştur ve 6.2 ile dağıt).

Sıra özeti: 1 proje → 2 veritabanları → **8 veri taşıma** → 3 servisler → 4–5 gizli değerler ve değişkenler (servis oluştururken ilk dağıtım değişkensiz başarısız olursa normal; değişkenleri girince yeniden dağıt) → 6 ilk dağıtım → 7 domain → 9 duman testi → 10 uzantı → 11 yedekleme.

**Taşıma başarısızsa:** `--single-transaction` olduğu için hata yarıda kalan yazma bırakmaz; betik çıkış koduyla durur. Uyuşmazlık raporu verirse: backend'i dağıtma, hedef Postgres servisini sil ve yeniden oluştur (boş hedef), sebebi giderip betiği yeniden çalıştır. Yerel veri hiçbir adımda değişmez (yalnız `pg_dump`).

## 9. Duman testi

Web repo kökünde:
```bash
node scripts/deploy/smoke.mjs https://<web domain> --backend-public https://<backend-servis-adı>.up.railway.app
```
- Ana sayfa 200 + CSP/nosniff/Referrer/Permissions/HSTS; `/privacy`, `/about`; `/api/v1/config/app` `freeBeta:true`; oturumsuz `/me` 401; web adresinde actuator JSON yok; backend tahmini public adresinden uygulama yanıtı yok; **sahte `X-Real-IP` ile 11 giriş denemesinde 429**.
- **429 gelmezse:** edge istemcinin `X-Real-IP`'sini ezmiyor demektir → web'de `TRUST_EDGE_REAL_IP=0` yap (hız sınırı tüm kullanıcılar için edge adresine düşer, güvenli ama kaba) ve DEPLOY-01 raporundaki alternatife geç.
- Yerelde: `node scripts/deploy/smoke.mjs http://localhost:8081 --beta false --backend-public http://localhost:8080` → 7/7 geçti (16.10.2026).

**Elle (betik yapmaz, gerçek e-posta gönderir):**
1. `/register` → kendi e-postanla kayıt (aydınlatma kutusu zorunlu). **Görmelisin:** Brevo'dan doğrulama e-postası, bağlantı `https://<web domain>/verify-email?token=…`; tıklayınca "E-postan doğrulandı".
2. Profil → Beta etiketi; Hesabı sil akışını yalnız test hesabıyla dene.
3. Web loglarında bu isteklerin IP'si maskeli, `?token=` görünmüyor.

## 10. Uzantı

Domain belli olduktan sonra (git dışı `../extension`):
```bash
node extension/build.mjs --web-origin https://<web domain>
```
`extension/dist/` → `chrome://extensions` → Load unpacked. Mağaza yayını yok. **Görmelisin:** `dist/manifest.json` `externally_connectable` yalnız `https://<web domain>/*`.

## 11. Yedekleme

- Postgres servisinin volume'u → Backups: zamanlama Daily (24 saatte bir, 6 gün saklanır), Weekly (7 günde bir, 27 gün), Monthly (30 günde bir, 89 gün); elle yedek de alınabilir; geri yükleme aynı proje + ortamda, yeni volume'a, "Deploy" ile tamamlanır ([backups](https://docs.railway.com/volumes/backups)). Hangi planlarda olduğu yazmıyor **[doğrulanmadı]**. Postgres rehberi haftalığı ~1 ay, aylığı ~3 ay diyor ve PITR anlatıyor ([postgres backups](https://docs.railway.com/guides/postgres-backups-restores)) — iki sayfa çelişiyor **[doğrulanmadı]**.
- **Aydınlatma metni "yedeklerdeki kopyalar hesap silindikten sonra en geç 30 gün" diyor:** yalnız **Daily + Weekly** aç (≤ 27 gün). **Monthly açma** (89 gün metinle çelişir). PITR penceresi metinle uyumlu mu ayrıca kontrol et **[doğrulanmadı]**.
- **Görmelisin:** Backups sekmesinde iki zamanlama ve ilk yedek.

## 12. Yayın günü

1. Gizlilik metni sürüm/tarih: web repo `src/features/legal/privacyVersion.ts` → `PRIVACY_DATE` yayın günü (ör. `'20 Ekim 2026'`), metin değiştiyse `PRIVACY_VERSION` +1; `docs/legal/gizlilik-aydinlatma.md` güncel taslakla aynı olmalı (taslağın `<!-- -->` bloğu çıkarılır, sürüm satırı `{{SURUM}}`/`{{TARIH}}` kalır). Commit → CI → web yeniden dağıtılır.
2. Taslak notları (hukukçu kontrolü, KVKK md. 9 standart sözleşme + 5 iş günü bildirim, VERBIS) — kod dışı, senin.

## 13. Ana veriyi (temel katalog) oyunun ana menü dışa aktarımıyla güncelleme

Kariyer hareketleri ("Kayda göre hareketler") kariyer kaydını temel katalogla karşılaştırır; temel katalog oyunun **güncellemesiz ana menü** verisi
olmalı. Mevcut katalog EA reyting sitesinden geldiği için ~1.700 oyuncuda kulüp/OVR farklı (kariyerle karışma bulunmadı: kulüp farkı olan 1.777
oyuncudan yalnız 3'ü kariyer değeriyle aynı).

1. **Yedek:** Postgres → Backups → elle yedek al (11. adım). **Görmelisin:** yeni yedek satırı.
2. LE'de ana menüdeyken dışa aktarım betiğini (2.0.1) çalıştır. Log'da kariyer dışa aktarımı da olabilir: sunucu temel veri için yalnız
   ana menü (`BASE`) dışa aktarımını seçer, yalnız kariyer dışa aktarımı varsa **reddeder** (kariyer verisi kataloğa yazılamaz).
3. Yükle (yönetici anahtarı yalnız panoda; dosya yolu senin log'un):
   ```bash
   read -rs ADMIN_KEY && export ADMIN_KEY
   ```
   ```bash
   curl -sS -X POST "https://<web domain>/api/v1/admin/ingestion/import-base" -H "X-Admin-Key: $ADMIN_KEY" -F "log=@live_editor.log" -F overwrite=true -F label="FC27 ana menü"
   ```
   ```bash
   unset ADMIN_KEY
   ```
   - `overwrite=true`: mevcut oyuncuların kulüp/OVR/öznitelikleri ana menü değerleriyle güncellenir (değişiklikler sürüm geçmişine yazılır, `changesRecorded`).
   - `full` **verme**: dosyada olmayan oyuncuyu "kaldırıldı" işaretler.
   - Klasik Lig (Classic XI, … XI), yer tutucu ve "Erkekler - Diğer" takımları kataloğa girmez.
4. **Görmelisin:** JSON yanıtta `playersInFile` ≈ 21.950, `updated` > 0, `created` küçük. Ana kayıttaki kulüp kariyer satırına içe aktarma
   anında kopyalanır: kariyer log'unu **yeniden içe aktar**, sonra Kadro → Hareketler'de Iván Román "Serbest → Port Vale" (önceden "Crewe Alexandra →").
5. **Geri dönüş:** 1. adımdaki yedeği geri yükle (11. adım).

## 14. Backend bellek (maliyet)
Railway belleği GB-dakika faturalar; backend maliyetinin neredeyse tamamı RAM. Dockerfile `JAVA_TOOL_OPTIONS` ile heap'i 768 MB'ta
sınırlar ve boşta periyodik GC ile kullanılmayan heap'i işletim sistemine geri verir (G1PeriodicGCInterval, MaxHeapFreeRatio).
Yerel ölçüm (21,7 bin oyunculu kariyer log'u): ayarsız 0,6 → 1,35 GB ve orada kalıyordu; ayarlı tepe ~1,1 GB, 90 sn içinde ~0,5–0,6 GB.
İki eşzamanlı içe aktarma OOM'suz. 384 MB tavan içe aktarmada `OutOfMemoryError` verir.
- **Görmelisin:** deploy sonrası Metrics → Memory, içe aktarma tepelerinden sonra ~0,5–0,6 GB'a iner.
- Daha büyük tavan gerekirse servis değişkeni `JAVA_TOOL_OPTIONS` (Dockerfile'dakini tümüyle ezer; tüm bayrakları yaz).
- Loglarda `OutOfMemoryError` görülürse `-Xmx` artır.

## Geri dönüş

- **Önceki dağıtıma dön:** servis → Deployments → önceki dağıtımın üç noktası → Rollback; "Both the Docker image and custom variables are restored" ([deployment actions](https://docs.railway.com/deployments/deployment-actions)). İmaj saklama planla sınırlı: Hobby 72 saat, Pro 120 saat ([plans](https://docs.railway.com/pricing/plans)).
- **Şema:** Flyway migration'ları ileri yönlüdür; geri dönülen sürüm yeni bir migration'ı içermiyorsa açılışta "migration … not found" ile durabilir **[doğrulanmadı — bu çıkışta yeni migration yok]**. Veri geri yükleme: 11. adımdaki yedekten.
- **Taşıma:** 8. adımdaki "başarısızsa".

## Loglar ve gizlilik

- Railway log saklama: Free 3 gün, Trial/Hobby 7, Pro 30, Enterprise 90'a kadar ([logs](https://docs.railway.com/observability/logs)). nginx erişim logu IP'yi maskeli ve sorgu dizesiz yazar (`scripts/deploy/nginx.test.mjs`); uygulama logları e-posta/şifre/token içermez (ProdProfileIntegrationTest).
- Build sırasında değişkenler otomatik `ARG` olmaz; gerekiyorsa Dockerfile'da `ARG` ile ([dockerfiles](https://docs.railway.com/builds/dockerfiles)). Web build'i env istemez (API aynı köken).

## Doğrulanmadı listesi

1. Domainsiz servisin hiç public trafik almadığı (duman testi `--backend-public` ölçer).
2. Edge'in istemcinin `X-Real-IP`/`X-Forwarded-For` başlığını ezip ezmediği (duman testi ölçer).
3. Railway private DNS sunucu adresi (nginx resolv.conf'tan alır).
4. Spring/uvicorn/nginx için Railway'e özel IPv6 rehberi (yok; yerel ölçümle karar).
5. Private URL'de portun zorunluluğu (her zaman yazıyoruz).
6. Postgres sürümü; `PG*` mi `DATABASE_URL` mi "önerilen".
7. Watch paths'in kökü (repo kökü mü root directory mi).
8. Paneldeki restart policy alan adları.
9. Shared variable'ın sealed yapılabilmesi.
10. TCP proxy kaldırılınca `DATABASE_PUBLIC_URL`'in silinmesi.
11. Yedeklemenin hangi planlarda olduğu; Backups sayfası ile Postgres rehberinin saklama süreleri çelişkisi; PITR penceresi.
12. IaC'de dosyada olmayan değişkenlerin davranışı (IaC kullanılmadı).
13. Rollback'te Flyway davranışı.
