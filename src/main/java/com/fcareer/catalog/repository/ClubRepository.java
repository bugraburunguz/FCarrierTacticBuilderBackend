package com.fcareer.catalog.repository;

import com.fcareer.catalog.entity.ClubEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClubRepository extends JpaRepository<ClubEntity, Integer> {

    Optional<ClubEntity> findFirstByEaTeamIdOrderByIdAsc(Integer eaTeamId);

    Optional<ClubEntity> findByNameAndLeagueId(String name, Integer leagueId);

    List<ClubEntity> findByNameContainingIgnoreCaseOrderByName(String name);

    Page<ClubEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("SELECT c FROM ClubEntity c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')) AND (:leagueId = 0 OR c.leagueId = :leagueId) AND (:gender = -1 OR c.gender = :gender)")
    Page<ClubEntity> search(@Param("q") String query, @Param("leagueId") int leagueId, @Param("gender") int gender, Pageable pageable);
}
