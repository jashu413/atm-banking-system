package com.atm;

import com.atm.exception.AuthenticationException;
import com.atm.model.Customer;
import com.atm.service.ATMService;
import com.atm.service.AuthenticationService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationServiceTest {
    @Test
    void validCredentialsAllowLogin() {
        Map<String, Customer> customers = ATMService.createDemoCustomers();
        AuthenticationService auth = new AuthenticationService(customers);

        assertDoesNotThrow(() -> auth.login("1001001001", "1234"));
    }

    @Test
    void accountLocksAfterThreeBadPins() {
        Map<String, Customer> customers = ATMService.createDemoCustomers();
        AuthenticationService auth = new AuthenticationService(customers);

        assertThrows(AuthenticationException.class, () -> auth.login("1001001001", "0000"));
        assertThrows(AuthenticationException.class, () -> auth.login("1001001001", "0000"));
        assertThrows(AuthenticationException.class, () -> auth.login("1001001001", "0000"));

        assertTrue(customers.get("1001001001").getAccount().isLocked());
    }

    @Test
    void adminCredentialsAreRecognized() {
        AuthenticationService auth = new AuthenticationService(ATMService.createDemoCustomers());

        assertTrue(auth.isAdminLogin("admin", "0000"));
    }
}
