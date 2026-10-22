package com.fcareer.recommendation.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Kayıtlı taktikten (user_tactic) bir slotun rolü/focus'u. Etkin taktik: kariyerin ana taktiği (D-30); yoksa en düşük slot numaralı kayıt (geri uyum). */
@Component
@RequiredArgsConstructor
public class TacticSlotLookup {

    /** roleId taban rol ya da bileşik kimlik olabilir; focus boş olabilir. */
    public record SlotRole(String roleId, String focus) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public Optional<SlotRole> find(long careerId, String slotId, String position) {
        List<String> tactics = jdbc.queryForList("SELECT slots::text FROM user_tactic WHERE career_id = ? ORDER BY is_main DESC, slot LIMIT 1", String.class, careerId);
        if (tactics.isEmpty()) {
            return Optional.empty();
        }
        try {
            JsonNode slots = objectMapper.readTree(tactics.get(0));
            for (JsonNode slot : slots) {
                boolean byId = Objects.nonNull(slotId) && slotId.equals(slot.path("slot").asText(null));
                boolean byPosition = Objects.isNull(slotId) && position.equals(slot.path("position").asText(null));
                if ((byId || byPosition) && slot.hasNonNull("roleId")) {
                    return Optional.of(new SlotRole(slot.path("roleId").asText(), slot.path("focus").asText(null)));
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
