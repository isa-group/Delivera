package com.delivera.auth.exception;

public class InvalidRefreshTokenException extends RuntimeException {

	private static final long serialVersionUID = -2524285569334288882L;

	public InvalidRefreshTokenException() {
        super("Invalid Refresh Token");
    }
}
