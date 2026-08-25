package com.delivera.client.transaction.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import com.delivera.client.transaction.compensation.Compensations;
import com.delivera.client.transaction.exception.CompensableValidationException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
public class CompensableAspect {

    @Around("@annotation(com.delivera.client.transaction.annotation.Compensable)")
    public Object execute(
        ProceedingJoinPoint pjp
    ) throws Throwable {

        Compensations.init();
        log.debug(
            "TRANSACTION-TX [{}]",
            Compensations.getTransactionId()
        );
        try {

            return pjp.proceed();

        } catch (CompensableValidationException ex) {
            log.debug(
                "VALIDATION ERROR-TX [{}]",
                Compensations.getTransactionId()
            );
            throw ex;

        } catch (Exception ex) {
            log.debug(
                "ERROR-TX [{}]",
                Compensations.getTransactionId()
            );
            Compensations.rollback();

            throw ex;

        } finally {
            log.debug(
                "EXIT-TX [{}]",
                Compensations.getTransactionId()
            );
            Compensations.clear();
        }
    }
}