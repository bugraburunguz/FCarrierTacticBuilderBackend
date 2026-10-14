package com.fcareer.catalog.repository;

import com.fcareer.fitengine.model.Attr;

/**
 * Oyuncu aramasının iki kaynağı (E3-2): katalog (kariyer dışı) ve kariyer (career_player). Ortak arama motoru yalnız bu ifade parçalarını bilir;
 * kariyer profili katalog tablosuna hiç dokunmaz (mimari kural, CareerReadsOnlyCareerPlayerArchTest).
 * Tüm parçalar `b` (base_player), `n` (nationality), `c` (club), `l` (league) takma adlarını kullanır.
 */
interface SearchProfile {

    /** FROM ... JOIN zinciri. */
    String from();

    /** Satırları kaynağa bağlayan ön koşullar (sürüm, sahiplik). */
    String baseFilter();

    String overall();

    String potential();

    String positions();

    String weakFoot();

    String skillMoves();

    String accelerate();

    String attr(Attr attr);

    String playstyles();

    String clubId();

    String clubName();

    String leagueId();

    String leagueName();

    String value();

    String teamId();

    /** `free_agent` filtresi; kaynak serbest oyuncu bilmiyorsa FALSE. */
    String freeAgentFilter();

    String freeAgentSelect();

    String estimated();

    String runStyle();

    String faceOfficial();

    String attrsJson();

    /** Sözleşme bitiş tarihi (bilinmiyorsa NULL). */
    String contractUntil();

    /** Kiralıkta olmayan oyuncu koşulu. */
    String availableFilter();

    /** Kayıt dosyası havuzu koşulu (kaynakta kayıt kavramı yoksa TRUE). */
    String livePoolFilter();

    /** Yaş hesabının referans tarihi (kariyerde oyun tarihi, yoksa bugün). */
    String gameDate();

}
