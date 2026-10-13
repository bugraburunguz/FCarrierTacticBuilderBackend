# 14 · EA TAKTİK KODU — format, neden yanlış çalışıyor, çözüm
İstek (Buğra): Taktik kodları kısmı doğru çalışmıyor. 12 haneli kodlar (örn. `SYq8#eNaswmP`) arka planda bit maskesi + karakter kodlamasıyla çalışıyor; çözümüne ulaşmalıyız.

**Kısa cevap:** Repodaki codec (`EaTacticCodec.java`) formatı zaten çözmüş durumda. Bağımsız Python portuyla repodaki **64 gerçek FC27 kodunun 64'ü birebir** encode ve decode edildi, hiçbir kod iki farklı taktiğe çözülmedi. Senin örnek kodun da tek anlamlı çözülüyor (§2). Sorun formatta değil, üç yerde:
1. **Rol tablosunun yarısı doğrulanmamış.** 113 (mevki, rol, focus) değerinden yalnız 59'u gerçek kodlarla doğrulandı. 54'ü bir sitenin referans kodlayıcısından gözlenmiş. 29 formasyonun 7'sinin sabiti hiç doğrulanmadı. Bunlara denk gelen taktiklerde oyun rolü varsayılana döndürüyor.
2. **Site, oyunda olmayan kombinasyonlar ürettiriyor ve sessizce düşürüyor.** Bunlar: birleşik focus listesi, 5-3-2, preset/replika rollerinin tahmin edilmesi, uyarıların küçük gösterilmesi.
3. **Defensive approach koda girmiyor**, yalnız hat yüksekliği giriyor. UI ise 0-100 slider ve FC27'ye uymayan bantlar kullanıyor.

Referans araç: `data/ea_tactic_codec_reference.py` (Java codec'in birebir portu; test oracle).

---
## 1. FORMAT (gerçek, kodla doğrulanmış)
Buğra'nın bulduğu açıklama ana fikirde doğru (12 karakter × 6 bit = 72 bit, 64 karakterlik alfabe). Ama ayrıntıda farklı, bu yüzden "düz bitmask + base64" ile çözülmeye çalışılırsa çözülemez:

| Konu | Yaygın açıklama | Gerçekte (FC27) |
|---|---|---|
| Alfabe | A-Z a-z 0-9 + özel semboller | `ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789?@` (64). Karışmasın diye 4 harf gösterimde değiştirilir: `0→&`, `I→#`, `O→%`, `l→$`. Yani koddaki `#` aslında `I` |
| Formasyon | 5 bitlik ID alanı | **Alan yok.** Her formasyonun 12 basamaklık sabit bir "taban"ı (`base`) var; kod bu tabanın üstüne yazılır. Çözerken formasyon, tabanla tutarlılıktan (basamak 0 ve 2 aynı kaydırmayı vermeli) bulunur |
| Build-Up | 2 bit | 2 bit, bit 70-71: Short Passing = 0, Balanced = 2, Counter = 3 |
| Defensive Approach | 2 bit | **Kodda yok.** Yalnız hat yüksekliği var. Approach hat bandından türer (Deep 1-30, Balanced 31-60, High 61-90, Aggressive 91-100) |
| Hat yüksekliği | 7 bit | 7 bit, bit 63-69, değer 1-100 |
| Oyuncu rolü | rol 2-3 bit + focus 2 bit | **Slot başına tek 4 bitlik indeks**, bit 59'dan aşağı 4'er (slot s → bit 59−4s). İndeks, o mevkinin izinli (rol, focus) listesindeki sıra (`positionTables`). Mevki başına en fazla 16 kombinasyon (CB 10, CM 11, RB/LB 11, ST 11) |
| Slot sırası | — | Formasyonun `positions` listesi: GK, sonra savunma, orta saha, hücum; **aynı mevkiler sağdan sola** (örn. RB, CB(sağ), CB(sol), LB) |
| Karıştırma | — | Alanlar formasyonun varsayılanına göre **fark** olarak yazılır. Tüm farkların toplamı S, 2. basamak hariç **her basamağa eklenir**. 2. basamağa yalnız S'den doğan elde (carry) eklenir → hem karıştırma hem kontrol toplamı. Bu yüzden tek bir rol değişince kodun neredeyse tüm karakterleri değişir |

Bit bütçesi: 11×4 + 7 + 2 = 53 bit veri; kalan alan formasyon tabanı ve kontrol.

**Encode (özet):**
```
add[12] = 0, S = 0
her slot s: a = idx(pos_s, rol, focus), d = idx(pos_s, varsayılanRol_s, 0); S += a−d; add += split(a, 59−4s) − split(d, 59−4s)
S += lh−50;      add += split(lh, 63) − split(50, 63)
S += buChk;      add += split(buVal, 70) − split(2, 70)       // buChk: Balanced 0, Counter 1, Short 2
digit[i] = (base[i] + add[i] + S) mod 64      (i ≠ 1)
digit[1] = (base[1] + add[1] + floor((base[0]+S+5)/64) − floor((base[0]+5)/64)) mod 64
```
`split(v, k)` = `v << k`'yı 6'şar bitlik 12 basamağa ayırır (basamak 0 en düşük). Decode = formasyon tabanlarını dene → build-up × hat × slot araması (üst basamaklardan budamalı DFS) → kontrol toplamı ve 2. basamak eldesi tutmalı.

## 2. ÖRNEK: `SYq8#eNaswmP`
```
4-2-3-1 (2) (f4231a) · Build-Up BALANCED · Hat 25 (→ Deep)
 0 GK   Goalkeeper / Balanced
 1 RB   Fullback / Balanced
 2 CB   Stopper / Balanced          (sağ stoper)
 3 CB   Defender / Defend           (sol stoper)
 4 LB   Fullback / Balanced
 5 CDM  Deep-Lying Playmaker / Defend    (sağ)
 6 CDM  Deep-Lying Playmaker / Roaming   (sol)  ← doğrulanmamış kombinasyon
 7 RM   Wide Midfielder / Support
 8 LM   Wide Midfielder / Support
 9 CAM  Playmaker / Balanced
10 ST   Advanced Forward / Attack
```
Kod tek bir taktiğe çözülüyor. Bu taktiğin oyundaki ekran görüntüsü (roller sekmesi) bu çözümle aynıysa CDM Deep-Lying Playmaker/Roaming değeri de doğrulanmış olur.

## 3. DOĞRULAMA DURUMU
- **Doğrulanmış:** 59 kombinasyon, 22 formasyon (EasySBC'de yayınlanan 64 oyun içi kod).
- **Doğrulanmamış formasyonlar (7):** 3-4-1-2, 4-1-3-2, 4-1-4-1, 4-2-4, 4-5-1, 5-3-2, 5-4-1.
- **Doğrulanmamış kombinasyonlar (54):**

| Mevki | Doğrulanmamış rol / focus |
|---|---|
| RB | Inverted Wingback (Build-Up, Attack) · Falseback/Balanced · Attacking Wingback (Support, Attack) · Wingback (Balanced, Support) |
| LB | Wingback/Support · Falseback/Balanced · Inverted Wingback (Build-Up, Attack) · Attacking Wingback (Support, Attack) |
| CB | Wide Back (Defend, Aggressive, Support) · Stopper/Aggressive · Defender/Balanced · Ball-Playing Defender/Build-Up |
| CDM | Wide Half (Defend, Build-Up) · Deep-Lying Playmaker/Roaming · Centre-Half/Defend · Holding/Roaming |
| CM | Holding/Ball-Winning · Half-Winger (Balanced, Attack, Support) |
| CAM | Classic 10 (Wide, Versatile) · Half-Winger (Balanced, Attack, Roaming) · Playmaker/Build-Up |
| RM | Winger/Attack · Wide Playmaker/Attack · Wide Midfielder (Defend, Build-Up) |
| LM | Wide Midfielder (Defend, Build-Up) · Wide Playmaker (Attack, Build-Up) |
| RW / LW | Winger (Attack, Versatile) · Wide Playmaker (Attack, Build-Up) |
| ST | Advanced Forward/Versatile · Poacher/Support · Target Forward (Attack, Wide) |

Bunların çoğu (Inverted Wingback, Wingback, Half-Winger, Classic 10, Target Forward) kullanıcıların en çok seçtiği roller. "Kod çalışmıyor" şikâyetinin en olası kaynağı bu tablo.

## 4. NEDEN YANLIŞ ÇALIŞIYOR — kod tarafı
| ID | Pri | Yer | Sorun | Düzeltme |
|---|---|---|---|---|
| TAC-07 | P0 | `seed/ea-tactic-codec.json` `positionTables`, 7 formasyon `base` | §3'teki 54 değer + 7 taban doğrulanmamış | §5 protokolüyle 10 oyun içi kod al, her biri `EaTacticCodecTest`'e vektör olarak girsin; uyuşmayan değerleri decode farkından düzelt |
| TAC-08 | P0 | `TacticCodeService.java:113-129` (`eaSlot`) | Oyunda olmayan (mevki, rol, focus) → varsayılan role ya da focus 0'a **sessizce** düşüş, yalnız `warnings` | Export'ta bu durum **hata** (422) olsun. UI zaten engellemeli (TAC-10). `exact:false` yalnız bilinçli geri düşüşte | **DONE (be0d422)**
| TAC-09 | P0 | `TacticCodeService.java:145-149`, `formations.json` | Formasyon eşleme pozisyon çokluğuyla yapılıyor. Bizim 5-3-2 (3×CM) EA'nın f532'sine (CDM+2CM) uymuyor → export hatası | `formations.json`'a `eaFormationId` ekle, eşleme id ile; 5-3-2'yi düzelt (BE-BUG-10) | **DONE (3dd82c1; BE-BUG-10 görünen ad kısmı açık)**
| TAC-10 | P0 | web `lib/squadBuilder.ts:202-206`, `lib/eaRoles.ts:18` | `/tactics/ea-roles` yüklenmezse rol seçici pozisyonlar arası **birleşik** focus listesi gösteriyor (örn. RM'de Winger/Versatile) → kullanıcı oyunda olmayanı seçiyor | Seçici yalnız `GET /tactics/ea-roles`'tan, (mevki, rol, focus) bazında; yüklenene kadar kilitli; `retry` açık | **DONE (web e224e5f)**
| TAC-11 | P1 | `TacticResolver.java:147-158`, `presets.json` | Preset/replika seçildiğinde roller etiket oylamasıyla tahmin ediliyor → kod "replika"nın gerçek rollerini taşımıyor | Replikalar v2 (12) 11 rolü açıkça taşır; `tacticCode` seed'de üretilir (REP-03) |
| TAC-12 | P1 | `TacticCodeService.java:70-100` (import), web `EaTacticCode.tsx:20-30`, `EaCodeCard.tsx:21-31` | Import'ta sitede karşılığı olmayan slot **atlanıyor** (`continue`), web store'u bu eksik listeyle eziyor → o slotlar formasyon varsayılanına döner | Import 11 slotu her zaman döner (EA rol id + focus). Site rolü yoksa slot "EA rolü, sitede tanımsız" olarak işaretlenir ama korunur. Rol kimliği EA'nınki olur (13 §3.1: (pozisyon, rol, focus)) |
| TAC-13 | P1 | `TeamSetup.java:20-22`, web `TeamSetup.tsx:52-58`, `SetupModifier.java:16-18` | Hat 0 → sessizce 1'e clamp. Approach seçimi yok, bantlar 35/65/85 (FC27: 30/60/90) | UI'da Defensive Approach seçici + o bandın içinde slider (varsayılan 25/50/70/95); codec'e yalnız hat gider; import'ta approach hattan türetilir (04 RULE-06) |
| TAC-14 | P1 | `TacticCodeService.java:158-171` | Aynı mevkide sağdan-sola eşleme (C3) asimetrik formasyonlarda doğrulanmadı | §5'teki T kodları sağ/sol slotlara **farklı** rol koyar → doğrular |
| TAC-15 | P2 | web | Uyarılar küçük metin | Export/import'ta slot slot fark bandı (TAC-03); "Kodu doğrula" aracı (TAC-04): kod yapıştır → sahada göster + site taktiğiyle slot slot fark |
| TAC-16 | P2 | `ea-tactic-codec.json` | Patch'le tablo kayarsa eski kodlar bozulur | `gameVersion` + `patch` alanı (TAC-05); her yeni patch'te 2 bilinen kodla duman testi |

## 5. OYUN İÇİ DOĞRULAMA PROTOKOLÜ (Buğra yapacak, ~20 dk)
Aşağıdaki 10 taktiği oyunda kur ve her birinin paylaşım kodunu al. Her tablo **beklenen kodu** veriyor (bizim codec'in ürettiği):
- Oyundaki kod **aynıysa** o taktikteki bütün kombinasyonlar ve formasyon tabanı doğrulandı.
- **Farklıysa** oyundaki kodu ekran görüntüsüyle (roller sekmesi) birlikte gönder. Ajan `ea_tactic_codec_reference.py` ile farkı çözüp hangi değerin yanlış olduğunu bulur.

10 kod birlikte 54 doğrulanmamış kombinasyonun ve 7 formasyonun hepsini kapsıyor.
Slot sırası: aynı mevkiler **sağdan sola**. Örneğin 3 CB'de 1. CB = sağ, 2. = orta, 3. = sol; CDM, CM, ST için de aynı kural.
#### T01 · 3-4-1-2 · Build-Up COUNTER · Hat 50 · **beklenen kod `HxhPhVrvr$jX`**
| # | Mevki | Rol / Focus |
|---|---|---|
| 0 | GK | Goalkeeper / Balanced (varsayılan) |
| 1 | CB | Defender / Balanced |
| 2 | CB | Stopper / Aggressive |
| 3 | CB | Ball-Playing Defender / Build-Up |
| 4 | RM | Winger / Attack |
| 5 | CM | Holding / Ball-Winning |
| 6 | CM | Half-Winger / Balanced |
| 7 | LM | Wide Midfielder / Defend |
| 8 | CAM | Playmaker / Build-Up |
| 9 | ST | Advanced Forward / Versatile |
| 10 | ST | Poacher / Support |

#### T02 · 4-1-3-2 · Build-Up SHORT_PASSING · Hat 70 · **beklenen kod `SBqUoF8iY4%&`**
| # | Mevki | Rol / Focus |
|---|---|---|
| 0 | GK | Goalkeeper / Balanced (varsayılan) |
| 1 | RB | Falseback / Balanced |
| 2 | CB | Wide Back / Defend |
| 3 | CB | Wide Back / Aggressive |
| 4 | LB | Falseback / Balanced |
| 5 | CDM | Holding / Roaming |
| 6 | RM | Wide Midfielder / Defend |
| 7 | CM | Half-Winger / Attack |
| 8 | LM | Wide Midfielder / Build-Up |
| 9 | ST | Target Forward / Attack |
| 10 | ST | Target Forward / Wide |

#### T03 · 4-1-4-1 · Build-Up BALANCED · Hat 95 · **beklenen kod `hJ5L451tzC$m`**
| # | Mevki | Rol / Focus |
|---|---|---|
| 0 | GK | Goalkeeper / Balanced (varsayılan) |
| 1 | RB | Wingback / Balanced |
| 2 | CB | Wide Back / Support |
| 3 | CB | Defender / Defend (varsayılan) |
| 4 | LB | Wingback / Support |
| 5 | CDM | Centre-Half / Defend |
| 6 | RM | Wide Midfielder / Build-Up |
| 7 | CM | Half-Winger / Support |
| 8 | CM | Box-to-Box / Balanced (varsayılan) |
| 9 | LM | Wide Playmaker / Attack |
| 10 | ST | Advanced Forward / Attack (varsayılan) |

#### T04 · 4-2-4 · Build-Up COUNTER · Hat 25 · **beklenen kod `TgrZcfj$d1ng`**
| # | Mevki | Rol / Focus |
|---|---|---|
| 0 | GK | Goalkeeper / Balanced (varsayılan) |
| 1 | RB | Wingback / Support |
| 2 | CB | Defender / Defend (varsayılan) |
| 3 | CB | Defender / Defend (varsayılan) |
| 4 | LB | Attacking Wingback / Support |
| 5 | CM | Box-to-Box / Balanced (varsayılan) |
| 6 | CM | Box-to-Box / Balanced (varsayılan) |
| 7 | RW | Winger / Attack |
| 8 | ST | Advanced Forward / Attack (varsayılan) |
| 9 | ST | Advanced Forward / Attack (varsayılan) |
| 10 | LW | Winger / Attack |

#### T05 · 4-5-1 · Build-Up SHORT_PASSING · Hat 50 · **beklenen kod `BhbLBJpVLpdh`**
| # | Mevki | Rol / Focus |
|---|---|---|
| 0 | GK | Goalkeeper / Balanced (varsayılan) |
| 1 | RB | Attacking Wingback / Support |
| 2 | CB | Defender / Defend (varsayılan) |
| 3 | CB | Defender / Defend (varsayılan) |
| 4 | LB | Attacking Wingback / Attack |
| 5 | RM | Wide Playmaker / Attack |
| 6 | CM | Box-to-Box / Balanced (varsayılan) |
| 7 | LM | Wide Playmaker / Build-Up |
| 8 | CAM | Half-Winger / Balanced |
| 9 | CAM | Half-Winger / Attack |
| 10 | ST | Advanced Forward / Attack (varsayılan) |

#### T06 · 5-3-2 · Build-Up BALANCED · Hat 70 · **beklenen kod `ex5o3i5$o#ag`**
| # | Mevki | Rol / Focus |
|---|---|---|
| 0 | GK | Goalkeeper / Balanced (varsayılan) |
| 1 | RB | Attacking Wingback / Attack |
| 2 | CB | Defender / Defend (varsayılan) |
| 3 | CB | Defender / Defend (varsayılan) |
| 4 | CB | Defender / Defend (varsayılan) |
| 5 | LB | Inverted Wingback / Build-Up |
| 6 | CDM | Deep-Lying Playmaker / Roaming |
| 7 | CM | Box-to-Box / Balanced (varsayılan) |
| 8 | CM | Box-to-Box / Balanced (varsayılan) |
| 9 | ST | Advanced Forward / Attack (varsayılan) |
| 10 | ST | Advanced Forward / Attack (varsayılan) |

#### T07 · 5-4-1 · Build-Up COUNTER · Hat 95 · **beklenen kod `xBN7H1R47f1G`**
| # | Mevki | Rol / Focus |
|---|---|---|
| 0 | GK | Goalkeeper / Balanced (varsayılan) |
| 1 | RB | Inverted Wingback / Build-Up |
| 2 | CB | Defender / Defend (varsayılan) |
| 3 | CB | Defender / Defend (varsayılan) |
| 4 | CB | Defender / Defend (varsayılan) |
| 5 | LB | Inverted Wingback / Attack |
| 6 | RM | Winger / Balanced (varsayılan) |
| 7 | CM | Box-to-Box / Balanced (varsayılan) |
| 8 | CM | Box-to-Box / Balanced (varsayılan) |
| 9 | LM | Winger / Balanced (varsayılan) |
| 10 | ST | Advanced Forward / Attack (varsayılan) |

#### T08 · 4-2-1-3 · Build-Up SHORT_PASSING · Hat 25 · **beklenen kod `MZoYdgYUW8gp`**
| # | Mevki | Rol / Focus |
|---|---|---|
| 0 | GK | Goalkeeper / Balanced (varsayılan) |
| 1 | RB | Inverted Wingback / Attack |
| 2 | CB | Defender / Defend (varsayılan) |
| 3 | CB | Defender / Defend (varsayılan) |
| 4 | LB | Fullback / Balanced (varsayılan) |
| 5 | CDM | Wide Half / Defend |
| 6 | CDM | Wide Half / Build-Up |
| 7 | CAM | Half-Winger / Roaming |
| 8 | RW | Winger / Versatile |
| 9 | ST | Advanced Forward / Attack (varsayılan) |
| 10 | LW | Winger / Versatile |

#### T09 · 4-2-1-3 · Build-Up BALANCED · Hat 50 · **beklenen kod `KZmUT8iSUomK`**
| # | Mevki | Rol / Focus |
|---|---|---|
| 0 | GK | Goalkeeper / Balanced (varsayılan) |
| 1 | RB | Fullback / Balanced (varsayılan) |
| 2 | CB | Defender / Defend (varsayılan) |
| 3 | CB | Defender / Defend (varsayılan) |
| 4 | LB | Fullback / Balanced (varsayılan) |
| 5 | CDM | Holding / Defend (varsayılan) |
| 6 | CDM | Holding / Defend (varsayılan) |
| 7 | CAM | Classic 10 / Wide |
| 8 | RW | Wide Playmaker / Attack |
| 9 | ST | Advanced Forward / Attack (varsayılan) |
| 10 | LW | Wide Playmaker / Attack |

#### T10 · 4-2-1-3 · Build-Up COUNTER · Hat 70 · **beklenen kod `fZ7ngV3np9bx`**
| # | Mevki | Rol / Focus |
|---|---|---|
| 0 | GK | Goalkeeper / Balanced (varsayılan) |
| 1 | RB | Fullback / Balanced (varsayılan) |
| 2 | CB | Defender / Defend (varsayılan) |
| 3 | CB | Defender / Defend (varsayılan) |
| 4 | LB | Fullback / Balanced (varsayılan) |
| 5 | CDM | Holding / Defend (varsayılan) |
| 6 | CDM | Holding / Defend (varsayılan) |
| 7 | CAM | Classic 10 / Versatile |
| 8 | RW | Wide Playmaker / Build-Up |
| 9 | ST | Advanced Forward / Attack (varsayılan) |
| 10 | LW | Wide Playmaker / Build-Up |


Not: T kodlarındaki hat/build-up değerleri (25/50/70/95, üç build-up) bilerek karıştırıldı. Hepsi doğrulanmış alanlar. Sorun çıkarsa rol/formasyon tablosundan gelir.

## 6. KABUL KRİTERLERİ
- `EaTacticCodecTest`: 64 EasySBC vektörü + 10 T kodu + Buğra'nın gönderdiği her oyun kodu encode ve decode olarak birebir.
- `TacticCodeRoundTripIntegrationTest`: her T kodu import → export → aynı kod (eşleme katmanı dahil).
- Rol seçicide oyunda olmayan kombinasyon seçilemiyor (UI testi). Export, oyunda olmayan girdi için 422 dönüyor.
- 5-3-2 export edilebiliyor, decode edilince `f532`.
- 107 replikanın `tacticCode`'u üretilip decode edilince seed'deki ayarlarla aynı (REP-03).
- `SYq8#eNaswmP` import edilince §2'deki 11 rol sahada doğru slotlarda.
