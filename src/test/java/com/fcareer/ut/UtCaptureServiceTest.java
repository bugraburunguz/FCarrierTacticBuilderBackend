package com.fcareer.ut;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.ut.model.UtCaptureModels.Capture;
import com.fcareer.ut.model.UtCaptureModels.CaptureResult;
import com.fcareer.ut.repository.UtCatalogJoinRepository;
import com.fcareer.ut.repository.UtCatalogJoinRepository.CatalogInfo;
import com.fcareer.ut.service.UtCaptureService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class UtCaptureServiceTest {

    private static final String UT = "https://utas.mob.v1.prd.futc-ext.gcp.ea.com/ut/game/fc27";
    private static final String CDN = "https://www.ea.com/ea-sports-fc/ultimate-team/web-app/content/GUID/2027/fut";

    private final UtCaptureService service = newService(Map.of());

    private static UtCaptureService newService(Map<Long, CatalogInfo> catalog) {
        return newService(catalog, new ObjectMapper().createObjectNode());
    }

    private static UtCaptureService newService(Map<Long, CatalogInfo> catalog, com.fasterxml.jackson.databind.JsonNode rarityOverrides) {
        try {
            UtCatalogJoinRepository repository = Mockito.mock(UtCatalogJoinRepository.class);
            Mockito.when(repository.findByEaResourceIds(Mockito.anyCollection())).thenReturn(catalog);
            UtCaptureService created = new UtCaptureService(new ObjectMapper(), repository, Mockito.mock(com.fcareer.ut.repository.EaIdRepository.class), new com.fcareer.catalog.configuration.CatalogProperties(), rulesWithRarities(rarityOverrides));
            Method load = UtCaptureService.class.getDeclaredMethod("loadAttrOrder");
            load.setAccessible(true);
            load.invoke(created);
            return created;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static com.fcareer.ut.service.UtRules rulesWithRarities(com.fasterxml.jackson.databind.JsonNode byId) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode document = (com.fasterxml.jackson.databind.node.ObjectNode) new ObjectMapper().readTree(new org.springframework.core.io.ClassPathResource("seed/ut-rules.fc27.json").getInputStream());
            ((com.fasterxml.jackson.databind.node.ObjectNode) document.get("rarities")).set("byId", byId);
            return com.fcareer.ut.service.UtRules.of(document);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static Capture capture(String url, long ts, String body) {
        return new Capture(url, 200, ts, body);
    }

    private static String item(long id, long assetId, int rating, String position, int rarity) {
        return "{\"id\":" + id + ",\"assetId\":" + assetId + ",\"resourceId\":" + assetId + ",\"itemType\":\"player\",\"rating\":" + rating
                + ",\"preferredPosition\":\"" + position + "\",\"rareflag\":" + rarity + ",\"teamid\":5,\"leagueId\":13,\"nation\":7,\"untradeable\":true,"
                + "\"attributeArray\":[91,84,85,93,37,69],\"lastSalePrice\":1200,\"marketDataMinPrice\":900,\"skillmoves\":4,\"weakfootabilitytypecode\":4,"
                + "\"possiblePositions\":[\"" + position + "\",\"CAM\"]}";
    }

    @Test
    void stripsAuthAndAccountEndpointsAndNeverReturnsTheirContent() {
        CaptureResult result = service.process(List.of(
                capture("https://x.ea.com/ut/auth?sid=SECRET", 1, "{\"sid\":\"SECRET-TOKEN\"}"),
                capture("https://x.ea.com/fc/user", 2, "{\"profiles\":[]}"),
                capture("https://x.ea.com/fc/user/season/stats", 3, "{}"),
                capture("https://x.ea.com/connect/auth", 4, "{\"phishingToken\":\"SECRET-TOKEN\"}"),
                capture(UT + "/user/credits", 5, "{\"credits\":12345,\"currencies\":[{\"name\":\"COINS\",\"funds\":12345}]}")));
        assertThat(result.stats().stripped()).isEqualTo(4);
        assertThat(result.coins()).isEqualTo(12345L);
        assertThat(new ObjectMapper().valueToTree(result).toString()).doesNotContain("SECRET");
    }

    @Test
    void joinsClubCardsWithDefinitionsEvenWhenPlayersJsonIsTruncated() {
        String players = "{\"LegendsPlayers\":[{\"c\":\"Iniesta\",\"f\":\"Andres\",\"id\":41,\"l\":\"Iniesta Lujan\",\"r\":92}],\"Players\":[{\"f\":\"Kylian\",\"id\":183277,\"l\":\"Mbappe\",\"r\":91},{\"f\":\"Trunc";
        String rarities = "{\"rarities\":[{\"id\":72,\"name\":\"Rare Gold\"},{\"id\":12,\"name\":\"Icon\"}]}";
        CaptureResult result = service.process(List.of(
                capture(CDN + "/items/web/players.json", 1, players),
                capture(CDN + "/items/images/backgrounds/itemBGs/futcompitemraritytunables.json", 2, rarities),
                capture(UT + "/club", 3, "{\"itemData\":[" + item(1, 183277, 89, "LM", 72) + "," + item(2, 41, 92, "CM", 12) + "]}"),
                capture(UT + "/club/stats/club", 4, "{\"stat\":[]}")));
        assertThat(result.stats().definitionsTruncated()).isTrue();
        assertThat(result.cards()).hasSize(2);
        assertThat(result.cards().get(0).name()).isEqualTo("Kylian Mbappe");
        assertThat(result.cards().get(0).rarity()).isEqualTo("Rare Gold");
        assertThat(result.cards().get(0).faceLabels()).containsExactly("PAC", "SHO", "PAS", "DRI", "DEF", "PHY");
        assertThat(result.cards().get(1).name()).isEqualTo("Iniesta");
        assertThat(result.cards().get(1).cardType()).isEqualTo("ICON");
        assertThat(result.stats().byKind()).doesNotContainKey("CLUB_STATS");
    }

    @Test
    void readsActiveSquadWithFormationLabelAndSlotPositions() {
        String squadData = "{\"positionData\":[{\"uniqueId\":0,\"typeId\":0,\"uniqueName\":\"GK\",\"typeName\":\"GK\"},{\"uniqueId\":25,\"typeId\":9,\"uniqueName\":\"ST\",\"typeName\":\"ST\"}],"
                + "\"formationData\":[{\"id\":1,\"index\":0,\"name\":\"f4231a\",\"uniquePositionSlots\":[0,25],\"generalPositionSlots\":[0,25]}],\"styleAttribMods\":[{\"styleId\":250,\"statBonus\":[1,1],\"style\":0,\"styleType\":0}]}";
        String squad = "{\"formation\":\"f4231a\",\"chemistry\":27,\"squadName\":\"Ana\",\"players\":[{\"index\":0,\"itemData\":" + item(1, 10, 80, "GK", 1) + ",\"chemistry\":3},{\"index\":1,\"itemData\":" + item(2, 11, 90, "ST", 1) + ",\"chemistry\":2}]}";
        CaptureResult result = service.process(List.of(capture(CDN + "/config/companion/squadData.json", 1, squadData), capture(UT + "/squad/active", 2, squad)));
        assertThat(result.activeSquad().formationLabel()).isEqualTo("4-2-3-1 (a)");
        assertThat(result.activeSquad().slots().get(1).position()).isEqualTo("ST");
        assertThat(result.activeSquad().slots().get(0).card().faceLabels()).contains("DIV");
        assertThat(result.config().formations()).hasSize(1);
        assertThat(result.config().styleMods()).hasSize(1);
    }

    @Test
    void readsMarketPricesObjectivesAndSbcHub() {
        String market = "{\"auctionInfo\":[{\"tradeId\":1,\"itemData\":" + item(3, 12, 85, "RW", 1) + ",\"buyNowPrice\":15000,\"currentBid\":0,\"startingBid\":1000,\"expires\":3600}]}";
        String objectives = "{\"objectiveGroups\":[{\"groupId\":7,\"name\":\"Weekly\",\"gameMode\":\"CLUB\",\"groupStatus\":\"IN_PROGRESS\",\"startDateTime\":100,\"endDateTime\":200,"
                + "\"awardSetList\":[{\"awards\":[{\"awardType\":\"coin\",\"value\":500,\"count\":1}]}],\"objectives\":[{\"objectiveId\":9,\"name\":\"Score 3 goals\",\"progress\":1,"
                + "\"progressTotal\":3,\"status\":\"IN_PROGRESS\",\"operator\":6,\"lockedByObjectiveIds\":[8],\"awardSetList\":[{\"awards\":[{\"awardType\":\"coin\",\"value\":250,\"count\":2}]}]}]}]}";
        String sbc = "{\"categories\":[{\"categoryId\":2,\"name\":\"Upgrades\",\"sets\":[{\"setId\":5,\"name\":\"85+ Upgrade\",\"challengesCount\":1,\"challengesCompletedCount\":0,\"repeatable\":true,\"endTime\":999,\"awards\":[{}]}]}]}";
        CaptureResult result = service.process(List.of(capture(UT + "/transfermarket?num=21", 1, market), capture("https://f.ea.com/fc/user/objective/list", 2, objectives),
                capture(UT + "/sbs/hub/v2", 3, sbc), capture(UT + "/sbs/sets/5", 4, "{\"challenges\":[{\"id\":1}]}")));
        assertThat(result.prices()).hasSize(1);
        assertThat(result.prices().get(0).buyNow()).isEqualTo(15000L);
        assertThat(result.objectives()).hasSize(1);
        assertThat(result.objectives().get(0).objectives().get(0).lockedBy()).containsExactly(8L);
        assertThat(result.objectives().get(0).objectives().get(0).rewards().get(0).count()).isEqualTo(2);
        assertThat(result.sbcSets()).hasSize(1);
        assertThat(result.sbcSets().get(0).category()).isEqualTo("Upgrades");
        assertThat(result.sbcDetails()).hasSize(1);
    }

    @Test
    void ignoresUnknownEndpointsAndCountsInvalidBodies() {
        CaptureResult result = service.process(List.of(capture(UT + "/playStats", 1, "{}"), capture(UT + "/club", 2, "not json"), new Capture(null, 200, 3L, "{}")));
        assertThat(result.stats().ignored()).isEqualTo(1);
        assertThat(result.stats().invalid()).isEqualTo(2);
    }

    @Test
    void learnsLeagueAndNationNamesFromCatalogMatchesAndAttachesMetaAttributes() {
        UtCaptureService withCatalog = newService(Map.of(183277L, new CatalogInfo(77L, 0, "Brazil", "Arsenal", "Premier League")));
        StringBuilder attrs = new StringBuilder();
        for (int i = 0; i < 34; i++) {
            attrs.append(i == 0 ? "" : ",").append(60 + i);
        }
        String meta = "{\"attrKeys\":[],\"players\":{\"99\":{\"a\":[" + attrs + "]}}}";
        CaptureResult result = withCatalog.process(List.of(
                capture(CDN + "/items/web/players_meta.json", 1, meta),
                capture(UT + "/club", 2, "{\"itemData\":[" + item(1, 183277, 89, "LM", 72) + "," + item(2, 99, 90, "ST", 5) + "]}")));
        assertThat(result.cards().get(0).playerId()).isEqualTo(77L);
        assertThat(result.cards().get(0).league()).isEqualTo("Premier League");
        assertThat(result.cards().get(1).playerId()).isNull();
        assertThat(result.cards().get(1).league()).isEqualTo("Premier League");
        assertThat(result.cards().get(1).nationality()).isEqualTo("Brazil");
        assertThat(result.cards().get(1).attrs()).containsEntry("Acceleration", 60).containsEntry("GKPositioning", 93);
        assertThat(result.cards().get(0).attrs()).isNull();
    }

    private static String challengeBody(String name, int setId) {
        return "{\"challenges\":[{\"name\":\"" + name + "\",\"status\":\"NOT_STARTED\",\"setId\":" + setId + ",\"challengeId\":" + (setId * 10)
                + ",\"repeatable\":false,\"formation\":\"f442\",\"timesCompleted\":0,\"elgReq\":[],\"awards\":[],\"type\":\"OPEN_CHALLENGE\"}]}";
    }

    @Test
    void corruptSbcBodyDoesNotShiftLaterSetsOntoWrongBodies() {
        CaptureResult result = service.process(List.of(
                capture(UT + "/sbs/setid/10/challenges", 1, challengeBody("Birinci", 10)),
                capture(UT + "/sbs/setid/11/challenges", 2, "{bozuk json"),
                capture(UT + "/sbs/setid/12/challenges", 3, challengeBody("Ucuncu", 12))));

        assertThat(result.sbcChallenges()).extracting("setId", "name").containsExactly(
                org.assertj.core.api.Assertions.tuple(10L, "Birinci"), org.assertj.core.api.Assertions.tuple(12L, "Ucuncu"));
        assertThat(result.stats().invalid()).isEqualTo(1);
    }

    @Test
    void rejectsPayloadsAboveTheTotalLimit() {
        String chunk = "x".repeat(8_000_000);
        List<Capture> captures = java.util.stream.IntStream.range(0, 6).mapToObj(n -> capture(UT + "/club", n, chunk)).toList();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.process(captures))
                .isInstanceOf(com.fcareer.common.exception.PayloadTooLargeException.class);
    }

    @Test
    void cardTypeComesFromTheRarityIdNotTheLocalizedName() throws Exception {
        UtCaptureService localized = newService(Map.of(), new ObjectMapper().readTree("{\"77\":{\"cardType\":\"HERO\"},\"78\":{\"cardType\":\"HOF\"}}"));
        String rarities = "{\"rarities\":[{\"id\":77,\"name\":\"Kahraman Özel\"},{\"id\":78,\"name\":\"Şöhret Salonu\"},{\"id\":72,\"name\":\"Rare Gold\"}]}";
        CaptureResult result = localized.process(List.of(
                capture(CDN + "/items/images/backgrounds/itemBGs/futcompitemraritytunables.json", 1, rarities),
                capture(UT + "/club", 2, "{\"itemData\":[" + item(1, 501, 88, "ST", 77) + "," + item(2, 502, 90, "ST", 78) + "," + item(3, 503, 85, "ST", 72) + "]}")));

        assertThat(result.cards()).extracting("cardType").containsExactly("HERO", "HOF", null);
    }
}
