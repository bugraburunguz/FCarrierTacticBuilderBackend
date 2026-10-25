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

    /**
     * ACADEMY-b kararı: katalog oyuncusu olmayan kulüp (Klasik Lig, futsal, 5'e 5, kariyerde üretilmiş takım …) aramada görünmez; satır silinmez.
     * Kariyerin ürettiği oyuncular (owner_career_id) sayılmaz. Sıra adla.
     */
    @Query(value = """
            SELECT c.* FROM club c
             WHERE lower(c.name) LIKE lower('%' || :q || '%') AND (:leagueId = 0 OR c.league_id = :leagueId) AND (:gender = -1 OR c.gender = :gender)
               AND EXISTS (SELECT 1 FROM player_rating r JOIN base_player b ON b.id = r.base_player_id WHERE r.club_id = c.id AND b.owner_career_id IS NULL)
             ORDER BY c.name""", nativeQuery = true)
    List<ClubEntity> searchWithPlayers(@Param("q") String query, @Param("leagueId") int leagueId, @Param("gender") int gender, Pageable pageable);
}
