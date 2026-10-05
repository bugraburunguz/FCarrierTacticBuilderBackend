package com.fcareer.roles.repository;

import com.fcareer.roles.entity.BehaviorTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BehaviorTagRepository extends JpaRepository<BehaviorTagEntity, String> {

    List<BehaviorTagEntity> findAllByOrderBySortOrderAsc();

    List<BehaviorTagEntity> findByPositionOrderBySortOrderAsc(String position);
}
