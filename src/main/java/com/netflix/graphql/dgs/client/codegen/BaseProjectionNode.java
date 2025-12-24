package com.netflix.graphql.dgs.client.codegen;

import java.util.LinkedHashMap;
import java.util.Map;

public class BaseProjectionNode {
    private final Map<String, BaseProjectionNode> fields = new LinkedHashMap<>();

    public Map<String, BaseProjectionNode> getFields() {
        return fields;
    }
}
