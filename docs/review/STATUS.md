# İnceleme paketi — görev durumu (2026-10-09)
Kural (00 §4.6): biten görev `DONE`, kısmi olan `PARTIAL`, başlanmayan `TODO`. Kararlar 08'dedir.

## Faz A — güvenlik + doğruluk
| ID | Durum | Not |
|---|---|---|
| SEC-01 | DONE | Stub ödeme yalnız `fcareer.billing.stub-enabled=true`; aksi halde `POST /billing/subscribe` 503, aktif abonelikte 409. Web'de buton yalnız `import.meta.env.DEV`. iyzico entegrasyonu `TODO` (anahtarlar gelince). |
| SEC-02 | PARTIAL | → BE-SEC-01 (P0): anonim `/recommend/preview` tam sıralamayı döndürüyor. |
| SEC-03 | DONE | `POST /players/{id}/development` careerId'de sahiplik şart. |
| SEC-04 | DONE | `owner_career_id` filtresi her aramada; `fitPlayers` ve `detail` başkasının kariyer-üretimi oyuncusunu 404 yapar. |
| SEC-05 | PARTIAL | → BE-SEC-02: yalnız Content-Length; chunked ve `/ut/contrib/capture` sınırsız. |
| SEC-06 | PARTIAL | → BE-SEC-07: Redis kapalıyken admin kilidi fail-open. |
| SEC-07 | PARTIAL | → BE-ARCH-06: ml-service secret tanımsızsa herkesi kabul ediyor. |
| SEC-08 | PARTIAL | → BE-SEC-08: reuse tespiti + aile iptali yapılmış (STALE düzeltildi); logout token replay kenar durumu açık. |
| BUG-01 | PARTIAL | → BE-BUG-04, RULE-02: `/ut/squad/evaluate` düz ortalama; kural dosyası size:11, web 18. |
| BUG-02 | DONE (VERIFY) | Aynı gerçek oyuncu (katalog id / varlık id) kadroda tekrar seçilmez. Oyunun aynı oyuncunun iki kopyasına izin verip vermediği oyun içinde doğrulanmadı. |
| BUG-03 | DONE | Havuz budama (oyuncu başına en ucuz kopya, mevki başına ≤90, rating başına 3) + Web Worker; 3000 kartlık fixture < 1,5 sn testli. |
| BUG-04 | PARTIAL | → RULE-01: yalnız 3 eşik config'te; menajer UI yok. |
| BUG-05 | DONE | IndexedDB (Dexie) `bigStore`; eski localStorage kayıtları ilk açılışta taşınır; yazım hatası uyarı bandında görünür; sekmeler arası BroadcastChannel. |
| BUG-06 | DONE | raw/bodies hizası. |
| BUG-07 | DONE | CORS `PUT`. |
| BUG-08 | DONE | Oy upsert (`ON CONFLICT`), set bazlı toplam, `setId` doğrulaması. Materialized view `TODO`. |
| BUG-09 | PARTIAL | → BE-BUG-16: backoff + resume var; async, çoklu instance kilidi, heartbeat, RETIRED eksik. |
| BUG-10 | PARTIAL | → BE-BUG-11: framework exception'ları hâlâ 500. |
| BUG-11 | DONE | STALE düzeltildi: `ea_id_suggestion` yapıldı (eşik 3, admin apply). Zayıflık: BE-SEC-06. |
| BUG-12 | DONE (VERIFY) | Yenileme saati Europe/London 18:00 (yaz TRT 20:00, kış 21:00); `dailyRefresh.verified=false`. |
| BUG-13 | DONE | Ağ hatasında oturum silinmez, Web Locks ile tek yenileyici, 204 toleransı. |
| BUG-14 | PARTIAL | → WEB-BUG-12: `PlayerAvatar` göreli URL. |
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


## v2 inceleme (2026-10-12) — docs/review/v2/
- FAZ A2 kapandı (TAC-07 Buğra girdisi bekliyor: 14 §5 oyun içi kodlar; BE-BUG-01 TRUNCATE migration onay bekliyor). Sıradaki: FAZ A3
- STALE düzeltildi: SEC-08 (reuse yapılmış), BUG-09 (kısmi), BUG-11 (yapılmış), BUG-17 (yapılmış), DS-01 (token'lar global)
- Geri alınan DONE: SEC-02, SEC-05, BUG-01 (backend), BUG-14, DS-05 (EA_ASSET kapalı)
- BE-SEC-01: DONE (ac4bf01)

## FAZ A2 kayıtları
- BE-SEC-01 DONE (ac4bf01) · BE-BUG-01 DONE (9c09747 + V16 TRUNCATE) · BE-BUG-02 DONE (54249aa) · BE-BUG-03 DONE (9c09747)
- WEB-BUG-01 DONE (8a5c018) · TAC-08 DONE (be0d422) · TAC-09 DONE (3dd82c1) · TAC-10 DONE (web e224e5f)
- TAC-07: Buğra'nın 14 §5 oyun içi kodları bekleniyor; doğrulanmamış rol/taban değerlerine dokunulmaz.

## FAZ A3 ilerleme (ara rapor 1)
- DONE: BE-BUG-04 + RULE-01..06 (ut-rules v2, ChemistryCalculator, squadRating(size), ortak vektörler, SBC skoru monoton), WEB-ARCH-02 (useUtRules/rulesStore), BE-BUG-10 (EA/FUT.GG görünen adlar), BE-BUG-05 (parser v2), BE-BUG-06, BE-BUG-07, BE-BUG-08 (rarity id eşlemesi; `byId` boş, VERIFY), BE-BUG-09, BE-BUG-11, BE-BUG-25 (monoton skor), TAC-09 veri taşıması (V18 + web localStorage migration)
- Açık: BE-SEC-02..07, WEB-BUG-02..14, LE-01/02, RULE-03 UI, BE-ARCH-03 (ETag + ut_rules tablosu), TAC-07 (Buğra girdisi)
