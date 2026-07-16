package com.banking.accountservice.service;

import java.math.BigDecimal;
import java.security.SecureRandom;

import org.springframework.stereotype.Service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.*;
import com.banking.accountservice.repository.AccountRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private SecureRandom secureRandom = new SecureRandom();

    public AccountResponse createAccount(CreateAccountRequest createAccountRequest) {
        log.info("Creating account for: {}", createAccountRequest.getEmail());

        // Check if an account with the same email already exists
        if (accountRepository.existsByEmail(createAccountRequest.getEmail())) {
            throw new RuntimeException(
                "Account with email " + createAccountRequest.getEmail() + " already exists");
        }

        Account account = new Account();
        account.setAccountHolderName(createAccountRequest.getAccountHolderName());
        account.setEmail(createAccountRequest.getEmail());
        account.setPhoneNumber(createAccountRequest.getPhoneNumber());
        account.setAccountType(createAccountRequest.getAccountType());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setBalance(createAccountRequest.getInitialDeposit());

        account.setAccountNumber(genereateAccountNumber());

        account.setDailyTransactionLimit(
            createAccountRequest.getAccountType() == AccountType.SAVINGS
                ? new BigDecimal("100000.00")
                : new BigDecimal("500000.00"));

        Account savedAccount = accountRepository.save(account);
        log.info("Account created successfully with account number: {}",
            savedAccount.getAccountNumber());

        return mapToAccountResponse(savedAccount);
    }

    private AccountResponse mapToAccountResponse(Account savedAccount) {
        AccountResponse response = new AccountResponse();
        response.setId(savedAccount.getId().toString());
        response.setAccountNumber(savedAccount.getAccountNumber());
        response.setAccountHolderName(savedAccount.getAccountHolderName());
        response.setEmail(savedAccount.getEmail());
        response.setPhoneNumber(savedAccount.getPhoneNumber());
        response.setAccountType(savedAccount.getAccountType());
        response.setAccountStatus(savedAccount.getAccountStatus());
        response.setBalance(savedAccount.getBalance());
        response.setDailyTransactionLimit(savedAccount.getDailyTransactionLimit());
        return response;
    }

    /**
     * Generates a unique 12-digit account number.
     *
     * @return a unique account number
     */
    private String genereateAccountNumber() {
        String accountNumber;

        do {

            long number = secureRandom.nextLong(1_000_000_000_0000L);
            accountNumber = String.format("%012d", number);

        } while (accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }

    public AccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new RuntimeException(
                "Account with account number " + accountNumber + " not found"));

        return mapToAccountResponse(account);
    }

    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new RuntimeException(
                "Account with account number " + accountNumber + " not found"));

        return account.getBalance();
    }

    public void blockAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new RuntimeException(
                "Account with account number " + accountNumber + " not found"));

        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account with account number {} has been blocked", accountNumber);
    }

    public void deductBalance(String accountNumber, BigDecimal amount) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new RuntimeException(
                "Account with account number " + accountNumber + " not found"));

        // Check if the account is active before deducting balance
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException(
                "Cannot deduct balance from account with account number " + accountNumber
                    + " because it is not active");
        }

        // Check if the account has sufficient balance before deducting
        if (account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException(
                "Insufficient balance in account with account number " + accountNumber);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Deducted {} from account with account number {}", amount, accountNumber);
    }

    public void creditBalance(String accountNumber, BigDecimal amount) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new RuntimeException(
                "Account with account number " + accountNumber + " not found"));

        // Check if the account is active before crediting balance
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException(
                "Cannot credit balance to account with account number " + accountNumber
                    + " because it is not active");
        }

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("Credited {} to account with account number {}", amount, accountNumber);
    }

}
