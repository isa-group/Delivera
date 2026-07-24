package com.delivera.fms.routing.config;

/**
 * Se lanza cuando se solicita un solver que no esta registrado en la
 * configuracion del gateway.
 */
public class SolverNotFoundException extends RuntimeException {

    public SolverNotFoundException(String message) {
        super(message);
    }
}
