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
import com.fcareer.ut.service.RewardAllowlist;
import com.fcareer.ut.service.UtCaptureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private static final Set<String> TEXT_FIELDS = Set.of("name", "description", "setName", "formationLabel", "category", "label", "title");
    /** Bizim ham alanlardan türettiğimiz alanlar (V3-05d-b): içerik kimliğine girmez. */
    private static final Set<String> DERIVED_FIELDS = Set.of("kind");
    private static final Set<String> HASH_SKIPPED = java.util.stream.Stream.concat(TEXT_FIELDS.stream(), DERIVED_FIELDS.stream()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final String CONFIRMED = "CONFIRMED";
    private static final String UNDETERMINED_LOCALE = "und";
    private static final int MAX_LOCALE = 16;
    /** Yakalama saati sunucudan en çok bu kadar uzaksa olduğu gibi kabul edilir; fiyat toplama penceresi de bu (UtScheduledJobs). */
    static final Duration MAX_CLOCK_SKEW = Duration.ofHours(48);
    private static final String MARKET_SOURCE = "transfermarket";
    private static final Set<String> PLATFORMS = Set.of("CONSOLE", "PC");

    private final UtCaptureService captureService;
    private final KnowledgeBaseRepository repository;
    private final ContributorService contributorService;
    private final CatalogProperties catalogProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final CaptureRules captureRules;
    private final ObjectMapper canonical = com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

    private record Entity(String kind, long id, ObjectNode payload, Instant endsAt) {
        String key() {
            return kind + ":" + id;
        }
    }

    @Transactional
    public ContributionResult contribute(Long userId, List<Capture> captures, String platform, String locale) {
        Contributor contributor = contributorService.require(userId);
        String normalizedPlatform = Objects.nonNull(platform) && PLATFORMS.contains(platform.toUpperCase()) ? platform.toUpperCase() : "CONSOLE";
        CaptureResult result = captureService.process(captures.stream().filter(c -> Objects.isNull(c.url()) || !captureRules.isPersonalOnly(c.url())).toList(), userId);
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int queued = 0;
        String textLocale = Objects.isNull(locale) || locale.isBlank() || locale.length() > MAX_LOCALE ? UNDETERMINED_LOCALE : locale;
        for (Entity entity : entities(result)) {
            String hash = hash(structural(entity.payload()));
            repository.upsertText(entity.kind(), entity.key(), textLocale, text(entity.payload(), "name"), text(entity.payload(), "description"));
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
        if (stored.isEmpty() || KnowledgeBaseRepository.STUB_HASH.equals(stored.get())) {
            apply(entity, hash, trusted ? "CONFIRMED" : "PENDING");
            return Outcome.CREATED;
        }
        if (stored.get().equals(hash)) {
            repository.touch(table, entity.id(), entity.endsAt());
            if (SBC_CHALLENGE.equals(entity.kind())) {
                // İçerik aynı; türetilmiş sütunlar (formasyon kimliği, EA tipi, kind) güncel kodla yeniden yazılır (V3-05d-b).
                apply(entity, hash, CONFIRMED);
            }
            return Outcome.UNCHANGED;
        }
        if (SBC_CHALLENGE.equals(entity.kind()) && stored.get().equals(legacyChallengeHash(entity.payload(), repository.challengeKind(entity.id()).orElse(null)))) {
            // Eski tanımlı hash (türetilmiş kind dahildi): içerik değişmedi, yalnız bizim türetmemiz değişti → çatışma değil, yeni tanıma geçilir.
            apply(entity, hash, CONFIRMED);
            repository.closeOpenQueue(entity.kind(), entity.key(), "RESOLVED");
            return Outcome.UPDATED;
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
        String hash = hash(structural(payload));
        apply(new Entity(kind, id, payload, null), hash, "CONFIRMED");
        return hash;
    }

    /** İstemci saati bozuksa (sunucudan MAX_CLOCK_SKEW kadar uzak) sunucu saati kullanılır. */
    private Instant captureTime(Long capturedAtMillis) {
        return Objects.isNull(capturedAtMillis) || capturedAtMillis <= 0 || isSkewed(capturedAtMillis) ? clock.instant() : Instant.ofEpochMilli(capturedAtMillis);
    }

    /** Capture saati verilmiş ama sunucudan MAX_CLOCK_SKEW kadar uzaksa true (BE-BUG-06/07 saat koruması). */
    private boolean isSkewed(Long capturedAtMillis) {
        return Objects.nonNull(capturedAtMillis) && capturedAtMillis > 0
                && Duration.between(Instant.ofEpochMilli(capturedAtMillis), clock.instant()).abs().compareTo(MAX_CLOCK_SKEW) > 0;
    }

    /**
     * V3-05d-b — EA görev tipi → KB türü (BEHAVIOR CHANGE): ONE_CLICK_CHALLENGE → SINGLE_ITEM (tek kart teslimi; yakalamada şartlar yalnız
     * PLAYER_ATTRIBUTE/ACADEMY_PLAYER_SLOTTING/SCOPE, formasyon yok). BRICK_CHALLENGE ve diğerleri formasyonlu kadro (SQUAD); BRICK'in anlamı doğrulanmadı.
     * Önceki eşleme "ONE_CLICK"/"BRICK" adlarına bakıyordu, EA'nın gönderdiği "*_CHALLENGE" adlarıyla hiç eşleşmiyordu.
     */
    static String kindOf(String eaType) {
        return Objects.nonNull(eaType) && eaType.startsWith("ONE_CLICK") ? "SINGLE_ITEM" : "SQUAD";
    }

    private static Integer repeatable(ObjectNode p) {
        if (p.hasNonNull("repeatCount") && p.get("repeatCount").asInt() > 0) {
            return p.get("repeatCount").asInt();
        }
        return p.path("repeatable").asBoolean(false) ? -1 : null;
    }

    private void apply(Entity entity, String hash, String status) {
        short gv = catalogProperties.getDefaultGameVersion();
        ObjectNode p = entity.payload();
        switch (entity.kind()) {
            case SBC_SET -> repository.upsertSbcSet(entity.id(), gv, p.path("name").asText("SBC #" + entity.id()), text(p, "description"), p.path("category").asText("OTHER"),
                    repeatable(p), entity.endsAt(), RewardAllowlist.rewards(p.path("reward"), objectMapper), status, hash);
            case SBC_CHALLENGE -> {
                long setId = p.path("setId").asLong();
                repository.ensureSetStub(setId, gv, text(p, "setName"));
                repository.upsertSbcChallenge(entity.id(), setId, p.path("name").asText("Challenge #" + entity.id()), text(p, "formationLabel"), text(p, "formation"), text(p, "type"),
                        p.path("kind").asText("SQUAD"), p.path("requirements").isMissingNode() ? objectMapper.createArrayNode() : p.path("requirements"),
                        p.hasNonNull("itemScoreTarget") ? p.path("itemScoreTarget").asInt() : null, RewardAllowlist.rewards(p.path("awards"), objectMapper), hash);
            }
            case OBJECTIVE_GROUP -> {
                repository.upsertObjectiveGroup(entity.id(), gv, p.path("name").asText("Grup #" + entity.id()), text(p, "gameMode"), null, entity.endsAt(),
                        RewardAllowlist.rewards(p.path("rewards"), objectMapper), RewardAllowlist.objectives(p.path("objectives"), objectMapper), hash);
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

    /** Bitiş zamanı capture anına göre hesaplanır (kalan saniye o an ölçüldü); ts yoksa sunucu saati. */
    private List<Entity> entities(CaptureResult result) {
        Instant now = captureTime(result.capturedAt());
        List<Entity> entities = new ArrayList<>();
        for (SbcSetView set : result.sbcSets()) {
            ObjectNode p = objectMapper.createObjectNode();
            p.put("name", set.name());
            p.put("category", set.category());
            p.put("categoryId", set.categoryId());
            p.put("challengesCount", set.challengesCount());
            p.put("repeatable", set.repeatable());
            p.put("awardCount", set.awardCount());
            p.set("reward", objectMapper.valueToTree(set.awards()));
            if (Objects.nonNull(set.repeatCount())) {
                p.put("repeatCount", set.repeatCount());
            }
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
            p.put("kind", kindOf(challenge.type()));
            p.put("type", challenge.type());
            p.set("awards", objectMapper.valueToTree(challenge.awards()));
            p.set("requirements", objectMapper.valueToTree(challenge.requirements()));
            if (Objects.nonNull(challenge.scoreRequirement())) {
                p.put("itemScoreTarget", challenge.scoreRequirement());
            }
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

    /** Market fiyatı gözlemi (BE-BUG-01/03): yalnız canlı transfer pazarı sonuçları, yalnız (oyun sürümü, EA resourceId) ile eşlenen item; satıcı/alıcı kimliği taşınmaz. */
    private int[] recordPrices(Contributor contributor, List<MarketPrice> prices, String platform) {
        short gv = catalogProperties.getDefaultGameVersion();
        int recorded = 0;
        int unknown = 0;
        int noResource = 0;
        int skewed = 0;
        for (MarketPrice price : prices) {
            if (!MARKET_SOURCE.equals(price.source()) || Objects.isNull(price.expiresIn()) || price.expiresIn() <= 0) {
                continue;
            }
            if (Objects.isNull(price.resourceId())) {
                noResource++;
                continue;
            }
            var itemId = repository.itemIdByResource(price.resourceId(), gv);
            if (itemId.isEmpty()) {
                unknown++;
                continue;
            }
            if (Objects.nonNull(price.buyNow()) && price.buyNow() > 0 && price.buyNow() <= Integer.MAX_VALUE) {
                Instant at = captureTime(price.capturedAt());
                if (isSkewed(price.capturedAt())) {
                    skewed++;
                }
                repository.insertPriceObservation(itemId.get(), platform, at, "BIN", price.buyNow().intValue(), contributor.id());
                recorded++;
            }
        }
        if (unknown + noResource > 0) {
            log.warn("Price observations skipped contributorId : {} unknownItem : {} noResourceId : {}", contributor.id(), unknown, noResource);
        }
        repository.addPriceStats(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC), recorded, unknown, noResource, skewed);
        return new int[]{recorded, unknown + noResource};
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

    /**
     * Hash yalnız yapıyı kapsar (BE-BUG-07): lokalize ad/açıklama/etiketler her derinlikte çıkarılır, id'ler ve sayılar kalır.
     * V3-05d-b: bizim kodumuzun ham alanlardan türettiği alanlar (DERIVED_FIELDS, ör. görev `kind`) da çıkarılır; türetme düzeltilince katkılar çatışmaz.
     */
    private JsonNode structural(JsonNode payload) {
        return structural(payload, HASH_SKIPPED);
    }

    private JsonNode structural(JsonNode payload, Set<String> skipped) {
        if (payload.isObject()) {
            ObjectNode copy = objectMapper.createObjectNode();
            payload.fields().forEachRemaining(e -> {
                if (!skipped.contains(e.getKey())) {
                    copy.set(e.getKey(), structural(e.getValue(), skipped));
                }
            });
            return copy;
        }
        if (payload.isArray()) {
            ArrayNode copy = objectMapper.createArrayNode();
            payload.forEach(n -> copy.add(structural(n, skipped)));
            return copy;
        }
        return payload;
    }

    /**
     * V3-05d-b geçişi: V3-05d-b öncesi görev hash'i türetilmiş `kind`'ı da içeriyordu. Saklı satırın kind'ıyla eski tanımla hesaplanır;
     * saklı hash'e eşitse içerik aynıdır (yalnız türetme değişmiştir).
     */
    public String legacyChallengeHash(ObjectNode payload, String storedKind) {
        ObjectNode copy = payload.deepCopy();
        if (Objects.nonNull(storedKind)) {
            copy.put("kind", storedKind);
        }
        return hash(structural(copy, TEXT_FIELDS));
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
