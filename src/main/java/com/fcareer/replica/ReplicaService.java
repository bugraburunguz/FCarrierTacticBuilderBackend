package com.fcareer.replica;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.tacticanalysis.BuildUpType;
import com.fcareer.tacticanalysis.RoleMovements;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Analysis;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Input;
import com.fcareer.tacticanalysis.TacticAnalysisModels.Slot;
import com.fcareer.tacticanalysis.TacticAnalyzer;
import com.fcareer.tacticcode.model.EaBuildUp;
import com.fcareer.tacticcode.service.TacticCodeService;
import com.fcareer.tactics.model.FormationDefinition;
import com.fcareer.tactics.model.FormationSlot;
import com.fcareer.tactics.service.FormationRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Replika taktikleri (E3-9, REP-01..03): `seed/replicas.fc27.json` (107 kayıt; yer tutucu hariç 106). `tacticCode` seed'de değil yüklemede `EaTacticCodec` ile üretilir; böylece kod her zaman
 * seed ayarlarının kodlanmış halidir. Roller seed'deki (mevki, rol, focus) adlarıdır; analiz (13 §3.4) roller + formasyon konumlarıyla etiketsiz çalışır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplicaService {

    private static final String SEED = "seed/replicas.fc27.json";

    public record RoleSlot(String slotId, String position, String role, String focus, String roleId) {
    }

    /** eaOrderedSlots: EA kod sırası (aynı mevkiler sağdan sola); slots: formasyonun slot sırası. */
    public record Replica(String id, String category, String club, String coach, String season, String formationId, String eaFormation, String buildUp, String defensiveApproach, int lineHeight,
                          Integer fidelity, String tacticCode, List<RoleSlot> slots, List<RoleSlot> eaOrderedSlots) {
    }

    private final ObjectMapper mapper;
    private final FormationRegistry formations;
    private final TacticCodeService tacticCodeService;
    private final RoleMovements movements;
    private final TacticAnalyzer analyzer;

    private final Map<String, JsonNode> raw = new LinkedHashMap<>();
    private final Map<String, Replica> byId = new LinkedHashMap<>();

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource(SEED).getInputStream()) {
            for (JsonNode r : mapper.readTree(in)) {
                raw.put(r.path("id").asText(), r);
                if (!r.path("fc27").isNull()) {
                    Replica replica = build(r);
                    byId.put(replica.id(), replica);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("Replicas loaded records : {} withTactic : {}", raw.size(), byId.size());
    }

    public Optional<FormationDefinition> formationOf(String eaLabel) {
        return formations.findAll().stream().filter((f) -> eaLabel.equals(f.eaLabel())).findFirst();
    }

    public List<Replica> all() {
        return List.copyOf(byId.values());
    }

    public Optional<Replica> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<JsonNode> rawOf(String id) {
        return Optional.ofNullable(raw.get(id));
    }

    /** Etiketsiz analiz: formasyon konumları + replikanın rolleri + build-up + hat (oyuncu yok). */
    public Analysis analyze(Replica replica) {
        FormationDefinition formation = formations.get(replica.formationId());
        Map<String, RoleSlot> bySlot = new HashMap<>();
        replica.slots().forEach((s) -> bySlot.put(s.slotId(), s));
        List<Slot> slots = new ArrayList<>();
        for (FormationSlot s : formation.slots()) {
            slots.add(new Slot(s.slotId(), s.position(), s.x(), s.y(), bySlot.get(s.slotId()).roleId()));
        }
        return analyzer.analyze(new Input(formation.id(), BuildUpType.parse(replica.buildUp()), replica.lineHeight(), slots, Map.of()));
    }

    private Replica build(JsonNode r) {
        JsonNode fc = r.path("fc27");
        String eaLabel = fc.path("formation").asText();
        FormationDefinition formation = formationOf(eaLabel).orElseThrow(() -> new IllegalStateException(r.path("id").asText() + ": formasyon " + eaLabel));
        List<RoleSlot> slots = new ArrayList<>();
        Map<String, String[]> roleBySlot = new HashMap<>();
        for (JsonNode s : fc.path("slots")) {
            String roleId = slug(s.path("role").asText()) + "_" + slug(s.path("focus").asText());
            if (movements.find(roleId).isEmpty()) {
                throw new IllegalStateException(r.path("id").asText() + ": rol " + roleId);
            }
            slots.add(new RoleSlot(s.path("slot").asText(), s.path("position").asText(), s.path("role").asText(), s.path("focus").asText(), roleId));
            roleBySlot.put(s.path("slot").asText(), new String[]{s.path("role").asText(), s.path("focus").asText()});
        }
        EaBuildUp buildUp = EaBuildUp.valueOf(fc.path("buildUp").asText());
        int lineHeight = fc.path("lineHeight").asInt();
        String code = tacticCodeService.exportSlots(formation, buildUp, lineHeight, roleBySlot);
        // EA sırası: aynı mevkiler x azalan (sağdan sola)
        Map<String, List<FormationSlot>> byPosition = new HashMap<>();
        formation.slots().stream().sorted(Comparator.comparingInt(FormationSlot::x).reversed()).forEach((s) -> byPosition.computeIfAbsent(s.position(), (k) -> new ArrayList<>()).add(s));
        Map<String, RoleSlot> bySlotId = new HashMap<>();
        slots.forEach((s) -> bySlotId.put(s.slotId(), s));
        List<RoleSlot> ordered = new ArrayList<>();
        Map<String, Integer> used = new HashMap<>();
        for (String position : tacticCodeService.eaPositions(formation)) {
            int n = used.merge(position, 1, Integer::sum) - 1;
            ordered.add(bySlotId.get(byPosition.get(position).get(n).slotId()));
        }
        Integer fidelity = r.path("replicaFidelity").path("score").isNumber() ? r.path("replicaFidelity").path("score").asInt() : null;
        return new Replica(r.path("id").asText(), r.path("category").asText(), r.path("club").asText(), r.path("coach").asText(), r.path("season").asText(), formation.id(), eaLabel, buildUp.name(),
                fc.path("defensiveApproach").asText(), lineHeight, fidelity, code, slots, ordered);
    }

    static String slug(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }
}
