package com.example.vectorsearch.api;

import com.example.vectorsearch.api.dto.ErrorResponse;
import com.example.vectorsearch.document.DocumentNotFoundException;
import com.example.vectorsearch.task.TaskNotFoundException;
import com.example.vectorsearch.vectorization.QueueOverflowException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Translates every failure into one response shape.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} is what makes "one shape" true rather than
 * aspirational: the exceptions Spring MVC raises by itself &mdash; unreadable body, wrong method,
 * unsupported media type, unknown path &mdash; are rendered like the domain failures below instead
 * of falling through to the container's default error body.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({DocumentNotFoundException.class, TaskNotFoundException.class})
    public ResponseEntity<Object> notFound(RuntimeException e) {
        return respond(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** The backlog is full: an honest "try again", not a failure of the request itself. */
    @ExceptionHandler(QueueOverflowException.class)
    public ResponseEntity<Object> overloaded(QueueOverflowException e) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    /** Two writers touched the same document at once; the loser is told to retry. */
    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Object> conflict(Exception e) {
        return respond(HttpStatus.CONFLICT, "concurrent modification of the same document, retry");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> badRequest(IllegalArgumentException e) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> invalidParameter(ConstraintViolationException e) {
        String details = e.getConstraintViolations().stream()
                .map(violation -> lastNode(violation) + " " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, details);
    }

    /**
     * Last resort. An unforeseen failure is logged in full and reported as a bare 500: the client
     * learns that the request failed, and nothing about our internals.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception e) {
        logger.error("unhandled failure while serving a request", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "unexpected internal error");
    }

    /** Field-level detail is more useful than the framework's generic "invalid content". */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, details.isEmpty() ? "invalid request" : details);
    }

    /** The framework only offers "Bad Request" here; say what it was that could not be read. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "malformed request body");
    }

    /** Names the parameter that could not be converted, and what it would have accepted. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException e,
                                                        HttpHeaders headers,
                                                        HttpStatusCode status,
                                                        WebRequest request) {
        String name = e instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : "parameter";
        return respond(HttpStatus.BAD_REQUEST,
                name + " has an invalid value: '" + e.getValue() + "'" + accepted(e));
    }

    /** The default mentions static resources, which says more about our plumbing than the request. */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException e,
                                                                    HttpHeaders headers,
                                                                    HttpStatusCode status,
                                                                    WebRequest request) {
        return respond(HttpStatus.NOT_FOUND, "no endpoint for " + e.getHttpMethod() + " /" + e.getResourcePath());
    }

    /** Every exception the base class handles ends up here, and leaves in our body shape. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        // Spring's own web.ErrorResponse (not our DTO of the same name) carries a safe description.
        String message = e instanceof org.springframework.web.ErrorResponse framework
                ? framework.getBody().getDetail()
                : status.getReasonPhrase();
        return ResponseEntity.status(status).headers(headers)
                .body(ErrorResponse.of(status.value(), status.getReasonPhrase(), message));
    }

    private static String accepted(TypeMismatchException e) {
        Class<?> required = e.getRequiredType();
        return required != null && required.isEnum()
                ? " (expected one of " + Arrays.toString(required.getEnumConstants()) + ")"
                : "";
    }

    private static String lastNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        return path.substring(path.lastIndexOf('.') + 1);
    }

    private static ResponseEntity<Object> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), status.getReasonPhrase(), message));
    }
}
