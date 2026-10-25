package com.fcareer.career;

import com.fcareer.career.academy.AcademyDetector;
import com.fcareer.career.academy.AcademyDetector.Detection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ACADEMY-a madde 1: altyapı takımı veriden bulunur (kodda takım/lig kimliği yok). Önce career_youthplayers oyuncularının takım çoğunluğu; oyuncu yoksa
 * adı çözülmemiş takımlardan, kullanıcının kadrosundaki oyuncuların önceki takımı (altyapıdan terfi) olarak en çok geçen ve tek olanı; değilse bulunamadı.
 */
class AcademyDetectorTest {

    private static final int USER = 10;
    private static final int ACADEMY = 77;
    private static final int OTHER_PLACEHOLDER = 78;

    private static Map<String, String> row(Object... kv) {
        Map<String, String> m = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), String.valueOf(kv[i + 1]));
        }
        return m;
    }

    private static final List<Map<String, String>> TEAMS = List.of(
            row("teamid", USER, "teamname", "Port Vale"), row("teamid", ACADEMY, "teamname", "*TeamName_Abbr15_77"),
            row("teamid", OTHER_PLACEHOLDER, "teamname", "*TeamName_Abbr15_78"), row("teamid", 5, "teamname", "Chelsea"));

    @Test
    void userTeamIsNeverTheAcademyEvenIfPromotedYouthStillListed() {
        // career_youthplayers terfi etmiş oyuncuları hâlâ listeliyorsa çoğunluk A takımda olabilir; A takım altyapı sayılmaz.
        Map<String, List<Map<String, String>>> tables = Map.of(
                "teams", TEAMS,
                "career_youthplayers", List.of(row("playerid", 1), row("playerid", 2), row("playerid", 3)),
                "teamplayerlinks", List.of(row("playerid", 1, "teamid", USER), row("playerid", 2, "teamid", USER), row("playerid", 3, "teamid", ACADEMY)));
        Detection d = AcademyDetector.detect(tables, USER);
        assertThat(d.teamId()).isEqualTo(ACADEMY);
        assertThat(d.signal()).isEqualTo("YOUTH_PLAYERS");
    }

    @Test
    void youthPlayersTeamMajorityWins() {
        Map<String, List<Map<String, String>>> tables = Map.of(
                "teams", TEAMS,
                "career_youthplayers", List.of(row("playerid", 1), row("playerid", 2), row("playerid", 3)),
                "teamplayerlinks", List.of(row("playerid", 1, "teamid", ACADEMY), row("playerid", 2, "teamid", ACADEMY), row("playerid", 3, "teamid", 5)));
        Detection d = AcademyDetector.detect(tables, USER);
        assertThat(d.teamId()).isEqualTo(ACADEMY);
        assertThat(d.signal()).isEqualTo("YOUTH_PLAYERS");
    }

    @Test
    void withoutYouthPlayersPromotedPlayersPreviousTeamAmongUnresolvedNames() {
        Map<String, List<Map<String, String>>> tables = Map.of(
                "teams", TEAMS,
                "teamplayerlinks", List.of(row("playerid", 11, "teamid", USER), row("playerid", 12, "teamid", USER), row("playerid", 13, "teamid", USER)),
                "previousteam", List.of(row("playerid", 11, "previousteamid", ACADEMY), row("playerid", 12, "previousteamid", ACADEMY),
                        row("playerid", 13, "previousteamid", 5), row("playerid", 99, "previousteamid", OTHER_PLACEHOLDER)));
        Detection d = AcademyDetector.detect(tables, USER);
        assertThat(d.teamId()).isEqualTo(ACADEMY);
        assertThat(d.signal()).isEqualTo("PREVIOUS_TEAM");
    }

    @Test
    void notCertainMeansNotFound() {
        // Tek terfi (≥ 2 gerekir) ve adı çözülmüş takım sayılmaz.
        Map<String, List<Map<String, String>>> tables = Map.of(
                "teams", TEAMS,
                "teamplayerlinks", List.of(row("playerid", 11, "teamid", USER), row("playerid", 12, "teamid", USER)),
                "previousteam", List.of(row("playerid", 11, "previousteamid", ACADEMY), row("playerid", 12, "previousteamid", 5)));
        assertThat(AcademyDetector.detect(tables, USER).teamId()).isNull();
        assertThat(AcademyDetector.detect(Map.of("teams", TEAMS), USER).signal()).isEqualTo("NOT_FOUND");
    }

    @Test
    void academyNameIsTheClubsNameWithYouthAcademyAndOtherUnresolvedNamesGetAGenericFallback() {
        assertThat(AcademyDetector.displayName("*TeamName_Abbr15_77", ACADEMY, ACADEMY, "Port Vale")).isEqualTo("Port Vale Youth Academy");
        assertThat(AcademyDetector.displayName("*TeamName_Abbr15_78", OTHER_PLACEHOLDER, ACADEMY, "Port Vale")).as("katalogla aynı genel kural").isEqualTo("Takım #78");
        assertThat(AcademyDetector.displayName("*TeamName_Abbr15_77", ACADEMY, ACADEMY, null)).as("kulüp adı yoksa").isEqualTo("Altyapı");
        assertThat(AcademyDetector.displayName("Chelsea", 5, ACADEMY, "Port Vale")).isEqualTo("Chelsea");
    }
}
