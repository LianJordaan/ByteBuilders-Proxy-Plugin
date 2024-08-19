package io.github.lianjordaan.bytebuildersproxyplugin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.net.URI;

public class WebSocketClientHandler extends WebSocketClient {

    private final PluginManager pluginManager;
    private final ProxyServer server;
    private final Logger logger;

    // Constructor accepts PluginManager and Logger
    public WebSocketClientHandler(URI serverUri, PluginManager pluginManager) {
        super(serverUri);
        this.pluginManager = pluginManager;

        this.server = pluginManager.getServer();
        this.logger = pluginManager.getLogger();
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("WebSocket connection opened");
        send("{\"type\": \"message\", \"message\": \"Hello from Minecraft Velocity plugin!\"}");
    }

    @Override
    public void onMessage(String message) {
        logger.info("Received message: {}", message);

        try {
            // Parse the incoming message
            JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
            String type = jsonMessage.get("type").getAsString();

            if ("forwarded-message".equals(type)) {
                String from = jsonMessage.get("from").getAsString();
                String forwardedMessage = jsonMessage.get("message").getAsString();

                if ("running".equals(forwardedMessage)) {
                    // Construct the server key
                    String serverKey = "dyn-" + from;

                    // Update the server list with the new status
                    // Ensure you have a method in PluginManager to handle this
                    try {
                        server.registerServer(new ServerInfo(serverKey, new InetSocketAddress("localhost", Integer.parseInt(from))));
                    } catch (Exception ignored) {
                    }
                    server.sendMessage(Component.text("Server dyn-" + from + " is ready for players.."));

                    logger.info("Server {} status updated to {}", serverKey, forwardedMessage);
                } else if ("stopping".equals(forwardedMessage)) {
                    // Construct the server key
                    String serverKey = "dyn-" + from;

                    // Update the server list with the new status
                    // Ensure you have a method in PluginManager to handle this
                    try {
                        server.unregisterServer(new ServerInfo(serverKey, new InetSocketAddress("localhost", Integer.parseInt(from))));
                    } catch (Exception ignored) {
                    }
                    server.sendMessage(Component.text("Server dyn-" + from + " is no longer ready for players.."));

                    logger.info("Server {} status updated to {}", serverKey, forwardedMessage);
                }
            }
        } catch (JsonParseException e) {
            logger.error("Failed to parse message", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        pluginManager.getLogger().info("WebSocket connection closed: {}", reason);
    }

    @Override
    public void onError(Exception ex) {
        pluginManager.getLogger().error("WebSocket error", ex);
    }

}