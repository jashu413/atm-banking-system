package com.bank.controller;

import com.bank.domain.Customer;
import com.bank.domain.Role;
import com.bank.domain.SavingsAccount;
import com.bank.domain.UserAccount;
import com.bank.dto.LoginRequest;
import com.bank.repository.AccountRepository;
import com.bank.repository.CustomerRepository;
import com.bank.repository.RefreshTokenRepository;
import com.bank.repository.TransactionRepository;
import com.bank.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AdminController}: listing accounts, locking/unlocking, and role
 * enforcement (customers must not access admin endpoints).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerIT {

    private static final String ACCT = "4001004001";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();
        userRepository.deleteAll();

        // Standalone admin (no bank account)
        userRepository.save(new UserAccount("admin", passwordEncoder.encode("Admin@123"), Role.ADMIN));

        // One customer with an account
        Customer customer = new Customer("F001", "Lock Test User");
        customer.assignAccount(new SavingsAccount(ACCT, passwordEncoder.encode("7777"),
                new BigDecimal("5000.00"), new BigDecimal("10000.00")));
        customer.assignUser(new UserAccount("locktest", passwordEncoder.encode("Password@123"), Role.CUSTOMER));
        customerRepository.save(customer);
    }

    @Test
    void listAccounts_asAdmin_returnsAll() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].accountNumber").value(ACCT));
    }

    @Test
    void getAccount_asAdmin_returnsAccount() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts/" + ACCT)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(ACCT))
                .andExpect(jsonPath("$.customerName").value("Lock Test User"));
    }

    @Test
    void lockAndUnlock_asAdmin_togglesLockedState() throws Exception {
        // Lock
        mockMvc.perform(post("/api/v1/admin/accounts/" + ACCT + "/lock")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked", is(true)));

        // Unlock
        mockMvc.perform(post("/api/v1/admin/accounts/" + ACCT + "/unlock")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked", is(false)));
    }

    @Test
    void adminEndpoints_asCustomer_returns403() throws Exception {
        String customerToken = token("locktest", "Password@123");

        mockMvc.perform(get("/api/v1/admin/accounts")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoints_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAccount_missingAccount_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts/DOESNOTEXIST")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    private String adminToken() throws Exception {
        return token("admin", "Admin@123");
    }

    private String token(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest(username, password));
        String resp = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).get("accessToken").asText();
    }
}
