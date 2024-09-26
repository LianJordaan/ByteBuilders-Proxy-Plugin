package io.github.lianjordaan.bytebuildersproxyplugin;

import com.google.gson.*;
import com.google.inject.Inject;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.*;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.units.qual.C;
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
    public void onPlayerJoin(PlayerChooseInitialServerEvent event) {
        CompletableFuture.runAsync(() -> {
            try {
                // Make HTTP request to start the server
                URL url = new URL("http://localhost:3000/player/login");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                String jsonInputString = String.format("{\"uuid\": \"%s\", \"username\": \"%s\"}", event.getPlayer().getUniqueId(), event.getPlayer().getUsername());
                connection.getOutputStream().write(jsonInputString.getBytes(StandardCharsets.UTF_8));
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    logger.info("Player logged in successfully");
                } else {
                    logger.error("Error while logging in player");
                    event.getPlayer().disconnect(Component.text("Error while logging in player. Please try again later. If the problem persists, please contact the server administrator."));
                }
            } catch (Exception e) {
                logger.error("Error while registering player", e);
                event.getPlayer().disconnect(Component.text("Error while registering player. Please try again later. If the problem persists, please contact the server administrator."));
            }
        });
    }
}
