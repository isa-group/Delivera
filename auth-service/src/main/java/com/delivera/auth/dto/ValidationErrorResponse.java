package com.delivera.auth.dto;

import java.util.List;

public record ValidationErrorResponse(String code, List<FieldError> errors) {

    public record FieldError(String field, String message) {
    }
}
