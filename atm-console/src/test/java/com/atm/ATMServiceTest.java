package com.atm;

import com.atm.exception.ATMException;
import com.atm.model.Customer;
import com.atm.service.ATMService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ATMServiceTest {
    @Test
    void transferMovesMoneyBetweenAccounts() {
        Map<String, Customer> customers = ATMService.createDemoCustomers();
        ATMService service = new ATMService(customers);
        Customer source = customers.get("1001001001");

        service.transfer(source, "1001001002", new BigDecimal("500.00"));

        assertEquals(new BigDecimal("24500.00"), customers.get("1001001001").getAccount().getBalance());
        assertEquals(new BigDecimal("50500.00"), customers.get("1001001002").getAccount().getBalance());
    }

    @Test
    void transferFailsForUnknownTargetAccount() {
        Map<String, Customer> customers = ATMService.createDemoCustomers();
        ATMService service = new ATMService(customers);

        assertThrows(ATMException.class,
                () -> service.transfer(customers.get("1001001001"), "9999999999", new BigDecimal("500.00")));
    }
}
