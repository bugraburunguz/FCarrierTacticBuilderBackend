# 08 · KARAR KAYDI (Buğra onayı: 2026-10-09 — "önerilen her şeye onay")
Bu dosya diğer md'lerdeki `DECISION` maddelerinin **onaylı** hâlidir. Çelişki olursa **bu dosya geçerlidir**.
`VERIFY` etiketli oyun kuralları bu onayla doğrulanmış sayılmaz; oyun içi doğrulama hâlâ gerekir.

| ID | Konu | Karar | Etkilediği görevler |
|---|---|---|---|
| D-01 | Bedava önizleme uçları (SEC-02) | **Kırp, ücretlendirme.** `/fit/lineup` ücretsiz kalır ama yalnız şunları döner: atama + slot başına RoleFit% + rozet. Kurallar, NLG, zayıf halkalar ve atak paterni yalnız ücretli `/fit/squad`'da. `/recommend/slot-view` ücretsiz ama **ilk 3 aday**, gerekçe metni ve scout filtresi yok; tam liste ücretli `/recommend/slot`'ta. | SEC-02, 05-D.3 |
| D-02 | Ödeme | **(güncellendi 2026-10-09)** Prod sağlayıcı **iyzico**. Paddle yok. Stub ödeme yalnız `dev` profilinde. iyzico anahtarları gelene kadar `POST /billing/subscribe` prod'da 503 döner. Entegrasyon `PaymentProvider` arayüzüyle yapılır; anahtarlar env'den okunur (`IYZICO_API_KEY`, `IYZICO_SECRET_KEY`, `IYZICO_BASE_URL`). | SEC-01, PLT-04 |
| D-03 | UT veri kaynakları | **Onaylı:** S1 (EA ratings sitesi), S2 (EA web-app auth'suz statik içerik), S3 (gönüllü katkıcı programı + konsensüs), S4 (admin CMS). S5 (üçüncü taraf API/ortaklık) araştırılır. **Reddedildi:** S6 (EA hesabıyla bot), S7 (fan sitelerini scrape etmek). | DATA-01..13 |
| D-04 | Görseller | Oyuncu yüzü: kendi image proxy'mizden servis (webp, 30 gün cache), toplu rehost yok. Kart çerçevesi: kendi `card_design` SVG'lerimiz. Bayraklar: MIT lisanslı set. Kulüp/lig rozetleri: proxy üzerinden hotlink, monogram yedek. Kaldırma talebine 48 saat içinde uyulur. | DATA-09, DS-05 |
| D-05 | Veri politikası | `09_DATA_POLICY_DRAFT.md` → repoya `docs/DATA_POLICY.md` olarak kopyalanır (DATA-01 tamam sayılır; `{{...}}` yer tutucuları Buğra doldurur). | DATA-01 |
| D-06 | Görsel dil | "Matchday" yönü onaylı (02 §2). Fontlar: Barlow Condensed (display), IBM Plex Sans (metin), IBM Plex Mono (sayı). Vurgu rengi: flood sarısı. Bileşen altyapısı: Radix primitives + kendi stilimiz. Tasarım sandbox'ı: Ladle. | DS-01..10, UX-01..06 |
| D-07 | Tasarım teslim sırası | Önce tek ekran: `PlayerCard` (DS-05) + `Pitch` v2 (DS-06) + UT Squad Builder (UX-02). Buğra ekran görüntüsünü onaylayınca geri kalan ekranlara geçilir. | 02 §8 |
| D-08 | Bilgi mimarisi | Mod URL'nin ilk parçası olur: `/career/*` ve `/ut/*`. Ortak sayfalar mod parametresiyle açılır. Eski yollar için yönlendirme konur. UT'deki "Kart veritabanı" etiketi, 03 bitene kadar "Base kartlar" olarak değişir. | 05-D.1, 05-D.2, DS-10 |
| D-09 | Hata kodları (BUG-10) | Uygun HTTP status kodlarına geçilir (404/401/402/409). Bu bir **API CONTRACT CHANGE**: web `api/client.ts` aynı PR serisinde güncellenir. | BUG-10 |
| D-10 | Tarayıcı depolama | Büyük veri için IndexedDB, **Dexie** ile; `schemaVersion` + migration. localStorage yalnız tercih (tema, sekme). | BUG-05 |
| D-11 | SBC çözücü | Kısa vadede frontend beam çözücü düzeltilip Web Worker'a alınır (UT-10). Uzun vadede backend'de OR-Tools CP-SAT (UT-11). Streamlined/item-score çözücüsü kalır, auth + rate limit eklenir. | UT-10..15 |
| D-12 | Kredi şeffaflığı | Her ücretli butonda kredi maliyeti etiketi ve kalan kredi gösterilir. | 05-D.3 |
| D-13 | Backlog sırası | 05'teki puanlama aynen geçerli. Ajan Faz A–D bittikten sonra 05'i değer↓, efor↑ sırasıyla işler. **PLT-08 (analitik + Sentry) Faz B ile birlikte, erken** yapılır. | 05 |
| D-14 | Sim oyunu: lisans | **L1** (kendi üretilmiş oyuncu evreni) online oyunun varsayılanı. **L2** (kullanıcının kendi Live Editor import'uyla özel maçlar) kariyer köprüsü için. L3/L4 yok. | SIM-*, 06 §1 |
| D-15 | Sim oyunu: teknoloji | Motor saf Java kütüphanesi `fcareer-match-engine` olur; backend ile aynı toolchain için **Java 17** uyumlu (records var). Java 21'e ileride iki modül birlikte geçer. Servis `fcareer-match-service` (Spring Boot + WebSocket). İstemci renderer: MVP'de Canvas2D, performans gerekirse PixiJS. | 06 §3.1, §6, §7 |
| D-16 | Sim oyunu: tempo | Canlı lig maçında 1 maç dakikası ≈ 8 sn (maç ~13 dk). Tek oyunculu maç 1×/2×/4× ve "önemli anlar" modunda izlenir. Lig: 8 takımlı async MVP, sonra 16 takım ve günde 2 maç günü. | 06 §2, §6 |
| D-17 | Sim oyunu: para | Pay-to-win yok. Premium = gelişmiş analiz, ekstra lig, kozmetik, replay arşivi. | 06 §10 |
| D-18 | Extension | Extension iki moda ayrılır: **Kişisel** (kulüp import) ve **Katkı** (opt-in; yalnız global tanımlar ve fiyat). Katkı modu açılmadan önce extension repo'suna `docs/ut_capture_mapping.md` yazılır. | DATA-11 |
| D-19 | Faz sırası | 00 §3 aynen: A → B → C → D → E, F (sim) paralel hat. F, ancak Faz A'nın P0'ları bittiğinde başlar. | 00 §3 |

| D-20 | Katkıcı = PREMIUM | Katkıcılar Buğra ve arkadaşlarıdır. Admin bir kullanıcıya `CONTRIBUTOR` bayrağı verir; bayrak açıkken kullanıcı **PREMIUM** haklarını alır (sınırsız kredi, reklamsız). Eşik ya da puan sistemi yok, elle verilir. Bayrağın verilmesi/kaldırılması `credit_transaction` benzeri bir audit tablosuna yazılır. Katkıcılar trusted başlar (trust ≥ eşik): tek katkıları CONFIRMED sayılır. Admin kuyruğu yalnız çelişkide çalışır. | DATA-05, DATA-06, SEC-01 |
| D-21 | Kimya | **(düzeltildi)** FC27 kuralları kaynakla doğrulandı (fifauteam.com/fc-27-chemistry): kulüp 2/4/7, lig 3/5/8, ülke 2/5/8; Icon: ülke +1 ve XI'deki her lig +1; Hero ve Hall of FUT: ülke +1, lig +1; menajer: ülke veya lig eşleşirse +1, oyuncu başına en fazla 1; mevki dışı = 0 ve katkı yok; yedekler sayılmaz. Mevcut `chemistry.ts` değerleri **doğru**; ilk denetimdeki BUG-04 yanlış alarmdı. Kalan: menajer slotunu UI'a bağla, testleri +1/+2 ayırt edecek şekilde güçlendir, değerleri `ut-rules`'a taşı. | BUG-04, DATA-02, UT-01 |
| D-22 | Taktik kodu | `10_TACTIC_CODE_DIAGNOSIS.md` geçerli. Codec, EasySBC'nin yayımladığı 64 gerçek FC27 koduyla birebir doğrulandı. Sorun eşleme katmanında ya da zip sonrası değişiklikte aranır; regresyon testleri `patches/`'te. | TAC-01..06 |
| D-23 | UT kart görünümü | Buğra'nın isteği: kartlar **oyundaki gibi** görünsün (EasySBC gibi). `card_design.mode = EA_ASSET` varsayılan (EA kart arka planı + portre, kendi proxy'mizden, 30 gün cache); `OWN` (bizim SVG çerçeve) tek bayrakla geri dönüş. EA/Cruyff kart fontu kopyalanmaz. Risk notu: EA görsellerinin kullanımı EA'nın iznine tabi; EasySBC/futbin de aynısını yapıyor ama bu bir izin değil. Kaldırma talebinde 48 saat içinde `OWN`'a geçilir. | DS-05, DS-11, 09 §6 |
| D-24 | Fiyat ve UT verisi | **(güncellendi)** Ortaklık yok; veriyi kendimiz toplarız. EasySBC'den otomatik çekim yok. Fiyatlar yalnız **pasif** toplanır: katkıcılar (Buğra + arkadaşlar) EA Web App'te normal gezinirken extension market sonuçlarını yakalar. 22 bin kartı tek tek arayan otomatik script/bot **yok** (EA kullanım şartları + market arama limiti → hesap kısıtlaması/ban riski). Kapsam boşluğu 03 §4.1'deki önceliklendirme ile kapatılır. | DATA-10, DATA-15 |
| D-25 | Kulüp içe aktarma UX | Dosya indir/yükle yerine tek tık senkron (12 §2). Pasif yakalama korunur. Tüm kulübü tek seferde çekmek için web-app'i extension'ın kendisinin gezdirmesi/istek atması (EasySBC tarzı) **ayrı bir karar**: EA kullanım şartları açısından daha gri, kullanıcı hesabına risk. Önce pasif + "şu sayfaları aç" rehberi; aktif mod Buğra'nın ayrı onayıyla. | EXT-01..12 |

## Hâlâ Buğra'dan gelmesi gerekenler (girdi, karar değil)
- `{{CONTACT_EMAIL}}` ve `{{LEGAL_ENTITY}}` (veri politikası).
- iyzico API anahtarları (D-02). Gelince eklenecek; o zamana kadar yer tutucu.
- Taktik kodu test verisi (10 §3).
- Katkıcı hesapları: Buğra + arkadaşları (D-20).
