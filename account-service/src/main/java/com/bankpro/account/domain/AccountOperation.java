package com.bankpro.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "account_operations")
public class AccountOperation {

    @Id
    @Column(length = 68)
    private String operationKey;

    @Column(nullable = false)
    private OffsetDateTime processedAt = OffsetDateTime.now();

    public AccountOperation() {}

    public AccountOperation(String operationKey) {
        this.operationKey = operationKey;
    }

    public String getOperationKey() { return operationKey; }
    public void setOperationKey(String operationKey) { this.operationKey = operationKey; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime processedAt) { this.processedAt = processedAt; }
}
