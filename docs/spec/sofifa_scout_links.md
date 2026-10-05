# SOFIFA SCOUT LİNKLERİ — Çözüm & Base (FC 27 / 4-3-3 Holding)
Buğra'nın oluşturduğu filtre URL'leri + parametre deşifresi. "Yetenek avı" dendiğinde bu base'den
eşikler güncellenip link yeniden üretilir.

## 1. PARAMETRE SÖZLÜĞÜ (deşifre)
**Genel:** `type=all` · `col=oa` (OVR'a göre sırala) · `sort=asc` (düşük OVR önce = en ucuz/ulaşılabilir — akıllıca).

**Pozisyon** `pn[]=N` (SoFIFA mevki ızgarası):
GK=0 · RB=3 · CB=5 · LB=7 · CDM=10 · CM=14 · RW=23 · ST=25 · LW=27
(RM≈16 · LM≈12 · CAM≈18 · CF≈21 — gerekirse teyit.) GK'da `pn` yok, `g*` statları kullanılır.

**Attribute kodları** (`...l` = min / low, `...h` = max koyulabilir):
`acl`=Acceleration · `spl`=Sprint Speed · `agl`=Agility · `bll`=Ball Control · `drl`=Dribbling ·
`cml`=Composure · `vil`=Vision · `shl`=Short Passing · `lol`=Long Passing · `crl`=Crossing ·
`fil`=Finishing · `sol`=Shot Power · `mal`=Marking/Def Awareness · `inl`=Interceptions ·
`sal`=Standing Tackle · `srl`=Strength · `hel`=Heading · `stl`=Stamina.
**GK:** `grl`=Reflexes · `gpl`=GK Positioning · `gcl`=Kicking · (`gdl`=Diving · `ghl`=Handling).

**PlayStyle** `ps1[]=BIT` (2'nin kuvveti, her değer bir PS):
256=Incisive Pass · 512=Pinged Pass · 1024=Long Ball Pass · 2048=Tiki Taka ·
16384=Jockey · 65536=Intercept · 131072=Anticipate · 1048576=Technical.
(Diğerleri gerektiğinde eklenir; aynı mantık.)

**AcceleRATE** `at[]=N`: 1=Explosive · 4/6=Lengthy/Controlled-Lengthy · 0=Controlled.
(Kanat/bek → `at[]=1`; stoper → `at[]=4&at[]=6&at[]=0`.)

**Bütçe (sonra eklenecek):** değer tavanı filtresini budget verince eklerim (`&vlh=...` tarzı max-value;
tam parametreyi ilk kullanımda teyit ederim). Şimdilik `sort=asc` zaten en ucuzları öne alıyor.

---

## 2. BASE LİNKLER + kodladıkları eşikler (mevcut = League One/Two seviyesi)

**GK** — Reflexes≥68, GK Pos≥68, Kicking≥60
`https://sofifa.com/players?type=all&gcl=60&gpl=68&grl=68&col=oa&sort=asc`

**RB (AWB-Attack)** — Accel≥80, Sprint≥78, Cross≥66, DefAwr≥60, Stamina≥80, Explosive
`https://sofifa.com/players?type=all&pn%5B%5D=3&spl=78&acl=80&crl=66&mal=60&stl=80&at%5B%5D=1`

**RCB (Ball-Playing Def)** — Accel≥66, DefAwr≥64, Strength≥74, PS: Anticipate
`https://sofifa.com/players?type=all&pn%5B%5D=5&acl=66&mal=64&srl=74&ps1%5B%5D=131072`

**LCB (Stopper)** — Accel≥66, Heading≥72, DefAwr≥64, St.Tackle≥66, Strength≥76, PS: Anticipate+Jockey, Lengthy/Ctrl
`https://sofifa.com/players?type=all&pn%5B%5D=5&acl=66&hel=72&mal=64&sal=66&srl=76&ps1%5B%5D=131072&ps1%5B%5D=16384&at%5B%5D=4&at%5B%5D=6&at%5B%5D=0`

**LB (Wingback-Balanced)** — Accel≥76, Cross≥60, DefAwr≥62, St.Tackle≥62, Stamina≥80
`https://sofifa.com/players?type=all&pn%5B%5D=7&acl=76&crl=60&inl=0&mal=62&sal=62&stl=80`

**CDM (Holding)** — ShortPass≥62, Intercept≥66, DefAwr≥66, Stamina≥78, Strength≥70, PS: Intercept+Anticipate
`https://sofifa.com/players?type=all&pn%5B%5D=10&shl=62&inl=66&mal=66&stl=78&srl=70&ps1%5B%5D=65536&ps1%5B%5D=131072`

**RCM (Playmaker-Roaming)** — Vision≥70, ShortPass≥70, BallCtrl≥70, Dribbling≥70, Stamina≥80, PS: Incisive+Tiki Taka
`https://sofifa.com/players?type=all&pn%5B0%5D=14&vil=70&shl=70&bll=70&drl=70&stl=80&ps1%5B0%5D=256&ps1%5B1%5D=2048&col=oa&sort=asc`

**LCM (Deep-Lying Playmaker)** — Vision≥70, LongPass≥70, Composure≥70, BallCtrl≥70, DefAwr≥60, PS: Pinged+Long Ball
`https://sofifa.com/players?type=all&pn%5B0%5D=14&vil=70&lol=70&cml=70&bll=70&mal=60&ps1%5B0%5D=512&ps1%5B1%5D=1024&col=oa&sort=asc`

**RW (Wide Playmaker)** — Accel≥80, Vision≥68, ShortPass≥70, Agility≥78, Dribbling≥74, PS: Technical+Incisive
`https://sofifa.com/players?type=all&pn%5B0%5D=23&acl=80&vil=68&shl=70&agl=78&drl=74&ps1%5B0%5D=1048576&ps1%5B1%5D=256&col=oa&sort=asc`

**LW (Inside Forward)** — Accel≥82, Finishing≥66, Shot Power≥72, Composure≥64, Dribbling≥74
`https://sofifa.com/players?type=all&pn%5B%5D=27&acl=82&fil=66&sol=72&cml=64&drl=74&col=oa&sort=asc`

**ST (False 9)** — Finishing≥66, Vision≥68, ShortPass≥70, Composure≥70, BallCtrl≥72, Dribbling≥74
`https://sofifa.com/players?type=all&pn%5B0%5D=25&fil=66&vil=68&shl=70&cml=70&bll=72&drl=74&col=oa&sort=asc`

---

## 3. GÜNCELLEME KURALLARI
**Lig atlayınca** (her kademe) eşikleri yükselt:
- **Silah attribute'ları** (⭐ olanlar, ör. CAM Vision, LW Finishing, bek Crossing): **+4/kademe**.
- **Destek attribute'ları:** **+2-3/kademe**.
- Kademeler: League Two → League One → Championship → Premier League.
- Örn. promosyonda ST Vision 68→72, Finishing 66→70; RB Cross 66→70 olur.

**Bütçe verince:** `sort=asc` + değer tavanı → o bütçeye sığan, eşiği geçen en iyi adayları öne çıkarırım.

**Çalışma akışı:** Buğra "yetenek avı, [lig], bütçe [X]" der → bu base'den eşikleri kaydırır, linkleri
üretir ya da Chrome'dan çekip mevki mevki shortlist çıkarırım.

### Küçük öneri
- **RCB linkine pasör filtresi ekleyebilirsin:** Ball-Playing Def için `&vil=62&shl=64` → top oynatan stoperleri yakalar (şu an sadece accel/defAwr/strength + Anticipate var).
