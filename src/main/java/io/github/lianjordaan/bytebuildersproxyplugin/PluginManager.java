package io.github.lianjordaan.bytebuildersproxyplugin;

import com.velocitypowered.api.proxy.ProxyServer;
import org.java_websocket.client.WebSocketClient;
import org.slf4j.Logger;

public class PluginManager {

    private final ProxyServer server;
    private final Logger logger;
    private WebSocketClient webSocketClient;

    public PluginManager(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public WebSocketClient getWebSocketClient() {
        return webSocketClient;
    }

    public void setWebSocketClient(WebSocketClient webSocketClient) {
        this.webSocketClient = webSocketClient;
    }
}
