package com.fcareer.mlclient.configuration;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class MlClientConfiguration {

    public static final String ML_REST_CLIENT = "mlRestClient";

    @Bean(ML_REST_CLIENT)
    public RestClient mlRestClient(MlProperties properties, RestClient.Builder builder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(properties.getTimeout())
                .withReadTimeout(properties.getTimeout());
        return builder
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .baseUrl(properties.getBaseUrl())
                .build();
    }
}
