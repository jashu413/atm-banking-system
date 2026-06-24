package com.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request body for a PIN change. Both current and new PINs are required. */
public record ChangePinRequest(
        @NotBlank @Pattern(regexp = "\\d{4}", message = "must be exactly 4 digits") String currentPin,
        @NotBlank @Pattern(regexp = "\\d{4}", message = "must be exactly 4 digits") String newPin
) {
}
