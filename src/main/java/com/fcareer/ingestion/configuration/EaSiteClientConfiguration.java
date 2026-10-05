package com.fcareer.ingestion.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(prefix = "fcareer.ingestion", name = "provider", havingValue = EaSiteClientConfiguration.PROVIDER_NAME)
public class EaSiteClientConfiguration {

    public static final String PROVIDER_NAME = "ea-site";
    public static final String EA_SITE_REST_CLIENT = "eaSiteRestClient";

    @Bean(EA_SITE_REST_CLIENT)
    public RestClient eaSiteRestClient(IngestionProperties properties, RestClient.Builder builder) {
        IngestionProperties.EaSite config = properties.getEaSite();
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(config.getTimeout())
                .withReadTimeout(config.getTimeout());
        return builder
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .baseUrl(config.getBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, config.getUserAgent())
                .build();
    }
}
