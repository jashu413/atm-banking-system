package com.bank.controller;

import com.bank.dto.TransferRequest;
import com.bank.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Initiates a fund transfer from the authenticated customer's account to any other account.
 *
 * <p>The source account is identified by the path variable and must be owned by the authenticated
 * user — enforced inside {@link TransferService} via a pessimistic-locked ownership query.
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountNumber}/transfer")
@Tag(name = "Transfers", description = "Fund transfers between accounts")
@Validated
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @Operation(summary = "Transfer funds to another account", description = "Transfers a positive amount from an owned source account after PIN verification.")
    public ResponseEntity<Map<String, String>> transfer(
            @PathVariable @NotBlank String accountNumber,
            @Valid @RequestBody TransferRequest request,
            Authentication auth) {
        transferService.transfer(
                accountNumber,
                request.targetAccountNumber(),
                auth.getName(),
                request.pin(),
                request.amount());
        return ResponseEntity.ok(Map.of("message", "Transfer completed successfully."));
    }
}
