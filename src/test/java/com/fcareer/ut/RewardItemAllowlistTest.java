package com.fcareer.ut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.ut.model.UtCaptureModels.RewardItem;
import com.fcareer.ut.service.RewardAllowlist;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** V3-05c-c: ödül kartı verisi allowlist ile alınır (denylist değil); KB'ye yazılan reward JSON'u da aynı listeden geçer. */
class RewardItemAllowlistTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keepsOnlyTheNineAllowedFields() throws Exception {
        RewardItem item = RewardAllowlist.item(mapper.readTree(RewardItemFixture.ITEM_DATA));

        assertThat(item.resourceId()).isEqualTo(50000777L);
        assertThat(item.assetId()).isEqualTo(777L);
        assertThat(item.rating()).isEqualTo(88);
        assertThat(item.preferredPosition()).isEqualTo("ST");
        assertThat(item.possiblePositions()).containsExactly("ST", "CF");
        assertThat(item.rareflag()).isEqualTo(3);
        assertThat(item.nation()).isEqualTo(901);
        assertThat(item.leagueId()).isEqualTo(902);
        assertThat(item.teamid()).isEqualTo(903);

        JsonNode json = mapper.valueToTree(item);
        List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrderElementsOf(RewardItemFixture.ALLOWED);
        String text = json.toString();
        RewardItemFixture.FORBIDDEN.forEach(f -> assertThat(text).as(f).doesNotContain(f));
    }

    @Test
    void missingOrNonObjectItemDataGivesNoItem() throws Exception {
        assertThat(RewardAllowlist.item(mapper.missingNode())).isNull();
        assertThat(RewardAllowlist.item(mapper.readTree("[1,2]"))).isNull();
        assertThat(RewardAllowlist.item(mapper.readTree("{\"owners\":3}"))).isNull();
    }

    @Test
    void storedRewardJsonIsRebuiltFromTheAllowlistIncludingNestedObjectiveRewards() throws Exception {
        JsonNode dirty = mapper.readTree("[{\"type\":\"item\",\"value\":50000777,\"count\":1,\"secret\":\"x\",\"item\":" + RewardItemFixture.ITEM_DATA + "},"
                + "{\"type\":\"pack\",\"value\":519,\"count\":2,\"itemData\":" + RewardItemFixture.ITEM_DATA + "}]");

        JsonNode clean = RewardAllowlist.rewards(dirty, mapper);

        assertThat(clean).hasSize(2);
        assertThat(clean.get(0).at("/item/assetId").asLong()).isEqualTo(777L);
        assertThat(clean.get(1).at("/item/assetId").asLong()).isEqualTo(777L);
        assertThat(clean.get(1).at("/value").asLong()).isEqualTo(519L);
        String text = clean.toString();
        assertThat(text).doesNotContain("secret").doesNotContain("itemData");
        RewardItemFixture.FORBIDDEN.forEach(f -> assertThat(text).as(f).doesNotContain(f));

        JsonNode objectives = mapper.readTree("[{\"id\":9,\"name\":\"o\",\"rewards\":" + dirty + "}]");
        String cleanObjectives = RewardAllowlist.objectives(objectives, mapper).toString();
        assertThat(cleanObjectives).contains("\"assetId\":777").contains("\"name\":\"o\"").doesNotContain("900000000001");
        // objective'in kendi "id" alanı meşru; kart örneği kimliği (900000000001) yukarıda yok
        RewardItemFixture.FORBIDDEN.stream().filter(f -> !f.equals("\"id\"")).forEach(f -> assertThat(cleanObjectives).as(f).doesNotContain(f));
    }
}
