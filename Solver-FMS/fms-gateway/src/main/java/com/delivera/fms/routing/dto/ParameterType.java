package com.delivera.fms.routing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tipo de dato de un parametro de solver.
 *
 * Para los numeros distingue si el parametro admite decimales, que es justo lo
 * que el rango no dice: elitismCount (0-100) y crossoverProbability (0-1) se ven
 * iguales en el descriptor y no lo son. Quien programe un barrido de parametros
 * necesita ese dato para generar valores validos. BOOLEAN cubre los interruptores,
 * como activar una familia de restricciones, que no tienen rango.
 *
 * No convierte tipos: tanto el YAML de configuracion como el JSON de la peticion
 * entregan ya los valores tipados. Su trabajo es rechazar lo que no encaja.
 */
@Schema(description = "Data type of a solver parameter", enumAsRef = true)
public enum ParameterType {

    // Numero sin parte decimal. Se normaliza a Long para no acotar el rango a 32 bits.
    INTEGER,

    // Numero con decimales. Se normaliza a Double.
    DECIMAL,

    // Verdadero o falso. No admite 0/1 ni "true" en texto, para no adivinar intenciones.
    BOOLEAN;

    /**
     * Comprueba que el valor encaja con el tipo declarado y lo normaliza.
     *
     * @return el valor como {@link Long}, {@link Double} o {@link Boolean}, o null si entra null
     * @throws IllegalArgumentException si no es del tipo declarado, o si tiene
     *                                  parte decimal y el tipo declarado es INTEGER
     */
    public Object validate(Object raw) {
        if (raw == null) {
            return null;
        }
        if (this == BOOLEAN) {
            if (!(raw instanceof Boolean flag)) {
                throw new IllegalArgumentException("expected true or false, received '" + raw + "'");
            }
            return flag;
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
