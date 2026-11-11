---
paths:
  - "**/persistence/**"
  - "**/entity/**"
  - "**/repository/**"
  - "**/*Entity.java"
  - "**/*Repository.java"
  - "**/converter/**"
  - "**/db/migration/**"
  - "**/src/main/resources/**"
  - "**/*Properties.java"
  - "**/configuration/**"
  - "**/config/**"
---

# Persistence, migration ve configuration

## Entity
- `@Entity @Table(name = "snake_case")`, `@Getter @Setter`, `@Data` yok.
- Id: `@GeneratedValue(strategy = IDENTITY)` ile `Long`; özel kolonlarda attribute converter.
- Auditing: `@CreatedDate` (`updatable = false`) ve `@LastModifiedDate`, sınıfta `@EntityListeners(AuditingEntityListener.class)`.
- Zaman kolonları UTC saklanır.
- Entity ⇆ DTO dönüşümü `*Converter`'da (static metotlu, `@NoArgsConstructor(access = PRIVATE)`) ya da bağımlılığı varsa `@Component` mapper'da. Dönüşüm service'te durmaz.

## Repository
- `extends JpaRepository<Entity, Long>`.
- Sorgu önceliği: derived, sonra JPQL, native yalnız son çare.
- Ham SQL (`JdbcTemplate`, native) repository sınıfında durur; service ve controller'da SQL yok. Uzun SQL `*Constants`'a alınır.
- Çakışma riski olan yazımda iş id'siyle lock-then-read:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT t FROM PaymentEntity t WHERE t.paymentId = :paymentId")
  Optional<PaymentEntity> findByPaymentIdForWrite(String paymentId);
  ```

## Transaction
- `@Transactional` service katmanında; para/kredi ve çok satırlı yazımda şart.
- Uzun parse ve dış çağrı transaction dışında yapılır.

## Idempotency
Tekrar gelebilecek istek (callback, yeniden deneme, çift tıklama) için:
1. İşlemi dış/iş id'siyle önce ara.
2. Varsa önceki sonucu dön; işlemi tekrar uygulama, "duplicate" diye throw etme.
3. Yoksa: validate, uygula, persist, sonuç dön.
4. Yan etki (para/kredi) uygulandıktan sonra local persist başarısız olursa kompanse et.

## Flyway migration
- Şema yalnız migration ile değişir; `ddl-auto: validate`.
- Migration geri alınabilir değilse (kolon/tablo silme, tip daraltma) önce dry-run ve yedek.
- Veri silme: dry-run, git dışında yedek, tek transaction, kullanıcı onayı. `DROP`, `TRUNCATE`, `DELETE`, `docker compose down -v` onaysız çalıştırılmaz.
- Prod veritabanında hesap açılmaz, veri yazılmaz.
- Yeni kolon önermeden önce mevcut alanların zaten kalıcı olup olmadığına bakılır.

## Configuration
- Prefix başına tek `*Properties`: `@ConfigurationProperties(prefix = "...")`, `@Getter @Setter`. Helper getter serbest.
- Değer `${ENV_VAR:default}` ile override edilir; default yalnız secret olmayan değerlerde.
- Literal secret default yok: şifre, JWT secret, API key, paylaşılan anahtar koda ve `application*.yml` içine yazılmaz; env/secret store'dan gelir.
- `.env` okunmaz; anahtar varlığı gerekiyorsa Buğra'ya sorulur.
- Secret commit'e, loga, rapora, dokümana girmez.
- Oyun/iş kuralı değerleri (eşik, limit, fiyat aralığı) koda gömülmez; config ya da seed dosyasına alınır.
- Ortama özel değerler profile dosyasında (`application-<profil>.yml`), ortak değerler `application.yml`'de.

## Don'ts
- `ddl-auto: update/create` ile şema değiştirmek.
- Literal secret default'u (`${JWT_SECRET:changeme}`).
- Service'te ham SQL.
- `@Data` entity.
- Onaysız veri silme.
