package com.delivera.fms.engine.core.model;

/**
 * Cliente del problema.
 *
 * @param index       posicion en la lista de clientes del problema; es el identificador que usan
 *                    las secuencias y los operadores
 * @param id          identificador externo, el que se devuelve en las rutas
 * @param matrixIndex fila y columna del cliente en la matriz de distancias
 * @param demand      unidades a entregar
 * @param service     tiempo de servicio; consume duracion de ruta pero no coste
 */
public record Customer(int index, String id, int matrixIndex, int demand, double service) {
}
