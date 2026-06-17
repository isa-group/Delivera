package com.delivera.auth.exception;

public class EmailAlreadyExistsException extends RuntimeException {

	private static final long serialVersionUID = 4173701214004876277L;

	public EmailAlreadyExistsException() {
        super("Email already exists");
    }
}
