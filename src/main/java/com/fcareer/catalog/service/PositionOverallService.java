package com.fcareer.catalog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mevkiye göre overall: her mevki için oyunun overall formülünü, katalogdaki 22 bin oyuncudan (R² ≈ 0,998,
 * RMSE ≈ 0,33) doğrusal regresyonla öğrenilmiş ağırlıklarla yeniden üretir. Ağırlıklar seed/position-overall-weights.json.
 */
@Service
@RequiredArgsConstructor
public class PositionOverallService {

    private static final String GOALKEEPER = "GK";
    private static final int MIN_OVERALL = 1;
    private static final int MAX_OVERALL = 99;
    private static final List<String> OUTFIELD = List.of("ST", "LW", "RW", "CAM", "LM", "CM", "RM", "CDM", "LB", "CB", "RB");

    private record Formula(double intercept, Map<String, Double> weights) {
    }

    private final ObjectMapper objectMapper;
    private Map<String, Formula> formulas = Map.of();

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource("seed/position-overall-weights.json").getInputStream()) {
            Map<String, Map<String, Object>> raw = objectMapper.readValue(in, new TypeReference<>() {
            });
            Map<String, Formula> parsed = new LinkedHashMap<>();
            raw.forEach((position, body) -> {
                @SuppressWarnings("unchecked")
                Map<String, Number> weights = (Map<String, Number>) body.get("weights");
                Map<String, Double> converted = new LinkedHashMap<>();
                weights.forEach((column, value) -> converted.put(column, value.doubleValue()));
                parsed.put(position, new Formula(((Number) body.get("intercept")).doubleValue(), converted));
            });
            formulas = parsed;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Formül tüm mevkilerde aynı kalibrasyon hatasını taşır; oyuncunun gerçek overall'ı ile ana mevkisindeki formül
     * sonucu arasındaki fark (PlayStyle/yuvarlama kaynaklı) tüm mevkilere eklenir. Böylece ana mevki gerçek overall'a eşitlenir.
     */
    public Map<String, Integer> byPosition(Map<Attr, Integer> attrs, boolean goalkeeper, String mainPosition, int actualOverall) {
        Map<String, Double> raw = new LinkedHashMap<>();
        for (String position : goalkeeper ? List.of(GOALKEEPER) : OUTFIELD) {
            Formula formula = formulas.get(position);
            if (Objects.nonNull(formula)) {
                raw.put(position, evaluate(formula, attrs));
            }
        }
        double residual = raw.containsKey(mainPosition) ? actualOverall - raw.get(mainPosition) : 0;
        Map<String, Integer> result = new LinkedHashMap<>();
        raw.forEach((position, value) -> result.put(position, clamp(value + residual)));
        return result;
    }

    private double evaluate(Formula formula, Map<Attr, Integer> attrs) {
        double total = formula.intercept();
        for (Attr attr : attrs.keySet()) {
            Double weight = formula.weights().get(attr.getColumn());
            if (Objects.nonNull(weight)) {
                total += weight * attrs.get(attr);
            }
        }
        return total;
    }

    private static int clamp(double value) {
        return (int) Math.max(MIN_OVERALL, Math.min(MAX_OVERALL, Math.round(value)));
    }
}
