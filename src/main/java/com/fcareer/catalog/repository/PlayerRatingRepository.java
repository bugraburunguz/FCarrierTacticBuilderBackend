package com.fcareer.catalog.repository;

import com.fcareer.catalog.entity.PlayerRatingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlayerRatingRepository extends JpaRepository<PlayerRatingEntity, Long> {

    Optional<PlayerRatingEntity> findByBasePlayerIdAndGameVersionId(Long basePlayerId, Short gameVersionId);

    List<PlayerRatingEntity> findByBasePlayerIdInAndGameVersionId(Collection<Long> basePlayerIds, Short gameVersionId);

    List<PlayerRatingEntity> findByClubIdAndGameVersionId(Integer clubId, Short gameVersionId);

    /** Kulüp kadrosu, başka bir kariyerin ürettiği (owner_career_id) oyuncular hariç: yeni kariyerin kadrosuna sızmamalı. */
    @org.springframework.data.jpa.repository.Query(value = "SELECT r.* FROM player_rating r JOIN base_player b ON b.id = r.base_player_id WHERE r.club_id = :clubId AND r.game_version_id = :gv AND b.owner_career_id IS NULL", nativeQuery = true)
    List<PlayerRatingEntity> findCatalogSquad(@org.springframework.data.repository.query.Param("clubId") Integer clubId, @org.springframework.data.repository.query.Param("gv") Short gameVersionId);

    Page<PlayerRatingEntity> findByGameVersionId(Short gameVersionId, Pageable pageable);
}
