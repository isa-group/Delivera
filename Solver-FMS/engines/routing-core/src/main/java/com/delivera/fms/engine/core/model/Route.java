package com.delivera.fms.engine.core.model;

import java.util.List;

/**
 * Una ruta ya materializada: el viaje de un vehiculo desde su deposito, por los clientes en orden,
 * y de vuelta.
 *
 * @param depot     deposito de salida y llegada
 * @param customers indices de los clientes servidos, en orden de visita
 * @param distance  longitud del ciclo deposito - clientes - deposito; es lo que suma al coste
 * @param load      demanda total servida
 * @param service   tiempo de servicio acumulado; con la distancia forma la duracion de la ruta
 */
public record Route(Depot depot, List<Integer> customers, double distance, int load, double service) {

    public double duration() {
        return distance + service;
    }
}
