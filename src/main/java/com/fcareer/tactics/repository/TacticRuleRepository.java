package com.fcareer.tactics.repository;

import com.fcareer.tactics.entity.TacticRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TacticRuleRepository extends JpaRepository<TacticRuleEntity, String> {

    List<TacticRuleEntity> findByEnabledTrue();
}
