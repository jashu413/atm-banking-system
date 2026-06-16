package com.bank.config;

import com.bank.domain.BankAccount;
import com.bank.domain.CurrentAccount;
import com.bank.domain.Customer;
import com.bank.domain.SavingsAccount;
import com.bank.repository.AccountRepository;
import com.bank.repository.CustomerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Seeds the demo customers from the console application on startup, replacing
 * {@code ATMService.createDemoCustomers()}.
 *
 * <p>Guarded by {@code app.seed-demo-data=true} (set in {@code application.yml}); the property
 * is absent in the test profile, so tests start with an empty database. No-ops if accounts
 * already exist.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;

    public DataSeeder(CustomerRepository customerRepository, AccountRepository accountRepository) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (accountRepository.count() > 0) {
            return;
        }

        customerRepository.save(buildCustomer("C001", "Asha Sharma",
                new SavingsAccount("1001001001", "1234",
                        new BigDecimal("25000.00"), new BigDecimal("10000.00"))));
        customerRepository.save(buildCustomer("C002", "Ravi Kumar",
                new CurrentAccount("1001001002", "2345",
                        new BigDecimal("50000.00"), new BigDecimal("25000.00"))));
        customerRepository.save(buildCustomer("C003", "Neha Patel",
                new SavingsAccount("1001001003", "3456",
                        new BigDecimal("15000.00"), new BigDecimal("10000.00"))));
    }

    private Customer buildCustomer(String code, String name, BankAccount account) {
        Customer customer = new Customer(code, name);
        customer.assignAccount(account);
        return customer;
    }
}
