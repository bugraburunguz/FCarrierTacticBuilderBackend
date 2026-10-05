# Azerlottery — Backend House Style & Review Standard

> `com.misli.*` backend microservice'lerinin (ör. `nsoft-virtual-game-service`, `groove-virtual-game-service`, `campaign-aggregator`, user/finance/sportsbook servisleri) **tek referans dokümanı**.
> Bu dosya şu üç eski dokümanı birleştirir ve onların yerini alır: house-style conventions + aggregator geliştirme rehberi + review/refactoring talimatları.
> Amaç: (1) yeni kod/servis yazarken (AI asistanları dahil) mevcut pattern'lere uymak, (2) **davranış değiştirmeden** review/refactor yapmak.
>
> Not: "Claude ile çalışma / ticket-Jira iletişim" kuralları ayrı dosyadadır (`collaboration.md`) — o kod standardı değil, çalışma biçimidir.

---

## 0. Bu dokümanı nasıl kullan

İki mod:

- **Yazma modu** (yeni servis / yeni endpoint / PHP→Java taşıma): Bölüm 3–23 pattern'lerini uygula.
- **Review modu** (mevcut kodu gözden geçir/refactor et): önce **Bölüm 1 (Review Sözleşmesi)** ve **Bölüm 2 (Katman-katman Checklist)**; kalan bölümler "doğru pattern nedir" referansı.

**Review'ı başlatma (tek cümle yeter):**
```
{servis}/{controller veya path} → review-mode, bu dokümana göre. Logic değişmesin.
Örn: user-identity-controller → tüm endpoint flow'u review et (Bölüm 1.4). SAFE REFACTOR uygula, gerisini raporla.
```

Review modunun altın kuralı Bölüm 1.1'de.

---

## 1. REVIEW SÖZLEŞMESİ

### 1.1 Altın kural — LOGIC DEĞİŞMEZ (pazarlıksız)

Bu bir **refactor / kod-kalitesi** işidir, feature geliştirme değil. Aşağıdakilerin **hiçbiri** yapılmaz:

- Yeni iş kuralı **ekleme**, mevcut iş kuralını **kaldırma**.
- Validation / authorization / authentication davranışını değiştirme.
- **Davranışı etkileyebilecek** koşul sırasını değiştirme.
- Default değer, fallback davranışı, exception davranışı, response semantiği değiştirme.
- DB davranışı / persistence semantiği / dış servis davranışı değiştirme.
- "Bence yanlış" diye mevcut davranışı **düzeltme**.
- Öncesinde olmayan bir kontrol/varsayım ekleme.

Mevcut kod nasıl davranıyorsa öyle korunur. Yanlış olduğuna inansan bile bu görevde düzeltmezsin — **POTENTIAL LOGIC ISSUE** olarak raporlarsın (bkz. 1.3).

Karar kuralı: "davranışı değiştirebilecek daha temiz kod" ile "daha az mükemmel ama davranışı koruyan kod" arasında → **her zaman davranışı koru.**

### 1.2 Değişiklik güvenliği — A / B / C (bir edit'i uygulayabilir miyim?)

Her somut edit üç kovadan birine düşer. Kararsızsan bir üst (daha kısıtlı) kovaya çek.

**A) Doğrudan uygula — davranış-nötr (safe refactor):**
- Eksik `{ }` ekleme (Bölüm 5.1).
- `import static` ile sabit erişimini sadeleştirme (yeni sabit üretmeden).
- Kullanılmayan import / ölü değişken temizliği (referanssız olduğu **kanıtlıysa**).
- Format/whitespace/annotation hizalama.
- Yerel değişken ve `private` helper isimlendirme düzeltmesi (public API/DTO alanı **hariç**).
- Magic string/number'ı **mevcut** bir `*Constants`'a taşıma (değer birebir aynı, yeni public sözleşme yok).
- Log placeholder düzeltme: `"x=" + id` → `"x={}", id`.

**B) Öner + davranışı açıkça belirt — sessizce uygulama:**
- `==`→`Objects.equals` / manuel null → `Objects.isNull/nonNull`: null & referans-eşitliği davranışını **değiştirebilir** (unboxing, interne edilmemiş nesneler). Sadece semantik birebir aynıysa.
- Guard-clause'u erken-return'e çevirme / sıralama: **yan etki & değerlendirme sırasını** değiştirebilir. Koşulun side-effect'i varsa dokunma.
- `private` helper extraction: davranış aynı kalmalı; field capture / lazy-eval sırası bozulmamalı.
- Log seviyesi değişimi (`info`↔`warn`): davranış değil ama operasyonel etki.
- Loglardan PII/secret maskeleme: doğru düzeltme ama çıktı formatı değişir → güvenlik bulgusu olarak ayrı ele al.

**C) Asla dokunma — sadece raporla (behavior-changing):**
- HTTP status kodları (özellikle **business error → `ResponseEntity.ok(200)`** deseni, Bölüm 12).
- Dış/callback endpoint response şekli (provider wallet vb.) — `ApiResponse` envelope'a çevirme (Bölüm 10 notu).
- Para: `/1000`, `setScale`, `RoundingMode`, tax, birim dönüşümü (Bölüm 18).
- Validation kümesi: `@Valid`, null-check ekleme/çıkarma, atılan/atlanmayan exception koşulları.
- `@Transactional` sınırı — ekleme/kaldırma/propagation değişimi.
- Client'a ulaşan exception tipi & mesajı, `ErrorCodes`/`MessageCodes` anahtarları, i18n metni.
- Para operasyon sırası, idempotency/kompanzasyon akışı (Bölüm 15/16).
- Feign contract: path, method, header, body/DTO alan adları, `@JsonProperty` kısa key'leri (`"cta"`,`"bai"`,`"tgi"` — wire sözleşmesi).
- Enum backing value, Kafka topic adı, Redis key prefix (dış görünür/kalıcı olanlar).
- **Yeni** endpoint / **yeni** exception / **yeni** iş kuralı — söylenmedikçe.

### 1.3 Bulgu sınıflandırması — bir bulguyu nasıl raporlarım?

A/B/C "edit yapılabilir mi" eksenidir. Rapor ederken bulgunun **türünü** de etiketle:

| Etiket | Ne demek | Aksiyon |
|---|---|---|
| `SAFE REFACTOR` | Yapısal olarak güvenli, davranışı değiştiremez (A, ve kanıtlanmış B) | **Uygula** |
| `POTENTIAL LOGIC ISSUE` | Yanlış görünüyor ama düzeltmek davranış değiştirir | **Dokunma, dokümante et** |
| `API CONTRACT CHANGE` | Dış API'yi değiştirmeyi gerektirir | **Dokunma, öneri olarak yaz** |
| `ARCHITECTURAL RECOMMENDATION` | Mimariyi iyileştirir ama bu görevin kapsamı dışı | **Dokümante et, erteleme öner** |

Sadece `SAFE REFACTOR` uygulanır. Bir değişiklik "davranış-koruyan" olduğu **kanıtlanamıyorsa** uygulanmaz.

### 1.4 Review akışı — endpoint'i izole etme, tüm flow'u izle

Controller'ı tek başına review etme. Her endpoint için zinciri izle:
```
HTTP Request → Controller → Service → Service'in çağırdığı metotlar
            → Model/DTO/Request/Response → Repository/Client/diğer Service → Response
```
Her verinin **nerede doğduğunu, nerede değiştiğini, nerede tüketildiğini** çıkar. Önce anla (hiçbir şey değiştirmeden), sonra bulguları 1.2/1.3'e göre sınıfla, sonra sadece güvenli olanı uygula, sonra doğrula (1.7).

### 1.5 Model ownership & metot imzası

- **`{servis}-model` sahipliği:** Service/controller katmanında duran bir model, gerçekten domain'e aitse (`user-model` gibi) oraya taşınır. Ama alan adları user içeriyor diye **service'e özel** request/response'u `user-model`'e taşıma — sahiplik **sorumluluğa** göredir, alan adına göre değil.
- **Duplicate DTO yaratma.** Yeni model açmadan önce: repo'da eşdeğerini ara → `user-model`'e bak → ilgili servislere bak → mevcut model güvenle kullanılabiliyorsa onu kullan. Sadece gerçek mimari gerekçe varsa yeni model.
- **POST endpoint çok query-param:** mantıksal olarak tek bir isteği temsil ediyorsa `@RequestBody SomeRequest`'e çevirmeyi **öner**. Ama körlemesine her param'ı body'ye çevirme — mevcut API contract, HTTP semantiği, başka client bağımlılığı gözet. Contract değişimi gerekiyorsa **uygulama, `API CONTRACT CHANGE` olarak öner**.
- **Çok parametreli metot:** parametreler anlamlı tek bir domain kavramıysa request/command modeli düşün. `MethodParameters`, `DataHolder`, `Wrapper` gibi anlamsız sınıflar **açma** (proje zaten kullanmıyorsa).

### 1.6 Over-engineering freni

Basit implementasyonu gereksiz karmaşık mimariye çevirme. Gereksiz pattern / factory / strategy / interface / abstraction / generic / util / inheritance / DI katmanı ekleme. Refactor **sınıf sayısı arttığında değil, kod sadeleştiğinde** başarılıdır. (Proje mimarisi teorik saflıktan üstündür; DRY/KISS/SRP dogmatik uygulanmaz.)

### 1.7 Doğrulama — dokunmadan önce ve sonra

- **Backward compatibility:** public metot/endpoint/model/contract değiştirmeden önce repo genelinde tüm kullanımları ara (kim çağırıyor, başka servis mi, test mi, dışa açık mı, serialization/deserialization etkileniyor mu).
- **Cross-service twin kuralı:** bu projede `user-service` ↔ `user-admin-service` aynı kavramı (entity, enum, statü mantığı) genelde **ikisinde de ayrı** tutar. Birinde bir şey değiştirdiğinde kardeş serviste de gerekip gerekmediğini otomatik tara.
- **"Bu branch/feature ne ekledi" tarzı özet:** sadece kendi diff'ine güvenme. Her enum/exception/kolonun hangi commit'te eklendiğini `git log -S"<symbol>"` ile doğrula; feature branch'e mi yoksa eski/base koda mı ait ayırt et.
- **Regresyon:** namespace'li `Nsoft*`/`Groove*` sınıfların paylaşımlı bir util'ini etkileyen değişiklikte **canlı entegrasyonların etkilenmediğini** ayrıca doğrula.
- **Test eklenmez** (söylenmedikçe) — Bölüm 21.
- İş bittiğinde: derleme, ilgili testler, endpoint imzaları, model uyumu, serialization statik kontrol.

### 1.8 Bulgu raporlama biçimi

Her bulgu: `dosya:satır` · katman · **tür (1.3)** · **kova (1.2 A/B/C)** · kısa tanım · (C/POTENTIAL/CONTRACT ise) davranış farkı · önerilen aksiyon. Önce davranışsal/riskli (C, POTENTIAL LOGIC ISSUE, API CONTRACT CHANGE), sonra B, sonra A. A kovası düzeltmeleri toplu "safe cleanup" verilebilir.

### 1.9 Final self-review (teslimden önce)

1. Mevcut iş kuralı değişti mi / eklendi mi / kaldırıldı mı?
2. Validation / exception / API davranışı değişti mi?
3. `user-model`'e ait bir model service'te mi kaldı, ya da service'e özel model gereksiz `user-model`'e mi taşındı?
4. Gereksiz abstraction yarattım mı? POST param'ı yerinde bir request modele mi çevirdim?
5. Kırıcı (breaking) değişiklik var mı? `user-identity-controller` ve doğrudan etkilenenler dışında bir şeye dokundum mu?

Herhangi biri davranış değişimine işaret ediyorsa, "davranış-koruyan" olduğu kanıtlanamıyorsa o değişikliği geri al. Sonuç: **mevcut sistemin daha temiz hali, farklı bir sistem değil.**

---

## 2. Katman-katman Review Checklist

Her katmanda sırayla bak; ihlali 1.2/1.3'e göre etiketle.

**Controller**
- İçinde business logic / mapping / try-catch var mı? → service/mapper/advice'a ait (taşımak davranışı bozmadan; şüpheyse C/POTENTIAL).
- Gereksiz local değişken / tekrar / gereksiz branching / tutarsız naming var mı?
- Base path `@RequestMapping`'te mi; `/api/web/v2` & `/api/mobile/v2` şeması; v1 kalmış mı?
- Header sabitleri `*Constants`'tan mı (`CHANNEL_TYPE`, `X_DOMAIN_TYPE`); inline string?
- Body'de `@Valid`; query-object'te `@ModelAttribute`; JSON'da `@RequestBody`?
- Dönüş tipi servis içinde tutarlı mı (advice sarıyorsa raw / manuel sarıyorsa `ResponseEntity<T>`)?
- Çok query-param'lı POST → mantıksal tek istek mi? (Bölüm 1.5; contract değişecekse öneri)

**Service**
- Metot çok iş mi yapıyor; küçük mü; gereksiz `private` metot / gereksiz abstraction var mı?
- Duplicated logic / duplicated validation / duplicated dış çağrı?
- Para taşıyan / çok satır mutasyonunda `@Transactional` var mı? (**varlığını doğrula, sınırını değiştirme**)
- Çok-tipli dispatch dev `switch/if` yerine handler/factory mi (groove `GrooveRequestFactory`)?
- Feign interface'i doğrudan mı çağrılıyor yoksa `*Service` arkasından mı?
- Async/parallel'de `CompletionException` advice'ta ele alınıyor mu?
- Service'e özel model gerçekten `user-model`'e mi ait? (Bölüm 1.5)

**Persistence (entity / repository / converter)**
- Entity'de `@Data` var mı? (**yasak** — `@Getter @Setter`; entity'de `equals/hashCode` istemiyoruz)
- Auditing (`@CreatedDate`/`@LastModifiedDate`, `@EntityListeners`) + `timestamptz` doğru mu?
- Id `IDENTITY` Long; özel kolonlarda attribute converter (`StringUuidConverter`)?
- Mapping service'e sızmış mı? → `*Converter implements Function<In,Out>` / `*Mapper`.
- Karmaşık `@Query` SQL'i `*Constants`'ta mı; derived query adları doğru mu?
- **Idempotency/concurrency:** iş id'siyle `@Lock(PESSIMISTIC_WRITE)` lock-then-read? (Bölüm 15)
- N+1: lazy koleksiyon döngüde mi; gerekli `join fetch`/`@EntityGraph`? (sorgu sonucunu değiştiriyorsa C)
- Yeni kolon önermeden önce: mevcut alanların hangisi zaten kalıcı/immutable, gerçekten eksik olan ne? (sadece eksik olanı öner)

**Feign client**
- `name` + `url = "${<service>.url}"`; hardcoded URL yok; downstream adı `BackendServices`'ten.
- Header sabitleri `*Constants`/`HttpHeaders`; body'de `@Valid`; query-object'te `@SpringQueryMap`.
- Her aggregator'ın kendi `ErrorDecoder`'ı var mı?

**Exception & Advice**
- Domain hatası `MisliRuntimeException` alt sınıfı, sabit mesajlı mı?
- Advice: business/expected → `ResponseEntity.ok` + `ApiResponse.fail(code)` + `warn`; unexpected → 5xx + `error` + throwable?
- `FeignException` (+ `Unauthorized`) explicit map; `CompletionException` case'i var mı?
- Kullanıcıya giden mesaj `MessageService.getMessage(code,args)`'tan mı?
- ErrorDecoder: bilinen kod → tipli exception; bilinmeyen → `defaultDecoder`/`DOWNSTREAM_ERROR`; çıplak `RuntimeException` sadece gerçekten bilinmeyen (`misli.999999`).

**Configuration / properties**
- Prefix başına tek `*Properties`; `@ConfigurationProperties`; `${ENV:default}` override?
- Secret/credential hardcoded mı? (**yasak**)

**Redis / sessions**
- Key `*Constants` prefix + id; değer JSON; **write'ta TTL** set ediliyor mu?
- Token POJO'ları küçük/serializable (`@Builder`)?

**Kafka / events**
- Topic adı config/constants; publish hatasında `failed_message`'a yazılıyor mu (event düşmüyor)?
- Event emit kritik para yolundan ayrı mı?

**Money**
- İçeride `BigDecimal`; display/wire'da `setScale(2, HALF_UP)`; birimler net mi?
- Aggregator'da `/1000` doğru mu? (**hesabı değiştirme** — Bölüm 18)

**Logging**
- `@Slf4j`, placeholder `{}`; correlating id; **token/şifre/PII yok**?

**Mapper (aggregator)**
- `@Component @RequiredArgsConstructor`; response `@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude(NON_NULL)`.
- `@JsonProperty` kısa key'leri sözleşme — **değiştirme**. i18n `getMessage(MessageCodes.KEY, args...)`.

**Genel (her katman)**
- Constructor injection (`@RequiredArgsConstructor` + `private final`); field/`@Autowired` yok.
- Naming: rol suffix'leri; boolean predicate isimleri.
- Class-level açıklayıcı Javadoc/comment yok (Bölüm 5.9).
- Duplication / ölü kod / sızan sorumluluk / gereksiz coupling.

---

## 3. Tech baseline

- **Java 17**, **Spring Boot 3.x**, **Jakarta** (`jakarta.*`, `javax.*` değil).
- **PostgreSQL** + Spring Data JPA (Hibernate, `ddl-auto: validate` — şema dışarıda migrate edilir).
- **Redis** (session, kısa-TTL cache), **Kafka** (Spring Kafka producer), **OpenFeign** (servis-arası çağrı).
- **Lombok** her yerde. **Micrometer** tracing. **springdoc** Swagger.
- Build: Maven (`pom.xml`), app adı `@project.name@`.

---

## 4. Proje & paket yapısı

İki layout var. **Yeni servis/modül için campaign-aggregator modüler stili tercih edilir.**

### Tercih edilen (campaign-aggregator) — feature/area + `common`
```
com.misli.<service>
├── web/                 # bir delivery alanı (web, mobile, admin…)
│   ├── controller/
│   ├── service/
│   ├── model/response/   (ve request/)
│   └── mapper/
├── mobile/              # başka delivery alanı, aynı alt yapı
│   └── controller/
└── common/             # alanlar arası paylaşımlı
    ├── configuration/   (+ swagger/)
    ├── client/          (+ dto/, configuration/)
    ├── advice/
    ├── exception/
    ├── constants/
    ├── model/enums/
    └── service/
```

### Legacy/flat (nsoft, groove) — layer-first
```
com.misli.<service>
├── controller/  service/(impl/)  client/  producer/  converter/
├── persistence/ ( entity/  repository/  service/  converter/  enums/ )
├── model/ ( dto/  <provider>/request|response  misli/request|response )
├── configuration/ ( properties/  redis/  kafka/  swagger/ )
├── advice/ ( exception/  model/  decoder/  constants/ )
├── enums/  constants/  utils/  util/
```

### Aggregator: iki Maven modülü
```
{servis}-model/          ← DTO, enum, request/response (birden fazla servis kullanacaksa ayrı modül)
{servis}-aggregator/     ← controller, service, mapper, feign client, exception
```
Sadece bu aggregator kullanıyorsa model, aggregator'ın kendi `web/model/` paketinde durur.

Layout'tan bağımsız:
- Dosya başına tek public tip; sorumlulukla paketle.
- Provider/entegrasyon kodu **namespace'lidir** (`service.nsoft`, `Nsoft*`, `Groove*`). **Yeni entegrasyon eklerken canlı bir entegrasyonun sınıfları düzenlenmez; izole, namespace'li yeni kod eklenir.**

---

## 5. House style kuralları (ZORUNLU)

1. **Her `if`/`else`/`for`/`while`/`do` için `{ }`** — tek satırlık dahil.
   ```java
   // ❌ if (timestamp == null) return "";
   // ✅
   if (timestamp == null) {
       return "";
   }
   ```
2. **Guard clause + erken return/throw**, her biri throw öncesi loglanır. Önce validate, happy-path düz.
   ```java
   Optional<NsoftToken> token = tokenService.getToken(request.getTpToken());
   if (token.isEmpty()) {
       log.info("Token invalid or timed-out! token : {}", request.getUser());
       throw new TokenExpireException();
   }
   ```
3. **Sadece constructor injection** — `@RequiredArgsConstructor` + `private final`. Field `@Autowired` yok.
4. **Metotlar küçük**; `private` helper'lara cömertçe böl (metot başına tek sorumluluk).
5. **Sabitleri static-import** (`import static ...Constants.X`).
6. **Magic string/number yok** — `*Constants` veya enum.
7. **Loglar placeholder `{}`**, string concat yok; context (id) taşır. Secret/PII yok.
8. **Kaçabilen lookup'lar için `Optional`**; `==`/manuel null yerine `Objects.equals`/`isNull`/`nonNull`.
9. **Class-level açıklayıcı Javadoc/comment yok.** Bağlam PR açıklamasına.

> Review notu: 1–7,9 → A kovası (safe). 8'deki `==`→`Objects.equals` → B (null semantiği).

---

## 6. Naming

- Sınıflar `PascalCase`; rol suffix'i — `*Controller`, `*Service`, `*ServiceImpl`, `*Client`, `*Repository`, `*Entity`, `*Properties`, `*Converter`, `*Constants`, `*Exception`, `*RequestDto`/`*ResponseDto` (veya `*Request`/`*Response`).
- Metot/field `camelCase`; sabit `UPPER_SNAKE_CASE`.
- Boolean'lar predicate (`isActive...`, `shouldNotApplyTax`, `taxLimitNotExceeded`).
- Feign metot adları uzak operasyonu birebir yansıtır.

---

## 7. Dependency injection

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ReserveFundsService {
    private final TokenService tokenService;
    private final FinanceService financeService;
    private final NsoftProperties properties;
}
```
`private final` + `@RequiredArgsConstructor`. Setter/field injection yok.

---

## 8. Lombok

- **Service/component:** `@RequiredArgsConstructor`, `@Slf4j`.
- **DTO/request/response:** `@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor @ToString` (+ `@JsonInclude(NON_NULL)`; `@JsonProperty` zorunlu/rename).
- **JPA entity:** `@Getter @Setter` (**`@Data` DEĞİL**); `@Entity @Table(name=...)`.
- **Küçük value/config holder:** `@Data` kabul (`ApiResponse`, `FailedMessageEntity`), entity hariç.
- **Enum:** `@Getter @AllArgsConstructor` + backing value.

---

## 9. Controllers

İnce — input doğrula, service'e delege et, dön. Business logic yok, mapping yok, try/catch yok.

```java
@RestController
@RequestMapping("/api/web/v2")
@RequiredArgsConstructor
public class CampaignController {
    private final CampaignService campaignService;

    @GetMapping("/campaigns/list")
    public List<CampaignResponse> list(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(name = CHANNEL_TYPE) ChannelType channelType) {
        return campaignService.getListForUser(authorization, channelType);
    }
}
```
- Web `/api/web/v2/...`, Mobile `/api/mobile/v2/...` (mobile web ile aynı service'i kullanır). **v1 kaldırıldı.**
- Base path `@RequestMapping`'te. Header adları `*Constants`'tan (`CHANNEL_TYPE`="channel-type", `X_DOMAIN_TYPE`="X-Domain-Type").
- Body'de `@Valid`; query-param object'te `@ModelAttribute`; JSON'da `@RequestBody`.
- `ResponseBodyAdvice` sarıyorsa raw dön (campaign stili); manuel sarıyorsa `ResponseEntity<T>` (nsoft/groove). Servis içinde tutarlı ol.

Auth gerektiren endpoint:
```java
@GetMapping("/campaign/{id}/validate")
public void validate(
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
        @PathVariable Long id) {
    campaignService.validate(authorization, id);
}
```
(PHP `$this->successResponse(null)` → Java `void`.)

---

## 10. Standart response envelope

`ApiResponse<T>` + başarılı dönüşleri saran `ResponseBodyAdvice`:
```java
@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private List<ApiError> error;

    public static <T> ApiResponse<T> success(T data) { ... }
    public static ApiResponse<Void> fail(String message, String code) { ... }
}
```
- `CampaignSuccessAdvice implements ResponseBodyAdvice` — `ApiResponse` olmayan body'yi `ApiResponse.success(...)`'a sarar. Controller raw döner.
- **Not:** Dış-kontratlı callback endpoint'leri (provider wallet vb.) provider'ın response şeklini birebir izler — iç `ApiResponse` envelope **uygulanmaz** (C kovası).

---

## 11. Services

- `@Service`, tek sorumluluk, `@Slf4j`.
- **Para taşıyan / çok satır mutasyonunda `@Transactional`.**
- Çok-tipli dispatch: strategy/handler — groove `GrooveRequestFactory` + `GrooveRequestHandler<T,R>`; dev `switch/if` yerine.
- Async/parallel çağrı `CompletionException` olarak çıkar — advice'ta ele al.

---

## 12. Exception handling

- Base: `MisliRuntimeException extends RuntimeException`. Domain hatası sabit mesajlı tipli alt sınıf:
  ```java
  public class CampaignNotFoundException extends MisliRuntimeException {
      public CampaignNotFoundException() { super("Campaign not found"); }
  }
  ```
- Central `@RestControllerAdvice`:
  - **Business/expected → `ResponseEntity.ok(...)` (HTTP 200)** + `ApiResponse.fail(message, code)`; `warn`.
  - **Unexpected/technical** → `5xx`/uygun status; `error` + throwable.
  - `FeignException` (+ `Unauthorized`) explicit map.
  - Kullanıcı mesajı `MessageService.getMessage(code, args)` (i18n), `ErrorCodes`/`MessageCodes` ile; hardcoded string yok.
  - `CompletionException` handler'ına (parallel Feign) case eklenir.
- Feign çeviri: `CustomErrorDecoder`/`*ErrorDecoder` downstream HTTP hatasını tipli exception'a çevirir.

### Aggregator akışı
```
Downstream hata kodu → ErrorDecoder → özel Exception → ExceptionAdvice → ApiResponse(success=false)
```
- ErrorDecoder: bilinen kod → `if (code == XYZ)` → özel exception. Bilinmeyen → `defaultDecoder.decode()` → `DOWNSTREAM_ERROR`. Çıplak `RuntimeException` sadece gerçekten bilinmeyen (`misli.999999`/UNKNOWN_ERROR).
  ```java
  if (code == ONLY_NEW_MEMBER) {
      log.warn("Only new member can participate");
      return new OnlyNewMemberException();
  }
  ```
- ExceptionAdvice: her exception için ayrı `@ExceptionHandler`; kod `ErrorCodes`, mesaj `MessageService`.
  ```java
  @ExceptionHandler(OnlyNewMemberException.class)
  public ResponseEntity<ApiResponse<Void>> handleOnlyNewMember(OnlyNewMemberException ex) {
      log.warn("Only new member can participate in this campaign");
      return ResponseEntity.ok(buildErrorResponse(ONLY_NEW_MEMBER_ERROR));
  }
  ```
- Exception sınıfı:
  ```java
  public class OnlyNewMemberException extends MisliRuntimeException {
      public OnlyNewMemberException() { super("Only new members can participate!"); }
  }
  ```

### Hata kodları & mesajlar
- `ErrorCodes.java`: `public static final String ONLY_NEW_MEMBER_ERROR = "misli.exception.0004";`
- Üç properties (`message.properties`, `message_az.properties`, `message_tr.properties`); varsayılan locale **az-AZ**:
  ```properties
  misli.exception.0004=Bu kampaniya yalnız başlanğıc tarixindən sonra qeydiyyatdan keçən istifadəçilər üçün aktivdir.
  ```
- Anahtarlar `MessageCodes.java`'da; mapper/advice içinde `getMessage(KEY, args...)`.

> **Review notu:** Yeni exception **söylenmeden oluşturulmaz** — mevcut kullanılır. Status kodu, exception tipi, mesaj anahtarları → C kovası.

---

## 13. Feign clients

```java
@FeignClient(name = "finance-service", url = "${finance-service.url}")
public interface FinanceServiceClient {
    @PostMapping("/transaction/spend/money-bonus")
    SpendingMoneyAndBonusResponse spendMoneyAndBonus(
            @RequestHeader(AUTHORIZATION) String token,
            @RequestHeader(name = X_DOMAIN_TYPE) Domain domain,
            @Valid @RequestBody SpendingMoneyAndBonusRequest request);
}
```
- `name` + `url = "${<service>.url}"` (env-override). Downstream adı `BackendServices` sabitlerinden (PHP `BackendServices.php`).
- Header sabitleri `*Constants`/`HttpHeaders`; query-object'te `@SpringQueryMap`; body'de `@Valid`.
- Client'ı ince `*Service` arkasına al — business kod Feign'i **doğrudan çağırmaz**.
- Her aggregator'ın kendi `ErrorDecoder`'ı var.

---

## 14. Configuration properties

```java
@Getter @Setter @Component
@ConfigurationProperties(prefix = "nsoft")
public class NsoftProperties {
    private String host;
    private String currency;
    private Long playerTokenTtlInMins;
    // helper getter serbest: ör. CSV → List<String>
}
```
- Prefix başına tek `*Properties`. `${ENV_VAR:default}` ile override.
- Secret hardcoded yok — env/secret store, ortam bazlı.

---

## 15. Persistence

- Entity: `@Entity @Table(name="snake_case")`, `@Getter @Setter`, auditing:
  ```java
  @CreatedDate @Column(columnDefinition="timestamptz", updatable=false) private LocalDateTime createdDate;
  @LastModifiedDate @Column(columnDefinition="timestamptz") private LocalDateTime lastModifiedDate;
  // sınıf @EntityListeners(AuditingEntityListener.class)
  ```
- `@GeneratedValue(strategy = IDENTITY)` Long id; özel kolonlarda attribute converter (`StringUuidConverter`).
- Entity ⇆ dto dönüşümü `*Converter implements Function<In,Out>` (`@Component`) veya `*Mapper` — service'te durmaz.
- Repository: `extends JpaRepository<Entity, Long>`; derived query; karmaşık okuma `@Query` (JPQL/`nativeQuery=true`), SQL `*Constants`'ta.
- **Idempotency/concurrency:** iş id'siyle lock-then-read:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM NsoftTransactionEntity t WHERE t.paymentId = :paymentId")
  Optional<NsoftTransactionEntity> findByPaymentIdForWrite(String paymentId);
  ```
- Başarısızlıkları `failed_message`'a `FailedMessageService` ile dead-letter et.

### Idempotency pattern (para/callback)
Groove `WagerServiceImpl`/`RollbackServiceImpl`:
1. İşlemi dış id'siyle **önce** ara.
2. Varsa → **yeniden kurgulanmış "idempotency" response** dön; parayı tekrar hareket ettirme, throw etme.
3. Yoksa: validate → para (finance) → persist → event → success.
4. `@Transactional`; local persist başarısızsa finance'ı kompanse et.

> Tekrar-güvenli callback'lerde "duplicate exception" yerine "önceki sonucu dön".

---

## 16. Redis / sessions

- `RedisTemplate<String,String>`; key `*Constants` prefix + id; değer JSON.
- **Write'ta TTL set et.** Token POJO'ları küçük serializable (`@Builder`).
- Uzun ömürlü ihtiyaçta groove **Redis + DB** fallback tutar.

---

## 17. Kafka & events

- Producer'lar `producer/`; topic config/constants'tan.
- Publish başarısızsa `failed_message`'a yaz. Event emit'i kritik para yolundan ayrı tut.

---

## 18. Money handling

- İçeride `BigDecimal`; display/wire'da **2 dp `RoundingMode.HALF_UP`** (groove `scaleUserBalance`). Tax `TaxService`'te.
- Birimlerde açık ol; minor-unit int ile decimal'i converter olmadan karıştırma.

### Aggregator: /1000 formatlama
Downstream para/miktar değerleri **1000'e bölünerek** sunulur.

| Dönüş tipi | Yöntem |
|---|---|
| `BigDecimal` | `.divide(BigDecimal.valueOf(1000)).setScale(2, RoundingMode.HALF_UP)` |
| String (para birimiyle, `"100.00₼"`) | `getMessage(CURRENCY_SYMBOL, formatAmount(value.divide(BigDecimal.valueOf(1000))))` |
| String (sıfırlar atılmış, conditional bonus) | `formatConditionalAmount(value.divide(BigDecimal.valueOf(1000)))` → `stripTrailingZeros().toPlainString()` |

- `formatAmount`: null→`"0.00"`, değer→`"100.00"` (`Locale.US`, 2 basamak).
- `formatConditionalAmount`: null→`"0"`, trailing-zero'suz (`100`→`"100"`, `3.50`→`"3.5"`).
- `formatCouponRate(Double)`: `String.format(Locale.US, "%.2f", rate)`.

> **Review notu:** `/1000`, scale, rounding, format metotları iş kuralı — C kovası, dokunma.

---

## 19. Logging

- `@Slf4j`; `log.info("... id : {}", id)`.
- `info` akış+outcome, `warn` beklenen hata, `error` (throwable) beklenmeyen.
- Correlating id her zaman; **token/şifre/PII loglanmaz** (maskele).

---

## 20. Constants & enums

- Sabitler `*Constants`'ta; static-import.
- Enum backing value + `@Getter @AllArgsConstructor`:
  ```java
  @Getter @AllArgsConstructor
  public enum TransactionStatus {
      CREATED(0), CONFIRMED(1), CANCELLED(2), REVERTED(3), WON(4), LOST(5);
      private final int value;
  }
  ```
> Backing value dış sözleşme olabilir — review'da değiştirme (C).

---

## 21. Testing (uzlaştırılmış)

- **Review/refactor modu:** **söylenmedikçe test yazma/ekleme.**
- **Yeni feature modu:** service'leri unit-test et — idempotency, concurrency, compensation, validation/guard. Provider entegrasyonu için contract test (request/response şekli + beklenen HTTP status).
- Her modda: değişikliğin **canlı/diğer entegrasyonları etkilemediğini** doğrula (Bölüm 1.7).

---

## 22. Aggregator iş akışı (yazma modu)

### Hızlı görev formatı
```
Aggregator   : {{aggregator-proje-adı}}   # campaign-aggregator, user-aggregator, payment-aggregator...
PHP Proje    : {{php-proje-klasörü}}
Eklenecek    : {{path | path1, path2 | "kelime" geçen tüm pathler}}
```
Asistan PHP projesini okur; hangi controller/service/feign metodunun oluşacağına kendi karar verir.

### PHP → Java taşıma
```php
class GetCampaignById extends AbstractController {
    public function __invoke(Request $request, int $id): JsonResponse {
        $serverResponse = $this->campaignService->getById($id);
        $minifyResponse = $this->campaignMinifyService->minify($serverResponse->toArray()['data']);
        return $this->successResponse($minifyResponse);
    }
}
```
1. **Feign Client**: downstream çağrısı.
2. **Service**: iş mantığı + mapper.
3. **Controller (web + mobile)**: path & header'ları PHP'den kopyala, service'i çağır.
4. **Mapper** (gerekiyorsa): PHP minify/transform → Java mapper.
5. **Return type**: PHP `$this->successResponse(null)` → Java `void`.
- PHP `$this->checkAuthorization($request)` → Java `@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization`.

### Mapper kuralları
- `@Component @RequiredArgsConstructor`; response `@Data @Builder @NoArgsConstructor @AllArgsConstructor`; `@JsonInclude(NON_NULL)`.
- `@JsonProperty("kısaAd")` — JSON key kısaltma (`"cta"`, `"bai"`, `"tgi"`). i18n `getMessage(MessageCodes.KEY, args...)`.

### Commit formatı
```
branch-adı : yapılan iş kısaca
```
Her anlamlı aşama sonrası commit.

---

## 23. Don'ts (birleşik)

- ❌ Field/`@Autowired` injection. ❌ `@Data` JPA entity'de. ❌ Brace-less `if`.
- ❌ Controller'da business logic / mapping / try-catch. ❌ Feign'i business kodundan doğrudan çağırma.
- ❌ Hardcoded URL/secret/kullanıcıya-giden string. ❌ PII/secret loglama.
- ❌ Canlı entegrasyonun sınıflarını yeni entegrasyon için düzenleme — izole, namespace'li yeni kod ekle.
- ❌ Exception yutma / failed event düşürme.
- ❌ v1/v2 dışında path — `v2` (v1 kaldırıldı).
- ❌ **Söylenmeden** yeni exception / test class.
- ❌ Gereksiz abstraction / pattern / wrapper (Bölüm 1.6).
- ❌ **Review modunda** davranış/iş kuralı değiştirme — raporla, uygulama.

---

## Ek: Uzlaştırılan kararlar

Bu doküman 3 kaynağı (conventions + aggregator guide + review instructions) birleştirirken:

1. **Test defaultu:** review/refactor modunda söylenmedikçe test yok; unit-test yeni-feature moduna ait (Bölüm 21).
2. **Response envelope:** provider-callback ham kontratı iç `ApiResponse`'a çevrilmez (Bölüm 10, C).
3. **Business error status:** `ResponseEntity.ok(200)` + fail envelope bilinçli tercih — review'da "bug" sanılıp düzeltilmez (Bölüm 1, 12).
4. **Yeni exception/kontrol:** her modda söylenmeden eklenmez (Bölüm 12, 23).
5. **Bulgu iki eksende sınıflanır:** edit-güvenliği A/B/C (1.2) + bulgu türü SAFE REFACTOR / POTENTIAL LOGIC ISSUE / API CONTRACT CHANGE / ARCHITECTURAL RECOMMENDATION (1.3). Sadece SAFE REFACTOR uygulanır.
