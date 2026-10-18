package com.fcareer.career.service;

import com.fcareer.career.entity.CareerEntity;
import com.fcareer.career.repository.CareerMovesRepository;
import com.fcareer.career.repository.CareerMovesRepository.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Kadro hareketleri (ana kayda göre): son içe aktarılan kariyer kaydı ile oyunun ana kaydı (temel katalog) karşılaştırılır. Bedel bilinmez.
 * <ul>
 *   <li>TRANSFER_IN: ana kayıtta başka kulüpte, kariyerde bizde, kiralık kaydı yok.</li>
 *   <li>LOAN_IN: kariyerde bizde ve kiralık kaydı var (sinceStart: ana kayıtta da bizdeydi, sezon başından kiralık).</li>
 *   <li>NEW_PLAYER: oyunun kariyerde ürettiği oyuncu (altyapı, yeni oyuncu); ana kayıtta yok.</li>
 *   <li>TRANSFER_OUT: ana kayıtta bizde, kariyerde başka takımda ya da serbest.</li>
 *   <li>LOAN_OUT: kiralık kaynağı biz, şu an başka takımda.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CareerMovesService {

    public static final String FREE_AGENT = "Serbest";

    private final CareerService careerService;
    private final CareerMovesRepository repository;

    public record Move(String type, long playerId, Long eaId, String name, Integer overall, String position,
                       String fromClub, String toClub, boolean sinceStart, LocalDate loanUntil) {
    }

    @Transactional(readOnly = true)
    public List<Move> moves(Long userId, Long careerId) {
        CareerEntity career = careerService.requireOwned(userId, careerId);
        Integer us = career.getClubId();
        List<Move> moves = new ArrayList<>();
        for (Row row : repository.rows(careerId)) {
            boolean withUs = Objects.equals(row.careerClubId(), us) && !row.freeAgent();
            boolean wasWithUs = Objects.equals(row.baseClubId(), us);
            boolean loaned = Objects.nonNull(row.loanFromTeamId());
            String now = row.freeAgent() || Objects.isNull(row.careerClub()) ? FREE_AGENT : row.careerClub();
            if (withUs && loaned && !row.loanFromUs()) {
                moves.add(move("LOAN_IN", row, row.loanFromClub(), row.careerClub(), wasWithUs));
            } else if (withUs && row.generated()) {
                moves.add(move("NEW_PLAYER", row, null, row.careerClub(), false));
            } else if (withUs && !wasWithUs) {
                moves.add(move("TRANSFER_IN", row, row.baseClub(), row.careerClub(), false));
            } else if (!withUs && loaned && row.loanFromUs()) {
                moves.add(move("LOAN_OUT", row, row.loanFromClub(), now, false));
            } else if (!withUs && wasWithUs) {
                moves.add(move("TRANSFER_OUT", row, row.baseClub(), now, false));
            }
        }
        return moves;
    }

    private static Move move(String type, Row row, String from, String to, boolean sinceStart) {
        return new Move(type, row.playerId(), row.eaId(), row.name(), row.overall(), row.position(), from, to, sinceStart, row.loanUntil());
    }
}
