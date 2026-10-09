# 12 · EXTENSION İNCELEMESİ — "FCareer Club Sync" v0.2
Dosyalar: `manifest.json` (MV3), `inject.js` (sayfa içi fetch/XHR gözlemcisi), `content.js` (köprü), `background.js` (tampon + indir/gönder), `popup.*`.
Genel karar: Yaklaşım doğru ve EasySBC ile aynı model (EasySBC de kulüp içe aktarmayı Chrome eklentisiyle yapıyor; 11 §5.6). Salt-okuma, kimlik bilgisi yok, EA'ya kendi isteği yok — korunmalı. Ama **"FCareer'a gönder" butonu bugün çalışmıyor**; birkaç veri kaybı/çökme riski ve katkı modu için eksikler var.

## 1. DOĞRULANMIŞ SORUNLAR
| ID | Pri | Sorun | Yer | Düzeltme |
|---|---|---|---|---|
| EXT-01 | P0 | **Gönder butonu çalışmaz.** `fetch(.../api/v1/ut/capture)` `Authorization` başlığı göndermiyor. Backend'de `/ut/capture` public değil (`SecurityConfig` PUBLIC_POST listesinde yok) → 401. Ayrıca gövde `link_token` gönderiyor; backend record alanı `linkToken` (camelCase) ve hiç kullanılmıyor. | `background.js` FC_SEND · BE `ut/model/UtCaptureModels.java:22` | **Tek-kullanımlık bağlama akışı** (§2) |
| EXT-02 | P1 | **Yarış durumu, capture kaybı:** her `FC_CAPTURE` mesajı `get()` → push → `set()` yapıyor; MV3 service worker'da eşzamanlı mesajlar birbirinin yazdığını ezer. | `background.js` | Bellekte kuyruk + 500 ms'de bir toplu yazım, ya da IndexedDB ile append |
| EXT-03 | P1 | **İndirmede bellek patlaması:** 400 × 5 MB'a kadar gövde → `btoa(unescape(encodeURIComponent(JSON.stringify(...))))` ile data URL. Yüzlerce MB string → service worker çöker. | `background.js` FC_EXPORT | `Blob` + `URL.createObjectURL` (offscreen document) ya da gzip'li parça parça indirme; tampon üst sınırı byte bazlı (ör. 100 MB) |
| EXT-04 | P1 | **Deny listeleri uyumsuz:** extension `DENY` = `/auth, accountinfo, connect, nucleus, phishing`; backend ayrıca `/fc/user/season` ve `sid=` atıyor. `/user/credits` (coin bakiyesi), `/tradepile`, `/watchlist` kişisel veri ve her zaman yakalanıyor. | `inject.js` MATCH/DENY | Tek kaynaklı `capture-rules.json` (backend `GET /ut/capture/rules` sunar, extension açılışta çeker); kişisel uçlar yalnız "Kişisel" modda |
| EXT-05 | P2 | **Mesaj sahteciliği:** `content.js` sayfadaki herhangi bir script'in `postMessage({__fcareer:true,…})` mesajını capture sayar. Kişisel modda zararsız; **katkı modunda** global veriyi zehirleme yolu. | `content.js` | inject ↔ content arasında yüklemede üretilen rastgele nonce; katkılarda sunucu tarafı konsensüs zaten şart (03 §3) |
| EXT-06 | P2 | Keşif yalnız API yollarını topluyor; kart arka planı/portre görsel yollarını görmüyor (11 §3 için gerekli). | `inject.js` discover | `performance.getEntriesByType('resource')` ile `.png/.webp` yol **kalıplarını** (gövde değil) `fcareer://discovery-assets` olarak raporla |
| EXT-07 | P2 | `popup.js` `alert()` kullanıyor; site adresi ve token elle giriliyor. | popup | §2 akışıyla gereksizleşir; durum metni popup içinde |
| EXT-08 | P2 | README "yakala modu, şema bilinmiyor" diyor ama backend parser'ları yazılmış; `docs/ut_capture_mapping.md` yok. | README | Mapping dokümanını yaz (DATA-11) |

## 2. YENİ AKIŞ — dosyasız, tek tık (EasySBC modeli)
```
Site /ut/import  →  "Kulübümü senkronla" butonu
  1. Backend: POST /ut/capture/link  (JWT'li)  → { linkToken (tek kullanımlık, 15 dk, kullanıcıya bağlı), mode: PERSONAL|CONTRIB }
  2. Site → extension: chrome.runtime.sendMessage(EXT_ID, {type:'FC_LINK', token, apiBase})   ← manifest "externally_connectable": {"matches":["https://<site>/*"]}
  3. Extension EA Web App sekmesini açar (ya da kullanıcı açık sekmeye geçer), kullanıcı Club/SBC/Objectives'e girer
  4. Extension yakalananları parça parça POST /ut/capture  (Header: X-Link-Token)   ← JWT extension'da hiç tutulmaz
  5. Backend token'ı doğrular → kullanıcıya bağlar → işler; site WebSocket/SSE veya polling ile "X kart, Y SBC alındı" gösterir
```
- `/ut/capture` güvenlik: `X-Link-Token` ya da JWT; token tek kullanımlık değil, **oturumluk** (15 dk içinde çok parça).
- Kişisel veri yine sunucuda **kalıcılaşmaz** (09 §4); sonuç istemciye döner ve IndexedDB'ye yazılır. Katkı modunda (D-20 katkıcılar) yalnız global tanımlar ve fiyatlar `contrib_*` hattına girer.
- Chrome Web Store'a çıkacaksa: izinler minimum (`storage`, `downloads` gerekmezse kaldır), gizlilik politikası linki (09), "read-only" açıklaması.

## 3. KATKI MODU (D-20) — extension tarafı
| ID | Pri | Görev |
|---|---|---|
| EXT-09 | P1 | Popup'ta mod anahtarı: Kişisel / Katkı. Katkı yalnız backend `CONTRIBUTOR` bayrağı olan kullanıcıya açılır (link yanıtında `mode`). |
| EXT-10 | P1 | Katkı modunda MATCH listesi yalnız global tanımlar: `/sbs/sets`, `/sbs/setguide`, `/sbs/challenge`, `players.json`, `players_meta`, `chemistry/`, `itemraritytunables`, `squaddata.json`, objective tanımları, evolution tanımları, transfer market **fiyat** sonuçları. `/club`, `/user/credits`, `/tradepile`, `/watchlist` **hariç**. |
| EXT-11 | P2 | İstemci tarafı temizlik: market yanıtlarından satıcı/alıcı kimliği, `tradeId` gibi alanları göndermeden önce sil (sunucu da siler; çift güvenlik). |
| EXT-12 | P2 | Sürümleme: extension `version` + `capture-rules` sürümü her gönderimde başlıkta; backend eski sürümü reddedip güncelleme ister. |

## 4. TEST
- Birim: `inject.js` eşleme fonksiyonlarını modül olarak ayırıp (`rules.js`) Vitest ile test et (MATCH/DENY tabloları).
- E2E: Playwright + yerel sahte "web-app" sayfası (gerçek EA'ya bağlanmadan) → fetch yanıtları taklit edilir, extension yüklü Chromium ile capture/gönderim doğrulanır.
