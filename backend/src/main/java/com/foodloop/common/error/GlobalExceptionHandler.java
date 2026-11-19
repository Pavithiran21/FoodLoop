package com.foodloop.common.error;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.foodloop.common.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Turns every exception into the shared error body. Expected failures are {@link ApiException}s;
 * the other handlers translate Spring's own exceptions into the same codes SubPulse uses.
 * Only unexpected errors are logged with a stack trace, and never with request data.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_FIELDS = "Some fields are not valid.";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.getStatus());
        ex.getHeaders().forEach(response::header);
        return response.body(body(ex.getCode(), ex.getMessage(), ex.getDetails(), request));
    }

    /** @Valid on a request body failed. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorDetail(fieldName(error), error.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", INVALID_FIELDS, details, request);
    }

    /** A constraint on a @RequestParam or @PathVariable failed. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<ErrorDetail> details = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErrorDetail(result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", INVALID_FIELDS, details, request);
    }

    /** A query or path value has the wrong type, for example ?page=abc or /bookings/xyz. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Class<?> type = ex.getRequiredType();
        if (type != null && Collection.class.isAssignableFrom(type)) {
            // For ?status=CONFIRMED,CANCELLED the useful type is the element type (the enum), not Set.
            type = ex.getParameter().nested().getNestedParameterType();
        }
        String message = "id".equals(ex.getName()) ? "Not a valid id." : messageForType(type);
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", INVALID_FIELDS,
                List.of(new ErrorDetail(ex.getName(), message)), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", INVALID_FIELDS,
                List.of(new ErrorDetail(ex.getParameterName(), "This value is required.")), request);
    }

    /** The body isn't JSON, or a value can't be read as its type (an unknown enum, a bad date). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String field = mismatch.getPath().stream()
                    .map(GlobalExceptionHandler::pathPart)
                    .collect(Collectors.joining("."));
            return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", INVALID_FIELDS,
                    List.of(new ErrorDetail(field, messageForType(mismatch.getTargetType()))), request);
        }
        return respond(HttpStatus.BAD_REQUEST, "INVALID_JSON", "The request body isn't valid JSON.", List.of(), request);
    }

    /** A photo over Spring's 5 MB multipart limit (the body filter answers the obvious cases before this). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return respond(HttpStatus.valueOf(413), "PAYLOAD_TOO_LARGE", "The file is too large.",
                List.of(new ErrorDetail("photo", "That file can't be used. Use a JPG or PNG photo up to 5 MB.")), request);
    }

    /** The upload had no "photo" part. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", INVALID_FIELDS,
                List.of(new ErrorDetail(ex.getRequestPartName(), "Choose a file to upload.")), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_JSON", "Send the request body as JSON.", List.of(), request);
    }

    /** Unknown path, or a known path with a method it doesn't have: both are "no such route". */
    @ExceptionHandler({ NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class })
    public ResponseEntity<ErrorResponse> handleNoRoute(Exception ex, HttpServletRequest request) {
        String message = "No route for " + request.getMethod() + " " + request.getRequestURI() + ".";
        return respond(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", message, List.of(), request);
    }

    /** Two writes to the same row raced (JPA @Version). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "This was changed somewhere else. Reload it and try again.", List.of(), request);
    }

    /** A database constraint the service didn't expect. The message is not logged: it can hold an email. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Unexpected database constraint violation (duplicate key: {})", DatabaseErrors.isDuplicateKey(ex));
        return respond(HttpStatus.CONFLICT, "CONFLICT", "That conflicts with data that already exists.", List.of(), request);
    }

    /**
     * The client went away while the answer was being written (a closed tab, a page left before the answer
     * came). Nothing went wrong on the server and nobody is left to read an error, so it is not an ERROR and
     * nothing more is written.
     */
    @ExceptionHandler({ AsyncRequestNotUsableException.class, HttpMessageNotWritableException.class })
    public ResponseEntity<ErrorResponse> handleClientGone(Exception ex, HttpServletRequest request) {
        if (ex instanceof HttpMessageNotWritableException && !(rootCause(ex) instanceof IOException)) {
            return handleUnexpected(ex, request); // a real serialisation bug, not a closed connection
        }
        log.debug("Client closed the connection before the response was written");
        return null;
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong. Please try again.",
                List.of(), request);
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message,
            List<ErrorDetail> details, HttpServletRequest request) {
        return ResponseEntity.status(status).body(body(code, message, details, request));
    }

    private static ErrorResponse body(String code, String message, List<ErrorDetail> details, HttpServletRequest request) {
        return ErrorResponse.of(code, message, details, RequestIdFilter.requestId(request));
    }

    /** "reminderDays[]" style names become "reminderDays" so the frontend can match its field. */
    private static String fieldName(FieldError error) {
        return error.getField().replaceAll("\\[.*?]", "");
    }

    private static String pathPart(JacksonException.Reference reference) {
        return reference.getPropertyName() != null ? reference.getPropertyName() : String.valueOf(reference.getIndex());
    }

    private static String messageForType(Class<?> type) {
        if (type == null) {
            return "Not a valid value.";
        }
        if (type.isEnum()) {
            String values = Arrays.stream(type.getEnumConstants()).map(Object::toString).collect(Collectors.joining(", "));
            return "Use one of: " + values + ".";
        }
        if (type == LocalDate.class) {
            return "Enter a valid date.";
        }
        if (type == Instant.class) {
            return "Use an ISO date and time, like 2026-10-25T09:30:00Z.";
        }
        if (type == BigDecimal.class || Number.class.isAssignableFrom(type) || type.isPrimitive()) {
            return "Enter a number.";
        }
        return "Not a valid value.";
    }
}
