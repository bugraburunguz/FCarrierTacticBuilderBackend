package com.fcareer.tacticanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** `seed/role-movement.fc27.json` (E3-7): (rol, focus) hareket profili, bant hat kayması ve pres çarpanı. Değerler taslak, verified:false. */
@Component
public class RoleMovements {

    private static final String SEED = "seed/role-movement.fc27.json";

    /** dxIn: topla ortaya kayma (− kanada açılır); dyFwd: topla ileri kayma (− geriye düşer); diğerleri 0-1. */
    public record Movement(String id, String role, String focus, List<String> positions, double dxIn, double dyFwd, double w, double r, double d, double p, double s,
                           double cross, double cutIn, double runBehind, double longShot) {
    }

    private final Map<String, Movement> byId = new HashMap<>();
    private final Map<AnalysisBand, Double> lineShift = new EnumMap<>(AnalysisBand.class);
    private final Map<AnalysisBand, Double> pressMult = new EnumMap<>(AnalysisBand.class);

    public RoleMovements(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource(SEED).getInputStream()) {
            JsonNode root = mapper.readTree(in);
            for (JsonNode n : root.path("roles")) {
                List<String> positions = new ArrayList<>();
                n.path("positions").forEach((x) -> positions.add(x.asText()));
                byId.put(n.path("id").asText(), new Movement(n.path("id").asText(), n.path("role").asText(), n.path("focus").asText(), positions, n.path("dxIn").asDouble(),
                        n.path("dyFwd").asDouble(), n.path("w").asDouble(), n.path("r").asDouble(), n.path("d").asDouble(), n.path("p").asDouble(), n.path("s").asDouble(),
                        n.path("cross").asDouble(), n.path("cutIn").asDouble(), n.path("runBehind").asDouble(), n.path("longShot").asDouble()));
            }
            for (AnalysisBand band : AnalysisBand.values()) {
                lineShift.put(band, root.path("lineShiftByBand").path(band.name()).asDouble());
                pressMult.put(band, root.path("pressMultByBand").path(band.name()).asDouble(1.0));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<Movement> find(String roleId) {
        return Optional.ofNullable(byId.get(roleId));
    }

    public double lineShift(AnalysisBand band) {
        return lineShift.get(band);
    }

    public double pressMult(AnalysisBand band) {
        return pressMult.get(band);
    }
}
