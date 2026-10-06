# EA taktik kodu (12 karakter) — çözülmüş format

Resmî olmayan tersine mühendislik; üretim kodu `com.fcareer.tacticcode` (backend), sabitler `seed/ea-tactic-codec.json`.

## Format
* Kod 12 karakter = 12 adet **6 bitlik basamak** (72 bit). Alfabe sırası `A–Z a–z 0–9 ? @`; görüntüde karışmasın diye
  `I→#`, `O→%`, `l→$`, `0→&` yazılır. İlk karakter en düşük basamaktır (little-endian).
* **Bit alanları** (basamak k, bit 6·(k−1)…): 11 slotun rol+odak sırası **4'er bit** (slot s: bit 59−4s, EA slot sırası = GK, sonra her
  mevkide **sağdan sola**), **hat yüksekliği 7 bit** (bit 63, değer 1–100), **build-up 2 bit** (bit 70: Short=0, Balanced=2, Counter=3).
  Alt 19 bit formasyona göre sabit "taban" içindir (formasyon başına bir taban kod).
* **Rol+odak değeri:** mevki başına tüm (rol, odak) çiftleri bir sırada numaralanır (0…n−1); numara `positionTables`'taki
  göreli değerlerin `sh − min(sh)` hâlidir.
* **Kaydırma S:** tüm alanların "mantıksal toplamı" (slotların numara farkları + (LH−50) + build-up sırası: Balanced 0, Counter 1, Short 2)
  **2. basamak hariç tüm basamaklara** (mod 64, taşımasız) eklenir. 2. basamak yalnızca 1. basamağın taşmasından etkilenir:
  `floor((d1 + S + 5)/64) − floor((d1 + 5)/64)`.
* Doğrulama: 24 test vektörü (oyundan gelen 11+3 kod ve referans kodlayıcıdan 10 rastgele) bire bir tutuyor; ayrıca 100 rastgele
  taktikte (29 formasyon) %100 tutmuştu. Çözücü, formasyon+kaydırma+checksum+2. basamak tutarlılığıyla 29 formasyonu dener.

## Araçlar
* `ea-model-data.json` — formasyon tabanları, mevki tabloları (gözlemden).
* `build-codec-resource.mjs` — veriyi `src/main/resources/seed/ea-tactic-codec.json`'a dönüştürür (rol listesi dahil).
* `samples.json`, `analyze.mjs` — ilk keşif örnekleri ve fark analizi.

## Sınırlar
* Mevki tabloları ve taban kodlar yalnızca sitenin referans kodlayıcısından gözlendi; EA yeni rol/formasyon eklerse yeniden toplanmalı.
* 11 karakterlik kodlar (arayüz "11 veya 12" diyor) desteklenmiyor; yalnızca 12 karakter.
* Davranış etiketleri koda girmez (EA kodunda yoktur).
