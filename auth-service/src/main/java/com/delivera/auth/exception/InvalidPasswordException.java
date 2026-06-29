package com.delivera.auth.exception;

import lombok.Getter;

@Getter
public class InvalidPasswordException extends RuntimeException {

	private static final long serialVersionUID = 5315986326937323416L;

	private final String code;

	public InvalidPasswordException(String code) {
		super(code);
		this.code = code;
	}
}
