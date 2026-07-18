package com.banking.frauddetectionservice.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.banking.frauddetectionservice.client.AccountServiceClient;
import com.banking.frauddetectionservice.model.FraudCheckResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
@RequiredArgsConstructor
public class FraudDetectionService {
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${fraud.max-transactions-minute}")
    private int maxTransactionsPerMinute = 5;
    @Value("${fraud.suspicious-amount-multiplier}")
    private double suspiciousAmountMultiplier;
    @Value("${fraud.max-balance-percentage}")
    private double maxBalancePercentage;

    private static final String VERIFICATION_REQUIRED_TOPIC = "verification.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT_TOPIC = "fraud.check.clean";

    public void checkTransaction(Map<String, Object> payload) {
        String transactionId = (String) payload.get("transactionId");
        String accountNumber = (String) payload.get("senderAccountNumber");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());

        // Fetch real balance for Account Service
        BigDecimal senderBalance = accountServiceClient.getBalance(accountNumber);
        log.info("Fetched sender balance from Account Service: {}", senderBalance);

        FraudCheckResult result = performFraudeChecks(accountNumber, amount, senderBalance);

        if (result.isFraud()) {
            log.info("Suspicious activity detected - account: {}, reason: {}", accountNumber,
                result.getReason());

            Map<String, Object> verificationEvent = new HashMap<>();
            verificationEvent.put("transactionId", transactionId);
            verificationEvent.put("accountNumber", accountNumber);
            verificationEvent.put("amount", amount);
            verificationEvent.put("reason", result.getReason());

            kafkaTemplate.send(VERIFICATION_REQUIRED_TOPIC, transactionId, verificationEvent);
        } else {
            log.info("Transaction approved - account: {}, amount: {}", accountNumber, amount);
            Map<String, Object> transactionCleanEvent = new HashMap<>();
            transactionCleanEvent.put("transactionId", transactionId);
            transactionCleanEvent.put("isFraud", false);
            transactionCleanEvent.put("reason", null);

            kafkaTemplate.send(FRAUD_CHECK_CLEAN_RESULT_TOPIC, transactionId,
                transactionCleanEvent);
        }

    }

    private FraudCheckResult performFraudeChecks(
        String accountNumber,
        BigDecimal amount,
        BigDecimal senderBalance) {

        // Check if the transaction amount exceeds the sender's balance
        if (isVelocityExceeded(accountNumber)) {
            return new FraudCheckResult(true, "Too many transactions in a short period");
        }

        // Check if the transaction amount is suspiciously high compared to the sender's
        // balance
        if (isAmountSuspicious(accountNumber, amount)) {
            return new FraudCheckResult(true, "Unusual transaction amount");

        }

        // Check if the sender's balance is sufficient for the transaction
        if (senderBalance.compareTo(BigDecimal.ZERO) > 0
            && isBalanceCheckFailed(senderBalance, amount)) {
            return new FraudCheckResult(true, "Insufficient balance for the transaction");
        }

        return new FraudCheckResult(false, null);
    }

    /**
     * @param senderBalance
     * @param amount
     * @return true if the balance check fails, false otherwise
     */
    private boolean isBalanceCheckFailed(BigDecimal senderBalance, BigDecimal amount) {
        BigDecimal maxAllowedAmount = senderBalance
            .multiply(BigDecimal.valueOf(maxBalancePercentage));

        log.info("Balance check - senderBalance: {} maxAllowedAmount: {} amount: {}", senderBalance,
            maxAllowedAmount, amount);

        return amount.compareTo(maxAllowedAmount) > 0;
    }

    /**
     * @param accountNumber
     * @param amount
     * @return true if the amount is suspicious, false otherwise
     */
    private boolean isAmountSuspicious(String accountNumber, BigDecimal amount) {
        String avgKey = "fraud:avg_amount:" + accountNumber;
        String avgStr = redisTemplate.opsForValue().get(avgKey);

        if (avgStr == null) {
            redisTemplate.opsForValue().set(avgKey, amount.toString());
            return false;
        }

        BigDecimal avgAmount = new BigDecimal(avgStr);
        BigDecimal threshold = avgAmount.multiply(BigDecimal.valueOf(suspiciousAmountMultiplier));

        // Update the average amount in Redis
        BigDecimal newAvg = avgAmount.add(amount).divide(BigDecimal.valueOf(2));
        redisTemplate.opsForValue().set(avgKey, newAvg.toString());

        log.info("Amount check - amount: {} threshold: {} suspicious: {}", amount, threshold,
            amount.compareTo(threshold) > 0);

        return amount.compareTo(threshold) > 0;
    }

    /**
     * @param accountNumber
     * @return true if the transaction velocity exceeds the maximum allowed, false
     *         otherwise
     */
    private boolean isVelocityExceeded(String accountNumber) {
        String key = "fraud:velocity:" + accountNumber;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, java.time.Duration.ofMinutes(1));
        }

        log.info("Transaction count for account {} in the last minute: {}", accountNumber, count);

        return count != null && count > maxTransactionsPerMinute;
    }

}
