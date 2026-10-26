package com.fcareer.development.track;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * GTR-b maliyet ölçümü (opt-in, varsayılan atlanır): gerçek kariyer export'u bir kez içe aktarılır, görüntü 6 kez birer ay geriye kopyalanır (7 görüntü,
 * pencere 6 ay → hepsi açılır). growth-recent 5 ısınma, sonra soğuk (önbelleksiz) ve sıcak 20'şer ölçüm. Süre (medyan, en kötü), istek iş parçacığının ayırdığı bellek ve GC sonrası yığın
 * tepe artışı (kaba). Sonuç target/gtr-b-cost.json. Kullanım: -Dgtr.cost=/mutlak/yol/live_editor_..._in_career.log
 */
@IntegrationTest
@EnabledIfSystemProperty(named = "gtr.cost", matches = ".+")
class GrowthRecentCostReportTest {

    private static final int WARMUP = 5;
    private static final int RUNS = 20;
    private static final long MB = 1_048_576;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void measuresGrowthRecentWithSevenSnapshots() throws Exception {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
        byte[] log = Files.readAllBytes(Path.of(System.getProperty("gtr.cost")));
        String token = support.register();
        long careerId = support.premiumCareer(token, null);
        try {
            support.json(multipart("/api/v1/careers/" + careerId + "/import").file(new MockMultipartFile("log", "career.log", "text/plain", log)), token);
            for (int m = 1; m <= 6; m++) {
                jdbc.update("""
                        INSERT INTO career_growth_snapshot (career_id, game_date, month_key, save_uid, player_count, data)
                        SELECT career_id, game_date - (? || ' month')::interval, EXTRACT(YEAR FROM game_date - (? || ' month')::interval) * 100
                               + EXTRACT(MONTH FROM game_date - (? || ' month')::interval), save_uid, player_count, data
                          FROM career_growth_snapshot WHERE career_id = ? ORDER BY game_date DESC LIMIT 1""",
                        String.valueOf(m), String.valueOf(m), String.valueOf(m), careerId);
            }
            int snapshots = jdbc.queryForObject("SELECT count(*) FROM career_growth_snapshot WHERE career_id = ?", Integer.class, careerId);
            int players = jdbc.queryForObject("SELECT max(player_count) FROM career_growth_snapshot WHERE career_id = ?", Integer.class, careerId);
            assertThat(snapshots).isEqualTo(7);

            String path = "/api/v1/careers/" + careerId + "/growth-recent";
            int rows = 0;
            for (int i = 0; i < WARMUP; i++) {
                rows = support.json(get(path), token).at("/data").size();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("snapshots", snapshots);
            out.put("playersPerSnapshot", players);
            out.put("rows", rows);
            // Soğuk: her istekten önce data_version artar (önbellek ıskalar, görüntüler açılır). Sıcak: önbellekten.
            out.put("cold", measure(path, token, careerId, true));
            out.put("warm", measure(path, token, careerId, false));
            Files.writeString(Path.of("target/gtr-b-cost.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(out));
        } finally {
            mockMvc.perform(delete("/api/v1/careers/" + careerId).header("Authorization", "Bearer " + token)).andReturn();
        }
    }

    private Map<String, Long> measure(String path, String token, long careerId, boolean bump) throws Exception {
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        List<Long> ms = new ArrayList<>();
        List<Long> allocated = new ArrayList<>();
        List<Long> peakDelta = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            if (bump) {
                jdbc.update("UPDATE career SET data_version = data_version + 1 WHERE id = ?", careerId);
            }
            System.gc();
            long base = heapUsed();
            ManagementFactory.getMemoryPoolMXBeans().forEach(MemoryPoolMXBean::resetPeakUsage);
            long bytes = threads.getCurrentThreadAllocatedBytes();
            long started = System.nanoTime();
            mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();
            ms.add((System.nanoTime() - started) / 1_000);
            allocated.add(threads.getCurrentThreadAllocatedBytes() - bytes);
            peakDelta.add(heapPeak() - base);
        }
        Collections.sort(ms);
        Collections.sort(allocated);
        Collections.sort(peakDelta);
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("msMedian", ms.get(RUNS / 2) / 1_000);
        out.put("usMedian", ms.get(RUNS / 2));
        out.put("msMax", ms.get(RUNS - 1) / 1_000);
        out.put("allocatedKbMedian", allocated.get(RUNS / 2) / 1_024);
        out.put("allocatedMbMax", allocated.get(RUNS - 1) / MB);
        out.put("peakHeapDeltaMbMedian", peakDelta.get(RUNS / 2) / MB);
        out.put("peakHeapDeltaMbMax", peakDelta.get(RUNS - 1) / MB);
        return out;
    }

    private static long heapUsed() {
        return ManagementFactory.getMemoryPoolMXBeans().stream().filter((p) -> p.getType() == MemoryType.HEAP).mapToLong((p) -> p.getUsage().getUsed()).sum();
    }

    private static long heapPeak() {
        return ManagementFactory.getMemoryPoolMXBeans().stream().filter((p) -> p.getType() == MemoryType.HEAP).mapToLong((p) -> p.getPeakUsage().getUsed()).sum();
    }
}
