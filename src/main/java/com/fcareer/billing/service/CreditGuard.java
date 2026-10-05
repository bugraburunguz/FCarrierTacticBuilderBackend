package com.fcareer.billing.service;

import com.fcareer.billing.model.response.CreditStatus;
import com.fcareer.billing.model.response.CreditedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditGuard {

    private final CreditService creditService;

    public <T> CreditedResponse<T> runCharged(Long userId, int cost, String reason, Supplier<T> operation) {
        int charged = creditService.charge(userId, cost, reason);
        T result;
        try {
            result = operation.get();
        } catch (RuntimeException e) {
            log.warn("Charged operation failed, refunding userId : {} reason : {}", userId, reason);
            creditService.refund(userId, charged, reason + "_FAILED");
            throw e;
        }
        CreditStatus status = creditService.status(userId);
        return CreditedResponse.<T>builder()
                .result(result)
                .charged(charged)
                .balance(status.premium() ? null : status.balance())
                .build();
    }
}
