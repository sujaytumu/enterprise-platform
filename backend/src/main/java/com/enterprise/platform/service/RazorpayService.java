package com.enterprise.platform.service;

import com.enterprise.platform.model.Payment;
import com.enterprise.platform.repository.PaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RazorpayService {
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${razorpay.key-id:}")
    private String keyId;

    @Value("${razorpay.key-secret:}")
    private String keySecret;

    public RazorpayService(PaymentRepository paymentRepository, ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;
    }

    public String getKeyId() {
        return keyId;
    }

    public JsonNode createOrder(BigDecimal amount, UUID accountId) throws Exception {
        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new IllegalStateException("Razorpay test credentials are not configured");
        }
        if (amount == null || amount.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Amount must be at least INR 1");
        }

        long paise = amount.movePointRight(2).longValueExact();
        String receipt = "rcpt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String body = "{\"amount\":" + paise + ",\"currency\":\"INR\",\"receipt\":\"" + receipt + "\"}";
        String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.razorpay.com/v1/orders"))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Razorpay order creation failed: " + response.body());
        }

        JsonNode order = objectMapper.readTree(response.body());
        Payment payment = new Payment();
        payment.setRazorpayOrderId(order.get("id").asText());
        payment.setAccountId(accountId);
        payment.setAmount(amount);
        payment.setCurrency("INR");
        payment.setStatus("CREATED");
        paymentRepository.save(payment);
        return order;
    }

    /** Returns the verified Payment, or null if the signature does not match. */
    public Payment verify(String orderId, String paymentId, String signature) throws Exception {
        Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Razorpay order"));

        String payload = orderId + "|" + paymentId;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

        boolean valid = java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            return null;
        }
        payment.setRazorpayPaymentId(paymentId);
        payment.setSignatureVerified(true);
        payment.setStatus("VERIFIED");
        return paymentRepository.save(payment);
    }
}
