---
paths:
  - "**/controller/**"
  - "**/*Controller.java"
  - "**/service/**"
  - "**/*Service.java"
  - "**/advice/**"
  - "**/*Advice.java"
  - "**/exception/**"
---

# Spring katmanları

## Controller
- İnce: input doğrula, service'e delege et, dön. Business logic, mapping ve try/catch yok.
- Base path `@RequestMapping`'te, operasyon alt path'te. Aynı ön ek altındaki operasyonlar tek controller'da toplanır.
- Yol sürümlü (`/api/v1/...`); projede kanal ayrımı yoksa tek kanal.
- Header adları `*Constants`'tan alınır.
- Body'de `@Valid`; query-param object'te `@ModelAttribute`; JSON'da `@RequestBody`.
- Header, özel status ya da body'siz yanıt gerekmiyorsa `ResponseEntity` yok, doğrudan DTO dönülür. Body'siz başarı `void`.
- Entity controller'dan dışarı çıkmaz; request/response DTO kullanılır.
- Auth gerektiren uçta kullanıcı kimliği oturum/token'dan alınır, request body'sinden değil.
- Kaynağa id ile erişen her uç sahipliği service katmanında doğrular. Başkasının kaynağı için 404 dönülür, varlığı sızdırılmaz.

## Response envelope
- Başarılı yanıtlar tek bir `ApiResponse<T>` zarfıyla (`success`, `data`, `message`, `error`) `ResponseBodyAdvice` üzerinden sarılır; controller ham nesne döner.
- Dış sözleşmeli uçlar (üçüncü taraf callback vb.) karşı tarafın response şeklini birebir izler, zarf uygulanmaz.
- Servis içinde tutarlı ol: ya advice sarar ya manuel sarılır, ikisi karışmaz.

## Hata yönetimi
- Hata HTTP status koduyla gider: 400 doğrulama, 401 oturum, 403 yetki, 404 yok (başkasının kaynağı da 404), 409 çakışma, 422 iş kuralı, 429 limit.
- Domain hatası ortak bir runtime exception'ın alt sınıfıdır ve HTTP status'unu kendisi taşır. Merkezi `@RestControllerAdvice` hepsini zarfa çevirir.
- Kullanıcıya giden mesajlar koda gömülmez, `messages.properties` kodlarından gelir.
- Ticket/iş istemiyorsa ortak exception, advice ve hata kodu sınıflarına dokunulmaz; mevcut exception kullanılır.
- Response'a bilgilendirme alanı (kalan süre, kalan hak) sorulmadan eklenmez.
- Exception yutulmaz; her throw öncesi bağlamlı log yazılır.

## Service
- `@Service`, tek sorumluluk, `@Slf4j`.
- `@Transactional` service katmanında durur; controller ve repository'de değil.
- Para/kredi ve çok satırlı yazımda `@Transactional` şart. Uzun parse/dış çağrı işlemi transaction dışında yapılır.
- Entity ⇆ DTO dönüşümü service'te durmaz; `*Converter` ya da mapper'a gider (`to*` isimli metotlar).
- Dış sistem çağrısı business kodundan doğrudan değil, ayrı bir client sınıfı üzerinden yapılır.
- Async/paralel çağrılar `CompletionException` olarak çıkar; advice'ta ele alınır.
- Yeni engelleme kuralı (blok, kilit, limit) ilgili tüm uçlarda diğer kontrollerden önce çalışır; her uç için "kural aktifken" testi yazılır.
- Pahalı ya da ücretli iş üreten uç, ilgili koruma (kredi, rate limit) arkasında durur; ücretsiz uç ücretli alanı döndürmez.

## Güvenlik
- Kimlik bilgisi, token, şifre loglanmaz, response'a ve rapora girmez.
- Rate limit pahalı ve kimliksiz uçlarda (giriş, kayıt, şifre sıfırlama) uygulanır; yeni pahalı uç mevcut kovaya eklenir.

## Don'ts
- Controller'da business logic, mapping, try/catch.
- Service'te ham SQL.
- Entity'yi doğrudan response yapmak.
- Kullanıcı kimliğini request body'sinden almak.
- Başkasının kaynağına 403 ile varlığını ele vermek.
