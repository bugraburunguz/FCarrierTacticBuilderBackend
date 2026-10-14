package com.fcareer.recommendation.pipeline;

import com.fcareer.catalog.repository.CareerPlayerRepository.TeamStrengthRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-6b: kulüp gücü = kayıttaki kulübün en iyi 16 oyuncusunun OVR ortalaması; lig gücü = ligdeki kulüp güçlerinin medyanı; küçük örneklem lig gücüne düşer. */
class ClubStrengthTest {

    private static final int MIN_PLAYERS = 11;
    private static final int MIN_CLUBS = 3;

    private static List<TeamStrengthRow> rows() {
        return List.of(
                new TeamStrengthRow("T:1", "Liga A", 70.0, 16), new TeamStrengthRow("T:2", "Liga A", 72.0, 16), new TeamStrengthRow("T:3", "Liga A", 74.0, 16),
                new TeamStrengthRow("T:4", "Liga A", 76.0, 16), new TeamStrengthRow("T:5", "Liga A", 90.0, 5),
                new TeamStrengthRow("T:6", "Liga B", 60.0, 16), new TeamStrengthRow("T:7", "Liga B", 62.0, 16));
    }

    @Test
    void aClubWithEnoughPlayersKeepsItsOwnStrength() {
        ClubStrength strength = ClubStrength.of(rows(), MIN_PLAYERS, MIN_CLUBS);
        assertThat(strength.clubStrength("T:2")).isEqualTo(72.0);
    }

    @Test
    void leagueStrengthIsTheMedianOfTheClubsWithEnoughPlayers() {
        ClubStrength strength = ClubStrength.of(rows(), MIN_PLAYERS, MIN_CLUBS);
        assertThat(strength.leagueStrength("Liga A")).isEqualTo(73.0);
    }

    @Test
    void aTinySampleFallsBackToItsLeaguesMedianInsteadOfAMisleadingAverage() {
        ClubStrength strength = ClubStrength.of(rows(), MIN_PLAYERS, MIN_CLUBS);
        assertThat(strength.clubStrength("T:5")).isEqualTo(73.0);
    }

    @Test
    void aLeagueWithTooFewClubsHasNoLeagueStrengthAndUnknownClubsAreNull() {
        ClubStrength strength = ClubStrength.of(rows(), MIN_PLAYERS, MIN_CLUBS);
        assertThat(strength.leagueStrength("Liga B")).isNull();
        assertThat(strength.clubStrength("T:99")).isNull();
        assertThat(strength.clubStrength("T:6")).isEqualTo(60.0);
    }

    @Test
    void theLeagueOfAKeyIsReported() {
        assertThat(ClubStrength.of(rows(), MIN_PLAYERS, MIN_CLUBS).leagueOf("T:3")).isEqualTo("Liga A");
    }
}
