package com.fcareer.advisor.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** "En iyi mevki" ayarları (KARIYER-UX FAZ 3.1). */
@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.position")
public class PositionProperties {

    /** Listelenen bir mevki en iyi mevki overall'ına bu kadar yakınsa (puan) listelenen mevki gösterilir. */
    private int listedPreference = 1;
    /** FIT-3 madde 12 (verified:false): dönüşüm için listede olmayan mevkideki en iyi Güç, listedekinin en az bu kadar üstünde olmalı. */
    private double conversionGain = 5;
    /** FIT-3 madde 12 (verified:false): dönüşüm mevkisinde EA OVR'si oyuncunun OVR'sinden en çok bu kadar düşük olabilir. */
    private int conversionMaxOverallDrop = 3;
    /** FIT-3 madde 13 (verified:false): iki kanadın en iyi rol Güç farkı bunun altındaysa eski kural (listedeki taraf, sonra ayak). */
    private double sideThreshold = 0.5;
}
