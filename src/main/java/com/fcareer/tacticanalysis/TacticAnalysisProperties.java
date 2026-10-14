package com.fcareer.tacticanalysis;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Taktik analizi ayarları (fcareer.tactic.*): seviye referansı için asgari örnek ve kariyer dışı OVR bandı genişliği. Hepsi verified:false. */
@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.tactic")
public class TacticAnalysisProperties {

    /** Referans medyanı için en az oyuncu sayısı; altında referans verilmez (mutlak eşiğe düşülür). */
    private int levelMinSample = 15;

    /** Kariyer dışı: takımın ilk 11 ortalama OVR'sinin ± bu kadarlık katalog bandı. */
    private int catalogOvrBand = 4;
}
