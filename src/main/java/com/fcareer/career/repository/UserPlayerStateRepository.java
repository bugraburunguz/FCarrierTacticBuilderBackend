package com.fcareer.career.repository;

import com.fcareer.career.entity.UserPlayerStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserPlayerStateRepository extends JpaRepository<UserPlayerStateEntity, Long> {

    interface CareerSquadSize {
        Long getCareerId();

        long getSize();
    }

    List<UserPlayerStateEntity> findByCareerIdOrderByIdAsc(Long careerId);

    long countByCareerId(Long careerId);

    @Query("SELECT s.careerId AS careerId, COUNT(s) AS size FROM UserPlayerStateEntity s WHERE s.careerId IN :careerIds GROUP BY s.careerId")
    List<CareerSquadSize> countByCareerIds(@Param("careerIds") Collection<Long> careerIds);

    Optional<UserPlayerStateEntity> findByCareerIdAndBasePlayerId(Long careerId, Long basePlayerId);

    void deleteByCareerId(Long careerId);
}
