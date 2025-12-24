package com.netflix.graphql.dgs.internal;

import com.netflix.graphql.dgs.DgsQueryExecutor;
import graphql.schema.GraphQLSchema;
import org.springframework.graphql.execution.GraphQlSource;

import java.util.Optional;

public class DefaultDgsQueryExecutor implements DgsQueryExecutor {
    private final GraphQlSource graphQlSource;

    public DefaultDgsQueryExecutor(GraphQlSource graphQlSource) {
        this.graphQlSource = graphQlSource;
    }

    @Override
    public <T> T executeAndExtractJsonPathAsObject(String query, String jsonPath, Class<T> targetClass) {
        throw new UnsupportedOperationException("DGS query execution is not supported without DGS runtime.");
    }

    @Override
    public Optional<GraphQLSchema> getSchema() {
        return Optional.ofNullable(graphQlSource).map(GraphQlSource::schema);
    }
}
