package com.fcareer.replica;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.IntegrationTest;
import com.fcareer.tacticcode.model.EaTactic;
import com.fcareer.tacticcode.service.EaTacticCodec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** E3-9 (REP-01..03, REP-c/e): 103 replika seed'i; hepsinin tacticCode'u üretilir ve decode edilince seed ayarlarıyla birebir aynıdır; seed doğrulayıcısı (REP-02). */
@IntegrationTest
class ReplicaSeedIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, int[]> APPROACH = Map.of("DEEP", new int[]{1, 30}, "BALANCED", new int[]{31, 60}, "HIGH", new int[]{61, 90}, "AGGRESSIVE", new int[]{91, 100});
    private static final int RECORDS = 103;
    private static final List<String> REMOVED = List.of("delbosque_real_2000", "cruyff_barcelona_1992", "sacchi_milan_1989", "capello_milan_1994", "kovacs_ajax_1972", "herrera_inter_1965",
            "lobanovskyi_dynamokyiv_1986", "lobanovskyi_dynamokyiv_1999", "vangaal_ajax_1995", "ferguson_manutd_1999", "ucl_winner_2026_tbd");
    private static final Set<String> LEAGUES = Set.of("TR_SUPER_LIG", "EN_PREMIER_LEAGUE", "EN_CHAMPIONSHIP", "ES_LALIGA", "IT_SERIE_A", "IT_SERIE_B", "DE_BUNDESLIGA", "FR_LIGUE_1",
            "NL_EREDIVISIE", "PT_LIGA", "SCO_PREMIERSHIP", "AT_BUNDESLIGA");
    /** Aynı kulübün id'de farklı yazılmış kökleri. */
    private static final Map<String, String> CLUB_STEM_ALIAS = Map.of("realmadrid", "real");

    @Autowired
    private ReplicaService replicas;
    @Autowired
    private EaTacticCodec codec;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static JsonNode seed() throws Exception {
        try (InputStream in = new ClassPathResource("seed/replicas.fc27.json").getInputStream()) {
            return MAPPER.readTree(in);
        }
    }

    @Test
    void theSeedHas103RecordsAndEveryOneHasATactic() throws Exception {
        JsonNode seed = seed();
        assertThat(seed.size()).isEqualTo(RECORDS);
        Set<String> ids = new HashSet<>();
        for (JsonNode r : seed) {
            assertThat(ids.add(r.path("id").asText())).as("id tekil: " + r.path("id")).isTrue();
            assertThat(r.path("fc27").isObject()).as(r.path("id").asText() + " taktik").isTrue();
        }
    }

    /** REP-c: izin listesi = doğrulama raporundaki 102 replika + italiano_besiktas_2027; 2000 öncesi sezonlar (terim_galatasaray_2000 hariç) ve yer tutucu arşive taşındı. */
    @Test
    void preMillenniumRecordsAndThePlaceholderMovedToTheArchive() throws Exception {
        Set<String> ids = new HashSet<>();
        seed().forEach((r) -> ids.add(r.path("id").asText()));
        assertThat(ids).doesNotContainAnyElementsOf(REMOVED).contains("terim_galatasaray_2000", "italiano_besiktas_2027");
        for (JsonNode r : seed()) {
            int start = Integer.parseInt(r.path("season").asText().substring(0, 4));
            assertThat(start >= 2000 || "terim_galatasaray_2000".equals(r.path("id").asText())).as(r.path("id").asText() + " sezon").isTrue();
        }
        JsonNode archive = MAPPER.readTree(java.nio.file.Path.of("docs/archive/replicas-removed.fc27.json").toFile());
        List<String> archived = new ArrayList<>();
        archive.forEach((r) -> archived.add(r.path("id").asText()));
        assertThat(archived).containsExactlyInAnyOrderElementsOf(REMOVED);
        for (String id : REMOVED) {
            assertThat(mockMvc.perform(get("/api/v1/replicas/" + id)).andReturn().getResponse().getStatus()).as(id).isEqualTo(404);
        }
    }

    /** REP-e: rapordaki formasyon düzeltmeleri (EA etiketi; 3-5-2 FC27'de doğrudan var). */
    @Test
    void formationCorrectionsFromTheVerificationReport() {
        Map<String, String> expected = Map.ofEntries(Map.entry("sergen_besiktas_2021", "4-2-3-1 (2)"), Map.entry("lucescu_besiktas_2003", "3-5-2"),
                Map.entry("michel_girona_2024", "4-2-3-1 (2)"), Map.entry("motta_bologna_2024", "4-2-3-1 (2)"), Map.entry("gunes_trabzonspor_2005", "4-2-3-1 (2)"),
                Map.entry("gunes_trabzonspor_2011", "4-2-3-1 (2)"), Map.entry("buruk_basaksehir_2020", "4-1-4-1"), Map.entry("hitzfeld_bayern_2001", "3-4-2-1"),
                Map.entry("toppmoller_leverkusen_2002", "4-2-3-1 (2)"), Map.entry("delbosque_real_2003", "4-2-3-1 (2)"), Map.entry("benitez_valencia_2004", "4-4-2 (2)"),
                Map.entry("ferguson_united_2008", "4-4-2 (2)"), Map.entry("mancini_city_2012", "4-2-3-1 (2)"), Map.entry("italiano_fiorentina_2023", "4-3-3 (2)"),
                Map.entry("pochettino_tottenham_2019", "4-2-3-1 (2)"), Map.entry("ancelotti_chelsea_2010", "4-3-3 (2)"));
        expected.forEach((id, formation) -> assertThat(replicas.find(id).orElseThrow(() -> new AssertionError(id)).eaFormation()).as(id).isEqualTo(formation));
        assertThat(replicas.find("lucescu_besiktas_2003").orElseThrow().formationId()).isEqualTo("3-5-2");
    }

    /** REP-e §3: kaynaklar Transfermarkt maç raporları; "VERIFY" / "model bilgisi" kalmadı. */
    @Test
    void noSourceIsAModelGuessAnymore() throws Exception {
        for (JsonNode r : seed()) {
            String sources = r.path("sources").toString();
            assertThat(sources).as(r.path("id").asText()).doesNotContain("VERIFY").doesNotContain("model bilgisi").contains("Transfermarkt");
        }
    }

    /** REP-e §4: güncel sezon replikası; oyun tarzı doğrulanmadığı için bilerek verified=false. */
    @Test
    void italianoBesiktas2027IsTheCurrentSeasonReplica() throws Exception {
        ReplicaService.Replica r = replicas.find("italiano_besiktas_2027").orElseThrow();
        assertThat(r.category()).isEqualTo("CURRENT");
        assertThat(r.season()).isEqualTo("2026-27");
        assertThat(r.club()).isEqualTo("Beşiktaş");
        assertThat(r.league()).isEqualTo("TR_SUPER_LIG");
        assertThat(r.slots()).hasSize(11);
        assertThat(r.eaFormation()).isEqualTo(replicas.find("postecoglou_celtic_2023").orElseThrow().eaFormation());
        assertThat(r.buildUp()).isEqualTo("BALANCED");
        assertThat(r.defensiveApproach()).isEqualTo("AGGRESSIVE");
        // REP-e 88 istedi; AGGRESSIVE bandı 91-100 (everyReplicaIsValid) → en yakın geçerli değer.
        assertThat(r.lineHeight()).isEqualTo(91);
        assertThat(r.fidelity()).isEqualTo(3);
        JsonNode pub = MAPPER.readTree(mockMvc.perform(get("/api/v1/replicas/italiano_besiktas_2027")).andReturn().getResponse().getContentAsString()).at("/data");
        assertThat(pub.path("verified").asBoolean(true)).isFalse();
        assertThat(pub.path("honours")).isEmpty();
    }

    /** REP-e: Trabzonspor 2004-05'in hocası Şenol Güneş; eski id eşleme (REP-b) üzerinden yeni id'ye çözülür. */
    @Test
    void trabzonspor2005IsRenamedAndTheOldIdResolvesThroughTheLegacyMap() throws Exception {
        assertThat(replicas.find("dogan_trabzonspor_2005")).isEmpty();
        assertThat(replicas.find("gunes_trabzonspor_2005").orElseThrow().coach()).isEqualTo("Şenol Güneş");
        assertThat(replicas.legacyIds()).containsEntry("dogan_trabzonspor_2005", "gunes_trabzonspor_2005");
        String body = mockMvc.perform(get("/api/v1/replicas/legacy-ids")).andReturn().getResponse().getContentAsString();
        assertThat(MAPPER.readTree(body).at("/data/dogan_trabzonspor_2005").asText()).isEqualTo("gunes_trabzonspor_2005");
        assertThat(mockMvc.perform(get("/api/v1/replicas/dogan_trabzonspor_2005")).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    /** REP-c: her kayıt o sezon oynadığı ligi taşır; liste ve detay yanıtında `league`. */
    @Test
    void everyReplicaCarriesTheLeagueItPlayedInThatSeason() throws Exception {
        for (JsonNode r : seed()) {
            assertThat(LEAGUES).as(r.path("id").asText()).contains(r.path("league").asText());
        }
        Map<String, String> spot = Map.of("sergen_besiktas_2021", "TR_SUPER_LIG", "zeman_pescara_2012", "IT_SERIE_B", "bielsa_leeds_2020", "EN_CHAMPIONSHIP",
                "schmidt_salzburg_2014", "AT_BUNDESLIGA", "postecoglou_celtic_2023", "SCO_PREMIERSHIP", "mourinho_porto_2004", "PT_LIGA", "tenhag_ajax_2019", "NL_EREDIVISIE",
                "garcia_lille_2011", "FR_LIGUE_1", "heynckes_bayern_2013", "DE_BUNDESLIGA", "simeone_atletico_2014", "ES_LALIGA");
        spot.forEach((id, league) -> assertThat(replicas.find(id).orElseThrow().league()).as(id).isEqualTo(league));
        JsonNode list = MAPPER.readTree(mockMvc.perform(get("/api/v1/replicas")).andReturn().getResponse().getContentAsString()).at("/data");
        list.forEach((r) -> assertThat(LEAGUES).as(r.path("id").asText()).contains(r.path("league").asText()));
        JsonNode detail = MAPPER.readTree(mockMvc.perform(get("/api/v1/replicas/inzaghi_inter_2023")).andReturn().getResponse().getContentAsString()).at("/data");
        assertThat(detail.path("league").asText()).isEqualTo("IT_SERIE_A");
    }

    /** REP-c: aynı kulüp tek yazımla (Türkçe yaygın ad); id'deki kulüp kökü aynı olan kayıtların adı aynı. */
    @Test
    void theSameClubIsAlwaysSpelledTheSameWay() throws Exception {
        Map<String, Set<String>> namesByStem = new java.util.TreeMap<>();
        Set<String> names = new HashSet<>();
        for (JsonNode r : seed()) {
            String id = r.path("id").asText();
            String stem = id.substring(id.indexOf('_') + 1, id.lastIndexOf('_'));
            namesByStem.computeIfAbsent(CLUB_STEM_ALIAS.getOrDefault(stem, stem), (k) -> new java.util.TreeSet<>()).add(r.path("club").asText());
            names.add(r.path("club").asText());
        }
        namesByStem.forEach((stem, clubNames) -> assertThat(clubNames).as(stem).hasSize(1));
        assertThat(names).contains("Bayern Münih", "Inter", "Milan", "Atlético Madrid", "Manchester United", "Barcelona", "Real Madrid")
                .doesNotContain("FC Bayern München", "Bayern Munich", "AC Milan", "Internazionale", "Atlético de Madrid", "FC Barcelona", "Valencia CF", "SSC Napoli");
    }

    /** Doğrulama bayrağı kuralı (değişmedi): verify boş ve kaynakta "VERIFY" yoksa verified=true. */
    @Test
    void theVerifiedFlagFollowsTheExistingRule() throws Exception {
        int verified = 0;
        for (JsonNode r : seed()) {
            boolean expected = r.path("verify").isEmpty() && !r.path("sources").toString().contains("VERIFY");
            JsonNode pub = MAPPER.readTree(mockMvc.perform(get("/api/v1/replicas/" + r.path("id").asText())).andReturn().getResponse().getContentAsString()).at("/data");
            assertThat(pub.path("verified").asBoolean()).as(r.path("id").asText()).isEqualTo(expected);
            verified += expected ? 1 : 0;
        }
        assertThat(verified).as("verified=true sayısı").isBetween(1, RECORDS - 1);
    }

    @Test
    void everyReplicaIsValid_formationSlotsRolesFocusesApproachAndPlanB() throws Exception {
        List<String> problems = new ArrayList<>();
        for (JsonNode r : seed()) {
            if (r.path("fc27").isNull()) {
                continue;
            }
            JsonNode fc = r.path("fc27");
            String id = r.path("id").asText();
            if (replicas.formationOf(fc.path("formation").asText()).isEmpty()) {
                problems.add(id + ": formasyon " + fc.path("formation").asText());
                continue;
            }
            if (fc.path("slots").size() != 11) {
                problems.add(id + ": 11 slot değil");
            }
            int lh = fc.path("lineHeight").asInt();
            int[] range = APPROACH.get(fc.path("defensiveApproach").asText());
            if (range == null || lh < range[0] || lh > range[1]) {
                problems.add(id + ": hat " + lh + " ≠ " + fc.path("defensiveApproach").asText());
            }
            for (JsonNode s : fc.path("slots")) {
                boolean valid = codec.roles().stream().anyMatch((role) -> role.position().equals(s.path("position").asText()) && norm(role.name()).equals(norm(s.path("role").asText()))
                        && role.focuses().stream().anyMatch((f) -> norm(f).equals(norm(s.path("focus").asText()))));
                if (!valid) {
                    problems.add(id + ": rol/focus geçersiz " + s.path("position").asText() + " " + s.path("role").asText() + "/" + s.path("focus").asText());
                }
            }
            JsonNode plan = fc.path("planB");
            if (!plan.isMissingNode() && !plan.isNull()) {
                for (JsonNode c : plan.path("changes")) {
                    if (!c.asText().matches("^[A-Z0-9]+: .+/.+( [(].+[)])?$")) {
                        problems.add(id + ": planB biçimi '" + c.asText() + "'");
                    }
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void all103ReplicasGetATacticCodeThatDecodesToExactlyTheSeedSettings() throws Exception {
        List<ReplicaService.Replica> all = replicas.all();
        assertThat(all).hasSize(RECORDS);
        for (ReplicaService.Replica r : all) {
            assertThat(r.tacticCode()).as(r.id()).hasSize(12);
            EaTactic tactic = codec.decode(r.tacticCode()).orElseThrow(() -> new AssertionError(r.id() + " decode"));
            assertThat(tactic.lineHeight()).as(r.id() + " hat").isEqualTo(r.lineHeight());
            assertThat(tactic.buildUp().name()).as(r.id() + " build-up").isEqualTo(r.buildUp());
            assertThat(codec.formation(tactic.formationId()).orElseThrow().label()).as(r.id() + " formasyon").isEqualTo(r.eaFormation());
            // decode edilen her slotun (mevki, rol, focus) seed'dekiyle aynı (EA sırası: aynı mevkiler sağdan sola)
            List<String> decoded = new ArrayList<>();
            for (int i = 0; i < tactic.slots().size(); i++) {
                var eaRole = codec.role(tactic.slots().get(i).roleId()).orElseThrow();
                decoded.add(codec.formation(tactic.formationId()).orElseThrow().positions().get(i) + "|" + norm(eaRole.name()) + "|" + norm(eaRole.focuses().get(tactic.slots().get(i).focusIndex())));
            }
            List<String> expected = new ArrayList<>(r.eaOrderedSlots().stream().map((s) -> s.position() + "|" + norm(s.role()) + "|" + norm(s.focus())).toList());
            assertThat(decoded).as(r.id() + " slotlar").containsExactlyElementsOf(expected);
        }
    }

    /** Liste kartındaki "dikkat noktası" sayısı = detaydaki uyarı + risk bulguları (bilgi sayılmaz). */
    @Test
    void theListCarriesTheAttentionCountThatMatchesTheAnalysis() throws Exception {
        int withAttention = 0;
        for (ReplicaService.Replica r : replicas.all()) {
            long expected = replicas.analyze(r).findings().stream().filter((f) -> f.severity() != com.fcareer.tacticanalysis.TacticAnalysisModels.Severity.INFO).count();
            assertThat((long) r.attentionCount()).as(r.id()).isEqualTo(expected);
            withAttention += r.attentionCount() > 0 ? 1 : 0;
        }
        assertThat(withAttention).as("E3-9 raporu: uyarı/risk üreten replika sayısı").isBetween(1, 40);
        String list = mockMvc.perform(get("/api/v1/replicas")).andReturn().getResponse().getContentAsString();
        assertThat(MAPPER.readTree(list).at("/data/0/attentionCount").isNumber()).isTrue();
    }

    /** REP-b: iç notlar (kaynaklar, doğrulanması gerekenler) yalnız yönetici görünümünde; herkese yalnız verified bayrağı. */
    @Test
    void internalVerificationNotesAreAdminOnly() throws Exception {
        JsonNode pub = MAPPER.readTree(mockMvc.perform(get("/api/v1/replicas/italiano_besiktas_2027")).andReturn().getResponse().getContentAsString()).at("/data");
        assertThat(pub.has("verify")).isFalse();
        assertThat(pub.has("sources")).isFalse();
        assertThat(pub.path("verified").asBoolean(true)).as("doğrulanmamış kayıt").isFalse();
        assertThat(pub.toString()).doesNotContain("VERIFY").doesNotContain("Transfermarkt");
        String token = com.fcareer.AdminTestSupport.adminToken(mockMvc, jdbc, MAPPER);
        JsonNode admin = MAPPER.readTree(mockMvc.perform(get("/api/v1/replicas/italiano_besiktas_2027").header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString()).at("/data");
        assertThat(admin.path("verify")).isNotEmpty();
        assertThat(admin.path("sources").isArray()).isTrue();
    }

    @Test
    void theReplicaEndpointsServeTheCodeAndTheAnalysisWithoutLogin() throws Exception {
        String list = mockMvc.perform(get("/api/v1/replicas")).andReturn().getResponse().getContentAsString();
        assertThat(MAPPER.readTree(list).at("/data").size()).isEqualTo(RECORDS);
        String detail = mockMvc.perform(get("/api/v1/replicas/guardiola_barcelona_2009")).andReturn().getResponse().getContentAsString();
        JsonNode d = MAPPER.readTree(detail).at("/data");
        assertThat(d.path("tacticCode").asText()).hasSize(12);
        assertThat(d.at("/analysis/metrics/band").asText()).isNotBlank();
        assertThat(d.path("fc27").isObject()).isTrue();
    }
}
