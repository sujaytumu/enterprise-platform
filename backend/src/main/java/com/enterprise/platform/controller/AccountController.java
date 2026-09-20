package com.enterprise.platform.controller;

import com.enterprise.platform.dto.CreateAccountRequest;
import com.enterprise.platform.model.Account;
import com.enterprise.platform.repository.AccountRepository;
import com.enterprise.platform.security.AuthUtil;
import com.enterprise.platform.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
        CurrentUser user = AuthUtil.current();
        if (user.isAdmin()) return accountRepository.findAll();
        return accountRepository.findByOwnerId(user.userId());
    }

    @GetMapping("/{id}")
    public Account get(@PathVariable UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Account not found"));
        requireOwnerOrAdmin(account);
        return account;
    }

    @PostMapping
    public ResponseEntity<Account> create(@Valid @RequestBody CreateAccountRequest req) {
        CurrentUser user = AuthUtil.current();
        Account account = new Account();
        account.setOwnerId(user.userId());
        account.setHolderName(req.holderName);
        account.setBalance(req.openingBalance);
        account.setCurrency(req.currency != null ? req.currency : "USD");
        account.setAccountNumber(generateAccountNumber());
        return ResponseEntity.ok(accountRepository.save(account));
    }

    /** Throws 403 if the current user neither owns the account nor is an admin. */
    public static void requireOwnerOrAdmin(Account account) {
        CurrentUser user = AuthUtil.current();
        if (user.isAdmin()) return;
        if (account.getOwnerId() == null || !account.getOwnerId().equals(user.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this account");
        }
    }

    private String generateAccountNumber() {
        StringBuilder sb = new StringBuilder("ACC");
        for (int i = 0; i < 10; i++) sb.append(RNG.nextInt(10));
        return sb.toString();
    }
}
