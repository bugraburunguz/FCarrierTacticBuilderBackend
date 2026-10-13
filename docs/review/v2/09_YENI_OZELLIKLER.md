# 09 · YENİ ÖZELLİKLER — yüklenen rehberlerden çıkanlar
Puan: **Değer** 1-5 (kullanıcıya fayda), **Efor** S/M/L, **Bağımlılık** (önce bitmesi gereken). Buğra seçer, ajan v1 `05_FEATURE_BACKLOG.md` ile birleştirip D-13 sırasıyla (değer↓, efor↑) işler. v1'de olanlarla çakışanlar "(v1: ID)" ile işaretli.

## 1. ULTIMATE TEAM
| ID | Özellik | Ne yapar | Kaynak rehber | Değer | Efor | Bağımlılık |
|---|---|---|---|---|---|---|
| FEAT-U01 | **Streamlined SBC planlayıcı** | Kulübündeki kartlarla hedef skora en ucuz kombinasyon, kopyalar önce, untradeable önce, kısmi gönderim takibi | SBC Score | 5 | M | SBC-01, CARD-05 |
| FEAT-U02 | **İçerik takvimi + sayaçlar** | Sonraki günlük yenileme (TR saatiyle, BST/GMT'ye göre 20:00/21:00), haftalık objective (Perşembe), sezon bitişi, Squad Battles ödülü (Pazar 08:05 UK) | Weekly Objectives, Squad Battles, SP | 5 | S | RULE-05 |
| FEAT-U03 | **Objective + SP takipçisi** | Weekly/Seasonal/Milestone/Foundations grupları, SP toplamı, sezon pass kademesine kalan, "web app'te yapılabilir" etiketi | Weekly Obj., Milestones, Foundations, SP | 4 | M | Katkı hattı objective tanımları |
| FEAT-U04 | **Evolution planlayıcı** (v1: UT-16) | Evo gereksinimine uyan kulüp oyuncuları, önce/sonra kart, zincir önizleme (FC27 Chain Preview), pathway ağacı, "bu oyuncu için en iyi evo" | Evolutions | 5 | M | Evo tanımlarının normalize gereksinimleri (BE-DATA §3) |
| FEAT-U05 | Chem style danışmanı | Kimya seviyesi başına (1/2/3 elmas) attribute boost'u; oyuncu + rol için en iyi stil | Chemistry, Attributes | 4 | S | Yakalanan `styleAttribMods` (BE-DATA-02) |
| FEAT-U06 | **5 özel taktik yöneticisi + Smart Tactics önizleme** | Oyundaki gibi 5 taktik slotu; her biri formasyon + build-up + approach + line height + 11 rol/focus + kod. "Tactical Focus: Hücum/Savunma" seçince hangi oyuncunun focus'u neye kayar önizlemesi (04 `tacticalFocus`) | Tactics, Smart Tactics | 4 | M | RULE-06, BE-DATA §6 `user_tactic` |
| FEAT-U07 | Taktik kodu kütüphanesi | Formasyon başına meta taktikler (FUT.GG popülerlik yüzdeleriyle, admin güncel tutar), kullanıcı paylaşımı, tek tık kopyala | Best Tactics (FUT.GG) | 4 | S | BE-BUG-10 |
| FEAT-U08 | Paket olasılığı hesaplayıcı | "%2,9 şanslı paketi 34 kez açınca en az bir = %65; %90 için 79 paket" (1-(1-p)^n); paket değeri beklentisi uyarısı | Pack Odds | 3 | S | — |
| FEAT-U09 | Transfer kâr hesaplayıcı | %5 vergi, alış/satış, fiyat adımları (EA aralık tablosu `VERIFY`), net kâr | Recommendations, Starting Guide | 3 | S | `ut-rules.priceRanges` |
| FEAT-U10 | SBC rating kombinasyonları | Hedef team rating'e (örn. 84) en ucuz rating dağılımı (11 oyuncu, correction factor'lü formül) | Squad Rating | 5 | S | RULE-02 |
| FEAT-U11 | FUT Gallery takipçisi | Galeri seviyesi, set ilerlemesi, holografik (≤87 normal, ≥88 pristine), galeri puanı (SBC skoruyla aynı tablo) | Player Items, Milestones, SBC Score | 3 | M | Gallery tanımları capture |
| FEAT-U12 | Player Pick yardımcısı | 1/3, 1/5 pick ekranındaki adaylardan kadrona en çok Δkimya/ΔOVR katan | Player Picks | 3 | S | Kadro kurucu delta worker'ı |
| FEAT-U13 | İzleme listesi + fiyat alarmı | Kart izle, fiyat eşiği → push | Recommendations | 4 | M | BE-BUG-01..03 düzeltilmiş fiyat |
| FEAT-U14 | PlayStyle gezgini | 36 PlayStyle (Acrobatic … Whipped Pass), PS/PS+ farkı, FC27 değişiklikleri (Finesse attribute bazlı, Low Driven hız bonusu kalktı), filtre: "Rapid+ ve Finesse+ olan ST'ler" | PlayStyles, Shooting, Attributes | 4 | S | Katalogda PS alanları |
| FEAT-U15 | Draft / Squad Battles / Rivals ödül referansı | Galibiyet/rank başına ödüller, giriş ücreti, "beklenen değer" notu | Draft Rewards, Squad Battles | 2 | S | Admin CMS |
| FEAT-U16 | Günlük kontrol listesi | "Ödülleri al, yeni SBC'ler (2 değerli), objective bitişi yaklaşıyor, izlediğin kart %10 düştü" | Recommendations | 4 | S | FEAT-U02, U03 |
| FEAT-U17 | Paylaşılabilir kadro görseli | Saha + kartlar PNG/OG görseli, taktik kodu | — | 3 | M | CARD-04 |

## 2. KARİYER
| ID | Özellik | Ne yapar | Kaynak | Değer | Efor | Bağımlılık |
|---|---|---|---|---|---|---|
| FEAT-C01 | **Genç akademi asistanı** | Scout görev kaydı (ülke, süre, scout yıldızı), gelen adayları karşılaştır (OVR, gerçek POT LE'den, pozisyon, growth profile), "terfi / akademide tut / kirala / bırak" önerisi kadro derinliğine göre | Youth Academy | 5 | M | LE `career_youthplayers` (07) |
| FEAT-C02 | **Kontrat ve maaş planlayıcı** | Bitiş yaklaşan kontratlar, maaş bütçesi %'si, yenile/sat önerisi, ön-kontratlar | — | 5 | M | LE kontrat tabloları |
| FEAT-C03 | Dynamic potential izleyici | Snapshot'lar arası POT değişimi + performans (sezon istatistiği) ilişkisi, "potansiyeli düşüyor" uyarısı | Youth Academy (Dynamic Potential, 6 Growth Profile) | 4 | M | LE PROBE (growth profile kolonu), CAR-02 |
| FEAT-C04 | Sezon istatistik panosu | Gol/asist/ort. puan/clean sheet, oyuncu ve takım | — | 4 | S | `GetPlayersStats` (07) |
| FEAT-C05 | Rol aşinalığı (Role+/++) ile kadro | LE `role1..5` (+100 = ++) → oyuncunun ++ olduğu rolde öneri, fit motoruna bonus | Player Roles | 4 | S | LE export v2 |
| FEAT-C06 | **Taktik özeti (oyun içi gibi)** | 6 boyut: Hücum/Savunma, Genişlik, Dayanıklılık, Uzunluk, Build-Up. 3 güçlü + 3 geliştirilecek yön | Tactics Guide (Tactical Summary) | 4 | M | Fit motoru |
| FEAT-C07 | Rotasyon / yorgunluk planlayıcı | Authentic modda yorgunluk; maç takvimine göre rotasyon, 70. dk değişiklik önerisi, Relentless(+) oyuncular | Stamina | 3 | M | Fikstür (LE sonraki sürüm) |
| FEAT-C08 | Oyun tarihine göre yaş + sezon geçişi | Tüm yaş filtreleri `current_game_date`'e göre (BE-BUG-18), sezon sonu otomatik snapshot | — | 4 | S | LE context |
| FEAT-C09 | Transfer yasağı / bütçe simülatörü genişletme | LE `TransferBanManager`, `GetUserTransferBudget` probe | — | 2 | S | LE probe |

## 3. PLATFORM
| ID | Özellik | Değer | Efor |
|---|---|---|---|
| FEAT-P01 | PWA + çevrimdışı (kadro, kulüp, son SBC listesi) (v1: CAR-33) | 4 | M |
| FEAT-P02 | Bildirim merkezi (web push + Discord) (SBC-09) | 4 | M |
| FEAT-P03 | İngilizce arayüz (i18n altyapısı zaten metin yoğun) | 3 | M |
| FEAT-P04 | Hesap silme + veri export (BE-ARCH-07, yasal) | 4 | S |
| FEAT-P05 | Sentry + ürün analitiği (D-13, PLT-08) | 4 | S |

## 4. UYARI — `fc_27_kariyer_uygulama_rehberi.md` içindeki mekanikler
Bu dosyadaki "eşik → PlayStyle tetiklenir" kuralları (örn. "85+ Long Passing + 83+ Vision → Long Ball Pass+ tetiklenir", "85+ Finishing + 82+ Curve → Finesse Shot+") **oyunda böyle bir mekanik yok**. PlayStyle'lar karta atanır, statla tetiklenmez. "$83+$ Vision kritik eşik", "Min. 80+ fark hissedilir" gibi sayılar kaynaksız. Ayrıca "Sweeper Keeper (Defend)" focus'u yok (Balanced/Build-Up), "Wingback (Attack)" yok (Attacking Wingback'in Attack'ı var). **Bu değerleri fit motoruna ya da UI'a kural olarak koyma.** Kullanılabilir kısım: 4 katmanlı sinerji fikri (stat+PS, rol+PS, çapraz rol, taktik uyum) ve mevki bazlı "kritik stat" listeleri. Bunlar zaten fit motorunun tasarımına uyuyor (`fit_engine_spec.md`). Eşikler config'te, `verified:false`.
