package com.fcareer.ut.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.ut.model.UtCaptureModels.ActiveSquad;
import com.fcareer.ut.model.UtCaptureModels.Capture;
import com.fcareer.ut.model.UtCaptureModels.CaptureCard;
import com.fcareer.ut.model.UtCaptureModels.CaptureConfig;
import com.fcareer.ut.model.UtCaptureModels.CaptureObjective;
import com.fcareer.ut.model.UtCaptureModels.CaptureResult;
import com.fcareer.ut.model.UtCaptureModels.CaptureStats;
import com.fcareer.ut.model.UtCaptureModels.CurrencyView;
import com.fcareer.ut.model.UtCaptureModels.EvolutionSlot;
import com.fcareer.ut.model.UtCaptureModels.FormationConfig;
import com.fcareer.ut.model.UtCaptureModels.MarketPrice;
import com.fcareer.ut.model.UtCaptureModels.ObjectiveGroupView;
import com.fcareer.ut.model.UtCaptureModels.PositionConfig;
import com.fcareer.ut.model.UtCaptureModels.Reward;
import com.fcareer.ut.model.UtCaptureModels.SbcDetail;
import com.fcareer.ut.model.UtCaptureModels.SbcSetView;
import com.fcareer.ut.model.UtCaptureModels.SquadSlot;
import com.fcareer.ut.model.UtCaptureModels.SquadSummary;
import com.fcareer.ut.model.UtCaptureModels.StyleMod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extension yakalamalarını (ut_capture_mapping.md) işler: whitelist dışını ve oturum/hesap uçlarını atar, tanım JOIN'i yapar.
 * Gövdeler loglanmaz ve saklanmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UtCaptureService {

    private static final List<String> DENY = List.of("/auth", "accountinfo", "connect", "nucleus", "phishing", "/fc/user/season", "sid=");
    private static final String USER_ENDPOINT = "/fc/user";
    private static final Pattern OBJECT_WITH_ID = Pattern.compile("\\{[^{}]*\"id\"\\s*:\\s*\\d+[^{}]*}");
    private static final List<String> OUTFIELD_FACE = List.of("PAC", "SHO", "PAS", "DRI", "DEF", "PHY");
    private static final List<String> GOALKEEPER_FACE = List.of("DIV", "HAN", "KIC", "REF", "SPD", "POS");
    private static final String GOALKEEPER = "GK";
    private static final int STARTERS = 11;
    private static final int MAX_SBC_DETAILS = 50;

    private final ObjectMapper objectMapper;

    enum Kind {
        RARITY, DEFINITIONS, SQUAD_DATA, KEY_ATTRIBUTES, CLUB, STORAGE, PURCHASED, SQUAD_ACTIVE, SQUAD_LIST, TRANSFER_MARKET, TRADE_PILE,
        WATCH_LIST, TRADE_STATUS, SBC_HUB, SBC_DETAIL, OBJECTIVES, ACADEMY, CREDITS
    }

    public CaptureResult process(List<Capture> captures) {
        int stripped = 0;
        int ignored = 0;
        int invalid = 0;
        Map<Kind, List<JsonNode>> bodies = new EnumMap<>(Kind.class);
        Map<Kind, List<Capture>> raw = new EnumMap<>(Kind.class);
        List<String> warnings = new ArrayList<>();
        List<Capture> ordered = captures.stream().sorted(Comparator.comparing(c -> Objects.requireNonNullElse(c.ts(), 0L))).toList();
        for (Capture capture : ordered) {
            if (Objects.isNull(capture.url()) || capture.url().isBlank() || Objects.isNull(capture.body())) {
                invalid++;
                continue;
            }
            String url = capture.url().toLowerCase(Locale.ROOT);
            if (isDenied(url)) {
                stripped++;
                continue;
            }
            Kind kind = classify(path(url));
            if (Objects.isNull(kind)) {
                ignored++;
                continue;
            }
            raw.computeIfAbsent(kind, k -> new ArrayList<>()).add(capture);
            if (kind == Kind.DEFINITIONS) {
                continue;
            }
            try {
                bodies.computeIfAbsent(kind, k -> new ArrayList<>()).add(objectMapper.readTree(capture.body()));
            } catch (IOException e) {
                invalid++;
                warnings.add("Çözülemeyen yanıt: " + kind);
            }
        }

        Definitions definitions = definitions(raw.getOrDefault(Kind.DEFINITIONS, List.of()));
        Map<Integer, String> rarities = rarities(bodies.getOrDefault(Kind.RARITY, List.of()));
        CaptureConfig config = config(bodies, rarities);
        Map<Integer, String> positionNames = config.positions().stream().collect(HashMap::new, (m, p) -> m.put(p.uniqueId(), p.uniqueName()), HashMap::putAll);
        Map<String, FormationConfig> formationsByName = config.formations().stream().collect(HashMap::new, (m, f) -> m.put(f.name(), f), HashMap::putAll);
        Set<Long> duplicates = duplicateIds(bodies.getOrDefault(Kind.STORAGE, List.of()));

        Map<Long, CaptureCard> cards = new LinkedHashMap<>();
        addCards(cards, bodies.get(Kind.CLUB), "club", definitions, rarities, duplicates);
        addCards(cards, bodies.get(Kind.STORAGE), "storage", definitions, rarities, duplicates);
        addCards(cards, bodies.get(Kind.PURCHASED), "purchased", definitions, rarities, duplicates);

        List<MarketPrice> prices = new ArrayList<>();
        addPrices(prices, bodies.get(Kind.TRANSFER_MARKET), "transfermarket", definitions);
        addPrices(prices, bodies.get(Kind.TRADE_PILE), "tradepile", definitions);
        addPrices(prices, bodies.get(Kind.WATCH_LIST), "watchlist", definitions);
        addPrices(prices, bodies.get(Kind.TRADE_STATUS), "tradestatus", definitions);

        Map<String, Integer> byKind = new LinkedHashMap<>();
        raw.forEach((k, v) -> byKind.put(k.name(), v.size()));
        int processed = raw.values().stream().mapToInt(List::size).sum();
        CaptureStats stats = new CaptureStats(captures.size(), processed, stripped, ignored, invalid, byKind, definitions.names().size(),
                definitions.truncated(), warnings);
        log.info("Capture processed received : {} processed : {} stripped : {} ignored : {} invalid : {}", captures.size(), processed, stripped, ignored, invalid);
        return new CaptureResult(stats, new ArrayList<>(cards.values()), activeSquad(last(bodies, Kind.SQUAD_ACTIVE), definitions, rarities, formationsByName, positionNames),
                squads(last(bodies, Kind.SQUAD_LIST)), prices, objectives(last(bodies, Kind.OBJECTIVES)), sbcSets(bodies.get(Kind.SBC_HUB)),
                sbcDetails(raw.get(Kind.SBC_DETAIL), bodies.get(Kind.SBC_DETAIL)), evolutions(bodies.get(Kind.ACADEMY)),
                credits(last(bodies, Kind.CREDITS)), currencies(last(bodies, Kind.CREDITS)), config);
    }

    static boolean isDenied(String lowerUrl) {
        String path = path(lowerUrl);
        return DENY.stream().anyMatch(lowerUrl::contains) || path.endsWith(USER_ENDPOINT);
    }

    static String path(String lowerUrl) {
        int query = lowerUrl.indexOf('?');
        return query < 0 ? lowerUrl : lowerUrl.substring(0, query);
    }

    static Kind classify(String path) {
        if (path.endsWith("futcompitemraritytunables.json")) {
            return Kind.RARITY;
        }
        if (path.endsWith("/items/web/players.json")) {
            return Kind.DEFINITIONS;
        }
        if (path.endsWith("squaddata.json")) {
            return Kind.SQUAD_DATA;
        }
        if (path.endsWith("keyattributes.json")) {
            return Kind.KEY_ATTRIBUTES;
        }
        if (path.matches(".*/game/[^/]+/club")) {
            return Kind.CLUB;
        }
        if (path.endsWith("/storagepile")) {
            return Kind.STORAGE;
        }
        if (path.endsWith("/purchased/items")) {
            return Kind.PURCHASED;
        }
        if (path.endsWith("/squad/active")) {
            return Kind.SQUAD_ACTIVE;
        }
        if (path.endsWith("/squad/list")) {
            return Kind.SQUAD_LIST;
        }
        if (path.endsWith("/transfermarket")) {
            return Kind.TRANSFER_MARKET;
        }
        if (path.endsWith("/tradepile")) {
            return Kind.TRADE_PILE;
        }
        if (path.endsWith("/watchlist")) {
            return Kind.WATCH_LIST;
        }
        if (path.endsWith("/trade/status/lite")) {
            return Kind.TRADE_STATUS;
        }
        if (path.contains("/sbs/hub")) {
            return Kind.SBC_HUB;
        }
        if (path.contains("/sbs/")) {
            return Kind.SBC_DETAIL;
        }
        if (path.endsWith("/objective/list")) {
            return Kind.OBJECTIVES;
        }
        if (path.contains("/academy/")) {
            return Kind.ACADEMY;
        }
        if (path.endsWith("/user/credits")) {
            return Kind.CREDITS;
        }
        return null;
    }

    private JsonNode last(Map<Kind, List<JsonNode>> bodies, Kind kind) {
        List<JsonNode> list = bodies.get(kind);
        return Objects.isNull(list) || list.isEmpty() ? null : list.get(list.size() - 1);
    }

    private Definitions definitions(List<Capture> captures) {
        Map<Long, String> names = new HashMap<>();
        boolean truncated = false;
        for (Capture capture : captures) {
            try {
                objectMapper.readTree(capture.body());
            } catch (IOException e) {
                truncated = true;
            }
            Matcher matcher = OBJECT_WITH_ID.matcher(capture.body());
            while (matcher.find()) {
                try {
                    JsonNode node = objectMapper.readTree(matcher.group());
                    String name = node.hasNonNull("c") ? node.get("c").asText() : (node.path("f").asText("") + " " + node.path("l").asText("")).trim();
                    if (!name.isBlank()) {
                        names.put(node.get("id").asLong(), name);
                    }
                } catch (IOException ignored) {
                    // kesik kayıt atlanır
                }
            }
        }
        return new Definitions(names, truncated);
    }

    private Map<Integer, String> rarities(List<JsonNode> bodies) {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (JsonNode body : bodies) {
            body.path("rarities").forEach(r -> result.put(r.path("id").asInt(), r.path("name").asText()));
        }
        return result;
    }

    private CaptureConfig config(Map<Kind, List<JsonNode>> bodies, Map<Integer, String> rarities) {
        JsonNode squadData = last(bodies, Kind.SQUAD_DATA);
        List<PositionConfig> positions = new ArrayList<>();
        List<StyleMod> mods = new ArrayList<>();
        List<FormationConfig> formations = new ArrayList<>();
        JsonNode chemistry = null;
        if (Objects.nonNull(squadData)) {
            squadData.path("positionData").forEach(p -> positions.add(new PositionConfig(p.path("uniqueId").asInt(), p.path("typeId").asInt(),
                    p.path("uniqueName").asText(), p.path("typeName").asText())));
            Map<Integer, String> names = new HashMap<>();
            positions.forEach(p -> names.put(p.uniqueId(), p.uniqueName()));
            squadData.path("formationData").forEach(f -> {
                List<Integer> slots = ints(f.path("uniquePositionSlots"));
                formations.add(new FormationConfig(f.path("id").asInt(), f.path("index").asInt(), f.path("name").asText(), formationLabel(f.path("name").asText()),
                        slots, slots.stream().map(id -> names.getOrDefault(id, String.valueOf(id))).toList()));
            });
            squadData.path("styleAttribMods").forEach(s -> mods.add(new StyleMod(s.path("styleId").asInt(), s.path("style").asInt(), s.path("styleType").asInt(),
                    ints(s.path("statBonus")))));
            chemistry = squadData.path("chemistry");
        }
        Map<String, List<Integer>> keyAttributes = new LinkedHashMap<>();
        JsonNode keys = last(bodies, Kind.KEY_ATTRIBUTES);
        if (Objects.nonNull(keys)) {
            keys.path("positionList").forEach(p -> keyAttributes.put(p.path("position").asText(), ints(p.path("keyAttributes"))));
        }
        return new CaptureConfig(formations, positions, mods, keyAttributes, chemistry, rarities);
    }

    static String formationLabel(String name) {
        if (Objects.isNull(name) || !name.matches("f\\d+[a-z]?")) {
            return name;
        }
        String digits = name.substring(1).replaceAll("[a-z]", "");
        String variant = name.substring(1).replaceAll("\\d", "");
        String joined = String.join("-", digits.split(""));
        return variant.isEmpty() ? joined : joined + " (" + variant + ")";
    }

    private Set<Long> duplicateIds(List<JsonNode> storage) {
        Set<Long> ids = new HashSet<>();
        storage.forEach(body -> body.path("duplicateItemIdList").forEach(d -> ids.add(d.path("itemId").asLong())));
        return ids;
    }

    private void addCards(Map<Long, CaptureCard> cards, List<JsonNode> bodies, String pile, Definitions definitions, Map<Integer, String> rarities,
                          Set<Long> duplicates) {
        if (Objects.isNull(bodies)) {
            return;
        }
        for (JsonNode body : bodies) {
            body.path("itemData").forEach(item -> {
                CaptureCard card = card(item, pile, definitions, rarities, duplicates);
                if (Objects.nonNull(card)) {
                    cards.put(card.instanceId(), card);
                }
            });
        }
    }

    private CaptureCard card(JsonNode item, String pile, Definitions definitions, Map<Integer, String> rarities, Set<Long> duplicates) {
        if (Objects.isNull(item) || item.isNull() || !item.hasNonNull("assetId") || (item.hasNonNull("itemType") && !"player".equals(item.get("itemType").asText()))) {
            return null;
        }
        long assetId = item.get("assetId").asLong();
        String position = item.path("preferredPosition").asText(null);
        List<String> positions = new ArrayList<>();
        item.path("possiblePositions").forEach(p -> positions.add(p.asText()));
        if (positions.isEmpty() && Objects.nonNull(position)) {
            positions.add(position);
        }
        int rarityId = item.path("rareflag").asInt(-1);
        String rarity = rarities.get(rarityId);
        List<String> face = GOALKEEPER.equals(position) ? GOALKEEPER_FACE : OUTFIELD_FACE;
        return new CaptureCard(item.path("id").asLong(), assetId, definitions.names().get(assetId), item.path("rating").asInt(), position, positions, rarity,
                rarityId < 0 ? null : rarityId, cardType(rarity), item.path("teamid").asInt(item.path("teamId").asInt()), item.path("leagueId").asInt(),
                item.path("nation").asInt(), item.hasNonNull("gender") ? item.get("gender").asInt() : null, item.path("untradeable").asBoolean(), face,
                ints(item.path("attributeArray")), intOrNull(item, "skillmoves"), intOrNull(item, "weakfootabilitytypecode"), intOrNull(item, "preferredfoot"),
                intOrNull(item, "playStyle"), ints(item.path("baseTraits")), ints(item.path("iconTraits")), ints(item.path("plusRoles")),
                longOrNull(item, "lastSalePrice"), longOrNull(item, "marketDataMinPrice"), longOrNull(item, "marketDataMaxPrice"),
                longOrNull(item, "marketAverage"), duplicates.contains(item.path("id").asLong()), pile);
    }

    private static String cardType(String rarity) {
        if (Objects.isNull(rarity)) {
            return null;
        }
        String lower = rarity.toLowerCase(Locale.ROOT);
        if (lower.contains("icon")) {
            return "ICON";
        }
        if (lower.contains("hall")) {
            return "HOF";
        }
        return lower.contains("hero") ? "HERO" : null;
    }

    private void addPrices(List<MarketPrice> prices, List<JsonNode> bodies, String source, Definitions definitions) {
        if (Objects.isNull(bodies)) {
            return;
        }
        for (JsonNode body : bodies) {
            body.path("auctionInfo").forEach(auction -> {
                JsonNode item = auction.path("itemData");
                if (!item.hasNonNull("assetId")) {
                    return;
                }
                long assetId = item.get("assetId").asLong();
                prices.add(new MarketPrice(source, assetId, definitions.names().get(assetId), item.path("rating").asInt(), longOrNull(auction, "buyNowPrice"),
                        longOrNull(auction, "currentBid"), longOrNull(auction, "startingBid"), longOrNull(item, "lastSalePrice"), longOrNull(auction, "expires")));
            });
        }
    }

    private ActiveSquad activeSquad(JsonNode squad, Definitions definitions, Map<Integer, String> rarities, Map<String, FormationConfig> formations,
                                    Map<Integer, String> positionNames) {
        if (Objects.isNull(squad)) {
            return null;
        }
        String formation = squad.path("formation").asText(null);
        FormationConfig config = formations.get(formation);
        List<SquadSlot> slots = new ArrayList<>();
        squad.path("players").forEach(player -> {
            int index = player.path("index").asInt();
            String position = Objects.nonNull(config) && index < config.positionNames().size() ? config.positionNames().get(index) : null;
            slots.add(new SquadSlot(index, index < STARTERS, position, intOrNull(player, "chemistry"),
                    card(player.path("itemData"), "squad", definitions, rarities, Set.of())));
        });
        return new ActiveSquad(formation, Objects.nonNull(config) ? config.label() : formationLabel(formation), intOrNull(squad, "chemistry"), intOrNull(squad, "rating"),
                squad.path("squadName").asText(null), slots);
    }

    private List<SquadSummary> squads(JsonNode body) {
        List<SquadSummary> result = new ArrayList<>();
        if (Objects.nonNull(body)) {
            body.path("squad").forEach(s -> result.add(new SquadSummary(s.path("id").asLong(), s.path("squadName").asText(null), s.path("formation").asText(null),
                    formationLabel(s.path("formation").asText(null)), intOrNull(s, "rating"), intOrNull(s, "chemistry"))));
        }
        return result;
    }

    private List<ObjectiveGroupView> objectives(JsonNode body) {
        List<ObjectiveGroupView> groups = new ArrayList<>();
        if (Objects.isNull(body)) {
            return groups;
        }
        body.path("objectiveGroups").forEach(g -> {
            List<CaptureObjective> objectives = new ArrayList<>();
            g.path("objectives").forEach(o -> objectives.add(new CaptureObjective(o.path("objectiveId").asLong(), o.path("name").asText(null),
                    o.path("description").asText(null), o.path("progress").asInt(), o.path("progressTotal").asInt(), o.path("status").asText(null),
                    o.path("operator").asInt(), longs(o.path("lockedByObjectiveIds")), rewards(o.path("awardSetList")), o.path("eadpStats").asText(null))));
            groups.add(new ObjectiveGroupView(g.path("groupId").asLong(), g.path("name").asText(null), g.path("gameMode").asText(null),
                    g.path("groupStatus").asText(null), longOrNull(g, "startDateTime"), longOrNull(g, "endDateTime"), rewards(g.path("awardSetList")), objectives));
        });
        return groups;
    }

    private List<Reward> rewards(JsonNode awardSets) {
        List<Reward> rewards = new ArrayList<>();
        awardSets.forEach(set -> set.path("awards").forEach(a -> rewards.add(new Reward(a.path("awardType").asText(null), longOrNull(a, "value"), a.path("count").asInt(1)))));
        return rewards;
    }

    private List<SbcSetView> sbcSets(List<JsonNode> bodies) {
        Map<Long, SbcSetView> sets = new LinkedHashMap<>();
        if (Objects.nonNull(bodies)) {
            bodies.forEach(body -> body.path("categories").forEach(category -> category.path("sets").forEach(s -> sets.put(s.path("setId").asLong(),
                    new SbcSetView(s.path("setId").asLong(), s.path("name").asText(null), category.path("categoryId").asInt(), category.path("name").asText(null),
                            s.path("challengesCount").asInt(), s.path("challengesCompletedCount").asInt(), s.path("repeatable").asBoolean(),
                            longOrNull(s, "endTime"), s.path("awards").size())))));
        }
        return new ArrayList<>(sets.values());
    }

    private List<SbcDetail> sbcDetails(List<Capture> raw, List<JsonNode> bodies) {
        List<SbcDetail> details = new ArrayList<>();
        if (Objects.isNull(raw) || Objects.isNull(bodies)) {
            return details;
        }
        for (int i = 0; i < Math.min(raw.size(), bodies.size()) && details.size() < MAX_SBC_DETAILS; i++) {
            details.add(new SbcDetail(path(raw.get(i).url().toLowerCase(Locale.ROOT)).replaceAll("^https?://[^/]+", ""), bodies.get(i)));
        }
        return details;
    }

    private List<EvolutionSlot> evolutions(List<JsonNode> bodies) {
        Map<Long, EvolutionSlot> slots = new LinkedHashMap<>();
        if (Objects.nonNull(bodies)) {
            bodies.forEach(body -> body.path("slots").forEach(s -> slots.put(s.path("id").asLong(), new EvolutionSlot(s.path("id").asLong(),
                    s.path("slotName").asText(null), s.path("status").asText(null), longOrNull(s, "endTime"), s.path("levels").size(), s.path("elgReqs").size()))));
        }
        return new ArrayList<>(slots.values());
    }

    private Long credits(JsonNode body) {
        return Objects.isNull(body) ? null : longOrNull(body, "credits");
    }

    private List<CurrencyView> currencies(JsonNode body) {
        List<CurrencyView> result = new ArrayList<>();
        if (Objects.nonNull(body)) {
            body.path("currencies").forEach(c -> result.add(new CurrencyView(c.path("name").asText(), c.path("funds").asLong())));
        }
        return result;
    }

    private static List<Integer> ints(JsonNode array) {
        List<Integer> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asInt()));
        return values;
    }

    private static List<Long> longs(JsonNode array) {
        List<Long> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asLong()));
        return values;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    private static Long longOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asLong() : null;
    }

    private record Definitions(Map<Long, String> names, boolean truncated) {
    }
}
