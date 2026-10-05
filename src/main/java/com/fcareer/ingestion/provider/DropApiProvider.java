package com.fcareer.ingestion.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fcareer.common.exception.DownstreamServiceException;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.configuration.IngestionProperties;
import com.fcareer.ingestion.model.ExternalPlayer;
import com.fcareer.ingestion.model.ExternalPlayerPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "fcareer.ingestion", name = "provider", havingValue = "drop-api")
public class DropApiProvider implements ExternalDataProvider {

    private static final String DROP_API = "drop-api";
    private static final DateTimeFormatter BIRTHDATE = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US);
    private static final String PLAYSTYLE_PLUS = "playStylePlus";
    private static final String LEFT_FOOT = "Left";
    private static final String RIGHT_FOOT = "Right";
    private static final int FOOT_LEFT_CODE = 2;
    private static final int FOOT_RIGHT_CODE = 1;

    private final IngestionProperties.DropApi config;
    private final RestClient restClient;

    public DropApiProvider(IngestionProperties properties) {
        this.config = properties.getDropApi();
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(config.getTimeout())
                .withReadTimeout(config.getTimeout());
        this.restClient = RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .baseUrl(config.getBaseUrl())
                .build();
    }

    @Override
    public String name() {
        return DROP_API;
    }

    @Override
    public ExternalPlayerPage fetch(int offset, int limit) {
        JsonNode root = call(offset, limit);
        List<ExternalPlayer> players = new ArrayList<>();
        for (JsonNode item : root.path("items")) {
            toPlayer(item).ifPresent(players::add);
        }
        return new ExternalPlayerPage(players, root.path("totalItems").asInt());
    }

    private JsonNode call(int offset, int limit) {
        try {
            JsonNode body = restClient.get()
                    .uri(uri -> uri.queryParam("limit", limit).queryParam("offset", offset)
                            .queryParam("locale", config.getLocale()).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            if (Objects.isNull(body) || !body.has("items")) {
                throw new DownstreamServiceException(DROP_API);
            }
            return body;
        } catch (RestClientException e) {
            log.error("drop-api call failed offset : {} limit : {}", offset, limit, e);
            throw new DownstreamServiceException(DROP_API);
        }
    }

    private Optional<ExternalPlayer> toPlayer(JsonNode item) {
        Map<Attr, Integer> attrs = attrs(item.path("stats"));
        if (attrs.size() != Attr.values().length || !item.has("id")) {
            log.warn("drop-api item skipped id : {} attrCount : {}", item.path("id").asText(), attrs.size());
            return Optional.empty();
        }
        JsonNode team = item.path("team");
        return Optional.of(new ExternalPlayer(
                item.path("id").asLong(),
                fullName(item),
                birthdate(item.path("birthdate").asText(null)),
                textOrNull(item.path("nationality").path("label")),
                intOrNull(item.path("height")),
                intOrNull(item.path("weight")),
                foot(item.path("preferredFoot").asInt()),
                textOrNull(item.path("leagueName")),
                team.has("id") ? team.path("id").asLong() : null,
                textOrNull(team.path("label")),
                item.path("overallRating").asInt(),
                positions(item),
                intOrNull(item.path("weakFootAbility")),
                intOrNull(item.path("skillMoves")),
                attrs,
                playstyles(item.path("playerAbilities"))));
    }

    private String fullName(JsonNode item) {
        String common = textOrNull(item.path("commonName"));
        if (Objects.nonNull(common)) {
            return common;
        }
        return (item.path("firstName").asText("") + " " + item.path("lastName").asText("")).trim();
    }

    private LocalDate birthdate(String raw) {
        if (Objects.isNull(raw) || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw, BIRTHDATE);
        } catch (DateTimeParseException e) {
            log.warn("drop-api birthdate unparsable value : {}", raw);
            return null;
        }
    }

    private String foot(int code) {
        if (code == FOOT_LEFT_CODE) {
            return LEFT_FOOT;
        }
        return code == FOOT_RIGHT_CODE ? RIGHT_FOOT : null;
    }

    private List<String> positions(JsonNode item) {
        Set<String> positions = new LinkedHashSet<>();
        positions.add(item.path("position").path("shortLabel").asText());
        for (JsonNode alt : item.path("alternatePositions")) {
            positions.add(alt.path("shortLabel").asText());
        }
        positions.removeIf(String::isBlank);
        return List.copyOf(positions);
    }

    private Map<Attr, Integer> attrs(JsonNode stats) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        DropApiStatKeys.KEYS.forEach((attr, key) -> {
            JsonNode value = stats.path(key).path("value");
            if (value.isNumber()) {
                attrs.put(attr, value.asInt());
            }
        });
        return attrs;
    }

    private Map<String, Integer> playstyles(JsonNode abilities) {
        Map<String, Integer> playstyles = new LinkedHashMap<>();
        for (JsonNode ability : abilities) {
            String label = ability.path("label").asText("");
            boolean plus = PLAYSTYLE_PLUS.equals(ability.path("type").path("id").asText());
            String key = label.replace("+", "").replaceAll("[^A-Za-z0-9]", "");
            if (!key.isBlank()) {
                playstyles.merge(key, plus ? 2 : 1, Math::max);
            }
        }
        return playstyles;
    }

    private static String textOrNull(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private static Integer intOrNull(JsonNode node) {
        return node.isNumber() ? node.asInt() : null;
    }
}
