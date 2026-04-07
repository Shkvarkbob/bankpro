package com.bankpro.account.repository;

import com.bankpro.account.domain.Account;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface AccountRepository extends CrudRepository<Account, Long> {
    Optional<Account> findByAccountNumber(String accountNumber);
}
