package com.hayden.utilitymodule.acp.config;

import com.agentclientprotocol.model.McpServer;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

@ConfigurationProperties("acp.mcp")
@Component
public class McpProperties {


    public Optional<McpServer> retrieve(ToolDefinition toolDefinition,
                                        ToolMetadata toolMetadata) {
        return Optional.empty();
    }

}
