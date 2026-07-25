package com.jobseekercopilot.paymentservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.paymentservice.dto.CommitReservationRequest;
import com.jobseekercopilot.paymentservice.dto.ConfirmStripePurchaseRequest;
import com.jobseekercopilot.paymentservice.dto.CreateReservationRequest;
import com.jobseekercopilot.paymentservice.dto.DemoPurchaseRequest;
import com.jobseekercopilot.paymentservice.dto.EstimateRequest;
import com.jobseekercopilot.paymentservice.dto.ReleaseReservationRequest;
import com.jobseekercopilot.paymentservice.repository.AiTokenReservationRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenTransactionRepository;
import com.jobseekercopilot.paymentservice.repository.AiTokenWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {
    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    private static final String OWNER_HEADER = "X-Payment-Owner";
    private static final String PAYMENT_GATEWAY_TOKEN =
            "payment-gateway-test-token-0000000000000001";
    private static final String CV_COVER_LETTER_TOKEN =
            "cv-cover-letter-test-token-000000000000001";
    private static final String STRIPE_GATEWAY_TOKEN =
            "stripe-gateway-test-token-0000000000000001";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AiTokenWalletRepository walletRepository;
    @Autowired private AiTokenTransactionRepository transactionRepository;
    @Autowired private AiTokenReservationRepository reservationRepository;

    @BeforeEach
    void clean() {
        reservationRepository.deleteAll();
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void walletCreatedForNewUserAndStarterTokensGrantedOnce() throws Exception {
        mockMvc.perform(get("/api/v1/payments/wallet").with(paymentGateway("user-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-123"))
                .andExpect(jsonPath("$.balanceTokens").value(20000))
                .andExpect(jsonPath("$.freeTrialGranted").value(true));

        mockMvc.perform(get("/api/v1/payments/wallet").with(paymentGateway("user-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceTokens").value(20000));

        mockMvc.perform(get("/api/v1/payments/transactions").with(paymentGateway("user-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(1)))
                .andExpect(jsonPath("$.transactions[0].transactionType").value("FREE_TRIAL_GRANTED"));
    }

    @Test
    void pricingPlansReturned() throws Exception {
        mockMvc.perform(get("/api/v1/payments/pricing")
                        .header(SERVICE_TOKEN_HEADER, PAYMENT_GATEWAY_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans", hasSize(3)))
                .andExpect(jsonPath("$.plans[0].id").value("starter"))
                .andExpect(jsonPath("$.plans[0].tokenAmount").value(100000))
                .andExpect(jsonPath("$.plans[0].priceGbpPence").value(799))
                .andExpect(jsonPath("$.plans[1].name").value("Standard"))
                .andExpect(jsonPath("$.plans[1].tokenAmount").value(250000))
                .andExpect(jsonPath("$.plans[1].priceGbpPence").value(1699))
                .andExpect(jsonPath("$.plans[2].name").value("Pro"))
                .andExpect(jsonPath("$.plans[2].tokenAmount").value(600000))
                .andExpect(jsonPath("$.plans[2].priceGbpPence").value(3499));
    }

    @Test
    void demoPurchaseAddsTokensAndCreatesNewestTransaction() throws Exception {
        mockMvc.perform(get("/api/v1/payments/wallet").with(paymentGateway("user-123")))
                .andExpect(status().isOk());

        DemoPurchaseRequest request = new DemoPurchaseRequest();
        request.setPricingPlanId("starter");
        mockMvc.perform(post("/api/v1/payments/demo-purchase")
                        .with(paymentGateway("user-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wallet.balanceTokens").value(120000))
                .andExpect(jsonPath("$.wallet.lifetimePurchasedTokens").value(100000))
                .andExpect(jsonPath("$.transaction.transactionType").value("DEMO_PURCHASE"))
                .andExpect(jsonPath("$.transaction.tokenAmount").value(100000));

        mockMvc.perform(get("/api/v1/payments/transactions").with(paymentGateway("user-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(2)))
                .andExpect(jsonPath("$.transactions[0].transactionType").value("DEMO_PURCHASE"))
                .andExpect(jsonPath("$.transactions[1].transactionType").value("FREE_TRIAL_GRANTED"));
    }

    @Test
    void confirmStripePurchaseAddsTokensAndCreatesPurchaseTransaction() throws Exception {
        ConfirmStripePurchaseRequest request = new ConfirmStripePurchaseRequest();
        request.setUserId("user-123");
        request.setPricingPlanId("starter");
        request.setTokenAmount(100000);
        request.setStripeSessionId("cs_test_123");
        request.setStripePaymentIntentId("pi_test_123");

        mockMvc.perform(post("/api/v1/payments/confirm-stripe-purchase")
                        .with(stripeGateway("user-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wallet.balanceTokens").value(120000))
                .andExpect(jsonPath("$.wallet.lifetimePurchasedTokens").value(100000))
                .andExpect(jsonPath("$.transaction.transactionType").value("PURCHASE"))
                .andExpect(jsonPath("$.transaction.referenceType").value("STRIPE_CHECKOUT_SESSION"))
                .andExpect(jsonPath("$.transaction.referenceId").value("cs_test_123"));
    }

    @Test
    void confirmStripePurchaseIsIdempotentForSameStripeSession() throws Exception {
        ConfirmStripePurchaseRequest request = new ConfirmStripePurchaseRequest();
        request.setUserId("user-123");
        request.setPricingPlanId("starter");
        request.setTokenAmount(100000);
        request.setStripeSessionId("cs_test_duplicate");

        mockMvc.perform(post("/api/v1/payments/confirm-stripe-purchase")
                        .with(stripeGateway("user-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wallet.balanceTokens").value(120000));

        mockMvc.perform(post("/api/v1/payments/confirm-stripe-purchase")
                        .with(stripeGateway("user-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wallet.balanceTokens").value(120000))
                .andExpect(jsonPath("$.transaction.referenceId").value("cs_test_duplicate"));

        mockMvc.perform(get("/api/v1/payments/transactions").with(paymentGateway("user-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(2)))
                .andExpect(jsonPath("$.transactions[0].transactionType").value("PURCHASE"))
                .andExpect(jsonPath("$.transactions[1].transactionType").value("FREE_TRIAL_GRANTED"));
    }

    @Test
    void confirmStripePurchaseRejectsMismatchedPlanTokens() throws Exception {
        ConfirmStripePurchaseRequest request = new ConfirmStripePurchaseRequest();
        request.setUserId("user-123");
        request.setPricingPlanId("starter");
        request.setTokenAmount(1);
        request.setStripeSessionId("cs_test_bad_amount");

        mockMvc.perform(post("/api/v1/payments/confirm-stripe-purchase")
                        .with(stripeGateway("user-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Stripe purchase token amount does not match pricing plan"));
    }

    @Test
    void estimateReturnsCanAffordTrueOrFalse() throws Exception {
        EstimateRequest affordable = new EstimateRequest();
        affordable.setFeature("CV_AND_COVER_LETTER_GENERATION");
        affordable.setEstimatedInputTokens(4000);
        affordable.setEstimatedOutputTokens(3000);

        mockMvc.perform(post("/api/v1/payments/estimate")
                        .with(paymentGateway("user-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(affordable)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedTotalTokens").value(7000))
                .andExpect(jsonPath("$.estimatedCostTokens").value(7000))
                .andExpect(jsonPath("$.userBalanceTokens").value(20000))
                .andExpect(jsonPath("$.canAfford", is(true)));

        EstimateRequest expensive = new EstimateRequest();
        expensive.setFeature("CV_AND_COVER_LETTER_GENERATION");
        expensive.setEstimatedInputTokens(40000);
        expensive.setEstimatedOutputTokens(40000);

        mockMvc.perform(post("/api/v1/payments/estimate")
                        .with(paymentGateway("user-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expensive)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canAfford", is(false)));
    }

    @Test
    void reservationCommitSpendsActualTokensAndReleasesUnusedHold() throws Exception {
        CreateReservationRequest reservationRequest = new CreateReservationRequest();
        reservationRequest.setFeature("CV_AND_COVER_LETTER_GENERATION");
        reservationRequest.setEstimatedTokens(10000);
        reservationRequest.setReferenceType("JOB_APPLICATION");
        reservationRequest.setReferenceId("job-123");

        String reservationJson = mockMvc.perform(post("/api/v1/payments/reservations")
                        .with(cvCoverLetter("user-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reservationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedTokens").value(10000))
                .andExpect(jsonPath("$.balanceAfterReservation").value(10000))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String reservationId = objectMapper.readTree(reservationJson).get("reservationId").asText();

        CommitReservationRequest commitRequest = new CommitReservationRequest();
        commitRequest.setActualTokens(7300L);
        commitRequest.setProvider("OPENAI");
        commitRequest.setModel("gpt-4.1-mini");
        commitRequest.setInputTokens(4200L);
        commitRequest.setOutputTokens(3100L);
        commitRequest.setDescription("CV and cover letter generation");

        mockMvc.perform(post("/api/v1/payments/reservations/{reservationId}/commit", reservationId)
                        .with(cvCoverLetter("user-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commitRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.committedTokens").value(7300))
                .andExpect(jsonPath("$.releasedTokens").value(2700))
                .andExpect(jsonPath("$.wallet.balanceTokens").value(12700))
                .andExpect(jsonPath("$.wallet.lifetimeSpentTokens").value(7300))
                .andExpect(jsonPath("$.spendTransaction.transactionType").value("SPEND"));

        mockMvc.perform(get("/api/v1/payments/transactions").with(paymentGateway("user-123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(4)))
                .andExpect(jsonPath("$.transactions[0].transactionType").value("RESERVATION_RELEASED"))
                .andExpect(jsonPath("$.transactions[1].transactionType").value("SPEND"))
                .andExpect(jsonPath("$.transactions[2].transactionType").value("RESERVATION"))
                .andExpect(jsonPath("$.transactions[3].transactionType").value("FREE_TRIAL_GRANTED"));
    }

    @Test
    void reservationReleaseRestoresHeldTokens() throws Exception {
        CreateReservationRequest reservationRequest = new CreateReservationRequest();
        reservationRequest.setFeature("CV_AND_COVER_LETTER_GENERATION");
        reservationRequest.setEstimatedTokens(10000);

        String reservationJson = mockMvc.perform(post("/api/v1/payments/reservations")
                        .with(cvCoverLetter("user-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reservationRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String reservationId = objectMapper.readTree(reservationJson).get("reservationId").asText();

        ReleaseReservationRequest releaseRequest = new ReleaseReservationRequest();
        releaseRequest.setReason("LLM generation failed");
        mockMvc.perform(post("/api/v1/payments/reservations/{reservationId}/release", reservationId)
                        .with(cvCoverLetter("user-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(releaseRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedTokens").value(10000))
                .andExpect(jsonPath("$.wallet.balanceTokens").value(20000));
    }

    @Test
    void reservationReturnsPaymentRequiredWhenBalanceCannotCoverEstimate() throws Exception {
        CreateReservationRequest reservationRequest = new CreateReservationRequest();
        reservationRequest.setFeature("CV_AND_COVER_LETTER_GENERATION");
        reservationRequest.setEstimatedTokens(60000);

        mockMvc.perform(post("/api/v1/payments/reservations")
                        .with(cvCoverLetter("user-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reservationRequest)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.message").value("Insufficient AI Credit"));
    }

    @Test
    void missingServiceIdentityReturnsControlledError() throws Exception {
        mockMvc.perform(get("/api/v1/payments/wallet"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SERVICE_AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Valid service authentication is required."));
    }

    @Test
    void forgedServiceIdentityFailsClosed() throws Exception {
        mockMvc.perform(get("/api/v1/payments/wallet")
                        .header(SERVICE_TOKEN_HEADER, "forged")
                        .header(OWNER_HEADER, "user-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SERVICE_AUTHENTICATION_REQUIRED"));
    }

    @Test
    void callerSelectedLegacyIdentityIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/payments/wallet")
                        .with(paymentGateway("owner-123"))
                        .header("X-User-Id", "victim-456"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CALLER_IDENTITY_REJECTED"));
    }

    @Test
    void serviceTokensAreAuthorizedOnlyForTheirOperations() throws Exception {
        mockMvc.perform(post("/api/v1/payments/reservations")
                        .with(paymentGateway("user-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"feature":"CV_AND_COVER_LETTER_GENERATION","estimatedTokens":1000}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_AUTHORIZED"));
    }

    @Test
    void conflictingStripeOwnerCannotGrantCredit() throws Exception {
        ConfirmStripePurchaseRequest request = new ConfirmStripePurchaseRequest();
        request.setUserId("attacker-selected");
        request.setPricingPlanId("starter");
        request.setTokenAmount(100000);
        request.setStripeSessionId("cs_test_owner_mismatch");

        mockMvc.perform(post("/api/v1/payments/confirm-stripe-purchase")
                        .with(stripeGateway("authenticated-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Payment owner does not match authenticated context"));
        org.assertj.core.api.Assertions.assertThat(walletRepository.count()).isZero();
    }

    @Test
    void crossUserReservationLookupIsNonEnumerating() throws Exception {
        CreateReservationRequest reservationRequest = new CreateReservationRequest();
        reservationRequest.setFeature("CV_AND_COVER_LETTER_GENERATION");
        reservationRequest.setEstimatedTokens(1000);
        String reservationJson = mockMvc.perform(post("/api/v1/payments/reservations")
                        .with(cvCoverLetter("owner-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reservationRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String reservationId = objectMapper.readTree(reservationJson).get("reservationId").asText();

        CommitReservationRequest request = new CommitReservationRequest();
        request.setActualTokens(500L);
        mockMvc.perform(post("/api/v1/payments/reservations/{reservationId}/commit", reservationId)
                        .with(cvCoverLetter("other-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Reservation was not found"));
    }

    private RequestPostProcessor paymentGateway(String owner) {
        return serviceIdentity(PAYMENT_GATEWAY_TOKEN, owner);
    }

    private RequestPostProcessor cvCoverLetter(String owner) {
        return serviceIdentity(CV_COVER_LETTER_TOKEN, owner);
    }

    private RequestPostProcessor stripeGateway(String owner) {
        return serviceIdentity(STRIPE_GATEWAY_TOKEN, owner);
    }

    private RequestPostProcessor serviceIdentity(String token, String owner) {
        return request -> {
            request.addHeader(SERVICE_TOKEN_HEADER, token);
            request.addHeader(OWNER_HEADER, owner);
            return request;
        };
    }
}
