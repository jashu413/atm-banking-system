package com.bank.controller;

import com.bank.domain.Customer;
import com.bank.dto.UserProfileResponse;
import com.bank.repository.CustomerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * User-scoped endpoints that work for both CUSTOMER and ADMIN principals.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Authenticated user profile")
public class UserController {

    private final CustomerRepository customerRepository;

    public UserController(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile",
               description = "Returns the authenticated user's username, role, and (for customers) account number.")
    public ResponseEntity<UserProfileResponse> getProfile(Authentication auth) {
        String username = auth.getName();
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("UNKNOWN");

        Optional<Customer> customerOpt = customerRepository.findByUser_Username(username);
        String customerName = customerOpt.map(Customer::getName).orElse(null);
        String accountNumber = customerOpt
                .map(Customer::getAccount)
                .map(a -> a != null ? a.getAccountNumber() : null)
                .orElse(null);

        return ResponseEntity.ok(new UserProfileResponse(username, role, customerName, accountNumber));
    }
}
