package com.telemetry.messaging.infrastructure.exporter.feign;

import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;

import java.util.Set;

/**
 * Converts the responses the OTLP specification considers retryable (429, 502, 503, 504)
 * into {@link RetryableException}, so that the Feign {@code Retryer} tries again.
 */
class OtlpErrorDecoder implements ErrorDecoder {

    private static final Set<Integer> RETRYABLE_STATUS = Set.of(429, 502, 503, 504);

    private final ErrorDecoder delegate = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        if (RETRYABLE_STATUS.contains(response.status())) {
            return new RetryableException(
                    response.status(),
                    "Backend OTLP indisponível (HTTP " + response.status() + ")",
                    response.request().httpMethod(),
                    (Long) null,
                    response.request());
        }
        return delegate.decode(methodKey, response);
    }
}
