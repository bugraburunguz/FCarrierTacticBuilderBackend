package com.fcareer.career.repository;

import com.fcareer.career.entity.RosterEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RosterEventRepository extends JpaRepository<RosterEventEntity, Long> {

    List<RosterEventEntity> findByCareerIdOrderByIdDesc(Long careerId);
}
