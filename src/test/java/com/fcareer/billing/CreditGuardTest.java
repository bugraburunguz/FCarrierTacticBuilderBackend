package com.fcareer.billing;

import com.fcareer.billing.service.CreditGuard;
import com.fcareer.billing.service.CreditService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** REVIEW (Orta): iade de başarısız olursa asıl hata maskelenmemeli (kullanıcı ve log gerçek nedeni görür). */
class CreditGuardTest {

    @Test
    void aFailingRefundDoesNotHideTheOriginalError() {
        CreditService credits = mock(CreditService.class);
        when(credits.charge(anyLong(), anyInt(), anyString())).thenReturn(5);
        doThrow(new IllegalStateException("db blip")).when(credits).refund(anyLong(), anyInt(), anyString());
        CreditGuard guard = new CreditGuard(credits);

        assertThatThrownBy(() -> guard.runCharged(1L, 5, "TEST", () -> {
            throw new UnsupportedOperationException("asıl hata");
        })).isInstanceOf(UnsupportedOperationException.class).hasMessage("asıl hata");
        verify(credits).refund(1L, 5, "TEST_FAILED");
    }
}
