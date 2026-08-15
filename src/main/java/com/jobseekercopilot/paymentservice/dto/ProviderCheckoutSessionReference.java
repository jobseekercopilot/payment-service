package com.jobseekercopilot.paymentservice.dto;

import java.util.UUID;

public record ProviderCheckoutSessionReference(UUID orderId, String providerSessionId) {}
