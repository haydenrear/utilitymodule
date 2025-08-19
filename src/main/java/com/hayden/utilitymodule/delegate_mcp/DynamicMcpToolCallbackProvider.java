package com.hayden.utilitymodule.delegate_mcp;

import com.hayden.utilitymodule.concurrent.striped.StripedLock;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.client.autoconfigure.NamedClientMcpTransport;
import org.springframework.ai.mcp.client.autoconfigure.configurer.McpSyncClientConfigurer;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Slf4j
@Component
public class DynamicMcpToolCallbackProvider {

    public record McpError(String getMessage) implements SingleError {}

    public interface ServerCustomizer extends BiFunction<String, ServerParameters, Map.Entry<String, ServerParameters>> { }

    public interface McpClientRequest extends Function<McpSyncClient, McpSchema.CallToolResult> { }

    @Autowired
    private McpClientCommonProperties commonProperties;

    @Autowired
    private McpSyncClientConfigurer mcpSyncClientConfigurer;

    @Autowired(required = false)
    private List<NamedClientMcpTransport> stdioTransports = new ArrayList<>();

    @Autowired(required = false)
    private List<NamedClientMcpTransport> namedTransports = new ArrayList<>();

    @PostConstruct
    public void initialize() {
        stdioTransports = new ArrayList<>(stdioTransports);
        stdioTransports.addAll(namedTransports.stream().filter(Predicate.not(stdioTransports::contains)).toList());
    }

    private final ConcurrentHashMap<String, McpSyncClient> clientConcurrentHashMap = new ConcurrentHashMap<>();

    private String connectedClientName(String clientName, String serverConnectionName) {
        return clientName + " - " + serverConnectionName;
    }

    //  must hold lock on (assuming) stdio otherwise send multiple concurrent requests on stdio but this fails.
    @StripedLock
    public Result<McpSchema.CallToolResult, McpError> doOnClient(String clientName,
                                                                 ServerCustomizer replace,
                                                                 McpClientRequest request) {
        return buildClient(clientName, replace)
                .map(request);
    }

    /**
     * Kill the client and then perform some synchronized action on that service.
     * @param clientName
     * @param toDo
     * @return
     * @param <T>
     */
    @SneakyThrows
    @StripedLock
    public <T> T killClientAndThen(String clientName, Supplier<T> toDo) {
        clientConcurrentHashMap.get(clientName).closeGracefully();
        clientConcurrentHashMap.remove(clientName);
        return toDo.get();
    }

    @StripedLock
    public Result<McpSyncClient, McpError> buildClient(String clientName) {
        try {
            return buildClient(clientName, Map::entry);
        } catch (Exception e) {
            return Result.err(new McpError(e.getMessage()));
        }
    }

    @StripedLock
    public Result<McpSyncClient, McpError> buildClient(String clientName,
                                                       ServerCustomizer replace) {
        List<NamedClientMcpTransport> namedTransports = stdioTransports;

        if (!CollectionUtils.isEmpty(namedTransports)) {
            for (NamedClientMcpTransport namedTransport : namedTransports) {

                try {
                    if (Objects.equals(namedTransport.name(), clientName)) {
                        if (!(namedTransport.transport() instanceof StdioClientTransport)) {
                            log.error("Haven't implemented build client with Dynamic with anything besides stdio: {}", namedTransport.name());
                            continue;
                        }

                        Field paramsField = namedTransport.transport().getClass().getDeclaredField("params");
                        paramsField.trySetAccessible();
                        ServerParameters params = (ServerParameters) ReflectionUtils.getField(paramsField, namedTransport.transport());

                        if (params == null)
                            continue;

                        var paramsAfter = replace.apply(this.connectedClientName(commonProperties.getName(), namedTransport.name()), params);

                        if (this.clientConcurrentHashMap.containsKey(paramsAfter.getKey())) {
                            return Result.ok(this.clientConcurrentHashMap.get(paramsAfter.getKey()));
                        } else {
                            doStopExistingNotInMap(paramsAfter.getValue());
                        }

                        var t = new StdioClientTransport(paramsAfter.getValue());
                        McpSchema.Implementation clientInfo = new McpSchema.Implementation(
                                paramsAfter.getKey(),
                                commonProperties.getVersion());

                        McpClient.SyncSpec spec = McpClient.sync(t)
                                .clientInfo(clientInfo)
                                .requestTimeout(commonProperties.getRequestTimeout());

                        spec = mcpSyncClientConfigurer.configure(namedTransport.name(), spec);

                        var client = spec.build();

                        if (commonProperties.isInitialized()) {
                            client.initialize();
                        }

                        return Result.ok(client);
                    }

                    return Result.empty();

                } catch (NoSuchFieldException e) {
                    log.error(e.getMessage(), e);
                }


            }
        }

        return Result.err(new McpError("Could not find valid client to augment for name %s.".formatted(clientName)));

    }

    private static void doStopExistingNotInMap(ServerParameters params) {
        String name = null;
        boolean next = false;
        for (var p : params.getArgs()) {
            if (next) {
                name = p;
                break;
            }
            if (Objects.equals(p, "--name")) {
                next = true;
            }
        }

        doRunStopDocker(name);
        String finalName = name;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Running docker shutdown hook for {}", finalName);
            doRunStopDocker(finalName);
        }));
    }

    public static void doRunStopDocker(String name) {
        if (name != null) {
            try {
                log.info("Attempting docker container {}.", name);
                new ProcessBuilder("docker", "stop", name).start().waitFor();
                log.info("Successfully shutdown docker container {}.", name);
            } catch (InterruptedException | IOException e) {
                log.error("Error when stopping container {}", e.getMessage());
            }
        }
    }

}
