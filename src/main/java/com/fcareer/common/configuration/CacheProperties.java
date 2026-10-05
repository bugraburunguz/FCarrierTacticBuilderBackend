package com.fcareer.common.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.cache")
public class CacheProperties {
    private Duration referenceTtl = Duration.ofHours(6);
}
