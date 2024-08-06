package io.github.lianjordaan.bytebuildersproxyplugin;

import com.google.gson.*;
import com.google.inject.Inject;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.*;
import net.kyori.adventure.text.Component;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Plugin(
        id = "bytebuilders_proxy_plugin",
        name = "ByteBuilders Proxy Plugin",
        version = "1.0"
)
public class ByteBuildersProxyPlugin {

    private final ProxyServer server;
    private final Logger logger;

    private WebSocketClient webSocketClient;

    @Inject
    public ByteBuildersProxyPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {

        startServerStatusUpdater();
        logger.info("ByteBuilders Proxy Plugin initialized!");
        try {
            webSocketClient = new WebSocketClient(new URI("ws://localhost:3000")) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    logger.info("WebSocket connection opened");
                    send("Hello from Minecraft Velocity plugin!");
                }

                @Override
                public void onMessage(String message) {
                    logger.info("Received message: {}", message);
                    // Handle incoming messages from WebSocket server
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    logger.info("WebSocket connection closed: {}", reason);
                }

                @Override
                public void onError(Exception ex) {
                    logger.error("WebSocket error", ex);
                }
            };
            webSocketClient.connect();
        } catch (Exception e) {
            logger.error("Failed to initialize WebSocket client", e);
        }
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        String message = event.getMessage();
        if (message.startsWith("start")) {
            String[] parts = message.split(" ");
            if (parts.length == 2) {
                String port = parts[1];
                handleStartCommand(port);
                event.setResult(PlayerChatEvent.ChatResult.denied()); // Prevent the command from being shown in chat
            } else {
                event.getPlayer().sendMessage(Component.text("Invalid command usage. Use start <port>"));
            }
        }
    }

    private void handleStartCommand(String port) {
        // Start the server by making a web request
        CompletableFuture.runAsync(() -> {
            try {
                // Make HTTP request to start the server
                URL url = new URL("http://localhost:3000/start-server");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                String jsonInputString = String.format("{\"port\": \"%s\"}", port);
                connection.getOutputStream().write(jsonInputString.getBytes(StandardCharsets.UTF_8));

                // Check response
                try (Scanner scanner = new Scanner(connection.getInputStream())) {
                    String response = scanner.useDelimiter("\\A").next();
                    logger.info("Server start response: {}", response);
                    if (response.contains("\"success\":true")) {
                        // will do things later
                    } else {
                        logger.error("Failed to start the server.");
                    }
                }
            } catch (Exception e) {
                logger.error("Error starting the server", e);
            }
        });
    }

    private void startServerStatusUpdater() {
        // Create and start a new thread
        Executors.newSingleThreadExecutor().execute(() -> {
            while (true) {
                try {
                    updateServerList();
                    // Sleep for 5 seconds
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Server status updater thread interrupted", e);
                }
            }
        });
    }

    private void updateServerList() {
        CompletableFuture.runAsync(() -> {
            try {
                // Make HTTP request to list server statuses
                URL url = new URL("http://localhost:3000/list-server-statuses");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                // Check response
                try (Scanner scanner = new Scanner(connection.getInputStream())) {
                    String response = scanner.useDelimiter("\\A").next();
//                    logger.info("List server statuses response: {}", response);

                    // Parse the response JSON using Gson
                    JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();

                    Set<String> serversInResponse = jsonObject.keySet();

                    for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                        // Get the status from the JSON object
                        String status = entry.getValue().getAsJsonObject().get("status").getAsString();

                        // Check if the status is "running"
                        if (Objects.equals(status, "running")) {
                            String serverId = "dyn-" + entry.getKey();

                            // Check if the server is not already registered
                            if (!this.server.getServer(serverId).isPresent()) {
                                // Register the server with the specified address and port
                                int port = Integer.parseInt(entry.getKey());
                                this.server.registerServer(new ServerInfo(serverId, new InetSocketAddress("localhost", port)));
                                this.server.sendMessage(Component.text("Server dyn-" + port + " is ready for players.."));
                            }
                        }
                    }

                    for (RegisteredServer registeredServer : this.server.getAllServers()) {
                        String serverID = registeredServer.getServerInfo().getName();
                        if (serverID.equalsIgnoreCase("lobby")) {
                            continue;
                        }

                        if (!serversInResponse.contains(serverID.replace("dyn-", ""))) {
                            int port = Integer.parseInt(serverID.replace("dyn-", ""));
                            this.server.unregisterServer(new ServerInfo(serverID, new InetSocketAddress("localhost", port)));
                            this.server.sendMessage(Component.text("Server dyn-" + port + " is no longer ready for players.."));
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error updating server list", e);
            }
        });
    }
}
