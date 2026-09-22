package com.delivera.client.space.service;

import com.delivera.client.transaction.annotation.Compensable;
import com.delivera.client.transaction.compensation.Compensations;

import lombok.RequiredArgsConstructor;

/**
 * 
 * AbstractSpaceFeature
 * <p>To use this class It's required a <b>SPACE API-KEY</b> of type <b>"MANAGMENT"</b>.</p>
 */
@RequiredArgsConstructor
public abstract class AbstractSpaceFeature {

    protected final SpaceManagment space;

    protected abstract String feature();
    
    /**
     * @param userId
     * @param quantity
     * @param server
     */
    public void consumption(String userId, Integer quantity, boolean server) {
        space.executeConsumptionWithMaxLimit(
            userId, 
            feature(), 
            quantity, 
            server
        );
    }
    
    /**
     * <p><b>
     * It's required the annotation {@link Compensable}
     * </p></b>
     * @param userId
     * @param quantity
     * @param server
     */
    public void consumptionWithRollBack(String userId, Integer quantity, boolean server) {
        consumption(userId, quantity, server);
        inverseConsumption(userId, quantity, server);
    }


    private void inverseConsumption(String userId, Integer quantity, boolean server) {
        Compensations.registerRollback(() -> 
            consumption(userId,-quantity,!server)
        );
    }

    private void checkPositiveQuantity(Integer quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("QUANTITY MUST BE A POSITIVE NUMBER OR ZERO");
        }
    }

    public void add(String userId) {
        consumption(userId, 1, false);
    }

    public void delete(String userId) {
        consumption(userId, -1, true);
    }

    public void deleteAll(String userId, Integer quantity) {
        checkPositiveQuantity(quantity);
        consumption(userId, -quantity, true);
    }

    public void addAll(String userId, Integer quantity) {
        checkPositiveQuantity(quantity);
        consumption(userId, quantity, true);
    }

    /**
     * 
     * <p><b>
     * It's required the annotation {@link Compensable}
     * </p></b>
     * @param userId
     * 
     */
    public void addWithRollBack(String userId) {
        consumptionWithRollBack(userId, 1, false);
    }

     /**
     * 
     * <p><b>
     * It's required the annotation {@link Compensable}
     * </p></b>
     * @param userId
     * 
     */
    public void deleteWithRollBack(String userId) {
        consumptionWithRollBack(userId, -1, true);
    }
    /**
     * <p><b>
     * It's required the annotation {@link Compensable}
     * </p></b>
     * @param userId
     * @param quantity
     */
    public void deleteAllWithRollBack(String userId, Integer quantity) {
        checkPositiveQuantity(quantity);
        consumptionWithRollBack(userId, -quantity, true);
    }

    /**
     * <p><b>
     * It's required the annotation {@link Compensable}
     * </p></b>
     * @param userId
     * @param quantity
     */
    public void addAllWithRollBack(String userId, Integer quantity) {
        checkPositiveQuantity(quantity);
        consumptionWithRollBack(userId, quantity, true);
    }

   



}
