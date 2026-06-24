package com.bank.controller;

import com.bank.dto.TransactionResponse;
import com.bank.service.AccountService;
import com.bank.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only access to the transaction ledger for a customer's own account.
 *
 * <p>Ownership is validated by calling {@link AccountService#getOwnedAccount} before fetching
 * transactions. A customer who requests transactions for an account they do not own receives a 404
 * (identical to a missing account) to prevent information leakage.
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountNumber}/transactions")
@Tag(name = "Transactions", description = "Account transaction history")
@Validated
public class TransactionController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    public TransactionController(AccountService accountService,
                                  TransactionService transactionService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    @GetMapping
    @Operation(summary = "Get full transaction history", description = "Returns all transactions for an owned account, newest first.")
    public ResponseEntity<List<TransactionResponse>> getHistory(
            @PathVariable @NotBlank String accountNumber, Authentication auth) {
        accountService.getOwnedAccount(accountNumber, auth.getName()); // ownership gate
        return ResponseEntity.ok(
                transactionService.getHistory(accountNumber).stream()
                        .map(TransactionResponse::from)
                        .toList());
    }

    @GetMapping("/mini")
    @Operation(summary = "Get mini-statement", description = "Returns the most recent transactions for an owned account.")
    public ResponseEntity<List<TransactionResponse>> getMiniStatement(
            @PathVariable @NotBlank String accountNumber,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int count,
            Authentication auth) {
        accountService.getOwnedAccount(accountNumber, auth.getName()); // ownership gate
        return ResponseEntity.ok(
                transactionService.getMiniStatement(accountNumber, count).stream()
                        .map(TransactionResponse::from)
                        .toList());
    }
}
