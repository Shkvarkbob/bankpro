package com.bankpro.account.controller;

import com.bankpro.common.dto.TransferRequest;
import com.bankpro.account.domain.Account;
import com.bankpro.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {this.accountService = accountService;}

    @GetMapping("/{accountNumber}")
    public ResponseEntity<Account> getAccount(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.findByAccountNumber(accountNumber));
    }

    @PostMapping("/debit")
    public ResponseEntity<Void> debit(@Valid @RequestBody TransferRequest request) {
        accountService.debit(request.fromAccount(), request.amount(), request.idempotencyKey());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/credit")
    public ResponseEntity<Void> credit(@Valid @RequestBody TransferRequest request) {
        accountService.credit(request.toAccount(), request.amount(), request.idempotencyKey());
        return ResponseEntity.ok().build();
    }
}
