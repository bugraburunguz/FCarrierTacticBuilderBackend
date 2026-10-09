package com.fcareer.ut.repository;

import com.fcareer.ut.entity.SbcVoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SbcVoteRepository extends JpaRepository<SbcVoteEntity, Long> {

    Optional<SbcVoteEntity> findByUserIdAndSetId(Long userId, Long setId);

    List<SbcVoteEntity> findByUserId(Long userId);

    @Query("SELECT v.setId AS setId, SUM(CASE WHEN v.vote = 1 THEN 1 ELSE 0 END) AS up, SUM(CASE WHEN v.vote = -1 THEN 1 ELSE 0 END) AS down "
            + "FROM SbcVoteEntity v GROUP BY v.setId")
    List<VoteTotals> totals();

    interface VoteTotals {
        Long getSetId();

        Long getUp();

        Long getDown();
    }
}
