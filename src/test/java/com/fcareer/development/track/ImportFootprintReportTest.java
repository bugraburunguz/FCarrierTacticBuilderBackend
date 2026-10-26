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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * GTR-a ölçümü (opt-in, varsayılan atlanır): kariyer içi export'un içe aktarma süresi ve yığın bellek tepe değeri, anlık görüntü kapalı ve açıkken.
 * Her durum iki kez (ısınma + ölçüm); sonuç target/gtr-a-footprint.json. Kullanım: -Dgtr.footprint=/mutlak/yol/live_editor_..._in_career.log
 */
@IntegrationTest
@EnabledIfSystemProperty(named = "gtr.footprint", matches = ".+")
class ImportFootprintReportTest {

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
    private GrowthTrackProperties properties;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void measuresImportTimeAndPeakHeap() throws Exception {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, clubRepository);
        byte[] log = Files.readAllBytes(Path.of(System.getProperty("gtr.footprint")));
        Map<String, Object> out = new LinkedHashMap<>();
        boolean original = properties.isEnabled();
        try {
            for (boolean enabled : new boolean[] {false, true}) {
                properties.setEnabled(enabled);
                for (int run = 0; run < 2; run++) {
                    String token = support.register();
                    long careerId = support.premiumCareer(token, null);
                    System.gc();
                    ManagementFactory.getMemoryPoolMXBeans().forEach(MemoryPoolMXBean::resetPeakUsage);
                    long started = System.nanoTime();
                    support.json(multipart("/api/v1/careers/" + careerId + "/import").file(new MockMultipartFile("log", "career.log", "text/plain", log)), token);
                    long ms = (System.nanoTime() - started) / 1_000_000;
                    long peak = ManagementFactory.getMemoryPoolMXBeans().stream().filter((p) -> p.getType() == MemoryType.HEAP)
                            .mapToLong((p) -> p.getPeakUsage().getUsed()).sum();
                    Integer bytes = enabled ? jdbc.queryForObject("SELECT coalesce(sum(length(data)), 0)::int FROM career_growth_snapshot WHERE career_id = ?",
                            Integer.class, careerId) : null;
                    Integer players = enabled ? jdbc.queryForObject("SELECT coalesce(max(player_count), 0) FROM career_growth_snapshot WHERE career_id = ?",
                            Integer.class, careerId) : null;
                    out.put((enabled ? "acik" : "kapali") + "_" + run, Map.of("ms", ms, "peakHeapMb", peak / 1_048_576, "snapshotBytes", String.valueOf(bytes),
                            "players", String.valueOf(players)));
                    mockMvc.perform(delete("/api/v1/careers/" + careerId).header("Authorization", "Bearer " + token)).andReturn();
                }
            }
        } finally {
            properties.setEnabled(original);
        }
        Files.writeString(Path.of("target/gtr-a-footprint.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(out));
    }
}
