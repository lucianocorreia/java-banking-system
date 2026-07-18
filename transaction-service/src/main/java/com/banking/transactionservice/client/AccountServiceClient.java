package com.banking.transactionservice.client;

import java.math.BigDecimal;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "account-service", url = "${account.service.url}")
public interface AccountServiceClient {

    @PutMapping("/api/v1/accounts/{accountNumber}/deduct")
    String deductBalance(
        @PathVariable
        String accountNumber,
        @RequestParam
        BigDecimal amount);

}
