package com.delivera.auth.exception;

import com.delivera.auth.controller.AuthController;
import com.delivera.auth.dto.ErrorResponse;
import com.delivera.auth.dto.ValidationErrorResponse;
import com.delivera.client.exception.ApiException;
import com.delivera.client.exception.ClientException;
import com.delivera.client.exception.NetworkException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;

import org.hibernate.service.spi.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final AuthController authController;

    @Autowired
    public GlobalExceptionHandler(AuthController authController) {
        this.authController = authController;
    }

    private record Mapping(HttpStatus status, String code) {}

    private static final Map<Class<?>, Mapping> ERRORS = Map.ofEntries(
        Map.entry(InvalidCredentialsException.class,      new Mapping(UNAUTHORIZED,         "INVALID_CREDENTIALS")),
        Map.entry(UserNotFoundException.class,            new Mapping(NOT_FOUND,            "USER_NOT_FOUND")),
        Map.entry(EmailAlreadyExistsException.class,      new Mapping(CONFLICT,             "EMAIL_ALREADY_EXISTS")),
        Map.entry(ForbiddenException.class,               new Mapping(FORBIDDEN,            "FORBIDDEN")),
        Map.entry(UsernameAlreadyExistsException.class,   new Mapping(CONFLICT,             "USERNAME_ALREADY_EXISTS")),
        Map.entry(RateLimitExceededException.class,       new Mapping(TOO_MANY_REQUESTS,    "RATE_LIMIT_EXCEEDED"))
    );

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleKnown(RuntimeException ex) {
        Mapping m = ERRORS.get(ex.getClass());
        if (m == null) {
            m = ERRORS.entrySet().stream()
                    .filter(e -> e.getKey().isAssignableFrom(ex.getClass()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        if (m == null) {
            log.error("Unhandled runtime exception: {}", ex.getMessage(), ex);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR"));
        }
        log.warn("{}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity.status(m.status()).body(new ErrorResponse(m.code()));
    }

    // InvalidPasswordException tiene código dinámico — no encaja en el mapa
    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(InvalidPasswordException ex) {
        log.warn("Password validation failed: {}", ex.getCode());
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getCode()));
    }

    // DataIntegrityViolationException requiere inspección del cause para distinguir casos
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof SQLException sqlEx) {
                ResponseEntity<ErrorResponse> typed = resolveSqlCause(sqlEx);
                if (typed != null) return typed;
            }
            cause = cause.getCause();
        }
        log.error("Data integrity violation: {}", ex.getMessage(), ex);
        return ResponseEntity.status(CONFLICT).body(new ErrorResponse("DATA_INTEGRITY_ERROR"));
    }

    private ResponseEntity<ErrorResponse> resolveSqlCause(SQLException sqlEx) {
        String sqlState = sqlEx.getSQLState();
        String msg = sqlEx.getMessage();
        if ("23505".equals(sqlState)) {
            String code = resolveUniqueViolation(msg);
            if (code != null) {
                log.warn("Unique violation: {}", msg);
                return ResponseEntity.status(CONFLICT).body(new ErrorResponse(code));
            }
        }
        return null;
    }

    private static String resolveUniqueViolation(String msg) {
        if (msg == null) return null;
        String lower = msg.toLowerCase();
        if (lower.contains("users_email")) return "EMAIL_ALREADY_EXISTS";
        if (lower.contains("users_username")) return "USERNAME_ALREADY_EXISTS";
        return null;
    }


    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Method argument type mismatch: {}={}", ex.getName(), ex.getValue());
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_PARAMETER"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        log.warn("Malformed request body: {}", cause.getClass().getSimpleName());
        if (cause instanceof InvalidFormatException ife && isNumericType(ife.getTargetType())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_NUMBER_FORMAT"));
        }
        return ResponseEntity.badRequest().body(new ErrorResponse("MALFORMED_REQUEST"));
    }

    private static boolean isNumericType(Class<?> t) {
        return t != null && (Number.class.isAssignableFrom(t)
                || t == int.class || t == long.class || t == double.class || t == float.class);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());
        return ResponseEntity.status(METHOD_NOT_ALLOWED).body(new ErrorResponse("METHOD_NOT_ALLOWED"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        log.warn("No resource found: {}", ex.getMessage());
        return ResponseEntity.status(NOT_FOUND).body(new ErrorResponse("NOT_FOUND"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ValidationErrorResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ValidationErrorResponse.FieldError(
                        e.getField(),
                        e.getDefaultMessage() != null ? e.getDefaultMessage() : "Invalid value"
                ))
                .toList();
        log.debug("Validation errors: {}", errors);
        return ResponseEntity.badRequest().body(new ValidationErrorResponse("VALIDATION_ERROR", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR"));
    }

    
    @ExceptionHandler(NetworkException.class)
    public ResponseEntity<?> handleNetwork(NetworkException ex) {
        return ResponseEntity.status(503).body(new ErrorResponse("SERVICE_UNAVAILABLE"));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleApiException(ApiException ex) {
        return ResponseEntity.status(503).body(new ErrorResponse("SERVICE_UNAVAILABLE"));
    }

    @ExceptionHandler(ClientException.class)
    public ResponseEntity<?> handleClientException(ClientException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(HttpStatus.valueOf(ex.getStatus()).name()));
    }

    
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<?> handleServiceException(ServiceException ex) {
        return ResponseEntity.status(503).body(new ErrorResponse("SERVICE_UNAVAILABLE"));
    }

    
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<?> handleOptimisticLock() {
        return ResponseEntity.status(409).body(new ErrorResponse("CONCURRENT_MODIFICATION"));
    }

 
    @ExceptionHandler(InvalidRefreshActionException.class)
    public ResponseEntity<?> handleInvalidRefreshActionException() {
        return ResponseEntity.status(409)
        .body(new ErrorResponse("REFRESH_ERROR"));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<?> handleInvalidRefreshTokenException() {
        return ResponseEntity.status(401)
        .header(HttpHeaders.SET_COOKIE,authController.deleteRefreshToken().toString())
        .body(new ErrorResponse("INVALID_REFRESH_TOKEN"));
    }

}
