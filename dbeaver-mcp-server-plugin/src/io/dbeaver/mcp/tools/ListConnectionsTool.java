package io.dbeaver.mcp.tools;

import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.dbeaver.mcp.db.DBeaverConnections;
import io.dbeaver.mcp.mcp.McpTool;

/** Lists every connection configured in DBeaver, with its id and endpoint. */
public class ListConnectionsTool implements McpTool {

    @Override
    public String name() {
        return "list_connections";
    }

    @Override
    public String description() {
        return "List all database connections configured in DBeaver. Returns each connection's id "
                + "(use it as connectionId for other tools), name, driver, host, port, database and "
                + "whether it is currently connected.";
    }

    @Override
    public JsonObject inputSchema() {
        return Schemas.object();
    }

    @Override
    public String execute(JsonObject arguments) {
        JsonArray connections = new JsonArray();
        for (DBPDataSourceContainer container : DBeaverConnections.all()) {
            DBPConnectionConfiguration cfg = container.getConnectionConfiguration();
            JsonObject entry = new JsonObject();
            entry.addProperty("connectionId", container.getId());
            entry.addProperty("name", container.getName());
            entry.addProperty("driver", container.getDriver() != null ? container.getDriver().getName() : null);
            entry.addProperty("host", cfg != null ? cfg.getHostName() : null);
            entry.addProperty("port", cfg != null ? cfg.getHostPort() : null);
            entry.addProperty("database", cfg != null ? cfg.getDatabaseName() : null);
            entry.addProperty("user", cfg != null ? cfg.getUserName() : null);
            entry.addProperty("connected", container.isConnected());
            connections.add(entry);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(connections);
    }
}
