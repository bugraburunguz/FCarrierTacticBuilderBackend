package com.fcareer.ingestion;

import com.fcareer.common.exception.DownstreamServiceException;
import com.fcareer.ingestion.configuration.IngestionProperties;
import com.fcareer.ingestion.model.ExternalPlayer;
import com.fcareer.ingestion.model.ExternalPlayerPage;
import com.fcareer.ingestion.provider.EaSiteProvider;
import com.fcareer.ingestion.provider.RatingItemMapper;
import com.fcareer.ingestion.provider.RatingStatKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EaSiteProviderTest {

    private static final String PAGE_URL = "https://www.ea.com/games/ea-sports-fc/ratings";
    private static final String DATA_URL = "https://www.ea.com/_next/data/B1/en/games/ea-sports-fc/ratings.json?franchiseSlug=ea-sports-fc&page=2";

    private MockRestServiceServer server;
    private EaSiteProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://www.ea.com");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new EaSiteProvider(new IngestionProperties(), builder.build(), new RatingItemMapper());
    }

    @Test
    void readsBuildIdFromFc27PageAndMapsAPlayerFromTheDataEndpoint() {
        server.expect(once(), requestTo(PAGE_URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(html("EA SPORTS FC™ 27 PLAYER RATINGS", "B1"), MediaType.TEXT_HTML));
        server.expect(once(), requestTo(DATA_URL))
                .andRespond(withSuccess(dataJson(), MediaType.APPLICATION_JSON));

        ExternalPlayerPage page = provider.fetch(100, 100);

        assertThat(page.totalItems()).isEqualTo(19789);
        assertThat(page.items()).hasSize(1);
        ExternalPlayer mbappe = page.items().get(0);
        assertThat(mbappe.externalId()).isEqualTo(231747);
        assertThat(mbappe.fullName()).isEqualTo("Kylian Mbappé");
        assertThat(mbappe.dob()).isEqualTo(LocalDate.of(1998, 12, 20));
        assertThat(mbappe.preferredFoot()).isEqualTo("Right");
        assertThat(mbappe.positions()).containsExactly("ST", "LW");
        assertThat(mbappe.faceStats()).as("resmî yüz değerleri (F10)").containsExactly(96, 91, 80, 92, 29, 76);
        assertThat(mbappe.playstyles()).containsEntry("FinesseShot", 2).containsEntry("Technical", 1);
        assertThat(mbappe.attrs()).hasSize(34).containsEntry(com.fcareer.fitengine.model.Attr.FINISHING, 95);
        server.verify();
    }

    @Test
    void refusesToIngestWhenThePageIsNotFc27() {
        server.expect(once(), requestTo(PAGE_URL))
                .andRespond(withSuccess(html("EA SPORTS FC™ 28 PLAYER RATINGS", "B1"), MediaType.TEXT_HTML));

        assertThatThrownBy(() -> provider.fetch(0, 100)).isInstanceOf(DownstreamServiceException.class);
    }

    @Test
    void refreshesTheBuildIdOnceWhenTheDataEndpointReturns404() {
        server.expect(once(), requestTo(PAGE_URL))
                .andRespond(withSuccess(html("EA SPORTS FC™ 27 PLAYER RATINGS", "OLD"), MediaType.TEXT_HTML));
        server.expect(once(), requestTo(DATA_URL.replace("/B1/", "/OLD/"))).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(once(), requestTo(PAGE_URL))
                .andRespond(withSuccess(html("EA SPORTS FC™ 27 PLAYER RATINGS", "B1"), MediaType.TEXT_HTML));
        server.expect(once(), requestTo(DATA_URL)).andRespond(withSuccess(dataJson(), MediaType.APPLICATION_JSON));

        assertThat(provider.fetch(100, 100).items()).hasSize(1);
        server.verify();
    }

    @Test
    void rejectsPageSizesTheSiteDoesNotServe() {
        assertThatThrownBy(() -> provider.fetch(0, 50)).isInstanceOf(IllegalStateException.class);
    }

    private static String html(String title, String buildId) {
        return "<html><head><title>" + title + "</title></head><body><script id=\"__NEXT_DATA__\">{\"buildId\":\""
                + buildId + "\"}</script></body></html>";
    }

    private static String dataJson() {
        String stats = RatingStatKeys.KEYS.values().stream()
                .map(k -> "\"" + k + "\":{\"value\":" + ("finishing".equals(k) ? 95 : 70) + ",\"diff\":0}")
                .collect(Collectors.joining(","))
                + ",\"pac\":{\"value\":96},\"sho\":{\"value\":91},\"pas\":{\"value\":80},\"dri\":{\"value\":92},\"def\":{\"value\":29},\"phy\":{\"value\":76}";
        return """
                {"pageProps":{"ratingDetails":{"totalItems":19789,"items":[{
                  "id":231747,"overallRating":91,"firstName":"Kylian","lastName":"Mbappé","commonName":"Kylian Mbappé",
                  "birthdate":"12/20/1998 0:00","height":178,"weight":75,"skillMoves":5,"weakFootAbility":4,"preferredFoot":1,
                  "leagueName":"LALIGA EA SPORTS","team":{"id":243,"label":"Real Madrid"},"nationality":{"label":"France"},
                  "position":{"shortLabel":"ST"},"alternatePositions":[{"shortLabel":"LW"}],
                  "playerAbilities":[
                    {"label":"Finesse Shot","type":{"id":"playStyle"}},
                    {"label":"Finesse Shot+","type":{"id":"playStylePlus"}},
                    {"label":"Technical","type":{"id":"playStyle"}}],
                  "stats":{%s}}]}}}
                """.formatted(stats);
    }
}
