package com.fcareer.billing.service;

import com.fcareer.auth.entity.AppUserEntity;
import com.fcareer.auth.repository.AppUserRepository;
import com.fcareer.billing.entity.CreditTransactionEntity;
import com.fcareer.billing.model.CreditTransactionType;
import com.fcareer.billing.model.response.CreditStatus;
import com.fcareer.billing.repository.CreditTransactionRepository;
import com.fcareer.common.exception.InsufficientCreditException;
import com.fcareer.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private static final String USER = "user";

    private final AppUserRepository appUserRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final EntitlementService entitlementService;
    private final Clock clock;

    @Transactional
    public CreditStatus status(Long userId) {
        AppUserEntity user = lockedUser(userId);
        refreshIfNeeded(user);
        return toStatus(user, entitlementService.isPremium(user));
    }

    @Transactional
    public int charge(Long userId, int cost, String reason) {
        AppUserEntity user = lockedUser(userId);
        refreshIfNeeded(user);
        if (entitlementService.isPremium(user)) {
            return 0;
        }
        if (user.getCreditBalance() < cost) {
            log.warn("Insufficient credit userId : {} balance : {} cost : {}", userId, user.getCreditBalance(), cost);
            throw new InsufficientCreditException();
        }
        user.setCreditBalance(user.getCreditBalance() - cost);
        record(userId, CreditTransactionType.CHARGE, -cost, reason);
        log.info("Credit charged userId : {} cost : {} reason : {}", userId, cost, reason);
        return cost;
    }

    @Transactional
    public void refund(Long userId, int amount, String reason) {
        if (amount <= 0) {
            return;
        }
        AppUserEntity user = lockedUser(userId);
        user.setCreditBalance(user.getCreditBalance() + amount);
        record(userId, CreditTransactionType.REFUND, amount, reason);
        log.info("Credit refunded userId : {} amount : {} reason : {}", userId, amount, reason);
    }

    public void initialize(AppUserEntity user) {
        user.setCreditBalance(user.getDailyCreditLimit());
        user.setLastCreditRefresh(LocalDate.now(clock));
    }

    private void refreshIfNeeded(AppUserEntity user) {
        LocalDate today = LocalDate.now(clock);
        if (Objects.equals(user.getLastCreditRefresh(), today)) {
            return;
        }
        int granted = Math.max(user.getDailyCreditLimit() - user.getCreditBalance(), 0);
        user.setCreditBalance(Math.max(user.getCreditBalance(), user.getDailyCreditLimit()));
        user.setLastCreditRefresh(today);
        record(user.getId(), CreditTransactionType.REFRESH, granted, "DAILY");
    }

    private AppUserEntity lockedUser(Long userId) {
        return appUserRepository.findByIdForUpdate(userId).orElseThrow(() -> new NotFoundException(USER, userId));
    }

    private CreditStatus toStatus(AppUserEntity user, boolean premium) {
        return new CreditStatus(user.getCreditBalance(), user.getDailyCreditLimit(), premium);
    }

    private void record(Long userId, CreditTransactionType type, int amount, String reason) {
        CreditTransactionEntity tx = new CreditTransactionEntity();
        tx.setUserId(userId);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setReason(reason);
        creditTransactionRepository.save(tx);
    }
}
