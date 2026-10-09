# 02 · TASARIM SİSTEMİ v2 — "yapay zekâ görünümü"nden çıkış
Hedef: Ürün bir "SaaS dashboard şablonu" gibi değil, **futbol oyuncusunun kendi aleti** gibi görünmeli: kart, saha, forma, skor tabelası, scout raporu dili. Önce oyuncu (kart/yüz/rating), sonra sayı, en son açıklama metni.

---

## 1. TEŞHİS — neden "AI yapmış" gibi duruyor (`VERIFIED`, WEB yolları)
| # | Belirti | Nerede | Neden kötü |
|---|---|---|---|
| 1 | Teal (#00e0b8) vurgu + koyu lacivert zemin + radial-gradient arka plan | `index.css:9-26, 112-115` | 2023-25 AI landing page'lerinin varsayılan paleti. Futbolla bağı yok. |
| 2 | Tailwind `emerald-*`, `slate-*`, `white` ölçekleri **override** edilmiş, sadece bazı adımlar | `index.css:9-26` | Ramp monoton değil (slate-300 orijinal, slate-400 özel); renkler öngörülemez. |
| 3 | "Inter" yazılmış ama **yüklenmiyor** | `index.css:27`, `index.html` | Kullanıcı sistem fontu görüyor; karakter yok. |
| 4 | Her blok aynı `Card`: `rounded-2xl` + `shadow-sm` + `hover:shadow-md` + `animate-fade-up` + küçük UPPERCASE gri başlık | `components/ui.tsx:10` (≈16 kullanım) + UT'de ikinci kopya `PANEL/PANEL_TITLE` (`UtSquadPage.tsx:33-34`) | Hiyerarşi yok; tıklanmayan karta hover gölgesi = yanlış affordance; her şey aynı ağırlıkta. |
| 5 | Durum = emoji 🟢🟡🔴, oy = 👍👎, kimya = ◆◇ glif | `lib/format.ts:66`, `SbcVote.tsx:35-36`, `UtSquadPage.tsx:129,454` | En belirgin "LLM çıktısı" işareti; platforma göre farklı render; renk körü erişimi yok. |
| 6 | Hemen her kartın altında `text-xs text-muted` algoritma açıklaması | `UtSquadPage.tsx:264,297,398,406`, `TraditionalSbc.tsx:166,197-200`, `UtSbcPage.tsx:160,198`, `UtMetaPage.tsx:76` | Arayüz kendini anlatıyor; oyuncu okumaz. Bilgi UI'da değil düzyazıda. |
| 7 | Ana sayfa = H1 slogan + 2 kolon 4 özellik kartı | `pages/HomePage.tsx` | Klasik şablon; veri yok, görsel yok, UT modunda bile kariyer metni. |
| 8 | Saha her temada koyu yeşil gradient, çizgiler soluk | `squadBuilder/Pitch.tsx` (#123420→#0e2a1a, stroke #2f5b3e) | Saha "ekranın kahramanı" olmalı; şu an sönük bir kutu. |
| 9 | Kart çerçeveleri jenerik CSS gradient; iki farklı palet; küçük boyutta 4.6px font | `lib/futCard.ts`, `components/futcard/FutCard.tsx`, `Pitch.tsx:43` | Oyuncunun en çok baktığı nesne (kart) en zayıf tasarlanmış nesne. |
| 10 | İki stil sistemi: eski `slate/emerald + dark:` (Layout'ta 23 `dark:`) ve yeni semantik token (`bg-surface`, `text-ink`) karışık; ~40 hardcoded hex | `Layout.tsx`, `ui.tsx`, `UtImportPage.tsx:81 vs 192` | Tutarsızlık "birden çok AI oturumunun yamalı işi" hissi verir. |

---

## 2. YENİ GÖRSEL DİL — "MATCHDAY" (✅ onaylı → 08 D-06, D-07)
Üç referans dünyası birleşir; hiçbiri "SaaS" değil:
1. **TV yayın grafikleri** (skor bandı, kadro dizilişi ekranı, oyuncu alt-bant): yoğun kondanse tipografi, net bloklar, keskin köşeler, hareket = kayan bant (fade değil).
2. **Basılı maç programı / scout raporu** (kâğıt dokusu açık tema, tablolar, ince çizgiler, numaralı dipnot): veri-yoğun, ciddi.
3. **Oyunun kendi nesneleri** (kart, saha, forma, kimya çizgisi): ekranın kahramanı.

Kurallar:
- **Saha ve kart ekranın %60'ını alır**, paneller onların etrafında.
- Köşe yarıçapı küçük ve tutarlı: 2px (veri), 6px (panel), kart kendi şeklinde. `rounded-2xl` yasak.
- Gölge yerine **çizgi ve kontrast**; derinlik yalnız sürüklenen/uçan nesnede.
- Animasyon anlamlı: rating/kimya değişince sayaç, kart sürüklenince fizik, sekme geçişinde yatay kayma. Statik içerikte `fade-up` yok.
- Açıklama metni yok → `?` ikonlu popover, ilk kullanımda tek seferlik coach-mark.
- Emoji yok. Durum = **şekil + renk + metin** (●/▲/■ değil; özel SVG ikon seti).

### 2.1 Renk token'ları (`src/styles/tokens.css`)
Tailwind ölçeklerini **override etme**; yeni semantik isimler aç (Tailwind v4 `@theme`).
```css
@theme {
  /* nötr — kâğıt & mürekkep */
  --color-ink-950:#0B0D10; --color-ink-900:#12151A; --color-ink-800:#1A1E25; --color-ink-700:#252A33;
  --color-ink-500:#5B6472; --color-ink-300:#A9B0BA; --color-paper-50:#F6F4EF; --color-paper-100:#ECE9E1; --color-paper-200:#DCD7CB;
  /* saha */
  --color-grass-900:#0F3B24; --color-grass-700:#17553A; --color-grass-500:#22764F; --color-chalk:#EDEBE3;
  /* marka vurgusu: "stadyum ışığı" sarısı (tek vurgu) */
  --color-flood-400:#FFD23F; --color-flood-500:#F2C200; --color-flood-700:#8A6D00;
  /* anlam renkleri (oyun içi konvansiyona yakın) */
  --color-good:#2FBF71; --color-ok:#E6B325; --color-bad:#E5484D; --color-info:#3E8EF7;
  /* rating ölçeği (stat değeri renkleri, FUT konvansiyonu) */
  --color-r-elite:#1F9D55; --color-r-high:#7AC74F; --color-r-mid:#E8C547; --color-r-low:#E58A3A; --color-r-poor:#D64545;
}
:root{ --bg:var(--color-paper-50); --panel:#FFFFFF; --line:var(--color-paper-200); --text:var(--color-ink-900); --sub:var(--color-ink-500); --accent:var(--color-ink-900); --accent-ink:var(--color-flood-400); }
:root.dark{ --bg:var(--color-ink-950); --panel:var(--color-ink-900); --line:var(--color-ink-700); --text:var(--color-paper-50); --sub:var(--color-ink-300); --accent:var(--color-flood-400); --accent-ink:var(--color-ink-950); }
```
- Açık tema = maç programı (krem kâğıt, siyah mürekkep, sarı yalnız seçili durum). Koyu tema = gece maçı (mürekkep siyahı, sarı ışık).
- Rating renk eşikleri (stat hücreleri): ≥90 elite, 80-89 high, 70-79 mid, 60-69 low, <60 poor. Tek fonksiyon `ratingTone(v)`.

### 2.2 Tipografi
| Rol | Font (öneri) | Kullanım |
|---|---|---|
| Display / sayılar | **Barlow Condensed** 600–800 (alternatif: Saira Condensed) | Rating, skor, kart adı, sayfa başlıkları, tablo başlıkları |
| Metin | **IBM Plex Sans** 400–600 (alternatif: Source Sans 3) | Gövde, form |
| Mono / stat | **IBM Plex Mono** 500 | Coin fiyatı, kod, tabular sayılar |
- `@fontsource/*` ile **self-host** (GDPR + performans); Türkçe glif (ğ, ş, ı, İ) kontrolü zorunlu.
- Ölçek (px): 11 · 12 · 14 · 16 · 20 · 28 · 40 · 56. Minimum okunur metin 11px — kart küçük varyantında bile.
- Tüm sayılar `font-variant-numeric: tabular-nums`.

### 2.3 İkon seti
`lucide-react` (nötr çizgi) + **kendi futbol ikonları** SVG sprite: mevki rozeti, kimya elması (0–3 dolu), PlayStyle ikonları (her PS için tek SVG, PS+ = altın çerçeve), AcceleRATE (Explosive/Controlled/Lengthy oklar), weak foot/skill yıldızları, untradeable kilidi, coin, forma. Emoji kaldırılır.

### 2.4 Durum dili (emoji yerine)
| Eski | Yeni bileşen | Görünüm |
|---|---|---|
| 🟢 GREEN | `<FitBadge state="good">` | Dolu yeşil dikdörtgen + "UYGUN" (kondanse, 11px) |
| 🟡 YELLOW | `state="ok"` | Sarı çerçeve, yarım dolu + "SINIRDA" + eksik silah adı tooltip |
| 🔴 RED | `state="bad"` | Kırmızı çapraz çizgili + "EKSİK" |
| 👍/👎 | `<VoteControl>` | Yukarı/aşağı ok + sayı + yüzde bar |
| ◆◇ | `<ChemDiamonds value={0..3}>` | 3 SVG elmas |

---

## 3. NESNELER — ekranın kahramanları

### 3.1 Tek kart bileşeni: `<PlayerCard>` (DS-05)
Mevcut iki FutCard + iki palet **silinir**; tek bileşen:
```ts
type CardSize = 'xs'|'sm'|'md'|'lg'   // xs: saha mobil (rating+mevki+yüz), sm: saha masaüstü, md: liste/grid, lg: oyuncu sayfası
interface PlayerCardProps {
  player: { name: string; rating: number; position: string; altPositions?: string[]; faceUrl?: string;
            nation?: Asset; club?: Asset; league?: Asset; stats?: Record<'PAC'|'SHO'|'PAS'|'DRI'|'DEF'|'PHY', number>;
            playstyles?: { id: string; plus: boolean }[]; weakFoot?: number; skills?: number; accelerate?: string }
  design: CardDesign          // promo/tier görseli — 03 §5'teki `card_design` tablosundan
  overlay?: 'chem'|'fit'|'price'|'none'; chem?: 0|1|2|3; fit?: { pct: number; state: 'good'|'ok'|'bad' }; price?: number
  size: CardSize; interactive?: boolean
}
```
- **Kart görseli veri-güdümlü:** `CardDesign = { id, frameShape, bgLayers[], foil?: 'none'|'shine'|'holo', textColor, accentColor, ratingColor, pattern? }`. Her promo için (TOTW, POTM, Future Stars, Icon, Hero, Evo…) admin panelden bir `card_design` kaydı açılır; çerçeve **bizim SVG'miz**, EA kart arka planı kopyalanmaz (`LEGAL-RISK` notu 03 §6).
- Render: SVG (çerçeve + desen) + `<img>` yüz (cutout PNG) + HTML metin. Hover'da hafif foil (CSS `mask` + gradient), `prefers-reduced-motion`'da kapalı.
- Boyut kuralı: `xs` < 56px genişlikte isim gösterme, sadece rating + mevki + yüz; isim tooltip/long-press.
- Yüz yoksa: baş harf yerine **siluet SVG** (forma rengiyle).

### 3.2 Saha: `<Pitch>` (DS-06)
- Gerçek çim dokusu: SVG `pattern` ile biçilmiş şeritler (açık/koyu `grass-700/500`), kireç çizgileri `chalk`, gerçek oranlar (105×68 → dikey 68×105).
- **Kimya çizgileri** (FUT'taki gibi): bağlantılı slotlar arasında çizgi; renk = iki oyuncunun ortak bağ sayısı (0 kırmızı kesik, 1 sarı, 2+ yeşil). UT'de default açık, toggle'lı.
- Taktik katmanı (kariyer): her slotta rol oku (overlap, içe kesme, derine düşme) — `movement_tags`'tan otomatik çizilir; "atak paterni" NLG'nin görsel karşılığı.
- Mobil: saha tam genişlik, kart `xs`; slot'a dokun → alttan **bottom sheet** (oyuncu ara/rol/davranış). `touch-action:none` yalnız uzun basma (300ms) sonrası (şu an tüm kartlarda — scroll tuzağı).
- Klavye: slot seç (Tab), `Enter` = değiştir, `M` = taşı modu, ok tuşları hedef slot, `Enter` bırak.

### 3.3 Tablolar (oyuncu DB, kulüp, SBC listesi)
- FBref/Transfermarkt yoğunluğu: 32px satır, sticky başlık + sticky ilk kolon (yüz+isim), sütun seçici, stat hücreleri `ratingTone` arka planlı, sanal liste (`@tanstack/react-virtual`).
- Satıra hover → sağda **önizleme kartı** (masaüstü), mobilde satır = kart listesi.

### 3.4 Panel / Section
`Card` bileşeni yerine iki varyant:
- `<Section title kicker?>` — başlık 20px kondanse, altında 1px çizgi, gölge yok, köşe 6px.
- `<Stat label value delta? tone?>` — büyük sayı + küçük etiket; KPI satırları için.

---

## 4. BİLEŞEN GÖREVLERİ (DS)
| ID | Görev | Dosyalar | Kabul kriteri |
|---|---|---|---|
| DS-01 (P1) | Token dosyası; `index.css`'teki Tailwind ölçek override'larını kaldır; tüm `emerald-*`/`slate-*`/`teal-*`/hex kullanımlarını semantik token'a çevir (codemod: `rg "emerald-|slate-|teal-|#[0-9a-f]{6}" src`) | `index.css`, tüm `*.tsx` | `rg` sonucu 0 (kart tasarım config'i hariç); iki tema görsel regresyon snapshot'ı |
| DS-02 (P1) | Fontları self-host et (`@fontsource/barlow-condensed`, `@fontsource/ibm-plex-sans`, `@fontsource/ibm-plex-mono`), ölçek token'ları | `main.tsx`, tokens | Lighthouse font-display swap, Türkçe glifler doğru |
| DS-03 (P1) | `ui.tsx`'i böl: `Button`, `Field`, `Select`, `Section`, `Stat`, `Tabs`, `Popover`, `Dialog` (Radix primitives — headless, kendi stilimiz), `Toast`, `Skeleton`. Eski `Modal`'ı sil; tek `Dialog` (portal + focus trap) | `components/ui/*` | Tüm sayfalar yeni primitives'le derlenir; axe-core 0 kritik |
| DS-04 (P1) | Emoji → SVG ikon seti + `FitBadge`, `ChemDiamonds`, `VoteControl` | `lib/format.ts:66` vb. | `rg "[🟢🟡🔴👍👎◆◇]"` = 0 |
| DS-05 (P0 for UT) | Tek `PlayerCard` + `card_design` config; eski iki FutCard'ı ve `lib/futCard.ts` paletlerini sil | `components/card/*` | Storybook/ladle'da 4 boyut × 8 tasarım × 2 tema; xs'de 11px altı metin yok |
| DS-06 (P1) | `Pitch` v2: çim deseni, kimya çizgileri, rol okları, bottom-sheet, klavye modu | `components/pitch/*` | Mobil 375px'te scroll tuzağı yok; klavyeyle kadro değiştirilebilir |
| DS-07 (P2) | Hareket sistemi: `motion` (framer-motion) ile kart sürükleme fiziği, sayı count-up, sekme kayması; `fade-up` animasyonunu kaldır | — | `prefers-reduced-motion` testi |
| DS-08 (P1) | Metin diyeti: her sayfadaki açıklama paragraflarını `HelpPopover`'a taşı; boş durumlar eylem içersin ("Kulübünü içe aktar" butonu) | §1 #6 satırları | Sayfa başına en fazla 1 satır yardımcı metin |
| DS-09 (P2) | Tasarım sandbox'ı: `ladle` veya Storybook; her primitive + PlayerCard + Pitch hikâyesi | — | CI'da build |
| DS-10 (P1) | Marka: "FC Kariyer — Taktik Zekâ" adı UT modunda da görünüyor; mod bazlı başlık + logo (forma numarası/taktik tahtası motifli monogram) | `Layout.tsx`, `index.html` | — |

---

## 5. EKRAN EKRAN YENİDEN TASARIM (UX)

### UX-01 Ana sayfa → mod bazlı "Matchday" panosu
- **UT modu:** üstte yayın bandı: "Bugün yenilenen SBC'ler · 3 saat 12 dk" + bitmek üzere olanlar; altında **kadronun saha önizlemesi** (rating, kimya, toplam değer), "Yeni kartlar" yatay kart şeridi (promo), "Objective ilerlemen", "Evolution önerisi: kulübündeki X oyuncusu Y evo'ya uygun".
- **Kariyer modu:** aktif kariyer kartı (kulüp armasına yakın renk, sezon, bütçe), **SquadFit göstergesi** büyük, "en zayıf 3 halka" kart olarak + "çözüm: 3 aday" butonu, sezon takvimi/transfer penceresi geri sayımı, wonderkid radar.
- Girişsiz kullanıcı: interaktif demo — hazır bir replika taktik (ör. Pep 2010-11) sahada, "kendi kadronla dene" CTA.

### UX-02 UT Squad Builder (en kritik ekran)
Düzen (masaüstü):
```
┌ üst bant: formasyon ▾ | Rating 86 | Kimya 33/33 | Değer 1.2M ▾platform | Paylaş | Kaydet ┐
├──────────────── SAHA (sol %62) ────────────────┬──── SAĞ PANEL (%38) ─────────────┤
│  kartlar + kimya çizgileri                     │  sekmeler: Ara · Kulübüm · Öneri │
│  yedek kulübesi (7) + menajer slotu altta     │  filtre çipleri (lig/ülke/kulüp) │
│                                                 │  sanal kart grid'i (md kart)     │
└─────────────────────────────────────────────────┴───────────────────────────────────┘
```
- Slot seçiliyken sağ panel otomatik o mevkiye filtrelenir ve her aday için **"bu kadroya eklenince kimya Δ / rating Δ / fiyat"** gösterir (rakiplerde yok → fark).
- Karta tıkla → kart arkası çevrilir (flip): 29 attribute, PlayStyles, chem style önerisi (yüzdeli), rol uyumu.
- Kimya stili: kart üzerinde ikon; seçici kart arkasında.
- Mobil: saha üstte (xs kart), arama bottom-sheet; üst bant yapışkan.

### UX-03 SBC listesi
- futbin/fut.gg kalıbı + fark: kategori sekmeleri (Players, Upgrades, Challenges, Foundations, Streamlined/Item Score, Expiring) + filtre (ödül türü, tekrarlanabilir, maliyet aralığı) + sıralama (yeni, bitiş, maliyet, **değer/maliyet oranı**).
- Kart satırı: ödül görseli (oyuncu kartı veya paket ikonu), isim, **benim kulübümle maliyet** (kulüp import edilmişse) vs piyasa maliyeti, geri sayım, tekrar, topluluk oyu.
- SBC detay: challenge'lar dikey adımlar; her biri için gereksinim çipleri + "çöz" → çözüm sahada + "bu çözüm kulübünden X kart kullanıyor, Y kart alman gerek (toplam Z coin)".

### UX-04 Oyuncu sayfası
- Sol: `lg` kart + versiyon seçici (aynı oyuncunun tüm kartları yatay şerit). Orta: attribute blokları (6 ana grup, renkli hücre), PlayStyles ikonları, AcceleRATE, boy/ayak. Sağ: fiyat grafiği (platform), rol uyumları (RoleFit listesi), benzer/alternatif kartlar (aynı lig/ülke = kimya alternatifi), evo uygunluğu.
- Kariyer modunda: potential eğrisi (MODELED*), değer, gelişim planı, "taktiğindeki hangi slota girer" mini saha.

### UX-05 Kariyer Taktik/Kadro kurucu
> Kariyer modunun tamamı için bkz. `13_CAREER_MODE_EXPERIENCE.md` (Masa, Kadro, Transfer, Gelişim, Akademi, Tarihçe, Challenge).
- Davranış tag seçimi = saha üstünde slot'a tıkla → radyal/çip menü (FM "player instructions" hissi); çelişen tag'ler kilitli + neden tooltip.
- SquadFit/IntentFit üstte iki büyük gösterge; zayıf halka slotları sahada kırmızı nabız.
- "Atak paterni" = sahada animasyonlu oklar (3-4 saniyelik döngü) — NLG metni opsiyonel altta.

### UX-06 Boş/hata/yükleme durumları
- Skeleton'lar gerçek düzeni taklit eder (kart şekli, saha slotları).
- Hata: ne oldu + ne yapabilirsin + tekrar dene; `ErrorBox`'ın "Beklenmeyen hata" genellemesi kaldırılır (ağ hatası ayrı mesaj).

---

## 6. ERİŞİLEBİLİRLİK & MOBİL (zorunlu)
- WCAG AA kontrast (tokens buna göre seçildi; ajan `@axe-core/react` dev modda açık tutsun).
- Renk tek başına bilgi taşımaz (fit/kimya/rating hücresi = renk + metin/şekil).
- Sürükle-bırakın klavye karşılığı (§3.2).
- Dokunma hedefi ≥ 40px; sahada xs kart 44px + görünmez genişletilmiş hit area.
- Tema "system" `matchMedia` değişimini dinler.

## 7. YASAK LİSTESİ (ajan kontrol listesi — PR'da `rg` ile denetle)
- `rounded-2xl`, `rounded-3xl`, `shadow-xl` statik içerikte
- `animate-fade-up` statik içerikte
- emoji ve ◆◇★✓✕ glifleri UI durumunda (yıldız yalnız WF/SM ikon bileşeninde SVG)
- `bg-gradient-to-*` dekoratif arka planlar (kart tasarımı hariç)
- "Bu özellik … yapar" türü açıklama paragrafları
- Tailwind `emerald/teal/slate/sky` sınıfları yeni kodda
- Glassmorphism (`backdrop-blur` + yarı saydam beyaz) — header hariç

## 8. GÖRSEL DOĞRULAMA
- Playwright ile her route için 375 / 768 / 1440 genişlikte, açık+koyu tema ekran görüntüsü → `tests/visual/`; PR'da diff.
- Tasarım kabulü Buğra'da: önce DS-05 (kart) + DS-06 (saha) + UX-02 tek ekran olarak yapılsın, onaylanınca geri kalanı.
