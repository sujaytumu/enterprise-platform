package com.enterprise.platform.controller;

import com.enterprise.platform.dto.CreateAccountRequest;
import com.enterprise.platform.model.Account;
import com.enterprise.platform.repository.AccountRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountRepository accountRepository;
    private static final SecureRandom RNG = new SecureRandom();

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<Account> list() {
        return accountRepository.findAll();
    }

    @GetMapping("/{id}")
    public Account get(@PathVariable UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Account not found"));
    }

    @PostMapping
    public ResponseEntity<Account> create(@Valid @RequestBody CreateAccountRequest req) {
        Account account = new Account();
        account.setHolderName(req.holderName);
        account.setBalance(req.openingBalance);
        account.setCurrency(req.currency != null ? req.currency : "USD");
        account.setAccountNumber(generateAccountNumber());
        return ResponseEntity.ok(accountRepository.save(account));
    }

    private String generateAccountNumber() {
        StringBuilder sb = new StringBuilder("ACC");
        for (int i = 0; i < 10; i++) sb.append(RNG.nextInt(10));
        return sb.toString();
    }
}
