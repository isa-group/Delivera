package com.delivera.fms.engine.core.model;

/**
 * Deposito del problema junto con las restricciones de sus rutas.
 *
 * Las restricciones viven aqui y no en el motor porque son parte de la funcion objetivo: dos
 * motores que las leyeran de sitios distintos podrian medir costes distintos para la misma solucion.
 *
 * @param index         posicion en la lista de depositos del problema
 * @param id            identificador externo, el que se devuelve en las rutas
 * @param matrixIndex   fila y columna del deposito en la matriz de distancias
 * @param capacity      carga maxima de una ruta; {@link #UNLIMITED} si no hay vehiculos declarados
 * @param fleet         numero de rutas disponibles; {@link #UNLIMITED} si no hay vehiculos declarados
 * @param durationLimit duracion maxima de una ruta (distancia mas servicio);
 *                      {@link Double#MAX_VALUE} si no hay limite
 */
public record Depot(int index, String id, int matrixIndex, int capacity, int fleet, double durationLimit) {

    public static final int UNLIMITED = Integer.MAX_VALUE;

    public boolean hasFleetLimit() {
        return fleet != UNLIMITED;
    }
}
