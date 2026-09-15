package com.enterprise.platform.controller;

import com.enterprise.platform.service.RazorpayService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final RazorpayService razorpayService;

    public PaymentController(RazorpayService razorpayService) {
        this.razorpayService = razorpayService;
    }

    public record CreateOrderRequest(@DecimalMin(value = "1.00") BigDecimal amount) {}
    public record VerifyRequest(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {}

    @GetMapping("/config")
    public ResponseEntity<?> config() {
        return ResponseEntity.ok(java.util.Map.of("keyId", razorpayService.getKeyId(), "mode", "test"));
    }

    @PostMapping("/order")
    public ResponseEntity<?> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        try {
            JsonNode order = razorpayService.createOrder(request.amount());
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
            boolean valid = razorpayService.verify(
                    request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature());
            if (!valid) return ResponseEntity.badRequest().body(java.util.Map.of("verified", false));
            return ResponseEntity.ok(java.util.Map.of("verified", true, "mode", "test"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("verified", false, "error", e.getMessage()));
        }
    }
}
