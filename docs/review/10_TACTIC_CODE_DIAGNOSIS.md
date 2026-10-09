# 10 · EA TAKTİK KODU — "sadece formasyon geçiyor" teşhisi
Şikâyet (Buğra, 2026-10-09): Ürettiğimiz kod oyuna girildiğinde sadece formasyon uygulanıyor. Roller ve alt roller (focus) geçmiyor.
Bu dosya ajan içindir: teşhisin sonucu, olası sebepler ve Buğra'dan gelecek test verisiyle çözüm protokolü.

---

## 0. GÜNCELLEME (2026-10-09, EasySBC incelemesi sonrası)
- EasySBC'nin taktik merkezindeki **64 gerçek FC27 kodu** (pro oyuncu ve creator taktikleri, oyunda çalıştığı bilinen kodlar; 17 farklı formasyon, 3-4-2-1, 5-2-3, 3-5-2 dahil, onlarca farklı rol+focus) codec'imizle **64/64 bit-bit aynı** üretildi.
- EasySBC'nin veri modeli bizimkiyle **aynı**: `formationId` (`f4411a`, `f4231a`…), `playerRoleId` 1–49, `focus` adı, `buildUpStyle`, `lineHeight`. `defensiveApproach` (Deep/Balanced/High) koda **girmiyor**; yalnız metadata.
- EA'nın her rol+focus kombinasyonu bizim `roles.json`'da var (eksik yok).
- **Sonuç: kod formatı ve rol tablosu doğru. "Sadece formasyon geçiyor" sorunu bu zip'teki codec'te değil.** Olası yerler: zip sonrası bir değişiklik (C4), eşleme katmanı (C3), ya da sitedeki taktik ekranında rol seçiminin `roleId` olarak export isteğine gitmemesi.
- Hazır regresyon testleri (`patches/backend/`): `EaTacticCodecEasySbcVectorsTest` (64 kod encode+decode) ve `TacticCodeRoundTripIntegrationTest` (kodu bizim modele import et → export et → aynı kod). İkincisi kırılırsa hata eşleme katmanındadır. Vektörler: `tools/tactic-code/easysbc-vectors.json`.
- §3'teki oyun içi test verisi artık **yalnız ters test** için gerekli (bizim ürettiğimiz ve oyunda tutmayan 3–5 kod + ekran görüntüsü).

## 1. Doğrulanan (`VERIFIED`, 2026-10-09 zip'i)
- Kod formatı rol + focus'u **içeriyor**: slot başına 4 bit (`EaTacticCodec`, `SLOT_TOP_SHIFT=59`, `SLOT_STRIDE=4`). Bunun dışında hat yüksekliği 7 bit, build-up 2 bit.
- Codec bağımsız bir Python port'uyla çalıştırıldı. Oyundan alınmış ve **varsayılan olmayan rol** içeren 3 kod birebir aynı üretildi:
  - `AJawHEA#K?tB` — 4-4-1-1 (2): False 9 *Attack*, Shadow Striker, Inside Forward
  - `vYHZ2fH45tMv` — 4-2-3-1 (2): Box Crasher, Holding, Inside Forward
  - `RZtRy3rYbN?y` — 4-2-1-3: Deep-Lying Playmaker, Playmaker *Balanced*, focus değişiklikleri
- Frontend'deki üç export yolu da (`EaTacticCode`, `squadBuilder/EaCodeCard`, `UtSquadPage` içindeki `UtTacticCode`) rol id'lerini backend'e gönderiyor.
- Bizim rol adlarımızın hepsi EA rol adlarıyla eşleşiyor. Formasyon eşleşmesi tekil (5-3-2 hariç; EA karşılığı yok).
**Sonuç:** Bu zip'te format sorunu yok. Sorun aşağıdaki sebeplerden biri (ya da zip'ten sonraki bir değişiklik).

## 2. Olası sebepler (olasılık sırasıyla)
| # | Sebep | Belirti | Kontrol |
|---|---|---|---|
| C1 | **Doğrulanmamış rol tablosu.** `positionTables` değerlerinin çoğu oyundan değil bir sitenin referans kodlayıcısından gözlendi (`tools/tactic-code/README.md` "Sınırlar"). Oyunda yalnız 3 formasyon + ~10 rol doğrulandı. Yanlış numara alan rolü oyun geçersiz sayıp varsayılana dönüyor olabilir. | Bazı rollerde çalışıyor, bazılarında varsayılan rol | §3 protokolü |
| C2 | **Desteklenmeyen focus → sessiz geri düşüş.** Bizim `roles.json` 11 (mevki, rol, focus) kombinasyonu üretiyor ki EA'da o mevkide yok. Export bunları `focus 0`'a ya da formasyonun varsayılan rolüne düşürüp yalnız *warning* veriyor (`TacticCodeService.eaSlot`). Uyarılar UI'da küçük metin; gözden kaçıyor. Kombinasyonlar: CM Holding *Roaming* · CM Deep-Lying Playmaker *Roaming* · CM Playmaker *Balanced*/*Build-Up* · CAM Playmaker *Attack* · CM Half Winger *Roaming* · CAM Half Winger *Support* · LM/RM Winger *Versatile* · LM/RM Inside Forward *Roaming* | Kod üretilir, oyunda o slotlar varsayılan | Export yanıtındaki `warnings` / `exact:false` |
| C3 | **Slot sırası.** Aynı mevkideki slotlar "sağdan sola" eşleniyor (`formationSlotMapping`, x azalan). Oyunun sırası bazı formasyonlarda farklıysa roller yer değiştirir. Simetrik ve varsayılan rollerde fark görünmez, "rol geçmedi" sanılır. | Roller geçiyor ama yanlış oyuncuda | §3 tek-değişiklik çiftleri |
| C4 | **Zip'ten sonraki regresyon.** Ajan kimya işinde ya da başka bir yerde `TacticCodeService`, `toTacticRequest` veya `resolveTactic` akışına dokunmuş olabilir. | Daha önce çalışan örnek artık çalışmıyor | Güncel repo + §4 testleri |
| C5 | **Oyun tarafı (`VERIFY`).** Patch ile format ya da rol listesi değişmiş olabilir (yeni rol/focus eklenmesi tabloyu kaydırır). | Eski oyun kodları da yeniden üretilemiyor | Oyundan yeni kod al, codec'le çöz |

## 3. Buğra'dan gelecek test verisi (`tools/tactic-code/samples.json` formatında)
Oyunda **Taktikler → kodu paylaş** ile alınacak. Her örnek için kod + rol listesinin ekran görüntüsü:
1. **Varsayılan taban:** 4-3-3 (1), 4-2-3-1 (2), 4-4-2, 3-5-2 için hiçbir şey değiştirmeden kod. (4 kod)
2. **Tek-değişiklik çiftleri:** Aynı formasyonda **yalnızca bir slotun** rolünü ya da focus'unu değiştirip kod al. Öncelik C2 listesindeki mevkiler ve CAM, CM, LW/RW, LB/RB. Mümkünse her mevkideki her rol+focus için bir kod. (10–30 kod)
3. **Ters test:** Bizim sitede üretilip oyunda "sadece formasyon" olan 3–5 kod. Sitede seçilen taktiğin ekran görüntüsü + oyunda açılan taktiğin ekran görüntüsü.
4. Oyun sürümü/patch numarası ve platform (PC/PS/Xbox).

## 4. Ajan görevleri
| ID | Pri | Görev | Kabul |
|---|---|---|---|
| TAC-01 | P0 | §3 verisini `samples.json`'a ekle; `analyze.mjs` ile `positionTables`'ı yeniden türet; `EaTacticCodecTest`'e **oyundan gelen her kodu** encode + decode testi olarak ekle | Oyundan gelen tüm kodlar birebir |
| TAC-02 | P0 | C2: Ya `roles.json`'daki EA'da olmayan focus'ları kaldır (rol kütüphanesi = EA rol/focus kümesi), ya da `TacticCodeService` export'unda bu durumu **hata** say. Seçim: **kaldır** (EA'da olmayan rol seçilemesin; davranış tag'leri zaten kişiselleştirmeyi taşıyor). UI'da rol seçici yalnız EA'nın o mevkide izin verdiği rol+focus'ları göstersin | `exact:false` artık yalnız 5-3-2 gibi formasyon durumunda |
| TAC-03 | P1 | Export yanıtında `exact:false` ise kodun yanında **görünür uyarı bandı**: hangi slot neye düştü | — |
| TAC-04 | P1 | "Kodu doğrula" aracı: kullanıcı oyundan aldığı kodu yapıştırır, codec çözer ve sahada gösterir. Sitedeki taktikle fark varsa slot slot listeler (C3 teşhisi için de kullanılır) | — |
| TAC-05 | P2 | Tablo sürümleme: `ea-tactic-codec.json`'a `gameVersion` + `patch` alanı; patch değişince eski tablo korunur | — |

## 5. Alternatif yol: oyuna doğrudan yazmak (yalnız PC + Kariyer)
"Taktiği direkt oyuna işleyen yapı" kod yerine kastediliyorsa: Live Editor (repo'da zaten `tools/live-editor/*.lua` var) PC kariyer modunda takımın taktik tablolarına yazabilir. Formasyon, rol/focus ve oyuncu talimatları bu yolla girilebilir.
- **Artı:** Rol + focus + belki talimatlar; kod formatına bağlı değil.
- **Eksi:** Yalnız PC, yalnız Kariyer (UT'de mümkün değil, denenmemeli), Live Editor sürümüne bağlı.
- **Görev TAC-06 (P2):** Sitedeki taktiği Lua script'e dönüştüren export ("Live Editor'a uygula" butonu → `.lua` indir). Önce Live Editor'ün FC27 taktik tablosu alan adları `VERIFY` (Buğra'nın makinesinde `fc27_dump.lua` ile dökülür).
