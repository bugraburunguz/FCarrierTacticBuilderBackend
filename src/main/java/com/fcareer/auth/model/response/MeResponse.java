package com.fcareer.auth.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.auth.model.SubscriptionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeResponse {
    private Long id;
    private String email;
    private SubscriptionType subscriptionType;
    private Integer creditBalance;
    private int dailyCreditLimit;
    private boolean showAds;
    private String role;
    private boolean emailVerified;
    private boolean emailVerificationRequired;
    private Instant createdAt;
    private Integer privacyAckVersion;
    private Instant privacyAckAt;
}
