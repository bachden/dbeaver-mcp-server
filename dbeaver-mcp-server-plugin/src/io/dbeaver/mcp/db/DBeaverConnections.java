package io.dbeaver.mcp.db;

import java.util.ArrayList;
import java.util.List;

import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.app.DBPDataSourceRegistry;
import org.jkiss.dbeaver.model.app.DBPProject;
import org.jkiss.dbeaver.model.app.DBPWorkspace;
import org.jkiss.dbeaver.runtime.DBWorkbench;

/**
 * Thin accessor over DBeaver's data source registry. Every connection the user
 * has configured in DBeaver (across all open projects) is reachable here — no
 * credential decryption needed, DBeaver already owns the live registry.
 */
public final class DBeaverConnections {

    private DBeaverConnections() {
    }

    /** All connections configured across every project in the workspace. */
    public static List<DBPDataSourceContainer> all() {
        List<DBPDataSourceContainer> result = new ArrayList<>();
        DBPWorkspace workspace = DBWorkbench.getPlatform().getWorkspace();
        for (DBPProject project : workspace.getProjects()) {
            DBPDataSourceRegistry registry = project.getDataSourceRegistry();
            if (registry != null) {
                result.addAll(registry.getDataSources());
            }
        }
        return result;
    }

    /** Look up a connection by its DBeaver id, or {@code null} if unknown. */
    public static DBPDataSourceContainer find(String id) {
        if (id == null) {
            return null;
        }
        for (DBPDataSourceContainer container : all()) {
            if (id.equals(container.getId())) {
                return container;
            }
        }
        return null;
    }

    /**
     * Return the already-open data source. MCP tools never open connections on the
     * user's behalf so DBeaver remains the source of truth for connection state.
     */
    public static DBPDataSource requireOpen(DBPDataSourceContainer container) {
        if (!container.isConnected()) {
            throw new IllegalStateException("Connection is not open in DBeaver: " + container.getName()
                    + ". Open it manually in DBeaver before using MCP tools.");
        }
        DBPDataSource dataSource = container.getDataSource();
        if (dataSource == null) {
            throw new IllegalStateException("Connection has no active data source in DBeaver: " + container.getName());
        }
        return dataSource;
    }
}
