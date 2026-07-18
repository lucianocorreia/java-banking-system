package com.banking.transactionservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.*;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";

    public TransactionResponse transfer(TransferRequest request) {
        log.info("Start transfer - {} -> {} amount {}", request.getSenderAccountNumber(),
            request.getReceiverAccountNumber(), request.getAmount());

        // STEP 1: Deduct balance from sender's account
        accountServiceClient.deductBalance(request.getSenderAccountNumber(), request.getAmount());

        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setTransactionStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as PROCESSING: {}", savedTransaction.getId());

        TransactionInitiatedEvent event = new TransactionInitiatedEvent();
        event.setTransactionId(savedTransaction.getId());
        event.setSenderAccountNumber(savedTransaction.getSenderAccountNumber());
        event.setReceiverAccountNumber(savedTransaction.getReceiverAccountNumber());
        event.setAmount(savedTransaction.getAmount());
        event.setDescription(savedTransaction.getDescription());

        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC, savedTransaction.getId(), event);
        log.info("TransactionIntiatedEvent publishied");

        return mapToResponse(savedTransaction);
    }

    public TransactionResponse getTransaction(String transactionId) {
        return mapToResponse(transactionRepository.findById(transactionId)
            .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId)));
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository
            .findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
            .stream()
            .map(this::mapToResponse)
            .toList();

    }

    public TransactionResponse verifyOTP(String transactionId, String otp) {
        throw new UnsupportedOperationException("Unimplemented method 'verifyOTP'");
    }

    private TransactionResponse mapToResponse(Transaction savedTransaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(savedTransaction.getId());
        response.setSenderAccountNumber(savedTransaction.getSenderAccountNumber());
        response.setReceiverAccountNumber(savedTransaction.getReceiverAccountNumber());
        response.setAmount(savedTransaction.getAmount());
        response.setTransactionType(savedTransaction.getTransactionType());
        response.setTransactionStatus(savedTransaction.getTransactionStatus());
        response.setDescription(savedTransaction.getDescription());
        response.setReferenceNumber(savedTransaction.getReferenceNumber());
        response.setCreatedAt(savedTransaction.getCreatedAt());
        response.setCompletedAt(savedTransaction.getCompletedAt());

        return response;

    }
}
