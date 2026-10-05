package com.fcareer.catalog.repository;

import com.fcareer.catalog.entity.GameVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameVersionRepository extends JpaRepository<GameVersionEntity, Short> {

}
