package com.fcareer.advisor.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.recommender")
public class RecommenderProperties {

    private double squadFitWeight = 0.55;
    private double intentFitWeight = 0.35;
    private double depthWeight = 0.10;
    private double minFeasibility = 0.15;
    private double pruneBelow = 0.30;
    private int topFormations = 12;
    private int listSize = 5;

    private double usableMin = 60;
    private double creativeCamMin = 70;
    private int staminaHigh = 70;
    private int paceHigh = 72;
    private int techHigh = 72;
    private int cbPaceMin = 66;
    private int crossingHigh = 68;
    private int aerialHigh = 68;

    private double missingStriker = 0.35;
    private double missingCreativeCam = 0.25;
    private double missingWinger = 0.20;
    private double missingCentreBack = 0.30;
    private double lowStaminaWideBack = 0.15;
    private double gegenpressStamina = 0.30;
    private double gegenpressPace = 0.15;
    private double highLineSlowCb = 0.25;
    private double possessionTech = 0.30;
    private double hybridTech = 0.20;
    private double hybridStamina = 0.15;
    private double wingCrossing = 0.20;
    private double wingAerial = 0.10;
    private double atleticoWide = 0.20;
    private double atleticoStrikers = 0.20;
    private double missingPlaymaker = 0.10;
    private double personalCrossing = 0.15;
    private double personalCreator = 0.20;
}
