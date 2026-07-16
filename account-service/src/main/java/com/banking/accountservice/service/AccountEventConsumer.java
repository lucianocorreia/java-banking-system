package com.banking.accountservice.service;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {
    private final AccountService accountService;

    /**
     * Consumes a transaction completed event from kafka and processes it
     * accordingly.
     *
     * @param payload the event payload
     */
    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(
        @Payload
        Map<String, Object> payload) {
        try {
            String receiverAccount = (String) payload.get("receiverAccountNumber");
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());

            log.info("Received transaction completed event for account {} with amount {}",
                receiverAccount, amount);

            accountService.creditBalance(receiverAccount, amount);

        } catch (Exception e) {
            log.error("Failed to consume transaction completed event", e);
        }

    }

    /**
     * Consumes a fraud detected event from kafka and processes it accordingly.
     *
     * @param payload the event payload
     */
    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(@Payload
    Map<String, Object> payload) {
        try {
            String accountNumber = (String) payload.get("accountNumber");

            log.info("Received fraud detected event for account {}",
                accountNumber);

            accountService.blockAccount(accountNumber);

        } catch (Exception e) {
            log.error("Failed to consume fraud detected event", e);
        }
    }
}
