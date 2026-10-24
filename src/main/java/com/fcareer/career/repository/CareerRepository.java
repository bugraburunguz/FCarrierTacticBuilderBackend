package com.fcareer.career.repository;

import com.fcareer.career.entity.CareerEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CareerRepository extends JpaRepository<CareerEntity, Long> {

    List<CareerEntity> findByUserIdOrderByIdDesc(Long userId);

    long countByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CareerEntity c WHERE c.id = :id")
    Optional<CareerEntity> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query(value = "DELETE FROM user_save_snapshot WHERE career_id = :id", nativeQuery = true)
    void deleteSnapshots(@Param("id") Long id);
}
