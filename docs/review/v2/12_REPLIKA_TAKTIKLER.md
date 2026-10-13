# 12 · REPLİKA TAKTİKLER v2 — FC27'de en yakın replika
İstek (Buğra): Kariyer bölümündeki replika taktikler, yüklenen FC27 rehberleriyle harmanlanıp oyundaki en yakın replikaya dönüşsün. Kapsam: (1) 2000 sonrası tüm Şampiyonlar Ligi şampiyonları, (2) kazanamasa da efsane kadrolar, (3) efsane hocaların efsane oldukları takımlar, (4) underrated hocalar (Sarri, Italiano, …), (5) Beşiktaş, Fenerbahçe, Galatasaray, Trabzonspor'un 2000 sonrası en iyi 3 sezonu (ikonik yıllar), (6) Başakşehir ve Bursaspor'un şampiyonluk sezonları.

**Teslim:** `data/replicas_v2.fc27.json` (107 kayıt, tamamı otomatik doğrulamadan geçti) + şema `data/replicas_v2.SCHEMA.md`. Bu dosya `presets.json`'daki 34 REPLICA kaydının yerini alır.

---
## 1. NEDEN YENİDEN? (bugünkü replikalar FC27'ye uymuyor)
`presets.json` incelemesi (13 ENG-17 ile aynı bulgu):
- Replikalarda **rol yok.** Yalnız `slotTags` + serbest metin `roleHints` var. Motor rolü etiket oylamasından ya da formasyon varsayılanından tahmin ediyor → kullanıcı "Sarri Napoli"yi seçince oyuna girecek 11 rol/focus belli değil.
- `defensiveApproach` serbest metin (31 farklı string, hiç parse edilmiyor), 14 kayıtta `depth` yok, `buildUp` değerlerinden bazıları ("Direct", "Balanced / Counter") FC27 enum'u değil → sessizce BALANCED'a düşüyor.
- FC25 kalıntıları: `chanceCreation`, `offensiveWidth`, `playersInBox`, "Players in Box 4". **FC27'de bu ayarlar yok.** FC27 taktiği = formasyon + Build-Up + Defensive Approach + line height + 11 × (pozisyon, rol, focus).
- Formasyon etiketleri EA ile uyuşmuyor (04 §3).

## 2. YÖNTEM — gerçek futbol → FC27 kolları
Her kayıt şu sırayla çevrildi:
1. **Gerçek şekil:** topla (örn. 3-2-5) ve topsuz (örn. 4-4-2 orta blok) yerleşim + 3-6 anahtar mekanizma.
2. **Formasyon:** FC27'nin 29 formasyonundan **topsuz** şekle en yakın olan (oyunda formasyon topsuz yerleşimi belirler; topla şekil rollerden gelir, Tactics Guide).
3. **Build-Up:** sabırlı possession → SHORT_PASSING; karışık → BALANCED; dikey geçiş → COUNTER.
4. **Defensive Approach + line height:** blok yüksekliği ve pres davranışına göre (DEEP 1-30 / BALANCED 31-60 / HIGH 61-90 / AGGRESSIVE 91-100; AGGRESSIVE = top kaybında anında pres + ekstra stamina).
5. **11 rol + focus** (yalnız 04 §4'teki geçerli kombinasyonlar). Topla şekli rollerle kurma kuralları:
   | Gerçek mekanizma | FC27 karşılığı |
   |---|---|
   | İçe kapanan bek (Lahm, Cancelo, Stones hibrit) | Falseback / Inverted Wingback |
   | Topla 3'lü savunma | Bir bek Fullback/Defend kalır ya da CDM Centre Half/Defend stoperlerin arasına iner, ya da Wide Back |
   | Bindiren bek / wingback | Attacking Wingback (Support/Attack), Wingback |
   | False 9 | False 9 (Build-Up) |
   | Trequartista / 10 | Classic 10 / CAM Playmaker |
   | Geç koşu yapan 8 | CDM Box Crasher / CAM Shadow Striker |
   | Yarı alanda oynayan 8 | Half Winger |
   | Çizgiyi kucaklayan kanat | Winger (Balanced/Attack) |
   | İçe kat eden kanat | Inside Forward (karşı ayak) |
   | Regista | Deep-Lying Playmaker (Build-Up) |
   | Adam adama savunma (Gasperini, Bielsa) | AGGRESSIVE + Stopper/Aggressive + Wide Back/Aggressive + Holding/Ball-Winning + Team Press zamanlaması (birebir değil, `replicaFidelity`'de yazılı) |
6. **Oyuncu profili:** her slotta gerçek oyuncu tipi ("Makélélé tipi ekran 6"), 3-4 anahtar attribute, 2-3 PlayStyle → kariyer modunda "bu replika için kadronda kim uygun / kimi almalısın" önerisinin girdisi.
7. **Plan B:** sezonun gerçek senaryosundan (öndeyken koruma, gerideyken risk) ikinci taktik; FC27'nin 5 özel taktik slotundan birine konur.
8. **Smart Tactics:** Tactical Focus Hücum/Savunma seçilince o kadronun hangi focus'unun nereye kaydığı (04 `tacticalFocus` kurallarıyla hesaplandı) + Quick Tactic önerisi.
9. **Dürüstlük:** `replicaFidelity.score` 1-5 ve `cannotReplicate` listesi (örn. "FC27'de genişlik ayarı yok", "3-4-x formasyonlarda wingback'ler LM/RM").

Kullanılan rehberler: Tactics Guide (approach aralıkları, build-up, 5 taktik, kod), Smart Tactics (focus kaydırma), roles_focus_authoritative (geçerli roller), Player Roles (rol davranışları), Positions, PlayStyles, Attributes, Best Tactics (FUT.GG formasyon adları), Best Formation.

## 3. KAYIT ŞEMASI (özet, tam hâli `data/replicas_v2.SCHEMA.md`)
```jsonc
{ "id": "sarri_napoli_2018", "kind": "REPLICA", "category": "UNDERRATED_COACH",
  "club": "Napoli", "coach": "Maurizio Sarri", "season": "2017-18", "honours": [...], "whyIconic": "...",
  "realShape": { "inPossession": "...", "outOfPossession": "...", "keyMechanisms": [...] },
  "fc27": { "formation": "4-3-3 (2)", "buildUp": "SHORT_PASSING", "defensiveApproach": "HIGH", "lineHeight": 80,
            "slots": [ { "slot": "GK", "position": "GK", "role": "Sweeper Keeper", "focus": "Build-Up",
                         "profile": "Pepe Reina tipi ...", "keyAttributes": [...], "keyPlayStyles": [...] }, ... 11 ],
            "planB": { "label": "...", "formation": "...", "buildUp": "...", "defensiveApproach": "...", "lineHeight": 0, "changes": ["RB: Fullback/Defend (Hysaj)"] },
            "smartTactics": [...], "tacticCode": null },
  "replicaFidelity": { "score": 4, "cannotReplicate": [...] },
  "behaviorTags": [...], "sources": ["model bilgisi — VERIFY"], "verify": [...], "alternatives": [...] }
```
`tacticCode` boş: backend `EaTacticCodec` ile **otomatik üretilecek** (REP-03). Böylece kullanıcı kodu oyuna yapıştırıp birebir aynı taktiği alır.

## 4. KATALOG (107 kayıt)
Benzerlik puanı 3 ve altı olanların `verify` listesi var (kesin ilk 11, kanat tarafları vb.). Tüm kaynaklar "model bilgisi — VERIFY" (bu oturumda web araması kapalıydı) → REP-06 doğrulama turu şart.
Seçim notları:
- **2025-26 ŞL şampiyonu** (30 Mayıs 2026 finali) doğrulanamadı → `ucl_winner_2026_tbd` yer tutucu, `fc27: null`. Kazanan doğrulanınca doldurulacak.
- 1999-00 Real Madrid (Mayıs 2000 finali) "2000 sonrası" sayıldı.
- Sarri Napoli 2017-18 ve Gasperini Atalanta 2019-20 hem "efsane kadro" hem "underrated hoca" adayıydı → underrated'da tutuldu.
- Galatasaray 2000 Süper Kupa'sı (Lucescu, 2000-01) bu kayda sayılmadı.
- Beşiktaş 2016-17: "ŞL grubunu yenilgisiz geçti" bilgisi yanlıştı (Kiev'de 6-0); yenilgisiz grup 2017-18. Kayıtta düzeltildi.
- Her Türk kaydında `alternatives` alanı var (örn. GS 2018-19 Terim, FB 2004-05 Daum, BJK 2008-09 Denizli, TS 2009-10 Güneş).
- **Hiç kullanılmayan formasyon:** 4-5-1 ve 4-5-1 (2). EA etiketi ↔ slot eşlemesi kesinleşmeden kullanılmadı (BE-BUG-10).

### UCL_WINNER
| id | Takım | Hoca | Sezon | Formasyon | Build-Up | Savunma | Hat | Benzerlik |
|---|---|---|---|---|---|---|---|---|
| `delbosque_real_2000` | Real Madrid | Vicente del Bosque | 1999-00 | 5-2-1-2 | BALANCED | BALANCED | 42 | 3/5 |
| `hitzfeld_bayern_2001` | Bayern Münih | Ottmar Hitzfeld | 2000-01 | 3-4-1-2 | BALANCED | BALANCED | 40 | 3/5 |
| `delbosque_real_2002` | Real Madrid | Vicente del Bosque | 2001-02 | 4-2-3-1 (2) | BALANCED | BALANCED | 48 | 3/5 |
| `ancelotti_milan_2003` | AC Milan | Carlo Ancelotti | 2002-03 | 4-1-2-1-2 (2) | BALANCED | BALANCED | 50 | 4/5 |
| `mourinho_porto_2004` | FC Porto | José Mourinho | 2003-04 | 4-1-2-1-2 (2) | BALANCED | HIGH | 64 | 4/5 |
| `benitez_liverpool_2005` | Liverpool | Rafael Benítez | 2004-05 | 4-2-3-1 (2) | BALANCED | BALANCED | 42 | 3/5 |
| `rijkaard_barcelona_2006` | FC Barcelona | Frank Rijkaard | 2005-06 | 4-3-3 (2) | SHORT_PASSING | HIGH | 64 | 4/5 |
| `ancelotti_milan_2007` | AC Milan | Carlo Ancelotti | 2006-07 | 4-3-2-1 | BALANCED | BALANCED | 46 | 4/5 |
| `ferguson_united_2008` | Manchester United | Sir Alex Ferguson | 2007-08 | 4-3-3 (2) | BALANCED | BALANCED | 50 | 4/5 |
| `guardiola_barcelona_2009` | FC Barcelona | Pep Guardiola | 2008-09 | 4-3-3 (2) | SHORT_PASSING | HIGH | 72 | 4/5 |
| `mourinho_inter_2010` | Inter | José Mourinho | 2009-10 | 4-2-3-1 (2) | COUNTER | BALANCED | 35 | 4/5 |
| `guardiola_barcelona_2011` | FC Barcelona | Pep Guardiola | 2010-11 | 4-3-3 (2) | SHORT_PASSING | AGGRESSIVE | 92 | 4/5 |
| `dimatteo_chelsea_2012` | Chelsea | Roberto Di Matteo | 2011-12 | 4-2-3-1 (2) | COUNTER | DEEP | 15 | 4/5 |
| `heynckes_bayern_2013` | Bayern Münih | Jupp Heynckes | 2012-13 | 4-2-3-1 (2) | BALANCED | HIGH | 66 | 4/5 |
| `ancelotti_real_2014` | Real Madrid | Carlo Ancelotti | 2013-14 | 4-3-3 (2) | BALANCED | BALANCED | 45 | 4/5 |
| `luisenrique_barcelona_2015` | FC Barcelona | Luis Enrique | 2014-15 | 4-3-3 (2) | BALANCED | HIGH | 66 | 4/5 |
| `zidane_real_2016` | Real Madrid | Zinédine Zidane | 2015-16 | 4-3-3 (2) | BALANCED | BALANCED | 46 | 4/5 |
| `zidane_real_2017` | Real Madrid | Zinédine Zidane | 2016-17 | 4-1-2-1-2 (2) | BALANCED | BALANCED | 52 | 4/5 |
| `zidane_real_2018` | Real Madrid | Zinédine Zidane | 2017-18 | 4-3-1-2 | BALANCED | BALANCED | 42 | 3/5 |
| `klopp_liverpool_2019` | Liverpool | Jürgen Klopp | 2018-19 | 4-3-3 (2) | COUNTER | HIGH | 76 | 4/5 |
| `flick_bayern_2020` | Bayern Münih | Hansi Flick | 2019-20 | 4-2-3-1 (2) | BALANCED | AGGRESSIVE | 95 | 4/5 |
| `tuchel_chelsea_2021` | Chelsea | Thomas Tuchel | 2020-21 | 3-4-2-1 | BALANCED | BALANCED | 50 | 4/5 |
| `ancelotti_real_2022` | Real Madrid | Carlo Ancelotti | 2021-22 | 4-3-3 (2) | BALANCED | BALANCED | 38 | 3/5 |
| `guardiola_city_2023` | Manchester City | Pep Guardiola | 2022-23 | 4-1-4-1 | SHORT_PASSING | HIGH | 70 | 4/5 |
| `ancelotti_real_2024` | Real Madrid | Carlo Ancelotti | 2023-24 | 4-1-2-1-2 (2) | BALANCED | BALANCED | 46 | 4/5 |
| `luisenrique_psg_2025` | Paris Saint-Germain | Luis Enrique | 2024-25 | 4-3-3 (2) | SHORT_PASSING | AGGRESSIVE | 93 | 4/5 |
| `ucl_winner_2026_tbd` | DOĞRULANMALI (2025-26 UCL kazananı) | DOĞRULANMALI | 2025-26 | — | — | — | — | 1/5 |

### LEGENDARY_SQUAD
| id | Takım | Hoca | Sezon | Formasyon | Build-Up | Savunma | Hat | Benzerlik |
|---|---|---|---|---|---|---|---|---|
| `cuper_valencia_2001` | Valencia | Héctor Cúper | 2000-01 | 4-4-2 | COUNTER | BALANCED | 36 | 3/5 |
| `toppmoller_leverkusen_2002` | Bayer Leverkusen | Klaus Toppmöller | 2001-02 | 4-1-4-1 | BALANCED | HIGH | 62 | 3/5 |
| `deschamps_monaco_2004` | AS Monaco | Didier Deschamps | 2003-04 | 4-4-2 | COUNTER | BALANCED | 45 | 3/5 |
| `wenger_arsenal_2004` | Arsenal | Arsène Wenger | 2003-04 | 4-4-1-1 (2) | BALANCED | BALANCED | 55 | 4/5 |
| `irureta_deportivo_2004` | Deportivo La Coruña | Javier Irureta | 2003-04 | 4-2-3-1 (2) | BALANCED | BALANCED | 50 | 3/5 |
| `pellegrini_villarreal_2006` | Villarreal | Manuel Pellegrini | 2005-06 | 4-1-2-1-2 (2) | SHORT_PASSING | BALANCED | 50 | 3/5 |
| `spalletti_roma_2007` | AS Roma | Luciano Spalletti | 2006-07 | 4-2-3-1 (2) | SHORT_PASSING | BALANCED | 55 | 4/5 |
| `wenger_arsenal_2008` | Arsenal | Arsène Wenger | 2007-08 | 4-4-2 | SHORT_PASSING | HIGH | 62 | 3/5 |
| `benitez_liverpool_2009` | Liverpool | Rafael Benítez | 2008-09 | 4-2-3-1 (2) | BALANCED | BALANCED | 48 | 4/5 |
| `klopp_dortmund_2013` | Borussia Dortmund | Jürgen Klopp | 2012-13 | 4-2-3-1 (2) | COUNTER | AGGRESSIVE | 93 | 4/5 |
| `simeone_atletico_2014` | Atlético Madrid | Diego Simeone | 2013-14 | 4-4-2 | COUNTER | BALANCED | 35 | 4/5 |
| `rodgers_liverpool_2014` | Liverpool | Brendan Rodgers | 2013-14 | 4-1-2-1-2 (2) | COUNTER | HIGH | 66 | 4/5 |
| `allegri_juventus_2015` | Juventus | Massimiliano Allegri | 2014-15 | 4-3-1-2 | BALANCED | BALANCED | 46 | 4/5 |
| `ranieri_leicester_2016` | Leicester City | Claudio Ranieri | 2015-16 | 4-4-2 | COUNTER | DEEP | 25 | 5/5 |
| `jardim_monaco_2017` | AS Monaco | Leonardo Jardim | 2016-17 | 4-4-2 | COUNTER | HIGH | 64 | 4/5 |
| `allegri_juventus_2017` | Juventus | Massimiliano Allegri | 2016-17 | 4-2-3-1 (2) | BALANCED | BALANCED | 42 | 4/5 |
| `guardiola_city_2018` | Manchester City | Pep Guardiola | 2017-18 | 4-3-3 (2) | SHORT_PASSING | HIGH | 76 | 4/5 |
| `tenhag_ajax_2019` | Ajax | Erik ten Hag | 2018-19 | 4-2-3-1 (2) | SHORT_PASSING | HIGH | 74 | 4/5 |
| `pochettino_tottenham_2019` | Tottenham Hotspur | Mauricio Pochettino | 2018-19 | 4-1-2-1-2 (2) | BALANCED | HIGH | 62 | 3/5 |
| `klopp_liverpool_2020` | Liverpool | Jürgen Klopp | 2019-20 | 4-3-3 (2) | BALANCED | HIGH | 78 | 4/5 |
| `inzaghi_inter_2023` | Inter | Simone Inzaghi | 2022-23 | 5-3-2 | BALANCED | BALANCED | 46 | 4/5 |
| `alonso_leverkusen_2024` | Bayer Leverkusen | Xabi Alonso | 2023-24 | 3-4-2-1 | SHORT_PASSING | HIGH | 68 | 4/5 |
| `inzaghi_inter_2025` | Inter | Simone Inzaghi | 2024-25 | 5-3-2 | BALANCED | BALANCED | 38 | 4/5 |
| `flick_barcelona_2025` | FC Barcelona | Hansi Flick | 2024-25 | 4-2-3-1 (2) | BALANCED | AGGRESSIVE | 96 | 4/5 |

### LEGENDARY_COACH
| id | Takım | Hoca | Sezon | Formasyon | Build-Up | Savunma | Hat | Benzerlik |
|---|---|---|---|---|---|---|---|---|
| `herrera_inter_1965` | Internazionale | Helenio Herrera | 1964-65 | 4-4-2 (2) | COUNTER | DEEP | 20 | 2/5 |
| `kovacs_ajax_1972` | AFC Ajax | Ștefan Kovács | 1971-72 | 4-3-3 (2) | SHORT_PASSING | AGGRESSIVE | 93 | 3/5 |
| `lobanovskyi_dynamokyiv_1986` | Dinamo Kyiv | Valeriy Lobanovskyi | 1985-86 | 4-4-2 | BALANCED | HIGH | 80 | 3/5 |
| `sacchi_milan_1989` | AC Milan | Arrigo Sacchi | 1988-89 | 4-4-2 | BALANCED | HIGH | 88 | 4/5 |
| `cruyff_barcelona_1992` | FC Barcelona | Johan Cruyff | 1991-92 | 4-3-3 (2) | SHORT_PASSING | HIGH | 80 | 3/5 |
| `capello_milan_1994` | AC Milan | Fabio Capello | 1993-94 | 4-4-2 | BALANCED | BALANCED | 58 | 4/5 |
| `vangaal_ajax_1995` | AFC Ajax | Louis van Gaal | 1994-95 | 4-3-3 (4) | SHORT_PASSING | HIGH | 82 | 3/5 |
| `lobanovskyi_dynamokyiv_1999` | Dinamo Kyiv | Valeriy Lobanovskyi | 1998-99 | 4-1-3-2 | BALANCED | HIGH | 74 | 3/5 |
| `ferguson_manutd_1999` | Manchester United | Sir Alex Ferguson | 1998-99 | 4-4-2 | BALANCED | BALANCED | 55 | 4/5 |
| `benitez_valencia_2004` | Valencia CF | Rafael Benítez | 2003-04 | 4-2-3-1 (2) | BALANCED | BALANCED | 52 | 4/5 |
| `mourinho_chelsea_2005` | Chelsea | José Mourinho | 2004-05 | 4-3-3 (2) | BALANCED | BALANCED | 52 | 4/5 |
| `ancelotti_chelsea_2010` | Chelsea | Carlo Ancelotti | 2009-10 | 4-1-2-1-2 (2) | BALANCED | HIGH | 66 | 4/5 |
| `bielsa_athletic_2012` | Athletic Club | Marcelo Bielsa | 2011-12 | 4-3-3 (2) | BALANCED | AGGRESSIVE | 94 | 2/5 |
| `mourinho_realmadrid_2012` | Real Madrid | José Mourinho | 2011-12 | 4-2-3-1 (2) | COUNTER | BALANCED | 50 | 4/5 |
| `klopp_dortmund_2012` | Borussia Dortmund | Jürgen Klopp | 2011-12 | 4-2-3-1 (2) | COUNTER | AGGRESSIVE | 92 | 4/5 |
| `conte_juventus_2014` | Juventus | Antonio Conte | 2013-14 | 3-1-4-2 | BALANCED | BALANCED | 55 | 4/5 |
| `guardiola_bayern_2014` | FC Bayern München | Pep Guardiola | 2013-14 | 4-3-3 (2) | SHORT_PASSING | HIGH | 82 | 4/5 |
| `conte_chelsea_2017` | Chelsea | Antonio Conte | 2016-17 | 3-4-3 | BALANCED | BALANCED | 45 | 4/5 |
| `simeone_atletico_2021` | Atlético de Madrid | Diego Simeone | 2020-21 | 3-1-4-2 | BALANCED | BALANCED | 40 | 4/5 |
| `spalletti_napoli_2023` | SSC Napoli | Luciano Spalletti | 2022-23 | 4-3-3 (2) | SHORT_PASSING | HIGH | 74 | 4/5 |

### UNDERRATED_COACH
| id | Takım | Hoca | Sezon | Formasyon | Build-Up | Savunma | Hat | Benzerlik |
|---|---|---|---|---|---|---|---|---|
| `garcia_lille_2011` | LOSC Lille | Rudi Garcia | 2010-11 | 4-3-3 (2) | BALANCED | HIGH | 68 | 4/5 |
| `zeman_pescara_2012` | Delfino Pescara 1936 | Zdeněk Zeman | 2011-12 | 4-3-3 (2) | BALANCED | AGGRESSIVE | 97 | 3/5 |
| `pochettino_southampton_2014` | Southampton | Mauricio Pochettino | 2013-14 | 4-2-3-1 (2) | BALANCED | AGGRESSIVE | 92 | 3/5 |
| `schmidt_salzburg_2014` | FC Red Bull Salzburg | Roger Schmidt | 2013-14 | 4-2-2-2 | COUNTER | AGGRESSIVE | 97 | 3/5 |
| `sarri_empoli_2015` | Empoli FC | Maurizio Sarri | 2014-15 | 4-3-1-2 | SHORT_PASSING | HIGH | 76 | 3/5 |
| `hasenhuttl_leipzig_2017` | RB Leipzig | Ralph Hasenhüttl | 2016-17 | 4-2-2-2 | COUNTER | AGGRESSIVE | 95 | 3/5 |
| `sarri_napoli_2018` | SSC Napoli | Maurizio Sarri | 2017-18 | 4-3-3 (2) | SHORT_PASSING | HIGH | 80 | 4/5 |
| `favre_dortmund_2019` | Borussia Dortmund | Lucien Favre | 2018-19 | 4-2-3-1 (2) | BALANCED | BALANCED | 54 | 4/5 |
| `gasperini_atalanta_2020` | Atalanta BC | Gian Piero Gasperini | 2019-20 | 3-4-2-1 | BALANCED | AGGRESSIVE | 95 | 2/5 |
| `bielsa_leeds_2020` | Leeds United | Marcelo Bielsa | 2019-20 | 4-1-4-1 | BALANCED | AGGRESSIVE | 96 | 3/5 |
| `nagelsmann_leipzig_2020` | RB Leipzig | Julian Nagelsmann | 2019-20 | 3-4-1-2 | BALANCED | HIGH | 80 | 3/5 |
| `dezerbi_sassuolo_2021` | US Sassuolo | Roberto De Zerbi | 2020-21 | 4-2-3-1 (2) | SHORT_PASSING | HIGH | 70 | 3/5 |
| `emery_villarreal_2021` | Villarreal CF | Unai Emery | 2020-21 | 4-4-2 | BALANCED | BALANCED | 45 | 3/5 |
| `italiano_fiorentina_2023` | ACF Fiorentina | Vincenzo Italiano | 2022-23 | 4-2-3-1 (2) | SHORT_PASSING | AGGRESSIVE | 93 | 3/5 |
| `dezerbi_brighton_2023` | Brighton & Hove Albion | Roberto De Zerbi | 2022-23 | 4-2-3-1 (2) | SHORT_PASSING | HIGH | 68 | 3/5 |
| `slot_feyenoord_2023` | Feyenoord | Arne Slot | 2022-23 | 4-3-3 (2) | SHORT_PASSING | HIGH | 80 | 4/5 |
| `postecoglou_celtic_2023` | Celtic | Ange Postecoglou | 2022-23 | 4-3-3 (2) | SHORT_PASSING | HIGH | 86 | 4/5 |
| `gasperini_atalanta_2024` | Atalanta BC | Gian Piero Gasperini | 2023-24 | 3-4-2-1 | BALANCED | AGGRESSIVE | 96 | 2/5 |
| `michel_girona_2024` | Girona FC | Míchel | 2023-24 | 4-3-3 (2) | SHORT_PASSING | HIGH | 70 | 4/5 |
| `motta_bologna_2024` | Bologna FC | Thiago Motta | 2023-24 | 4-1-4-1 | SHORT_PASSING | HIGH | 66 | 3/5 |
| `italiano_bologna_2025` | Bologna FC | Vincenzo Italiano | 2024-25 | 4-2-3-1 (2) | BALANCED | AGGRESSIVE | 92 | 3/5 |
| `iraola_bournemouth_2025` | AFC Bournemouth | Andoni Iraola | 2024-25 | 4-2-3-1 (2) | COUNTER | AGGRESSIVE | 94 | 3/5 |

### TURKISH_BIG4
| id | Takım | Hoca | Sezon | Formasyon | Build-Up | Savunma | Hat | Benzerlik |
|---|---|---|---|---|---|---|---|---|
| `lucescu_besiktas_2003` | Beşiktaş | Mircea Lucescu | 2002-03 | 4-4-2 | COUNTER | BALANCED | 38 | 3/5 |
| `gunes_besiktas_2017` | Beşiktaş | Şenol Güneş | 2016-17 | 4-2-3-1 (2) | COUNTER | BALANCED | 54 | 4/5 |
| `sergen_besiktas_2021` | Beşiktaş | Sergen Yalçın | 2020-21 | 4-4-2 | BALANCED | BALANCED | 52 | 3/5 |
| `zico_fenerbahce_2008` | Fenerbahçe | Zico | 2007-08 | 4-2-3-1 (2) | BALANCED | BALANCED | 52 | 3/5 |
| `kocaman_fenerbahce_2011` | Fenerbahçe | Aykut Kocaman | 2010-11 | 4-2-3-1 (2) | BALANCED | BALANCED | 46 | 3/5 |
| `kartal_fenerbahce_2024` | Fenerbahçe | İsmail Kartal | 2023-24 | 4-2-3-1 (2) | SHORT_PASSING | HIGH | 64 | 4/5 |
| `terim_galatasaray_2000` | Galatasaray | Fatih Terim | 1999-2000 | 4-1-2-1-2 (2) | COUNTER | BALANCED | 40 | 3/5 |
| `terim_galatasaray_2013` | Galatasaray | Fatih Terim | 2012-13 | 4-1-2-1-2 (2) | BALANCED | BALANCED | 50 | 4/5 |
| `buruk_galatasaray_2024` | Galatasaray | Okan Buruk | 2023-24 | 4-2-3-1 (2) | BALANCED | HIGH | 72 | 4/5 |
| `dogan_trabzonspor_2005` | Trabzonspor | Ziya Doğan (VERIFY) | 2004-05 | 4-4-2 | COUNTER | BALANCED | 42 | 2/5 |
| `gunes_trabzonspor_2011` | Trabzonspor | Şenol Güneş | 2010-11 | 4-4-2 | BALANCED | BALANCED | 46 | 3/5 |
| `avci_trabzonspor_2022` | Trabzonspor | Abdullah Avcı | 2021-22 | 4-2-3-1 (2) | BALANCED | BALANCED | 55 | 4/5 |

### TURKISH_CHAMPION
| id | Takım | Hoca | Sezon | Formasyon | Build-Up | Savunma | Hat | Benzerlik |
|---|---|---|---|---|---|---|---|---|
| `saglam_bursaspor_2010` | Bursaspor | Ertuğrul Sağlam | 2009-10 | 4-2-3-1 (2) | BALANCED | BALANCED | 45 | 3/5 |
| `buruk_basaksehir_2020` | Medipol Başakşehir | Okan Buruk | 2019-20 | 4-2-3-1 (2) | BALANCED | BALANCED | 40 | 4/5 |
## 5. ÜRÜN — replika ekranları
**Liste (`/career/replicas`):** kategori sekmeleri (ŞL Şampiyonları · Efsane Kadrolar · Efsane Hocalar · Underrated · Türk Futbolu), dönem filtresi (1960-90 / 2000-10 / 2010-20 / 2020+), formasyon, build-up, savunma yaklaşımı, arama (hoca/kulüp). Kart: kulüp + sezon + hoca, mini saha (11 rol noktası), formasyon/approach çipleri, benzerlik yıldızı.
**Detay (`/career/replicas/:id`):**
1. Kahraman: saha (08 §14 slot yapısı: kart yeri → rol → mevki), boş slotlarda gerçek oyuncu profili metni.
2. Sağda: "Neden efsane", gerçek şekil (topla/topsuz), anahtar mekanizmalar, FC27 ayarları (formasyon, build-up, approach, hat), **taktik kodu + Kopyala**.
3. "Plan B" sekmesi: ikinci taktik + değişen slotlar vurgulu.
4. "Maç içi" sekmesi: Tactical Focus kaymaları ve Quick Tactic önerileri.
5. "Birebir olmayanlar": `cannotReplicate`.
6. **"Kadromla uygula"** (kariyer): replikanın 11 slotunu kullanıcının kariyer kadrosuna yeni motorla (13 §4) atar → slot başına uyum + "bu profile en yakın 3 transfer adayı" (bütçe ve oyun tarihine göre). Sonuç kullanıcının 5 taktik slotundan birine kaydedilir.

## 6. GÖREVLER
| ID | Pri | Görev | Kabul |
|---|---|---|---|
| REP-01 | P1 | `data/replicas_v2.fc27.json`'u `seed/replicas.fc27.json` olarak ekle; `presets.json`'dan REPLICA kayıtlarını çıkar (STYLE + PERSONAL kalır, onlar da v2 şemasına taşınır: REP-05) | Seeder eski id'leri siler (BE-BUG-24) |
| REP-02 | P1 | Seed doğrulayıcısı (CI): formasyon 29'dan, 11 slot formasyonun pozisyonlarıyla aynı sırada, (pozisyon, rol, focus) geçerli, lineHeight approach aralığında, planB değişiklikleri geçerli, id tekil. Plan B değişiklik biçimi tek: `"SLOT: Rol/Focus (oyuncu)"` | Bozuk kayıt CI'ı kırar |
| REP-03 | P1 | Her kayıt için `tacticCode`'u `EaTacticCodec` ile üret (seed yüklenirken ya da build adımında) | 107 kayıt kodlu; kod decode edilince aynı ayarlar |
| REP-04 | P1 | Replika liste + detay ekranları (§5) | Ekran onayı |
| REP-05 | P2 | STYLE (8) ve PERSONAL (Çakal Doktrini) presetlerini v2 şemasına çevir (rol/focus'lu, FC25 alanları kaldırılmış) | `chanceCreation`/`playersInBox`/`offensiveWidth` repoda kalmaz |
| REP-06 | P1 | Doğrulama turu: Buğra/katkıcı `verify` listelerini oyun içi ve maç arşiviyle kontrol eder; benzerlik ≤3 olanlar öncelikli. Admin CMS'te replika düzenleme | `verify` listesi boş ya da bilinçli kabul |
| REP-07 | P1 | "Kadromla uygula" = 13'teki yeni motor (`currentFit` + atama + profil eşlemesi) | Golden test: Sarri replikasında Jorginho tipi oyuncu CDM DLP'ye atanır |
| REP-08 | P2 | 2025-26 ŞL şampiyonunu doldur | Yer tutucu kalkar |
| REP-09 | P2 | Kullanıcı replika önerisi gönderebilsin (moderasyonlu) | |
