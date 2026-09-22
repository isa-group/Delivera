package com.delivera.fms.routing.config;

/**
 * Se lanza cuando se pide una instancia que no existe en el directorio de
 * instancias del gateway.
 */
public class InstanceNotFoundException extends RuntimeException {

    public InstanceNotFoundException(String message) {
        super(message);
    }
}
