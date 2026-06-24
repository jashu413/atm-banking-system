package com.bank.controller;

import com.bank.domain.CurrentAccount;
import com.bank.domain.Customer;
import com.bank.domain.AuditAction;
import com.bank.domain.AuditStatus;
import com.bank.domain.Role;
import com.bank.domain.SavingsAccount;
import com.bank.domain.UserAccount;
import com.bank.dto.LoginRequest;
import com.bank.repository.AccountRepository;
import com.bank.repository.AuditLogRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AccountController} covering ownership enforcement, deposit,
 * withdrawal, and PIN change flows against a real H2 database.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIT {

    private static final String ACCT = "1001001001";
    private static final String OTHER_ACCT = "1001001002";
    private static final String PIN = "1234";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();
        userRepository.deleteAll();

        Customer asha = new Customer("C001", "Asha Sharma");
        asha.assignAccount(new SavingsAccount(ACCT, passwordEncoder.encode(PIN),
                new BigDecimal("25000.00"), new BigDecimal("10000.00")));
        asha.assignUser(new UserAccount("asha", passwordEncoder.encode("Password@123"), Role.CUSTOMER));
        customerRepository.save(asha);

        Customer ravi = new Customer("C002", "Ravi Kumar");
        ravi.assignAccount(new CurrentAccount(OTHER_ACCT, passwordEncoder.encode("2345"),
                new BigDecimal("50000.00"), new BigDecimal("25000.00")));
        ravi.assignUser(new UserAccount("ravi", passwordEncoder.encode("Password@123"), Role.CUSTOMER));
        customerRepository.save(ravi);
    }

    // ── GET /{accountNumber} ────────────────────────────────────────────────────

    @Test
    void getOwnAccount_returns200WithDetails() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + ACCT)
                        .header("Authorization", "Bearer " + ashaToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(ACCT))
                .andExpect(jsonPath("$.customerName").value("Asha Sharma"))
                .andExpect(jsonPath("$.balance").value(25000.00))
                .andExpect(jsonPath("$.locked").value(false));
    }

    @Test
    void getAnotherCustomersAccount_returns404() throws Exception {
        // Asha tries to access Ravi's account — must look identical to not-found
        mockMvc.perform(get("/api/v1/accounts/" + OTHER_ACCT)
                        .header("Authorization", "Bearer " + ashaToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAccount_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + ACCT))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{accountNumber}/deposit ───────────────────────────────────────────

    @Test
    void deposit_validAmount_increasesBalance() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("amount", "500.00"));

        mockMvc.perform(post("/api/v1/accounts/" + ACCT + "/deposit")
                        .header("Authorization", "Bearer " + ashaToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(25500.00));
    }

    @Test
    void deposit_toAnotherCustomersAccount_returns404() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("amount", "100.00"));

        mockMvc.perform(post("/api/v1/accounts/" + OTHER_ACCT + "/deposit")
                        .header("Authorization", "Bearer " + ashaToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void deposit_negativeAmount_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("amount", "-100.00"));

        mockMvc.perform(post("/api/v1/accounts/" + ACCT + "/deposit")
                        .header("Authorization", "Bearer " + ashaToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deposit_missingAmount_returns400WithErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/" + ACCT + "/deposit")
                        .header("Authorization", "Bearer " + ashaToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("amount: must not be null"))
                .andExpect(jsonPath("$.path").value("/api/v1/accounts/" + ACCT + "/deposit"));
    }

    @Test
    void deposit_validAmount_persistsAuditLog() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("amount", "250.00"));

        mockMvc.perform(post("/api/v1/accounts/" + ACCT + "/deposit")
                        .header("Authorization", "Bearer " + ashaToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.findAll())
                .anySatisfy(log -> {
                    assertThat(log.getAction()).isEqualTo(AuditAction.DEPOSIT);
                    assertThat(log.getStatus()).isEqualTo(AuditStatus.SUCCESS);
                    assertThat(log.getUsername()).isEqualTo("asha");
                    assertThat(log.getAccountNumber()).isEqualTo(ACCT);
                    assertThat(log.getMessage()).doesNotContain(PIN, "Password@123", "Bearer");
                });
    }

    // ── POST /{accountNumber}/withdraw ──────────────────────────────────────────

    @Test
    void withdraw_correctPin_decreasesBalance() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("pin", PIN, "amount", "1000.00"));

        mockMvc.perform(post("/api/v1/accounts/" + ACCT + "/withdraw")
                        .header("Authorization", "Bearer " + ashaToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(24000.00));
    }

    @Test
    void withdraw_wrongPin_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("pin", "0000", "amount", "100.00"));

        mockMvc.perform(post("/api/v1/accounts/" + ACCT + "/withdraw")
                        .header("Authorization", "Bearer " + ashaToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdraw_exceedsDailyLimit_returns422() throws Exception {
        // daily limit is 10000.00; single request of 10001.00 should fail
        String body = objectMapper.writeValueAsString(Map.of("pin", PIN, "amount", "10001.00"));

        mockMvc.perform(post("/api/v1/accounts/" + ACCT + "/withdraw")
                        .header("Authorization", "Bearer " + ashaToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── POST /{accountNumber}/pin ───────────────────────────────────────────────

    @Test
    void changePin_correctCurrentPin_succeeds() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("currentPin", PIN, "newPin", "9999"));

        mockMvc.perform(post("/api/v1/accounts/" + ACCT + "/pin")
                        .header("Authorization", "Bearer " + ashaToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void changePin_wrongCurrentPin_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("currentPin", "0000", "newPin", "9999"));

        mockMvc.perform(post("/api/v1/accounts/" + ACCT + "/pin")
                        .header("Authorization", "Bearer " + ashaToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private String ashaToken() throws Exception {
        return token("asha", "Password@123");
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
