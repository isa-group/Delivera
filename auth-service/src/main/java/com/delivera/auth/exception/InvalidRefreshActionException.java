package com.delivera.auth.exception;

public class InvalidRefreshActionException extends RuntimeException {

	private static final long serialVersionUID = -2524285569334288882L;

	public InvalidRefreshActionException() {
        super("The token has been modified");
    }
}
