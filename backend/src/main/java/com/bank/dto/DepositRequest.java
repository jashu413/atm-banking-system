package com.bank.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Request body for a deposit. */
public record DepositRequest(
        @NotNull Long accountId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount
) {
}
