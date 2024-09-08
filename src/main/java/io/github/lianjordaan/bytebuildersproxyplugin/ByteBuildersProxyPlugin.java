package io.github.lianjordaan.bytebuildersproxyplugin;

import com.google.gson.*;
import com.google.inject.Inject;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.*;
import net.kyori.adventure.text.Component;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Plugin(
        id = "bytebuilders_proxy_plugin",
        name = "ByteBuilders Proxy Plugin",
        version = "1.0"
)
public class ByteBuildersProxyPlugin {

    private PluginManager pluginManager;
    private ProxyServer server;
    private Logger logger;

    @Inject
    public ByteBuildersProxyPlugin(ProxyServer server, Logger logger) {
        this.pluginManager = new PluginManager(server, logger);
        this.server = pluginManager.getServer();
        this.logger = pluginManager.getLogger();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
//        startServerStatusUpdater();
        logger.info("ByteBuilders Proxy Plugin initialized!");

        try {
            Properties env = EnvLoader.loadEnv();
            String username = env.getProperty("USERNAME");
            Objects.requireNonNull(username, "USERNAME not set in .env file");
            WebSocketClient webSocketClient = new WebSocketClientHandler(new URI("ws://localhost:3000?username=" + username + "&id=proxy"), pluginManager);
            pluginManager.setWebSocketClient(webSocketClient);
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
                String id = parts[1];
                handleStartCommand(id, event.getPlayer());
                event.setResult(PlayerChatEvent.ChatResult.denied()); // Prevent the command from being shown in chat
            } else {
                event.getPlayer().sendMessage(Component.text("Invalid command usage. Use start <port>"));
            }
        }
    }

    private void handleStartCommand(String id, Player player) {
        // Start the server by making a web request
        CompletableFuture.runAsync(() -> {
            try {
                // Make HTTP request to start the server
                URL url = new URL("http://localhost:3000/start-server");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                String jsonInputString = String.format("{\"id\": \"%s\"}", id);
                connection.getOutputStream().write(jsonInputString.getBytes(StandardCharsets.UTF_8));
                int responseCode = connection.getResponseCode();

                // Read response based on the status code
                try (Scanner scanner = new Scanner(responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream())) {
                    String response = scanner.useDelimiter("\\A").next();
                    logger.info("Server start response: {}", response);
                    JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

                    if (responseCode == 200) {
                        server.sendMessage(Component.text("Empty server found, joining..."));
                        try {
                            server.registerServer(new ServerInfo("dyn-" + jsonResponse.get("port").getAsInt(), new InetSocketAddress("localhost", jsonResponse.get("port").getAsInt())));
                        } catch (Exception ignored) {
                        }
                        server.getPlayer(player.getUniqueId()).ifPresent(player1 -> player1.createConnectionRequest(this.server.registerServer(new ServerInfo("dyn-" + jsonResponse.get("port").getAsInt(), new InetSocketAddress("localhost", jsonResponse.get("port").getAsInt())))).connect());
                    } else if (responseCode == 409) {
                        server.sendMessage(Component.text("Server already registered. Joining..."));
                        try {
                            server.registerServer(new ServerInfo("dyn-" + jsonResponse.get("port").getAsInt(), new InetSocketAddress("localhost", jsonResponse.get("port").getAsInt())));
                        } catch (Exception ignored) {
                        }
                        server.getPlayer(player.getUniqueId()).ifPresent(player1 -> player1.createConnectionRequest(this.server.registerServer(new ServerInfo("dyn-" + jsonResponse.get("port").getAsInt(), new InetSocketAddress("localhost", jsonResponse.get("port").getAsInt())))).connect());
                    } else if (responseCode == 404) {
                        server.sendMessage(Component.text("No empty server was found, starting a new one. Please wait..."));
                    } else if (responseCode == 202) {
                        server.getPlayer(player.getUniqueId()).ifPresent(player1 -> player1.sendMessage(Component.text("A server is starting, please wait for it to become available, and then try again.")));
                    } else {
                        logger.error("Failed to start the server with response code: {}", responseCode);
                    }
                }
            } catch (IOException e) {
                logger.error("Error starting the server", e);
            }
        });
    }

//    private void startServerStatusUpdater() {
//        // Create and start a new thread
//        Executors.newSingleThreadExecutor().execute(() -> {
//            while (true) {
//                try {
//                    updateServerList();
//                    // Sleep for 5 seconds
//                    Thread.sleep(5000);
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                    logger.error("Server status updater thread interrupted", e);
//                }
//            }
//        });
//    }

//    private void updateServerList() {
//        CompletableFuture.runAsync(() -> {
//            try {
//                // Make HTTP request to list server statuses
//                URL url = new URL("http://localhost:3000/list-server-statuses");
//                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
//                connection.setRequestMethod("GET");
//
//                // Check response
//                try (Scanner scanner = new Scanner(connection.getInputStream())) {
//                    String response = scanner.useDelimiter("\\A").next();
////                    logger.info("List server statuses response: {}", response);
//
//                    // Parse the response JSON using Gson
//                    JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
//
//                    Set<String> serversInResponse = jsonObject.keySet();
//
//                    for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
//                        // Get the status from the JSON object
//                        String status = entry.getValue().getAsJsonObject().get("status").getAsString();
//
//                        // Check if the status is "running"
//                        if (Objects.equals(status, "running")) {
//                            String serverId = "dyn-" + entry.getKey();
//
//                            // Check if the server is not already registered
//                            if (!this.server.getServer(serverId).isPresent()) {
//                                // Register the server with the specified address and port
//                                int port = Integer.parseInt(entry.getKey());
//                                this.server.registerServer(new ServerInfo(serverId, new InetSocketAddress("localhost", port)));
//                                this.server.sendMessage(Component.text("Server dyn-" + port + " is ready for players.."));
//                            }
//                        }
//                    }
//
//                    for (RegisteredServer registeredServer : this.server.getAllServers()) {
//                        String serverID = registeredServer.getServerInfo().getName();
//                        if (serverID.equalsIgnoreCase("lobby")) {
//                            continue;
//                        }
//
//                        if (!serversInResponse.contains(serverID.replace("dyn-", ""))) {
//                            int port = Integer.parseInt(serverID.replace("dyn-", ""));
//                            this.server.unregisterServer(new ServerInfo(serverID, new InetSocketAddress("localhost", port)));
//                            this.server.sendMessage(Component.text("Server dyn-" + port + " is no longer ready for players.."));
//                        }
//                    }
//                }
//            } catch (Exception e) {
//                logger.error("Error updating server list", e);
//            }
//        });
//    }
}
