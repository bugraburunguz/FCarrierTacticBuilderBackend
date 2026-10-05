package com.fcareer.billing.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fcareer.billing.model.SubscriptionStatus;
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
public class SubscriptionResponse {
    private String plan;
    private SubscriptionStatus status;
    private Instant startedAt;
    private Instant expiresAt;
}
