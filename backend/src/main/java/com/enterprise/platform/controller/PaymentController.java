package com.enterprise.platform.controller;

import com.enterprise.platform.model.Payment;
import com.enterprise.platform.repository.AccountRepository;
import com.enterprise.platform.security.AuthUtil;
import com.enterprise.platform.service.RazorpayService;
import com.enterprise.platform.service.TransactionService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final RazorpayService razorpayService;
    private final TransactionService transactionService;
    private final AccountRepository accountRepository;

    public PaymentController(RazorpayService razorpayService, TransactionService transactionService,
                              AccountRepository accountRepository) {
        this.razorpayService = razorpayService;
        this.transactionService = transactionService;
        this.accountRepository = accountRepository;
    }

    public record CreateOrderRequest(@DecimalMin(value = "1.00") BigDecimal amount, @NotNull UUID accountId) {}
    public record VerifyRequest(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {}

    @GetMapping("/config")
    public ResponseEntity<?> config() {
        return ResponseEntity.ok(java.util.Map.of("keyId", razorpayService.getKeyId(), "mode", "test"));
    }

    @PostMapping("/order")
    public ResponseEntity<?> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        AccountController.requireOwnerOrAdmin(accountRepository.findById(request.accountId())
                .orElseThrow(() -> new NoSuchElementException("Account not found")));
        try {
            JsonNode order = razorpayService.createOrder(request.amount(), request.accountId());
            return ResponseEntity.ok(java.util.Map.of(
                    "orderId", order.get("id").asText(),
                    "amount", order.get("amount").asLong(),
                    "currency", order.get("currency").asText(),
                    "keyId", razorpayService.getKeyId(),
                    "mode", "test"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody VerifyRequest request) {
        try {
            Payment payment = razorpayService.verify(
                    request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature());
            if (payment == null) {
                return ResponseEntity.badRequest().body(java.util.Map.of("verified", false));
            }
            AccountController.requireOwnerOrAdmin(accountRepository.findById(payment.getAccountId())
                    .orElseThrow(() -> new NoSuchElementException("Account not found")));
            transactionService.creditTopUp(payment.getAccountId(), payment.getAmount(), request.razorpayPaymentId());
            return ResponseEntity.ok(java.util.Map.of("verified", true, "mode", "test", "credited", payment.getAmount()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("verified", false, "error", e.getMessage()));
        }
    }
}
