package com.fcareer.ingestion.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * LE `trait1/trait2/icontrait1/icontrait2` bit maskelerini PlayStyle adlarına çözer (E3-2). Eşleme `seed/le-playstyle-bits.json`
 * (kaynak: LE localize dizgeleri; tools/fit-seeds/gen-le-playstyle-bits.mjs). Dizgesi olmayan bit `unknown_<alan>_<bit>` olarak döner.
 * Kural: `icontrait` biti PS+ (2); yoksa `trait` biti PS (1). İkisi de yoksa PS yok.
 */
@Component
public class LePlaystyleBits {

    private static final int MASK_BITS = 62;
    private static final int PLAYSTYLE_PLUS = 2;
    private static final int PLAYSTYLE = 1;
    private static final String KIND_PLAYSTYLE = "playstyle";
    private static final String UNKNOWN_FORMAT = "unknown_%s_%d";

    public record Decoded(Map<String, Integer> playstyles, List<String> hiddenTraits, List<String> unknown) {
    }

    private record Bit(String name, String kind) {
    }

    private final Map<String, Map<Integer, Bit>> fields = new LinkedHashMap<>();

    @Autowired
    public LePlaystyleBits(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("seed/le-playstyle-bits.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in).path("fields");
            root.fields().forEachRemaining((field) -> {
                Map<Integer, Bit> bits = new LinkedHashMap<>();
                field.getValue().fields().forEachRemaining((e) -> bits.put(Integer.parseInt(e.getKey()), new Bit(e.getValue().path("name").asText(), e.getValue().path("kind").asText())));
                fields.put(field.getKey(), bits);
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Decoded decode(Long trait1, Long trait2, Long iconTrait1, Long iconTrait2) {
        Map<String, Integer> playstyles = new LinkedHashMap<>();
        List<String> hidden = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        decodeField("trait1", "icontrait1", trait1, iconTrait1, playstyles, hidden, unknown);
        decodeField("trait2", "icontrait2", trait2, iconTrait2, playstyles, hidden, unknown);
        return new Decoded(playstyles, hidden, unknown);
    }

    private void decodeField(String baseField, String plusField, Long base, Long plus, Map<String, Integer> playstyles, List<String> hidden, List<String> unknown) {
        Map<Integer, Bit> known = fields.get(baseField);
        for (int bit = 0; bit < MASK_BITS; bit++) {
            boolean baseSet = isSet(base, bit);
            boolean plusSet = isSet(plus, bit);
            if (!baseSet && !plusSet) {
                continue;
            }
            Bit def = known.get(bit);
            if (Objects.isNull(def)) {
                unknown.add(String.format(UNKNOWN_FORMAT, baseField, bit));
            } else if (KIND_PLAYSTYLE.equals(def.kind())) {
                playstyles.put(def.name(), plusSet ? PLAYSTYLE_PLUS : PLAYSTYLE);
            } else {
                hidden.add(def.name());
            }
        }
    }

    private static boolean isSet(Long mask, int bit) {
        return Objects.nonNull(mask) && ((mask >> bit) & 1L) == 1L;
    }
}
