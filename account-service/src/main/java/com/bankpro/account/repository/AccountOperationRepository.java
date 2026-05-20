package com.bankpro.account.repository;

import com.bankpro.account.domain.AccountOperation;
import org.springframework.data.repository.CrudRepository;

public interface AccountOperationRepository extends CrudRepository<AccountOperation, String> {}
