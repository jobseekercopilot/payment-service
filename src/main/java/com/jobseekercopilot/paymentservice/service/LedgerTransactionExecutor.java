package com.jobseekercopilot.paymentservice.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerTransactionExecutor {
    @Transactional
    public <T> T execute(Supplier<T> operation) {
        return operation.get();
    }

    @Transactional(readOnly = true)
    public <T> T read(Supplier<T> operation) {
        return operation.get();
    }
}
