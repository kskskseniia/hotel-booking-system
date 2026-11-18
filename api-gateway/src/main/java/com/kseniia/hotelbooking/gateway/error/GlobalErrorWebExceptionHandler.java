package com.kseniia.hotelbooking.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.DecoderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import javax.net.ssl.SSLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GlobalErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }

        HttpStatus status = resolveStatus(exception);
        if (status.is5xxServerError() && status != HttpStatus.SERVICE_UNAVAILABLE) {
            LOGGER.error("Unhandled gateway failure for {}", exchange.getRequest().getPath(), exception);
        }

        if (exception instanceof ErrorResponse errorResponse) {
            exchange.getResponse().getHeaders().putAll(errorResponse.getHeaders());
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] body = objectMapper.writeValueAsBytes(com.kseniia.hotelbooking.gateway.error.ErrorResponse.of(
                    status,
                    safeMessage(status),
                    exchange.getRequest().getPath().value()
            ));
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (JsonProcessingException serializationFailure) {
            return Mono.error(serializationFailure);
        }
    }

    private HttpStatus resolveStatus(Throwable exception) {
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status != null) {
                return status;
            }
        }
        if (isDownstreamConnectionFailure(exception)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private boolean isDownstreamConnectionFailure(Throwable exception) {
        if (isTransportFailure(exception)) {
            return true;
        }
        if (!(exception instanceof WebClientRequestException || exception instanceof DecoderException)) {
            return false;
        }

        Throwable current = exception.getCause();
        while (current != null) {
            if (isTransportFailure(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isTransportFailure(Throwable exception) {
        return exception instanceof SocketException
                || exception instanceof SocketTimeoutException
                || exception instanceof UnknownHostException
                || exception instanceof TimeoutException
                || exception instanceof io.netty.handler.timeout.TimeoutException
                || exception instanceof SSLException
                || exception instanceof PrematureCloseException;
    }

    private String safeMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Invalid request";
            case UNAUTHORIZED -> "Authentication is required";
            case FORBIDDEN -> "Access is denied";
            case NOT_FOUND -> "Resource not found";
            case METHOD_NOT_ALLOWED -> "Method not allowed";
            case SERVICE_UNAVAILABLE -> "Service unavailable";
            case INTERNAL_SERVER_ERROR -> "Internal server error";
            default -> status.getReasonPhrase();
        };
    }
}