package com.bankpro.account.service.impl;

import com.bankpro.account.domain.Account;
import com.bankpro.account.domain.AccountOperation;
import com.bankpro.account.repository.AccountOperationRepository;
import com.bankpro.account.repository.AccountRepository;
import com.bankpro.account.service.AccountService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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
    public Account findByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountNumber));
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void debit(String accountNumber, BigDecimal amount, String idempotencyKey) {
        String opKey = "D:" + idempotencyKey;
        if (accountOperationRepository.existsById(opKey)) {
            return;
        }
        Account account = findByAccountNumber(accountNumber);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        accountOperationRepository.save(new AccountOperation(opKey));
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void credit(String accountNumber, BigDecimal amount, String idempotencyKey) {
        String opKey = "C:" + idempotencyKey;
        if (accountOperationRepository.existsById(opKey)) {
            return;
        }
        Account account = findByAccountNumber(accountNumber);
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        accountOperationRepository.save(new AccountOperation(opKey));
    }
}
