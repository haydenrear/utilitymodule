package com.netflix.graphql.dgs;

import graphql.schema.GraphQLSchema;

import java.util.Optional;

public interface DgsQueryExecutor {
    <T> T executeAndExtractJsonPathAsObject(String query, String jsonPath, Class<T> targetClass);

    Optional<GraphQLSchema> getSchema();
}
