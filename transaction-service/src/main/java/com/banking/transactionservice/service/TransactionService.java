package com.banking.transactionservice.service;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.*;
import com.banking.transactionservice.event.TransactionCompletedEvent;
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
    private final RedisTemplate<String, String> redisTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC = "fraud.detected";

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
        log.info("OTP verification for the transaction: {}", transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));

        String otpKey = "verification:otp:" + transactionId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if (storedOtp == null) {
            // OTP Expired
            log.warn("Otp expired for transaction: {}", transactionId);
            compensateTransaction(transaction,
                "OTP expired, transaction cancelled and amount refunded");
            return mapToResponse(transaction);
        }

        if (!storedOtp.equals(otp)) {
            // OTP Mismatch
            log.warn("Otp mismatch for transaction: {}", transactionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction,
                "OTP mismatch, transaction cancelled and amount refunded");
            return mapToResponse(transaction);
        }

        // OTP Correct
        log.info("OTP verified - completing transaction: {}", transactionId);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);

        return mapToResponse(transaction);
    }

    private void completeTransaction(Transaction transaction) {
        transaction.setTransactionStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent = new TransactionCompletedEvent();
        completedEvent.setTransactionId(transaction.getId());
        completedEvent.setSenderAccountNumber(transaction.getSenderAccountNumber());
        completedEvent.setReceiverAccountNumber(transaction.getReceiverAccountNumber());
        completedEvent.setAmount(transaction.getAmount());
        completedEvent.setDescription(transaction.getDescription());

        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC, transaction.getId(), completedEvent);
        log.info("TransactionCompletedEvent published for transaction: {}", transaction.getId());
    }

    private void blockAccountAndCompensate(Transaction transaction, String reason) {
        // Publish fraud.detected
        Map<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("accountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason", reason);

        kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transaction.getSenderAccountNumber(), fraudEvent);
        log.warn("FraudDetectedEvent published for transaction: {}", transaction.getId());

        compensateTransaction(transaction, reason);
    }

    private void compensateTransaction(Transaction transaction, String reason) {
        log.info("Compensating transaction: {} - {}", transaction.getId(), reason);

        // Credite money back
        accountServiceClient.creditBalance(transaction.getSenderAccountNumber(),
            transaction.getAmount());
        transaction.setTransactionStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason);
        transactionRepository.save(transaction);

        // Publish Notification Event
        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount", transaction.getAmount());
        refundEvent.put("reason", reason);

        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC, transaction.getId(), refundEvent);
        log.info("TransactionRefundedEvent published for transaction: {}", transaction.getId());
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
