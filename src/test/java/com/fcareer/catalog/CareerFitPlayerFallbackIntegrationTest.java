package com.fcareer.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.CareerTestSupport;
import com.fcareer.IntegrationTest;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.ingestion.service.IngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KARIYER-UX FAZ 5 (4b-1 kararı): rol uyumu kariyer kaydındaki oyuncu verisinden hesaplanır (kariyerde reyting değişir, katalog eskir);
 * kayıtta özniteliği olmayan oyuncu için öznitelikler katalogdan gelir (overall ve mevkiler kayıttan kalır).
 */
@IntegrationTest
class CareerFitPlayerFallbackIntegrationTest {

    private static final short GAME_VERSION = 27;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BasePlayerRepository basePlayerRepository;
    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private PlayerService playerService;

    @BeforeEach
    void seed() {
        if (basePlayerRepository.count() == 0) {
            ingestionService.run();
        }
    }

    @Test
    void recordAttributesWinAndAPlayerWithoutRecordAttributesFallsBackToTheCatalog() throws Exception {
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, jdbc);
        long careerId = support.premiumCareer(support.register(), null);
        List<Long> squad = jdbc.queryForList("SELECT base_player_id FROM user_player_state WHERE career_id = ? ORDER BY base_player_id LIMIT 2", Long.class, careerId);
        long withAttrs = squad.get(0);
        long withoutAttrs = squad.get(1);
        playerService.fitPlayers(squad, GAME_VERSION, careerId);
        jdbc.update("UPDATE career_player SET attrs[1] = 99, ovr = 88 WHERE career_id = ? AND player_id = ?", careerId, withAttrs);
        jdbc.update("UPDATE career_player SET attrs = '{}', ovr = 77 WHERE career_id = ? AND player_id = ?", careerId, withoutAttrs);

        List<FitPlayer> fit = playerService.fitPlayers(squad, GAME_VERSION, careerId);
        FitPlayer catalog = playerService.fitPlayers(List.of(withoutAttrs), GAME_VERSION, null).get(0);

        Attr first = Attr.values()[0];
        assertThat(fit.get(0).getAttrs().get(first)).as("kayıttaki öznitelik").isEqualTo(99);
        assertThat(fit.get(0).getOverall()).isEqualTo(88);
        assertThat(fit.get(1).getAttrs()).as("kayıtta öznitelik yok: katalog").isNotEmpty().isEqualTo(catalog.getAttrs());
        assertThat(fit.get(1).getOverall()).as("overall kayıttan").isEqualTo(77);
    }

    /** FCA-9: kariyerde motorun potansiyeli kayıttaki (LE) değerdir; katalog tahmini (MODELED) yalnız kayıtta POT yoksa kullanılır. */
    @Test
    void recordPotentialWinsOverTheModeledEstimateWhichIsOnlyAFallback() throws Exception {
        CareerTestSupport support = new CareerTestSupport(mockMvc, objectMapper, jdbc);
        long careerId = support.premiumCareer(support.register(), null);
        List<Long> squad = jdbc.queryForList("SELECT base_player_id FROM user_player_state WHERE career_id = ? ORDER BY base_player_id LIMIT 2", Long.class, careerId);
        long withPot = squad.get(0);
        long withoutPot = squad.get(1);
        for (long id : squad) {
            jdbc.update("""
                    INSERT INTO player_valuation (base_player_id, game_version_id, source, potential, model_version) VALUES (?, ?, 'MODELED', 76, 'test')
                    ON CONFLICT ON CONSTRAINT uq_valuation DO UPDATE SET potential = 76""", id, GAME_VERSION);
        }
        playerService.fitPlayers(squad, GAME_VERSION, careerId);
        jdbc.update("UPDATE career_player SET pot = 84 WHERE career_id = ? AND player_id = ?", careerId, withPot);
        jdbc.update("UPDATE career_player SET pot = NULL WHERE career_id = ? AND player_id = ?", careerId, withoutPot);

        List<FitPlayer> fit = playerService.fitPlayers(squad, GAME_VERSION, careerId);

        assertThat(fit.get(0).getPotential()).as("kayıttaki POT").isEqualTo(84);
        assertThat(fit.get(1).getPotential()).as("kayıtta POT yok: katalog tahmini").isEqualTo(76);
    }
}
