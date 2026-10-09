# 13 · KARİYER MODU DENEYİMİ — "Menajer Masası"
Hedef: Offline kariyer oynayanın oyunun yanında açık tuttuğu **tek ekran**. Bu dosya 05'teki CAR-01..11 maddelerinin yerine geçer ve onları genişletir. Tasarım dili 02, kart ve saha bileşenleri 02 §3.
Durum etiketleri 00'daki gibi. Kariyer verisi kaynağına göre her özellik şu şekilde işaretlidir: **[K]** konsol/elle giriş yeterli · **[PC]** Live Editor import gerekir · **[K+PC]** ikisinde de çalışır, PC'de daha zengin.

---

## 0. KİM, NE ZAMAN, NE İÇİN
| Kullanıcı | Durum | İhtiyaç | Bugün ne oluyor |
|---|---|---|---|
| **Konsol oyuncusu** (çoğunluk) | Oyun TV'de, telefon/tablet elinde | Hızlı karar: kimi alayım, kimi satayım, kim oynasın | Live Editor kullanamıyor; elle giriş zahmetli; ürünün yarısı ona kapalı |
| **PC oyuncusu** | Oyun ve site aynı makinede, ikinci monitör | Derin analiz: gerçek potansiyel, gelişim takibi, regen avı | Import var ama tek seferlik; her yükleme öncekini siliyor, geçmiş yok |
| **Uzun save oyuncusu** (10+ sezon) | Aylarca aynı kariyer | Tarihçe, efsaneler, regen'ler, rekorlar | Hiç yok |
| **Challenge oyuncusu** (RTG, sadece altyapı, Anadolu kulübü) | Kural koyup oynar | Kuralların takibi, ilerleme, paylaşım | Hiç yok |

Kullanım anı: **transfer dönemi** (en yoğun), **sezon başı/devre arası** (taktik + kadro planı), **maç öncesi** (rakip, ilk 11, rotasyon), **sezon sonu** (gelişim, satış, sözleşmeler).

## 1. TASARIM İLKELERİ (bu ekranlar için zorunlu)
1. **İkinci ekran modu:** Telefon/tablette tek elle kullanılabilir. Dokunma hedefi ≥ 44px, en sık 5 eylem alt sekme çubuğunda. Oyun oynarken bakılacak bilgi tek bakışta okunur (büyük sayılar, renk + şekil).
2. **İki dokunuş kuralı:** "X'i aldım", "Y'yi sattım", "Z'yi kiraladım", "ilk 11'i değiştir" en fazla 2 dokunuş + arama.
3. **Hiçbir şey kaybolmaz:** Her değişiklik bir olay (event); geri alınabilir; her import bir **snapshot**; tüm kariyer JSON olarak dışa aktarılabilir/yedeklenebilir.
4. **Spoiler kontrolü:** Bazı oyuncular gerçek potansiyeli görmek istemez. "Scout modu" açıkken potansiyel oyundaki gibi **aralık** olarak gösterilir (ör. 78–84), tam sayı gizli. Varsayılan: açık (spoiler göster); ilk kurulumda sorulur.
5. **Her öneri gerekçeli:** "Bu oyuncuyu sat" değil; "Sat: 29 yaşında, taktiğinde yedek, değeri bu yaz zirvede (€18M), yerine akademiden Ahmet hazır."
6. **Oyunun dilini konuş:** Oyundaki terimler (Build-Up, Defensive Depth, rol/focus adları, pozisyon kısaltmaları) Türkçe arayüzde de oyundaki Türkçe karşılıklarıyla (`VERIFY` FC27 TR yerelleştirmesi).
7. **Çevrimdışı dayanıklı:** PWA; son durum cihazda önbellekte (sadece okuma), ağ gelince senkron.

## 2. BİLGİ MİMARİSİ (kariyer modu)
Alt sekme çubuğu (mobil) / sol menü (masaüstü):
```
MASA  ·  KADRO  ·  TAKTİK  ·  TRANSFER  ·  GELİŞİM
                       (⋯ daha fazla: Akademi & Scout · Rakipler · Tarihçe · Challenge · Ayarlar)
```
Üst bant (her ekranda yapışkan): kulüp arması/renkleri · sezon + dönem (ör. "2027/28 · Yaz transfer dönemi · 12 gün") · transfer bütçesi · maaş bütçesi · senkron durumu ("Son import: 2 gün önce").

## 3. EKRANLAR

### 3.1 MASA (kariyer ana sayfası) — CAR-20
```
┌ [Arma] Trabzonspor · 2027/28 · Yaz dönemi ⏱ 12 gün      Bütçe €24.5M · Maaş €310K/hf ┐
├───────────────────────────────┬─────────────────────────────────────────────────────┤
│ TAKTİK UYUMU   82% ▲3         │  BU DÖNEMİN 3 İŞİ                                     │
│ (SquadFit · IntentFit 76%)    │  1. Sol bek: zayıf halka (RoleFit 58) → 3 aday  [Gör] │
│ mini saha: kırmızı nabızlı    │  2. Sat: Visca (31, yedek, değer zirvede)      [Gör] │
│ zayıf slotlar                 │  3. Kiralık dönüşü: Ömer (19) → kadroya mı?    [Karar]│
├───────────────────────────────┼─────────────────────────────────────────────────────┤
│ KADRO SAĞLIĞI                 │  UYARILAR                                            │
│ Yaş profili ▁▃▆█▅▂ (ort 26.1) │  • 4 sözleşme sezon sonu bitiyor              [PC]  │
│ Derinlik: CB 2/3 ⚠  ST 3/3 ✓  │  • Berk potansiyeline ulaştı (78/78)                 │
│ Gelişen: 6 ▲ · Duran: 3 ▬     │  • Wonderkid: 17y 64→86 POT, €2.1M, Danimarka        │
├───────────────────────────────┴─────────────────────────────────────────────────────┤
│ ZAMAN ÇİZELGESİ: 3 Tem Import #4 · 1 Tem Satış: Hamsik €4M · 28 Haz Transfer: ...      │
└───────────────────────────────────────────────────────────────────────────────────────┘
```
- "Bu dönemin 3 işi" = öneri motorunun en yüksek etkili 3 aksiyonu (SquadFit artışı / bütçe / yaş profili ağırlıklı skor). Her biri tek dokunuşla ilgili ekrana gider.
- Uyarılar kuralları (deklaratif, `career-alerts.json`): sözleşme bitimi [PC], potansiyele ulaşma, uzun süredir gelişmeyen genç, derinlik eksiği, kiralık dönüşü, yaş eşiği (30+ ve düşüşte), rakip transferi [PC].
- Kabul: Masa 1.5 sn'de yüklenir; telefonda kaydırmadan ilk ekranda: uyum, 3 iş, ilk 2 uyarı.

### 3.2 KADRO — CAR-21
Üç görünüm (sekme):
1. **Saha + derinlik:** Her slotta 1. tercih kart (`PlayerCard md`, overlay=fit) ve altında 2. ve 3. tercih küçük satır (SquadFit depth chart, mevcut motor). Sürükle-bırak ile tercih sırası değişir.
2. **Tablo (scout raporu):** Sütunlar: forma/yüz/isim · yaş · mevki · OVR · POT (veya aralık) · **Δ son snapshot** (▲2) · rol & RoleFit · değer · maaş [PC] · sözleşme bitişi [PC] · durum etiketi (Kilit / Rotasyon / Gelişim / Satılık / Kiralık). Sanal liste, kolon seçici, sabit ilk kolon.
3. **Yaş/kalite haritası:** x = yaş, y = OVR, nokta boyutu = değer, renk = mevki grubu; "zirve bölgesi" (24–29) gölgeli. Satış ve gençleştirme kararını tek bakışta gösterir.
- **Durum etiketleri** kullanıcı tarafından tek dokunuşla atanır ve öneri motoruna girer (Satılık etiketi = transfer listesine ekle önerisi).
- Toplu işlem: çoklu seçim → "Satılık işaretle" / "Kiralığa uygun" / "Karşılaştır".
- **Hızlı olay girişi** [K]: sağ altta `+` → "Transfer / Satış / Kiralık / Altyapıdan / Serbest" → oyuncu ara (yazdıkça) → bedel (kısa yazım: "12.5m", "800k") → kaydet. 2 dokunuş + yazma.

### 3.3 TAKTİK — CAR-22
Mevcut kurucu (davranış tag'leri, RoleFit/IntentFit, EA kodu) korunur; eklemeler:
- **Rakibe göre plan (CAR-06):** Rakip seç (career_team verisi [PC] veya base DB [K]) → rakibin güçlü/zayıf yönleri (TeamsPage motoru zaten var) → "Bu maç için 2 değişiklik": ör. "Rakibin sol beki yavaş → sağ kanat *Arkaya koşsun*", "Rakip yüksek hat → Build-Up: Counter". Değişiklik tek dokunuşla taktiğe uygulanır, maç sonrası geri alınır.
- **Rotasyon planı (CAR-08):** Takvim girilirse [K] (ya da import [PC]) önümüzdeki 5 maç için önerilen 11'ler; yorgunluk/keskinlik verisi yoksa "maç sayısı/dakika dengesi" kuralıyla.
- **Taktik versiyonları:** "Ana taktik", "Deplasman", "Skoru koru" (ProtectLeadCard zaten var) — her biri EA koduyla.

### 3.4 TRANSFER MERKEZİ — CAR-23 (en kritik ekran)
```
┌ İHTİYAÇLAR (otomatik)            ┬ ADAYLAR: Sol bek · Bütçe ≤ €8M · Yaş ≤ 26        [Gerçekçi|İddialı|Hayal] ┐
│ ● Sol bek  RoleFit 58  (acil)    │ [kart] Kerem A.  23y  77/83  €6.5M  RoleFit 81 (+23)  Uyum Δ +4%  [★][Kıyas]│
│ ● 3. stoper (derinlik)           │ [kart] ...                                                                  │
│ ● Gelecek: 9 numara (30+)        │ Filtre çipleri: Serbest · Kiralık · Sözleşmesi bitiyor [PC] · Ülke · Lig     │
├──────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────┤
│ SATIŞ PLANI                      │ KISA LİSTE (★) — notlu, sıralanabilir, "pazarlık tavanı" alanı             │
│ Visca  €4–6M  "yedek, 31y"       │ KARŞILAŞTIRMA: aday vs. mevcut sahibi — attribute farkı, rol farkı,        │
│ Djaniny €2M   "kiralık öner"     │ "ya şunu alsaydım": SquadFit önce/sonra, ilk 11 değişimi                    │
└──────────────────────────────────┴─────────────────────────────────────────────────────────────────────────────┘
```
- **İhtiyaçlar** otomatik: zayıf halkalar (SquadFit) + derinlik açığı + yaşlanan kilit oyuncular (2 sezon ufku).
- **Adaylar**: mevcut scouting motoru (Gerçekçi/İddialı/Hayal katmanları zaten var) + "Uyum Δ" (bu oyuncu gelirse takım uyumu ne olur). Sıralama varsayılanı: uyum artışı / bedel.
- **Gerçekçilik** [PC]: kulüp prestiji (career_team `domestic_prestige`, `international_prestige`) ile oyuncunun kulübü arasındaki fark → "Gelir mi?" tahmini (Kolay / Zor / Hayal). [K]'da base DB prestij değeriyle.
- **Satış planı**: taktikte kullanılmayan + değeri yüksek + yaşı zirve sonrası oyuncular; önerilen fiyat aralığı (MODELED değer, ±%); "kiralığa ver" önerisi gençler için (dakika alamayan, POT yüksek).
- **Bütçe simülatörü**: kısa listedeki alımlar + satış planı → dönem sonu bütçe; maaş bütçesi [PC].
- **"Ya şunu alsaydım"** (CAR-01): önce/sonra saha + SquadFit/IntentFit farkı; ileride sim motoru (06) ile 100 maçlık tahmin.
- Kabul: Bir slot için aday listesi < 1 sn (cache); kısa listeye ekleme tek dokunuş; karşılaştırma ekranı mobilde dikey yığın.

### 3.5 GELİŞİM — CAR-24 (PC oyuncusu için en büyük değer)
- **Gelişim eğrileri:** Her oyuncunun snapshot'lar boyunca OVR/POT çizgisi (CAR-02 snapshot zorunlu). Takım geneli: "bu sezon en çok gelişen 5".
- **Plato tespiti:** 2+ snapshot'tır değişmeyen ve POT'u > OVR+5 olan genç → "Dakika ver ya da kirala" uyarısı.
- **Potansiyel sapması** [PC]: MODELED tahmin vs. oyundaki gerçek POT (spec §7 şeffaflık) → "Oyun bu oyuncuya bizim tahminimizden +4 fazla potansiyel vermiş."
- **Mevki dönüşümü (CAR-05):** "CM Ali → CDM Holding: RoleFit 64 → 79" (mevcut `advisor/positions` motoru); FC27'de mevki öğretme kuralları `VERIFY`.
- **Gelişim planı:** Mevcut DevelopmentPlan bileşeni (hangi rolü açabilir) bu ekrana taşınır; hedef rol seçilince hangi attribute'ların artması gerektiği.

### 3.6 AKADEMİ & SCOUT — CAR-25
- **Altyapı oyuncuları** [PC]: `career_youthplayers` tablosu (Lua export'ta zaten var) → POT'a göre sıralı, "ne zaman A takıma" önerisi (yaş + POT + taktikteki boşluk).
- **Scout planlayıcı (CAR-04)** [K+PC]: "Taktiğinde 2 sezon sonra lazım olacak arketip: hızlı sol bek" → hangi ülke/bölgeye scout göndermeli (base DB'deki genç oyuncu dağılımından ülke bazlı istatistik: o ülkede POT ≥ 80 genç sol bek yoğunluğu). Oyundaki scout kuralları `VERIFY`.
- **Wonderkids** (mevcut sayfa) burada bir sekme; "benim kariyerimde hâlâ alınabilir mi" filtresi [PC] (import'taki takım/serbest durumu).
- **Regen takibi** [PC]: kariyerde üretilmiş oyuncular (`owner_career_id`, zaten var) için ayrı liste — "efsane regen'ler".

### 3.7 RAKİPLER & LİG — CAR-26
- Mevcut TeamsPage (güçlü/zayıf yönler, kilit oyuncular, kulüp kültürü) burada.
- Ekleme: rakibin **son import'tan beri transferleri** [PC] (iki snapshot farkı), ligde güç sıralaması (takım OVR/atak/orta/defans), puan tablosu (opsiyonel elle giriş [K]).

### 3.8 TARİHÇE — CAR-27 (uzun save'lerin bağımlılığı)
- Sezon arşivi: her sezonun kadrosu (snapshot), taktiği, transferleri (event log), elle girilen kupalar/puan/gol kralı [K].
- **Kulüp efsaneleri:** en çok sezon oynayan, en büyük gelişim, en kârlı satış, en iyi transfer (bedel/katkı).
- **Kariyer kitabı:** sezon sonu paylaşılabilir görsel kart (CAR-10): arma, sezon, kupalar, en iyi 11, en çok gelişen — Instagram story oranı (9:16) + kare. Server-side render.

### 3.9 CHALLENGE — CAR-28 (topluluk ve viral büyüme)
- Hazır challenge'lar: Road to Glory (alt lig), Sadece Altyapı, Moneyball (yalnız ≤ X değerli alım), Tek Ülke, **Anadolu Kulübü** (preset kütüphanesindeki ruh), "Sıfır Bütçe".
- Her challenge = kurallar JSON'u (`challenge-rules.json`): izinli alım kuralları, kadro kısıtları, hedefler. Olay girişinde kural ihlali anında uyarı ("Bu alım Sadece Altyapı kuralını bozar").
- İlerleme kartı + sezon hedefleri; paylaşılabilir.

### 3.10 KURULUM VE SENKRON — CAR-29 (kullanışlılığın kapısı)
**Yeni kariyer sihirbazı (≤ 3 dk) [K]:**
1. Kulüp seç (arama, arma, lig) → kadro base DB'den otomatik gelir.
2. Oyun tarzını seç (mevcut 5 soruluk sihirbaz, görsel seçeneklerle) → önerilen taktik + preset/replika.
3. Spoiler tercihi (gerçek potansiyel göster / scout modu).
4. Bütçe ve sezon (oyundaki değerleri gir; boş bırakılabilir).
→ Masa açılır, ilk "3 iş" hazır.

**Senkron yolları:**
| Yol | Kim | Ne günceller | Not |
|---|---|---|---|
| Hızlı olay girişi | [K] | Transfer/satış/kiralık/altyapı | 2 dokunuş (§3.2) |
| Hızlı OVR güncelleme | [K] | Sezon arası kadronun OVR/POT değişimi | Kadro tablosunda satır içi düzenleme; klavyeyle aşağı ok + sayı; "sadece değişenleri gir" |
| **Ekran görüntüsünden okuma (OCR)** | [K] | Kadro ekranının fotoğrafı/ekran görüntüsü → isim + OVR (+ POT aralığı) | Ar-Ge (CAR-30): oyun içi kadro ekranı düzeni `VERIFY`; telefon kamerasıyla TV fotoğrafı da hedef. Eşleşmeyenler elle onay |
| Live Editor import | [PC] | Her şey (oyuncu, takım, altyapı, kiralık…) | Her import bir **snapshot** (CAR-02); önceki silinmez; fark raporu: "12 oyuncu gelişti, 3 transfer, 1 yeni regen" |
| Tek tık Live Editor | [PC] | Aynı | Lua script'i siteye doğrudan gönderim (yerel HTTP'yi Live Editor desteklemiyorsa log dosyasını sürükle-bırak, mevcut) |

**Live Editor export genişletmesi (CAR-31)** [PC] — `fc27_career_export.lua`'ya eklenecek tablolar (ad ve kolonlar `VERIFY`, `fc27_dump.lua` ile Buğra'nın makinesinde doğrulanır): oyuncu sözleşmesi + maaş + değer, form/moral, sakatlık, sezon istatistikleri (maç/gol/asist/not), takvim/fikstür, transfer bütçesi, scout'lar. Her biri ilgili ekranın [PC] özelliklerini açar.

## 4. VERİ MODELİ DEĞİŞİKLİKLERİ
```sql
-- CAR-02: import'lar artık silinmez; her import bir snapshot
CREATE TABLE career_snapshot (id BIGSERIAL PRIMARY KEY, career_id BIGINT NOT NULL REFERENCES career(id) ON DELETE CASCADE,
  taken_at TIMESTAMPTZ NOT NULL DEFAULT now(), season INT, transfer_window TEXT, source TEXT NOT NULL CHECK (source IN ('IMPORT','MANUAL','OCR')),
  player_count INT, note TEXT);
-- career_player_data / career_player_rating'e snapshot_id eklenir; "güncel" = son snapshot (view: career_player_current)
ALTER TABLE career_player_data ADD COLUMN snapshot_id BIGINT REFERENCES career_snapshot(id) ON DELETE CASCADE;
-- Kariyer başına kullanıcı ayarları
ALTER TABLE career ADD COLUMN spoiler_mode TEXT NOT NULL DEFAULT 'SHOW' CHECK (spoiler_mode IN ('SHOW','SCOUT_RANGE'));
ALTER TABLE career ADD COLUMN wage_budget_eur BIGINT, ADD COLUMN window_ends_on DATE, ADD COLUMN challenge_id TEXT;
-- Kullanıcı etiketleri ve kısa liste
CREATE TABLE career_player_tag (career_id BIGINT, base_player_id BIGINT, tag TEXT NOT NULL, PRIMARY KEY (career_id, base_player_id));
CREATE TABLE career_shortlist (career_id BIGINT, base_player_id BIGINT, note TEXT, max_fee_eur BIGINT, created_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (career_id, base_player_id));
-- Tarihçe
CREATE TABLE career_season_record (career_id BIGINT, season INT, league_position INT, trophies TEXT[], top_scorer_id BIGINT, note TEXT,
  PRIMARY KEY (career_id, season));
```
Mevcut `CareerImportService`'in "sil ve yeniden yaz" davranışı (`career/importer/CareerImportService.java:104`) bu değişiklikle **BEHAVIOR CHANGE** olur; geri dönüş için "son snapshot'ı sil" eylemi.

## 5. ÖNERİ MOTORU — "Bu dönemin 3 işi"
Aday aksiyonlar (her biri skor + gerekçe metni üretir; NLG şablonları mevcut NlgService yaklaşımıyla):
| Aksiyon | Skor girdileri |
|---|---|
| Zayıf slotu doldur | SquadFit artışı × slot önemi (ilk 11 > yedek) ÷ bütçe payı |
| Sat | taktikte kullanım (yedek/hiç) × değer × yaş eğrisi (zirve sonrası) |
| Kirala ver | yaş ≤ 21 × (POT − OVR) × dakika beklentisi düşük |
| Altyapıdan yükselt [PC] | POT × taktikteki boşluk |
| Sözleşme yenile/bırak [PC] | bitiş tarihi × rol önemi × yaş |
| Taktik değişikliği | IntentFit düşük slot sayısı → alternatif tag/rol önerisi |
Kurallar `career-actions.json`'da, katsayılar config (`CALIBRATE`). Kullanıcı bir öneriyi "Gizle" derse o oyuncu için 1 dönem önerilmez.

## 6. GÖREV LİSTESİ (sıra önerisi)
| ID | Pri | Görev | Bağımlılık | Kabul kriteri |
|---|---|---|---|---|
| CAR-29 | P0 | Yeni kariyer sihirbazı (≤ 3 dk, konsol uyumlu) | DS-01..05 | Yeni kullanıcı 3 dk içinde Masa'ya ulaşır (5 kişilik kullanılabilirlik testi) |
| CAR-02 | P0 | Snapshot modeli + fark raporu | — | 2 import arası fark raporu doğru (fixture) |
| CAR-20 | P0 | Masa ekranı + uyarı kuralları + "3 iş" | CAR-02 | §3.1 kabulü |
| CAR-21 | P0 | Kadro: saha+derinlik, tablo, yaş haritası, hızlı olay girişi | — | Olay girişi 2 dokunuş + yazma |
| CAR-23 | P0 | Transfer Merkezi (ihtiyaç, aday, kısa liste, satış planı, bütçe simülatörü, karşılaştırma) | CAR-21 | §3.4 kabulü |
| CAR-24 | P1 | Gelişim ekranı (eğriler, plato, sapma, mevki dönüşümü) | CAR-02 | 3 snapshot'lık fixture'da eğriler ve plato uyarısı |
| CAR-22 | P1 | Taktik: rakibe göre plan + taktik versiyonları + rotasyon | — | — |
| CAR-31 | P1 | Lua export genişletmesi (sözleşme, maaş, değer, istatistik, sakatlık, takvim) | Buğra'nın dump'ı | Yeni tablolar import'ta okunur |
| CAR-25 | P1 | Akademi & scout planlayıcı + regen listesi | CAR-31 | — |
| CAR-26 | P2 | Rakipler & lig (transfer farkı, güç sıralaması) | CAR-02 | — |
| CAR-27 | P2 | Tarihçe + kariyer kitabı paylaşım kartı | CAR-02 | — |
| CAR-28 | P2 | Challenge motoru + 6 hazır challenge | CAR-21 | Kural ihlali olay girişinde uyarı verir |
| CAR-30 | P2 | Ekran görüntüsünden kadro okuma (OCR) Ar-Ge | — | 3 farklı ekran görüntüsünde isim+OVR ≥ %90 doğru eşleşme |
| CAR-32 | P1 | Spoiler / scout modu (POT aralığı) | — | Mod açıkken API yanıtında da tam POT dönmez |
| CAR-33 | P1 | PWA + çevrimdışı okuma + JSON yedek/geri yükle | — | Uçak modunda Masa/Kadro açılır |
| CAR-01 | P2 | "Ya şunu alsaydım" — şimdilik SquadFit; sim motoru gelince Monte Carlo | 06 SIM-6 | — |

## 7. ÖLÇÜM (ürün analitiği, PLT-08)
- Aktivasyon: yeni kariyerden Masa'ya ulaşma oranı ve süresi.
- Haftalık geri dönüş: transfer dönemlerinde günlük aktif kullanıcı.
- "3 iş" tıklanma oranı (öneri kalitesinin göstergesi).
- Olay girişi süresi (ortanca, hedef < 15 sn).
- Konsol vs PC kullanıcı oranı (OCR'ın önceliğini belirler).
