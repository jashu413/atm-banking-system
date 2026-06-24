package com.bank.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Request body for a deposit. The target account number is supplied as a path variable. */
public record DepositRequest(
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount
) {
}
