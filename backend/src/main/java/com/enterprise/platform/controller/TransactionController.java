package com.enterprise.platform.controller;

import com.enterprise.platform.dto.AuthorizeRequest;
import com.enterprise.platform.model.Transaction;
import com.enterprise.platform.repository.TransactionRepository;
import com.enterprise.platform.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;

    public TransactionController(TransactionService transactionService,
                                  TransactionRepository transactionRepository) {
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
    }

    @GetMapping
    public List<Transaction> list(@RequestParam(required = false) UUID accountId) {
        return accountId != null
                ? transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
                : transactionRepository.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/authorize")
    public ResponseEntity<Transaction> authorize(@Valid @RequestBody AuthorizeRequest req) {
        return ResponseEntity.ok(transactionService.authorize(req));
    }
}
