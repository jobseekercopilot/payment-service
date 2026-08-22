package com.jobseekercopilot.paymentservice.service;

import com.jobseekercopilot.paymentservice.config.PaymentProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LedgerStartupVerifier implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LedgerStartupVerifier.class);

    private final PaymentProperties paymentProperties;
    private final LedgerReconciliationService reconciliationService;
    private final DocumentCreditLedgerReconciliationService documentCreditReconciliationService;

    @Override
    public void run(ApplicationArguments args) {
        if (!paymentProperties.getLedger().isVerifyOnStartup()) {
            log.warn("AI Credit ledger startup reconciliation is disabled");
            return;
        }
        int walletCount = reconciliationService.verifyAllOrThrow();
        log.info("AI Credit ledger startup reconciliation passed walletCount={}", walletCount);
        int documentWalletCount = documentCreditReconciliationService.verifyAllOrThrow();
        log.info("Document-credit ledger startup reconciliation passed walletCount={}",
                documentWalletCount);
    }
}
