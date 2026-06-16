package com.bank.repository;

import com.bank.domain.BankAccount;
import com.bank.domain.CurrentAccount;
import com.bank.domain.Customer;
import com.bank.domain.SavingsAccount;
import com.bank.domain.Transaction;
import com.bank.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the JPA mapping and repositories against H2. Verifies entity
 * relationships (Customer 1:1 Account, Account 1:N Transaction), cascade persistence,
 * inheritance, and the repository query methods.
 */
@DataJpaTest
class AccountPersistenceIT {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Customer persistCustomerWithAccount(String code, String name, BankAccount account) {
        Customer customer = new Customer(code, name);
        customer.assignAccount(account);
        return customerRepository.saveAndFlush(customer);
    }

    @Test
    void savesCustomerAndAccountViaCascadeAndFindsByAccountNumber() {
        persistCustomerWithAccount("C001", "Asha Sharma",
                new SavingsAccount("1001001001", "1234", new BigDecimal("25000.00"), new BigDecimal("10000.00")));
        em.clear();

        Optional<BankAccount> found = accountRepository.findByAccountNumber("1001001001");

        assertThat(found).isPresent();
        BankAccount account = found.get();
        assertThat(account.getBalance()).isEqualByComparingTo("25000.00");
        assertThat(account.getAccountType()).isEqualTo("Savings");
        assertThat(account.getCustomer().getName()).isEqualTo("Asha Sharma");
        assertThat(accountRepository.existsByAccountNumber("9999999999")).isFalse();
    }

    @Test
    void persistsTransactionsAddedThroughDomainBehaviour() {
        persistCustomerWithAccount("C001", "Asha Sharma",
                new SavingsAccount("1001001001", "1234", new BigDecimal("1000.00"), new BigDecimal("5000.00")));

        BankAccount account = accountRepository.findByAccountNumber("1001001001").orElseThrow();
        account.deposit(new BigDecimal("500.00"));
        account.withdraw(new BigDecimal("200.00"));
        accountRepository.saveAndFlush(account);
        em.clear();

        BankAccount reloaded = accountRepository.findByAccountNumber("1001001001").orElseThrow();
        assertThat(reloaded.getBalance()).isEqualByComparingTo("1300.00");
        assertThat(reloaded.getTransactions()).hasSize(2);

        List<Transaction> history =
                transactionRepository.findByAccount_AccountNumberOrderByCreatedAtDescIdDesc("1001001001");
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(history.get(0).getBalanceAfter()).isEqualByComparingTo("1300.00");
    }

    @Test
    void transferBetweenPersistedAccountsRecordsBothLegs() {
        persistCustomerWithAccount("C001", "Asha Sharma",
                new SavingsAccount("1001001001", "1234", new BigDecimal("1000.00"), new BigDecimal("5000.00")));
        persistCustomerWithAccount("C002", "Ravi Kumar",
                new CurrentAccount("1001001002", "2345", new BigDecimal("500.00"), new BigDecimal("25000.00")));

        BankAccount source = accountRepository.findByAccountNumber("1001001001").orElseThrow();
        BankAccount target = accountRepository.findByAccountNumber("1001001002").orElseThrow();
        source.debitForTransfer(new BigDecimal("300.00"), target.getAccountNumber());
        target.creditFromTransfer(new BigDecimal("300.00"), source.getAccountNumber());
        accountRepository.save(source);
        accountRepository.save(target);
        accountRepository.flush();
        em.clear();

        assertThat(accountRepository.findByAccountNumber("1001001001").orElseThrow().getBalance())
                .isEqualByComparingTo("700.00");
        assertThat(accountRepository.findByAccountNumber("1001001002").orElseThrow().getBalance())
                .isEqualByComparingTo("800.00");
    }

    @Test
    void persistsSingleTableInheritanceDiscriminator() {
        persistCustomerWithAccount("C002", "Ravi Kumar",
                new CurrentAccount("1001001002", "2345", new BigDecimal("50000.00"), new BigDecimal("25000.00")));
        em.clear();

        BankAccount account = accountRepository.findByAccountNumber("1001001002").orElseThrow();
        assertThat(account).isInstanceOf(CurrentAccount.class);
        assertThat(account.getAccountType()).isEqualTo("Current");
    }

    @Test
    void findsCustomerByBusinessCode() {
        persistCustomerWithAccount("C003", "Neha Patel",
                new SavingsAccount("1001001003", "3456", new BigDecimal("15000.00"), new BigDecimal("10000.00")));
        em.clear();

        assertThat(customerRepository.findByCustomerCode("C003")).isPresent();
        assertThat(customerRepository.findByCustomerCode("NOPE")).isEmpty();
    }
}
