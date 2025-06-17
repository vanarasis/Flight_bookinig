package com.flightbooking.flightbooking.Controllers;
import com.flightbooking.flightbooking.Services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class WebhookController {
    private final PaymentService paymentService;

    @Value("${razorpay.webhook.secret:default_webhook_secret}")
    private String webhookSecret;

    @PostMapping("/razorpay")
    public ResponseEntity<Map<String, Object>> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        try {
            log.info("Received Razorpay webhook");
            log.debug("Webhook payload: {}", payload);
            log.debug("Webhook signature: {}", signature);

            // For development, allow webhooks without signature verification
            if (signature == null || signature.isEmpty()) {
                log.warn("No signature provided in webhook - allowing for development");
            } else {
                // Verify webhook signature in production
                if (!verifyWebhookSignature(payload, signature)) {
                    log.warn("Invalid webhook signature received");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("success", false, "message", "Invalid signature"));
                }
            }

            // Process the webhook
            paymentService.processWebhook(payload, signature);

            log.info("Webhook processed successfully");
            return ResponseEntity.ok(Map.of("success", true, "message", "Webhook processed successfully"));

        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Webhook processing failed: " + e.getMessage()));
        }
    }

    private boolean verifyWebhookSignature(String payload, String signature) {
        try {
            // Create HMAC SHA256 hash
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            String expectedSignature = hexString.toString();

            // Compare signatures
            return signature.equals(expectedSignature);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }
}
