# 03 · WEB BULGULARI (FCarrierTacticBuilderWeb@40ca49c)
`W/` = `src/`. Repo kopyasında gerçekten çalıştırıldı: `npm ci` OK · `npm test` 29 dosya / 130 test yeşil · `tsc -b` temiz · `npm run lint` 0 hata, 28 uyarı · `npm run build` OK (tek chunk uyarısı).
Her madde: **Sorun → Düzeltme → Kabul**.

---
## P0
### WEB-BUG-01 · Bir kariyerin ayarları başka kariyere yazılabiliyor
`W/pages/CareerDeskPage.tsx:152` (`<DeskSettings career={career} />`), `:175-183`
- **Sorun:** `DeskSettings`'in `key`'i yok. State'i (spoiler, maaş bütçesi, dönem sonu, challenge) `useState(career.x)` ile yalnız ilk render'da alınıyor. CareerSelect ile kariyer değişince eski değerler kalıyor, "Kaydet" A'nın değerlerini B'ye PATCH'liyor.
- **Düzeltme:** `<DeskSettings key={career.id} career={career} />`, içindeki `MoneyInput`'a da `key={career.id}`.
- **Kabul:** Kariyer değiştirince form seçili kariyerin değerlerini gösterir, kaydetmek yalnız onu değiştirir. RTL testi.

---
## P1
| ID | Yer | Sorun | Düzeltme | Kabul |
|---|---|---|---|---|
| WEB-BUG-02 | `W/components/TraditionalSbc.tsx:82-90,98` | `min = Number(rating)\|\|0` → `ovr_max = min+6 = 6`. Rating şartı olmayan (yalnız kimya/sayı/lig) SBC'de market havuzu 0 oyuncu | `min===0` ise `ovr_max` kalite tavanından (64/74/99), `ovr_min=max(45, kaliteTabanı)`, `sort=overall asc` | Yalnız "kimya ≥ 20" şartlı challenge uygulanabilir kadro döner |
| WEB-BUG-03 | `W/lib/sbcChallenge.ts:98-104` | QUALITY MAX/EXACT yalnız `notes`'a ekleniyor; "Max Gümüş" upgrade SBC'sinde altın seçilebiliyor | `SbcConstraints`'e `maxQuality`, `exactQuality`; `prunePool` ve `extraIssues`'da filtre; market `ovr_max` (64/74) | "Max Silver" çözümünde ≥75 kart yok (unit test) |
| WEB-BUG-04 | `W/lib/kbSbc.ts:13`, `W/pages/UtSbcChallengePage.tsx:62` | KB `ITEM_SCORE` challenge `type:'ONE_CLICK'`'e eşleniyor ama sayfa her zaman `TraditionalSbc` + `autoRun` render ediyor | `c.kind==='ITEM_SCORE'` dalı → streamlined çözücü, `pointsRequired` dolu | Item-score set açınca beam çözücü hiç çalışmaz |
| WEB-BUG-05 | `W/components/ui.tsx:91` | `ErrorBox` mesajı yalnız `ApiError` için gösteriyor; extension (`ExtensionSync.tsx:21,28`), worker, JSON/restore hataları "Beklenmeyen bir hata" | Her `Error` için `error.message`; `TypeError: Failed to fetch` → ağ metni | Extension yok durumunda Türkçe sebep görünür |
| WEB-BUG-06 | `W/pages/CareerTransferPage.tsx:25` | `MID: roleId 'box_to_box_support'` yok (Box-to-Box = Balanced/Ball-Winning). Backend `getFitRole` NotFound → orta saha derinlik ihtiyacı hata verir | `box_to_box_balanced`, ya da formasyonun `defaultRole`'ünden türet. Sabit roleId'leri `/roles`'a karşı doğrulayan test | Transfer merkezi MID ihtiyacı yüklenir |
| WEB-BUG-07 | `W/components/Layout.tsx:98-102` | Mod yalnız `/ut/`'de `ut`'a geçiyor, hiç geri dönmüyor. UT modundayken `/career/desk` UT menüsüyle açılıyor | Mod = URL öneki (`/ut` → ut; `/career`, `/squad`, ... → career); yalnız son seçim saklanır | `/career/desk`'e doğrudan gidince kariyer menüsü |
| WEB-BUG-08 | `W/lib/utCard.ts:39`, `W/pages/UtSquadPage.tsx:323` | Aynı oyuncunun iki versiyonu (TOTW + altın, ya da katalog + kulüp) XI'e girebiliyor | `usedPlayers` = `assetId ?? playerId` (çözücünün `playerKey`'i gibi); XI'deki oyuncunun satırlarını disable et | TOTW ve altın Mbappé birlikte konamaz |
| WEB-BUG-09 | `W/components/MoneyInput.tsx:12-15,32,45` | `replace(',','.')` yalnız ilk virgül; "1.500.000", "1 500", "15m", "2,5 milyon" reddediliyor, `Number()` "0x10"/"1e3" kabul ediyor. State yalnız `useState(init)`. Birim değişimi anında `onCommit` → kısa listede tavan ×1000 kaydediliyor (`CareerTransferPage.tsx:159`) | `parseMoney()`: tr-TR ayraçları, K/M/bin/milyon ekleri, hex/üs reddi; odak dışındayken prop'tan resync; birim değişiminde commit yok | Her durum için unit test |
| WEB-BUG-10 | `W/components/NewCareerForm.tsx:40` | `key={name+club.id}` → ad alanında her tuşta MoneyInput remount, birim ve metin sıfırlanıyor | Yalnız create sonrası değişen reset sayacı | Ad yazarken bütçe korunur |
| WEB-BUG-11 | `W/components/pitch/Pitch.tsx:243` | Rol çipi yalnız `slot.player` varken render; boş slota rol/focus seçilemiyor | Boş slotta da çip | Boş slotta rol seçilir |
| WEB-BUG-12 | `W/components/PlayerAvatar.tsx:19` | Göreli `/api/v1/img/face/{id}`; API ayrı origin'deyken (GitHub Pages) kırık | `assetUrl`'i lib'e taşı, burada kullan | Pages build'inde avatar yüklenir |
| WEB-BUG-13 | `W/pages/CareerTransferPage.tsx:157`, `W/api/types.ts:397-402` | Kısa listede "Oyuncu #123": `ShortlistItem`'da oyuncu alanı yok | Backend gömülü PlayerSummary dönsün (ad, ovr, pozisyon, değer, kulüp) — `API CONTRACT CHANGE` | Satırda oyuncu adı |
| WEB-BUG-14 | `W/pages/UtImportPage.tsx:~49-53` | Her senkron `utCardStore`/`utCaptureStore`'u topyekûn değiştiriyor; yalnız SBC sayfası içeren capture `cards=[]` → kulüp ve aktif kadro siliniyor (`VERIFY` ile gerçek akışta) | Bölüm bazlı birleştir, yalnız gelen bölümleri (club, squad, sbc, evo) bölüm başına zaman damgasıyla yaz | SBC-only senkron kulübü silmez |
| WEB-BUG-25 | `public/fc27_career_export.lua` | Son kullanıcıya "Bana 'bitti' yaz" diyor (109-110), "projenin içindeki klasör" (1-5). ImportPage "Dışa aktarım bitti" penceresi bekliyor (`ImportPage.tsx:24`), script "Base disa aktarim bitti" yazıyor. Kariyer bağlamı yok (tarih, kullanıcı takımı, kontrat) | 07'deki export v2 ile değiştir; tek kaynak dosya (backend `tools/live-editor/` ile birebir 3 kopya var) | ImportPage metni ile script penceresi aynı |

## P2
| ID | Yer | Sorun → Düzeltme |
|---|---|---|
| WEB-BUG-15 | `KbSbcList.tsx:97`, `kbSbc.ts:14` | `repeatable` INT; 0 dahil her non-null tekrarlanabilir görünüyor → `(repeatable ?? 0) !== 0` ve -1 = sınırsız |
| WEB-BUG-16 | `DeleteCareerButton.tsx:13` | Aktif olmayan kariyeri silmek de aktif kariyeri sıfırlıyor → yalnız `careerStore.get()===career.id` ise |
| WEB-BUG-17 | `CareerDeskPage.tsx:183,160` | `windowEnds\|\|undefined` → tarih temizlenemiyor (`null` gönder); zaman çizelgesi ham `SELL`/`LOAN_OUT` (Türkçe etiket haritası) |
| WEB-BUG-18 | `capture.ts:25-40` | "Assist 3 goals" GOALS'a, "Win N" PLAY_MATCHES'e düşüyor → önce assist, `WIN_MATCHES` ekle. Daha iyisi: backend'in normalize ettiği objective tipini kullan |
| WEB-BUG-19 | `client.ts:69-77` | Refresh 429 → çıkış. Yalnız 400/401/403 `invalid`; 429 `network` + backoff |
| WEB-BUG-20 | `themeStore.ts:23-26` | "Sistem" teması OS değişimini dinlemiyor → `matchMedia(...).addEventListener('change')` |
| WEB-BUG-21 | `CareerGrowthPage.tsx:95`, `SquadTagsView.tsx:39` | `text-warn` token'ı yok → `--color-warn` ekle |
| WEB-BUG-22 | `ui.tsx:192-220` | Modal effect `[onClose]`'a bağlı, satır içi `onClose` her parent render'da ilk alana odaklıyor → `onClose` ref'te, deps `[]` |
| WEB-BUG-23 | `TraditionalSbc.tsx:73`, `UtSquadPage.tsx:495`, `UtClubPage.tsx:29` | `useMemo(() => utCardStore.get(), [])` hiç güncellenmiyor → `useUtCards()` = `useSyncExternalStore(bigStore.subscribe)` |
| WEB-BUG-24 | `sbcTraditional.ts:301-304,233,255` | Ceza düz ortalamayla (correction factor yok) → fazla ödeme (ort. 83.5 XI 84 olabilir). `chemMin=0` iken bile her slota pozisyona uygun kart zorlanıyor (SBC'de pozisyon yalnız kimyayı etkiler) → `teamRating(picks+projection)` ile ceza; `chemMin===0` ise tek "herhangi pozisyon" aday listesi |
| WEB-BUG-26 | `ImportPage.tsx:58,68`, `CareerDeskPage.tsx:32` | Import yalnız `squad`'ı invalidate ediyor (masa/gelişim bayat); lineup key `squad.length` → sat+al'da satılan görünüyor → ortak `invalidateCareer(id)`, kadro hash'i key |
| WEB-BUG-27 | `UtSquadPage.tsx:487-489` | Evaluate hata verirse `CurrentCard` sonsuz spinner → hata/boş durum |
| WEB-BUG-28 | `chemistry.ts:69-75,85-88` | Kulüp/lig/ülke anahtarı görünen ad → "Nacional" (POR) ile "Nacional" (URU) bağlanır; menajer `undefined===undefined` eşleşmesi → id ile anahtarla, undefined guard |

---
## UX
| ID | Pri | Sorun | Düzeltme |
|---|---|---|---|
| WEB-UX-01 | P1 | UT "Kart veritabanı" `/players`'ı (kariyer kataloğu, Potential*) açıyor (`Layout.tsx:63`). UT kartı, fiyat, promo yok | `/ut/players` (`kbItems` üstünde: yapışkan tablo, PlayerCard ızgara geçişi, rarity/promo/lig/ülke/kulüp/PS filtresi, fiyat kolonu) ve `/ut/players/:assetId` (versiyon şeridi + `kbPrices` grafiği). Tasarım 08 §2, veri 06 §5 |
| WEB-UX-02 | P1 | Oyuncu sayfası kariyer merkezli (`PlayerDetailPage.tsx:31,78-80`), PlayerCard yok, UT modunda Potential* görünüyor, rol seçici hep RW Winger Attack | `lg` PlayerCard; varsayılan rol = oyuncunun ana pozisyonunun ilk rolü (04 §4 sırası); UT modunda potansiyeli gizle, versiyon + fiyat göster |
| WEB-UX-03 | P1 | UT kurucuda yedek ve menajer yok (`UtSquadPage.tsx:107-118,201`) | 7 yedek + rezerv + menajer seçici (lig/ülke arama). Rating XI+yedek (18), kimya menajeri alır. **Kabul:** menajeri değiştirince kimya anında güncellenir |
| WEB-UX-04 | P1 | Bölüm sekmeleri telefonda taşıyor (`Layout.tsx:243`), 5 sekme 375px'ten geniş | `overflow-x-auto whitespace-nowrap` ya da mobilde select |
| WEB-UX-05 | P1 | Ana sayfa ve marka yalnız kariyer (`HomePage.tsx:4-9`, `Layout.tsx:109-111`, `index.html` title). UT modunda "FC Kariyer" | Mod başına ana sayfa (UX-01) ve marka (DS-10) |
| WEB-UX-06 | P1 | Kart seçici düz metin listesi (`UtSquadPage.tsx:494-565`): PlayerCard yok, kimya/rating/fiyat Δ yok (UT-04), lig/ülke/kulüp filtresi yok (UT-05) | `md` kart ızgarası, Δkimya/ΔOVR/fiyat worker'da |
| WEB-UX-07 | P2 | Taktik kontrolleri oyunla uyuşmuyor (`TeamSetup.tsx:52-58`, `UtSquadPage.tsx:27,248,255`): slider 0-100 (oyun 1-100), etiketler eşit aralıklı (bantlar 30/30/30/10), Defensive Approach seçici yok, "Short" | Defensive Approach seçici + slider ilgili bantta, varsayılan 25/50/70/95 (04 RULE-06); "Kısa Pas" |
| WEB-UX-08 | P2 | Kullanıcı extension ID'sini elle yazıyor (`ExtensionSync.tsx:56-58`) | `VITE_EXTENSION_ID` + otomatik ping; alan yalnız gelişmiş fallback |
| WEB-UX-09 | P2 | Bayat yardım metni: `TraditionalSbc.tsx:166` "fiyat verisi yok… kulüp havuzu yok" (ikisi de yanlış), `UtSbcPage.tsx:77,163,201`, `UtSquadPage.tsx:276` dosya yüklemeden bahsediyor | Metinleri güncel akışa göre yaz, paragrafları `HelpPopover`'a taşı |
| WEB-UX-10 | P2 | Sahada dokunmatik: sil butonu yalnız hover'da, dokunmada hover-dim kalıyor (`Pitch.tsx:213-216,237`) | Uzun basma menüsü / bottom sheet, `pointerup`'ta sıfırla |
| WEB-UX-11 | P2 | XI gereksinimleri serbest metin (`TraditionalSbc.tsx:184`: `lig:Premier League=1; …`) | Lig/ülke/kulüp autocomplete'li çip seçiciler |
| WEB-UX-12 | P2 (gizlilik) | Logout bigStore, `fc.utSquad`, `fc.sbcPool`, `fc.career`, `fc.tactic`'i temizlemiyor (`AuthContext.tsx:42-53`); aynı tarayıcıda sonraki kullanıcı önceki kulübü görür | Depolamayı user id ile namespace'le, logout'ta temizle |

## Tasarım sistemi
| ID | Pri | Sorun | Düzeltme |
|---|---|---|---|
| WEB-DS-01 | P1 | Kart metni çok küçük (`PlayerCard.tsx:108,145,156`): xs'de pozisyon 6.3px, md'de stat etiketi 5.5px, fiyat `text-[10px]` (02 kuralı: 11px altı yok) | Boyut başına düzen (xs: rating+pozisyon, font clamp ≥ 11/scale; md: etiketsiz değerler; lg: tam). EA render modunda (06) bu sorun base kartta ortadan kalkar |
| WEB-DS-02 | P1 | Ad son kelimeye kırpılıyor (`PlayerCard.tsx:65`): "Vinícius Jr." → "JR.", "De Bruyne" → "BRUYNE", "Van Dijk" → "DIJK" | Backend `commonName`/`cardName` (EA ratings `commonName`), fallback ekleri koruyan kural (de/van/da/dos/el, Jr.) |
| WEB-DS-03 | P1 | Ülke/kulüp/lig 3 harf metin ("PRE", "SPA") (`PlayerCard.tsx:66,111-115`, `cardDesign.ts:67-69`) | Bayrak seti (MIT) + kulüp/lig rozeti proxy'si (06 §3, D-04) |
| WEB-DS-04 | P1 | Kart EA'ya benzemiyor (`cardDesign.ts:28-31,59-64`): 7 jenerik tier, build-time bayrak, arka plan `${tier}.png` | 06 §3: base kartta EA render, promo'da kendi çerçeve + portre; mod runtime `/ut/rules.cardDesign.mode`'dan |
| WEB-DS-05 | P1 | Kopya/ölü bileşen: iki Modal (`ui.tsx:189`, `Modal.tsx`), `Kpi` = `Stat` (`UtSquadPage.tsx:567` vs `ui.tsx:23`), `PANEL`/`PANEL_TITLE` = `Card` (`UtSquadPage.tsx:39-40`), ölü `PitchView.tsx` (hex), ölü `PositionCard` bileşeni (slate/teal) | Ölüleri sil, tek `Dialog` (Radix, D-06) |
| WEB-DS-06 | P2 | Eski tone adları ve hex (`ui.tsx:100`; `PlayersPage.tsx:276-289` `#4da6ff`, `#1f9d63`; FitMeter, AttributeRadar, SquadDensity, PositionOverallPitch, PlayerDiffPanel) | Codemod: tone → `good\|info\|warn\|bad\|neutral`, hex → token |
| WEB-DS-07 | P2 | Glif durum ikonları (↑↓↕⇄ `PlayerDiffPanel.tsx:11-14`, ▲ `PositionOverallPitch.tsx:49`, ━┅ `CareerGrowthPage.tsx:94`, × `Pitch.tsx:239`, `CompareTray.tsx:39`, `ComparePage.tsx:66`) | lucide ikonları + aria-label |
| WEB-DS-08 | P2 | `HelpPopover` (`ui.tsx:176-186`) yorumu Esc ile kapanır diyor, `<details>` kapanmıyor, dış tık da kapatmıyor | Radix Popover |
| WEB-DS-09 | P2 | Saha çizgileri yanlış (`Pitch.tsx:170-173`): kale alanı 220 genişlik (183 olmalı), ceza yayının kirişi (184) 2r'den (182) uzun → D bozuk | Gerçek oranlar: 105×68, ceza alanı 40.32×16.5, kale alanı 18.32×5.5, nokta 11, yay r=9.15 |
| WEB-DS-10 | P2 (a11y) | `role=button` div içinde `<button>` (`Pitch.tsx:194-241`); tabpanel'siz `role=tab`, ok tuşu yok (`UtSquadPage.tsx:521-534`, UtClubPage, KbSbcList); `Modal.tsx:21` önce X'e odaklanıyor, scroll kilidi yok | Radix Tabs/Dialog, iç içe etkileşimli öğe yok |
| WEB-DS-11 | P2 | `ratingTone`/`letterGrade` kullanılmıyor → statlar renklenmiyor | Stat tablolarında ve kart arkasında kullan |

## Mimari / performans
| ID | Pri | Sorun | Düzeltme |
|---|---|---|---|
| WEB-ARCH-01 | P1 | D-08 rota tablosu yapılmamış | `/career/{squad,builder,wizard,recommend,scouting,teams}`, `/ut/{players,players/:id,…}`, ortak `/players?mode=`; `<Navigate>` yönlendirmeleri; çift yönlendirme `/fit` → `/tactics/builder` → `/squad/builder`'ı kaldır (`App.tsx:57,62`) |
| WEB-ARCH-02 | P1 | Oyun kuralları build'e gömülü (`utRules.fc27.json`, `coinPrice.ts:6-9`, `chemistry.ts:32`) | `useUtRules()` = `GET /ut/rules` (ETag), paketli kopya fallback; kart modu da buradan |
| WEB-ARCH-03 | P2 | IndexedDB migration/timeout yok (`bigStore.ts:5,45-48`, `main.tsx:16`): render `hydrateBigStores()`'u zaman aşımsız bekliyor | Versiyon kontrolü + migrator'lar; `Promise.race(3s)` |
| WEB-ARCH-04 | P2 | Küçük state'te sekmeler arası senkron yok (careerStore, token, tacticStore, utSquadStore) | `storage` event dinleyicisi |
| WEB-ARCH-05 | P2 | Kredi maliyeti sabit ("8 kredi" `AutoFitPanel.tsx:93`, "3 kredi" `RecommendPage.tsx:49`), `ErrorCodes.premiumRequired` işlenmiyor | Maliyet `/me` ya da config'ten; tek 402 handler → kredi/PREMIUM dialog (D-12) |
| WEB-ARCH-06 | P2 | CLAUDE.md gereksinimleri yok: Playwright, axe, Sentry (D-13). CLAUDE.md'deki token adları (`bg-panel`, `text-sub`) gerçekte yok (gerçek: surface/muted) | CLAUDE.md'yi gerçek token adlarıyla düzelt; Playwright görsel snapshot + axe CI |
| WEB-ARCH-07 | P2 | nginx.conf'ta CSP, frame, referrer başlıkları ve `X-Forwarded-Proto` yok | Başlıkları ekle; CSP `img-src` EA görsel hostunu değil kendi proxy'ni içersin |
| WEB-PERF-01 | P1 | Tek 775 KB chunk (gzip 234 KB): 28 sayfa eager import (`App.tsx:5-31`) | Rota başına `React.lazy` + `manualChunks` (react, query, dexie, lucide). **Kabul:** ilk JS < 250 KB raw |
| WEB-PERF-02 | P2 | Çözücü worker iptal edilemiyor (`solveAsync.ts:9-19`), her çözümde yeni Worker, sonuç yarışı | AbortSignal → terminate; tek worker yeniden kullan |
| WEB-PERF-03 | P2 | Her çözümde pozisyon × (1 + lig + ülke + fromAny) `/players` çağrısı (30-40), `/leagues` ve `/nationalities` her seferinde yeniden | `queryClient.fetchQuery` + uzun `staleTime`, ya da tek backend `/sbc/pool` ucu |
| WEB-PERF-04 | P2 | `matchday.css:6-15` latin-ext'i iki kez import ediyor | Tek import |

---
## Oyun kuralı uyuşmazlıkları (web)
| Yer | Şu an | Doğru |
|---|---|---|
| `W/lib/squadBuilder.ts:202-206`, `W/lib/eaRoles.ts:18,100-102` | `/tactics/ea-roles` yüklenmeden ya da hata verince (`retry:false`) focus listeleri pozisyonlar arası birleşik gösteriliyor: CM Holding'e Roaming, CM DLP'ye Roaming, CM Playmaker'a Balanced/Build-Up, CAM Playmaker'a Attack, CM Half Winger'a Roaming, CAM Half Winger'a Support, RM/LM Winger'a Versatile, RM/LM Inside Forward'a Roaming | Pozisyon bazlı liste (04 §4). `/roles` pozisyon anahtarlı olsun ya da seçici ea-roles yüklenene kadar kilitli |
| `CareerTransferPage.tsx:25` | `box_to_box_support` | Box-to-Box = Balanced / Ball-Winning |
| `UtSquadPage.tsx:201`; `utRules.fc27.json:7` vs `squadRating.ts:6` | UT rating yalnız XI; config 11, kod 18 | Kadro ekranı 18 (XI+7), SBC 11 (`sbcTraditional.ts:148` doğru) |
| `UtSquadPage.tsx:323`, `TraditionalSbc.tsx:108` | Aynı oyuncu iki kez; kulüp kartı fallback'i `n:name\|rating` farklı rating'li iki versiyonu farklı oyuncu sayıyor | Oyuncu başına bir (assetId) |
| `sbcChallenge.ts:98-104` | MAX/EXACT kalite yok sayılıyor | Bronz ≤64, gümüş 65-74, altın ≥75 |
| `sbcTraditional.ts:233,255` | Her slota pozisyon zorunlu | SBC'de pozisyon yalnız kimyayı etkiler |
| `chemistry.ts:69,88` | Kadın/erkek kulüp **adıyla** bağlanıyor; menajer undefined eşleşmesi; menajer bağlı değil | Bağlı kulüp id + ülke, lig değil; menajer +1 (ülke veya lig), oyuncu başına max 1, tavan 3 |
| `chemistry.test.ts:39` | Test başlığı "ülkeye +2" (Icon) | Kod doğru (+1), başlığı düzelt |
| `sbcText.ts:14-21` | Yalnız "Nation" metni; TOTW/rarity gereksinimleri yok | "Countries/Regions" (`VERIFY`), rarity gereksinim türleri (05 §3.2) |
| `kbSbc.ts:13` | ITEM_SCORE = one-click | Streamlined skor çözücü |
| `EaTacticCode.tsx:64` | Placeholder "12 karakter", maxLength 16 | **12 doğru** (repodaki 64 gerçek kod 12 karakter). Rehberdeki "11" yanlış. maxLength 12, istemcide karakter seti doğrulaması (`A-Za-z0-9` + `?@#` — codec alfabesini `EaTacticCodec.java`'dan al) |
| `TeamSetup.tsx:52`, `UtSquadPage.tsx:255` | Slider 0-100, approach yok | 1-100; Deep 1-30 (25), Balanced 31-60 (50), High 61-90 (70), Aggressive 91-100 (95) |
| `tacticStore.ts` | Tek taktik | Oyunda 5 özel taktik slotu (FEAT-U07) |
| `capture.ts:25-40` | "Win" = PLAY_MATCHES | Ayrı WIN |
| Formasyon sayısı (29), 18:00 UK, kalite eşikleri | — | Doğru |

---
## Sayfa envanteri
| Yol | Bileşen | Durum |
|---|---|---|
| `/` | HomePage | Çalışıyor; yalnız kariyer |
| `/login`, `/register` | AuthPages | Çalışıyor |
| `/players`, `/players/:id` | PlayersPage, PlayerDetailPage | Çalışıyor; kariyer verisi, yanlışlıkla UT kart DB olarak kullanılıyor |
| `/compare`, `/wonderkids` | ComparePage, WonderkidsPage | Çalışıyor |
| `/tactics/builder`, `/fit` | Navigate | Yönlendirme (çift) |
| `/tactics/wizard` | WizardPage | Çalışıyor |
| `/squad` (auth), `/squad/builder` | SquadPage, SquadBuilderPage | Çalışıyor |
| `/scouting`, `/recommend`, `/teams` (auth) | ScoutingPage, RecommendPage, TeamsPage | Çalışıyor |
| `/career/import`, `/career/new`, `/career/growth` | ImportPage, CareerNewPage, CareerGrowthPage | Çalışıyor (BUG-10, 25, 26) |
| `/career/desk` | CareerDeskPage | **P0** (BUG-01) |
| `/career/transfer` | CareerTransferPage | Kısmen kırık (BUG-06, 13) |
| `/ut/squad` | UtSquadPage | Yedek/menajer yok |
| `/ut/meta` | UtMetaPage | Tahmini veri |
| `/ut/sbc` | UtSbcPage | Çalışıyor |
| `/ut/sbc/:setId[/:challengeId]` | UtSbcChallengePage | Kısmen kırık (BUG-02, 03, 04) |
| `/ut/objectives`, `/ut/club`, `/ut/import` | … | Çalışıyor (BUG-14) |
| `/ut/evo` | UtEvolutionsPage | Yalnız liste |
| `/admin/ut` | AdminUtPage | İstemci guard yok, anahtar sessionStorage'da |
| `/profile` | ProfilePage | Çalışıyor |
| `/ut/players`, `/ut/players/:id`, D-08 `/career/*` hedefleri | — | **Yok** |

Lint uyarıları (28): 7 no-unused-expressions (`sbcChallenge.ts:81-96`, `sbcText.ts:49`), 9 exhaustive-deps (`CareerDeskPage.tsx:45-49`, `CareerGrowthPage.tsx:49`, `UtSquadPage.tsx:118`), 3 set-state-in-effect (`Layout.tsx:90`, `SquadPage.tsx:52`, `UtSquadPage.tsx:169`), 2 preserve-manual-memoization, 6 only-export-components, 1 refs-in-render (`Pitch.tsx:69`). Hedef: 0 uyarı, CI'da `--deny-warnings`.
Test edilmeyenler: MoneyInput, PlayerCard/Pitch render, bigStore, çok sekmeli token yenileme ve 429, TraditionalSbc havuz kurma (rating-0), masa kariyer değişimi, rota guard'ları, Lua script, görsel/axe.
