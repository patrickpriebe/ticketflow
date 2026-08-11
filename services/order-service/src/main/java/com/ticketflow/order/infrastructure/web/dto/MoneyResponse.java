package com.ticketflow.order.infrastructure.web.dto;

import com.ticketflow.order.domain.model.Money;

import java.math.BigDecimal;

public record MoneyResponse(BigDecimal amount, String currency) {

    public static MoneyResponse from(Money money) {
        return money == null ? null : new MoneyResponse(money.amount(), money.currency());
    }
}
