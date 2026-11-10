package com.fcareer.billing.service;

import com.fcareer.billing.model.response.CreditStatus;
import com.fcareer.billing.model.response.CreditedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Predicate;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditGuard {

    /** Boş sonuç iadesinin kayıt nedeni eki (hata iadesi "_FAILED"). */
    static final String REFUND_EMPTY_SUFFIX = "_EMPTY";

    private final CreditService creditService;

    public <T> CreditedResponse<T> runCharged(Long userId, int cost, String reason, Supplier<T> operation) {
        return runCharged(userId, cost, reason, operation, (result) -> false);
    }

    /**
     * FCA-7: `refundWhen` sonuç için doğruysa (ör. öneride hiç aday yok) iş başarılı olsa da kredi iade edilir; yanıtta charged 0.
     * Hiç kredi düşmediyse (PREMIUM, beta) iade denenmez. İade başarısız olursa sonuç yine döner, ücret kesilmiş sayılır ve loglanır.
     */
    public <T> CreditedResponse<T> runCharged(Long userId, int cost, String reason, Supplier<T> operation, Predicate<T> refundWhen) {
        int charged = creditService.charge(userId, cost, reason);
        T result;
        try {
            result = operation.get();
        } catch (RuntimeException e) {
            log.warn("Charged operation failed, refunding userId : {} reason : {}", userId, reason);
            // REVIEW: iade de başarısız olursa asıl hata kaybolmaz; iade hatası loglanır ve asıl hataya eklenir.
            try {
                creditService.refund(userId, charged, reason + "_FAILED");
            } catch (RuntimeException refundError) {
                log.error("Credit refund failed userId : {} amount : {} reason : {}", userId, charged, reason, refundError);
                e.addSuppressed(refundError);
            }
            throw e;
        }
        if (charged > 0 && refundable(refundWhen, result, userId, reason)) {
            try {
                creditService.refund(userId, charged, reason + REFUND_EMPTY_SUFFIX);
                charged = 0;
            } catch (RuntimeException refundError) {
                log.error("Empty-result refund failed userId : {} amount : {} reason : {}", userId, charged, reason, refundError);
            }
        }
        CreditStatus status = creditService.status(userId);
        return CreditedResponse.<T>builder()
                .result(result)
                .charged(charged)
                .balance(status.premium() ? null : status.balance())
                .build();
    }

    /** İade koşulu hata verirse iade yok (kredi kesilmiş kalır, kullanıcı sonucu alır; 500 yok). */
    private static <T> boolean refundable(Predicate<T> refundWhen, T result, Long userId, String reason) {
        try {
            return refundWhen.test(result);
        } catch (RuntimeException e) {
            log.warn("Refund condition failed, no refund userId : {} reason : {}", userId, reason, e);
            return false;
        }
    }
}
