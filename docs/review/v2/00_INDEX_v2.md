# FCAREER — İNCELEME PAKETİ v2 · 00 INDEX (önce bunu oku)
Tarih: 2026-10-12 · Kapsam: `FCarrierTacticBuilderBackend@4d2f8ae` + `FCarrierTacticBuilderWeb@40ca49c` + Buğra'nın yüklediği 40+ rehber md, FC 27 Live Editor v27.1.0 zip'i ve LE'den alınmış 5 CSV.
Hedef okuyucu: kod ajanı (Claude Code vb.). Her dosya tek başına okunabilir, görevler ID'li ve kabul kriterli.

> Bu paket, repoda zaten bulunan **v1 paketinin** (`docs/review/00..13`, 2026-10-09) üstüne yazıldı. v1'i silme. v1 kararları (`08_DECISIONS_LOG.md`, D-01..D-25) geçerliliğini korur. v2, bazı v1 iddialarının artık doğru olmadığını gösteriyor (bkz. `01_STATUS_DUZELTME.md`).

## 0. ETİKETLER (v1 ile aynı + 2 yeni)
| Etiket | Anlam |
|---|---|
| `VERIFIED` | Kodda dosya:satır ile ya da yüklenen kaynakla doğrulandı |
| `VERIFY` | Doğrulanamadı (bu oturumda EA alan adlarına ve web aramasına ağ erişimi yoktu). Uygulamadan önce test et, değeri config'e koy |
| `LEGAL-RISK` | EA ToS / telif riski. Buğra onayı olmadan uygulama |
| `DECISION-NEEDED` | **Yeni.** Buğra'nın karar vermesi gereken madde (bkz. §4) |
| `STALE` | **Yeni.** v1 STATUS'ta yazılı durum koddaki gerçekle uyuşmuyor |
| `P0/P1/P2` | P0 = güvenlik/para/veri bozulması, P1 = doğruluk/UX kırığı, P2 = iyileştirme |

ID önekleri (v2): `BE-SEC` `BE-BUG` `BE-ARCH` `BE-DATA` (backend) · `WEB-BUG` `WEB-UX` `WEB-DS` `WEB-ARCH` `WEB-PERF` (web) · `RULE` (oyun kuralı) · `SBC` (SBC veri hattı) · `CARD` (kart görseli + versiyonlar) · `LE` (Live Editor) · `FEAT` (yeni özellik). v1 ID'leri (SEC-xx, BUG-xx, DATA-xx, UT-xx, CAR-xx) olduğu gibi geçerli; v2 bunlara referans verir.

## 1. DOSYALAR
| # | Dosya | İçerik | Öncelik |
|---|---|---|---|
| 01 | `01_STATUS_DUZELTME.md` | v1 STATUS.md'deki iddialar ↔ koddaki gerçek (iki repo). STATUS'u nasıl güncelleyeceğin | Ajan, **ilk iş** |
| 02 | `02_BACKEND_BULGULAR.md` | 52 yeni backend bulgusu (4 P0), mimari, endpoint envanteri | Ajan |
| 03 | `03_WEB_BULGULAR.md` | 50+ web bulgusu (1 P0), build/test/lint sonucu, sayfa envanteri | Ajan |
| 04 | `04_OYUN_KURALLARI.md` | Yüklenen FC27 rehberlerine göre kural doğrulaması + tam `ut-rules.fc27.json` v2 önerisi + kaynaklar arası çelişkiler | Ajan |
| 05 | `05_SBC_VERI_HATTI.md` | SBC'leri detaylarıyla çekme: yenileme saati (TR 20:00/21:00), EA uçları, gereksinim enum'u, şema, zamanlayıcı, bildirim, seçenekler + risk | Ajan + Buğra |
| 06 | `06_UT_KART_GORSEL_VERSIYON.md` | EA kart görseli URL'i, ID eşlemesi (CSV ile doğrulandı), görsel proxy, "bir oyuncu → çok kart" modeli, versiyon geçişli oyuncu sayfası, extension'sız veri seçenekleri | Ajan + Buğra |
| 07 | `07_LIVE_EDITOR.md` | LE v27.1.0 Lua API kataloğu, export v2 şeması + Lua script, backend parser, CSV hataları, tek tık senkron, görseller | Ajan |
| 08 | `08_TASARIM_GORUNUM.md` | Ekran ekran görsel/UX yeniden tasarım (futbin/fut.gg/EasySBC seviyesi) | Ajan + Buğra |
| 09 | `09_YENI_OZELLIKLER.md` | Rehberlerden çıkan yeni özellikler (UT + Kariyer), puanlı backlog | Buğra seçer → Ajan |
| 10 | `10_YUKLENEN_DOSYA_NOTLARI.md` | Yüklenen rehber md'leri ve CSV'ler hakkında: hangisi seed verisi olur, hangisi eski/çelişkili | Ajan |
| 11 | `11_GLASSMORPHISM_TASARIM_V3.md` | **Yeni görsel dil (tüm site):** glassmorphism token'ları, yüzeyler, bileşenler, erişilebilirlik/performans kuralları, sayfa şablonları, kalite kapıları (bilinen hatalar yeni tasarımda kapalı) | Ajan + Buğra |
| 12 | `12_REPLIKA_TAKTIKLER.md` + `data/replicas_v2.fc27.json` | 107 replika (2000 sonrası tüm ŞL şampiyonları, efsane kadrolar, efsane hocalar, underrated hocalar, BJK/FB/GS/TS en iyi 3 sezon, Başakşehir 2019-20, Bursaspor 2009-10): FC27 formasyon + build-up + savunma + hat + 11 rol/focus + Plan B | Ajan + Buğra |
| 13 | `13_KARIYER_MOTORU_YENIDEN.md` | Öneri ve taktik analiz sisteminin neden yanlış çalıştığı (ENG-01..23) + sıfırdan kariyer motoru tasarımı + geçiş planı | Ajan |
| 14 | `14_TAKTIK_KODU.md` + `data/ea_tactic_codec_reference.py` | EA taktik kodu formatı (bit düzeni, alfabe, karıştırma), neden yanlış çalışıyor (54 doğrulanmamış rol değeri + 7 formasyon + sessiz düşüşler), 10 kodluk oyun içi doğrulama planı (beklenen kodlarla), TAC-07..16 | Ajan + Buğra |
| — | `assets/` | Buğra'nın referans ekran görüntüleri (08 §14-16) | — |

## 2. TEK PARAGRAF ÖZET
Motor ve veri altyapısı v1'den bu yana ciddi ilerlemiş (KB tabloları, katkı hattı, konsensüs, refresh-token aile iptali, Menajer Masası ekranları). Ama: (1) **UT fiyat verisi bugün bozuk yazılıyor**: promo kartın fiyatı base karta kaydediliyor, tek katkıcıda medyan en ucuz değere çöküyor, kullanıcının kendi transfer listesi "market fiyatı" sayılıyor (BE-BUG-01..03, P0). (2) Anonim `/recommend/preview` ücretli analizi bedava veriyor (BE-SEC-01, P0). (3) Kariyer masasında ayarlar başka kariyere yazılabiliyor (WEB-BUG-01, P0). (4) **UT kart veritabanı yok**: `ut_item`'a hiç promo kart girmiyor, web'de UT oyuncu sayfası ve versiyon geçişi yok, "Kart veritabanı" menüsü kariyer kataloğunu açıyor. (5) Squad rating backend'de hâlâ düz ortalama, kural dosyası 11 diyor, web 18 diyor. (6) Kart görseli hâlâ siluet: FC27 portre adresi 403 veriyor; Buğra'nın bulduğu `ratings-images-prod.pulse.ea.com/FC27/components/items/{id}_tr-TR.webp` adresi doğrudan kullanılabilir ve `{id}` = oyun içi oyuncu id'si (LE CSV'leriyle doğrulandı). (7) SBC'ler için extension'sız güvenilir bir EA kaynağı yok. Önerilen yol: katkıcı extension'ın pasif yakalaması + günlük 18:00 UK takvimi + admin CMS. (8) Live Editor export'u çalışıyor ama kariyer bağlamı (tarih, kullanıcı takımı, save), kontratlar ve gençler eksik. Backend Türkçe lig/kulüp adlarıyla eşleme yaptığı için yanlış içe aktarıyor.

## 3. YOL HARİTASI (sıralı, v1'in fazlarına eklenir)
```
FAZ A2 (3-4 gün)  P0'lar                  → BE-SEC-01, BE-BUG-01..03, WEB-BUG-01, TAC-08..10 (14) + 01_STATUS_DUZELTME; Buğra 14 §5 kodlarını alır → TAC-07
FAZ A3 (1 hafta)  Doğruluk P1             → BE-BUG-04..11, WEB-BUG-02..14, RULE-01..10 (04), LE-P0 (07 §6)
FAZ C2 (2 hafta)  UT veri omurgası        → CARD-01..08 (06), SBC-01..12 (05), BE-DATA-01, BE-ARCH-02/03
FAZ D2 (2-3 hafta) UT ekranları            → WEB-UX-01..03, 06; 08_TASARIM §2-§5 (oyuncu sayfası + versiyon şeridi, SBC kartları, kadro kurucu sağ panel)
FAZ E2 (1 hafta)  Kariyer veri hataları    → ENG-01..04 mevcut motorda (13 §4 kuralı), LE export v2 (07)
FAZ V3 (4-6 hafta) Tasarım v3 + yeni motor → 11 V3-01..08 ile 13 ENG-M1..M8 paralel; önce çalışma alanı ekranı (V3-04) onayı; replikalar 12 REP-01..07
FAZ D′2 (sürekli) Kariyer                  → CAR-02 snapshot zenginleştirme, 09'dan seçilenler
```
Gate: Bir faza geçmeden önceki fazın P0'ları yeşil + testli. Her görev bitince ilgili md'deki satıra `DONE (sha)` yaz, `docs/review/STATUS.md`'yi güncelle.

## 3.1 KURULUM (ajan ilk iş)
1. Bu klasörü iki repoya da `docs/review/v2/` olarak kopyala.
2. `01_STATUS_DUZELTME.md`'ye göre iki repodaki `docs/review/STATUS.md`'yi düzelt (STALE satırlar).
3. `CLAUDE.md` (backend) içindeki `@docs/backend-standard.md` importunu kaldır (BE-ARCH-01). Yerine `docs/fcareer-standard.md` yaz (yalnız uygulanan kurallar).
4. Faz A2'ye başla.

## 4. BUĞRA'NIN KARAR VERMESİ GEREKENLER (`DECISION-NEEDED`)
| ID | Soru | Önerim |
|---|---|---|
| DN-01 | Kart görseli: EA'nın hazır render'ı (`components/items/{id}_tr-TR.webp`) base kartlarda varsayılan olsun mu? (D-23 zaten EA_ASSET diyor, bu onu somutlaştırır) | Evet. Proxy'den servis et, 24 saat cache, kaldırma talebinde tek bayrakla OWN'a dön (06 §3) |
| DN-02 | SBC için EasySBC/futbin/fut.gg'den çekmek (D-03 S7 reddedildi) yeniden açılsın mı? | Hayır. Katkıcı extension + admin CMS yeterli kapsama verir. Ticari ölçekte lisans/ortaklık (S5) konuşulur (05 §6) |
| DN-03 | Extension'ın SBC sekmesini **kullanıcının tıklamasıyla** yenilemesi (EA'ya kendi isteğini atması) | Hayır, şimdilik pasif kalsın. Yalnız "yeni SBC düştü, SBC sekmesini aç" rozeti (05 §4.3) |
| DN-04 | LE'den oyuncu yüzü (DDS) çıkarıp kullanıcıya özel saklamak | Yalnız kullanıcının kendi kadrosu/kısa listesi, yalnız kendi tarayıcısında (IndexedDB). Sunucuya global koyma (07 §5) |
| DN-05 | Squad rating gösterimi: kadro ekranında 18 oyuncu (XI+7 yedek), SBC'de 11 | Evet, ikisi ayrı config değeri (04 RULE-02) |
| DN-07 | Görsel dil: glassmorphism v3, D-06 Matchday'in yerine (D-26 olarak kaydet) | Evet (Buğra istedi). Radix + Ladle kararı aynen |
| DN-08 | Replikaların `verify` maddeleri kim/nasıl doğrulayacak | Buğra + katkıcılar, benzerliği ≤3 olanlar önce (REP-06) |
| DN-06 | Admin girişi: paylaşılan `X-Admin-Key` yerine JWT'de `ADMIN` rolü | Evet (BE-SEC-07) |

## 5. BU İNCELEMENİN SINIRLARI
- Bu oturumda `ea.com`, `drop-api.ea.com`, `ratings-images-prod.pulse.ea.com`, fan siteleri ve web araması **ağ politikası nedeniyle erişilemedi**. EA uçlarına dair her şey açık kaynak projelerden (github: futapi/fut, icysymmetra/AutoPilot-SBC, ThomasSteere/Auto-SBC) ve yüklenen dosyalardan çıkarıldı → `VERIFY`. 06 §8'de Buğra'nın kendi makinesinde çalıştıracağı 10 satırlık doğrulama komutu var.
- Web repo kopyasında `npm ci`, `npm test` (130 test yeşil), `lint` (0 hata / 28 uyarı) ve `build` gerçekten çalıştırıldı. Backend derlenmedi, statik okuma yapıldı.
- LE zip'inden hiçbir şey çalıştırılmadı. Yalnız metin dosyaları (DOC.MD, Lua kütüphaneleri, localize.json, hash listesi) okundu.
- Taktik kodu: yüklenen rehber "11 karakter" diyor. Repodaki 64 gerçek FC27 kodunun hepsi **12 karakter** (`easysbc-vectors.json`). Kod doğru, rehber yanlış. 12'de kal.
