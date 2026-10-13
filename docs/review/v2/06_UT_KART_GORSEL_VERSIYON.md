# 06 · UT KART GÖRSELİ + "BİR OYUNCU → ÇOK KART" (promo versiyonları)
İstek (Buğra): Oyuncu fotoğrafı UT kartının üstünde, oyundaki gibi dursun (örnek: Mbappé 91 ST). EA'nın kullandığı adres:
`https://ratings-images-prod.pulse.ea.com/FC27/components/items/231747_tr-TR.webp` (Mbappé), `…/239085_tr-TR.webp` (Haaland). Oyuncu sayfasında birden çok kart (promo) arasında geçiş olsun. UT oyuncularını çekmenin kolay yolu bulunsun, hepsi extension'dan gelmesin.

---
## 1. ID EŞLEMESİ — doğrulandı
`{id}` = **oyun içi oyuncu id'si** = Live Editor `playerid` = FUT `assetId` (= `baseId`) = sofifa id.
Kanıt (Buğra'nın LE CSV'leri, `VERIFIED`):
- `players.csv`: `231747,91,94` · `239085,91,93`
- `teamplayerlinks.csv`: 231747 → takım 243 (Real Madrid) + 1335 (Fransa) · 239085 → 10 (Man City) + 1352 (Norveç)
- Backend zaten bu id'yi tutuyor: `PlayerSummary.faceAssetId` = `ea_id` (`J/catalog/repository/PlayerSearchRepository.java:228`). **Yeni veri gerekmeden** base kart görseli her katalog oyuncusu için hesaplanabilir.

Görselin kendisi (yüklenen örnek): 440×548 WebP, saydam arka plan, ~49 KB. İçinde **tam base kart** var: çerçeve, yüz, OVR, pozisyon, 6 face stat, bayrak/lig/kulüp, PlayStyle+ rozeti. Yani kartı biz çizmiyoruz, EA'nın render'ını gösteriyoruz.
Örnekte stat etiketleri İngilizce (PAC/SHO…), URL `tr-TR`. Locale'in etiketleri değiştirip değiştirmediği `VERIFY` → locale config'te (`cardDesign.locale`).

**Sınırları:**
- Yalnız **base** kart. Ratings sitesi promo/TOTW/Icon kartı barındırmıyor (`VERIFY`, büyük olasılıkla).
- Base kart rating güncellemesinde (sezon içi refresh) değişir. Görsel EA tarafında güncellenir, cache süremiz bunu geciktirmemeli (§3: 24 saat).
- **Kariyer oyuncularında kullanma.** Kariyerde OVR değişiyor, görseldeki 91 bayat kalır. Kariyerde kendi çerçevemiz + yüz (§2.3).

---
## 2. GÖRSEL KATMANLARI (öncelik sırası)
| # | Durum | Görsel | Not |
|---|---|---|---|
| 1 | UT base kart | EA render `components/items/{assetId}_{locale}.webp` | Tam kart, overlay'ler (kimya elması, fit rozeti, fiyat) kartın **dışında/altında** |
| 2 | UT promo kart | Kendi `card_design` çerçevemiz (rarity'ye göre renk/desen) + promo portresi (web-app CDN `p{resourceId}.png`, `VERIFY`) ya da base portre | EA çerçeve PNG'leri kopyalanmaz (D-04). Promo portresi yoksa base portre |
| 3 | Kariyer oyuncusu | Kendi çerçevemiz + yüz | Yüz kaynağı §2.3 |
| 4 | Hiçbiri yüklenemedi | Kendi çerçeve + siluet | Bugünkü davranış |

### 2.1 Neden EA render'ı?
Bugün FC27 portre adresi (`CatalogProperties.java:15`) 403 dönüyor → her yerde siluet. Kendi SVG çerçevemiz (OWN) EA'ya benzemiyor ve küçük boyutta okunmuyor (WEB-DS-01..04). EA render'ı ikisini birden çözer, D-23 ("kartlar oyundaki gibi görünsün, EA_ASSET varsayılan") ile birebir uyumlu.

### 2.2 Overlay düzeni (render'ın üstüne yazma)
Render'da EA zaten OVR/pozisyon/stat çiziyor. Bizim bilgilerimizi kartın üstüne basma, kartın **altına** şerit olarak koy:
```
┌────────────┐
│  EA render │  ← <img> object-fit: contain, aspect 440/548
│  (kart)    │
└────────────┘
 ◆◆◇  86%  12.5K   ← kimya elmasları · rol uyumu · fiyat (11px+ mono)
```
Sahada küçük boyutta (xs, ~64px genişlik) render okunaksızsa xs için yalnız yuvarlak yüz kırpması + OVR rozeti kullan (§2.3'teki kırpma).

### 2.3 Yüz (portre) kaynağı — kariyer kartları ve küçük boyut için
Sırayla dene, ilk başarılıyı kullan:
1. Ratings API `avatarUrl` (`VERIFY` alan adı ve FC27 adresi; §6 komutuyla kontrol et).
2. Web-app içerik CDN'i `…/content/{GUID}/2027/fut/items/images/mobile/portraits/{assetId}.png` (`VERIFY`; GUID extension keşfiyle).
3. **Render'dan kırpma:** EA render'ını `<img>` olarak yükle, CSS ile yalnız yüz bölgesini göster (`object-fit: none; object-position: 50% 22%; width/height` ölçeğiyle). Ayrı dosya yok, aynı görsel. 440×548 görselde yüz kabaca x 140-300, y 70-330 aralığında (örnek Mbappé görseline göre; `VERIFY` birkaç kartla).
4. LE'den kullanıcının kendi oyun dosyasındaki `heads/p{playerid}.dds` (yalnız kendi tarayıcısında, DN-04, 07 §5).
5. Siluet.

---
## 3. BACKEND — görsel proxy (D-04 kararıyla)
`GET /api/v1/img/card/{assetId}?locale=tr-TR` (yeni) ve mevcut `/img/face/{id}` aynı servisten:
```java
// ImageProxyService
record ImageKey(Kind kind, long id, String locale, int templateVersion) {}
enum Kind { CARD_RENDER, FACE }
```
- **Allowlist:** yalnız config'teki template'ler (`fcareer.images.templates.card-render`, `.face`). `{assetId}` yalnız rakam, 1..2^24 (base id). Kullanıcıdan URL alma (SSRF yok).
- **Cache:** disk (ya da Redis/S3) anahtarı `ImageKey`, toplam byte sınırı (örn. 2 GB disk, 64 MB bellek LRU), **negatif cache 1 saat** (404/403).
- **Yanıt başlıkları:** `Content-Type` upstream'den (`image/webp`), `Cache-Control: public, max-age=86400` (immutable değil, takedown 48 saat sözü için), `ETag` = içerik sha1.
- **Takedown:** `fcareer.images.card-render.enabled=false` → 404 döner, web OWN'a düşer. `takedown_log` tablosu (BE-ARCH-07).
- **Rate limit:** IP başına dakikada 300 görsel; EA'ya giden istek havuzu en fazla 4 eşzamanlı.
- **Referer/UA:** EA'ya `Referer` gönderme, sabit UA.
- **İzleme:** `last_status` (200/403/404) ve `checked_at` kartı başına (`card_image` tablosu, §4).

Alternatif (proxy'siz hotlink, `referrerPolicy="no-referrer"`): daha basit, kopya tutmayız. Ama EA referer/CORS ile kapatabilir ve kullanıcının IP'si EA'ya gider. **Öneri: proxy** (D-04 zaten böyle karar verdi).

`LEGAL-RISK`: Görseller EA'nın ve lisans verenlerinin. "© EA SPORTS" atfı göster, ticari öğe (reklam) kart görselinin yanına koyma, kaldırma talebine 48 saatte uy. EasySBC/futbin'in aynısını yapması izin değildir (D-23 notu).

---
## 4. VERİ MODELİ — bir oyuncu, çok kart
Bugünkü durum: `ut_item(ea_asset_id, ea_resource_id UNIQUE)` fikri doğru ama sync `asset = resource = base_player.ea_resource_id` yazıyor, capture `resourceId` taşımıyor, `card_type` CHECK'inde HALL_OF_FUT yok, hiç promo item oluşmuyor (BE-DATA-01).

### 4.1 Kimlikler (FUT kuralı, `VERIFY` FC27 ofsetleri)
- `assetId` (= `baseId`): gerçek oyuncu. Base kartta `resourceId == assetId`.
- `resourceId` (= `definitionId`): tek bir kart versiyonu. Promo versiyonları 2^24 katları eklenerek üretilir → `baseId = resourceId & 0xFFFFFF`. Örnek kalıp (Mbappé, gerçek değerler `VERIFY`): 231747, 50563395, 67340611, 84117827, …
- Diğer item alanları (futapi `itemParse`): `rareflag`, `cardsubtypeid`, `rating`, `preferredPosition`, `leagueId`, `teamid`, `nation`, `playStyle`, `resourceGameYear`, `cardassetid`, `attributeList`, `untradeable`.

### 4.2 Şema (V16)
```sql
ALTER TABLE base_player RENAME COLUMN ea_resource_id TO ea_player_id;   -- asset/resource karışıklığını bitir
CREATE TABLE ut_rarity (
  id INT PRIMARY KEY,                 -- EA rareflag
  code TEXT NOT NULL,                 -- 'GOLD_RARE','TOTW','ICON','HERO','HALL_OF_FUT','EVO', promo kodları
  card_type TEXT NOT NULL CHECK (card_type IN ('NORMAL','ICON','HERO','HALL_OF_FUT','EVO')),
  design_code TEXT REFERENCES card_design(code),
  name_en TEXT, name_tr TEXT, ea_guid TEXT
);
ALTER TABLE ut_item
  ADD COLUMN ea_rarity_id INT REFERENCES ut_rarity(id),
  ADD COLUMN quality TEXT GENERATED ALWAYS AS (CASE WHEN rating<=64 THEN 'BRONZE' WHEN rating<=74 THEN 'SILVER' ELSE 'GOLD' END) STORED,
  ADD COLUMN gender SMALLINT,
  ADD COLUMN ea_team_id INT, ADD COLUMN ea_league_id INT, ADD COLUMN ea_nation_id INT,   -- SBC gereksinimleri EA id kullanır
  ADD COLUMN parent_item_id BIGINT REFERENCES ut_item(id),                              -- evo/upgrade zinciri
  ADD COLUMN price_range_min INT, ADD COLUMN price_range_max INT,
  ADD COLUMN first_seen_at TIMESTAMPTZ, ADD COLUMN last_seen_at TIMESTAMPTZ,
  ADD COLUMN common_name TEXT, ADD COLUMN card_name TEXT;                               -- WEB-DS-02
ALTER TABLE ut_item DROP CONSTRAINT IF EXISTS ut_item_card_type_check;
ALTER TABLE ut_item ADD CONSTRAINT ut_item_card_type_check CHECK (card_type IN ('NORMAL','ICON','HERO','HALL_OF_FUT','EVO'));
CREATE TABLE card_image (
  ea_resource_id BIGINT NOT NULL, kind TEXT NOT NULL CHECK (kind IN ('EA_RENDER','EA_PORTRAIT','PROMO_PORTRAIT')),
  locale TEXT NOT NULL DEFAULT '-', last_status SMALLINT, checked_at TIMESTAMPTZ,
  PRIMARY KEY (ea_resource_id, kind, locale)
);
CREATE INDEX ix_item_asset ON ut_item(game_version_id, ea_asset_id);
```

### 4.3 Item nereden oluşur (normalizer, D-20 konsensüsüyle)
| Kaynak | Ne verir | Yol |
|---|---|---|
| S1 ratings sitesi (otomatik, günlük) | Tüm base kartlar: stat, PS/PS+, alt pozisyonlar, WF/SM, AcceleRATE, render | Mevcut `EaSiteProvider` → `syncBaseItems` (BE-BUG-09 düzeltmeleriyle) |
| S3 katkıcı capture | Kulüp, market araması, paket açılışı, SBC/objective ödülü, `defid` yanıtlarındaki **her** itemData | `ContributionService` → `UtItemNormalizer` (yeni). Anahtar `resourceId`. Kişisel alanlar (`id` instance, `lastSalePrice`, owner) atılır |
| S4 admin | TOTW/promo duyurusu (EA haberleri, X), henüz kimse görmemiş kart | Admin CMS: "promo kartı ekle" (base oyuncu seç + rarity + rating + stat farkları) |

Kişisel kulüp import'u (PERSONAL mod) global tabloya yazmaz; ama kulüpteki bilinmeyen `resourceId`'leri **anonim tanım** olarak katkı kuyruğuna önermek opsiyonel (Buğra onayı, D-18 ruhu).

---
## 5. API + EKRAN
### 5.1 Uçlar
| Uç | Dönüş |
|---|---|
| `GET /ut/kb/items?q=&rating=&pos=&league=&nation=&club=&rarity=&promo=&ps=&quality=&sort=&cursor=` | `[{resourceId, assetId, name, cardName, rating, position, altPositions, rarity:{id,code,cardType}, promo, quality, club:{eaId,name,crestUrl}, league:{...}, nation:{...,flagUrl}, faceStats, images:{cardRender?, portrait?}, price:{console,pc,updatedAt,lowConfidence}}]` (KbItem'ı zenginleştir — bugün yalnız iç id dönüyor) |
| `GET /ut/kb/players/{assetId}` | `{player:{assetId, names, birthdate, height, foot, ...}, versions:[item…] (rating desc + released_at), defaultResourceId}` |
| `GET /ut/kb/items/{resourceId}` | tek versiyon + detay attribute'lar + PS/PS+ + roller + "nasıl alınır" (SBC id / objective / paket) |
| `GET /ut/kb/items/{resourceId}/prices?platform=&range=7d` | grafik (BE-BUG-01..03 düzeldikten sonra) |
| `GET /img/card/{assetId}?locale=` | EA render (base) |

### 5.2 Oyuncu sayfası (`/ut/players/:assetId`, tasarım 08 §2)
```
┌──────────────────────────────────────────────────────────────┐
│ ‹ Base 91 › TOTW 93 › POTM 94 › Evo yolu ›    [Karşılaştır]   │ ← versiyon şeridi: küçük kart küçük resimleri, yatay kaydırma
├───────────────┬──────────────────────────────────────────────┤
│  [büyük kart] │ Fiyat: 1.2M (PS) · 1.1M (PC) · 2 sa önce     │
│  EA render    │ Nasıl alınır: Paket / SBC "X" / Objective     │
│  ya da kendi  │ PlayStyles+: Rapid+, Finesse Shot+           │
│  çerçeve      │ Roller: ST Advanced Forward++ …              │
│               │ 34 attribute tablosu (seçili versiyon, base'e │
│               │ göre +farklar yeşil)                          │
└───────────────┴──────────────────────────────────────────────┘
```
- Versiyon değişimi URL'de: `/ut/players/231747?v=50563395` (paylaşılabilir).
- Klavye: ←/→ versiyonlar arası. Mobilde şerit yapışkan.
- "Karşılaştır": iki versiyonun attribute farkı (yan yana).
- Kadro kurucudan karta tıklayınca aynı sayfa yan panelde açılır.

---
## 6. BUĞRA'NIN KENDİ MAKİNESİNDE ÇALIŞTIRACAĞI DOĞRULAMA (10 istek)
Bu oturumdan EA alan adlarına erişilemedi. Terminalde:
```bash
for l in tr-TR en-US en-GB; do
  curl -sI "https://ratings-images-prod.pulse.ea.com/FC27/components/items/231747_$l.webp" \
   | grep -Ei '^(HTTP|content-type|content-length|cache-control|etag|access-control)'; done
curl -sI "https://ratings-images-prod.pulse.ea.com/FC27/components/items/239085_tr-TR.webp" | head -3
curl -s "https://drop-api.ea.com/rating/ea-sports-fc?locale=tr&limit=2&offset=0" | head -c 3000
```
Not et: HTTP kodu, content-type, cache-control, `access-control-allow-origin`, ve ratings API yanıtındaki `shieldUrl`/`avatarUrl` alanlarının değeri. `shieldUrl` büyük olasılıkla tam bu render adresi. Öyleyse template'i API'den okumak daha sağlam olur (`VERIFY`). Sonucu `docs/review/v2/06_VERIFY_RESULTS.md`'ye yapıştır.
Ratings API alanları (önceki sürümlerden, `VERIFY`): `id, rank, overallRating, firstName, lastName, commonName, birthdate, height, weight, skillMoves, weakFootAbility, preferredFoot, leagueName, avatarUrl, shieldUrl, alternatePositions[], playerAbilities[{id,label,imageUrl,type}], gender, nationality{id,label,imageUrl}, team{id,label,imageUrl}, position{...}, stats{<attr>:{value,diff}}`. `nationality.imageUrl` ve `team.imageUrl` bayrak/arma için de kaynak olabilir (WEB-DS-03).

---
## 7. EXTENSION'SIZ UT VERİSİ — seçenekler
| Seçenek | Kapsam | Tazelik | Efor | Risk |
|---|---|---|---|---|
| S1 ratings API + render (mevcut) | **Tüm base kartlar** (erkek + kadın) | Rating güncellemelerinde | Düşük | Düşük (public; görseller EA telifi) |
| S2 web-app statik `players.json`/portreler | Base liste + portre | Patch başına | Düşük | Düşük-orta (`VERIFY` FC27'de auth'suz mu) |
| S4 admin CMS (EA haber/X promo duyuruları) | TOTW/promo, elle | Saatler | Yüksek | Düşük |
| S3 katkıcı capture | Zamanla tama yaklaşır | Neredeyse gerçek zamanlı | Orta | Düşük-orta |
| Lisanslı API (FutDB vb.) | Muhtemelen tüm versiyonlar (`VERIFY` FC27) | Günlük | Düşük | Şartlar izin verirse düşük |
| Fan sitelerini çekmek | Hepsi | Dakikalar | Orta | **Yüksek** — reddedildi |
| Bot hesapla `defid` çağrısı | Hepsi | Gerçek zamanlı | Orta | **Çok yüksek** — reddedildi |

**Sonuç:** "Kolay yol" = **S1 tüm base kartları otomatik getirir** (zaten var, görseliyle birlikte). Promo'lar için S3 + S4; ticari ölçekte lisans. Base kartlar için extension'a hiç gerek yok.

---
## 8. GÖREVLER
| ID | Pri | Görev | Kabul |
|---|---|---|---|
| CARD-01 | P1 | `ImageProxyService` + `/img/card/{assetId}` (§3), allowlist, disk cache, negatif cache, takedown bayrağı, upstream content-type | 2. istek upstream'e gitmez; bayrak kapalı → 404; 403 upstream → 1 sa negatif |
| CARD-02 | P1 | `/img/face` aynı servise taşınsın (BE-SEC-11), `immutable` kaldır | Başlık `max-age=86400` |
| CARD-03 | P1 | Ratings API alanları: `shieldUrl`, `avatarUrl`, `commonName`, `nationality.imageUrl`, `team.imageUrl` ingest'te saklansın | Katalogda `cardName` dolu |
| CARD-04 | P1 | Web `PlayerCard` `mode:'EA_RENDER'`: UT base kartında `<img>` + alt şerit overlay; hata → OWN; mod `/ut/rules.cardDesign`'dan (runtime) | Mbappé kartı render'la görünür; görsel bozuksa OWN |
| CARD-05 | P1 | V16 şema (§4.2) + `UtItemNormalizer` (capture → `ut_item`, `resourceId` anahtarı) | TOTW capture → yeni `ut_item` satırı, base'e dokunmaz |
| CARD-06 | P1 | `/ut/kb/players/{assetId}` + `/ut/players/:assetId` sayfası + versiyon şeridi (§5.2) | URL `?v=` ile versiyon değişir |
| CARD-07 | P2 | Rarity tablosu (`ut_rarity`) katkıcı capture'larından + admin; `card_design` rarity başına | TOTW kartı kendi renginde |
| CARD-08 | P2 | Yüz kırpma fallback'i (§2.3-3) xs kartlar ve kariyer için | Kariyer sahasında siluet yerine yüz |
| CARD-09 | P2 | `card_image.last_status` izleme + admin "kırık görsel" listesi | |
| CARD-10 | P2 | Admin "promo kartı ekle" formu (S4) | 1 kart < 1 dk |
