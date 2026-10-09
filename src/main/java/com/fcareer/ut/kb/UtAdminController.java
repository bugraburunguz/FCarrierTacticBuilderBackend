package com.fcareer.ut.kb;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.ingestion.service.IngestionRunRepository;
import com.fcareer.ut.kb.KbModels.QueueItem;
import com.fcareer.ut.repository.EaIdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.fcareer.common.constants.ApiConstants.ADMIN_PATH;

/** Admin CMS uçları (DATA-07): X-Admin-Key ile korunur. */
@RestController
@RequestMapping(ADMIN_PATH + "/ut")
@RequiredArgsConstructor
public class UtAdminController {

    private static final String ACTOR = "admin-key";
    private static final Map<String, String> KINDS = Map.of(
            "sbc-set", ContributionService.SBC_SET, "sbc-challenge", ContributionService.SBC_CHALLENGE,
            "objective-group", ContributionService.OBJECTIVE_GROUP, "evolution", ContributionService.EVOLUTION);

    public record ResolveRequest(String hash) {
    }

    public record PromoRequest(String code, String name, Instant startsAt, Instant endsAt) {
    }

    public record EaIdRequest(String kind, int eaId, String name) {
    }

    private final IngestionRunRepository ingestionRuns;
    private final EaIdRepository eaIds;
    private final KnowledgeBaseRepository repository;
    private final ContributionService contributionService;
    private final ContributorService contributorService;
    private final CatalogProperties catalogProperties;
    private final Clock clock;

    @GetMapping("/ingestion-runs")
    public List<Map<String, Object>> ingestionRuns() {
        return ingestionRuns.recent();
    }

    /** Kullanıcılardan gelen, eşiğe ulaşmamış EA lig/ülke kimlik önerileri (BUG-11). */
    @GetMapping("/ea-ids")
    public List<Map<String, Object>> eaIdSuggestions() {
        return eaIds.openSuggestions();
    }

    @PostMapping("/ea-ids/apply")
    public Map<String, Integer> applyEaId(@RequestBody EaIdRequest request) {
        if (request.name() == null || !java.util.Set.of("LEAGUE", "NATION").contains(request.kind())) {
            throw new InvalidTacticException("kind");
        }
        return Map.of("updated", eaIds.apply(request.kind(), request.eaId(), request.name()));
    }

    @PostMapping("/items/sync-base")
    public Map<String, Integer> syncBase() {
        return Map.of("upserted", repository.syncBaseItems(catalogProperties.getDefaultGameVersion()));
    }

    @GetMapping("/queue")
    public List<QueueItem> queue() {
        return repository.openQueue();
    }

    /** hash verilirse o içerik uygulanır; boşsa kuyruk maddesi reddedilir. */
    @PostMapping("/queue/{id}/resolve")
    public Map<String, String> resolve(@PathVariable long id, @RequestBody ResolveRequest request) {
        contributionService.resolve(id, request.hash());
        return Map.of("status", request.hash() == null ? "REJECTED" : "RESOLVED");
    }

    @PutMapping("/{kind}/{id}")
    public Map<String, String> upsert(@PathVariable String kind, @PathVariable long id, @RequestBody ObjectNode payload) {
        String normalized = KINDS.get(kind);
        if (normalized == null) {
            throw new InvalidTacticException("kind");
        }
        return Map.of("hash", contributionService.adminUpsert(normalized, id, payload));
    }

    @PutMapping("/promos")
    public Map<String, String> promo(@RequestBody PromoRequest request) {
        if (request.code() == null || request.code().isBlank() || request.name() == null || request.name().isBlank()) {
            throw new InvalidTacticException("promo");
        }
        repository.upsertPromo(catalogProperties.getDefaultGameVersion(), request.code().trim().toUpperCase(), request.name().trim(), request.startsAt(), request.endsAt());
        return Map.of("status", "OK");
    }

    @PostMapping("/contributors/{userId}/grant")
    public Map<String, String> grant(@PathVariable long userId) {
        contributorService.grant(userId, ACTOR);
        return Map.of("status", "GRANTED");
    }

    @PostMapping("/contributors/{userId}/revoke")
    public Map<String, String> revoke(@PathVariable long userId) {
        contributorService.revoke(userId, ACTOR);
        return Map.of("status", "REVOKED");
    }

    @PostMapping("/prices/aggregate")
    public Map<String, Integer> aggregate() {
        return Map.of("buckets", repository.aggregatePrices(clock.instant().minus(Duration.ofHours(48))));
    }
}
