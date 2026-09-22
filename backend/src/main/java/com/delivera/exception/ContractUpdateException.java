package com.delivera.exception;

public class ContractUpdateException extends RuntimeException {
    public ContractUpdateException() {
        super("CONTRACT CAN'T BE UPDATED");
    }
}
