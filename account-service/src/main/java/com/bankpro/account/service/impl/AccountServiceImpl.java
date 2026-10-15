package com.bankpro.account.service.impl;

import com.bankpro.account.domain.Account;
import com.bankpro.account.domain.AccountOperation;
import com.bankpro.account.dto.CreateAccountRequest;
import com.bankpro.account.exception.AccountAlreadyExistsException;
import com.bankpro.account.exception.InsufficientFundsException;
import com.bankpro.account.repository.AccountOperationRepository;
import com.bankpro.account.repository.AccountRepository;
import com.bankpro.account.service.AccountService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Every balance-changing operation first takes a row lock on the account, so operations on one
 * account are serialized and the idempotency check below cannot race with itself.
 */
@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AccountOperationRepository accountOperationRepository;

    public AccountServiceImpl(AccountRepository accountRepository,
                               AccountOperationRepository accountOperationRepository) {
        this.accountRepository = accountRepository;
        this.accountOperationRepository = accountOperationRepository;
    }

    @Override
    @Transactional
    public Account create(CreateAccountRequest request) {
        if (accountRepository.existsByAccountNumber(request.accountNumber())) {
            throw new AccountAlreadyExistsException(request.accountNumber());
        }
        Account account = new Account();
        account.setAccountNumber(request.accountNumber());
        account.setCustomerId(request.customerId());
        account.setBalance(request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO);
        return accountRepository.save(account);
    }

    @Override
    @Transactional(readOnly = true)
    public Account findByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> notFound(accountNumber));
    }

    @Override
    @Transactional
    public void debit(String accountNumber, BigDecimal amount, String idempotencyKey) {
        Account account = lock(accountNumber);
        String opKey = "D:" + idempotencyKey;
        if (accountOperationRepository.existsById(opKey)) {
            return;
        }
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        accountOperationRepository.save(new AccountOperation(opKey));
    }

    @Override
    @Transactional
    public void credit(String accountNumber, BigDecimal amount, String idempotencyKey) {
        Account account = lock(accountNumber);
        String opKey = "C:" + idempotencyKey;
        if (accountOperationRepository.existsById(opKey)) {
            return;
        }
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        accountOperationRepository.save(new AccountOperation(opKey));
    }

    @Override
    @Transactional
    public void reverseDebit(String accountNumber, BigDecimal amount, String idempotencyKey) {
        Account account = lock(accountNumber);
        String debitKey = "D:" + idempotencyKey;
        String reverseKey = "R:" + idempotencyKey;
        if (accountOperationRepository.existsById(reverseKey)) {
            return;
        }
        if (accountOperationRepository.existsById(debitKey)) {
            account.setBalance(account.getBalance().add(amount));
            accountRepository.save(account);
        } else {
            // The debit never arrived (or its outcome is unknown): block it from ever applying.
            accountOperationRepository.save(new AccountOperation(debitKey));
        }
        accountOperationRepository.save(new AccountOperation(reverseKey));
    }

    private Account lock(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> notFound(accountNumber));
    }

    private static EntityNotFoundException notFound(String accountNumber) {
        return new EntityNotFoundException("Account not found: " + accountNumber);
    }
}
