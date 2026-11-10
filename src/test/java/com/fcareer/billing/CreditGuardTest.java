package com.fcareer.billing;

import com.fcareer.billing.model.response.CreditStatus;
import com.fcareer.billing.model.response.CreditedResponse;
import com.fcareer.billing.service.CreditGuard;
import com.fcareer.billing.service.CreditService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    /** FCA-7: iş başarılı ama sonuç boş (öneride aday yok) → kredi iade edilir, yanıtta charged 0. Dolu sonuçta iade yok. */
    @Test
    void anEmptyResultIsRefundedAndReportedAsNotCharged() {
        CreditService credits = mock(CreditService.class);
        when(credits.charge(anyLong(), anyInt(), anyString())).thenReturn(3);
        when(credits.status(anyLong())).thenReturn(new CreditStatus(20, 30, false));
        CreditGuard guard = new CreditGuard(credits);

        CreditedResponse<List<String>> empty = guard.runCharged(1L, 3, "RECOMMEND", List::<String>of, List::isEmpty);
        assertThat(empty.getCharged()).isZero();
        verify(credits).refund(1L, 3, "RECOMMEND_EMPTY");

        CreditedResponse<List<String>> full = guard.runCharged(1L, 3, "RECOMMEND", () -> List.of("aday"), List::isEmpty);
        assertThat(full.getCharged()).isEqualTo(3);
        // yalnız boş sonuçtaki tek iade
        verify(credits, times(1)).refund(anyLong(), anyInt(), anyString());
    }

    /** FCA-7 inceleme: iade başarısızsa sonuç yine döner, ücret kesilmiş sayılır (charged = maliyet). */
    @Test
    void aFailedEmptyRefundStillReturnsTheResultAsCharged() {
        CreditService credits = mock(CreditService.class);
        when(credits.charge(anyLong(), anyInt(), anyString())).thenReturn(3);
        when(credits.status(anyLong())).thenReturn(new CreditStatus(17, 30, false));
        doThrow(new IllegalStateException("db blip")).when(credits).refund(anyLong(), anyInt(), anyString());
        CreditGuard guard = new CreditGuard(credits);

        CreditedResponse<List<String>> r = guard.runCharged(1L, 3, "RECOMMEND", List::<String>of, List::isEmpty);
        assertThat(r.getResult()).isEmpty();
        assertThat(r.getCharged()).isEqualTo(3);
    }

    /** FCA-7 inceleme: iade koşulu hata verirse iade yapılmaz, sonuç döner (kredi kesilmiş, 500 yok). */
    @Test
    void aThrowingRefundConditionMeansNoRefund() {
        CreditService credits = mock(CreditService.class);
        when(credits.charge(anyLong(), anyInt(), anyString())).thenReturn(3);
        when(credits.status(anyLong())).thenReturn(new CreditStatus(17, 30, false));
        CreditGuard guard = new CreditGuard(credits);

        CreditedResponse<List<String>> r = guard.runCharged(1L, 3, "RECOMMEND", () -> List.of("aday"), (x) -> {
            throw new IllegalStateException("koşul");
        });
        assertThat(r.getCharged()).isEqualTo(3);
        verify(credits, never()).refund(anyLong(), anyInt(), anyString());
    }

    /** Sıfır ücretli (PREMIUM ya da beta) çağrıda boş sonuç iade denemez. */
    @Test
    void anEmptyResultWithNothingChargedRefundsNothing() {
        CreditService credits = mock(CreditService.class);
        when(credits.charge(anyLong(), anyInt(), anyString())).thenReturn(0);
        when(credits.status(anyLong())).thenReturn(new CreditStatus(0, 0, true));
        CreditGuard guard = new CreditGuard(credits);

        assertThat(guard.runCharged(1L, 3, "RECOMMEND", List::<String>of, List::isEmpty).getCharged()).isZero();
        verify(credits, never()).refund(anyLong(), anyInt(), anyString());
    }
}
