package com.hayden.utilitymodule.delegate_mcp;

import com.hayden.utilitymodule.concurrent.striped.StripedLock;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
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
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Slf4j
@Component
public class DynamicMcpToolCallbackProvider {

    public record McpError(
            String getMessage) implements SingleError {
    }

    public sealed interface McpServerMetadata {

        record StdioServerMetadata(String name,
                                   ServerParameters serverParameters) implements McpServerMetadata {
        }

        record HttpServerMetadata(String baseUri,
                                  String sseEndpoint) implements McpServerMetadata {
        }

    }

    public interface ServerCustomizer<T extends McpServerMetadata> extends Function<T, T> {

        interface StdioServerCustomizer extends ServerCustomizer<McpServerMetadata.StdioServerMetadata> {}

        interface HttpServerCustomizer extends ServerCustomizer<McpServerMetadata.HttpServerMetadata> {}

    }

    public interface McpClientRequest extends Function<McpSyncClient, McpSchema.CallToolResult> {
    }

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

    ConcurrentHashMap<String, McpSyncClient> getClientConcurrentHashMap() {
        return clientConcurrentHashMap;
    }

    final ConcurrentHashMap<String, McpSyncClient> clientConcurrentHashMap = new ConcurrentHashMap<>();

    public void shutdown() {
        try {
            for (var c : clientConcurrentHashMap.values()) {
                tryStop(c);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void tryStop(McpSyncClient s) {
        try {
            log.info("Stopping client {}", s.getClientInfo().name());
            s.closeGracefully();
        } catch (Exception e) {
            log.error("Error stopping client {}", s.getClientInfo().name(), e);
        }
    }

    private static void tryStop(McpClient c) {
        if (c instanceof McpSyncClient s) {
            tryStop(s);
        } else if (c instanceof McpAsyncClient a) {
            try {
                a.closeGracefully().block();
            } catch (Exception e) {
                log.error("Error stopping client {}", a.getClientInfo().name(), e);
            }
        }
    }

    private String connectedClientName(String clientName, String serverConnectionName) {
        return clientName + " - " + serverConnectionName;
    }

    public boolean containsClient(String clientName) {
        return clientConcurrentHashMap.containsKey(clientName);
    }

    public boolean containsActiveClient(String clientName) {
        if (!containsClient(clientName)) {
            return false;
        }
        try {
            var ping = clientConcurrentHashMap.get(clientName).isInitialized();
            return ping;
        } catch (Exception e) {
            return false;
        }
    }

    //  must hold lock on (assuming) stdio otherwise send multiple concurrent requests on stdio but this fails.
    @StripedLock
    public <T extends McpServerMetadata> Result<McpSchema.CallToolResult, McpError> doOnClient(String clientName,
                                                                                               ServerCustomizer<T> replace,
                                                                                               McpClientRequest request) {
        return buildClient(clientName, replace)
                .map(request);
    }

    /**
     * Kill the client and then perform some synchronized action on that service.
     *
     * @param clientName
     * @param toDo
     * @param <T>
     * @return
     */
    @SneakyThrows
    @StripedLock
    public <T> T killClientAndThen(String clientName, Supplier<T> toDo) {
        if (clientConcurrentHashMap.containsKey(clientName)) {
            try {
                var r = clientConcurrentHashMap.remove(clientName);
                r.closeGracefully();
            } catch (Exception e) {
                log.error("Killing client " + clientName, e);
            }
        }
        return toDo.get();
    }

    @StripedLock
    public Result<McpSyncClient, McpError> buildClient(String clientName) {
        try {
            return buildClient(clientName, e -> e);
        } catch (Exception e) {
            return Result.err(new McpError(e.getMessage()));
        }
    }

    @StripedLock
    private <T extends McpServerMetadata> Result<McpSyncClient, McpError> buildClient(String clientName,
                                                                                      ServerCustomizer<T> replace) {
        List<NamedClientMcpTransport> namedTransports = stdioTransports;

        if (!CollectionUtils.isEmpty(namedTransports)) {
            for (NamedClientMcpTransport namedTransport : namedTransports) {

                try {
                    if (Objects.equals(namedTransport.name(), clientName)) {
                        if (namedTransport.transport() instanceof HttpClientSseClientTransport) {
                            return initializeHttpMcpSyncClient(
                                    clientName,
                                    replace instanceof ServerCustomizer.HttpServerCustomizer h ? h : s -> s,
                                    namedTransport);
                        }
                        if (namedTransport.transport() instanceof StdioClientTransport) {
                            return initializeStdioMcpSyncClient(
                                    clientName,
                                    replace instanceof ServerCustomizer.StdioServerCustomizer h ? h : s -> s,
                                    namedTransport);
                        }

                        log.error("Haven't implemented build client with Dynamic with anything besides stdio: {}", namedTransport.name());
                        return Result.err(new McpError("Could not find valid client to augment for name %s.".formatted(clientName)));
                    }


                } catch (NoSuchFieldException e) {
                    log.error(e.getMessage(), e);
                }


            }
        }

        return Result.err(new McpError("Could not find valid client to augment for name %s.".formatted(clientName)));

    }

    private Result<McpSyncClient, McpError> initializeHttpMcpSyncClient(String clientName,
                                                                        ServerCustomizer.HttpServerCustomizer replace,
                                                                        NamedClientMcpTransport namedTransport) throws NoSuchFieldException {
        Field uriField = namedTransport.transport().getClass().getDeclaredField("baseUri");
        uriField.trySetAccessible();
        URI uri = (URI) ReflectionUtils.getField(uriField, namedTransport.transport());

        Field endpointField = namedTransport.transport().getClass().getDeclaredField("sseEndpoint");
        endpointField.trySetAccessible();
        String endpoint = (String) ReflectionUtils.getField(endpointField, namedTransport.transport());

        var paramsAfter = replace.apply(new McpServerMetadata.HttpServerMetadata(this.connectedClientName(commonProperties.getName(), namedTransport.name()), endpoint));
        System.out.printf("HAVENT LOOKED AT HOW NAME IS SET FOR HTTP YET: %s%n", paramsAfter.toString());

//        var nameAfter = paramsAfter.name;
//        var serverParamsAfter = paramsAfter.serverParameters;

        if (uri == null) {
            log.error("Could not find valid endpoint for name {}.", clientName);
            return Result.err(new McpError("Could not find valid endpoint for name " + clientName));
        }

        if (this.clientConcurrentHashMap.containsKey(uri.toString())
                && this.clientConcurrentHashMap.get(uri.toString()).isInitialized()) {
            return Result.ok(this.clientConcurrentHashMap.get(uri.toString()));
        }

        var t = HttpClientSseClientTransport.builder(uri.toString())
                .sseEndpoint(endpoint)
                .build();

        var client = buildInitializeClient(uri.toString(), McpClient.sync(t), namedTransport);

        clientConcurrentHashMap.put(clientName, client);

        return Result.ok(client);
    }

    private McpSyncClient buildInitializeClient(String uri, McpClient.SyncSpec t, NamedClientMcpTransport namedTransport) {
        McpSchema.Implementation clientInfo = new McpSchema.Implementation(
                uri,
                commonProperties.getVersion());

        McpClient.SyncSpec spec = t
                .clientInfo(clientInfo)
                .initializationTimeout(Duration.ofSeconds(120))
                .requestTimeout(Duration.ofSeconds(120));

        spec = mcpSyncClientConfigurer.configure(namedTransport.name(), spec);

        var client = spec.build();

        if (commonProperties.isInitialized()) {
            client.initialize();
        }
        return client;
    }

    private Result<McpSyncClient, McpError> initializeStdioMcpSyncClient(String clientName,
                                                                                      ServerCustomizer.StdioServerCustomizer replace,
                                                                                      NamedClientMcpTransport namedTransport) throws NoSuchFieldException {
        Field paramsField = namedTransport.transport().getClass().getDeclaredField("params");
        paramsField.trySetAccessible();
        ServerParameters params = (ServerParameters) ReflectionUtils.getField(paramsField, namedTransport.transport());

        if (params == null)
            return Result.err(new McpError("Could not find valid params for name %s.".formatted(clientName)));

        var paramsAfter = replace.apply(new McpServerMetadata.StdioServerMetadata(this.connectedClientName(commonProperties.getName(), namedTransport.name()), params));

        var nameAfter = paramsAfter.name;
        var serverParamsAfter = paramsAfter.serverParameters;

        if (this.clientConcurrentHashMap.containsKey(nameAfter)
                && this.clientConcurrentHashMap.get(nameAfter).isInitialized()) {
            return Result.ok(this.clientConcurrentHashMap.get(nameAfter));
        } else {
            doStopExistingNotInMap(serverParamsAfter);
        }

        var t = new StdioClientTransport(serverParamsAfter);
        var client = buildInitializeClient(nameAfter, McpClient.sync(t), namedTransport);

        clientConcurrentHashMap.put(clientName, client);

        return Result.ok(client);
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
            } catch (InterruptedException |
                     IOException e) {
                log.error("Error when stopping container {}", e.getMessage());
            }
        }
    }

}
