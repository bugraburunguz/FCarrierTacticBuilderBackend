# 10 · YÜKLENEN DOSYALAR — ne işe yarar, ne çelişkili
Buğra'nın bu oturumda yüklediği dosyalar. Ajan bunları `docs/sources/fc27/` altına koyabilir (kaynak arşivi). **Kural:** rehber metni kod değildir. Değerler `ut-rules`'a `_source` notuyla girer.

## 1. REHBER MD'LERİ
| Dosya | Seed / kural olarak kullanım | Güven | Not |
|---|---|---|---|
| FC 27 Chemistry Explained | `ut-rules.chemistry` (04 §2) | Yüksek | FC26'ya göre: Icon ülke +2 → +1, Hero lig +2 → +1 |
| FC 27 Squad Rating Guide | `squadRating` formülü, yıldız tablosu, test vektörü | Orta | "18 oyuncu" iddiası oyun içinde doğrulanmalı (DN-05) |
| FC 27 SBC Score | `sbcScore` tabloları | Yüksek (EA pitch notes'a atıf) | Özel rarity tabloları kısmi |
| FC 27 Player Items | `quality`, holografik eşik, first owner, "UT'de work rate yok" | Yüksek | Base kartta rare/common ayrımı kalktı |
| FC 27 Positions Guide | 12 pozisyon, ikincil pozisyon sınırı yok, 10 dilde kısaltma tablosu | Yüksek | Kısaltma tablosu i18n için faydalı. Türkçe kısaltma tabloda yok; oyunun TR dil dosyasından (`data/loc/tur_tr.db`, LE ile) alınmalı, tahminle yazılmamalı |
| FC 27 Player Roles + FC 27 Roles (aynı içerik) | Rol açıklamaları, 4'lü trait etiketleri, Role+/++ | Orta | **Focus setleri eksik/yanlış** → FUTBIN tablosu kazanır (04 §4) |
| roles_focus_authoritative (FUTBIN) | `(pozisyon, rol) → focus` tek kaynak | Yüksek | Backend codec seed'iyle birebir |
| EA SPORTS FC 25 FC IQ Deep Dive | Rol felsefesi, açıklamalar | Düşük-orta | **FC25**; FC27'de 4 yeni rol (Wide Back, Inverted Wingback, Box Crasher, Ball-Playing Keeper) ve Versatile focus yok |
| FC 27 Tactics Guide | Defensive Approach aralıkları, build-up, preset, 5 özel taktik, taktik kodu, Tactical Summary 6 boyut | Yüksek | "11 karakter kod" yanlış → 12 |
| FC 27 Smart Tactics | Tactical Focus kaydırma tablosu, Quick Tactics (Team Press 20 sn / 100 sn şarj) | Orta | "Complete" focus'u hiçbir rolde yok |
| Best EA FC 27 Tactics (FUT.GG) | 29 formasyonun popülerliği (topluluk/pro), mevki başına en popüler rol | Yüksek (tarihli) | Otomatik çekme yok, admin güncellemesi (FEAT-U07) |
| The Best FC 27 Formation | Formasyon kategorileri (hücum/savunma/kontra/possession/orta) | Orta | "Position modifiers" bahsi eski (FC24'te kalktı) |
| FC 27 Attributes Guide | 34 attribute tanımı + **face stat ağırlıkları** (`faceStatWeights`) | Yüksek | Kariyer kartında face stat hesaplamak için |
| PlayStyles | 36 PlayStyle açıklaması, PS/PS+ farkı, "Keys" maddeleri | Orta | Bazı başlıklar dosyada kayıp (ilk girişler adsız). Low Driven "daha hızlı top" diyor; FC27 Shooting rehberi hız bonusunun kalktığını söylüyor → Shooting rehberi kazanır |
| FC 27 Shooting and Finishing | Şut tipleri, FC27 değişiklikleri | Yüksek | PlayStyle gezgini notları (FEAT-U14) |
| FC 27 Passing and Crossing | Pas tipleri, FC27'de manuel ara pas | Orta | Başlık hâlâ "FC 26" |
| FC 27 Stamina and Fatigue | UT'de fitness yok, Competitive vs Authentic | Yüksek | FEAT-C07 |
| FC 27 Evolutions Explained | Pathways, Chain Preview, PlayStyles Lab, kurallar (untradeable, süre, SBC'ye giremez) | Yüksek | FEAT-U04 |
| FC 27 Foundation SBC Guide | Foundations SBC listesi (gereksinim + ödül) | Yüksek | Admin CMS'e **ilk seed** olarak girilebilir (`source='ADMIN'`). "5 Leagues & 6 Nations" başlığı ile gereksinim (Leagues: 4) çelişiyor → `VERIFY` |
| FC 27 Foundations Objectives | Foundations objective grupları | Yüksek | Seed |
| FC 27 Milestones Objectives | Kalıcı milestone listesi + ödüller | Yüksek | Seed |
| FC 27 Weekly Objectives | S1 haftalık listesi + **9 sezonun tarihleri** | Yüksek | `contentCalendar.seasons` (04 §2) |
| FC 27 SP Guide | SP mantığı, Premium Pass | Yüksek | FEAT-U03 |
| FC 27 Squad Battles Rewards | Rank/ödül tablosu, Pazar 08:05 UK | Yüksek | FEAT-U15 |
| Draft Rewards (Online/Single) | Ödül tabloları, giriş 300 FP / 15.000 coin / token | Yüksek (S1) | FEAT-U15 |
| FC 27 Pack Odds | Olasılık matematiği | Yüksek | FEAT-U08 |
| FC 27 Player Picks | Pick türleri | Orta | FEAT-U12 |
| FC 27 Recommendations / Pro Tips / Starting Guide / How to Choose Best Players / UT Settings / Web App Live | Genel ipucu | Düşük (kural değil) | İçerik/yardım metni kaynağı. Web App: yeni hesaplar erken erişemiyor, login doğrulaması gerekli → extension rehberine not |
| FC 27 Youth Academy & Scouting (u7buy) | Akademi akışı, 6 growth profile, dynamic potential, senior vs youth scouting | Orta | **Coin satış reklamı içeriyor**, bağlantı verme. FEAT-C01 |
| fc_27_kariyer_uygulama_rehberi | Sinerji katman fikri | **Düşük** | Uydurma eşik/tetikleme mekanikleri (09 §4). Kural olarak kullanma |

## 2. LIVE EDITOR CSV'LERİ (`VERIFIED`, ayrıntı 07 §2)
| Dosya | Satır | Not |
|---|---|---|
| `players.csv` | 21.620 | Yalnız `playerid, overallrating, potential` → pozisyon/attribute yok, import'ta kariyer-üretimi oyuncular atlanıyor (LE-02). Tam export için 07 §4 |
| `teamplayerlinks.csv` | 23.360 (21.620 tekil oyuncu) | Her oyuncu tam 1 kulüpte; 1.740'ı ayrıca milli takımda |
| `teams.csv` | 841 | 16 kolon (rating'ler, prestij, `clubworth`, `youthdevelopment`, `rivalteam`, `gender`). 739 erkek, 102 kadın. "Fürth " sonda boşluk → trim |
| `leagueteamlinks.csv` | 841 | Her takımın tam 1 ligi |
| `leagues.csv` | 48 | Türkçe adlar. Özel id'ler: 76 "Erkekler - Diğer", 78 "Erkek Milli", 2136 "Kadın Milli Kup.", 382 "Serbest" (111592: 223 oyuncu, 131368: 145 kadın), 2000 yer tutucu (takımsız), 2028 "DÜNYA LİGİ" (112264: 5 oyuncu id 460002-460008, muhtemelen akademi bekleme takımı) |

Diğer gözlemler: OVR 47-91, POT 48-95, hiçbir oyuncuda POT < OVR yok. Kariyer-üretimi 10 oyuncu (460000-460009): 3'ü Crewe Alexandra'da (takım 121, `youthdevelopment` 10; muhtemelen Buğra'nın kulübü), 5'i 112264'te, 2'si serbest. Mbappé 231747 (91/94, RMA 243 + FRA 1335), Haaland 239085 (91/93, MCI 10 + NOR 1352) doğrulandı.

**Ajan için:** Bu CSV'leri `src/test/resources/le-export/fc27-2026-10/` altına fixture olarak koy (players.csv küçük, sorun yok). LE-01/LE-02 testleri bunlarla yazılsın: "Erkekler - Diğer" kulüpleri katalogdan dışlanır, "Serbest" serbest oyuncu olarak işaretlenir, Crewe kullanıcı takımı `context.userTeamId` ile seçilir.

## 3. FC 27 LE v27.1.0 ZIP
İçerik: `Launcher.exe`, `FCLiveEditor.DLL`, `FakeEAACLauncher/` (anticheat'i atlatan sahte launcher), `lua/` (DOC.MD + v1/v2 kütüphaneleri + 4 örnek script), `loc/eng_us/localize.json`, `legacy_filename_hash_list.csv`.
- **Repoya koyma.** Binary'ler ve anticheat atlatıcı repo/CI'a girmemeli. Yalnız `lua/libs/v2` API referansı için `docs/sources/le-27.1.0/` altına **metin dosyaları** (DOC.MD, temp_doc_draft.md) alınabilir; lisansı `VERIFY` (xAranaktu).
- Kullanıcıya uyarı metni (import sayfasında): "Live Editor yalnız çevrimdışı kariyerde kullanılmalı; çevrimiçi modlarda EA anticheat ban riski var."
