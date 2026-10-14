package com.fcareer.fitengine;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fcareer.common.service.MessageService;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.response.FitNarrative;
import com.fcareer.fitengine.service.FitEngineV3.Contribution;
import com.fcareer.fitengine.service.FitNarrativeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3-8: RoleFit anlatımı (NLG). Her cümle yalnız katkı ve kanıt sayılarından üretilir ve cümlenin sayısal kaynağı log'lanır. "Biçilmiş kaftan" yalnız levelFit ≥ 85 iken (eşik config, verified:false).
 */
class FitNarrativeTest {

    private static final List<Contribution> CONTRIBUTIONS = List.of(
            new Contribution("attr", "Vision", 11.24), new Contribution("attr", "ShortPassing", 10.31), new Contribution("attr", "LongPassing", 9.87), new Contribution("attr", "Positioning", 4.02),
            new Contribution("attr", "Acceleration", -1.12), new Contribution("base", "Stamina", -3.5), new Contribution("playstyle", "IncisivePass", 5.0), new Contribution("position", "CB", -6.4));

    private FitNarrativeService service;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        service = new FitNarrativeService(new MessageService(messages), new FitEngineProperties());
        logger = (Logger) LoggerFactory.getLogger(FitNarrativeService.class);
        logger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private FitNarrative narrate(Double levelFit) {
        return service.narrate("Playmaker", 88.4, levelFit, 240, levelFit == null ? "AT_LEVEL" : levelFit >= 65 ? "GOOD" : "AT_LEVEL", CONTRIBUTIONS);
    }

    @Test
    void theTailoredBadgeAppearsOnlyAtLevelFitEightyFiveAndAbove() {
        assertThat(narrate(85.0).isTailored()).isTrue();
        assertThat(narrate(97.3).isTailored()).isTrue();
        assertThat(narrate(84.9).isTailored()).isFalse();
        assertThat(narrate(null).isTailored()).isFalse();
        assertThat(narrate(85.0).getHeadline()).containsIgnoringCase("Biçilmiş kaftan");
    }

    @Test
    void noSentenceBelowEightyFiveEverClaimsTheTailoredFit() {
        for (int level = 0; level < 85; level++) {
            FitNarrative n = narrate((double) level);
            assertThat(n.isTailored()).as("levelFit " + level).isFalse();
            assertThat(n.getHeadline()).as("levelFit " + level).doesNotContainIgnoringCase("kaftan");
            n.getStrengths().forEach((s) -> assertThat(s.getText()).doesNotContainIgnoringCase("kaftan"));
        }
    }

    @Test
    void strengthsAreTheThreeBiggestPositiveContributionsAndWeaknessesTheTwoBiggestNegativeOnes() {
        FitNarrative n = narrate(70.0);
        assertThat(n.getStrengths()).extracting((s) -> s.getKey()).containsExactly("Vision", "ShortPassing", "LongPassing");
        assertThat(n.getWeaknesses()).extracting((s) -> s.getKey()).containsExactly("CB", "Stamina");
    }

    @Test
    void everySentenceCarriesItsNumericSourceAndShowsTheNumber() {
        FitNarrative n = narrate(70.0);
        for (FitNarrative.Sentence s : java.util.stream.Stream.concat(n.getStrengths().stream(), n.getWeaknesses().stream()).toList()) {
            Contribution source = CONTRIBUTIONS.stream().filter((c) -> c.key().equals(s.getKey()) && c.term().equals(s.getTerm())).findFirst().orElseThrow();
            assertThat(s.getPoints()).isEqualTo(Math.round(source.points() * 10.0) / 10.0);
            assertThat(s.getText()).contains(String.format(Locale.ROOT, "%.1f", Math.abs(source.points())));
            assertThat(s.getText()).as("iç kod yok").doesNotContain("{").doesNotContain("_");
        }
    }

    @Test
    void theHeadlineShowsTheRolePointsTheLevelFitAndTheSample() {
        FitNarrative n = narrate(72.0);
        assertThat(n.getHeadline()).contains("88").contains("72").contains("240");
    }

    @Test
    void theNumericSourceOfTheNarrativeIsLogged() {
        narrate(91.0);
        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).anyMatch((m) -> m.contains("levelFit : 91.0") && m.contains("currentFit : 88.4") && m.contains("Vision") && m.contains("11.2"));
    }

    @Test
    void positionAndPlaystyleTermsReadNaturally() {
        FitNarrative n = service.narrate("Playmaker", 80.0, 60.0, 100, "AT_LEVEL", List.of(new Contribution("position", "CB", -6.4), new Contribution("playstyle", "IncisivePass", 5.0)));
        assertThat(n.getWeaknesses().get(0).getText()).containsIgnoringCase("mevki").contains("CB").contains("6.4");
        assertThat(n.getStrengths().get(0).getText()).contains("5");
    }
}
