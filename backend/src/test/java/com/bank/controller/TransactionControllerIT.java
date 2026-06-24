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
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link TransactionController}: history, mini-statement, and cross-account
 * access prevention.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerIT {

    private static final String ACCT = "2001002001";
    private static final String OTHER_ACCT = "2001002002";
    private static final String PIN = "5678";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();
        userRepository.deleteAll();

        Customer neha = new Customer("D001", "Neha Patel");
        neha.assignAccount(new SavingsAccount(ACCT, passwordEncoder.encode(PIN),
                new BigDecimal("20000.00"), new BigDecimal("10000.00")));
        neha.assignUser(new UserAccount("neha", passwordEncoder.encode("Password@123"), Role.CUSTOMER));
        customerRepository.save(neha);

        Customer ravi = new Customer("D002", "Ravi Kumar");
        ravi.assignAccount(new SavingsAccount(OTHER_ACCT, passwordEncoder.encode("1111"),
                new BigDecimal("5000.00"), new BigDecimal("10000.00")));
        ravi.assignUser(new UserAccount("ravi2", passwordEncoder.encode("Password@123"), Role.CUSTOMER));
        customerRepository.save(ravi);

        // Generate 4 deposits via the API so transactions appear in the ledger
        String nehaToken = token("neha", "Password@123");
        for (int i = 1; i <= 4; i++) {
            String body = objectMapper.writeValueAsString(Map.of("amount", String.valueOf(i * 100)));
            mockMvc.perform(post("/api/v1/accounts/" + ACCT + "/deposit")
                    .header("Authorization", "Bearer " + nehaToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body));
        }
    }

    @Test
    void getHistory_returnsAllTransactionsNewestFirst() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + ACCT + "/transactions")
                        .header("Authorization", "Bearer " + token("neha", "Password@123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].type").value("DEPOSIT"));
    }

    @Test
    void getMiniStatement_limitsResults() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + ACCT + "/transactions/mini?count=2")
                        .header("Authorization", "Bearer " + token("neha", "Password@123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void getHistory_forAnotherCustomersAccount_returns404() throws Exception {
        // Neha trying to read Ravi's transaction history
        mockMvc.perform(get("/api/v1/accounts/" + OTHER_ACCT + "/transactions")
                        .header("Authorization", "Bearer " + token("neha", "Password@123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getHistory_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + ACCT + "/transactions"))
                .andExpect(status().isUnauthorized());
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
