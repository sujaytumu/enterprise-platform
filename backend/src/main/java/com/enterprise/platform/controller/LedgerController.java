package com.enterprise.platform.controller;

import com.enterprise.platform.model.LedgerEntry;
import com.enterprise.platform.repository.LedgerEntryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerController(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @GetMapping("/entries")
    public List<LedgerEntry> entries(@RequestParam(required = false) UUID accountId) {
        if (accountId == null) {
            return ledgerEntryRepository.findAll();
        }
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }
}
