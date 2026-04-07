package com.bankpro.account.service;

import com.bankpro.account.domain.Account;

import java.math.BigDecimal;

public interface AccountService {
    Account findByAccountNumber(String accountNumber);
    void debit(String accountNumber, BigDecimal amount, String idempotencyKey);
    void credit(String accountNumber, BigDecimal amount, String idempotencyKey);
}
