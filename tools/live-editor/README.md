# Live Editor araçları

Hepsi **sadece okur**; oyun verisini değiştirmez. Live Editor'ün Lua motoru dosya yazamadığı için çıktı,
`FC 27 LE v27.1.0/Logs/live_editor_<tarih>.log` içine `FCDUMP` / `NAMERAW` satırları olarak düşer.

| Script | Ne yapar | Kim çalıştırır |
|---|---|---|
| `fc27_career_export.lua` (= `fc27_base_export.lua`) | Oyuncular (tüm attribute, trait, isim kimlikleri), takımlar, ligler, `editedplayernames`, `dcplayernames` | Kullanıcı (kariyerin **içinde**) → sitede İçe aktar sayfasına log'u yükler |
| `fc27_names_rawdump.lua` | `playernames` tablosunun sıkıştırılmış isim bloğunu ham bayt olarak yazar | Sadece katalog yazarı (yılda bir) |
| `decode-names.mjs` | Ham baytlardan 43 bin ismi çözer, `playernames` FCDUMP bloğu üretir | Katalog yazarı |
| `fc27_name_probe*.lua`, `fc27_names_stream.lua` | Keşif/teşhis script'leri (artık gerekmez) | – |

## Katalog isimlerini yenileme (yeni oyun sürümü)
1. `fc27_names_rawdump.lua`'yı çalıştır → log'a `NAMERAW` yazılır.
2. `node decode-names.mjs <log> playernames.log [bilinen_sözcükler.txt]`
3. Base export log'unun sonuna `playernames.log`'u ekleyip `POST /api/v1/admin/ingestion/import-base` ile yükle
   (`overwrite=false&createMissing=false` yalnızca "Oyuncu #id" adlı oyuncuları yeniden adlandırır).

## İsim sıkıştırması (çözüldü)
Tablo sonrasında bellekte: 24 bayt başlık, 138 düğümlü Huffman ağacı (düğüm = iki u16, ≥256 yaprak = bayt<<8), sonra bit akışı.
Kayıt `key`'i akışta dizinin başladığı bayt konumudur (`AFTER[25 + key]`). Her dizinin sonunda 9 bitlik sonluk
(`d` kodu + 4 bit) ve hizalama sıfırları vardır; sıfırlar `d` gibi çözüldüğünden sondaki tek `d` belirsizdir
(katalogla doğrulamada tam ad %96,6, sözcük listesiyle %97,7).
