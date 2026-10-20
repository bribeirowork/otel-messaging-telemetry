package com.telemetry.messaging.infrastructure.exporter.otlp;

import com.google.protobuf.ByteString;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.ArrayValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translates OpenTelemetry SDK spans into the protobuf model of the OTLP protocol
 * ({@code ResourceSpans > ScopeSpans > Span}).
 */
public class OtlpProtobufMapper {

    public ExportTraceServiceRequest toRequest(Collection<SpanData> spans) {
        Map<io.opentelemetry.sdk.resources.Resource, Map<InstrumentationScopeInfo, List<SpanData>>> grouped =
                spans.stream().collect(Collectors.groupingBy(
                        SpanData::getResource, LinkedHashMap::new,
                        Collectors.groupingBy(SpanData::getInstrumentationScopeInfo, LinkedHashMap::new,
                                Collectors.toList())));

        ExportTraceServiceRequest.Builder request = ExportTraceServiceRequest.newBuilder();
        grouped.forEach((resource, byScope) -> {
            ResourceSpans.Builder resourceSpans = ResourceSpans.newBuilder()
                    .setResource(Resource.newBuilder().addAllAttributes(toKeyValues(resource.getAttributes())));
            if (resource.getSchemaUrl() != null) {
                resourceSpans.setSchemaUrl(resource.getSchemaUrl());
            }
            byScope.forEach((scope, scopeSpans) -> resourceSpans.addScopeSpans(toScopeSpans(scope, scopeSpans)));
            request.addResourceSpans(resourceSpans);
        });
        return request.build();
    }

    private ScopeSpans toScopeSpans(InstrumentationScopeInfo scope, List<SpanData> spans) {
        InstrumentationScope.Builder protoScope = InstrumentationScope.newBuilder()
                .setName(scope.getName())
                .addAllAttributes(toKeyValues(scope.getAttributes()));
        if (scope.getVersion() != null) {
            protoScope.setVersion(scope.getVersion());
        }
        ScopeSpans.Builder builder = ScopeSpans.newBuilder().setScope(protoScope);
        if (scope.getSchemaUrl() != null) {
            builder.setSchemaUrl(scope.getSchemaUrl());
        }
        spans.forEach(span -> builder.addSpans(toSpan(span)));
        return builder.build();
    }

    private Span toSpan(SpanData data) {
        SpanContext context = data.getSpanContext();
        Span.Builder span = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(context.getTraceIdBytes()))
                .setSpanId(ByteString.copyFrom(context.getSpanIdBytes()))
                .setTraceState(encode(context.getTraceState()))
                .setFlags(context.getTraceFlags().asByte() & 0xff)
                .setName(data.getName())
                .setKind(toKind(data.getKind()))
                .setStartTimeUnixNano(data.getStartEpochNanos())
                .setEndTimeUnixNano(data.getEndEpochNanos())
                .addAllAttributes(toKeyValues(data.getAttributes()))
                .setDroppedAttributesCount(data.getTotalAttributeCount() - data.getAttributes().size())
                .setDroppedEventsCount(data.getTotalRecordedEvents() - data.getEvents().size())
                .setDroppedLinksCount(data.getTotalRecordedLinks() - data.getLinks().size())
                .setStatus(toStatus(data));

        if (data.getParentSpanContext().isValid()) {
            span.setParentSpanId(ByteString.copyFrom(data.getParentSpanContext().getSpanIdBytes()));
        }
        data.getEvents().forEach(event -> span.addEvents(toEvent(event)));
        data.getLinks().forEach(link -> span.addLinks(toLink(link)));
        return span.build();
    }

    private Span.Event toEvent(EventData event) {
        return Span.Event.newBuilder()
                .setName(event.getName())
                .setTimeUnixNano(event.getEpochNanos())
                .addAllAttributes(toKeyValues(event.getAttributes()))
                .setDroppedAttributesCount(event.getTotalAttributeCount() - event.getAttributes().size())
                .build();
    }

    private Span.Link toLink(LinkData link) {
        SpanContext context = link.getSpanContext();
        return Span.Link.newBuilder()
                .setTraceId(ByteString.copyFrom(context.getTraceIdBytes()))
                .setSpanId(ByteString.copyFrom(context.getSpanIdBytes()))
                .setTraceState(encode(context.getTraceState()))
                .addAllAttributes(toKeyValues(link.getAttributes()))
                .setDroppedAttributesCount(link.getTotalAttributeCount() - link.getAttributes().size())
                .build();
    }

    private Status toStatus(SpanData data) {
        StatusCode code = data.getStatus().getStatusCode();
        Status.Builder status = Status.newBuilder().setCode(switch (code) {
            case OK -> Status.StatusCode.STATUS_CODE_OK;
            case ERROR -> Status.StatusCode.STATUS_CODE_ERROR;
            case UNSET -> Status.StatusCode.STATUS_CODE_UNSET;
        });
        if (data.getStatus().getDescription() != null) {
            status.setMessage(data.getStatus().getDescription());
        }
        return status.build();
    }

    private Span.SpanKind toKind(io.opentelemetry.api.trace.SpanKind kind) {
        return switch (kind) {
            case INTERNAL -> Span.SpanKind.SPAN_KIND_INTERNAL;
            case SERVER -> Span.SpanKind.SPAN_KIND_SERVER;
            case CLIENT -> Span.SpanKind.SPAN_KIND_CLIENT;
            case PRODUCER -> Span.SpanKind.SPAN_KIND_PRODUCER;
            case CONSUMER -> Span.SpanKind.SPAN_KIND_CONSUMER;
        };
    }

    private List<KeyValue> toKeyValues(Attributes attributes) {
        List<KeyValue> keyValues = new ArrayList<>(attributes.size());
        attributes.forEach((key, value) -> keyValues.add(KeyValue.newBuilder()
                .setKey(key.getKey())
                .setValue(toAnyValue(key, value))
                .build()));
        return keyValues;
    }

    private AnyValue toAnyValue(AttributeKey<?> key, Object value) {
        return switch (key.getType()) {
            case STRING -> AnyValue.newBuilder().setStringValue((String) value).build();
            case BOOLEAN -> AnyValue.newBuilder().setBoolValue((Boolean) value).build();
            case LONG -> AnyValue.newBuilder().setIntValue((Long) value).build();
            case DOUBLE -> AnyValue.newBuilder().setDoubleValue((Double) value).build();
            case STRING_ARRAY, BOOLEAN_ARRAY, LONG_ARRAY, DOUBLE_ARRAY -> toArrayValue((List<?>) value);
            default -> AnyValue.newBuilder().setStringValue(String.valueOf(value)).build();
        };
    }

    private AnyValue toArrayValue(List<?> values) {
        ArrayValue.Builder array = ArrayValue.newBuilder();
        for (Object item : values) {
            AnyValue.Builder element = AnyValue.newBuilder();
            if (item instanceof String text) {
                element.setStringValue(text);
            } else if (item instanceof Boolean bool) {
                element.setBoolValue(bool);
            } else if (item instanceof Long number) {
                element.setIntValue(number);
            } else if (item instanceof Double decimal) {
                element.setDoubleValue(decimal);
            } else {
                element.setStringValue(String.valueOf(item));
            }
            array.addValues(element);
        }
        return AnyValue.newBuilder().setArrayValue(array).build();
    }

    private String encode(TraceState traceState) {
        if (traceState == null || traceState.isEmpty()) {
            return "";
        }
        return traceState.asMap().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
    }
}
