package com.bank.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Request body for a cash withdrawal. Requires the transaction PIN for authorisation. */
public record WithdrawRequest(
        @NotBlank @Pattern(regexp = "\\d{4}", message = "must be exactly 4 digits") String pin,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount
) {
}
