package com.fcareer.billing.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.billing")
public class BillingProperties {
    private int dailyCredits = 20;
    private int squadFitCost = 5;
    private int recommendCost = 3;
    private int advisorCost = 8;
    private int subscriptionDays = 30;
    private String plan = "PREMIUM_MONTHLY";
}
