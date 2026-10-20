package com.telemetry.messaging.infrastructure.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;

/**
 * Attribute keys from the stable OpenTelemetry HTTP semantic conventions.
 */
public final class HttpAttributes {

    public static final AttributeKey<String> REQUEST_METHOD = AttributeKey.stringKey("http.request.method");
    public static final AttributeKey<String> REQUEST_METHOD_ORIGINAL = AttributeKey.stringKey("http.request.method_original");
    public static final AttributeKey<String> ROUTE = AttributeKey.stringKey("http.route");
    public static final AttributeKey<Long> RESPONSE_STATUS_CODE = AttributeKey.longKey("http.response.status_code");
    public static final AttributeKey<String> URL_PATH = AttributeKey.stringKey("url.path");
    public static final AttributeKey<String> URL_SCHEME = AttributeKey.stringKey("url.scheme");
    public static final AttributeKey<String> SERVER_ADDRESS = AttributeKey.stringKey("server.address");
    public static final AttributeKey<Long> SERVER_PORT = AttributeKey.longKey("server.port");
    public static final AttributeKey<String> CLIENT_ADDRESS = AttributeKey.stringKey("client.address");
    public static final AttributeKey<String> USER_AGENT = AttributeKey.stringKey("user_agent.original");

    private HttpAttributes() {
    }
}
