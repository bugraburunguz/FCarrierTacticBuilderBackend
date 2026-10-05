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
}
