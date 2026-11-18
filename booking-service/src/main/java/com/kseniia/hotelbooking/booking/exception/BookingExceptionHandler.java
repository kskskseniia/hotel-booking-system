package com.kseniia.hotelbooking.booking.exception;

import com.kseniia.hotelbooking.booking.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;

@RestControllerAdvice
public class BookingExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookingExceptionHandler.class);

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> usernameAlreadyExists(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "Username already exists", request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ErrorResponse> invalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
    }

    @ExceptionHandler(UserDeletionConflictException.class)
    ResponseEntity<ErrorResponse> userDeletionConflict(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "User has a booking that is still being processed", request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ErrorResponse> userNotFound(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "User not found", request);
    }

    @ExceptionHandler(RoomUnavailableException.class)
    ResponseEntity<ErrorResponse> roomUnavailable(
            RoomUnavailableException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(HotelServiceUnavailableException.class)
    ResponseEntity<ErrorResponse> hotelServiceUnavailable(
            HotelServiceUnavailableException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> responseStatus(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return response(status, message, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest webRequest
    ) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String message = frameworkMessage(exception, status);
        String path = ((ServletWebRequest) webRequest).getRequest().getRequestURI();
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .body(ErrorResponse.of(status, message, path));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> accessDenied(HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "Access is denied", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected error while processing {}", request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private String frameworkMessage(Exception exception, HttpStatus status) {
        if (exception instanceof MethodArgumentNotValidException validation) {
            return validation.getBindingResult().getFieldErrors().stream()
                    .min(Comparator.comparing(FieldError::getField))
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .orElse("Request validation failed");
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return "Malformed JSON request";
        }
        return switch (status) {
            case BAD_REQUEST -> "Invalid request";
            case NOT_FOUND -> "Resource not found";
            case METHOD_NOT_ALLOWED -> "Method not allowed";
            case UNSUPPORTED_MEDIA_TYPE -> "Unsupported media type";
            default -> status.getReasonPhrase();
        };
    }

    private ResponseEntity<ErrorResponse> response(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status, message, request.getRequestURI()));
    }
}
