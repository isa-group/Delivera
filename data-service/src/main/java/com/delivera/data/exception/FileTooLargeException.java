package com.delivera.data.exception;

public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException() {
        super("File exceeds the maximum allowed size");
    }
}
