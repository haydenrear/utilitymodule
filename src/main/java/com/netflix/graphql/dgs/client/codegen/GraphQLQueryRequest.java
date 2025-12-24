package com.netflix.graphql.dgs.client.codegen;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

public class GraphQLQueryRequest {
    private final GraphQLQuery query;
    private final BaseProjectionNode projection;

    public GraphQLQueryRequest(GraphQLQuery query, BaseProjectionNode projection) {
        this.query = query;
        this.projection = projection;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        String opType = query.getOperationType() == null ? "query" : query.getOperationType();
        String opName = query.getOperationName() == null ? "" : query.getOperationName();
        sb.append(opType).append(" { ");
        if (!opName.isBlank()) {
            sb.append(opName);
        }
        String args = serializeArguments(query.getInput());
        if (!args.isBlank()) {
            sb.append("(").append(args).append(")");
        }
        if (projection != null) {
            String fields = serializeProjection(projection);
            if (!fields.isBlank()) {
                sb.append(" ").append(fields);
            }
        }
        sb.append(" }");
        return sb.toString();
    }

    private String serializeProjection(BaseProjectionNode node) {
        if (node.getFields().isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(" ");
        for (Map.Entry<String, BaseProjectionNode> entry : node.getFields().entrySet()) {
            String key = entry.getKey();
            BaseProjectionNode child = entry.getValue();
            if (child == null || child.getFields().isEmpty()) {
                joiner.add(key);
            } else {
                joiner.add(key + " " + serializeProjection(child));
            }
        }
        return "{" + joiner + "}";
    }

    private String serializeArguments(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            joiner.add(entry.getKey() + ": " + serializeValue(entry.getValue()));
        }
        return joiner.toString();
    }

    private String serializeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof String s) {
            return "\"" + escapeString(s) + "\"";
        }
        if (value instanceof Map<?, ?> map) {
            StringJoiner joiner = new StringJoiner(", ");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                joiner.add(entry.getKey() + ": " + serializeValue(entry.getValue()));
            }
            return "{" + joiner + "}";
        }
        if (value instanceof Collection<?> collection) {
            StringJoiner joiner = new StringJoiner(", ");
            for (Object item : collection) {
                joiner.add(serializeValue(item));
            }
            return "[" + joiner + "]";
        }
        if (value.getClass().isArray()) {
            StringJoiner joiner = new StringJoiner(", ");
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                joiner.add(serializeValue(java.lang.reflect.Array.get(value, i)));
            }
            return "[" + joiner + "]";
        }
        return "\"" + escapeString(Objects.toString(value)) + "\"";
    }

    private String escapeString(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
