package io.dbeaver.mcp;

import org.jkiss.dbeaver.runtime.IPluginService;

import io.dbeaver.mcp.ui.McpStatusBarContribution;

/**
 * Starts and stops the MCP server with DBeaver's platform lifecycle. DBeaver
 * activates plugin services after the workspace and data source registries are
 * ready.
 */
public final class McpPluginService implements IPluginService {

    @Override
    public void activateService() {
        Activator activator = Activator.getDefault();
        if (activator != null && McpServerPreferences.isServerEnabled()) {
            activator.startServer();
        }
        McpStatusBarContribution.install();
    }

    @Override
    public void deactivateService() {
        McpStatusBarContribution.uninstall();
        Activator activator = Activator.getDefault();
        if (activator != null) {
            activator.stopServer();
        }
    }
}
