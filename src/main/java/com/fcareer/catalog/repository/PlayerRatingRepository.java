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

    Page<PlayerRatingEntity> findByGameVersionId(Short gameVersionId, Pageable pageable);
}
