package com.enterprise.platform.controller;

import com.enterprise.platform.dto.AuthorizeRequest;
import com.enterprise.platform.dto.TransferRequest;
import com.enterprise.platform.model.Account;
import com.enterprise.platform.model.Transaction;
import com.enterprise.platform.repository.AccountRepository;
import com.enterprise.platform.repository.TransactionRepository;
import com.enterprise.platform.security.AuthUtil;
import com.enterprise.platform.security.CurrentUser;
import com.enterprise.platform.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransactionController(TransactionService transactionService,
                                  TransactionRepository transactionRepository,
                                  AccountRepository accountRepository) {
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public List<Transaction> list(@RequestParam(required = false) UUID accountId) {
        CurrentUser user = AuthUtil.current();
        if (accountId != null) {
            AccountController.requireOwnerOrAdmin(requireAccount(accountId));
            return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
        }
        if (user.isAdmin()) {
            return transactionRepository.findAllByOrderByCreatedAtDesc();
        }
        return accountRepository.findByOwnerId(user.userId()).stream()
                .flatMap(a -> transactionRepository.findByAccountIdOrderByCreatedAtDesc(a.getId()).stream())
                .toList();
    }

    @PostMapping("/authorize")
    public ResponseEntity<Transaction> authorize(@Valid @RequestBody AuthorizeRequest req) {
        AccountController.requireOwnerOrAdmin(requireAccount(req.accountId));
        return ResponseEntity.ok(transactionService.authorize(req));
    }

    @PostMapping("/transfer")
    public ResponseEntity<Transaction> transfer(@Valid @RequestBody TransferRequest req) {
        // You can only move money OUT of an account you own; the destination can
        // belong to anyone, same as a real bank transfer.
        AccountController.requireOwnerOrAdmin(requireAccount(req.fromAccountId));
        return ResponseEntity.ok(transactionService.transfer(req.fromAccountId, req.toAccountId, req.amount, req.note));
    }

    private Account requireAccount(UUID id) {
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId is required");
        return accountRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Account not found"));
    }
}
