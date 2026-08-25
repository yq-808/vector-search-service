package com.example.vectorsearch.api;

import com.example.vectorsearch.api.dto.ErrorResponse;
import com.example.vectorsearch.document.DocumentNotFoundException;
import com.example.vectorsearch.task.TaskNotFoundException;
import com.example.vectorsearch.vectorization.QueueOverflowException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/** Translates domain failures into HTTP responses with a consistent body. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({DocumentNotFoundException.class, TaskNotFoundException.class})
    public ResponseEntity<ErrorResponse> notFound(RuntimeException e) {
        return respond(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** The backlog is full: an honest "try again", not a failure of the request itself. */
    @ExceptionHandler(QueueOverflowException.class)
    public ResponseEntity<ErrorResponse> overloaded(QueueOverflowException e) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    /** Two writers touched the same document at once; the loser is told to retry. */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ErrorResponse> conflict(Exception e) {
        return respond(HttpStatus.CONFLICT, "concurrent modification of the same document, retry");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException e) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, details.isEmpty() ? "invalid request" : details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> invalidParameter(ConstraintViolationException e) {
        String details = e.getConstraintViolations().stream()
                .map(violation -> lastNode(violation) + " " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, details);
    }

    private static String lastNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        return path.substring(path.lastIndexOf('.') + 1);
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), status.getReasonPhrase(), message));
    }
}
