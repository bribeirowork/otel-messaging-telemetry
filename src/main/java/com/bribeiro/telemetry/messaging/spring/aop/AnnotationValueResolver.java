package com.bribeiro.telemetry.messaging.spring.aop;

import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.env.Environment;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves annotation values: first Environment placeholders ({@code ${...}}),
 * then SpEL when the value starts with {@code #} (e.g. {@code #order.id}). Otherwise it is a literal.
 */
class AnnotationValueResolver {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> cache = new ConcurrentHashMap<>();
    private final Environment environment;

    AnnotationValueResolver(Environment environment) {
        this.environment = environment;
    }

    String resolve(String rawValue, Object target, Method method, Object[] args) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String value = environment.resolvePlaceholders(rawValue.strip());
        if (!value.startsWith("#")) {
            return value;
        }
        Expression expression = cache.computeIfAbsent(value, parser::parseExpression);
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(target, method, args, parameterNames);
        Object result = expression.getValue(context);
        return result == null ? null : Objects.toString(result);
    }
}
