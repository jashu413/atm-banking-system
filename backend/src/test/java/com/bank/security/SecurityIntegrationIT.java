package com.bank.security;

import com.bank.domain.Role;
import com.bank.domain.UserAccount;
import com.bank.dto.LoginRequest;
import com.bank.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end security wiring test against the full application context (H2). Verifies that
 * protected endpoints reject unauthenticated/under-privileged access, that the login endpoint
 * issues a usable JWT, and that role rules are enforced.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedUser() {
        userRepository.deleteAll();
        userRepository.save(new UserAccount("alice",
                passwordEncoder.encode("Password@123"), Role.CUSTOMER));
    }

    private String loginBody(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginRequest(username, password));
    }

    @Test
    void unauthenticatedRequestToProtectedEndpointIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authEndpointsArePublic_loginWithValidCredentialsReturnsAToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alice", "Password@123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("alice", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerTokenIsForbiddenFromAdminEndpoints() throws Exception {
        String token = obtainAccessToken("alice", "Password@123");

        mockMvc.perform(get("/api/v1/admin/customers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void validTokenPassesAuthentication() throws Exception {
        String token = obtainAccessToken("alice", "Password@123");

        // No Phase 4 controller exists for this path yet, so a successfully authenticated request
        // falls through to 404 rather than being rejected with 401/403.
        mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private String obtainAccessToken(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        String token = node.get("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
