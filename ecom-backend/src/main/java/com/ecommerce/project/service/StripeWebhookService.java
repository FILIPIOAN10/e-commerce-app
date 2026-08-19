package com.ecommerce.project.service;

public interface StripeWebhookService {
    void handleWebhook(String payload, String signatureHeader);
}
