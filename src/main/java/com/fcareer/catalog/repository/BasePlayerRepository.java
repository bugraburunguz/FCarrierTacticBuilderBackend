package com.fcareer.catalog.repository;

import com.fcareer.catalog.entity.BasePlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BasePlayerRepository extends JpaRepository<BasePlayerEntity, Long> {

    Optional<BasePlayerEntity> findByEaResourceId(Long eaResourceId);

    List<BasePlayerEntity> findByEaResourceIdIn(Collection<Long> eaResourceIds);

    /** Verilen kariyere ait olmayan, kariyer-üretimi (owner_career_id dolu) oyuncular; başka kullanıcının verisi sızmasın diye elenir. */
    @Query(value = "SELECT id FROM base_player WHERE id IN (:ids) AND owner_career_id IS NOT NULL "
            + "AND (CAST(:careerId AS BIGINT) IS NULL OR owner_career_id <> CAST(:careerId AS BIGINT))", nativeQuery = true)
    List<Long> findForeignOwnedIds(@Param("ids") Collection<Long> ids, @Param("careerId") Long careerId);

    /** V3-05c-c: EA assetId → katalog adı (yalnız katalog oyuncuları; kariyer üretimi oyuncu dönmez). */
    interface AssetName {
        Long getAssetId();

        String getName();
    }

    @Query(value = "SELECT ea_resource_id AS assetId, full_name AS name FROM base_player WHERE ea_resource_id IN (:ids) AND owner_career_id IS NULL ORDER BY ea_resource_id",
            nativeQuery = true)
    List<AssetName> findCatalogNamesByAssetIds(@Param("ids") Collection<Long> ids);
}
