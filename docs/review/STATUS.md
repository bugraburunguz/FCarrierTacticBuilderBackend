# İnceleme paketi — görev durumu (2026-10-09)
Kural (00 §4.6): biten görev `DONE`, kısmi olan `PARTIAL`, başlanmayan `TODO`. Kararlar 08'dedir.

## Faz A — güvenlik + doğruluk
| ID | Durum | Not |
|---|---|---|
| SEC-01 | DONE | Stub ödeme yalnız `fcareer.billing.stub-enabled=true`; aksi halde `POST /billing/subscribe` 503, aktif abonelikte 409. Web'de buton yalnız `import.meta.env.DEV`. iyzico entegrasyonu `TODO` (anahtarlar gelince). |
| SEC-02 | DONE | `/fit/lineup`: atama + RoleFit% + rozet. `/recommend/slot-view`: ilk 3 aday, gerekçe/silah/scoutQuery yok (08 D-01). Depth ve setupDelta da ücretli içgörü sayıldı. |
| SEC-03 | DONE | `POST /players/{id}/development` careerId'de sahiplik şart. |
| SEC-04 | DONE | `owner_career_id` filtresi her aramada; `fitPlayers` ve `detail` başkasının kariyer-üretimi oyuncusunu 404 yapar. |
| SEC-05 | DONE (sapma) | Gövde başına 8M karakter, toplam 40M, 60MB Content-Length. Paket 2MB/25MB diyordu; gerçek yakalamada `players_meta.json` 3,2MB olduğundan sınırlar yükseltildi. |
| SEC-06 | DONE | Atomik Lua sayaç, `forward-headers-strategy: native`, auth uçları fail-closed, admin 10 hatada 15 dk kilit. |
| SEC-07 | DONE | `X-ML-Secret` (ml-service + backend istemcisi + compose); port yayımlanmıyor. |
| SEC-08 | PARTIAL | Login süresi sabit (dummy bcrypt). Register e-posta varlığını hâlâ söylüyor (e-posta doğrulaması olmadan genel mesaj mümkün değil); refresh token reuse/aile iptali `TODO`. |
| BUG-01 | DONE | Tek formül `floor(round(sum+excess)/n)`; backend `SquadRatingCalculator` + web `squadRating`, ortak vektörler `seed/ut-rules.fc27.json` (web kopyası `src/lib/utRules.fc27.json`). |
| BUG-02 | DONE (VERIFY) | Aynı gerçek oyuncu (katalog id / varlık id) kadroda tekrar seçilmez. Oyunun aynı oyuncunun iki kopyasına izin verip vermediği oyun içinde doğrulanmadı. |
| BUG-03 | DONE | Havuz budama (oyuncu başına en ucuz kopya, mevki başına ≤90, rating başına 3) + Web Worker; 3000 kartlık fixture < 1,5 sn testli. |
| BUG-04 | PARTIAL | Eşikler `utRules.fc27.json`'dan. Menajer slotunun arayüze bağlanması ve +1/+2 ayırt eden ek testler `TODO`. |
| BUG-05 | DONE | IndexedDB (Dexie) `bigStore`; eski localStorage kayıtları ilk açılışta taşınır; yazım hatası uyarı bandında görünür; sekmeler arası BroadcastChannel. |
| BUG-06 | DONE | raw/bodies hizası. |
| BUG-07 | DONE | CORS `PUT`. |
| BUG-08 | DONE | Oy upsert (`ON CONFLICT`), set bazlı toplam, `setId` doğrulaması. Materialized view `TODO`. |
| BUG-09 | TODO | Ingestion job/resume/backoff. |
| BUG-10 | DONE | Hata kodları HTTP status ile (400/401/402/403/404/409/422/429/503); web `client.ts` zaten `!res.ok` işliyor. |
| BUG-11 | TODO | `ea_id_suggestion` + onay akışı. |
| BUG-12 | DONE (VERIFY) | Yenileme saati Europe/London 18:00 (yaz TRT 20:00, kış 21:00); `dailyRefresh.verified=false`. |
| BUG-13 | DONE | Ağ hatasında oturum silinmez, Web Locks ile tek yenileyici, 204 toleransı. |
| BUG-14 | DONE | Mutlak URL'ler. |
| BUG-15..18 | TODO | P2. |
| OPS-01 | DONE | Backend ve web `CLAUDE.md`, `docs/review/`, `docs/DATA_POLICY.md` (yer tutucular Buğra'da). |
| OPS-02 | DONE | Backend `backend-test.yml`, web `ci.yml` (lint+test+build). Testcontainers Redis `TODO`. |
| OPS-03 | DONE | Non-root Docker (backend, ml), Swagger varsayılan kapalı. |
| TAC | PARTIAL | `patches/` regresyon testleri ve 64 vektör eklendi, geçiyor. TAC-01..06 (rol/focus eşleme teşhisi) kullanıcı test verisi bekliyor. |

## Faz B — tasarım sistemi (ilk dilim: D-07 onay ekranı)
| ID | Durum | Not |
|---|---|---|
| DS-01 | PARTIAL | `styles/matchday.css` token'ları; `.matchday` kapsamı UT Kadro Kurucu, Kadrom ve SBC çözücüde. Tüm uygulamaya yayma ve `emerald/slate` codemod'u onay sonrası. |
| DS-02 | DONE | Barlow Condensed / IBM Plex Sans / IBM Plex Mono self-host. |
| DS-04 | PARTIAL | `FitBadge`, `ChemDiamonds`; `VoteControl` ve kalan emoji temizliği `TODO`. |
| DS-05 / DS-11 | DONE | Tek `PlayerCard` (180×250 taban + `scale`); eski iki FutCard ve `lib/futCard.ts` silindi. `card_design.mode=EA_ASSET` arka plan adresi (`VITE_CARD_BG_BASE`) yapılandırılmadıkça OWN'a düşer. |
| DS-06 | PARTIAL | `Pitch` v2: çim şeritleri, kireç çizgileri, sürükle + klavye taşıma modu, paylaşılan kulüp/lig/ülke vurgusu. Bottom-sheet (mobil) ve rol okları `TODO`. Kimya çizgileri yapılmadı: FC27 kimyası sayıma dayalı, bağlantı çizgisi oyunda yok (VERIFY). |
| DS-12 | PARTIAL | `letterGrade`/`ratingTone` hazır; listelere bağlanması `TODO`. |
| UX-02 | PARTIAL | KPI bandı + yeni saha/kart. Slot seçilince "kimya Δ / rating Δ / fiyat" gösteren sağ panel, kart arkası (flip), bottom-sheet `TODO`. |
| D-04 | PARTIAL | `GET /api/v1/img/face/{id}` proxy'si (30 gün önbellek, bellekte LRU, takedown anahtarı). **FC27 portre adresi şu an 403 döndürüyor** (FC25 yolu 200); bu yüzden yüz yerine silüet çiziliyor. FC27 portre kaynağı bulunmalı. |

## Sonraki fazlar
Faz B'nin geri kalanı (DS-03/07/08/09/10, UX-01/03..06), Faz C (veri platformu), Faz D (UT v2 + Kariyer "Menajer Masası" CAR-20..33), Faz E (backlog), Faz F (maç simülasyonu): `TODO` — Buğra'nın D-07 ekran onayından sonra.
