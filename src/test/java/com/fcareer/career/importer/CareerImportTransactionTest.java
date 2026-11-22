package com.fcareer.career.importer;

import com.fcareer.billing.service.EntitlementService;
import com.fcareer.career.entity.CareerEntity;
import com.fcareer.career.service.CareerService;
import com.fcareer.common.exception.ImportParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareerImportTransactionTest {

    @Mock
    private CareerService careerService;
    @Mock
    private EntitlementService entitlementService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private MultipartFile logFile;
    @InjectMocks
    private CareerImportService importService;

    @Test
    void writesRunInOneTransactionThatRollsBackOnFailure() {
        when(careerService.requireOwned(1L, 2L)).thenReturn(new CareerEntity()).thenThrow(new IllegalStateException("write failed"));
        when(transactionManager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());

        assertThatThrownBy(() -> importService.importData(1L, 2L, null, Map.of())).isInstanceOf(IllegalStateException.class);

        verify(transactionManager).getTransaction(any());
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void anUnreadableLogFailsBeforeAnyTransactionOpens() throws IOException, NoSuchMethodException {
        when(careerService.requireOwned(1L, 2L)).thenReturn(new CareerEntity());
        when(logFile.isEmpty()).thenReturn(false);
        when(logFile.getInputStream()).thenThrow(new IOException("broken"));

        assertThatThrownBy(() -> importService.importData(1L, 2L, logFile, Map.of())).isInstanceOf(ImportParseException.class);

        verify(transactionManager, never()).getTransaction(any());
        assertThat(CareerImportService.class.getMethod("importData", Long.class, Long.class, MultipartFile.class, Map.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .as("ayrıştırma proxy transaction'ı içinde koşmamalı").isFalse();
    }
}
