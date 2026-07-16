package com.banking.accountservice.controller;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.service.AccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/accounts")
@Slf4j
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * Endpoint to create a new bank account.
     *
     * @param createAccountRequest the request body containing account details
     * @return ResponseEntity containing the created account response
     */
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
        @Valid
        @RequestBody
        CreateAccountRequest createAccountRequest) {

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(accountService.createAccount(createAccountRequest));

    }

    /**
     * Endpoint to retrieve account details by account number.
     *
     * @param accountNumber the account number of the account to retrieve
     * @return ResponseEntity containing the account response
     */
    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccount(
        @PathVariable
        String accountNumber) {
        return ResponseEntity.ok(accountService.getAccount(accountNumber));
    }

    /**
     * Endpoint to retrieve the balance of an account by account number.
     *
     * @param accountNumber the account number of the account to retrieve the
     *                      balance
     * @return ResponseEntity containing the account balance
     */
    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<BigDecimal> getBalance(
        @PathVariable
        String accountNumber) {
        return ResponseEntity.ok(accountService.getBalance(accountNumber));
    }

    @PutMapping("/{accountNumber}/block")
    public ResponseEntity<String> blockAccount(
        @PathVariable
        String accountNumber) {
        accountService.blockAccount(accountNumber);
        return ResponseEntity.ok("Account blocked successfully");
    }

    @PutMapping("/{accountNumber}/deduct")
    public ResponseEntity<String> deductBalance(
        @PathVariable
        String accountNumber,
        @RequestParam
        BigDecimal amount) {
        accountService.deductBalance(accountNumber, amount);
        return ResponseEntity.ok("Balance deducted successfully");
    }

    /**
     * Endpoint to block an account by account number.
     *
     * @param accountNumber the account number of the account to block
     * @return ResponseEntity containing the status message
     */
    @PutMapping("/{accountNumber}/credit")
    public ResponseEntity<String> creditBalance(
        @PathVariable
        String accountNumber,
        @RequestParam
        BigDecimal amount) {
        accountService.creditBalance(accountNumber, amount);
        return ResponseEntity.ok("Balance credited successfully");
    }

}
