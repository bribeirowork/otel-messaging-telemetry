package com.bribeiro.telemetry.messaging.spring.web;

import com.bribeiro.telemetry.messaging.application.usecase.TraceHttpRequestUseCase;
import com.bribeiro.telemetry.messaging.domain.http.HttpOutcome;
import com.bribeiro.telemetry.messaging.domain.http.HttpRequestMetadata;
import com.bribeiro.telemetry.messaging.domain.http.HttpServerSpan;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Inbound adapter that creates a SERVER span for every request handled by a controller.
 * <p>
 * It continues the caller's trace ({@code traceparent} header), so a request coming from another service,
 * the controller and any {@code @TracedProducer} called inside it all share the same trace.
 */
public class HttpServerTracingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpServerTracingFilter.class);

    // string constants keep the library free of a compile dependency on spring-webmvc
    static final String BEST_MATCHING_PATTERN = "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern";
    static final String HANDLED_EXCEPTION = "org.springframework.web.servlet.DispatcherServlet.EXCEPTION";
    static final String SERVLET_ERROR_EXCEPTION = "jakarta.servlet.error.exception";
    private static final String CATCH_ALL_PATTERN = "/**";
    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final int INTERNAL_SERVER_ERROR = 500;

    private final TraceHttpRequestUseCase useCase;
    private final List<String> excludedPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public HttpServerTracingFilter(TraceHttpRequestUseCase useCase, List<String> excludedPaths) {
        this.useCase = useCase;
        this.excludedPaths = List.copyOf(excludedPaths);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathWithinApplication(request);
        return excludedPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpServerSpan span = startSpan(request);
        if (span == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException error) {
            span.fail(error, outcome(request, INTERNAL_SERVER_ERROR));
            throw error;
        } finally {
            span.leaveScope();
        }

        if (request.isAsyncStarted()) {
            // the response is written later, on another thread: end the span when the async cycle finishes
            request.getAsyncContext().addListener(new CompletingAsyncListener(span));
        } else {
            finish(span, request, response);
        }
    }

    private HttpServerSpan startSpan(HttpServletRequest request) {
        try {
            return useCase.start(metadataOf(request), new ServletRequestHeaders(request));
        } catch (RuntimeException invalidRequest) {
            // telemetry must never block a request
            log.warn("Não foi possível iniciar o span HTTP de {} {}: {}",
                    request.getMethod(), request.getRequestURI(), invalidRequest.getMessage());
            return null;
        }
    }

    private static void finish(HttpServerSpan span, HttpServletRequest request, HttpServletResponse response) {
        HttpOutcome outcome = outcome(request, response.getStatus());
        Throwable handled = handledException(request);
        if (handled != null && outcome.isServerError()) {
            span.fail(handled, outcome);
        } else {
            span.complete(outcome);
        }
    }

    /** Exception already turned into a response by Spring ({@code @ExceptionHandler}, error page...). */
    private static Throwable handledException(HttpServletRequest request) {
        Object handled = request.getAttribute(HANDLED_EXCEPTION);
        if (handled == null) {
            handled = request.getAttribute(SERVLET_ERROR_EXCEPTION);
        }
        return handled instanceof Throwable throwable ? throwable : null;
    }

    private static HttpOutcome outcome(HttpServletRequest request, int status) {
        Object pattern = request.getAttribute(BEST_MATCHING_PATTERN);
        // "/**" is Spring Boot's static resource handler: it matches every unmapped path and says nothing about the route
        String route = pattern == null || CATCH_ALL_PATTERN.equals(pattern) ? null : request.getContextPath() + pattern;
        return new HttpOutcome(status < 100 || status > 599 ? INTERNAL_SERVER_ERROR : status, route);
    }

    private static HttpRequestMetadata metadataOf(HttpServletRequest request) {
        return HttpRequestMetadata.builder(request.getMethod(), request.getRequestURI())
                .scheme(request.getScheme())
                .serverAddress(request.getServerName())
                .serverPort(request.getServerPort())
                .clientAddress(clientAddress(request))
                .userAgent(request.getHeader("User-Agent"))
                .build();
    }

    /** First address of {@code X-Forwarded-For} when behind a proxy/load balancer, otherwise the socket peer. */
    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length())
                : uri;
    }

    private static final class CompletingAsyncListener implements AsyncListener {

        private final HttpServerSpan span;

        private CompletingAsyncListener(HttpServerSpan span) {
            this.span = span;
        }

        @Override
        public void onComplete(AsyncEvent event) {
            finish(span, (HttpServletRequest) event.getSuppliedRequest(), (HttpServletResponse) event.getSuppliedResponse());
        }

        @Override
        public void onError(AsyncEvent event) {
            HttpServletRequest request = (HttpServletRequest) event.getSuppliedRequest();
            span.fail(event.getThrowable(), outcome(request, INTERNAL_SERVER_ERROR));
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            // onComplete follows a timeout and records the final status
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            event.getAsyncContext().addListener(this);
        }
    }
}
