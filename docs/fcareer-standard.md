# FC Assist — Backend standardı

Java 17, Spring Boot 3, Jakarta, PostgreSQL + Flyway, Maven (Redis yok, OPS-02). Base package `com.fcareer`, tek kanal `/api/v1`.
Karar kaydı `docs/review/08_DECISIONS_LOG.md`; çelişkide o geçerlidir.

## Kod
- Constructor injection: `@RequiredArgsConstructor` + `private final`. Field `@Autowired` yok.
- Her `if/else/for/while` için `{ }`. Guard clause ve erken return/throw.
- Magic string/number yok: `*Constants`, enum ya da config. Oyun kuralları `seed/ut-rules.fc27.json` (koda gömülmez).
- Loglar `@Slf4j` ve `{}` placeholder; token, şifre, e-posta, capture gövdesi loglanmaz.
- JPA entity'de `@Getter @Setter` (`@Data` yok). Yeni modüllerde `JdbcTemplate` serbest ama SQL repository sınıfında durur; service ve controller'da ham SQL yok.
- Controller ince: doğrula, service'e delege et. İş mantığı, mapping ve try/catch yok.
- `@Transactional` service katmanında. Para/kredi ve çok satırlı yazımda şart; uzun parse işlemi transaction dışında yapılır.
- Zaman: `Clock.systemUTC()`, UTC saklanır. İçerik takvimi Europe/London, gösterim Europe/Istanbul.
- Para: `BigDecimal` ya da tam sayı euro; birim açık yazılır.
- Gereksiz abstraction yok; refactor kod sadeleşince başarılıdır.

## API
- Yol: `/api/v1/...`. Envelope: `ApiResponse` (`SuccessResponseAdvice` sarar), controller ham nesne döner.
- Hata kodları HTTP status ile gider (D-09): 400 doğrulama, 401 oturum, 402 kredi, 403 yetki, 404 yok (başkasının kaynağı da 404), 409 çakışma, 413 gövde, 422 iş kuralı, 426 eski extension, 429 limit, 503 devre dışı.
- Domain hatası `FcareerRuntimeException` alt sınıfı (`httpStatus()` verir). Kullanıcı mesajları `messages.properties` kodlarından.
- Davranış değişikliği commit mesajında `BEHAVIOR CHANGE:`, sözleşme değişikliği `API CONTRACT CHANGE:` ile yazılır ve web aynı seride güncellenir.

## Güvenlik
- Bedava uçlar ücretli içgörü döndürmez (D-01): `withoutPaidInsights`, slot-view 3 aday.
- `careerId` alan her uç sahipliği servis katmanında doğrular (`CareerService.requireOwned`); kapsam `OwnershipMatrixIntegrationTest`'te.
  Admin uçları ADMIN rolü; `X-Admin-Key` yalnız `/admin/ingestion` ve `/admin/ml` (makine-makine).
- Rate limit bellek içi sabit pencereli sayaçla (`WindowCounter`; backend tek örnek, yeniden başlatmada sıfırlanır).
  Kovalar (`RateLimitFilter`): auth 10/dk (IP), kayıt 5/gün (IP), genel API 300/dk (kullanıcı), analiz 60/dk (`/fit/lineup`,
  replika uygula, öneri önizlemesi, başlangıç önerisi, gelişim planı önerisi, altyapı; yeni pahalı ücretsiz uç buraya), taktik analizi 60/dk (`/tactics/analysis`, kendi kovası:
  Lab'da dizilişle birlikte gider), kulüp önerisi 5/dk (`/manager/club-recommendations`, kendi kovası: önbellek dışı istek ~50 kulüp hesaplar),
  LE içe aktarma 10/saat, görsel 1200/dk.
  Kariyer: kullanıcı başına en çok 10 açık (422), günde 20 açma (429) — `CareerLimiter`.
- EA hesap kimlik bilgisi istenmez, saklanmaz, loglanmaz. Sunucu EA'nın oturum gerektiren uçlarını çağırmaz.

## Test
- Testler zorunludur: kabul kriteri önce kırmızı test olarak yazılır.
- `mvn test` (gömülü Postgres: zonky `@AutoConfigureEmbeddedDatabase`, Docker gerekmez; Testcontainers kullanılmaz). Token depoları (Postgres), sayaç ve önbellek (bellek içi) testte de üretimdekiyle aynı; ortak oyun kuralı vektörleri `seed/ut-rules.fc27.json` içinde, web ile paylaşılır.
- Fit engine katsayı değişikliği senaryo testleri (T1–T6) yeşil olmadan commit edilmez.

## Commit
`<ID>: kısa açıklama` (+ `BEHAVIOR CHANGE:` / `API CONTRACT CHANGE:` satırı gerekirse). Bir PR'da tek konu.

## Bilinen sorunlar
- **Gömülü Postgres önbelleği bozulabilir:** Entegrasyon testleri toplu düşer ve loglarda `FATAL: invalid value for parameter "TimeZone"` ile "Gave up waiting for server to start" görünür. Önbellek klasörünü silip testi yeniden çalıştır: `$TMPDIR/embedded-pg`.
