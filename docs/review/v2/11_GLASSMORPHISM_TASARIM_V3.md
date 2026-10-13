# 11 · TASARIM SİSTEMİ v3 — GLASSMORPHISM (tüm site baştan)
İstek (Buğra): Glassmorphism tasarım. Tüm site baştan yeni bir tasarıma geçsin, yeni versiyonu olsun. Yeni görsel tasarımda bilinen hatalara yer olmayacak.

**Karar etkisi:** Bu doküman v1 D-06 "Matchday" görsel yönünün yerini alır → `08_DECISIONS_LOG.md`'ye **D-26** olarak ekle: "Görsel dil v3 = glassmorphism (11). D-06'nın Radix + Ladle kararı geçerli; renk/yüzey dili değişti." 08_TASARIM_GORUNUM'daki **yerleşimler** (§1-§16: oyuncu sayfası, kadro kurucu, SBC, saha slotu, sağ panel) geçerli kalır; yalnız görsel katman bu dokümana göre çizilir.

---
## 1. İLKELER
1. **Derinlik katmanları:** arka plan (renkli, hareketsiz) → cam paneller → cam üstünde içerik. Cam yalnız katmanı ayırmak için, süs için değil.
2. **İçerik camın üstünde net:** metin asla doğrudan bulanık renk geçişinin üstünde okunmaya çalışılmaz. Camın minimum opaklığı kontrastı garanti eder (§3.3).
3. **Kart ve saha kahraman:** EA kart render'ı ve saha camın içinde ama kendileri cam değil (kartın üstüne blur/tint uygulanmaz).
4. **Az ama yoğun cam:** aynı anda ekranda en fazla ~6 `backdrop-filter` öğesi. Liste satırları, ızgaradaki kartlar, saha slotları blur kullanmaz (performans, §7).
5. **Mod kimliği:** Kariyer = sıcak (amber) vurgu, UT = soğuk (iris/mavi-mor) vurgu. Arka plan renk lekeleri moda göre değişir. Aynı bileşenler.
6. **Erişilebilirlik önce:** `prefers-reduced-transparency`, `prefers-reduced-motion`, `prefers-contrast: more` ve `@supports not (backdrop-filter)` için düz yüzey fallback'i zorunlu.

---
## 2. ARKA PLAN
```
Katman 0  --bg-base: koyu lacivert-siyah zemin
Katman 1  "aurora" lekeleri: 3 büyük radial-gradient (mod rengi + nötr), position: fixed, blur yok (gradient zaten yumuşak), opaklık 0.35-0.55
Katman 2  ince saha çizgisi deseni (SVG, %4 opaklık) + film grain (SVG feTurbulence, %3) — banding'i kırar
```
- Lekeler sabit (scroll'la kaymaz), sayfa geçişinde 600 ms renk geçişi (reduced-motion'da yok).
- Açık temada zemin `#eef1f7`, lekeler pastel, grain %2.

## 3. TOKEN'LAR (`src/design/tokens.css`, Tailwind v4 `@theme`)
### 3.1 Koyu tema (varsayılan)
```css
:root, :root[data-theme="dark"] {
  /* zemin */
  --bg-base: #070a12;
  --aurora-1: #2b4cff;  /* UT: iris */   --aurora-2: #00c2a8;  --aurora-3: #7a2cff;
  /* kariyerde [data-mode="career"] ile: --aurora-1:#ff9f1c; --aurora-2:#3a7d44; --aurora-3:#c2410c; */

  /* cam yüzeyler (renk = beyazın düşük opaklığı + koyu taban karışımı) */
  --glass-1: rgba(18, 24, 38, 0.62);     /* paneller, sağ panel, kenar çubuğu */
  --glass-2: rgba(22, 30, 48, 0.74);     /* dialog, popover, bottom sheet */
  --glass-3: rgba(255, 255, 255, 0.06);  /* çip, segment, input zemini (panel içinde) */
  --glass-hover: rgba(255, 255, 255, 0.09);
  --glass-press: rgba(255, 255, 255, 0.12);
  --glass-blur-1: 18px;  --glass-blur-2: 24px;  --glass-saturate: 140%;
  --glass-border: rgba(255, 255, 255, 0.10);
  --glass-highlight: rgba(255, 255, 255, 0.18);   /* üst kenar 1px iç ışık */
  --glass-shadow: 0 8px 32px rgba(0, 0, 0, 0.45), 0 1px 0 rgba(255,255,255,0.04) inset;

  /* metin */
  --ink: #f2f5fb;  --ink-2: #c4cbd8;  --muted: #8e98aa;  --faint: #5d6678;

  /* vurgu (moda göre) */
  --accent: #8f9bff;  --accent-ink: #0b0f1d;  --accent-soft: rgba(143,155,255,0.16);
  /* kariyer: --accent:#ffb547; --accent-soft: rgba(255,181,71,0.16); */

  /* durum */
  --good: #4ade80;  --good-soft: rgba(74,222,128,0.14);
  --warn: #fbbf24;  --warn-soft: rgba(251,191,36,0.14);
  --bad:  #f87171;  --bad-soft:  rgba(248,113,113,0.14);
  --info: #60a5fa;  --info-soft: rgba(96,165,250,0.14);

  /* kimya / rating rampası */
  --chem-0: #5d6678; --chem-1: #f59e0b; --chem-2: #a3e635; --chem-3: #22c55e;
  --rate-90: #22c55e; --rate-80: #84cc16; --rate-70: #eab308; --rate-60: #f97316; --rate-low: #ef4444;

  /* rarity (yalnız kart çerçevesi/rozetler) */
  --r-bronze:#b07a4f; --r-silver:#b8c2cc; --r-gold:#e3c15c; --r-totw:#d4af37; --r-icon:#f3ead2; --r-hero:#8b5cf6; --r-evo:#14b8a6;

  /* saha */
  --pitch-1: rgba(16, 94, 58, 0.55); --pitch-2: rgba(12, 74, 46, 0.55); --pitch-line: rgba(255,255,255,0.35);

  /* ölçü */
  --radius-sm: 10px; --radius-md: 14px; --radius-lg: 20px; --radius-xl: 28px;
  --space-1: 4px; --space-2: 8px; --space-3: 12px; --space-4: 16px; --space-5: 24px; --space-6: 32px;
  --font-display: "Barlow Condensed", system-ui, sans-serif;
  --font-body: "Inter", system-ui, sans-serif;
  --font-num: "IBM Plex Mono", ui-monospace, monospace;
}
```
### 3.2 Açık tema
```css
:root[data-theme="light"] {
  --bg-base: #eef1f7;
  --glass-1: rgba(255,255,255,0.62); --glass-2: rgba(255,255,255,0.78); --glass-3: rgba(10,20,40,0.05);
  --glass-hover: rgba(10,20,40,0.07); --glass-press: rgba(10,20,40,0.10);
  --glass-border: rgba(10,20,40,0.10); --glass-highlight: rgba(255,255,255,0.9);
  --glass-shadow: 0 8px 28px rgba(20,30,60,0.12);
  --ink: #0d1424; --ink-2: #2b3547; --muted: #5b6577; --faint: #8b94a5;
  --accent: #4353ff; --accent-ink: #ffffff;   /* kariyer: #c2410c */
}
```
### 3.3 Kontrast kuralı
- Metin her zaman cam yüzeyin **üstünde**; camın opaklığı koyu temada ≥ 0,62, açıkta ≥ 0,62 → `--ink` / `--ink-2` / `--muted` için WCAG AA (4,5:1) en kötü aurora rengine karşı test edilir (Playwright + axe, §8).
- `--faint` yalnız dekoratif/devre dışı metin.
- Sayı ve stat renkleri (`--rate-*`) her zaman rakamın kendisinde değil, rakamın arkasındaki küçük **çipte** (rakam `--ink`, çip zemini rampa rengi %18) → renk körlüğü + kontrast güvenli.

### 3.4 Fallback'ler
```css
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  :root { --glass-1: rgba(18,24,38,0.96); --glass-2: rgba(22,30,48,0.98); }
}
@media (prefers-reduced-transparency: reduce), (prefers-contrast: more) {
  :root { --glass-1: #121826; --glass-2: #161e30; --glass-3: #1c2538; }
  .glass { backdrop-filter: none !important; }
}
@media (prefers-reduced-motion: reduce) { * { animation: none !important; transition: none !important; } }
```
Ayarlar sayfasında "Şeffaflığı azalt" anahtarı (`data-transparency="reduced"`) aynı fallback'i elle açar.

## 4. YÜZEY SINIFLARI
```css
.glass   { background: var(--glass-1); border: 1px solid var(--glass-border); border-radius: var(--radius-lg);
           backdrop-filter: blur(var(--glass-blur-1)) saturate(var(--glass-saturate));
           -webkit-backdrop-filter: blur(var(--glass-blur-1)) saturate(var(--glass-saturate));
           box-shadow: var(--glass-shadow); position: relative; }
.glass::before { content:""; position:absolute; inset:0; border-radius:inherit; pointer-events:none;
           background: linear-gradient(180deg, var(--glass-highlight), transparent 28%); opacity:.35;
           mask: linear-gradient(#000,#000) content-box, linear-gradient(#000,#000); mask-composite: exclude; padding:1px; }
.glass-2 { background: var(--glass-2); backdrop-filter: blur(var(--glass-blur-2)) saturate(var(--glass-saturate)); border-radius: var(--radius-xl); }
.glass-3 { background: var(--glass-3); border: 1px solid var(--glass-border); border-radius: var(--radius-md); }   /* blur yok */
```
Kural: `.glass` iç içe kullanılmaz. Panel içindeki alt bölümler `.glass-3` (blur'suz) ya da sadece ayraç çizgisi.

## 5. TİPOGRAFİ
| Kullanım | Font | Boyut / satır | Not |
|---|---|---|---|
| Sayfa başlığı | Barlow Condensed 700 | 32/36 (mobil 26/30) | büyük harf yok, takip −0,5% |
| Bölüm başlığı | Barlow Condensed 600 | 20/24 | |
| Gövde | Inter 400/500 | 14/20 (mobil 15/22) | |
| Etiket | Inter 500 | 12/16, `--muted` | **11px altı yok** |
| Sayı / stat / fiyat | IBM Plex Mono 500 | 13-28, `tabular-nums` | |
Self-host (Barlow + Plex zaten var, Inter eklenir); `font-display: swap`; latin + latin-ext tek kez (WEB-PERF-04).

## 6. BİLEŞENLER (Radix primitives + v3 stilleri, `src/design/components/`)
| Bileşen | Görünüm | Davranış |
|---|---|---|
| **AppShell** | Üstte 56px cam bar (logo + mod anahtarı Kariyer/UT segment + arama + profil); masaüstünde solda 72px cam ray (ikon + etiket), mobilde altta 64px cam tab bar | Mod = URL öneki (WEB-BUG-07). Aktif öğe vurgu rengi soft zemin |
| **Panel** | `.glass`, başlık satırı (Barlow 20) + opsiyonel sağ eylem | Varsayılan padding 16/20 |
| **SidePanel** (08 §15-16) | Sağda 380px `.glass-2`, üstten alta tam yükseklik; mobilde bottom sheet (%85 yükseklik, sürükle-kapat) | Esc / dış tık kapatır; odak tuzağı; açılış 180 ms slide+fade |
| **Dialog / Sheet / Popover** | `.glass-2` | Radix; scroll kilidi; ilk odak ilk alan |
| **Button** | primary: dolu `--accent`, metin `--accent-ink`; secondary: `.glass-3`; ghost: şeffaf; danger | 40px yükseklik (mobil 44), focus ring 2px `--accent` + 2px offset |
| **Segmented / Tabs** | `.glass-3` hap, seçili öğe dolu cam (`--glass-press`) + vurgu alt çizgi | Ok tuşları, `role=tablist` + tabpanel (WEB-DS-10) |
| **Input / Select / MoneyInput** | `.glass-3`, 40px, ikonlu | MoneyInput: `parseMoney` (WEB-BUG-09), birim çipi |
| **Chip** | `.glass-3` küçük; durum çipleri soft renk zemin | Kapat ikonu lucide `X` |
| **StatTile** | Büyük mono sayı + etiket + Δ çipi | Boş değer "–" (08 §16) |
| **ChemBar** | 33 bölmeli ince bar, `--chem-*` rampası | X/33 yanında |
| **DataTable** | Başlık satırı yapışkan `.glass` (tek blur), satırlar blur'suz, hover `--glass-hover`, zebra yok | Sanal liste ≥200 satır; sıralanabilir başlıklar `aria-sort` |
| **Toast** | `.glass-2` sağ alt | 4 sn, eylemli |
| **Skeleton** | `.glass-3` + yatay parıltı (reduced-motion'da sabit) | Her `useQuery` yükleme durumu |
| **EmptyState / ErrorState** | Panel içinde ikon + tek cümle + eylem butonu | Hata: sebep + "Tekrar dene" (WEB-BUG-05) |
| **HelpPopover** | lucide `CircleHelp` → `.glass-2` popover | Açıklama paragrafı yerine (G2) |
| **Pitch** | Cam panel içinde saha: `--pitch-1/2` şeritli yarı saydam yeşil, çizgiler `--pitch-line` + 1px glow, doğru oranlar (WEB-DS-09) | Blur yok; slotlar 08 §14 |
| **PitchSlot** | Kart (EA render ya da kendi çerçeve) → altında rol hapı (`.glass-3`, Inter 12) → altında mevki hapı (mono 11→12, koyu) ; boş: kesikli kenarlı cam kart siluet + "+" | Tıkla → SidePanel (08 §15); boşta da rol çipi (WEB-BUG-11) |
| **PlayerCard (OWN)** | Rarity çerçevesi + yüz + OVR/pozisyon + 6 stat; altında cam bilgi şeridi (kimya elmasları, rol uyumu, fiyat) | EA render modunda kartın üstüne hiçbir şey basılmaz (06 §2.2) |
| **Chart** | Şeffaf zemin, ızgara `--glass-border`, seri renkleri vurgu + nötr | uPlot/Recharts lazy |

## 7. PERFORMANS KURALLARI
- `backdrop-filter` sayısı ekranda ≤ 6; `.glass` yalnız: AppShell bar/ray, sayfa başına 2-3 panel, açık SidePanel/Dialog.
- Izgaradaki kartlar, tablo satırları, saha slotları, çipler **blur kullanmaz** (`.glass-3` blur'suz).
- Mobilde (`max-width: 768px`) blur 12px, saturate 120%.
- Aurora katmanı tek `position: fixed` div, `will-change` yok, animasyon yalnız mod değişiminde.
- Ölçüt: orta seviye Android'de (4× CPU throttle) kadro kurucuda sürükleme 50+ fps; Lighthouse Performance ≥ 85 (mobil), CLS < 0,05.

## 8. KALİTE KAPILARI — "yeni tasarımda bu hatalara yer olmayacak"
v3 bir sayfa için ancak şu kontrol listesi yeşilse "bitti" sayılır (PR şablonuna kopyala):
| # | Kapı | Nasıl doğrulanır |
|---|---|---|
| Q1 | Yükleme / boş / hata durumlarının üçü de var | Ladle story'leri + Playwright |
| Q2 | 11px altı metin yok; renk yalnız token | stylelint kuralı (hex ve `emerald|slate|teal|sky` yasak), font-size lint |
| Q3 | 375 / 768 / 1280'de yatay taşma yok | Playwright görsel snapshot 3 kırılım |
| Q4 | Klavyeyle tamamlanabilir; axe 0 ihlal (AA) | `@axe-core/playwright` CI |
| Q5 | Kontrast: metin/cam ≥ 4,5:1 en kötü aurora rengiyle | axe + özel kontrast testi |
| Q6 | Kariyer ekranları yalnız yeni motor uçlarını kullanır (13) | Eski uçlara çağrı yok (ağ testi) |
| Q7 | Sayfaya ait bilinen buglar kapalı | Aşağıdaki eşleme tablosu |
| Q8 | Açıklama paragrafı yok, `HelpPopover` var | Gözden geçirme |
| Q9 | Reduced transparency / motion fallback'i görsel snapshot'ta | Playwright `emulateMedia` |
| Q10 | İlk JS ≤ 250 KB, rota başına lazy chunk | Build raporu CI'da (WEB-PERF-01) |

Sayfa ↔ kapatılması zorunlu buglar:
| Sayfa | Zorunlu düzeltmeler |
|---|---|
| Kariyer Masası | WEB-BUG-01, 17, 26; ENG-01..04, 13, 21 |
| Transfer Merkezi | WEB-BUG-06, 09, 13; ENG-02, 08, 19 |
| Gelişim | WEB-BUG-21; ENG-04, 14 |
| Yeni kariyer / Import | WEB-BUG-10, 25; LE-01, LE-02, LE-06 |
| Kariyer kadrosu + taktik | WEB-BUG-11, 28; ENG-05, 06, 10, 11, 12, 16; WEB-UX-07 |
| Replikalar | REP-01..04; ENG-17 |
| UT Kadro Kurucu | WEB-BUG-08, 11, 27; WEB-UX-03, 06; RULE-01, 02 |
| SBC liste + çözüm | WEB-BUG-02, 03, 04, 15, 24 |
| UT oyuncu / kart DB | WEB-UX-01, 02; WEB-DS-01..04; CARD-04, 06 |
| Kulübüm / Import | WEB-BUG-14, 23; WEB-UX-12 |
| Global | WEB-BUG-05, 07, 12, 19, 20, 22; WEB-ARCH-01..05; WEB-PERF-01..03 |

## 9. SAYFA ŞABLONLARI
| Şablon | Yerleşim | Sayfalar |
|---|---|---|
| **Çalışma alanı** | Sol: kahraman (saha) %62 · Sağ: SidePanel 380px (varsayılan: kadro bilgisi 08 §16; slot seçilince oyuncu ekle 08 §15) | UT kadro kurucu, kariyer kadrosu, taktik oluşturucu, replika "kadromla uygula" |
| **Katalog** | Üstte cam filtre çubuğu (yapışkan) · altında ızgara/tablo geçişi · sağda opsiyonel önizleme paneli | UT oyuncular, SBC listesi, replikalar, kulübüm, wonderkids |
| **Detay** | Sol: büyük kart/saha · Sağ: sekmeli bilgi paneli | UT oyuncu, SBC çözüm, replika detay, kariyer oyuncu |
| **Pano** | Üstte KPI bandı (StatTile'lar) · 12 kolon ızgarada 2-3 panel | Kariyer masası, UT ana sayfa, gelişim |
| **Akış** | Ortada tek cam panel, stepper | Import, yeni kariyer, ayarlar, giriş |

## 10. UYGULAMA PLANI
| ID | Pri | Görev | Kabul |
|---|---|---|---|
| V3-01 | P1 | `src/design/` (tokens.css, glass.css, components/), Radix kurulum, Inter self-host, aurora + grain katmanı, tema + mod + şeffaflık anahtarları | Ladle'da tüm bileşenler 2 tema × 2 mod × reduced-transparency |
| V3-02 | P1 | stylelint + font-size lint + axe + Playwright 3 kırılım CI | Kapılar CI'da |
| V3-03 | P1 | AppShell + yeni rota tablosu (D-08) + lazy rotalar | Eski yollar `<Navigate>` |
| V3-04 | P1 | Çalışma alanı şablonu: Pitch + PitchSlot + SidePanel (08 §14-16) — UT ve kariyer ortak | Ekran onayı (D-07 kuralı: önce bu ekran) |
| V3-05 | P1 | Katalog ve Detay şablonları → UT oyuncu, SBC, replikalar | §8 kapıları |
| V3-06 | P1 | Kariyer ekranları yeni motorla (13 ENG-M8) | §8 kapıları |
| V3-07 | P2 | Pano ve Akış şablonları → masa, gelişim, import, ayarlar | §8 kapıları |
| V3-08 | P2 | Eski bileşenleri sil (`ui.tsx` eski Card/Pill/Modal, `Modal.tsx`, `PitchView.tsx`, `PositionCard`, `matchday.css` renkleri), codemod | Repoda eski token/sınıf yok |

Yayın: v3 ayrı branch'te tüm sayfalar §8'i geçince tek seferde yayınlanır (karma görünüm yok). Geçiş süresince `?ui=v3` ön izleme bayrağı ile Buğra ekran ekran onaylar.
