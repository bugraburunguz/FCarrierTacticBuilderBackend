package com.fcareer.catalog.repository;

import com.fcareer.catalog.entity.ClubEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ClubRepository extends JpaRepository<ClubEntity, Integer> {

    Optional<ClubEntity> findByNameAndLeagueId(String name, Integer leagueId);

    List<ClubEntity> findByNameContainingIgnoreCaseOrderByName(String name);

    Page<ClubEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
