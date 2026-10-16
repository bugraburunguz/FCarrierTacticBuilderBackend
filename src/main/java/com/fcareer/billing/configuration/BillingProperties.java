package com.fcareer.billing.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.billing")
public class BillingProperties {
    /** Günlük kredi limiti, tek yer (FCAREER_DAILY_CREDITS). Gün dönümünde bakiye bunun altındaysa buna tamamlanır; birikmez (PROD-PREP 2). */
    private int dailyCredits = 30;
    /** Ücretsiz beta (FCAREER_FREE_BETA; kodda kapalı, prod env'de açık): herkes Premium hakkı alır. Ödeme kodu kapalı kalır. */
    private boolean freeBeta;
    /** Kredi gün sınırı. */
    private String creditZone = "Europe/Istanbul";
    private int squadFitCost = 5;
    private int recommendCost = 3;
    private int advisorCost = 8;
    private int subscriptionDays = 30;
    private String plan = "PREMIUM_MONTHLY";
}
