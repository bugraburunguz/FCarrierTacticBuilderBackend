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
    private int cardRiskLimit = 5;
}
