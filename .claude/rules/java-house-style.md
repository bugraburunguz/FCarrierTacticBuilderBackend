---
paths:
  - "**/*.java"
---

# Java house style

## Temel
- Jakarta (`jakarta.*`, `javax.*` değil). Maven. Lombok her yerde.
- Java ve Spring Boot sürümü projenin `pom.xml`'indedir; kural dosyasında sürüm varsayılmaz.
- Var olan projede o projenin paket düzenine uyulur. Yeni proje en yakın projeyi şablon alır.
- Dosya başına tek public tip; sorumlulukla paketle.
- Paket düzeni feature-first ise (`auth`, `career`, `album`…) her feature kendi controller/service/repository'sini taşır; katman-first ise mevcut düzen korunur.

## Zorunlu kurallar
1. Her `if`/`else`/`for`/`while`/`do` için `{ }`, tek satırlık dahil.
2. Guard clause ve erken return/throw. Önce validate, happy-path düz.
3. Yalnız constructor injection: `@RequiredArgsConstructor` + `private final`. Field `@Autowired`, setter injection yok.
4. Metotlar küçük; `private` helper'lara bölünür, metot başına tek sorumluluk.
5. Magic string/number yok: `*Constants`, enum ya da config. Sabitler static-import edilir.
6. Kaçabilen lookup'larda `Optional`; null kontrolü `Objects.equals`/`isNull`/`nonNull` ile.
7. Kod içinde açıklayıcı yorum ve Javadoc yok. Sebep PR açıklamasında ya da sohbette anlatılır.
8. Exception yutulmaz.

## Metot isimleri
- Metot adı fiille başlar, istisnasız; `private` helper'lar dahil: `buildFailCountKey`, `createBlockedException`, `findUserById`, `sendMail`.
- Boolean metotlar `is`/`has`/`can`/`should` ile başlar (`isActivationCodeMatched`).
- Tek istisna converter/mapper dönüşüm metotlarıdır: `toDto`, `toEntity`, `toResponse`.
- `failCountKey`, `blockedException`, `userInfo` gibi isim-tamlaması metot adı yazma.

## Naming
- Sınıflar `PascalCase`, rol suffix'i taşır: `*Controller`, `*Service`, `*Repository`, `*Entity`, `*Properties`, `*Converter`, `*Constants`, `*Exception`, `*Request`/`*Response`.
- Metot/field `camelCase`; sabit `UPPER_SNAKE_CASE`.

## Lombok
- Service/component: `@RequiredArgsConstructor`, `@Slf4j`.
- DTO: `@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor`; JSON'da `@JsonInclude(NON_NULL)`.
- JPA entity: `@Getter @Setter`, `@Data` yok.
- Küçük value/config holder'da `@Data` kabul; entity hariç.
- Enum: `@Getter @AllArgsConstructor` + backing value. Backing value dış sözleşme olabilir, review'da değiştirme.

## Logging
- `@Slf4j`, `{}` placeholder, string concat yok.
- `info` akış ve sonuç, `warn` beklenen hata, `error` (throwable ile) beklenmeyen.
- Correlating id her zaman loglanır; token, şifre, e-posta, PII ve secret loglanmaz.
- Guard clause'larda throw öncesi log yazılır.

## Zaman
- `Clock` enjekte edilir (`Clock.systemUTC()`); UTC saklanır, gösterim dönüşümü sınırda yapılır.

## Para
- `BigDecimal` ya da tam sayı; birim açık yazılır. `double`/`float` ile para tutulmaz.

## Refactor ve review
- Refactor ve review'da davranış (business logic) değişmez. Yanlış bulunan davranış o görevde düzeltilmez, `POTENTIAL LOGIC ISSUE` olarak raporlanır.
- Davranışı değiştiren öneri açıkça `BEHAVIOR CHANGE` ile etiketlenir; sessizce uygulanmaz.
- Gereksiz abstraction/pattern/factory/wrapper açılmaz. Refactor kod sadeleşince başarılıdır, sınıf sayısı artınca değil.
- Çok-tipli dispatch gerçekten gerekiyorsa `switch`/`if` zinciri yerine strategy/handler; tek tipli yerde açılmaz.
- Dosyayı düzenlemeden önce diskteki hali okunur; kullanıcının paralel değişikliği geri alınmaz.

## Test
- Feature/fix: önce kırmızı test, sonra kod. Test mevcut test sınıfına eklenir; yeni test sınıfı gerekiyorsa önce sorulur (projenin kendi CLAUDE.md'si "testler zorunlu" diyorsa o geçerlidir).
- Review/refactor modunda söylenmedikçe test yazılmaz.
- Değişikliğin diğer feature'ları etkilemediği doğrulanır.

## Don'ts
- Field/`@Autowired` injection. `@Data` JPA entity'de. Brace-less `if`.
- Controller'da business logic, mapping, try/catch.
- Hardcoded URL, secret, kullanıcıya giden string.
- PII/secret loglama.
- Söylenmeden yeni exception türü ya da yeni test sınıfı.
- Gereksiz abstraction, wrapper.
- Kod yorumu ve Javadoc.
