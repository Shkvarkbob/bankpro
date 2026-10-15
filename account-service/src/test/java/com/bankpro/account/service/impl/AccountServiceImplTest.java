package com.bankpro.account.service.impl;

import com.bankpro.account.domain.Account;
import com.bankpro.account.domain.AccountOperation;
import com.bankpro.account.exception.InsufficientFundsException;
import com.bankpro.account.repository.AccountOperationRepository;
import com.bankpro.account.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountServiceImplTest {

    private AccountRepository accounts;
    private AccountOperationRepository operations;
    private AccountServiceImpl service;
    private Account account;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        operations = mock(AccountOperationRepository.class);
        service = new AccountServiceImpl(accounts, operations);

        account = new Account();
        account.setAccountNumber("A");
        account.setBalance(new BigDecimal("100.00"));
        when(accounts.findByAccountNumberForUpdate("A")).thenReturn(Optional.of(account));
    }

    @Test
    void debitReducesBalanceAndRecordsOperation() {
        service.debit("A", new BigDecimal("30.00"), "k");

        assertEquals(0, new BigDecimal("70.00").compareTo(account.getBalance()));
        verify(operations).save(any(AccountOperation.class));
    }

    @Test
    void repeatedDebitWithSameKeyIsIgnored() {
        when(operations.existsById("D:k")).thenReturn(true);

        service.debit("A", new BigDecimal("30.00"), "k");

        assertEquals(0, new BigDecimal("100.00").compareTo(account.getBalance()));
        verify(accounts, never()).save(any());
    }

    @Test
    void debitRejectsInsufficientFunds() {
        assertThrows(InsufficientFundsException.class, () -> service.debit("A", new BigDecimal("500"), "k"));
        verify(operations, never()).save(any());
    }

    @Test
    void reverseOfAppliedDebitReturnsMoney() {
        when(operations.existsById("D:k")).thenReturn(true);

        service.reverseDebit("A", new BigDecimal("30.00"), "k");

        assertEquals(0, new BigDecimal("130.00").compareTo(account.getBalance()));
        verify(operations).save(argThat(op -> op.getOperationKey().equals("R:k")));
    }

    @Test
    void reverseOfUnknownDebitBlocksALateDebit() {
        service.reverseDebit("A", new BigDecimal("30.00"), "k");

        assertEquals(0, new BigDecimal("100.00").compareTo(account.getBalance()));
        verify(operations).save(argThat(op -> op.getOperationKey().equals("D:k")));
        verify(operations).save(argThat(op -> op.getOperationKey().equals("R:k")));
    }

    @Test
    void repeatedReverseIsIgnored() {
        when(operations.existsById("R:k")).thenReturn(true);

        service.reverseDebit("A", new BigDecimal("30.00"), "k");

        assertEquals(0, new BigDecimal("100.00").compareTo(account.getBalance()));
        verify(operations, never()).save(any());
    }
}
