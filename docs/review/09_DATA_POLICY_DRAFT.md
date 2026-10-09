# 09 · VERİ POLİTİKASI TASLAĞI → repoya `docs/DATA_POLICY.md` olarak kopyala
Durum: Taslak v1 (2026-10-09). D-03, D-04 ve D-05 ile onaylı. `{{...}}` yer tutucularını Buğra doldurur.
Not: Bu hukuki danışmanlık değildir. Ticari lansmandan önce KVKK/GDPR ve fikri mülkiyet konusunda bir avukata baktır.

---

## 1. Kapsam
FCareer web uygulamasının (Kariyer ve Ultimate Team companion, ileride PITCHMIND sim oyunu) topladığı, işlediği, sakladığı ve yayımladığı tüm veriler.
Ürün EA SPORTS'un ya da herhangi bir kulüp, lig veya oyuncunun resmi ürünü değildir ve onlarla bağlantılı değildir.

## 2. Veri kaynakları (izinli liste — yeni kaynak eklemek Buğra onayı ister)
| Kaynak | Ne alınır | Sıklık | Yöntem |
|---|---|---|---|
| EA FC herkese açık ratings sayfası | Base kart statları, mevki, PlayStyle, portre URL'si | Günlük; promo günlerinde en fazla saatlik | Sayfa başına ≥ 1 sn bekleme, kendi tanımlayıcı User-Agent'ımız, 429/5xx'te geri çekilme |
| EA web-app auth gerektirmeyen statik içerik | İsim–ID eşlemesi, görsel yolları, lokalizasyon | Günlük | Aynı kurallar; **kimlik doğrulama gerektiren hiçbir uç çağrılmaz** |
| Gönüllü katkıcılar (extension "Katkı" modu; katkıcılar karşılığında PREMIUM kullanır) | Yalnız global tanımlar (SBC set/challenge, objective, evolution, kart tanımı, chem-style bonusları) ve market fiyat gözlemleri | Katkıcının kendi kullanımı sırasında | Opt-in, kullanıcıya özel token, whitelist normalizer, konsensüs |
| Admin girişi | Aynı global tanımlar | Gerektikçe | CMS, kim girdiği kayıtlı |
| Açık veri setleri | Potential/değer modeli eğitimi | Model sürümünde | Lisansı `docs/licenses.md`'de kayıtlı (`open_dataset_license` teyidi) |
| Kullanıcının kendi dosyaları | Live Editor kariyer export'u, UT kulüp capture'ı | Kullanıcı yüklediğinde | §4 |

**Yasak:** EA hesap kimlik bilgisi istemek, saklamak ya da loglamak; EA hesabıyla otomatik/bot erişim; üçüncü taraf fan sitelerini (SoFIFA, FUTBIN, fut.gg, cmtracker vb.) scrape etmek; captcha veya rate-limit atlatmak.

## 3. Global veritabanında saklananlar
- Kart, SBC, objective ve evolution tanımları; promo bilgisi; agregre fiyatlar (p10, p50, min, örnek sayısı).
- Her kaydın `source` alanı vardır (EA_SITE, EA_STATIC, CONTRIB, ADMIN, PARTNER) ve konsensüs durumu tutulur (PENDING/CONFIRMED).
- Ham fiyat gözlemleri 90 gün saklanır, sonra yalnız agregre kalır.
- Katkı ham gövdeleri (`contrib_raw`) şifreli saklanır, 30 gün sonra silinir.

## 4. Kullanıcı verisi (kişisel)
| Veri | Nerede | Saklama | Paylaşım |
|---|---|---|---|
| Hesap (e-posta, bcrypt şifre özeti) | Postgres | Hesap silinene kadar | Yok |
| Kariyer import'u (Live Editor) | Postgres, kullanıcıya özel (`IMPORT` kaynağı) | Kariyer silinene kadar; snapshot geçmişi kullanıcı silebilir | Hiçbir kullanıcıya gösterilmez |
| UT kulüp capture'ı | **Tarayıcıda** (IndexedDB). Sunucu yalnız işleyip geri döner; gövdeyi saklamaz ve loglamaz | Kullanıcı silene kadar (tarayıcıda) | Yok |
| SBC çözümü için kulüp özeti | İstek anında sunucuda bellekte | Saklanmaz | Yok |
| Katkılar | Ham gövde §3'teki gibi; katkıcı ID ve trust skoru | Hesap silinince anonimleştirilir | Global tanımlar katkıcı adı olmadan yayımlanır |
| Analitik (PostHog self-host), hata izleme (Sentry) | Kendi altyapımız | 12 ay | Yok; IP maskelenir |

Kullanıcı hakları (KVKK md. 11 / GDPR md. 15–21): verisine erişim, düzeltme, silme ve dışa aktarma — `/profile` üzerinden veya {{CONTACT_EMAIL}} adresine yazarak. Cevap süresi en fazla 30 gün.

## 5. Kişisel verinin global veriden ayrılması (teknik kural)
- Normalizer'da whitelist: yalnız tanım tipleri ve fiyat alanları geçer. Persona, kulüp envanteri, coin bakiyesi, transfer listesi sahibi, oturum ve hesap uçları atılır.
- Bu kural birim testiyle korunur: kişisel alan içeren fixture hiçbir global tabloya yazılmaz.

## 6. Görseller ve markalar
- Oyuncu portreleri, kulüp ve lig rozetleri ilgili hak sahiplerine aittir. Kendi proxy'mizden önbellekli servis edilir (en fazla 30 gün), toplu arşivlenmez.
- Kart görünümü (08 D-23): varsayılan olarak EA kart arka planları ve portreleri proxy-cache üzerinden gösterilir; kaldırma talebinde kendi çerçeve tasarımımıza (`OWN`) geçilir. İkonlar ve PlayStyle ikonları FCareer'ın kendi tasarımıdır. EA kart fontu kullanılmaz.
- Ülke bayrakları açık lisanslı bir setten alınır (lisans `docs/licenses.md`'de).
- "EA SPORTS FC", "Ultimate Team" ve kulüp/lig adları yalnız tanımlayıcı olarak kullanılır; logolarla ya da onay izlenimi verecek şekilde kullanılmaz.

## 7. Kaldırma talebi süreci
Hak sahibinden gelen talep {{CONTACT_EMAIL}} adresine yapılır. 48 saat içinde ilgili içerik yayından kaldırılır, sonra değerlendirilir. Talep ve yapılan işlem `takedown_log`'a yazılır.

## 8. Sim oyunu (PITCHMIND)
Online oyun kendi üretilmiş oyuncu evrenini kullanır (D-14 / L1). Gerçek oyuncu adı, EA reytingi ve görseli online oyunda kullanılmaz. Kullanıcının kendi import'u yalnız kendi özel maçlarında kullanılır (L2) ve başka kullanıcıya gösterilmez.

## 9. Sorumlu ve iletişim
Veri sorumlusu: {{LEGAL_ENTITY}} · İletişim: {{CONTACT_EMAIL}} · Politika sürümü: v1 · Değişiklikler bu dosyanın git geçmişinde izlenir.
