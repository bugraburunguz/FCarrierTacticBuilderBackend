package com.fcareer.career.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImportSummaryResponse {
    private int players;
    private int generatedPlayers;
    private int unmatchedPlayers;
    private int teams;
    private int freeAgents;
    private Integer guessedTeamId;
    private Integer squadSynced;
    private String guessedTeamName;
    /** Pozisyon kolonu olmadığı için atlanan kariyer-üretimi oyuncu sayısı (LE-02). */
    private int skippedNoPosition;
    /** Kullanıcıya gösterilecek uyarı kodları: CAREER_PLAYERS_SKIPPED_NO_POSITIONS, NOT_IN_CAREER. */
    private java.util.List<String> warnings;
}
