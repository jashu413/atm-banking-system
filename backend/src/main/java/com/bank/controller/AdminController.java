package com.bank.controller;

import com.bank.dto.AccountResponse;
import com.bank.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Back-office endpoints restricted to the {@code ADMIN} role (enforced in
 * {@link com.bank.config.SecurityConfig} at the URL level and again by {@code @PreAuthorize} on
 * {@link AdminService}).
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Administrative account management")
@Validated
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/accounts")
    @Operation(summary = "List all customer accounts", description = "Returns all customer bank accounts. Requires ADMIN role.")
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        return ResponseEntity.ok(adminService.listAllAccounts());
    }

    @GetMapping("/accounts/{accountNumber}")
    @Operation(summary = "Get any account by number", description = "Returns any customer account by account number. Requires ADMIN role.")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable @NotBlank String accountNumber) {
        return ResponseEntity.ok(adminService.getAccount(accountNumber));
    }

    @PostMapping("/accounts/{accountNumber}/lock")
    @Operation(summary = "Lock an account", description = "Freezes account operations for a customer account. Requires ADMIN role.")
    public ResponseEntity<AccountResponse> lockAccount(@PathVariable @NotBlank String accountNumber) {
        return ResponseEntity.ok(adminService.lockAccount(accountNumber));
    }

    @PostMapping("/accounts/{accountNumber}/unlock")
    @Operation(summary = "Unlock an account", description = "Restores operations for a previously locked account. Requires ADMIN role.")
    public ResponseEntity<AccountResponse> unlockAccount(@PathVariable @NotBlank String accountNumber) {
        return ResponseEntity.ok(adminService.unlockAccount(accountNumber));
    }
}
