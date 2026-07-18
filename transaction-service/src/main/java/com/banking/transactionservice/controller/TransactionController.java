package com.banking.transactionservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.service.TransactionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/transactions")
@Slf4j
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
        @Valid
        @RequestBody
        TransferRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.transfer(request));

    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(
        @PathVariable
        String transactionId) {
        // Implement the logic to retrieve a transaction by its ID
        return ResponseEntity.ok(transactionService.getTransaction(transactionId));
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<List<TransactionResponse>> getTransactionHistory(
        @PathVariable
        String accountNumber) {
        // Implement the logic to retrieve a transaction by its ID
        return ResponseEntity.ok(transactionService.getTransactionHistory(accountNumber));
    }

    @PostMapping("/{transactionId}/verify")
    public ResponseEntity<TransactionResponse> verifyOTP(
        @PathVariable
        String transactionId,
        @RequestParam
        String otp) {
        log.info("OTP vberification request received for transactionId: {} with OTP: {}",
            transactionId, otp);
        return ResponseEntity.ok(transactionService.verifyOTP(transactionId, otp));
    }

}
