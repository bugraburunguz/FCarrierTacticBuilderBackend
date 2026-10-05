package com.fcareer.tactics.repository;

import com.fcareer.tactics.entity.PresetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PresetRepository extends JpaRepository<PresetEntity, String> {

    List<PresetEntity> findAllByOrderByKindDescIdAsc();

    List<PresetEntity> findByKindOrderByIdAsc(String kind);
}
