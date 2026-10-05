# CLAUDE.md — Azerlottery Backend

`com.misli.*` Spring Boot microservice'lerinde (Şanstech / Azerlottery) çalışırken
Claude'un giriş dokümanı. Ayrıntılı kurallar aşağıda `@import` ile yüklenir.

## Proje bağlamı

- **Stack:** Java 17, Spring Boot 3.x, Jakarta, PostgreSQL + Spring Data JPA, Redis,
  Kafka, OpenFeign, Lombok, Maven.
- **Servisler:**
  - `nsoft-virtual-game-service`, `groove-virtual-game-service` — legacy layer-first layout.
  - `campaign-aggregator` — tercih edilen modüler (feature/area + `common`) layout.
  - `social-aggregator`, ayrıca user / finance / sportsbook servisleri.
- **Aggregator'lar** downstream servisleri Feign ile toplar; **PHP → Java taşıma** sık bir iş tipidir.
- **Entegrasyonlar (dış/dahili):** iddaa-wagering-service, NSoft, Sportradar, iGNITE, Risk Department.
- **Kanallar:** Web `/api/web/v2` ve Mobile `/api/mobile/v2` **aynı service'i paylaşır**;
  mobil, web davranışını korur. v1 kaldırıldı. Yeni endpoint eklemek mevcut iş kuralını değiştirmez.

## Nasıl çalışıyoruz

- Deneyimli bir backend geliştiricisiyle konuşuyorsun. Temel syntax / başlangıç anlatımı yok;
  doğrudan production, mimari, maintainability ve business logic seviyesinde düşün.
- Doğrudan ve analitik ol; gereksiz diplomasi ve içi boş övgü yok. Yanlış bir yaklaşım
  görürsen açıkça karşı çık, gerekçelendir, uygulanabilir sonuca bağla.
- **Refactor/review'da davranış (business logic) DEĞİŞMEZ — pazarlıksız.** Yanlış bulduğun
  davranışı bu görevde düzeltmezsin, `POTENTIAL LOGIC ISSUE` olarak raporlarsın.
  Ayrıntı: review sözleşmesi (§1) ve edit-güvenliği A/B/C (§1.2).
- Davranışı değiştiren her öneriyi **açıkça etiketle**; sessizce uygulama. Sadece
  `SAFE REFACTOR` uygulanır.
- Gereksiz abstraction / pattern / factory / wrapper açma (§1.6). Refactor kod sadeleşince
  başarılıdır, sınıf sayısı artınca değil.

## Review'ı başlatma

Tek cümle yeter (bkz. `docs/backend-standard.md` §0):

```
{servis}/{controller|path} → review-mode, backend-standard'a göre. Logic değişmesin.
Örn: user-identity-controller → tüm endpoint flow'unu review et (§1.4). SAFE REFACTOR uygula, gerisini raporla.
```

## Kurallar (otomatik yüklenir)

Backend house style & review standardı — **her** kod/review görevinde geçerli:

@docs/backend-standard.md

Ticket verme, Jira yorumu ve soru disiplini — iş devri ve çıktı formatı:

@docs/collaboration.md

---

### Setup / bakım notu

- Bu dosyaları repo köküne koy: `CLAUDE.md` kökte, `backend-standard.md` ve
  `collaboration.md` ise `docs/` altında. `@import` yolları köke göre çözülür.
- Tek gerçek kaynak repo'dur; Claude'daki kopya bir aynadır — tek nüsha olarak güvenme.
- `docs/backend-standard.md` büyük (~650 satır) ve import edildiği için **her oturumda
  tümüyle context'e yüklenir** (`@import` bağlam tasarrufu yapmaz, içeriği inline genişletir).
  İleride adherence düşerse: türetilebilir kısımları (paket ağaçları, tech baseline listesi)
  Claude Code `/doctor` ile kırp, pitfall / konvansiyon / gerekçe kısımlarını koru.
- `collaboration.md` küçük olduğu için import'ta bırakıldı; her turda gerekmiyorsa import
  satırını kaldırıp "Jira yorumu yazarken `docs/collaboration.md`'yi oku" talimatına
  çevirebilirsin — ama on-demand okuma import kadar güvenilir tetiklenmez.
- Markdown içindeki Java annotation'larını (`@Service`, `@Transactional` …) her zaman
  backtick veya kod bloğu içinde tut; düz metinde bırakılan `@kelime` yanlışlıkla import
  olarak yorumlanabilir.
