package com.fcareer.catalog.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fcareer.catalog")
public class CatalogProperties {
    private short defaultGameVersion = 27;
}
