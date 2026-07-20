package com.delivera.auth.exception;

public class UserNotFoundException extends RuntimeException {

	private static final long serialVersionUID = -1985102079927585806L;

	public UserNotFoundException() {
        super("User not found");
    }
}
