# FCAREER — İNCELEME PAKETİ · 00 INDEX (önce bunu oku)
Tarih: 2026-10-09 · Kapsam: `FCarrierTacticBuilderBackend` + `FCarrierTacticBuilderWeb` (main, zip) + 6 spec md.
Hedef okuyucu: kod ajanı (Claude Code vb.). Her doküman **kendi başına okunabilir**; görevler ID'lidir.

## 0. ETİKET SÖZLÜĞÜ (bütün dosyalarda aynı)
| Etiket | Anlam | Ajan ne yapar |
|---|---|---|
| `VERIFIED` | Kodda/kaynakta doğrulandı (dosya:satır verildi) | Uygula |
| `VERIFY` | Bilgi eğitim verisinden/önceki sürümden; FC27 için **teyit edilmedi** | Uygulamadan önce oyun içinden ya da resmi pitch notes'tan doğrula; değeri **config'e** koy, koda gömme |
| `DECISION` | Karar maddesi — **2026-10-09'da hepsi onaylandı**, kesin hâli `08_DECISIONS_LOG.md` | 08'deki hâli uygula |
| `LEGAL-RISK` | EA ToS / telif / kişilik hakkı riski | Buğra'nın açık onayı olmadan uygulama |
| `P0/P1/P2` | Öncelik: P0 = güvenlik/para/veri bozulması, P1 = doğruluk/UX kırığı, P2 = iyileştirme | P0 → P1 → P2 sırası |

Görev ID önekleri: `SEC` güvenlik · `BUG` hata · `DS` tasarım sistemi · `UX` ekran · `DATA` veri platformu · `UT` Ultimate Team özelliği · `CAR` kariyer özelliği · `SIM` maç simülasyon oyunu · `OPS` altyapı/CI.

## 1. DOSYALAR
| # | Dosya | İçerik | Kim için |
|---|---|---|---|
| 01 | `01_AUDIT_CURRENT_STATE.md` | Ne yapılmış, spec'e göre ne eksik, doğrulanmış bug'lar (dosya:satır), P0/P1 düzeltme listesi | Ajan — **ilk iş** |
| 02 | `02_DESIGN_SYSTEM_REDESIGN.md` | "Yapay zekâ görünümü"nün sebepleri, yeni görsel dil, token'lar, bileşenler, ekran ekran yeniden tasarım | Ajan + Buğra |
| 03 | `03_UT_DATA_PLATFORM.md` | Extension'sız SBC/objective/evo/kart/fiyat/görsel verisi: kaynaklar, hukuki risk matrisi, şema, pipeline | Ajan (kararlar 08'de) |
| 04 | `04_UT_FEATURES_SPEC.md` | Squad builder v2, SBC çözücü v2, Evolutions, Objectives, oyuncu sayfası, fiyat araçları | Ajan |
| 05 | `05_FEATURE_BACKLOG.md` | Kariyer + UT + platform için eklenebilecek özellikler (öncelikli) | Buğra (seçer) → Ajan |
| 06 | `06_MATCH_SIM_GAME_SPEC.md` | OSM × FM arası online 2D taktik-sim oyunu: motor, taktik, canlı müdahale, online mimari, MVP fazları | Ajan + Buğra |
| 07 | `07_CLAUDE_MD_FCAREER.md` | Backend repo'daki **yanlış** (Azerlottery) CLAUDE.md yerine konacak FCareer versiyonu | Ajan (repo köküne kopyala) |
| 08 | `08_DECISIONS_LOG.md` | **Onaylı kararlar (D-01..D-19)** — diğer dosyalardaki `DECISION` maddelerinin kesin hâli; çelişkide bu geçerli | Ajan — 01'den önce oku |
| 10 | `10_TACTIC_CODE_DIAGNOSIS.md` | Taktik kodunda rol/focus'un oyuna geçmemesi: teşhis, test protokolü, TAC görevleri | Ajan + Buğra (test verisi) |
| 13 | `13_CAREER_MODE_EXPERIENCE.md` | **Kariyer modu "Menajer Masası"**: ekranlar, konsol/PC senkron yolları, snapshot modeli, öneri motoru, CAR-20..33 | Ajan + Buğra |
| 11 | `11_EASYSBC_REFERENCE.md` | EasySBC sayfa sayfa inceleme: UT kartının nasıl çizildiği, veri modeli, SBC/Galeri/Evo/Kulübüm/Oyuncu ekran kalıpları, yeni görevler | Ajan + Buğra |
| 12 | `12_EXTENSION_REVIEW.md` | Extension denetimi: çalışmayan gönder butonu, veri kaybı riskleri, dosyasız tek tık senkron, katkı modu | Ajan |
| — | `patches/backend/...` | Taktik kodu regresyon testleri + 64 test vektörü (isteğe bağlı; ajan repoya kopyalar) | Ajan |
| 09 | `09_DATA_POLICY_DRAFT.md` | Veri politikası taslağı → `docs/DATA_POLICY.md` | Ajan kopyalar, Buğra yer tutucuları doldurur |

## 2. TEK PARAGRAF ÖZET
Ürünün **motoru güçlü** (RoleFit/IntentFit/SquadFit + Hungarian + rules engine + NLG + EA taktik kodu codec'i + Live Editor import; spec'teki T1–T6 testleri var). Zayıf yanlar: (1) **güvenlik/para** — herkes ücretsiz PREMIUM alabiliyor, iki uç ücretli işi bedava veriyor, bir public uç başka kullanıcının kariyer verisini sızdırıyor; (2) **UT katmanı yarım** — tüm UT verisi kullanıcının extension yakalamasına bağlı, hiçbir global UT kataloğu (özel kart, SBC listesi, fiyat, promo) yok, kimya/squad-rating kuralları tutarsız ve test edilmemiş; (3) **tasarım** — Tailwind palet override'ı + teal-on-dark + emoji rozet + her yerde aynı kart + açıklama paragrafları = jenerik "AI dashboard" görünümü; iki ayrı FutCard ve iki ayrı stil sistemi var; (4) backend repo'daki `CLAUDE.md` **başka bir projeyi** (Azerlottery, `com.misli`) tarif ediyor — ajanı yanlış yönlendirir.

## 3. ÖNERİLEN YOL HARİTASI (sıralı)
```
FAZ A  (1 hafta)   Güvenlik + doğruluk P0          → 01 §5 (SEC-01..05, BUG-01..06), 07 (CLAUDE.md değiştir)
FAZ B  (2 hafta)   Tasarım sistemi v2 temeli        → 02 §4-§6 (DS-01..10), tek UtCard bileşeni
FAZ C  (2-3 hafta) UT veri platformu v1             → 03 (DATA-01..13) — kararlar 08 D-03..D-05'te onaylı
FAZ D′ (3-4 hafta) Kariyer "Menajer Masası"       → 13 (önce CAR-29, CAR-02, CAR-20, CAR-21, CAR-23) — Faz B üstüne, D ile paralel
FAZ D  (3 hafta)   UT özellikleri v2                → 04 (UT-01..20) + 02 ekran yeniden tasarımları
FAZ E  (sürekli)   Backlog                          → 05
FAZ F  (ayrı ürün hattı) Maç simülasyon oyunu        → 06 (SIM fazları; Faz A tamamlanmadan başlama)
```
Gate kuralı: Bir faza geçmeden önce önceki fazın P0 maddeleri yeşil + testli olmalı.

## 3.1 KURULUM (ajan ilk iş)
Önce `08_DECISIONS_LOG.md`'yi oku. Bu paketi **iki repoya da** `docs/review/` altına kopyala (07'deki yeni CLAUDE.md'ler buraya referans veriyor). Backend'de 07'deki içerikle `CLAUDE.md`'yi değiştir, web köküne yeni `CLAUDE.md` ekle. Görev durumlarını bu md'lerin içinde güncelle.

## 4. AJAN İÇİN GENEL KURALLAR
1. Backend house-style: `docs/backend-standard.md` (Azerlottery'ye özel kısımlar HARİÇ — bkz. 07). Constructor injection, manuel mapper, `@Transactional` serviste, magic number yok, config'e taşı.
2. Davranış değiştiren her değişikliği commit mesajında **açıkça** yaz (`fix!:` / `BEHAVIOR CHANGE:`).
3. `VERIFY` etiketli hiçbir oyun kuralını (kimya eşiği, rating formülü, fiyat aralığı, refresh saati) koda sabit gömme → versiyonlu seed/config (`seed/ut-rules.fc27.json`) + birim testi config'ten beslenir.
4. `LEGAL-RISK` maddeleri 08'de karara bağlandı: EA hesabıyla bot (S6), fan sitesi scrape (S7), gerçek isim/EA reytingiyle online oyun (L3/L4) **reddedildi — yapma**. İzinli olanlar 08 D-03/D-04/D-14'te.
5. Frontend: yeni bileşen yazmadan önce 02'deki token ve bileşen listesine bak; `emerald-*`/`slate-*` Tailwind sınıflarını yeni kodda kullanma, semantik token kullan.
6. Her görev bitince: test + `npm run build` / `mvn test` yeşil, ilgili md'deki görev satırına `DONE (commit-sha)` yaz.

## 5. BU İNCELEMENİN SINIRLARI (dürüstlük notu)
- Web araması bu oturumda kapalıydı; Chrome eklentisine erişilemedi. Rakip analizi fut.gg/futbin sayfalarının metin çıktısıyla ve önceki bilgiyle yapıldı. EA FC27'ye özgü oyun kuralları bu yüzden `VERIFY` etiketli.
- Extension'ın kendi kodu zip'lerde yok; extension'a dair her şey backend'in `UtCaptureService` tarafından çıkarıldı. `ut_capture_mapping.md` repoda referans veriliyor ama yok.
- Kod çalıştırılmadı (build/test koşulmadı); bulgular statik okuma.
