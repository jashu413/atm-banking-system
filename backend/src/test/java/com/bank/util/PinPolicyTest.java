package com.bank.util;

import com.bank.exception.InvalidPinException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests the four-digit PIN format rule ported from the console application. */
class PinPolicyTest {

    @Test
    void acceptsExactlyFourDigits() {
        assertThatCode(() -> PinPolicy.validateFormat("1234")).doesNotThrowAnyException();
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> PinPolicy.validateFormat(null)).isInstanceOf(InvalidPinException.class);
    }

    @Test
    void rejectsWrongLength() {
        assertThatThrownBy(() -> PinPolicy.validateFormat("123")).isInstanceOf(InvalidPinException.class);
        assertThatThrownBy(() -> PinPolicy.validateFormat("12345")).isInstanceOf(InvalidPinException.class);
    }

    @Test
    void rejectsNonDigits() {
        assertThatThrownBy(() -> PinPolicy.validateFormat("12a4")).isInstanceOf(InvalidPinException.class);
        assertThatThrownBy(() -> PinPolicy.validateFormat("    ")).isInstanceOf(InvalidPinException.class);
    }
}
