package com.fcareer.mlclient.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.ml")
public class MlProperties {
    private String baseUrl = "http://localhost:8000";
    private Duration timeout = Duration.ofSeconds(60);
    private int batchSize = 500;
    /** ml-service'in beklediği X-ML-Secret başlığı; boşsa gönderilmez. */
    private String sharedSecret = "";
    // Yaş bu tarihe göre hesaplanır; model eğitiminde kullanılan snapshot-date ile aynı olmalı (ml-service /health).
    private java.time.LocalDate ageReferenceDate;
}
