package com.jobseekercopilot.paymentservice;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiExportTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void exportOpenApi() throws Exception {
        String spec = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Files.writeString(Path.of("target/openapi.json"), spec);
        if (Boolean.getBoolean("payment.updateContract")) {
            Files.writeString(Path.of("contracts/openapi.json"), spec);
        }
    }

    @Test
    void defaultCommercialConfigurationIsVisibleButCheckoutAndPromotionFailClosed()
            throws Exception {
        String token = "payment-gateway-test-token-0000000000000001";
        mockMvc.perform(get("/api/v2/payments/checkout-readiness")
                        .header("X-Service-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutAvailable").value(false))
                .andExpect(jsonPath("$.code").value("PAYMENTS_DISABLED"));
        mockMvc.perform(get("/api/v2/payments/catalog")
                        .header("X-Service-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxStatus").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.promotion.enabled").value(false))
                .andExpect(jsonPath("$.promotion.status").value("DISABLED"));
    }
}
