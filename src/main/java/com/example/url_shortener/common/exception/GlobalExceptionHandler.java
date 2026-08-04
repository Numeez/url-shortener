package com.example.url_shortener.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailExists(EmailAlreadyExistsException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(AliasAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleAliasExists(AliasAlreadyExistsException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ShortUrlNotFoundException.class)
    public ResponseEntity<ApiError> handleShortUrlNotFound(ShortUrlNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ShortUrlExpiredException.class)
    public ResponseEntity<ApiError> handleShortUrlExpired(ShortUrlExpiredException ex, HttpServletRequest request) {
        return build(HttpStatus.GONE, ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid email or password", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed",
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
