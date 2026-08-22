package com.jobseekercopilot.paymentservice.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentCreditWalletProvisioner {
    private final DocumentCreditWalletCreationTransaction creationTransaction;

    public UUID ensureWallet(String owner) {
        try {
            return creationTransaction.findOrCreate(owner);
        } catch (DataIntegrityViolationException race) {
            return creationTransaction.findWalletId(owner).orElseThrow(() -> race);
        }
    }
}
