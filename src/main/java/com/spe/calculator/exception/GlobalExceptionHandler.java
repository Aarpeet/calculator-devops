package com.spe.calculator.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.spe.calculator.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Translates validation and arithmetic failures into HTTP 400 responses with a
 * consistent JSON body instead of Spring's default error page.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** ?a=2 (no b) or ?a=&b=3 (empty value). The message shows the expected usage. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                HttpServletRequest request) {
        return badRequest("Missing or empty query parameter '" + ex.getParameterName() + "' (usage: "
                + request.getRequestURI() + "?a=<number>&b=<number>)");
    }

    /** ?a=abc&b=5: the value cannot be converted to a number. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("Invalid value '" + ex.getValue() + "' for query parameter '" + ex.getName()
                + "': must be a number");
    }

    /** Division by zero, out-of-range operands, and other arithmetic errors. */
    @ExceptionHandler({ArithmeticException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleInvalidArithmetic(RuntimeException ex) {
        return badRequest(ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> badRequest(String message) {
        log.debug("Rejecting request with 400: {}", message);
        return ResponseEntity.badRequest().body(ErrorResponse.of(message, HttpStatus.BAD_REQUEST.value()));
    }
}
