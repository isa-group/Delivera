package com.delivera.client.transaction.compensation;

import com.delivera.client.transaction.annotation.Compensable;

/**
 * Utility class used to register compensation actions
 * within an active {@link Compensable} transaction.
 *
 * <p>Compensations are executed when the enclosing
 * compensable transaction fails.
 *
 * <p>Actions are executed in reverse registration order.
 *
 * Example:
 *
 * <pre>
 * Compensations.register(
 *     () -> space.deleteCompany(companyId)
 * );
 * </pre>
 */
public final class Compensations {

    private static final ThreadLocal<CompensationContext>
        CONTEXT = new ThreadLocal<>();

    private Compensations() {}

    public static void registerRollback(
        Runnable rollback
    ) {
        CompensationContext context = assertContext();

        context.register(
            CompensationAction.builder()
                .rollback(rollback)
                .build()
        );
    }

    public static String getTransactionId() {
        CompensationContext context = assertContext();
        return context.getTransactionId();
    }

    public static void register(
        Runnable rollback, 
        Runnable rollbackFailure
    ) {
        CompensationContext context = assertContext();
        context.register(
            CompensationAction.builder()
                .rollback(rollback)
                .rollbackFailure(rollbackFailure)
                .build()
        );
    }

    public static void register(CompensationAction action) {
        CompensationContext context = assertContext();
        context.register(action);
    }

    public static void idempotencyKey(String idempotencyKey) {
        CompensationContext context = assertContext();
        context.idempotencyKey(idempotencyKey);
    }

    public static void init() {
        CONTEXT.set(
            new CompensationContext()
        );
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static void rollback() {

        CompensationContext context =
            CONTEXT.get();

        if (context != null) {
            context.rollback();
        }
    }

    private static CompensationContext assertContext() {
        CompensationContext context =
            CONTEXT.get();

        if (context == null) {
            throw new IllegalStateException(
                "No active compensable transaction"
            );
        }

        return context;

    }
}