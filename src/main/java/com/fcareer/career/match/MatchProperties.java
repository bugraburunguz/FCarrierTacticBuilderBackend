package com.fcareer.career.match;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.match")
public class MatchProperties {
    private int recentRatings = 5;
    private int leadersDefaultLimit = 20;
    private int leadersMaxLimit = 50;
    private int ratingLeadersMinAppearances = 3;
    private int dangerousPlayersLimit = 5;
    private int seasonRatingMinAppearances = 3;
    private Plan plan = new Plan();

    @Getter
    @Setter
    public static class Plan {
        private int restWindowDays = 10;
        private int restMinMatches = 3;
        private int restMinMinutes = 240;
        private int recentMinMatches = 3;
        private double lowFormBelow = 6.3;
        private int lineGap = 3;
        private int highLineFrom = 65;
        private int lowBlockTo = 35;
        private int keyThreatMin = 10;
        private int cardYellowMin = 4;
        private int maxNotes = 5;
        private double aerialHeading = 0.4;
        private double aerialJumping = 0.3;
        private double aerialStrength = 0.3;
        private int aerialHeightBaseCm = 180;
        private double aerialPerCm = 0.5;
        private int aerialTop = 2;
    }
    private int cardRiskLimit = 5;
}
