package com.bankpro.account.controller;

import com.bankpro.account.domain.Account;
import com.bankpro.account.dto.CreateAccountRequest;
import com.bankpro.account.service.AccountService;
import com.bankpro.common.dto.TransferRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {this.accountService = accountService;}

    @PostMapping
    public ResponseEntity<Account> create(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(request));
    }

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

    @PostMapping("/reverse-debit")
    public ResponseEntity<Void> reverseDebit(@Valid @RequestBody TransferRequest request) {
        accountService.reverseDebit(request.fromAccount(), request.amount(), request.idempotencyKey());
        return ResponseEntity.ok().build();
    }
}
