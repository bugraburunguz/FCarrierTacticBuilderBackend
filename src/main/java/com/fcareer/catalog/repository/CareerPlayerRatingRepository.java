package com.fcareer.catalog.repository;

import com.fcareer.catalog.entity.CareerPlayerRatingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CareerPlayerRatingRepository extends JpaRepository<CareerPlayerRatingEntity, Long> {

    List<CareerPlayerRatingEntity> findByCareerIdAndBasePlayerIdInAndGameVersionId(Long careerId, Collection<Long> basePlayerIds, Short gameVersionId);
}
