package com.enterprise.platform.controller;

import com.enterprise.platform.model.LedgerEntry;
import com.enterprise.platform.repository.AccountRepository;
import com.enterprise.platform.repository.LedgerEntryRepository;
import com.enterprise.platform.security.AuthUtil;
import com.enterprise.platform.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;

    public LedgerController(LedgerEntryRepository ledgerEntryRepository, AccountRepository accountRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping("/entries")
    public List<LedgerEntry> entries(@RequestParam(required = false) UUID accountId) {
        CurrentUser user = AuthUtil.current();
        if (accountId != null) {
            AccountController.requireOwnerOrAdmin(accountRepository.findById(accountId)
                    .orElseThrow(() -> new NoSuchElementException("Account not found")));
            return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
        }
        if (user.isAdmin()) return ledgerEntryRepository.findAll();
        return accountRepository.findByOwnerId(user.userId()).stream()
                .flatMap(a -> ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(a.getId()).stream())
                .toList();
    }
}
