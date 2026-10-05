package com.fcareer.catalog.repository;

import com.fcareer.catalog.entity.LeagueEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeagueRepository extends JpaRepository<LeagueEntity, Integer> {

    Optional<LeagueEntity> findByName(String name);
}
