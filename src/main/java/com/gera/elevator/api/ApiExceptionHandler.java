package com.gera.elevator.api;

import com.gera.elevator.api.dto.ErrorResponse;
import com.gera.elevator.exception.ElevatorNotFoundException;
import com.gera.elevator.exception.InvalidElevatorRequestException;
import com.gera.elevator.exception.StateLockTimeoutException;
import com.gera.elevator.exception.StateStoreUnavailableException;
import com.gera.elevator.exception.StopCapacityExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({
            InvalidElevatorRequestException.class,
            ElevatorNotFoundException.class,
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, messageFor(ex), request, fieldErrors(ex));
    }

    @ExceptionHandler(StopCapacityExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(StopCapacityExceededException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler({
            StateStoreUnavailableException.class,
            StateLockTimeoutException.class
    })
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleUnavailable(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected elevator service error", request, Map.of());
    }

    private ErrorResponse error(HttpStatus status, String message, HttpServletRequest request, Map<String, String> fieldErrors) {
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fieldErrors
        );
    }

    private String messageFor(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException validationException) {
            return "Request validation failed";
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "Malformed JSON request";
        }
        return ex.getMessage();
    }

    private Map<String, String> fieldErrors(Exception ex) {
        if (!(ex instanceof MethodArgumentNotValidException validationException)) {
            return Map.of();
        }
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : validationException.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return errors;
    }
}
