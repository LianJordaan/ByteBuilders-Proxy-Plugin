package io.github.lianjordaan.bytebuildersproxyplugin;

import com.google.inject.Inject;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.*;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

import static java.lang.Integer.parseInt;

@Plugin(
        id = "bytebuilders_proxy_plugin",
        name = "ByteBuilders Proxy Plugin",
        version = "1.0"
)
public class ByteBuildersProxyPlugin {

    private final ProxyServer server;
    private final Logger logger;

    @Inject
    public ByteBuildersProxyPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("ByteBuilders Proxy Plugin initialized!");
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        String message = event.getMessage();
        if (message.startsWith("start")) {
            String[] parts = message.split(" ");
            if (parts.length == 3) {
                String port = parts[1];
                String plotId = parts[2];
                handleStartCommand(port, plotId);
                event.setResult(PlayerChatEvent.ChatResult.denied()); // Prevent the command from being shown in chat
            } else {
                event.getPlayer().sendMessage(Component.text("Invalid command usage. Use start <port> <id>"));
            }
        }

        if (message.startsWith("stop")) {
            String[] parts = message.split(" ");
            if (parts.length == 2) {
                String port = parts[1];
                handleStopCommand(port);
                event.setResult(PlayerChatEvent.ChatResult.denied()); // Prevent the command from being shown in chat
            } else {
                event.getPlayer().sendMessage(Component.text("Invalid command usage. Use stop <port>"));
            }
        }
    }

    private void handleStartCommand(String port, String plotId) {
        // Start the server by making a web request
        CompletableFuture.runAsync(() -> {
            try {
                // Make HTTP request to start the server
                URL url = new URL("http://localhost:3000/start-server");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                String jsonInputString = String.format("{\"port\": \"%s\", \"plotId\": \"%s\"}", port, plotId);
                connection.getOutputStream().write(jsonInputString.getBytes(StandardCharsets.UTF_8));

                // Check response
                try (Scanner scanner = new Scanner(connection.getInputStream())) {
                    String response = scanner.useDelimiter("\\A").next();
                    logger.info("Server start response: {}", response);
                    if (response.contains("\"success\":true")) {
                        // Request list of plots
                        server.registerServer(new ServerInfo("dyn-" + port, new InetSocketAddress("localhost", parseInt(port))));
                        handleListPlots(port);
                    } else {
                        logger.error("Failed to start the server.");
                    }
                }
            } catch (Exception e) {
                logger.error("Error starting the server", e);
            }
        });
    }

    private void handleStopCommand(String port) {
        // Start the server by making a web request
        CompletableFuture.runAsync(() -> {
            try {
                // Make HTTP request to start the server
                URL url = new URL("http://localhost:3000/stop-server");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                String jsonInputString = String.format("{\"port\": \"%s\"}", port);
                connection.getOutputStream().write(jsonInputString.getBytes(StandardCharsets.UTF_8));

                // Check response
                try (Scanner scanner = new Scanner(connection.getInputStream())) {
                    String response = scanner.useDelimiter("\\A").next();
                    logger.info("Server stop response: {}", response);
                    if (response.contains("\"success\":true")) {
                        server.unregisterServer(new ServerInfo("dyn-" + port, new InetSocketAddress("localhost", parseInt(port))));
                    } else {
                        logger.error("Failed to stop the server.");
                    }
                }
            } catch (Exception e) {
                logger.error("Error starting the server", e);
            }
        });
    }

    private void handleListPlots(String port) {
        CompletableFuture.runAsync(() -> retryWithBackoff(() -> {
            try {
                // Make HTTP request to list plots
                URL url = new URL("http://localhost:3000/list-plots");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                // Check response
                try (Scanner scanner = new Scanner(connection.getInputStream())) {
                    String response = scanner.useDelimiter("\\A").next();
                    logger.info("List plots response: {}", response);

                    if (response.contains("\"name\":\"/dyn-" + port + "\"") && response.contains("\"status\":true")) {
                        // Send player to the plot
                        sendToPlot(port, 10);
                        return true;
                    } else {
                        logger.error("Plot {} not found or status is false.", port);
                    }
                }
            } catch (Exception e) {
                logger.error("Error listing plots", e);
            }
            return false;
        }, 3, 2000)); // Retry 3 times with 2 seconds delay
    }

    private boolean retryWithBackoff(RunnableWithReturn task, int maxRetries, int delayMillis) {
        int retryCount = 0;
        while (retryCount < maxRetries) {
            try {
                if (task.run()) {
                    return true;
                }
            } catch (Exception e) {
                // Log error if needed
            }

            retryCount++;
            try {
                Thread.sleep(delayMillis); // Wait before retrying
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    private void sendToPlot(String port, int retriesLeft) {
        server.getPlayer("LianJordaan").ifPresent(player -> {
            RegisteredServer targetServer = server.getServer("dyn-" + port).orElse(null);
            if (targetServer != null) {
                player.createConnectionRequest(targetServer).connect().exceptionally(throwable -> {
                    if (retriesLeft > 0) {
//                        logger.error("Failed to connect player to plot. Retries left: {}", retriesLeft, throwable);
                        retryConnection(player, targetServer, retriesLeft);
                    } else {
                        logger.error("Failed to connect player to plot after maximum retries.", throwable);
                    }
                    return null;
                });
            } else {
                logger.error("Server {} not found.", port);
            }
        });
    }

    private void retryConnection(Player player, RegisteredServer targetServer, int retriesLeft) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000); // Wait before retrying
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted status
                logger.error("Retry interrupted", e);
            }
            // Retry by calling sendToPlot with decremented retries
            sendToPlot(targetServer.getServerInfo().getName().replace("dyn-", ""), retriesLeft - 1);
        });
    }

    @FunctionalInterface
    private interface RunnableWithReturn {
        boolean run() throws Exception;
    }
}
