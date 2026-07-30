package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tipo de dato de un parametro de solver.
 *
 * Solo distingue si el parametro admite decimales, que es justo lo que el rango
 * no dice: elitismCount (0-100) y crossoverProbability (0-1) se ven iguales en el
 * descriptor y no lo son. Quien programe un barrido de parametros necesita ese
 * dato para generar valores validos.
 *
 * No convierte tipos: tanto el YAML de configuracion como el JSON de la peticion
 * entregan ya los numeros tipados. Su trabajo es rechazar lo que no encaja.
 */
@Schema(description = "Tipo de dato de un parametro de solver", enumAsRef = true)
public enum ParameterType {

    // Numero sin parte decimal. Se normaliza a Long para admitir semillas de 64 bits.
    INTEGER,

    // Numero con decimales. Se normaliza a Double.
    DECIMAL;

    /**
     * Comprueba que el valor encaja con el tipo declarado y lo normaliza.
     *
     * @return el valor como {@link Long} o {@link Double}, o null si entra null
     * @throws IllegalArgumentException si no es un numero, o si tiene parte
     *                                  decimal y el tipo declarado es INTEGER
     */
    public Object validate(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("expected a number, received '" + raw + "'");
        }
        if (this == DECIMAL) {
            return number.doubleValue();
        }

        // Truncar en silencio un 12.7 a 12 es peor que rechazarlo: el motor
        // ejecutaria una configuracion que nadie ha pedido.
        double value = number.doubleValue();
        if (value != Math.rint(value)) {
            throw new IllegalArgumentException("expected an integer, received " + raw);
        }
        return number.longValue();
    }
}
