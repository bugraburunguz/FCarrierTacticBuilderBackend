package com.fcareer.ut.repository;

import com.fcareer.ut.entity.SbcVoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SbcVoteRepository extends JpaRepository<SbcVoteEntity, Long> {

    Optional<SbcVoteEntity> findByUserIdAndSetId(Long userId, Long setId);

    List<SbcVoteEntity> findByUserId(Long userId);

    @Query("SELECT v.setId AS setId, SUM(CASE WHEN v.vote = 1 THEN 1 ELSE 0 END) AS up, SUM(CASE WHEN v.vote = -1 THEN 1 ELSE 0 END) AS down "
            + "FROM SbcVoteEntity v GROUP BY v.setId")
    List<VoteTotals> totals();

    /** Eşzamanlı ilk oylarda unique ihlali yerine tek satıra birleşir (BUG-08). */
    @Modifying
    @Query(value = "INSERT INTO sbc_vote (user_id, set_id, vote, updated_at) VALUES (:userId, :setId, :vote, now()) "
            + "ON CONFLICT (user_id, set_id) DO UPDATE SET vote = EXCLUDED.vote, updated_at = now()", nativeQuery = true)
    void upsert(@Param("userId") Long userId, @Param("setId") Long setId, @Param("vote") int vote);

    @Modifying
    @Query(value = "DELETE FROM sbc_vote WHERE user_id = :userId AND set_id = :setId", nativeQuery = true)
    void remove(@Param("userId") Long userId, @Param("setId") Long setId);

    @Query("SELECT v.setId AS setId, SUM(CASE WHEN v.vote = 1 THEN 1 ELSE 0 END) AS up, SUM(CASE WHEN v.vote = -1 THEN 1 ELSE 0 END) AS down "
            + "FROM SbcVoteEntity v WHERE v.setId = :setId GROUP BY v.setId")
    Optional<VoteTotals> totalsFor(@Param("setId") Long setId);

    interface VoteTotals {
        Long getSetId();

        Long getUp();

        Long getDown();
    }
}
