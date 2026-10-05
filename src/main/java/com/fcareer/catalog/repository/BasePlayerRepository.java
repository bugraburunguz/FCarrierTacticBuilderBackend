package com.fcareer.catalog.repository;

import com.fcareer.catalog.entity.BasePlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BasePlayerRepository extends JpaRepository<BasePlayerEntity, Long> {

    Optional<BasePlayerEntity> findByEaResourceId(Long eaResourceId);

    List<BasePlayerEntity> findByEaResourceIdIn(Collection<Long> eaResourceIds);
}
