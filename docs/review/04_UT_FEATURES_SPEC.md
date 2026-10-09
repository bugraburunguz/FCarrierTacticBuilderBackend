# 04 · UT ÖZELLİKLERİ v2 — squad builder, SBC çözücü, evolutions, objectives, oyuncu sayfası
Ön koşul: 03'teki `ut_item`, `sbc_*`, `evolution`, `ut_rules` tabloları ve 02'deki `PlayerCard`/`Pitch`. Kural değerleri **daima** `ut_rules`'tan.
Rakip referansı (fut.gg/futbin sayfa çıktılarından): fut.gg builder'da kimya + fiyat + menajer + paylaşım linki + EA taktik kodu export var, **otomatik kurma/optimizasyon yok**; futbin'de "Cheapest by rating", "Cheapest item score", topluluk çözümleri, streamlined solver var. Bizim farkımız: **optimizasyon + açıklanabilirlik + kendi kulübünle maliyet**.

---

## 1. ORTAK MOTOR — `ut-core` (TypeScript paket + Java eşleniği)
Tek doğruluk kaynağı; hem frontend (anlık UI) hem backend (çözücü/değerlendirme) kullanır.
```ts
// web/src/ut-core/
squadRating(ratings: number[], rules): number                  // BUG-01
chemistry(slots: Slot[], manager?: Manager, rules): { perSlot: number[]; total: number; links: Link[] }  // links = kimya çizgileri için
positionEligible(card, slotPosition, rules): boolean            // alt pozisyonlar + eşdeğerlik
applyChemStyle(card, style, chem, rules): FaceStats             // chem seviyesine göre bonus (styleMods)
itemScore(card, rules): number                                  // streamlined SBC
evaluateRequirement(squad, req): { ok: boolean; have: number; need: number }
```
- Java tarafı: `com.fcareer.ut.core` — aynı fonksiyonlar; **paylaşılan test vektörleri** `seed/ut-core-vectors.json` (her iki test suite'i okur).
- Kabul: iki dilde aynı 50+ vektör yeşil.

---

## 2. SQUAD BUILDER v2 (UT-01..07)
| ID | Pri | Özellik | Detay | Kabul |
|---|---|---|---|---|
| UT-01 | P1 | 11 + 7 yedek + 5 rezerv + **menajer** slotu | Menajer: lig/ülke seçimi (UI'da arama), kimyaya kural config'ine göre etki | Menajer değişince kimya anında güncellenir |
| UT-02 | P1 | Üst bant: Rating · Kimya · Toplam değer (platform seçili) · Ortalama yaş · PS+ sayısı | `ut-core` ile | — |
| UT-03 | P1 | Kimya çizgileri + slot başına elmas + "bu slot neden 1 kimya?" popover (hangi eşik eksik: "Premier League için 1 oyuncu daha") | `links` çıktısı | — |
| UT-04 | P1 | **Delta önizleme:** aday kart hover → kadroya girerse rating Δ, kimya Δ, fiyat Δ | Web Worker'da hesap | 60fps hover |
| UT-05 | P1 | Kaynak sekmeleri: Tüm kartlar (`/ut/items`) · Kulübüm (import) · Sadece untradeable · Bütçe ≤ X | Filtre çipleri | — |
| UT-06 | P2 | **"Kimyayı tamamla"** optimizasyonu: kilitli kartları koru, boş/zayıf slotları bütçe içinde 33 kimyaya getiren en ucuz kartları öner (CP-SAT, §3 aynı motor) | Backend `POST /ut/squad/complete` (kredi) | 3 saniye içinde öneri |
| UT-07 | P2 | Paylaşım: kısa link (`/s/{code}`), OG görseli (saha + kartlar, server-side render — `@vercel/og` benzeri veya Java2D), EA taktik kodu export (mevcut codec), klonla | — | Link önizlemesi Discord/WhatsApp'ta görünür |
| UT-08 | P2 | Chem-style önerisi: slot rolüne göre (mevcut RoleFit motoru!) en iyi stil ve elde edilen stat değişimi | `POST /ut/card/{id}/chemstyle` zaten var → rol bilgisini ekle | — |
| UT-09 | P2 | Taktik katmanı: UT kadrosunda da rol+focus seçimi ve **RoleFit** rozetleri (kariyer motorunu UT'ye taşı — rakiplerde yok) | — | — |

---

## 3. SBC ÇÖZÜCÜ v2 (UT-10..14)
### 3.1 Model (CP-SAT — `com.google.ortools:ortools-java`, backend)
Karar değişkenleri: `x[c,s] ∈ {0,1}` (kart c, slot s). Kısıtlar:
- Her slota tam 1 kart; her **gerçek oyuncu** (`ea_asset_id`) en fazla 1 kez (BUG-02 kökten çözülür).
- Mevki uygunluğu (`positionEligible`) — SBC'lerde kimya için mevki önemli.
- Gereksinimler (`SbcChallengeParser` çıktısı): `TEAM_RATING ≥ R` → rating formülü doğrusal değil; yöntem: **rating çok-seti numaralandırma** — önce hedef rating için geçerli 11'li rating çok-setlerini (ör. {86×2, 85×1, 84×8}) ön-hesapla (cache'li tablo, futbin'in "rating combinations"ı), sonra her çok-set için en ucuz kart atamasını CP-SAT ile çöz, en ucuzu al.
- `CHEMISTRY ≥ K`, `MIN/MAX/EXACT count` (lig/ülke/kulüp/rarity/quality/TOTW/rare), `SAME_* ≥ n`, `DIFFERENT_* ≥ n` (yardımcı bool değişkenlerle), `FROM` (karışık ref türleri — mevcut parser hatası düzeltilir), oyuncu seviyesi (gold/silver/bronze).
- Kimya: lig/ülke/kulüp sayaçları tamsayı değişken, eşik bool'ları (`count ≥ t`), oyuncu kimyası = min(3, Σ eşik bool) — CP-SAT'ta doğrudan modellenebilir.
- Amaç: `min Σ cost(c)·x` ; `cost` = kulüp kartı için `fırsat maliyeti` (untradeable=0, tradeable= p50×0.95 vergi — `VERIFY` vergi oranı), piyasa kartı için `p50` veya `min_bin`.
- Seçenekler: kilitli kartlar, hariç tutulan oyuncular, "yalnız kulübüm", "yalnız untradeable", "en fazla N satın alma", çoklu çözüm (k-best: önceki çözümü yasaklayan kesme), zaman limiti 5 sn.
### 3.2 Görevler
| ID | Pri | Görev | Kabul |
|---|---|---|---|
| UT-10 | P0 | Kısa vadeli: frontend beam çözücüyü Web Worker'a al + duplicate oyuncu + rating formülü düzelt (BUG-01..03) | — |
| UT-11 | P1 | `POST /ut/sbc/solve` (CP-SAT) — istek: challengeId veya ham gereksinim + havuz kaynağı (`market`, `club` [client'tan özet], karışık) + seçenekler; yanıt: kadro, maliyet kırılımı, kimya, rating, **neden bu kart** açıklaması | 20 gerçek FC27 SBC fixture'ında EA kurallarına uygun çözüm (manuel doğrulama) |
| UT-12 | P1 | Rating kombinasyon tablosu (`/ut/sbc/rating-combos?target=86`) + "en ucuz rating başına kart" tablosu (fiyat verisi gelince) | — |
| UT-13 | P2 | Topluluk çözümleri: kullanıcı çözümünü paylaşır (kart id listesi), oy + "benim kulübümle uyumu %" | — |
| UT-14 | P2 | SBC değer analizi: ödül değeri (oyuncu p50 / paket beklenen değeri — `VERIFY` paket olasılıkları EA'nın yayımladığı oranlardan) − maliyet → "Değer mi?" rozeti | — |
| UT-15 | P2 | Streamlined/item-score çözücü (mevcut, iyi): auth + rate limit + `sbc-score.json`'u `ut_rules`'a taşı + promo özel puanları | — |

Kulüp verisi gizliliği: kullanıcı kulübü sunucuya **kalıcı yazılmaz**; çözüm isteğinde istemci yalnız gerekli özeti (ea_resource_id, untradeable, adet) gönderir; istek loglanmaz.

---

## 4. EVOLUTIONS (UT-16..17) — mevcut capture'da var, hiç UI yok
| ID | Pri | Özellik | Detay |
|---|---|---|---|
| UT-16 | P1 | Evo listesi + detay: gereksinimler, seviye seviye kazanımlar (OVR/attr/PS/roles+), süre, maliyet; **"önce/sonra" kart çifti** (fut.gg'deki gibi) | `evolution` tablosu |
| UT-17 | P1 | **"Kulübümde bu evo'ya en uygun oyuncular"** + **"bu oyuncu için en iyi evo zinciri"** (evo sırası: ilk evo sonrası ikinci evo'ya uygunluk korunuyor mu — maxOvr/maxPace sınırları) — greedy değil DFS (zincir derinliği ≤ 4) | Sonuç: zincir sonunda tahmini kart statları + RoleFit artışı |

---

## 5. OBJECTIVES (UT-18)
- Global `objective_group` tablosundan liste (extension'sız).
- Kişisel ilerleme yalnız import ile (opsiyonel).
- Planlayıcı: aynı maçta birlikte tamamlanabilecek objective'leri grupla (ör. "Premier League oyuncusuyla 3 gol" + "Squad Battles'ta 2 maç") → **"tek kadroda hepsini karşılayan XI"** önerisi (CP-SAT, §3 motoru yeniden kullan; objective gereksinimleri = kadro kısıtları).
- XI gereksinimlerinin parse'ı (bugün yok — `UtObjectivesPage` kabul ediyor) — parser'a eklenir.
- `requirementOf` regex sıralaması hatası (win→PLAY_MATCHES vb.) düzeltilir (WEB `src/lib/capture.ts`).

---

## 6. OYUNCU SAYFASI (UT-19) & FİYAT ARAÇLARI (UT-20)
- Versiyon şeridi (tüm `ut_item` versiyonları), kart karşılaştırma (aynı oyuncunun iki versiyonu: stat farkı vurgulu).
- Fiyat grafiği (console/PC, 24s/7g/30g/sezon), "fiyat aralığı" (min/max EA limitleri `VERIFY`), son güncelleme zamanı + örnek sayısı (dürüstlük).
- "Kimya alternatifleri": aynı lig/ülke/kulüp + aynı mevki + daha ucuz/daha iyi.
- RoleFit: bu kart hangi FC27 rollerinde iyi (mevcut motor!) — UT tarafında rakiplerde olmayan değer.
- UT-20 Fiyat araçları: izleme listesi + fiyat alarmı (web push), "SBC yemi" listesi (rating başına en ucuz), trade vergisi hesaplayıcı (`VERIFY` vergi).

---

## 7. META / TIER (mevcut `/ut/meta` geliştirmesi)
- Tier list'i base kart yerine `ut_item` üzerinden; skor = rol bazlı RoleFit (oyuncu kendi rol/taktiğini seçer) + AcceleRATE + PS+ + boy/ayak; "metayı değil **senin taktiğini** sırala" modu.
- Meta formasyon payları kaynaklı değilse (`ut-meta.json` notu "tahmin") UI'da "editör görüşü" etiketi.
