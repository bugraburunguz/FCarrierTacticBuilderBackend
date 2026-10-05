package com.fcareer.catalog.repository;

import com.fcareer.catalog.entity.PlayerValuationEntity;
import com.fcareer.catalog.model.enums.ValuationSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlayerValuationRepository extends JpaRepository<PlayerValuationEntity, Long> {

    Optional<PlayerValuationEntity> findByBasePlayerIdAndGameVersionIdAndSourceAndOwnerUserIdIsNull(
            Long basePlayerId, Short gameVersionId, ValuationSource source);

    Optional<PlayerValuationEntity> findByBasePlayerIdAndGameVersionIdAndSourceAndOwnerUserId(
            Long basePlayerId, Short gameVersionId, ValuationSource source, Long ownerUserId);

    List<PlayerValuationEntity> findByBasePlayerIdInAndGameVersionIdAndSourceAndOwnerUserIdIsNull(
            Collection<Long> basePlayerIds, Short gameVersionId, ValuationSource source);

    List<PlayerValuationEntity> findByBasePlayerIdInAndGameVersionIdAndSourceAndOwnerUserId(
            Collection<Long> basePlayerIds, Short gameVersionId, ValuationSource source, Long ownerUserId);
}
