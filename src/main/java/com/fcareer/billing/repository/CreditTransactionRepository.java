package com.fcareer.billing.repository;

import com.fcareer.billing.entity.CreditTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditTransactionRepository extends JpaRepository<CreditTransactionEntity, Long> {

    List<CreditTransactionEntity> findByUserIdOrderByIdDesc(Long userId);
}
