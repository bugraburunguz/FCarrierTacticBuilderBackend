# 05 · ÖZELLİK BACKLOG'U (kariyer + UT + platform)
Puanlama: **Değer** (oyuncu için, 1-5) · **Efor** (S/M/L/XL) · **Moat** (rakiplerde yok mu? ✓). Önerilen sıra = yüksek değer, düşük efor, moat ✓ önce.
✅ Tüm liste onaylı (08 D-13): ajan Faz A–D'den sonra maddeleri puan sırasıyla işler; PLT-08 erken (Faz B). Her maddeyi işe başlamadan ayrıntılı göreve çevirir (amaç → uç/şema → UI → kabul kriteri).

---

## A. KARİYER MODU (ana ürün — moat burada)
> **Güncel ve ayrıntılı hâli: `13_CAREER_MODE_EXPERIENCE.md`.** Aşağıdaki tablo özet olarak kalır; çelişkide 13 geçerlidir.
| ID | Özellik | Neden | Değer | Efor | Moat |
|---|---|---|---|---|---|
| CAR-01 | **Sezon simülasyonu "ya şunu alsaydım"**: mevcut kadro vs transfer sonrası kadro → SquadFit farkı + (06'daki motor hazır olunca) 100 maçlık Monte Carlo puan/gol tahmini | Kararı sayıyla gösterir | 5 | M→L | ✓ |
| CAR-02 | **Kariyer zaman çizelgesi**: her save import'u bir snapshot (spec §6 `user_save_snapshot` — şu an yok); oyuncu gelişim eğrileri, potential tahmini vs gerçek (MODELED vs IMPORT şeffaflığı, spec §7) | Kariyer oyuncusunun en çok merak ettiği şey "oyuncum gelişiyor mu" | 5 | M | ✓ |
| CAR-03 | **Transfer pazarı asistanı**: bütçe + maaş tavanı + yaş profili + taktik → "bu pencerede alınacak 3 oyuncu, satılacak 2 oyuncu" planı; satış önerisi = taktikte kullanılmayan + değeri yüksek | Tek ekranda plan | 5 | M | ✓ |
| CAR-04 | **Akademi / youth scout planlayıcı**: hangi ülke/bölge scout'u mevcut taktik ihtiyacına uygun (rol eksikliği → arketip) | FC kariyer oyuncusunun sık kullandığı alan | 4 | M | ✓ |
| CAR-05 | **Antrenman/pozisyon dönüşümü önerisi**: mevcut oyuncuyu yeni mevkiye çevirince RoleFit (ör. CM→CDM) — mevcut `advisor/positions` üzerine UI | Para harcamadan çözüm | 4 | S | ✓ |
| CAR-06 | **Rakip analizi**: lig rakibinin (career_team verisi) güçlü/zayıf yönü → maç öncesi taktik önerisi (kontra taktik) | 06'ya köprü | 4 | M | ✓ |
| CAR-07 | **Replika taktik galerisi** görselleştirme: preset_and_replica_library'deki her efsane için saha + oklar + "senin kadronla benzerlik %" | İçerik pazarlaması da olur | 4 | S | ✓ |
| CAR-08 | **Kadro derinlik tablosu (depth chart)** sezon boyu yorgunluk/rotasyon planı (maç takvimi girilirse) | FM hissi | 3 | M | ✓ |
| CAR-09 | **Kontrat/maaş takibi** (import'tan) + "sözleşmesi biten oyuncular" uyarısı | Kariyer QoL | 3 | S | — |
| CAR-10 | **Paylaşılabilir kariyer kartı** (sezon özeti görseli: kupa, gol kralı, en çok gelişen) | Viral büyüme | 3 | S | ✓ |
| CAR-11 | Save import'u "Live Editor" zorunluluğundan kurtarma araştırması (doğrudan save okuma — spec §11 v2) | Erişilebilirlik; kırılgan | 4 | XL | — |

## B. ULTIMATE TEAM
| ID | Özellik | Değer | Efor | Moat |
|---|---|---|---|---|
| UT-21 | **"Kadromu yükselt" danışmanı**: bütçe X → kimya bozulmadan rating/RoleFit artışı en yüksek 1-3 değişiklik (CP-SAT) | 5 | M | ✓ |
| UT-22 | **Taktik + rol uyumu UT'de** (kariyer motoru) — "bu kartı hangi rolde oynat" | 5 | S | ✓ |
| UT-23 | **Evo zinciri planlayıcı** (04 UT-17) | 5 | M | kısmen |
| UT-24 | **Paket/ödül değer hesaplayıcı** (EA'nın yayımladığı olasılıklarla beklenen değer) | 3 | M | — |
| UT-25 | **Rivals/Champions hedef takipçisi** (maç sayısı, ödül eşiği hatırlatma) | 3 | S | — |
| UT-26 | **Kulüp envanteri analizi**: SBC yemi değeri, "satılabilir ama kullanılmayan", duplicate yönetimi | 4 | S | kısmen |
| UT-27 | **Promo takvimi + tahmin sayfası** (geçmiş promo örüntüsü — kaynaklı, spekülasyon etiketli) | 3 | S | — |

## C. PLATFORM / BÜYÜME
| ID | Özellik | Değer | Efor | Not |
|---|---|---|---|---|
| PLT-01 | **PWA** (ana ekrana ekle, offline okuma, web push) | 4 | S | Mobil kullanım yüksek |
| PLT-02 | **i18n EN** (kod hazır diyor; metinler gömülü) — pazar büyür | 5 | M | `react-i18next`, metinleri çıkar |
| PLT-03 | Hesap: Google/Apple/Discord ile giriş (EA değil!) | 3 | S | — |
| PLT-04 | Ödeme sağlayıcı: iyzico (08 D-02) — SEC-01 sonrası | 5 | M | Gelir |
| PLT-05 | **Topluluk**: taktik/kadro paylaşım galerisi, beğeni, kopyala, "trend taktikler" | 4 | M | Ağ etkisi |
| PLT-06 | Discord botu: `/sbc`, `/kart Messi`, yeni promo bildirimi | 3 | S | Dağıtım kanalı |
| PLT-07 | İçerik: her preset/replika ve rol için SEO sayfası (statik prerender) | 4 | M | Organik trafik |
| PLT-08 | Analitik (PostHog self-host) + hata izleme (Sentry) | 4 | S | Ürün kararları için şart |
| PLT-09 | Admin paneli (03 DATA-07) + feature flag'ler | 4 | M | — |
| PLT-10 | Reklam yerleşimi (`showAds` flag var, gerçek sağlayıcı yok) — tasarımı bozmayan tek slot | 2 | S | — |

## D. YANLIŞ / FARKLI OLMASI GEREKENLER (mevcut özelliklerden çıkarım)
1. **İki modlu tek uygulama** (Kariyer + UT) bilgi mimarisini bulandırıyor: logo/başlık hep "FC Kariyer", ana sayfa hep kariyer, mod `/ut/*`'ye girince UT'ye geçiyor ama geri dönmüyor. → Mod, URL'nin ilk segmenti olsun (`/career/*`, `/ut/*`), header mod-bilinçli; ortak sayfalar (oyuncu DB) mod parametresiyle.
2. **"Kart veritabanı" UT'de base-kart DB'si** — kullanıcıyı yanıltıyor. 03 gelene kadar "Base kartlar" diye adlandır.
3. **Kredi modeli** sert sınırlar koyuyor ama iki uç bedava kopya (SEC-02). Kredi neye harcanıyor kullanıcıya şeffaf değil → her ücretli butonda "1 kredi" etiketi + kalan kredi.
4. **Potential/değer "tahmin"** etiketi sadece `*` — daha görünür güven aralığı (ör. "78–83") göster; ML MAE'yi kullanıcıya açıkla.
5. **Davranış tag'leri** güçlü ama yalnız Türkçe ve FM jargonuyla sınırlı değil — iyi; fakat pozisyon başına tag sayısı üst sınırı (spec notu) UI'da yok → 3 ile sınırla, fazlası IntentFit'i sulandırır.
6. **Wizard 5 soru** → sonucu sahada canlı göster; soru-cevap formu yerine görsel seçimler (oyun tarzı videoları/animasyonları).
7. **Taktik kurucu her slot için 11 ayrı `/fit/role` çağrısı** → tek `POST /fit/lineup` batch (kredi meselesi SEC-02 ile birlikte çöz).
8. **Replika kütüphanesi** gerçek kişi isimleri içeriyor (profil ipucu olarak) — sorun değil, ama UI'da oyuncu adları "profil" etiketiyle kalsın; efsane hoca/kulüp adlarını ticari markaya dönüştürme (logo vb.) yok.
9. **Kariyer import** Live Editor'a bağımlı — çoğu konsol oyuncusu kullanamaz. → Konsol oyuncusu için "manuel kadro girişi" hızlandırıcısı: takım seç → base kadroyu otomatik getir → sadece değişiklikleri işaretle (in-app kariyer akışı zaten var; UX'i öne çıkar).
10. **UT ve Kariyer aynı `PlayerCard`'ı farklı anlamlarla kullanıyor** (UT: kimya elması, kariyer: RoleFit) — iyi fikir, tek bileşen + `overlay` prop ile koru (02 §3.1).
