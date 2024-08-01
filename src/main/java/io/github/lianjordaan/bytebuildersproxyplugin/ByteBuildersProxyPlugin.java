package io.github.lianjordaan.bytebuildersproxyplugin;

import com.google.inject.Inject;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.slf4j.Logger;

import java.net.InetSocketAddress;

import static java.lang.Integer.parseInt;
import static java.lang.Integer.valueOf;

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
        this.registerServer("dyn1", new InetSocketAddress("134.195.89.227", 25567));
        logger.info("ByteBuilders Proxy Plugin initialized!");
    }

    public void registerServer(String name, InetSocketAddress address) {
        ServerInfo serverInfo = new ServerInfo(name, address);
        server.registerServer(serverInfo);
        logger.info("Server {} registered with address {}", name, address);
    }

    public void unregisterServer(String name) {
        server.getServer(name).ifPresent(serverInfo -> {
            server.unregisterServer(serverInfo.getServerInfo());
            logger.info("Server {} unregistered", name);
        });
    }
}
