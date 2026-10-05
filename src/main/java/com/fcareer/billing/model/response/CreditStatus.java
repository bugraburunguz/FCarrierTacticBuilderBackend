package com.fcareer.billing.model.response;

public record CreditStatus(int balance, int dailyLimit, boolean premium) {
}
