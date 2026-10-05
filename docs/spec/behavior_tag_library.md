# DAVRANIŞ TAG KÜTÜPHANESİ — seed v1
Kullanıcı rol jargonu bilmeden "oyuncunun ne yapmasını istediğini" seçer; motor bunu
weapon attribute'larına + FC 27 roluna + atak paternine çevirir. Çelişen tag'ler client'ta kilitlenir.

**Sütunlar:** Davranış (kullanıcı görür) · Weapon attr (RoleFit + scout filtresi) · Rol eğilimi (oy) ·
Çelişir (seçilemez olur) · NLG (sahada ne yapar) · AcceleRATE ima.

**Motor mantığı:** seçilen tag'ler → (1) weapon'lar toplanır = scout filtresi + RoleFit ağırlık yükseltmesi,
(2) rol-eğilimi oyları en yüksek FC 27 rol+focus'u belirler, (3) çelişenler kilitlenir, (4) NLG cümleleri
atak paterni açıklamasını kurar, (5) AcceleRATE imaları oyuncu seçiminde arketip filtresi olur.
"—" = modifier (tek başına rol belirlemez, weapon/ima ekler).

---

## GK (Kaleci)
| Davranış | Weapon | Rol eğilimi | Çelişir | NLG | AcceleRATE |
|---|---|---|---|---|---|
| Çizgide dursun, kurtarsın | Reflexes, GK Positioning, Diving | Goalkeeper | Arkayı süpürsün | Kalede kalır, şutları kurtarır | — |
| Arkayı süpürsün (yüksek hat) | GK Positioning, Speed, Reflexes | Sweeper Keeper | Çizgide dursun | Ceza sahası dışına çıkıp arkayı temizler | — |
| Ayakla oyun kursun | Kicking, Composure, Short Passing | Ball-Playing Keeper | Uzun açsın (direkt) | Kısa pasla kuruluşa katılır | — |
| Uzun açsın (direkt) | Kicking (uzun) | (Sweeper/GK + direkt) | Ayakla oyun kursun | Uzun topla forvete direkt çıkar | — |

## RB / LB (Bek)
| Davranış | Weapon | Rol eğilimi | Çelişir | NLG | AcceleRATE |
|---|---|---|---|---|---|
| Sürekli bindirsin (overlap) | Stamina, Acceleration, Crossing | Attacking Wingback (Attack) | Sadece savunsun · İçeri katsın | Çizgiden overlap yapıp arka direğe orta açar | Explosive |
| Erken orta açsın | Crossing, Curve | Attacking Wingback (Support) | Sadece savunsun | Dar alandan erken/kavisli orta | Explosive |
| Dengeli: hem bindir hem dön | Stamina, Def Awareness, Crossing | Wingback (Balanced) | Sadece savunsun | Ölçülü çıkar, topu kaybedince döner | Explosive/Ctrl |
| İçeri katsın (inverted) | Short Passing, Composure, Ball Control | Inverted Wingback | Sürekli bindirsin | Orta sahaya girip ekstra pivot olur | Controlled |
| Kutuya girsin (arka direk kafa) | Jumping, Heading, Stamina | AWB (Attack) + hava | Sadece savunsun | Karşı ortalarda arka direğe yükselir | Explosive |
| Sadece savunsun (geri kalsın) | Def Awareness, Standing Tackle, Strength | Fullback (Defend) | Bindirsin/overlap | Hatta kalır, rest-defense'i korur | Lengthy/Ctrl |

## CB (Stoper)
| Davranış | Weapon | Rol eğilimi | Çelişir | NLG | AcceleRATE |
|---|---|---|---|---|---|
| Sadece savunsun (pozisyon tut) | Def Awareness, Standing Tackle, Strength | Defender (Defend) | Öne çıkıp bassın | Pozisyonunu korur, lunge atmaz | Lengthy/Ctrl |
| Öne çıkıp bassın (agresif) | Aggression, Standing Tackle, Acceleration | Stopper (Aggressive) | Sadece savunsun | Rakibe çıkıp top kazanır (riskli) | Controlled |
| Topu ilerletsin (oyun kursun) | Short/Long Passing, Composure, Vision | Ball-Playing Defender | — | Arkadan pasla atak başlatır | Lengthy/Ctrl |
| Hava hakimiyeti | Heading, Jumping, Strength | (aerial CB) | — | Ortaları ve duran topları kafayla temizler | — |
| Hızlı kapatsın (geniş alan) | Acceleration, Sprint Speed, Def Awareness | Wide Back / hızlı CB | — | Arkaya atılan toplarda hız avantajı | Explosive/Ctrl |

## CDM (Çapa)
| Davranış | Weapon | Rol eğilimi | Çelişir | NLG | AcceleRATE |
|---|---|---|---|---|---|
| Önü kapatsın (süpürücü) | Def Awareness, Interceptions, Positioning | Holding (Defend) | Kutuya dalsın | Dörtlünün önünde pas yollarını keser | Ctrl/Lengthy |
| Yıkıcı: top kazansın, pres | Interceptions, Aggression, Stamina | Holding (Ball-Winning) | — | Agresif müdahaleyle topu söker | Ctrl/Explosive |
| Geriden oyun kursun | Vision, Long Passing, Composure | Deep-Lying Playmaker | Kutuya dalsın | Derinden tempo kurar, diyagonal açar | Controlled |
| Stopere düşsün (3'lü kursun) | Def Awareness, Positioning | Centre Half | Kutuya dalsın | Topluyken CB'ler arasına düşer | Lengthy/Ctrl |
| Kutuya dalsın (geç gelen gol) | Positioning, Long Shots, Shot Power | Box Crasher | Önü kapatsın · Stopere düşsün | Geç gelip kutuya dalar, uzaktan vurur | Explosive/Ctrl |

## CM (Orta saha)
| Davranış | Weapon | Rol eğilimi | Çelişir | NLG | AcceleRATE |
|---|---|---|---|---|---|
| Uçtan uca koşsun (motor) | Stamina, Reactions, Strength | Box-to-Box | Sadece oyun kursun | İki ceza sahası arası sürekli çalışır | Ctrl/Explosive |
| Yıkıcı/ball-winner | Interceptions, Aggression, Standing Tackle | Box-to-Box (Ball-Winning) | — | Rakip kuruluşunu bozar, topu söker | Ctrl |
| Oyun kursun (ileri) | Vision, Short Passing, Dribbling | Playmaker (Attack/Roaming) | Sadece savunsun | Boşluk arayıp ara pas dağıtır | Explosive/Ctrl |
| Derinden kursun (2. pivot) | Vision, Long Passing, Composure | Deep-Lying Playmaker | Kutuya dalsın | Geriden ilerletir, pivotu korur | Controlled |
| Geç kutuya dalsın + uzaktan şut | Positioning, Long Shots, Shot Power | Box-to-Box (atağa) | Sadece savunsun | Lampard gibi geç dalış + füze | Explosive/Ctrl |
| Kenara kayıp kombine olsun | Dribbling, Crossing, Agility | Half Winger | — | Kanada açılıp üçgen kurar | Explosive |

## CAM (On numara)
| Davranış | Weapon | Rol eğilimi | Çelişir | NLG | AcceleRATE |
|---|---|---|---|---|---|
| Oyunu kursun (dahi 10, merkez) | Vision, Short Passing, Ball Control, Composure | Classic 10 / Playmaker | Forvetin ötesine koşsun | Savunmayı yaran ara pasları dağıtır | Explosive/Ctrl |
| Forvetin ötesine koşsun (gol) | Positioning, Finishing, Acceleration | Shadow Striker | Oyunu kursun | Forvet önüne fırlayıp bitirir | Explosive |
| Uzaktan vursun | Long Shots, Shot Power | — (modifier) | — | Ceza yayından füze çeker | — |
| Kenara kaysın (geniş yaratıcılık) | Dribbling, Crossing, Vision | Half Winger | — | Kanada açılıp içeriden kurar | Explosive |
| Dribbling ile fark yaratsın | Dribbling, Agility, Balance | — (modifier) | — | Bireysel çalımla savunma kırar | Explosive |

## RM / LM (Kanat orta saha)
| Davranış | Weapon | Rol eğilimi | Çelişir | NLG | AcceleRATE |
|---|---|---|---|---|---|
| Çizgide kalsın, orta açsın | Crossing, Curve, Stamina | Winger | İçe kessin | Dışarıda kalıp orta yağdırır | Explosive |
| İçe kessin, bitirsin | Finishing, Agility, Dribbling | Inside Forward | Çizgide kalsın | İçe kesip şuta girer | Explosive |
| İçeriden kursun | Vision, Short Passing, Dribbling | Wide Playmaker | Sadece bitir | İçeri kıvrılıp oyun kurar | Explosive/Ctrl |
| İki yönlü (savunmaya destek) | Stamina, Def Awareness, St. Tackle | Wide Midfielder | Sürekli ileride kalsın | Bekine yardım eder, kanadı kapatır | Ctrl/Explosive |

## RW / LW (Kanat)
| Davranış | Weapon | Rol eğilimi | Çelişir | NLG | AcceleRATE |
|---|---|---|---|---|---|
| Orta açsın (çizgiye in) | Crossing, Curve | Winger | İçe kessin | Çizgiye inip arka direğe orta açar | Explosive |
| İçe kessin, içeriden tehdit | Agility, Dribbling | Inside Forward | Orta açsın | İçe kesip şut/ara pasa girer | Explosive |
| Plase/falso golü | Finesse Shot (PS), Curve, Composure | Inside Forward | — | İçe kesip köşeye kavisli plase | Explosive |
| Uzaktan vursun | Long Shots, Shot Power | — (modifier) | — | Yaydan sert şut | — |
| Dribbling ile geçsin | Dribbling, Agility, Balance | — (modifier) | — | Bireysel çalımla bekini geçer | Explosive |
| Kutuda kafa tehdidi (fizikli) | Heading, Jumping, Strength | fizikli Inside Forward | — | Kutuda ikinci hava tehdidi olur | Lengthy/Ctrl |
| İçeriden oyun kursun | Vision, Short Passing | Wide Playmaker | Sadece bitir | İçeri kıvrılıp yaratıcılık yapar | Explosive/Ctrl |
| 1'e 1 bitirici (sadece gol) | Finishing, Composure, Positioning | Inside Forward (Attack) | İçeriden oyun kursun | Sadece kutuda bitirmeye odaklanır | Explosive |
| Geri gelip savunsun | Def Awareness, Stamina, St. Tackle | Wide Midfielder | Sürekli ileride kalsın | Bekine yardım, kanadı kapatır | Ctrl/Explosive |
| Sürekli ileride kalsın | Positioning, Acceleration | Inside Forward (Attack) | Geri gelip savunsun | Hiç geri gelmez, kontra ucu olur | Explosive |

## ST (Santrfor)
| Davranış | Weapon | Rol eğilimi | Çelişir | NLG | AcceleRATE |
|---|---|---|---|---|---|
| Arkaya koşsun (derinlik) | Acceleration, Positioning, Finishing | Advanced Forward | Derine gelip bağlasın | Son savunma arkasına sürekli sarkar | Explosive |
| Derine gelip bağlasın (F9) | Vision, Ball Control, Short Passing, Dribbling | False 9 | Arkaya koşsun · Hava kulesi dursun | Geriye gelip kombine olur, alan açar | Explosive/Ctrl |
| İstasyon/hava kulesi dursun | Strength, Heading, Jumping | Target Forward | Derine gelip bağlasın | Sırtı dönük tutar, ortalara yükselir | Lengthy/Ctrl |
| Kutuda bekleyen bitirici | Finishing, Positioning, Composure | Poacher | Derine gelip bağlasın | Kutuda pusuda bekler, bitirir | Ctrl/Explosive |
| Uzaktan + plase vursun | Long Shots, Finesse Shot (PS), Shot Power | (AF + şut) | — | Kutu dışından da tehdit eder | — |
| Baskı yapsın (ilk savunan) | Stamina, Aggression, Press Proven (PS) | (presli forvet) | — | Savunmaya ilk baskıyı o kurar | Explosive |

---

## Çatışma (mutual-exclusion) özeti — client'ta kilitlenecek
- Bek: *Sürekli bindirsin* ⇎ *Sadece savunsun* ⇎ *İçeri katsın*
- CB: *Sadece savunsun* ⇎ *Öne çıkıp bassın*
- CDM: *Kutuya dalsın* ⇎ *Önü kapatsın* / *Stopere düşsün* / *Geriden kursun*
- CM: *Sadece oyun kursun* ⇎ *Sadece savunsun* / *geç kutuya dalsın*
- CAM: *Oyunu kursun* ⇎ *Forvetin ötesine koşsun*
- Kanat: *Orta açsın* ⇎ *İçe kessin* · *İçeriden oyun kursun* ⇎ *1'e 1 bitirici* · *Geri gelip savunsun* ⇎ *Sürekli ileride kalsın*
- ST: *Arkaya koşsun* ⇎ *Derine gelip bağlasın* ⇎ *Hava kulesi dursun*

## Notlar / açık uçlar
- "modifier" tag'ler rol belirlemez, sadece weapon + NLG ekler (ör. "uzaktan vursun" her ileri pozisyonda).
- PlayStyle weapon'ları (Finesse Shot, Press Proven) ⭐ ps olarak işaretli — PS+ varsa weapon eşiğini −4 telafi eder (D kararı).
- Pozisyon başına makul tag üst sınırı + çelişki kilidi UX'te; sayı MVP testinde ayarlanır.
- Senin ekleyeceğin/çıkaracağın tag'ler için boş: ileride bu tabloya satır ekle/sil.
