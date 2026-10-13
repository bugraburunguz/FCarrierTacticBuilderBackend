package com.fcareer.ut.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.provider.RatingStatKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Kartın 6 yüz istatistiği (RULE-10): saha oyuncusunda PAC/SHO/PAS/DRI/DEF/PHY, kalecide DIV/HAN/KIC/REF/SPD/POS.
 * Resmî değer (EA ratings sitesinden içe aktarılan) varsa o kullanılır; yoksa ut_rules içindeki ağırlıklarla hesaplanır.
 * Ağırlıklar koda gömülmez; verified:false iken arayüz hesaplanan değerin yanında "≈" gösterir.
 */
@Service
@RequiredArgsConstructor
public class FaceStatsService {

    public static final String OFFICIAL = "OFFICIAL";
    public static final String COMPUTED = "COMPUTED";
    private static final int FACE_COUNT = 6;

    private final UtRules rules;

    public record FaceStats(List<String> labels, List<Integer> values, String source) {
    }

    /** Resmî değer (6 elemanlı) varsa resmî, yoksa attribute'lardan hesaplanan; ikisi de yoksa null. */
    public FaceStats resolve(List<? extends Number> official, Map<Attr, Integer> attrs, boolean goalkeeper) {
        if (Objects.nonNull(official) && official.size() == FACE_COUNT) {
            return new FaceStats(labels(goalkeeper), official.stream().map(Number::intValue).toList(), OFFICIAL);
        }
        return compute(attrs, goalkeeper);
    }

    public FaceStats compute(Map<Attr, Integer> attrs, boolean goalkeeper) {
        JsonNode weights = rules.document().path(goalkeeper ? "faceStatWeightsGk" : "faceStatWeights");
        List<String> labels = labels(goalkeeper);
        List<Integer> values = new ArrayList<>();
        for (String label : labels) {
            Integer value = weighted(weights.path(label), attrs);
            if (Objects.isNull(value)) {
                return null;
            }
            values.add(value);
        }
        return new FaceStats(labels, values, COMPUTED);
    }

    /** Ağırlıklar doğrulandı mı (ut_rules verified); false ise hesaplanan değer yaklaşıktır. */
    public boolean weightsVerified() {
        return rules.document().path("faceStatWeights").path("verified").asBoolean(false)
                && rules.document().path("faceStatWeightsGk").path("verified").asBoolean(false);
    }

    private List<String> labels(boolean goalkeeper) {
        JsonNode node = rules.document().path(goalkeeper ? "faceStatWeightsGk" : "faceStatWeights").path("labels");
        List<String> labels = new ArrayList<>();
        node.forEach(n -> labels.add(n.asText()));
        return labels;
    }

    private static Integer weighted(JsonNode weights, Map<Attr, Integer> attrs) {
        double sum = 0;
        double total = 0;
        Iterator<Map.Entry<String, JsonNode>> fields = weights.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            Attr attr = attrOf(entry.getKey());
            Integer value = Objects.isNull(attr) ? null : attrs.get(attr);
            if (Objects.isNull(value)) {
                return null;
            }
            sum += value * entry.getValue().asDouble();
            total += entry.getValue().asDouble();
        }
        return total == 0 ? null : (int) Math.round(sum / total);
    }

    private static Attr attrOf(String siteKey) {
        return RatingStatKeys.KEYS.entrySet().stream().filter(e -> e.getValue().equals(siteKey)).map(Map.Entry::getKey).findFirst().orElse(null);
    }
}
