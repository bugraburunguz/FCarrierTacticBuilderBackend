package com.fcareer.recommendation.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import com.fcareer.recommendation.pipeline.CareerRecommender.Scored;
import com.fcareer.recommendation.pipeline.model.CareerRecommendationRequest;
import com.fcareer.recommendation.pipeline.model.CareerRecommendationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.fcareer.CareerTestSupport.FIRST_MOCK_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** E3-6 öneri hattı: sert kısıtlar, kayıt dosyası havuzu, null bütçe, cinsiyet izolasyonu, recall@10, açıklama alanları. */
@IntegrationTest
class CareerRecommenderIntegrationTest {

    private static final double MIN_RECALL = 0.95;
    private static final int RECALL_K = 10;
    private static final int SMALL_POOL = 80;
    private static final int FULL_POOL = 100_000;
    private static final List<String> POSITIONS = List.of("ST", "CM", "CB", "LB", "RW", "GK");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private CareerService careerService;
    @Autowired
    private CareerRecommender recommender;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private com.fcareer.fitengine.level.FitLevelNormsBuilder levelNormsBuilder;
    @Autowired
    private com.fcareer.fitengine.level.LevelFitService levelFitService;

    private CareerTestSupport support;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        if (jdbc.queryForObject("SELECT count(*) FROM fit_level_norms", Integer.class) == 0) {
            levelNormsBuilder.rebuild((short) 27);
            levelFitService.refresh();
        }
        support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
    }

    private long userId(String token) {
        return jdbc.queryForObject("SELECT user_id FROM career ORDER BY id DESC LIMIT 1", Long.class);
    }

    private CareerService.CareerSquad squad(long careerId) {
        Long owner = jdbc.queryForObject("SELECT user_id FROM career WHERE id = ?", Long.class, careerId);
        return careerService.squadPlayerIds(owner, careerId);
    }

    private CareerRecommendationRequest request(String position) {
        CareerRecommendationRequest request = new CareerRecommendationRequest();
        request.setPosition(position);
        request.setLimit(10);
        return request;
    }

    @Test
    void anImportedCareersRecommendationsComeOnlyFromTheSaveFileAndNeverFromTheCatalogRemainder() throws Exception {
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        long a = FIRST_MOCK_ID;
        support.importCsv(careerId, token, a + ",91,95\n" + (a + 1) + ",90,93\n" + (a + 2) + ",77,80\n" + (a + 3) + ",76,79\n",
                a + ",1\n" + (a + 1) + ",1\n" + (a + 2) + ",111592\n" + (a + 3) + ",111592\n", (a + 3) + ",99\n");
        json(post("/api/v1/careers/" + careerId + "/import/squad?teamId=1"), token);

        Set<Long> live = jdbc.queryForList("SELECT ea_resource_id FROM base_player WHERE ea_resource_id IN (?, ?, ?, ?)", Long.class, a, a + 1, a + 2, a + 3).stream().collect(Collectors.toSet());
        Set<Long> seen = new HashSet<>();
        for (String position : POSITIONS) {
            CareerRecommendationResponse response = recommender.compute(squad(careerId), request(position));
            response.getItems().forEach((item) -> seen.add(item.getPlayer().getFaceAssetId()));
        }
        assertThat(seen).as("kayıtta olmayan katalog oyuncusu önerilmez; kiralıktaki (a+3) ve kadrodakiler (a, a+1) hariç").isSubsetOf(live);
        assertThat(seen).doesNotContain(a, a + 1, a + 3);
    }

    @Test
    void zeroAndNullBudgetBothMeanUnknownAndTinyBudgetOnlyKeepsAcquirablePlayers() throws Exception {
        long nullBudget = support.premiumCareer(support.register(), null);
        long zeroBudget = support.premiumCareer(support.register(), 0L);
        long tinyBudget = support.premiumCareer(support.register(), 1L);
        List<Long> fromNull = ids(recommender.compute(squad(nullBudget), request("ST")));
        List<Long> fromZero = ids(recommender.compute(squad(zeroBudget), request("ST")));
        CareerRecommendationResponse tiny = recommender.compute(squad(tinyBudget), request("ST"));
        assertThat(fromZero).as("bütçe 0 = bilinmiyor (null ile aynı)").isEqualTo(fromNull);
        assertThat(fromNull).isNotEmpty();
        tiny.getItems().forEach((item) -> assertThat(item.getPlayer().isFreeAgent() || (item.getPlayer().getValueEur() == null || item.getPlayer().getValueEur() <= 2))
                .as("1 € bütçede yalnız edinilebilir (ya da değeri bilinmeyen) oyuncular: " + item.getPlayer().getName()).isTrue());
    }

    @Test
    void recommendationsNeverCrossTheGenderOfTheCareerAndNeverContainTheOwnSquad() throws Exception {
        long careerId = support.premiumCareer(support.register(), null);
        CareerService.CareerSquad squad = squad(careerId);
        for (String position : POSITIONS) {
            CareerRecommendationResponse response = recommender.compute(squad, request(position));
            for (var item : response.getItems()) {
                Short gender = jdbc.queryForObject("SELECT gender FROM base_player WHERE id = ?", Short.class, item.getPlayer().getId());
                assertThat(gender).isEqualTo(squad.gender());
                assertThat(squad.ownedIds()).doesNotContain(item.getPlayer().getId());
            }
        }
    }

    @Test
    void thePrescoredPoolFindsTheSameTopTenAsScoringEveryone() throws Exception {
        long careerId = support.premiumCareer(support.register(), null);
        CareerService.CareerSquad squad = squad(careerId);
        double total = 0;
        int counted = 0;
        for (String position : POSITIONS) {
            List<Long> full = top(recommender.rank(squad, request(position), FULL_POOL), RECALL_K);
            List<Long> pooled = top(recommender.rank(squad, request(position), SMALL_POOL), RECALL_K);
            if (full.size() < RECALL_K) {
                continue;
            }
            total += pooled.stream().filter(full::contains).count() / (double) RECALL_K;
            counted++;
        }
        assertThat(counted).as("recall ölçülecek mevki var").isPositive();
        assertThat(total / counted).as("recall@10").isGreaterThanOrEqualTo(MIN_RECALL);
    }

    @Test
    void everyItemExplainsItselfWithNumbers() throws Exception {
        long careerId = support.premiumCareer(support.register(), 50_000_000L);
        CareerRecommendationResponse response = recommender.compute(squad(careerId), request("ST"));
        assertThat(response.getItems()).isNotEmpty();
        for (var item : response.getItems()) {
            assertThat(item.getBucket()).isNotNull();
            assertThat(item.getBucketFactors()).isNotEmpty();
            assertThat(item.getCurrentFit()).isBetween(0.0, 100.0);
            assertThat(item.getLevelFit()).isNotNull().isBetween(0.0, 100.0);
            assertThat(item.getPros()).hasSizeLessThanOrEqualTo(3);
            assertThat(item.getCons()).hasSizeLessThanOrEqualTo(2);
            assertThat(item.getFutureFit()).isNotNull();
            assertThat(item.getProjectedRating()).isNotNull();
            assertThat(item.getNarrative()).as("E3-8: anlatım yalnız katkı/kanıt sayılarından").isNotNull();
            assertThat(item.getNarrative().getHeadline()).isNotBlank();
            assertThat(item.getNarrative().isTailored()).as("Biçilmiş kaftan yalnız levelFit ≥ 85").isEqualTo(item.getLevelFit() >= 85.0);
            assertThat(item.getNarrative().getStrengths()).hasSizeLessThanOrEqualTo(3);
            assertThat(item.getNarrative().getWeaknesses()).hasSizeLessThanOrEqualTo(2);
        }
    }

    @Test
    void theResultListNeverOrdersByOverallAlone() throws Exception {
        long careerId = support.premiumCareer(support.register(), null);
        List<Scored> ranked = recommender.rank(squad(careerId), request("CM"), FULL_POOL);
        boolean sortedByOverall = true;
        for (int i = 1; i < ranked.size() && i < 30; i++) {
            if (ranked.get(i - 1).player().getOverall() < ranked.get(i).player().getOverall()) {
                sortedByOverall = false;
            }
        }
        assertThat(sortedByOverall).as("sıralama OVR değil rol uyumu/fizibilite").isFalse();
    }


    private void tactic(long careerId, int slot, String slotsJson) {
        Long owner = jdbc.queryForObject("SELECT user_id FROM career WHERE id = ?", Long.class, careerId);
        jdbc.update("INSERT INTO user_tactic (user_id, career_id, slot, name, formation, slots) VALUES (?, ?, ?, 't', '4-4-2', ?::jsonb)", owner, careerId, slot, slotsJson);
    }

    @Test
    void aSlotWithAnAdvancedForwardAttackRoleNeverGetsTargetForwardOrFalseNineUnlessAlternativesAreOn() throws Exception {
        long careerId = support.premiumCareer(support.register(), null);
        tactic(careerId, 1, "[{\"slot\":\"ST1\",\"position\":\"ST\",\"roleId\":\"advanced_forward\",\"focus\":\"attack\"}]");
        CareerRecommendationResponse strict = recommender.compute(squad(careerId), request("ST"));
        assertThat(strict.getRoleSource()).isEqualTo("TACTIC");
        assertThat(strict.getItems()).isNotEmpty();
        strict.getItems().forEach((i) -> assertThat(i.getRoleId()).isEqualTo("advanced_forward_attack"));
        CareerRecommendationRequest open = request("ST");
        open.setAlternativeRoles(true);
        CareerRecommendationResponse alt = recommender.compute(squad(careerId), open);
        assertThat(alt.getRoleSource()).isEqualTo("ALTERNATIVES");
        assertThat(alt.getItems().stream().map(CareerRecommendationResponse.Item::getRoleId).distinct().count()).as("alternatif roller satırda yazılır").isGreaterThan(1);
    }

    @Test
    void theTacticSlotBeatsTheSquadAssignmentAndForceRoleBeatsTheTactic() throws Exception {
        long careerId = support.premiumCareer(support.register(), null);
        tactic(careerId, 1, "[{\"slot\":\"ST1\",\"position\":\"ST\",\"roleId\":\"target_forward\",\"focus\":\"attack\"}]");
        assertThat(recommender.compute(squad(careerId), request("ST")).getRoleId()).isEqualTo("target_forward_attack");
        CareerRecommendationRequest forced = request("ST");
        forced.setRoleId("poacher");
        forced.setFocus("attack");
        forced.setForceRole(true);
        CareerRecommendationResponse r = recommender.compute(squad(careerId), forced);
        assertThat(r.getRoleSource()).isEqualTo("REQUEST");
        assertThat(r.getRoleId()).isEqualTo("poacher_attack");
    }

    @Test
    void aCenterBackRequestIsMostlyNaturalCenterBacksWithAtMostTwoLabelledOutOfPosition() throws Exception {
        long careerId = support.premiumCareer(support.register(), null);
        CareerRecommendationResponse r = recommender.compute(squad(careerId), request("CB"));
        assertThat(r.getItems()).hasSize(10);
        long natural = r.getItems().stream().filter((i) -> i.getPositionLabel() == null).count();
        assertThat(natural).isGreaterThanOrEqualTo(8);
        r.getItems().stream().filter((i) -> i.getPositionLabel() != null).forEach((i) -> assertThat(i.getPositionLabel()).startsWith("Mevki dışı (").endsWith("→CB)"));
        r.getItems().stream().filter((i) -> i.getPositionLabel() == null).forEach((i) -> assertThat(i.getPlayer().getPositions()).contains("CB"));
    }

    @Test
    void theDefaultBalancedProfileKeepsOldPlayersOutAndAtLeastFourOfTenAreInTheirPrime() throws Exception {
        long careerId = support.premiumCareer(support.register(), null);
        CareerRecommendationResponse r = recommender.compute(squad(careerId), request("CM"));
        assertThat(r.getProfile()).isEqualToIgnoringCase("BALANCED");
        r.getItems().forEach((i) -> assertThat(i.getPlayer().getAge()).isLessThanOrEqualTo(34));
        assertThat(r.getItems().stream().filter((i) -> i.getPlayer().getAge() <= 27).count()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void fantasyTransfersNeverEnterTheMainListAndComeBackAsAtMostThreeLongShotsOnRequest() throws Exception {
        long careerId = support.premiumCareer(support.register(), null);
        for (String position : POSITIONS) {
            CareerRecommendationResponse plain = recommender.compute(squad(careerId), request(position));
            assertThat(plain.getLongShots()).isNull();
            plain.getItems().forEach((i) -> assertThat(i.getBucket()).isNotEqualTo(FeasibilityBucket.HAYAL));
            CareerRecommendationRequest withLongShots = request(position);
            withLongShots.setIncludeLongShots(true);
            CareerRecommendationResponse r = recommender.compute(squad(careerId), withLongShots);
            r.getItems().forEach((i) -> assertThat(i.getBucket()).isNotEqualTo(FeasibilityBucket.HAYAL));
            assertThat(r.getLongShots()).hasSizeLessThanOrEqualTo(3);
            r.getLongShots().forEach((i) -> assertThat(i.getBucket()).isEqualTo(FeasibilityBucket.HAYAL));
        }
    }

    @Test
    void theRequestCanOverrideTheBudgetOrDeclareItUnknown() throws Exception {
        long tiny = support.premiumCareer(support.register(), 1L);
        long open = support.premiumCareer(support.register(), null);
        CareerRecommendationRequest unknown = request("ST");
        unknown.setBudgetUnknown(true);
        assertThat(ids(recommender.compute(squad(tiny), unknown))).as("bütçe bilinmiyor = kariyerde bütçe girilmemiş gibi").isEqualTo(ids(recommender.compute(squad(open), request("ST"))));
        CareerRecommendationRequest override = request("ST");
        override.setBudgetEur(1L);
        recommender.compute(squad(open), override).getItems().forEach((item) -> assertThat(item.getPlayer().isFreeAgent() || item.getPlayer().getValueEur() == null || item.getPlayer().getValueEur() <= 2)
                .as("istekteki 1 € bütçe kariyer bütçesinin yerine geçer: " + item.getPlayer().getName()).isTrue());
    }

    private static List<Long> ids(CareerRecommendationResponse response) {
        return response.getItems().stream().map((i) -> i.getPlayer().getId()).toList();
    }

    private static List<Long> top(List<Scored> ranked, int k) {
        return ranked.stream().limit(k).map((s) -> s.summary().getId()).toList();
    }

    private JsonNode json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String token) throws Exception {
        return support.json(builder, token);
    }
}
