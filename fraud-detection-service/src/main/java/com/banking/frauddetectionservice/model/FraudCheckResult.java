package com.banking.frauddetectionservice.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckResult {
    private boolean fraud;
    private String reason;
}
