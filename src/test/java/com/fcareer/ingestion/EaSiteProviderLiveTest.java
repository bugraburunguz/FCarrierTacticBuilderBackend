package com.fcareer.ingestion;

import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.configuration.IngestionProperties;
import com.fcareer.ingestion.model.ExternalPlayer;
import com.fcareer.ingestion.model.ExternalPlayerPage;
import com.fcareer.ingestion.provider.EaSiteProvider;
import com.fcareer.ingestion.provider.RatingItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "ea.live", matches = "true")
class EaSiteProviderLiveTest {

    @Test
    void fetchesFc27PageAndMapsEveryPlayer() {
        IngestionProperties properties = new IngestionProperties();
        RestClient client = RestClient.builder()
                .baseUrl(properties.getEaSite().getBaseUrl())
                .defaultHeader(HttpHeaders.USER_AGENT, properties.getEaSite().getUserAgent())
                .build();
        EaSiteProvider provider = new EaSiteProvider(properties, client, new RatingItemMapper());

        ExternalPlayerPage first = provider.fetch(0, 100);
        ExternalPlayerPage last = provider.fetch(19700, 100);

        assertThat(first.totalItems()).isGreaterThan(19_000);
        assertThat(first.items()).hasSize(100).allSatisfy(p -> {
            assertThat(p.attrs()).hasSize(Attr.values().length);
            assertThat(p.positions()).isNotEmpty();
            assertThat(p.positions().get(0)).matches("[A-Z]{2,3}");
            assertThat(p.dob()).isNotNull();
        });
        assertThat(last.items()).isNotEmpty().hasSizeLessThanOrEqualTo(100);
        ExternalPlayer top = first.items().get(0);
        System.out.println("LIVE FC27 " + top.fullName() + " " + top.overall() + " " + top.positions() + " total=" + first.totalItems());
    }
}
