package com.netflix.graphql.dgs.client.codegen;

import java.util.LinkedHashMap;
import java.util.Map;

public class GraphQLQuery {
    private final String operationType;
    private final String queryName;
    private final Map<String, Object> input = new LinkedHashMap<>();

    public GraphQLQuery(String operationType, String queryName) {
        this.operationType = operationType;
        this.queryName = queryName;
    }

    public GraphQLQuery(String operationType) {
        this(operationType, null);
    }

    public String getOperationType() {
        return operationType;
    }

    public String getQueryName() {
        return queryName;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public String getOperationName() {
        return queryName;
    }
}
