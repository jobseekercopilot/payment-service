package com.jobseekercopilot.paymentservice.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletProvisioner {
    private final WalletCreationTransaction creationTransaction;

    public UUID ensureWallet(String userId) {
        try {
            return creationTransaction.findOrCreate(userId);
        } catch (DataIntegrityViolationException creationRace) {
            return creationTransaction.findWalletId(userId)
                    .orElseThrow(() -> creationRace);
        }
    }
}
