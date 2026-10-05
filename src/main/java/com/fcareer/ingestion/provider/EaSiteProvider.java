package com.fcareer.ingestion.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fcareer.common.exception.DownstreamServiceException;
import com.fcareer.ingestion.configuration.EaSiteClientConfiguration;
import com.fcareer.ingestion.configuration.IngestionProperties;
import com.fcareer.ingestion.model.ExternalPlayer;
import com.fcareer.ingestion.model.ExternalPlayerPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EA'nın resmi ratings sayfasının (FC27) herkese açık sayfa verisini okur. Bu belgelenmiş bir API değildir;
 * buildId her site güncellemesinde değişir, bu yüzden sayfa HTML'inden okunur ve 404'te bir kez yenilenir.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "fcareer.ingestion", name = "provider", havingValue = EaSiteClientConfiguration.PROVIDER_NAME)
public class EaSiteProvider implements ExternalDataProvider {

    private static final Pattern BUILD_ID = Pattern.compile("\"buildId\":\"([A-Za-z0-9_-]+)\"");
    private static final String DEFAULT_LOCALE = "en";
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final IngestionProperties.EaSite config;
    private final RestClient restClient;
    private final RatingItemMapper mapper;
    private final Pattern expectedTitle;
    private String buildId;

    public EaSiteProvider(IngestionProperties properties,
                          @Qualifier(EaSiteClientConfiguration.EA_SITE_REST_CLIENT) RestClient restClient,
                          RatingItemMapper mapper) {
        this.config = properties.getEaSite();
        this.restClient = restClient;
        this.mapper = mapper;
        this.expectedTitle = Pattern.compile(config.getExpectedTitlePattern());
    }

    @Override
    public String name() {
        return EaSiteClientConfiguration.PROVIDER_NAME;
    }

    @Override
    public long pageDelayMillis() {
        return config.getPageDelay().toMillis();
    }

    @Override
    public synchronized ExternalPlayerPage fetch(int offset, int limit) {
        if (limit != config.getSitePageSize()) {
            throw new IllegalStateException("ea-site pages are fixed at " + config.getSitePageSize()
                    + " items; set fcareer.ingestion.page-size accordingly (was " + limit + ")");
        }
        int page = offset / limit + 1;
        JsonNode details = pageDetails(page, true);
        List<ExternalPlayer> players = new ArrayList<>();
        for (JsonNode item : details.path("items")) {
            mapper.toPlayer(item).ifPresent(players::add);
        }
        return new ExternalPlayerPage(players, details.path("totalItems").asInt());
    }

    private JsonNode pageDetails(int page, boolean allowBuildRefresh) {
        String id = ensureBuildId();
        try {
            JsonNode body = restClient.get()
                    .uri(uri -> uri.path("/_next/data/" + id + "/" + config.getLocale() + config.getRatingsPath() + ".json")
                            .queryParam("franchiseSlug", config.getFranchiseSlug())
                            .queryParam("page", page)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode details = Objects.isNull(body) ? null : body.path("pageProps").path("ratingDetails");
            if (Objects.isNull(details) || !details.has("items")) {
                throw new DownstreamServiceException(name());
            }
            return details;
        } catch (HttpClientErrorException.NotFound e) {
            if (allowBuildRefresh) {
                log.warn("ea-site data endpoint 404, refreshing buildId page : {}", page);
                buildId = null;
                return pageDetails(page, false);
            }
            throw new DownstreamServiceException(name());
        } catch (RestClientException e) {
            log.error("ea-site call failed page : {}", page, e);
            throw new DownstreamServiceException(name());
        }
    }

    private String ensureBuildId() {
        if (Objects.nonNull(buildId)) {
            return buildId;
        }
        try {
            String html = restClient.get()
                    .uri(uri -> uri.path(pagePath()).build())
                    .accept(MediaType.TEXT_HTML)
                    .retrieve()
                    .body(String.class);
            assertExpectedGame(html);
            Matcher matcher = Objects.isNull(html) ? null : BUILD_ID.matcher(html);
            if (Objects.isNull(matcher) || !matcher.find()) {
                log.error("ea-site buildId not found in ratings page");
                throw new DownstreamServiceException(name());
            }
            buildId = matcher.group(1);
            log.info("ea-site buildId resolved : {}", buildId);
            return buildId;
        } catch (RestClientException e) {
            log.error("ea-site ratings page could not be loaded", e);
            throw new DownstreamServiceException(name());
        }
    }

    private String pagePath() {
        // İngilizce site varsayılan locale: /en/... adresi 307 ile ön eksiz yola yönlenir.
        return DEFAULT_LOCALE.equals(config.getLocale()) ? config.getRatingsPath() : "/" + config.getLocale() + config.getRatingsPath();
    }

    private void assertExpectedGame(String html) {
        Matcher title = Objects.isNull(html) ? null : TITLE.matcher(html);
        String text = Objects.nonNull(title) && title.find() ? title.group(1).trim() : "";
        if (!expectedTitle.matcher(text).find()) {
            log.error("ea-site page is not the expected game, title : {} expected : {}", text, expectedTitle.pattern());
            throw new DownstreamServiceException(name());
        }
    }
}
