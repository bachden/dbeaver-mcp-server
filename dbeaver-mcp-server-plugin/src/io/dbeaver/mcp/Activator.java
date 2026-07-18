package io.dbeaver.mcp;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import io.dbeaver.mcp.server.McpConnectionLog;
import io.dbeaver.mcp.server.McpHttpServer;
import io.dbeaver.mcp.ui.McpStatusBarContribution;

/**
 * OSGi lifecycle hook. Holds the singleton MCP HTTP server so it can be stopped
 * cleanly when DBeaver shuts down. The server is started from
 * {@link McpPluginService} after the DBeaver platform and workspace are fully
 * initialized.
 */
public class Activator implements BundleActivator {

    public static final String PLUGIN_ID = "io.dbeaver.mcp";

    /** Notified whenever the server transitions between running/stopped. */
    public interface ServerStateListener {
        void onServerStateChanged(boolean running);
    }

    private static Activator instance;
    private McpHttpServer server;
    private final List<ServerStateListener> listeners = new CopyOnWriteArrayList<>();

    public static Activator getDefault() {
        return instance;
    }

    @Override
    public void start(BundleContext context) {
        instance = this;
    }

    @Override
    public void stop(BundleContext context) {
        McpStatusBarContribution.uninstall();
        listeners.clear();
        stopServer();
        instance = null;
    }

    public void addServerStateListener(ServerStateListener listener) {
        listeners.add(listener);
    }

    public void removeServerStateListener(ServerStateListener listener) {
        listeners.remove(listener);
    }

    public synchronized void startServer() {
        if (server != null) {
            return;
        }
        McpHttpServer candidate = new McpHttpServer();
        try {
            candidate.setAuth(McpServerPreferences::isAuthEnabled, McpServerPreferences::getAuthToken);
            candidate.start(McpServerPreferences.getHost(), McpServerPreferences.getPort());
            server = candidate;
        } catch (RuntimeException e) {
            server = null;
            throw e;
        } finally {
            fireStateChanged();
        }
    }

    public synchronized void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
        fireStateChanged();
    }

    public synchronized void restartServer() {
        stopServer();
        startServer();
    }

    public synchronized boolean isServerRunning() {
        return server != null && server.isRunning();
    }

    /**
     * Returns the running server's connection log, or {@code null} if not running.
     */
    public synchronized McpConnectionLog getConnectionLog() {
        return server != null ? server.getConnectionLog() : null;
    }

    private void fireStateChanged() {
        boolean running = isServerRunning();
        for (ServerStateListener listener : listeners) {
            listener.onServerStateChanged(running);
        }
    }
}
