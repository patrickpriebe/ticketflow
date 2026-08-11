package com.ticketflow.order.infrastructure.web;

import com.ticketflow.order.domain.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Turns exceptions into RFC 7807 problem responses matching the {@code Problem}
 * schema in contracts/openapi/order-service.yaml.
 *
 * <p>The mapping from a domain error code to an HTTP status lives here and only
 * here: the domain does not know what a status code is.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://ticketflow.dev/problems/";

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException e, HttpServletRequest request) {
        HttpStatus status = statusOf(e.code());
        ProblemDetail problem = problem(status, title(e.code()), e.getMessage(), e.code(), request);

        if (status.is5xxServerError()) {
            log.error("Unmapped domain failure [{}]", e.code(), e);
        }
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed",
                "The request body failed validation.", "validation-error", request);

        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> Map.of(
                        "field", fieldError.getField(),
                        "message", String.valueOf(fieldError.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * A missing or malformed Idempotency-Key lands here. It is a 400 rather than a
     * 422 because the request is not merely invalid - it is unsafe to process at all.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Missing required header",
                "Header '%s' is required.".formatted(e.getHeaderName()), "missing-header", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Missing required parameter",
                "Query parameter '%s' is required.".formatted(e.getParameterName()), "missing-parameter", request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ProblemDetail handleMalformed(Exception e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "The request could not be parsed.", "malformed-request", request);
    }

    /** Value objects reject bad input by throwing this - an unknown payment method, for instance. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed",
                e.getMessage(), "validation-error", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
        // Never leak the exception message: it can carry SQL, host names or worse.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "The request could not be completed.", "internal-error", request);
    }

    private static HttpStatus statusOf(String code) {
        return switch (code) {
            case "order-not-found", "event-not-found", "ticket-category-not-found" -> HttpStatus.NOT_FOUND;
            case "event-not-on-sale", "insufficient-inventory",
                 "concurrent-inventory-update", "concurrent-order-update",
                 "invalid-order-transition", "duplicate-idempotency-key" -> HttpStatus.CONFLICT;
            case "invalid-order" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static String title(String code) {
        return switch (code) {
            case "order-not-found" -> "Order not found";
            case "event-not-found" -> "Event not found";
            case "ticket-category-not-found" -> "Ticket category not found";
            case "event-not-on-sale" -> "Event not on sale";
            case "insufficient-inventory" -> "Insufficient inventory";
            case "concurrent-inventory-update" -> "Concurrent inventory update";
            case "concurrent-order-update" -> "Concurrent order update";
            case "invalid-order-transition" -> "Invalid order transition";
            case "duplicate-idempotency-key" -> "Duplicate idempotency key";
            case "invalid-order" -> "Invalid order";
            default -> "Unexpected error";
        };
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail,
                                         String code, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE + code));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
