package com.bank.controller;

import com.bank.dto.AccountResponse;
import com.bank.dto.ChangePinRequest;
import com.bank.dto.DepositRequest;
import com.bank.dto.WithdrawRequest;
import com.bank.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for single-account operations: details, deposit, withdraw, and PIN change.
 *
 * <p>All endpoints are customer-facing. The authenticated principal's username is extracted from
 * the JWT and passed to {@link AccountService} for ownership validation — a customer can only
 * operate on their own account. Any attempt to access a non-owned account returns 404 (same as
 * missing) to prevent account-number enumeration.
 *
 * <p>Domain exceptions are mapped to HTTP status codes by {@link com.bank.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Customer account operations")
@Validated
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{accountNumber}")
    @Operation(summary = "Get account details", description = "Returns details for an account owned by the authenticated customer.")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable @NotBlank String accountNumber, Authentication auth) {
        return ResponseEntity.ok(accountService.getOwnedAccount(accountNumber, auth.getName()));
    }

    @PostMapping("/{accountNumber}/deposit")
    @Operation(summary = "Deposit cash into the account", description = "Deposits a positive amount into an owned account.")
    public ResponseEntity<AccountResponse> deposit(
            @PathVariable @NotBlank String accountNumber,
            @Valid @RequestBody DepositRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                accountService.deposit(accountNumber, auth.getName(), request.amount()));
    }

    @PostMapping("/{accountNumber}/withdraw")
    @Operation(summary = "Withdraw cash", description = "Withdraws a positive amount from an owned account after PIN verification.")
    public ResponseEntity<AccountResponse> withdraw(
            @PathVariable @NotBlank String accountNumber,
            @Valid @RequestBody WithdrawRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                accountService.withdraw(accountNumber, auth.getName(), request.pin(), request.amount()));
    }

    @PostMapping("/{accountNumber}/pin")
    @Operation(summary = "Change the transaction PIN", description = "Changes the transaction PIN after verifying the current PIN.")
    public ResponseEntity<AccountResponse> changePin(
            @PathVariable @NotBlank String accountNumber,
            @Valid @RequestBody ChangePinRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                accountService.changePin(accountNumber, auth.getName(),
                        request.currentPin(), request.newPin()));
    }
}
