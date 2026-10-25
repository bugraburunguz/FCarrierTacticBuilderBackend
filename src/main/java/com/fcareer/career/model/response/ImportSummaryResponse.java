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
    /** Kullanıcıya gösterilecek uyarı kodları: CAREER_PLAYERS_SKIPPED_NO_POSITIONS, NOT_IN_CAREER, CONTRACTS_MISSING. */
    private java.util.List<String> warnings;
    /** FAZ 6a-A: export'taki oyun tarihi (yoksa alan yok); Masa'nın sözleşme ve takvim hesapları buna bağlı. */
    private java.time.LocalDate gameDate;
    /** FAZ 6a-A: maaş, sözleşme ya da kiralık bitişi yazılan oyuncu sayısı; 0 ise export bu verileri taşımıyor. */
    private int contracts;
}
