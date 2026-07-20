package com.delivera.auth.exception;

public class InvalidCredentialsException extends RuntimeException {

	private static final long serialVersionUID = -2524285569334288882L;

	public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
