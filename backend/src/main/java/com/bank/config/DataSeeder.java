package com.bank.config;

import com.bank.domain.BankAccount;
import com.bank.domain.CurrentAccount;
import com.bank.domain.Customer;
import com.bank.domain.Role;
import com.bank.domain.SavingsAccount;
import com.bank.domain.UserAccount;
import com.bank.repository.AccountRepository;
import com.bank.repository.CustomerRepository;
import com.bank.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Seeds demo customers (each with a login user) plus a standalone admin on startup, replacing
 * {@code ATMService.createDemoCustomers()}.
 *
 * <p>All credentials are BCrypt-hashed via the {@link PasswordEncoder} — both login passwords and
 * transaction PINs. Guarded by {@code app.seed-demo-data=true} (absent in the test profile, so
 * tests start empty). No-ops if users already exist.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final UserAccountRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(CustomerRepository customerRepository,
                      UserAccountRepository userRepository,
                      AccountRepository accountRepository,
                      PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0 || accountRepository.count() > 0) {
            return;
        }

        // Customers with both a login user (username/password) and a transaction PIN.
        customerRepository.save(buildCustomer("C001", "Asha Sharma", "asha", "Password@123", "1234",
                new SavingsAccount("1001001001", encodePin("1234"),
                        new BigDecimal("25000.00"), new BigDecimal("10000.00"))));
        customerRepository.save(buildCustomer("C002", "Ravi Kumar", "ravi", "Password@123", "2345",
                new CurrentAccount("1001001002", encodePin("2345"),
                        new BigDecimal("50000.00"), new BigDecimal("25000.00"))));
        customerRepository.save(buildCustomer("C003", "Neha Patel", "neha", "Password@123", "3456",
                new SavingsAccount("1001001003", encodePin("3456"),
                        new BigDecimal("15000.00"), new BigDecimal("10000.00"))));

        // A standalone administrator (no bank account).
        userRepository.save(new UserAccount("admin",
                passwordEncoder.encode("Admin@123"), Role.ADMIN));
    }

    private Customer buildCustomer(String code, String name, String username, String rawPassword,
                                   String rawPin, BankAccount account) {
        Customer customer = new Customer(code, name);
        customer.assignAccount(account);
        customer.assignUser(new UserAccount(username, passwordEncoder.encode(rawPassword), Role.CUSTOMER));
        return customer;
    }

    private String encodePin(String rawPin) {
        return passwordEncoder.encode(rawPin);
    }
}
