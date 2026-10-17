package com.shuhongyi.trading.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Renders every error as an RFC 9457 {@link ProblemDetail}.
 * Spring MVC's own exceptions are handled by {@link ResponseEntityExceptionHandler};
 * this class adds a {@code code} property to them and maps the project's {@link ApiException}s.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException exception) {
        ProblemDetail problem = ProblemDetails.of(exception.getStatus(), exception.getCode(), exception.getMessage());
        return ResponseEntity.status(exception.getStatus()).body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, "invalid-parameter",
                "Invalid value for parameter '%s'".formatted(exception.getName()));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        ProblemDetail problem = ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "An unexpected error occurred");
        return ResponseEntity.internalServerError().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        ProblemDetail problem = ProblemDetails.of(status, "validation-failed", "Request validation failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = ProblemDetails.of(status, "validation-failed", "Request validation failed");
        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getProperties() == null) {
            problem.setProperty("code", "request-error");
        }
        return super.handleExceptionInternal(exception, body, headers, statusCode, request);
    }
}
