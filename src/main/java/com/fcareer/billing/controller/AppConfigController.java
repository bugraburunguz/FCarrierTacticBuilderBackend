package com.fcareer.billing.controller;

import com.fcareer.billing.configuration.BillingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.API_V1;

/** PROD-PREP 2: arayüzün okuduğu herkese açık uygulama ayarları (beta etiketi, günlük kredi). Tek kaynak BillingProperties. */
@RestController
@RequestMapping(API_V1 + "/config")
@RequiredArgsConstructor
public class AppConfigController {

    private final BillingProperties billingProperties;

    public record AppConfig(boolean freeBeta, int dailyCredits) {
    }

    @GetMapping("/app")
    public AppConfig app() {
        return new AppConfig(billingProperties.isFreeBeta(), billingProperties.getDailyCredits());
    }
}
