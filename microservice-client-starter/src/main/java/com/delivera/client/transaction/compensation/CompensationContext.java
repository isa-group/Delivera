package com.delivera.client.transaction.compensation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class CompensationContext {

    private final String transactionId = UUID.randomUUID().toString();

    private String idempotencyKey;

    private final Deque<CompensationAction> rollbacks =
        new ArrayDeque<>();

    public void idempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void register(CompensationAction rollback) {
        rollbacks.push(rollback);
    }

    public void register(Runnable rollback) {
        rollbacks.push(
            CompensationAction.builder()
                .rollback(rollback)
                .build()
        );
    }

    public void register(Runnable rollback, Runnable rollbackFailure) {
        rollbacks.push(
            CompensationAction.builder()
                .rollback(rollback)
                .rollbackFailure(rollbackFailure)
                .build()
        );
    }

    public void rollback() {

        while (!rollbacks.isEmpty()) {
            CompensationAction action =  rollbacks.pop();
            try {
                if (action.getRollback() != null) {
                    action.getRollback().run();
                }
            } catch (Exception ex) {
                log.error(
                    "FAILED_ROLLBACK-TX [{}]",
                    transactionId, 
                    ex
                );
                if (action.getRollbackFailure() != null){
                    try {
                        action.getRollbackFailure().run();
                    } catch (Exception ex2) {
                        log.error(
                            "FAILED_ROLLBACK_FAILURE-TX [{}]",
                            transactionId, 
                            ex2

                        );
                    }
                   
                }
            }
        }
    }
}
