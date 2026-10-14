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

/** E3-9 (REP-01..03): 107 replika seed'i; yer tutucu hariç 106'sının tacticCode'u üretilir ve decode edilince seed ayarlarıyla birebir aynıdır; seed doğrulayıcısı (REP-02). */
@IntegrationTest
class ReplicaSeedIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, int[]> APPROACH = Map.of("DEEP", new int[]{1, 30}, "BALANCED", new int[]{31, 60}, "HIGH", new int[]{61, 90}, "AGGRESSIVE", new int[]{91, 100});

    @Autowired
    private ReplicaService replicas;
    @Autowired
    private EaTacticCodec codec;
    @Autowired
    private MockMvc mockMvc;

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static JsonNode seed() throws Exception {
        try (InputStream in = new ClassPathResource("seed/replicas.fc27.json").getInputStream()) {
            return MAPPER.readTree(in);
        }
    }

    @Test
    void theSeedHas107RecordsAndOnlyThePlaceholderHasNoTactic() throws Exception {
        JsonNode seed = seed();
        assertThat(seed.size()).isEqualTo(107);
        Set<String> ids = new HashSet<>();
        List<String> noTactic = new ArrayList<>();
        for (JsonNode r : seed) {
            assertThat(ids.add(r.path("id").asText())).as("id tekil: " + r.path("id")).isTrue();
            if (r.path("fc27").isNull()) {
                noTactic.add(r.path("id").asText());
            }
        }
        assertThat(noTactic).containsExactly("ucl_winner_2026_tbd");
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
    void all106ReplicasGetATacticCodeThatDecodesToExactlyTheSeedSettings() throws Exception {
        List<ReplicaService.Replica> all = replicas.all();
        assertThat(all).hasSize(106);
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

    @Test
    void theReplicaEndpointsServeTheCodeAndTheAnalysisWithoutLogin() throws Exception {
        String list = mockMvc.perform(get("/api/v1/replicas")).andReturn().getResponse().getContentAsString();
        assertThat(MAPPER.readTree(list).at("/data").size()).isEqualTo(106);
        String detail = mockMvc.perform(get("/api/v1/replicas/guardiola_barcelona_2009")).andReturn().getResponse().getContentAsString();
        JsonNode d = MAPPER.readTree(detail).at("/data");
        assertThat(d.path("tacticCode").asText()).hasSize(12);
        assertThat(d.at("/analysis/metrics/band").asText()).isNotBlank();
        assertThat(d.path("fc27").isObject()).isTrue();
    }
}
