package com.netflix.graphql.dgs;

import com.netflix.graphql.dgs.internal.DefaultDgsQueryExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.GraphQlSource;

@Configuration
public class DgsQueryExecutorConfig {

    @Bean
    DgsQueryExecutor dgsQueryExecutor(ObjectProvider<GraphQlSource> graphQlSourceProvider) {
        return new DefaultDgsQueryExecutor(graphQlSourceProvider.getIfAvailable());
    }
}
