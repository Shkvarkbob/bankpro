package com.bankpro.account.service;

import com.bankpro.account.domain.Account;
import com.bankpro.account.dto.CreateAccountRequest;

import java.math.BigDecimal;

public interface AccountService {
    Account create(CreateAccountRequest request);
    Account findByAccountNumber(String accountNumber);
    void debit(String accountNumber, BigDecimal amount, String idempotencyKey);
    void credit(String accountNumber, BigDecimal amount, String idempotencyKey);

    /**
     * Undoes the debit made under the same idempotency key. If that debit was never seen, it is
     * recorded as cancelled so that a late-arriving debit with this key becomes a no-op.
     */
    void reverseDebit(String accountNumber, BigDecimal amount, String idempotencyKey);
}
