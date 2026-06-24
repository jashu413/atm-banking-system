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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link TransferController}: successful transfer, IDOR prevention on
 * source account, self-transfer rejection, insufficient funds, and wrong PIN.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransferControllerIT {

    private static final String SOURCE = "3001003001";
    private static final String TARGET = "3001003002";
    private static final String SOURCE_PIN = "4321";

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

        Customer sender = new Customer("E001", "Transfer Sender");
        sender.assignAccount(new SavingsAccount(SOURCE, passwordEncoder.encode(SOURCE_PIN),
                new BigDecimal("10000.00"), new BigDecimal("50000.00")));
        sender.assignUser(new UserAccount("sender", passwordEncoder.encode("Password@123"), Role.CUSTOMER));
        customerRepository.save(sender);

        Customer receiver = new Customer("E002", "Transfer Receiver");
        receiver.assignAccount(new SavingsAccount(TARGET, passwordEncoder.encode("9999"),
                new BigDecimal("5000.00"), new BigDecimal("50000.00")));
        receiver.assignUser(new UserAccount("receiver", passwordEncoder.encode("Password@123"), Role.CUSTOMER));
        customerRepository.save(receiver);
    }

    @Test
    void transfer_correctPinAndSufficientFunds_succeeds() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "targetAccountNumber", TARGET, "pin", SOURCE_PIN, "amount", "500.00"));

        mockMvc.perform(post("/api/v1/accounts/" + SOURCE + "/transfer")
                        .header("Authorization", "Bearer " + senderToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Transfer completed successfully."));
    }

    @Test
    void transfer_fromAnotherCustomersAccount_returns404() throws Exception {
        // Receiver tries to initiate a transfer FROM sender's account (IDOR check)
        String body = objectMapper.writeValueAsString(Map.of(
                "targetAccountNumber", TARGET, "pin", SOURCE_PIN, "amount", "100.00"));

        mockMvc.perform(post("/api/v1/accounts/" + SOURCE + "/transfer")
                        .header("Authorization", "Bearer " + token("receiver", "Password@123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_selfTransfer_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "targetAccountNumber", SOURCE, "pin", SOURCE_PIN, "amount", "100.00"));

        mockMvc.perform(post("/api/v1/accounts/" + SOURCE + "/transfer")
                        .header("Authorization", "Bearer " + senderToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_wrongPin_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "targetAccountNumber", TARGET, "pin", "0000", "amount", "100.00"));

        mockMvc.perform(post("/api/v1/accounts/" + SOURCE + "/transfer")
                        .header("Authorization", "Bearer " + senderToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_insufficientFunds_returns422() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "targetAccountNumber", TARGET, "pin", SOURCE_PIN, "amount", "99999.00"));

        mockMvc.perform(post("/api/v1/accounts/" + SOURCE + "/transfer")
                        .header("Authorization", "Bearer " + senderToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    private String senderToken() throws Exception {
        return token("sender", "Password@123");
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
