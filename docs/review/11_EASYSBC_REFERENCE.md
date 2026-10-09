# 11 · EASYSBC İNCELEMESİ — tasarım ve veri modeli referansı (2026-10-09)
Kaynak: easysbc.io (TR arayüz, FC27), tarayıcıyla gezildi: Kadro Kurucu, Taktikler, KKG'ler (SBC) + SBC çözüm sayfası, Galeri + set detayı, Geliştirmeler, Kulübüm, Oyuncular + oyuncu sayfası. Ağ istekleri ve DOM incelendi.
Amaç: **kopyalamak değil**, oyuncu-merkezli kalıpları ve veri modelini bizim tasarım sistemimize (02) uyarlamak. Bu belge 02'yi geçersiz kılmaz; 02'deki ekran tanımlarına somut referans ekler.

---

## 1. GENEL İZLENİM — neden "AI yapımı" gibi durmuyor
- **Oyunun kendi nesneleri her yerde:** her listede, her kartta gerçek UT kartı (doğru rarity arka planı, oyuncu kesik fotoğrafı, rating, mevki, 6 stat). Metin minimum.
- **Yoğun ama düzenli:** koyu nötr zemin (#1a1d21 civarı), ince kenarlıklı paneller, küçük köşe yarıçapı. Vurgu rengi tek: mavi (#2d5bff civarı) butonlar + "YENİ" mor rozet.
- **Tipografi:** başlıklar **Oswald** (kondanse, büyük harf), gövde **Inter**. Kart içinde EA'nın kart fontu ("Cruyff" ailesi, ticari font, sitede self-host). Kondanse display + sade gövde kombinasyonu bizim 02 önerimizle aynı yönde (Barlow Condensed + IBM Plex).
- **Her sayıya bağlam:** fiyat yanında platform (PS/PC), meta rating yanında harf notu (S, A+, B…), değişim yanında ▲ yeşil delta.
- **Eylem odaklı boş durumlar:** "Kulübünü içe aktar → sana özel çözüm" her yerde aynı çağrı.
- Eksi yanlar (bizim için fırsat): reklam yükü çok ağır (Mediavine, video overlay); kariyer modu yok; taktik açıklanabilirliği yok (rol açıklaması tek paragraf).

## 2. BİLGİ MİMARİSİ (üst menü)
`KKG'ler` (SBC) · `Oyuncular` · `Kadro Kurucu` · `Galeri` (FC27 yeni mod) · `Geliştirmeler` (Evolutions) · `Kulübüm`. Sağda arama, Yükselt (premium), giriş, dil.
Gizli rotalar (bundle'dan): `/tactics` (taktik merkezi), `/evolutions/builder|players|my-evolutions`, `/sbc-solution/:slug/:id`, `/my-club/players`, `/meta-rating`, `/club-import-tutorial`, `/import-mobile`.
→ Bizim UT modumuz için öneri: aynı 6 ana sekme + `Taktikler` + `Meta`. Kariyer modu ayrı (bizim farkımız).

## 3. UT KARTI NASIL ÇİZİLİYOR (DOM'dan, birebir teknik)
```
<div style="width:180px;height:250px; background-image:url(…/fc27/cards/e_{versionId}_{variant}.png); background-size:100% 100%; transform:scale(s)">
  <img src="…/fc27/players/{assetId|resourceId}.png" class="absolute top-[17%] left-[57%] w-[65%] -translate-x-1/2">   ← oyuncu kesik foto
  <div class="absolute top-[21%] left-[13%]"> <span class="text-3xl">85</span> <span>SNT</span> </div>                ← rating + mevki
  <div class="absolute left-[3.5%] top-[42%]"> …PlayStyle+ ikonları… </div>
  <div class="absolute right-1 top-[40%] border rounded-sm" style="background:{kartRengi}"> 3 • 4 </div>     ← SM • WF rozet
  <div class="absolute inset-x-[10%] bottom-[12.5%]"> İsim · HIZ ŞUT PAS DRİ DEF FİZ (değerler) · bayrak/lig/kulüp </div>
</div>
```
- Tek bir **taban boyut (180×250)** tasarlanıp `transform: scale()` ile her yerde küçültülüyor → tüm boyutlarda oranlar sabit, metin kırılmıyor. Bizim `PlayerCard` (02 §3.1) bu tekniği almalı: tasarım 180×250'de, boyut = ölçek.
- Kart arka planı **versionId (rarity/promo) başına bir PNG** (`e_71_0.png` = Future Stars benzeri). Renkler (yazı, SM/WF rozet zemini) versiyon başına bir tabloda (`metalId`: bronz/gümüş/altın).
- Oyuncu görseli: base kartta `assetId`, özel kartta `resourceId` (ör. `50402615` = özel versiyon, `70967` = base asset). Özel kartların bazılarının kendi portresi var (`hasDynamicImage`).
- Tüm görseller kendi CDN'leri: `assets.easysbc.io/fc27/{players|cards|clubs/dark|leagues/dark|countries|packs|sbcs/sets/icons}/…png`. Yani EA görsellerini **rehost** ediyorlar.

**Bizim için karar (08 D-23):** Buğra UT kartlarının oyundaki gibi görünmesini istiyor. Uygulama: `card_design.mode = 'EA_ASSET' | 'OWN'`:
- `EA_ASSET`: arka plan + portre EA kaynaklı, **kendi image proxy'mizden** (03 §5, 30 gün cache).
- `OWN`: bizim SVG çerçevemiz.
- Varsayılan `EA_ASSET`. Admin tek bayrakla `OWN`'a çevirebilir (kaldırma talebi gelirse 1 dakikada geri dönüş).
- Kart fontu: EA/Cruyff fontu **kopyalanmaz** (ticari font lisansı). Yerine kondanse açık font (Barlow Condensed / Saira Condensed) — görsel fark küçük.
- Arka plan PNG'lerinin kaynağı: EA web-app'in kendi içerik CDN'i. URL kalıbı `VERIFY` → katkıcı extension'ı web-app'in yüklediği görsel yollarını (`performance` kayıtları) keşif olarak raporlar (12 EXT-06).

## 4. VERİ MODELİ (gözlenen API alanları — bizim şemaya eşleme)
| EasySBC alanı | Anlam | Bizim karşılık (03 §4) |
|---|---|---|
| `resourceId`, `assetId`, `versionId`, `metalId` | kart versiyonu, oyuncu, rarity/promo, bronz/gümüş/altın | `ut_item.ea_resource_id`, `ea_asset_id`, `promo_id`, `metal` (yeni kolon) |
| `attributes:[85,85,73,82,46,88]` | 6 yüz statı | `face_stats` |
| `playStyles:[25,29,4,6]`, `playStylesPlus:[]`, `rolesPlus:[141..]` | EA id'leri | `playstyles`, `roles_plus` (id → isim tablosu gerekli: `seed/ut-playstyles.json`, `seed/ut-roles-plus.json`) |
| `metaRating`, `playerRoleId`, `metaRatingZeroChem` | rol bazlı tek sayı + en iyi rol | Bizim **RoleFit** zaten bu (rol bazlı); UI'da harf notu ekle (S/A+/A/B+…) |
| `psPrice`, `pcPrice`, `sbcPrice` | platform fiyatı | `price_agg` |
| SBC set: `kind: STREAMLINE` + `pointsRequired`; `repeatabilityMode`; `startTime/endTime`; `rewards[] (player/pack)`; `upVotes/downVotes`; `categoryName` | | `sbc_set` (+ `kind`, `points_required`) — bizim item-score çözücümüz `STREAMLINE` ile aynı şey |
| Galeri: kategori → set (`requiredCards`, `totalTokens`, `badge`) → `grades` D/C/B/A/S (`threshold`, `tokenRewards`) → platform bazlı `budgetNeeded`, `taxLoss` | FC27 "FUT Gallery" modu | **Yeni tablo** `gallery_set`, `gallery_grade` (aşağıda §6) |
| Taktik: `eaCode`, `teamTactics{formationId,buildUpStyle,defensiveApproach,lineHeight}`, `playerRoles[{playerRoleId,focus}]`, `author`, `popularityScore` | | Bizim codec ile birebir aynı numaralama (10) |

## 5. EKRAN EKRAN — gördüğümüz kalıp → bizim uyarlamamız
### 5.1 Kadro Kurucu
- Gördük: saha ortada perspektifli, boş slotlarda **rol adı + mevki** etiketi ("İç Forvet · SLK"), slota tıkla → sağda **rol paneli**: rol listesi, rol açıklaması, **Focus kartları mini ısı haritası görseliyle** (Gezici / Hücum Yap). Üstte teknik direktör (ülke/lig) seçici, Kaydet/Paylaş/İçe aktar. Sağ kolon: diziliş, "En iyi diziliş" anahtarı, **bütçe**, kaynak (pazar/kulüp/ikisi), ülke-lig-kulüp filtreleri, "Kadro kur" (AI). Altta yedekler şeridi.
- Bizim uyarlama (UX-02'ye ek):
  - Focus seçiminde **mini ısı haritası** görselleri — bizim `movement_tags`'tan otomatik üretilebilir (rakipte statik görsel, bizde davranış tag'ine göre dinamik).
  - Slot etiketinde rol + focus kısaltması.
  - "Kadro kur" = bizim CP-SAT "kimyayı tamamla" (UT-06) + **RoleFit**: rakip meta rating'e göre kurar, biz **senin seçtiğin taktiğe** göre.
  - EA taktik kodu kopyala butonu kadro kurucunun üst barında olmalı (bizde var; görünürlüğünü artır).

### 5.2 Taktikler (taktik merkezi)
- Gördük: pro oyuncu/creator taktikleri listesi: avatar, isim, rozet (Pro / Rank 1 Creator), açıklama, **kod + kopyala**, formasyon, build-up, savunma yaklaşımı:hat, **popülerlik puanı** (oyun içi kullanım verisinden), "Kadro Kurucu'da kullan".
- Bizim uyarlama: **PLT-05 topluluk galerisi** + replika kütüphanemiz (preset_and_replica_library) aynı listede: "Efsane" rozetli Pep 2010-11 vs. "Creator" taktikleri. Her taktikte **"senin kadronla uyum %"** (SquadFit) — rakipte yok.

### 5.3 KKG'ler (SBC listesi + çözüm)
- Liste kartı: set ikonu, ad, **YENİ**, maliyet (coin, PS) + altında PC fiyatı küçük, ⋮ menü, ilerleme çubuğu "0/1 tamamlandı", tekrarlanabilirlik, kalan süre (kırmızı < 1 gün), ödül görseli (oyuncu kartı veya paket), ödül adı + "SATILABİLİR/SATILAMAZ" rozeti, **meta rating + harf notu**, **oy yüzdesi** (yeşil/kırmızı) + oy sayısı.
- Sekmeler: Tümü · Favoriler · Oyuncular · Yükseltmeler · Görevler · Altyapı; arama; "Mevcut (24)" filtresi; sıralama.
- Çözüm sayfası: üstte set başlığı + ana ödül + oy; altında **her challenge bir kart**: şartlar listesi (Türkçe, sade: "Min. 1 Oyuncu RB Leipzig VEYA Frankfurt", "Toplam Kimya: Min. 14"), challenge ödülü, maliyet, **"Çözüm oluştur"**; üstte "Kulübü içe aktar · Özelleştir".
- Bizim uyarlama: UX-03 bu düzeni alsın. Şart metinleri `SbcChallengeParser` normalize çıktısından **aynı sade Türkçe kalıpla** üretilsin. Ek farkımız: "kulübünle maliyet vs. pazar maliyeti" iki fiyat yan yana.

### 5.4 Galeri (FC27 yeni mod — bizde hiç yok)
- Kategori → setler (ör. kulüp setleri Como/Fulham 15 oyuncu, Heroes, Holographics, TOTW). Her sette **D C B A S** not basamakları, toplanan token / toplam token, "Gereken jeton" (coin bütçe), "Vergi kaybı", değerlendirme, "Pazar çözümünü gör".
- **Galeri Planlayıcı:** token hedefi, coin bakiyesi, maks. vergi kaybı, maks. transfer, maks. set → setleri plana göre sıralar.
- Set detayı: oyuncu kartları grid'i; her kartın üstünde token değeri + fiyat; altında "İlk sahip" ve "Toplandı" işaretleri (First Owner bonusu).
- Bizim uyarlama → yeni görevler (04'e eklenir): **UT-30 Galeri setleri** (liste + detay), **UT-31 Galeri planlayıcı** (çok-amaçlı optimizasyon: token/coin/vergi — CP-SAT aynı motor), **UT-32 kulüp + koleksiyon senkronu** (extension capture'ından "toplandı" durumu).
- Veri: `gallery_category`, `gallery_set(id, category_id, name, required_cards, total_tokens, badge)`, `gallery_grade(set_id, name, threshold, token_rewards)`, `gallery_set_item(set_id, ut_item_id, token_value)`; katkıcı/admin pipeline'ı (03) ile beslenir. Puan/threshold mantığı `VERIFY` (oyun içi kural sayfasından).

### 5.5 Geliştirmeler (Evolutions)
- Üst sekmeler görsel kartlarla: Özet · Oyuncular · Kurucu ("meta rating maksimuma") · Geliştirmelerim.
- Durum sekmeleri: Tümü (45) · Mevcut · Kilitli · Planlandı · Devam ediyor · Tamamlandı. Filtre çipleri: Yeni, Ücretsiz, Süresi yakında doluyor.
- Evo kartı: ad + YENİ + kaynak (Rewards / Akademi / Geliştirmeler), **önce/sonra kart çifti**, altında meta rating + harf notu ve **delta (+3.3)**, durum (Kilitli: "Temellere Dönüş Görevi: Assist 5"), maç sayısı, fiyat, son kilit açma / son tamamlama süresi, şartlar (Maks. 80 OVR, Maks. 2 PlayStyle, "+3 şart daha").
- Bizim uyarlama: UT-16/17 bu kart düzenini alsın; delta = **RoleFit farkı** (kullanıcının taktiğindeki rolde).

### 5.6 Kulübüm
- İçe aktarılmamışken: saha silüeti üzerinde tek CTA "Kulübümü içe aktar" + 4 fayda maddesi; sağda "Kulübün için" önizleme kartları (Pazar: "Takımına bir oyuncu ekle: kadronu +1,5", Geliştirme: "+4,5 meta rating", KKG: "Sıradaki SBC").
- İçe aktarma: **Chrome eklentisi** + "İçe aktarmayı başlat" butonu web-app'i açar, eklenti otomatik çeker (dosya indirme/yükleme yok). Ayrıca mobil içe aktarma rotası var.
- Bizim uyarlama: 12 EXT-01..05 (dosyasız, tek tık senkron). Kulübüm panosu = UT-21 "kadromu yükselt" + UT-26 envanter analizi + "sıradaki SBC" önerisi.

### 5.7 Oyuncular (liste) ve oyuncu sayfası
- Liste satırı: mini kart · isim + YENİ · mevki çipleri · **PlayStyle ikon şeridi (PS+ altın)** · 6 stat + renkli alt çizgi · BH/ZA yıldız · **en iyi rol (++ roles plus)** + meta rating + harf notu · fiyat. Liste/grid görünüm anahtarı. Filtre çipleri: Kulübüm, Geliştirmelerim, Kadro, Favori kulüp, Favori ülke; "En iyi rol" anahtarı.
- Oyuncu sayfası: stadyum fotoğraflı hero; büyük kart; Meta Rating · Meta Tier (S) · Oyuncu rolü (Derin Oyun Kurucu MDO ++) · en iyi kimya stili (Güçlü) · piyasa fiyatı; **versiyon şeridi** (aynı oyuncunun diğer kartları); Karşılaştır / Paylaş / Evo Kurucu; mevki çipleri, beceri/zayıf ayak, ayak, vücut tipi; PlayStyle ikonları isimli; **"En iyi roller" listesi** mevkiye göre gruplu (rol ++ + skor); 6 grup attribute paneli (grup değeri + alt statlar ince çubuklu); AcceleRATE + "dengesiz dribbling" türetilmiş metrik; **kimya stili seçici** (0-3 kimya sekmesi + stil grid'i, seçince statlar canlı değişir).
- Bizim uyarlama: UX-04 bu iskeleti alsın. "En iyi roller" = bizim RoleFit (zaten rol bazlı, ve davranış tag'li IntentFit ekleyebiliriz). Kimya stili seçici: `applyChemStyle` (04 §1) + capture'dan gelen `styleMods`.

## 6. ALMAYACAKLARIMIZ
- Ağır reklam ve video overlay (bizde PLT-10 tek slot kuralı).
- EA/Cruyff kart fontunun kopyası.
- Toplu EA görsel rehost'u (bizde proxy + cache + kaldırma süreci; 08 D-23).
- "Meta rating" kara kutu tek sayı → bizde her skorun **nedeni** gösterilir (moat).

## 7. GÖREVLER (bu belgeden doğanlar)
| ID | Pri | Görev |
|---|---|---|
| DS-11 | P1 | `PlayerCard` taban 180×250 + `transform: scale` mimarisi; `card_design.mode` (EA_ASSET/OWN) |
| DS-12 | P1 | Harf notu sistemi (S, A+, A, B+, B, C…) RoleFit skoruna eşlenir; tüm listelerde rating yanında |
| UX-07 | P1 | Focus seçiminde mini ısı haritası (movement_tags'tan SVG) |
| UX-08 | P1 | SBC şart metinleri sade Türkçe kalıplarla (§5.3) |
| UT-30 | P1 | Galeri setleri liste + detay (token/not/vergi kaybı/ilk sahip) |
| UT-31 | P2 | Galeri planlayıcı (CP-SAT) |
| UT-32 | P2 | Koleksiyon senkronu (extension) |
| UT-33 | P2 | Taktik merkezi: creator + efsane replika + "kadronla uyum %" |
| DATA-14 | P1 | `seed/ut-playstyles.json`, `seed/ut-roles-plus.json` (EA id → ad/ikon) — capture `players_meta` / config'ten konsensüsle |
