# 08 · TASARIM ve GÖRÜNÜM — ekran ekran yeniden tasarım
> **Not (v3):** Görsel dil (renk, yüzey, token) artık `11_GLASSMORPHISM_TASARIM_V3.md`. Bu dokümandaki **yerleşimler ve ekran davranışları** (§1-§16) geçerli; çizim 11'e göre yapılır. Aşağıdaki Matchday token/renk anılarını 11'deki karşılıklarıyla değiştir.
Temel: v1 `02_DESIGN_SYSTEM_REDESIGN.md` ve D-06 "Matchday" yönü geçerli (Barlow Condensed başlık, IBM Plex Sans metin, IBM Plex Mono sayı, flood sarısı vurgu, Radix primitives). Mevcut token'lar `src/index.css`'te: `--bg --surface --surface-2 --line --ink --muted --accent --accent-bg --on-accent --accent-soft --code --danger --good --info` (+ `-soft`). Eksik: `--warn`/`--warn-soft` (WEB-BUG-21), rarity renkleri.
Hedef: futbin / fut.gg / EasySBC seviyesinde **yoğun, taranabilir, kart merkezli** ekranlar. Bugünkü "aynı ağırlıkta paneller + açıklama paragrafı" görünümü yerine: **tek kahraman öğe + yan panel + yoğun tablo**.

---
## 1. GENEL KURALLAR (her ekran)
| # | Kural | Uygulama |
|---|---|---|
| G1 | Her ekranda **tek kahraman öğe** (saha, büyük kart, SBC sonucu). Diğer her şey ikincil | Kahraman ≥ %55 genişlik (desktop) |
| G2 | Açıklama paragrafı yok. Yardım `HelpPopover` (Radix Popover, Esc + dış tık kapatır) | 125 paragraf → popover (WEB-DS-08) |
| G3 | Sayılar mono, sağa hizalı, tabular-nums. Para `12,5 M €` / coin `12.5K` | `num` sınıfı tek yerde |
| G4 | 11px altı metin yok | Kartta boyut başına düzen (WEB-DS-01) |
| G5 | Yükleme = iskelet (kart/satır şekli), boş = eylem öneren tek satır, hata = sebep + yeniden dene | Her `useQuery` için üç durum bileşeni |
| G6 | Mod rengi: Kariyer = mürekkep/sarı, UT = mürekkep/sarı + rarity renkleri **yalnız kartlarda** | Rarity rengi UI kromuna sızmaz |
| G7 | Mobil öncelikli kırılımlar: 375 / 768 / 1280. Yatay taşma yok | Sekmeler kaydırılabilir (WEB-UX-04), saha mobilde tam genişlik + bottom sheet |
| G8 | Klavye: her liste ok tuşlarıyla, her dialog Esc, kart seçimi Enter | Radix Tabs/Dialog/Popover |
| G9 | Durum ikonları lucide + aria-label; glif/emoji yok | WEB-DS-07 |
| G10 | Rarity token'ları: `--r-bronze #8c5a3c`, `--r-silver #a7b0b8`, `--r-gold #d9b24c`, `--r-totw #0e0e10 / #e3c25b`, `--r-icon #f4efe2`, `--r-hero #6d2ccf`, `--r-evo #0fb5ae` (kart config'i; EA renklerine benzer, aynı değil) | `cardDesign.ts` |

---
## 2. UT OYUNCU SAYFASI (`/ut/players/:assetId`) — yeni, kahraman: büyük kart
```
Desktop 1280
┌─ Başlık: MBAPPÉ · ST · Real Madrid · Fransa · LALIGA ─────────────── [★ İzle] [Kadroya ekle] ┐
│ Versiyon şeridi:  [Base 91] [TOTW 93] [POTM 94] [Evo yolu ▸]          ←/→ ile gez          │
├──────────────────────────┬─────────────────────────────────────────────────────────────────┤
│                          │ Fiyat  PS 1.2M  PC 1.1M  · 2 sa önce · grafik (7g/30g)         │
│   [ EA render / kendi     │ Nasıl alınır: Paket · SBC "…" (link) · Objective              │
│     çerçeve, 360px ]      │ PlayStyles+: ◆ Rapid+  ◆ Finesse Shot+   PlayStyles: …         │
│   ◆◆◆  ST AF++  1.2M      │ Roller: ST Advanced Forward ++ · Poacher + · False 9           │
│                          │ ┌ Attribute tablosu (6 grup × satırlar, base'e göre +Δ yeşil) ┐│
│                          │ │ PAC 96  Acc 97  Sprint 95                                   ││
│                          │ │ SHO 91  Fin 93 (+2) …                                       ││
│                          │ └─────────────────────────────────────────────────────────────┘│
└──────────────────────────┴─────────────────────────────────────────────────────────────────┘
Mobil: kart üstte (tam genişlik %70), şerit yapışkan, bilgiler sekmeli (Statlar · Fiyat · Roller · Nasıl alınır).
```
- Attribute satırları `ratingTone` ile renklenir (≥90 koyu yeşil, 80-89 yeşil, 70-79 sarı, <70 gri) (WEB-DS-11).
- "Karşılaştır" iki versiyonu yan yana koyar, farklar vurgulu.
- Kariyer modunda aynı sayfa: kart = kendi çerçeve + yüz, potansiyel ve rol uyumu görünür; fiyat/versiyon yok.

## 3. UT KADRO KURUCU (`/ut/squad`) — kahraman: saha
```
┌ Üst bar: Formasyon ▾ · Taktik kodu [AJawHEA#K?tB] [Kopyala] · Kimya 31/33 · Rating 86 (XI) / 85 (18) · Değer 4.2M ┐
├──────────────────────────────────────── 62% ───────────┬──────────── 38% ───────────────┐
│  SAHA (Pitch v2)                                      │ Sekmeler: Ara | Kulübüm | Öneri │
│   kartlar: EA render (md), altında ◆◆◇ ve rol çipi    │ Filtre çipleri: Lig Ülke Kulüp   │
│   boş slotta da rol çipi (WEB-BUG-11)                │ Pozisyon Rarity Fiyat ≤          │
│                                                       │ md kart ızgarası, her kartta:    │
│                                                       │  Δkimya +3  ΔOVR +2  12.5K       │
├───────────────────────────────────────────────────────┤ (worker hesaplar, WEB-UX-06)     │
│ Yedekler (7) [ ][ ][ ][ ][ ][ ][ ]   Menajer [ Lig/Ülke ] │                                │
└───────────────────────────────────────────────────────┴────────────────────────────────┘
```
- Slot seçince sağ panel "Ara" sekmesine geçer ve o pozisyona filtrelenir. Karta tıklayınca önizleme: saha üstünde hayalet kart + Δ değerleri; "Yerleştir" ile onay.
- Kimya açıklaması: oyuncunun elmasına tıklayınca popover "Kulüp 2/2 ✓ (+1) · Lig 5/5 ✓ (+2) · Menajer ✓ (+1, tavan 3)".
- Mobil: saha tam genişlik, yedekler yatay kaydırma, arama **bottom sheet** (Radix Dialog, `side=bottom`).

## 4. SBC LİSTESİ (`/ut/sbc`) — kahraman: kart ızgarası
```
Sekmeler: Yeni (12) · Bitiyor (4) · Oyuncular · Yükseltmeler · Foundations · Icon · Favoriler    Sonraki yenileme 20:00 (3 sa 12 dk)
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ [ödül kartı] │ │ [paket görs.]│ │ [ödül kartı] │
│ 88 POTM X    │ │ 84+ x10      │ │ ...          │
│ 3 görev      │ │ tekrar ∞     │ │              │
│ ~45K PS      │ │ ~8K          │ │              │
│ Süre 2g 4s      │ │ Süre 18 sa      │ │              │
│ Oy 82%  [Çöz] │ │ ...          │ │              │
└──────────────┘ └──────────────┘ └──────────────┘
```
(Gerçek UI'da süre ve oy için lucide `Timer` ve `ThumbsUp` ikonları, G9.)
- Kartta: ödül görseli (item ise EA render ya da kendi kart, paket ise paket adı rozeti), görev sayısı, tahmini maliyet (kulüpten / marketten), geri sayım (`ends_at`), oy yüzdesi, "Yeni" ve "Değişti" rozetleri (SBC-08).
- Sıralama: yeni, bitiyor, ucuz, değerli. Arama.
- Boş durum: "Bu kategoride aktif SBC yok."

## 5. SBC ÇÖZÜM SAYFASI (`/ut/sbc/:setId/:challengeId`) — kahraman: çözüm sahası
```
┌ Gereksinim çipleri: [Rating ≥ 84 ✓] [Kimya ≥ 25 ✓] [TOTW ≥ 1 ✗] [Ülke ≤ 4 ✓] ┐
├────────── Çözüm sahası (11 kart, kulüpten olanlar ✓ işaretli) ──────────────┤
│ Alışveriş listesi: 4 kart, toplam 23.4K · [Kopyala]                         │
│ Kulüpten kullanılan: 7 kart (untradeable önce)                             │
├ ▸ Özelleştir (kapalı): kilitli oyuncular, hariç tut, yalnız kulüp, bütçe ─┤
```
- Streamlined (skor) challenge: saha yerine **skor barı** (hedef 4.100 / mevcut 3.880) + kart listesi; "kısmi gönder" bilgisi (WEB-BUG-04).
- Kriter çipi ✗ ise üstüne gelince sebep.

## 6. KULÜBÜM (`/ut/club`)
Yapışkan filtre çubuğu (pozisyon, rating aralığı, rarity, lig, ülke, kulüp, untradeable, kopya), sıralama, ızgara/tablo geçişi, sanal liste (≥1000 kart). Kart üstünde "SBC yemi" rozeti (gereksiz kopya / düşük değer). Toplu seçim → "Şu SBC'de kullan".

## 7. EVOLUTIONS (`/ut/evo`)
Her evo kartı: önce → sonra kart çifti (base + yükseltilmiş tahmini stat), gereksinim çipleri (max OVR, pozisyon, max pace, PS sayısı), maliyet, bitiş, "Kulübünde uygun N oyuncu" (tıkla → liste). Evo zinciri önizlemesi (FC27 Chain Preview benzeri: bu evo bitince açılabilecekler). Pathway'li evolarda dallanma ağacı.

## 8. KARİYER MASASI (`/career/desk`)
- Üstte **dönem bandı**: "Transfer dönemi bitimine 12 gün · Bütçe 18,5 M € · Maaş 72%".
- Kahraman: **taktik uyumu göstergesi** (büyük yarım daire, %), altında 3 zayıf halka **oyuncu kartı** olarak, her birinde "3 aday" butonu (→ transfer merkezi filtreli).
- Sağ sütun: uyarılar (kontrat bitiyor, plato, yaş), zaman çizelgesi Türkçe etiketlerle (WEB-BUG-17).
- Ayarlar ayrı sekmeye (DeskSettings, `key={career.id}` WEB-BUG-01).

## 9. TRANSFER MERKEZİ (`/career/transfer`)
Aday satırları `sm` kart + değer/ücret çubuğu + "alım sonrası bütçe". Kısa liste oyuncu adıyla (WEB-BUG-13), pazarlık tavanı MoneyInput (WEB-BUG-09). Satış planı tablosu: oyuncu · değer · kontrat bitişi · öneri (sat/kirala/tut).

## 10. GELİŞİM (`/career/growth`)
Eksenli çizgi grafik (x = sezon/oyun tarihi, y = OVR; POT kesikli), oyuncu seçici, "en çok yükselenler" bar grafiği (kadro geneli), plato uyarısı `--warn` rengiyle. Grafik kütüphanesi: hafif (uPlot ya da Recharts lazy-load).

## 11. İMPORT (`/career/import`)
Adım göstergesi (1 Script indir → 2 LE'de çalıştır → 3 Log'u sürükle) + her adımda ekran görüntüsü, sürükle-bırak alanı (dosya boyutu doğru: KB/MB), yükleme ilerlemesi, tablo bazlı doğrulama sonucu ("players 21.620 ✓, teams 841 ✓, career_users ✗ kariyer algılanmadı"). Tek tık senkron hazır olunca (07 §6) adım 3 otomatik.

## 12. ANA SAYFA + MARKA (mod başına)
- `/` kariyer: "Kariyerine devam et" kartı (son kariyer, son import tarihi, 3 öneri) + hızlı eylemler.
- `/ut` UT: bugünün SBC'leri (4 kart) + sonraki yenileme sayacı + kadrom özeti + izlenen oyuncular fiyat değişimi.
- Marka: "FCareer" ortak, alt başlık moda göre "Kariyer" / "Ultimate Team" (DS-10).

## 13. GÖREV LİSTESİ
| ID | Pri | Ekran | Kabul |
|---|---|---|---|
| UI-01 | P1 | Rarity token'ları + `--warn` + iskelet/boş/hata bileşenleri (G5, G10) | Ladle'da tüm durumlar |
| UI-02 | P1 | UT oyuncu sayfası (§2) | Ekran onayı (D-07 kuralı) |
| UI-03 | P1 | Kadro kurucu sağ panel + yedek + menajer (§3) | Δ değerleri worker'da, < 100 ms |
| UI-04 | P1 | SBC kart ızgarası + geri sayım (§4) | |
| UI-05 | P1 | SBC çözüm sayfası sırası (§5) | |
| UI-06 | P2 | Kulübüm filtre + sanal liste (§6) | 3000 kartta 60 fps kaydırma |
| UI-07 | P2 | Evo kartları + uygun oyuncu sayısı (§7) | |
| UI-08 | P2 | Masa + transfer + gelişim yenilemesi (§8-10) | |
| UI-09 | P2 | Import stepper (§11) | |
| UI-10 | P2 | Mod başına ana sayfa (§12) | |
| UI-11 | P2 | Playwright görsel snapshot + axe CI (WEB-ARCH-06) | CI yeşil |
| UI-12 | P1 | Saha slotu dolu/boş hâli (§14) | Referans görsellerle birebir yapı |
| UI-13 | P1 | Mevkiye tıklayınca sağ panel, mevki + meta sıralaması (§15) | Tıklanan mevkide oynayabilen oyuncular en iyiden en kötüye |
| UI-14 | P1 | Kadro oluştururken sağ panel varsayılan hâli, boş değerler, UT maks. fiyat, kariyerde basit hâl (§16) | Oyuncu yokken tüm değerler boş; maks. fiyat üstü oyuncu önerilmez |

---
## 14. SAHA SLOTU — DOLU ve BOŞ HÂL (Buğra'nın isteği)
Geçerli olduğu yerler: UT kadro kurucu ve kariyer kadrosu (squad koyulan her saha).
Referans görseller: `assets/saha_slot_dolu.png` (dolu hâl), `assets/saha_slot_bos.png` (boş hâl).

Her slot yukarıdan aşağıya üç katmandan oluşur:
1. **Kart:** kart şeklinde durur.
   - Dolu: oyuncunun kartı.
   - Boş: aynı kart siluetinde, ortasında "+" işareti olan boş kart.
2. **Rol:** kartın hemen altında rol adı (ör. Advanced Forward, Shadow Striker, Box-to-Box, Inside Forward, Fullback, Defender, Goalkeeper).
3. **Mevki:** rolün altında mevki kısaltması (ör. ST, CAM, CM, CDM, RW, LW, RM, LM, RB, LB, CB, GK).

Boş slotta da rol ve mevki satırları görünür (WEB-BUG-11 ile aynı yön).

---
## 15. MEVKİYE TIKLAYINCA SAĞ PANEL — OYUNCU EKLE (Buğra'nın isteği)
Geçerli olduğu yerler: UT kadro kurucu ve kariyer kadrosu.
Referans görsel: `assets/oyuncu_ekle_paneli.png`.

- Sahada herhangi bir mevkiye (dolu ya da boş slot) tıklanınca sağda bir panel açılır.
- Panelde **o mevkide oynayabilen oyuncular** listelenir.
- Sıralama **o mevkiye ve meta hesaplamalarına göre en iyiden en kötüye** doğrudur.
- Oyuncular kart şeklinde gösterilir.

---
## 16. KADRO OLUŞTURURKEN SAĞ PANEL — VARSAYILAN HÂL + MAKS. FİYAT (Buğra'nın isteği)
Referans görseller: `assets/sag_panel_kadro_bilgisi_dolu.png`, `assets/sag_panel_kadro_bilgisi_bos.png`, `assets/sag_panel_oneri_filtreleri.png`.

### 16.1 Varsayılan hâl (hiçbir mevki seçili değilken)
Kadro oluştururken sağ tarafta hiçbir şeye basılı değilken bu panel görünür (görsellerdeki içerik):
- **Kadro oluştur:** kadro adı alanı (kaydet).
- **Kadro bilgisi:** formasyon seçici, squad rating (yıldızlarla), 6 ortalama (PAC, SHO, PAS, DRI, DEF, PHY), "Tüm kadro ortalamaları" açılır bölümü.
- **Toplam kimya:** bar + X/33, altında lig/ülke/kulüp bazında kimya dökümü ("Daha fazla" ile açılır).
- **Fiyat:** konsol ve PC toplam fiyatı + fiyat grafiği.

**Oyuncu yoksa değerler boş görünür:** ortalamalar "-", squad rating 0, kimya 0/33, fiyat 0, grafik yok.

Bir mevkiye tıklanınca panel §15'teki oyuncu ekle paneline döner.

### 16.2 Maks. fiyat (yalnız Ultimate Team)
- Marketten veya kulüpten oyuncu alırken **maks. fiyat** seçeneği olur.
- Önerilen oyuncular bu maks. fiyata göre çıkarılır (fiyatı üstündekiler gösterilmez).
- Öneri filtreleri paneli (görsel): bütçe (maks. takım fiyatı), kaynak (market / kulüp), genel filtreler (ülke, lig, kulüp, live event, daha fazla filtre), filtreleri temizle.

### 16.3 Kariyer modu karşılığı (daha basit)
- Kariyer kısmında bunun daha basit hâli olur.
- Meta odaklı değil, keyif odaklı: o mevkiye uygun, kullanıcının ne istediğine göre bir senaryo.
