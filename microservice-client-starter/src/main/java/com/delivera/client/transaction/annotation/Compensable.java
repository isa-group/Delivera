package com.delivera.client.transaction.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 
  * @apiNote
  * <p><b>ONLY SYNCHRONOUS OPERATIONS.</b></p>
  *
  * <p>
  * This implementation uses {@link ThreadLocal}; therefore,
  * compensation actions must be registered and executed within
  * the same thread.
  * </p>
  *
  * <p>
  * Not compatible with {@code @Async}, {@code CompletableFuture},
  * {@code ExecutorService}, Reactor ({@code Mono}/{@code Flux}),
  * or any other asynchronous execution model unless custom
  * context propagation is implemented.
  * </p>
 * 
 * Executes the annotated method inside a compensable transaction.
 * <p>If the method finishes successfully, all registered
 * compensation actions are discarded.
 *
 * <p>If an exception is thrown, compensation actions are executed
 * in reverse registration order (LIFO).
 *
 * <p>This annotation is intended for distributed operations involving
 * external services where a traditional database transaction cannot
 * guarantee consistency.
 *
 * <p>Compensation actions may be registered using:
 *
 * <pre>
 * Compensations.registerRollBack(
 *     () -> service.deleteResource(id)
 * );
 * </pre>
 * 
 * <pre>
 * Compensations.register(
        () -> space.deleteCompany(companyId),
        () -> rollbackJobService.schedule(companyId)
    );
 * </pre>
 *
 * Example:
 *
* <pre>
 * @Compensable
 * public void createCompany() {
 *
 *     String companyId = client.createCompany();
 *
 *     Compensations.registerRollBack(
 *         () -> client.deleteCompany(companyId)
 *     );
 *
 *     createUsers();
 * }
 * </pre>
 * 
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Compensable {
}