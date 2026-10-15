package com.fcareer.ut.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fcareer.ut.model.UtCaptureModels.Reward;
import com.fcareer.ut.model.UtCaptureModels.RewardItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * V3-05c-c: ödül kartı verisi ALLOWLIST ile alınır (denylist değil): EA itemData'sından yalnız RewardItem'ın 9 alanı okunur, gerisi hiç kopyalanmaz.
 * Bilgi tabanına yazılan reward JSON'u da buradan yeniden kurulur (type, value, count, item); bilinmeyen alan taşınmaz.
 */
public final class RewardAllowlist {

    private RewardAllowlist() {
    }

    /** itemData nesnesinden izinli alanlar; nesne değilse ya da hiçbir izinli alan yoksa null. */
    public static RewardItem item(JsonNode itemData) {
        if (Objects.isNull(itemData) || !itemData.isObject()) {
            return null;
        }
        RewardItem item = new RewardItem(longOrNull(itemData, "resourceId"), longOrNull(itemData, "assetId"), intOrNull(itemData, "rating"),
                textOrNull(itemData, "preferredPosition"), positions(itemData.path("possiblePositions")), intOrNull(itemData, "rareflag"),
                intOrNull(itemData, "nation"), intOrNull(itemData, "leagueId"), intOrNull(itemData, "teamid"));
        return Objects.isNull(item.resourceId()) && Objects.isNull(item.assetId()) && Objects.isNull(item.rating()) ? null : item;
    }

    /** Ham EA ödülü: tip alanı adı yola göre değişir (hub/challenge "type", awardSet "awardType"). */
    public static Reward reward(JsonNode award, String typeField) {
        return new Reward(award.path(typeField).asText(null), longOrNull(award, "value"), award.path("count").asInt(1), item(award.path("itemData")));
    }

    /** KB'ye yazılacak ödül dizisi: her öğe yalnız type/value/count ve izinli item ile yeniden kurulur (girdide item ya da itemData olabilir). */
    public static ArrayNode rewards(JsonNode rewards, ObjectMapper mapper) {
        ArrayNode out = mapper.createArrayNode();
        if (Objects.isNull(rewards) || !rewards.isArray()) {
            return out;
        }
        for (JsonNode r : rewards) {
            String type = r.hasNonNull("type") ? r.get("type").asText() : r.path("awardType").asText(null);
            RewardItem item = item(r.has("item") ? r.get("item") : r.path("itemData"));
            out.add(mapper.valueToTree(new Reward(type, longOrNull(r, "value"), r.path("count").asInt(1), item)));
        }
        return out;
    }

    /** Objective dizisi: her objective'in rewards alanı aynı allowlist'ten geçer; objective'in kendi alanları (id, ad, açıklama...) korunur. */
    public static ArrayNode objectives(JsonNode objectives, ObjectMapper mapper) {
        ArrayNode out = mapper.createArrayNode();
        if (Objects.isNull(objectives) || !objectives.isArray()) {
            return out;
        }
        for (JsonNode o : objectives) {
            ObjectNode copy = o.isObject() ? ((ObjectNode) o).deepCopy() : mapper.createObjectNode();
            copy.set("rewards", rewards(o.path("rewards"), mapper));
            out.add(copy);
        }
        return out;
    }

    private static List<String> positions(JsonNode node) {
        if (!node.isArray()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        node.forEach(p -> {
            if (p.isTextual()) {
                out.add(p.asText());
            }
        });
        return out;
    }

    private static Long longOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) && node.get(field).canConvertToLong() ? node.get(field).asLong() : null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) && node.get(field).canConvertToInt() ? node.get(field).asInt() : null;
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) && node.get(field).isTextual() ? node.get(field).asText() : null;
    }
}
