package com.delivera.fms.engine.core.model;

import java.util.List;

/**
 * Instancia del MD-CVRP tal y como la ven los motores: depositos con sus restricciones, clientes
 * con su demanda y la matriz de distancias entre todos los nodos.
 *
 * Es el unico contrato entre el nucleo y los motores. Cada motor traduce sus DTOs a este modelo
 * una vez, al recibir la peticion, y a partir de ahi todo el calculo de costes ocurre aqui.
 */
public record RoutingProblem(List<Depot> depots, List<Customer> customers, double[][] distanceMatrix) {

    public RoutingProblem {
        depots = List.copyOf(depots);
        customers = List.copyOf(customers);
        for (int i = 0; i < depots.size(); i++) {
            if (depots.get(i).index() != i) {
                throw new IllegalArgumentException("Depot at position " + i + " has index " + depots.get(i).index());
            }
        }
        for (int i = 0; i < customers.size(); i++) {
            if (customers.get(i).index() != i) {
                throw new IllegalArgumentException("Customer at position " + i + " has index " + customers.get(i).index());
            }
        }
    }

    public int depotCount() {
        return depots.size();
    }

    public int customerCount() {
        return customers.size();
    }

    public Depot depot(int index) {
        return depots.get(index);
    }

    public Customer customer(int index) {
        return customers.get(index);
    }

    /** Distancia entre dos clientes. */
    public double distance(int customerA, int customerB) {
        return distanceMatrix[customers.get(customerA).matrixIndex()][customers.get(customerB).matrixIndex()];
    }

    /** Distancia entre un deposito y un cliente, simetrica. */
    public double distance(Depot depot, int customer) {
        return distanceMatrix[depot.matrixIndex()][customers.get(customer).matrixIndex()];
    }
}
