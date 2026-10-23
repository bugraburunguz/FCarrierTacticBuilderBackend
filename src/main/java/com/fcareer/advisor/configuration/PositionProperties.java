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
}
