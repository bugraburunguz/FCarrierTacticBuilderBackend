package com.fcareer.career.repository;

import com.fcareer.career.entity.UserPlayerStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPlayerStateRepository extends JpaRepository<UserPlayerStateEntity, Long> {

    List<UserPlayerStateEntity> findByCareerIdOrderByIdAsc(Long careerId);

    Optional<UserPlayerStateEntity> findByCareerIdAndBasePlayerId(Long careerId, Long basePlayerId);

    void deleteByCareerId(Long careerId);
}
