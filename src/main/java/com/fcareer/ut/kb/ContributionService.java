package com.fcareer.ut.kb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.common.exception.InvalidTacticException;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.ut.kb.ContributorService.Contributor;
import com.fcareer.ut.kb.KbModels.ContributionResult;
import com.fcareer.ut.model.UtCaptureModels.Capture;
import com.fcareer.ut.model.UtCaptureModels.CaptureObjective;
import com.fcareer.ut.model.UtCaptureModels.CaptureResult;
import com.fcareer.ut.model.UtCaptureModels.EvolutionSlot;
import com.fcareer.ut.model.UtCaptureModels.MarketPrice;
import com.fcareer.ut.model.UtCaptureModels.ObjectiveGroupView;
import com.fcareer.ut.model.UtCaptureModels.SbcChallengeView;
import com.fcareer.ut.model.UtCaptureModels.SbcSetView;
import com.fcareer.ut.service.UtCaptureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Katkı hattı (03 §3): yakalamayı mevcut ayrıştırıcıyla işler, yalnız global (kişisel olmayan) tanımları beyaz listeyle alır
 * (SBC set/challenge, objective grubu, evolution, market fiyatı). Kulüp envanteri, coin, kadro, ilerleme/tamamlanma durumu asla yazılmaz.
 * Konsensüs: yeni kayıt güvenilir katkıcıdan hemen CONFIRMED; mevcut kayıtla çelişen içerik ≥2 bağımsız katkıcıdan gelirse uygulanır, aksi halde admin kuyruğuna düşer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContributionService {

    public static final String SBC_SET = "SBC_SET";
    public static final String SBC_CHALLENGE = "SBC_CHALLENGE";
    public static final String OBJECTIVE_GROUP = "OBJECTIVE_GROUP";
    public static final String EVOLUTION = "EVOLUTION";

    private static final long MAX_REMAINING_SECONDS = 120L * 86_400;
    private static final long MIN_EPOCH = 1_000_000_000L;
    private static final long MAX_EPOCH = 4_000_000_000L;
    private static final int CONFIRMING_CONTRIBUTORS = 2;
    private static final int CONFIRM_REWARD = 1;
    private static final int CONFLICT_PENALTY = -3;
    private static final Set<String> PLATFORMS = Set.of("CONSOLE", "PC");

    private final UtCaptureService captureService;
    private final KnowledgeBaseRepository repository;
    private final ContributorService contributorService;
    private final CatalogProperties catalogProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ObjectMapper canonical = com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

    private record Entity(String kind, long id, ObjectNode payload, Instant endsAt) {
        String key() {
            return kind + ":" + id;
        }
    }

    @Transactional
    public ContributionResult contribute(Long userId, List<Capture> captures, String platform) {
        Contributor contributor = contributorService.require(userId);
        String normalizedPlatform = Objects.nonNull(platform) && PLATFORMS.contains(platform.toUpperCase()) ? platform.toUpperCase() : "CONSOLE";
        CaptureResult result = captureService.process(captures, userId);
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int queued = 0;
        for (Entity entity : entities(result)) {
            String hash = hash(entity.payload());
            repository.insertEvent(contributor.id(), entity.kind(), entity.key(), hash, entity.payload());
            switch (decide(contributor, entity, hash)) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
                case QUEUED -> queued++;
            }
        }
        int[] prices = recordPrices(contributor, result.prices(), normalizedPlatform);
        log.info("Contribution processed contributorId : {} created : {} updated : {} unchanged : {} queued : {} prices : {}", contributor.id(), created, updated, unchanged, queued, prices[0]);
        return new ContributionResult(created, updated, unchanged, queued, prices[0], prices[1]);
    }

    private enum Outcome { CREATED, UPDATED, UNCHANGED, QUEUED }

    private Outcome decide(Contributor contributor, Entity entity, String hash) {
        boolean trusted = contributor.trust() >= ContributorService.TRUSTED_THRESHOLD;
        String table = table(entity.kind());
        var stored = repository.hashOf(table, entity.id());
        if (stored.isEmpty() || "stub".equals(stored.get())) {
            apply(entity, hash, trusted ? "CONFIRMED" : "PENDING");
            return Outcome.CREATED;
        }
        if (stored.get().equals(hash)) {
            return Outcome.UNCHANGED;
        }
        if (repository.distinctContributors(entity.kind(), entity.key(), hash) >= CONFIRMING_CONTRIBUTORS) {
            apply(entity, hash, "CONFIRMED");
            repository.contributorsOf(entity.kind(), entity.key(), hash).forEach(id -> contributorService.adjustTrust(id, CONFIRM_REWARD));
            return Outcome.UPDATED;
        }
        ArrayNode candidates = objectMapper.createArrayNode();
        candidates.add(objectMapper.createObjectNode().put("hash", stored.get()).put("source", "stored"));
        candidates.add(objectMapper.createObjectNode().put("hash", hash).put("source", "contributor").set("payload", entity.payload()));
        repository.openQueue(entity.kind(), entity.key(), candidates);
        return Outcome.QUEUED;
    }

    /** Admin kuyruğundan seçilen içerik uygulanır; doğru çıkan hash'in katkıcıları güven kazanır, çelişenler kaybeder. */
    @Transactional
    public void resolve(long queueId, String chosenHash) {
        var item = repository.queueItem(queueId).orElseThrow(() -> new NotFoundException("queue", queueId));
        if (Objects.isNull(chosenHash)) {
            repository.closeQueue(queueId, "REJECTED");
            return;
        }
        long id = Long.parseLong(item.naturalKey().substring(item.naturalKey().indexOf(':') + 1));
        JsonNode payload = repository.latestPayload(item.kind(), item.naturalKey(), chosenHash).orElseThrow(() -> new NotFoundException("candidate", chosenHash));
        apply(new Entity(item.kind(), id, (ObjectNode) payload, null), chosenHash, "CONFIRMED");
        repository.contributorsOf(item.kind(), item.naturalKey(), chosenHash).forEach(c -> contributorService.adjustTrust(c, CONFIRM_REWARD));
        item.candidates().forEach(candidate -> {
            String other = candidate.path("hash").asText();
            if (!other.equals(chosenHash)) {
                repository.contributorsOf(item.kind(), item.naturalKey(), other).forEach(c -> contributorService.adjustTrust(c, CONFLICT_PENALTY));
            }
        });
        repository.closeQueue(queueId, "RESOLVED");
    }

    /** Admin elle girişi (DATA-07): doğrudan CONFIRMED yazılır; sonradan gelen çelişkili katkı kuyruğa düşer. */
    @Transactional
    public String adminUpsert(String kind, long id, ObjectNode payload) {
        if (!Set.of(SBC_SET, SBC_CHALLENGE, OBJECTIVE_GROUP, EVOLUTION).contains(kind)) {
            throw new InvalidTacticException("kind");
        }
        payload.put("id", id);
        String hash = hash(payload);
        apply(new Entity(kind, id, payload, null), hash, "CONFIRMED");
        return hash;
    }

    private void apply(Entity entity, String hash, String status) {
        short gv = catalogProperties.getDefaultGameVersion();
        ObjectNode p = entity.payload();
        switch (entity.kind()) {
            case SBC_SET -> repository.upsertSbcSet(entity.id(), gv, p.path("name").asText("SBC #" + entity.id()), text(p, "description"), p.path("category").asText("OTHER"),
                    p.path("repeatable").asBoolean(false) ? -1 : null, entity.endsAt(), p.path("reward").isMissingNode() ? objectMapper.createArrayNode() : p.path("reward"), status, hash);
            case SBC_CHALLENGE -> {
                long setId = p.path("setId").asLong();
                repository.ensureSetStub(setId, gv, text(p, "setName"));
                repository.upsertSbcChallenge(entity.id(), setId, p.path("name").asText("Challenge #" + entity.id()), text(p, "formationLabel"),
                        p.path("kind").asText("SQUAD"), p.path("requirements").isMissingNode() ? objectMapper.createArrayNode() : p.path("requirements"),
                        p.hasNonNull("itemScoreTarget") ? p.path("itemScoreTarget").asInt() : null, p.path("awards"), hash);
            }
            case OBJECTIVE_GROUP -> {
                repository.upsertObjectiveGroup(entity.id(), gv, p.path("name").asText("Grup #" + entity.id()), text(p, "gameMode"), null, entity.endsAt(), p.path("rewards"), p.path("objectives"), hash);
                if (!"CONFIRMED".equals(status)) {
                    repository.setStatus("objective_group", entity.id(), status);
                }
            }
            case EVOLUTION -> {
                repository.upsertEvolution(entity.id(), gv, p.path("name").asText("Evo #" + entity.id()), p.path("cost"), p.path("requirements"), p.path("levels"), entity.endsAt(), hash);
                if (!"CONFIRMED".equals(status)) {
                    repository.setStatus("evolution", entity.id(), status);
                }
            }
            default -> throw new InvalidTacticException("kind");
        }
    }

    private List<Entity> entities(CaptureResult result) {
        Instant now = clock.instant();
        List<Entity> entities = new ArrayList<>();
        for (SbcSetView set : result.sbcSets()) {
            ObjectNode p = objectMapper.createObjectNode();
            p.put("name", set.name());
            p.put("category", set.category());
            p.put("challengesCount", set.challengesCount());
            p.put("repeatable", set.repeatable());
            p.put("awardCount", set.awardCount());
            entities.add(new Entity(SBC_SET, set.setId(), p, remainingToInstant(set.endTime(), now)));
        }
        for (SbcChallengeView challenge : result.sbcChallenges()) {
            ObjectNode p = objectMapper.createObjectNode();
            p.put("setId", challenge.setId());
            if (Objects.nonNull(challenge.setName())) {
                p.put("setName", challenge.setName());
            }
            p.put("name", challenge.name());
            p.put("formation", challenge.formation());
            p.put("formationLabel", challenge.formationLabel());
            p.put("kind", "ONE_CLICK".equals(challenge.type()) || "BRICK".equals(challenge.type()) ? "ITEM_SCORE" : "SQUAD");
            p.put("type", challenge.type());
            p.set("awards", objectMapper.valueToTree(challenge.awards()));
            p.set("requirements", objectMapper.valueToTree(challenge.requirements()));
            entities.add(new Entity(SBC_CHALLENGE, challenge.challengeId(), p, null));
        }
        for (ObjectiveGroupView group : result.objectives()) {
            ObjectNode p = objectMapper.createObjectNode();
            p.put("name", group.name());
            p.put("gameMode", group.gameMode());
            p.set("rewards", objectMapper.valueToTree(group.rewards()));
            ArrayNode objectives = objectMapper.createArrayNode();
            group.objectives().stream().sorted(Comparator.comparingLong(CaptureObjective::id)).forEach(o -> {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("id", o.id());
                node.put("name", o.name());
                node.put("description", o.description());
                node.put("total", o.total());
                node.set("lockedBy", objectMapper.valueToTree(o.lockedBy()));
                node.set("rewards", objectMapper.valueToTree(o.rewards()));
                objectives.add(node);
            });
            p.set("objectives", objectives);
            entities.add(new Entity(OBJECTIVE_GROUP, group.groupId(), p, epochToInstant(group.endTime())));
        }
        for (EvolutionSlot evo : result.evolutions()) {
            ObjectNode p = objectMapper.createObjectNode();
            p.put("name", evo.name());
            p.put("levels", evo.levels());
            p.put("requirementCount", evo.requirements());
            entities.add(new Entity(EVOLUTION, evo.id(), p, remainingToInstant(evo.endTime(), now)));
        }
        return entities;
    }

    /** Market fiyatı gözlemi: satıcı/alıcı kimliği hiç taşınmaz; yalnız (kart, platform, zaman, fiyat). Tanınmayan kart atlanır. */
    private int[] recordPrices(Contributor contributor, List<MarketPrice> prices, String platform) {
        short gv = catalogProperties.getDefaultGameVersion();
        Instant now = clock.instant();
        int recorded = 0;
        int skipped = 0;
        for (MarketPrice price : prices) {
            var itemId = repository.itemIdByAsset(price.assetId(), price.rating(), gv);
            if (itemId.isEmpty()) {
                skipped++;
                continue;
            }
            if (Objects.nonNull(price.buyNow()) && price.buyNow() > 0 && price.buyNow() <= Integer.MAX_VALUE) {
                repository.insertPriceObservation(itemId.get(), platform, now, "BIN", price.buyNow().intValue(), contributor.id());
                recorded++;
            }
            if (Objects.nonNull(price.lastSale()) && price.lastSale() > 0 && price.lastSale() <= Integer.MAX_VALUE) {
                repository.insertPriceObservation(itemId.get(), platform, now, "LAST_SALE", price.lastSale().intValue(), contributor.id());
                recorded++;
            }
        }
        return new int[]{recorded, skipped};
    }

    private static String table(String kind) {
        return switch (kind) {
            case SBC_SET -> "sbc_set";
            case SBC_CHALLENGE -> "sbc_challenge";
            case OBJECTIVE_GROUP -> "objective_group";
            case EVOLUTION -> "evolution";
            default -> throw new InvalidTacticException("kind");
        };
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static Instant remainingToInstant(Long seconds, Instant now) {
        return Objects.nonNull(seconds) && seconds > 0 && seconds <= MAX_REMAINING_SECONDS ? now.plusSeconds(seconds) : null;
    }

    private static Instant epochToInstant(Long epoch) {
        return Objects.nonNull(epoch) && epoch >= MIN_EPOCH && epoch <= MAX_EPOCH ? Instant.ofEpochSecond(epoch) : null;
    }

    private String hash(JsonNode payload) {
        try {
            String text = canonical.writeValueAsString(canonical.treeToValue(payload, Object.class));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
