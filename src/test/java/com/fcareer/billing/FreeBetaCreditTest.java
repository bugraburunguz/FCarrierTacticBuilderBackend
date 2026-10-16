package com.fcareer.billing;

import com.fcareer.auth.entity.AppUserEntity;
import com.fcareer.auth.model.SubscriptionType;
import com.fcareer.auth.repository.AppUserRepository;
import com.fcareer.billing.configuration.BillingProperties;
import com.fcareer.billing.model.response.CreditStatus;
import com.fcareer.billing.repository.CreditTransactionRepository;
import com.fcareer.billing.repository.SubscriptionRepository;
import com.fcareer.billing.service.CreditService;
import com.fcareer.billing.service.EntitlementService;
import com.fcareer.ut.kb.ContributorService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PROD-PREP 2: ücretsiz beta (FCAREER_FREE_BETA) ve günlük kredi tamamlama. Bayrak açıkken herkes Premium hakkı alır; günlük limit tek yerde
 * (FCAREER_DAILY_CREDITS, varsayılan 30); Europe/Istanbul gün dönümünde bakiye limitin altındaysa limite tamamlanır, üstündeyse dokunulmaz, birikmez.
 */
class FreeBetaCreditTest {

    /** 2026-10-16T21:30Z = İstanbul'da 17 Ekim 00:30 (UTC tarihi hâlâ 16). */
    private static final Instant AFTER_ISTANBUL_MIDNIGHT = Instant.parse("2026-10-16T21:30:00Z");

    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final ContributorService contributors = mock(ContributorService.class);

    private EntitlementService entitlement(boolean freeBeta, Clock clock) {
        BillingProperties properties = new BillingProperties();
        properties.setFreeBeta(freeBeta);
        return new EntitlementService(subscriptions, users, clock, contributors, properties);
    }

    private CreditService credits(boolean freeBeta, Clock clock) {
        BillingProperties properties = new BillingProperties();
        properties.setFreeBeta(freeBeta);
        return new CreditService(users, mock(CreditTransactionRepository.class), entitlement(freeBeta, clock), clock, properties);
    }

    private AppUserEntity user(int balance, LocalDate lastRefresh) {
        AppUserEntity user = new AppUserEntity();
        user.setId(7L);
        user.setSubscriptionType(SubscriptionType.FREE);
        user.setCreditBalance(balance);
        user.setDailyCreditLimit(20);
        user.setLastCreditRefresh(lastRefresh);
        user.setEmailVerified(true);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void freeBetaGivesEveryonePremiumAndIsOffByDefault() {
        Clock clock = Clock.fixed(AFTER_ISTANBUL_MIDNIGHT, ZoneOffset.UTC);
        AppUserEntity free = user(0, null);
        assertThat(new BillingProperties().isFreeBeta()).isFalse();
        assertThat(entitlement(true, clock).isPremium(free)).isTrue();
        verifyNoInteractions(subscriptions);
        when(subscriptions.findFirstByUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(any(), any(), any())).thenReturn(Optional.empty());
        assertThat(entitlement(false, clock).isPremium(free)).isFalse();
    }

    @Test
    void dailyLimitDefaultsTo30() {
        assertThat(new BillingProperties().getDailyCredits()).isEqualTo(30);
    }

    @Test
    void istanbulDayBoundaryTopsUpToTheConfiguredLimit() {
        Clock clock = Clock.fixed(AFTER_ISTANBUL_MIDNIGHT, ZoneOffset.UTC);
        AppUserEntity user = user(12, LocalDate.of(2026, 10, 16));
        CreditStatus status = credits(true, clock).status(7L);
        assertThat(status.balance()).isEqualTo(30);
        assertThat(status.dailyLimit()).isEqualTo(30);
        assertThat(user.getLastCreditRefresh()).isEqualTo(LocalDate.of(2026, 10, 17));
    }

    @Test
    void sameIstanbulDayDoesNotRefreshAgainAndNothingAccumulates() {
        Clock clock = Clock.fixed(AFTER_ISTANBUL_MIDNIGHT, ZoneOffset.UTC);
        AppUserEntity user = user(12, LocalDate.of(2026, 10, 17));
        CreditService service = credits(true, clock);
        assertThat(service.status(7L).balance()).isEqualTo(12);
        user.setLastCreditRefresh(LocalDate.of(2026, 10, 16));
        service.status(7L);
        service.status(7L);
        assertThat(user.getCreditBalance()).isEqualTo(30);
    }

    @Test
    void balanceAboveTheLimitIsLeftUntouched() {
        Clock clock = Clock.fixed(AFTER_ISTANBUL_MIDNIGHT, ZoneOffset.UTC);
        AppUserEntity user = user(45, LocalDate.of(2026, 10, 16));
        assertThat(credits(true, clock).status(7L).balance()).isEqualTo(45);
        assertThat(user.getLastCreditRefresh()).isEqualTo(LocalDate.of(2026, 10, 17));
    }
}
