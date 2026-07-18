package io.dbeaver.mcp.tools;

import org.jkiss.dbeaver.model.DBPDataSourceContainer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import io.dbeaver.mcp.db.DBeaverConnections;
import io.dbeaver.mcp.db.SchemaExplorer;
import io.dbeaver.mcp.mcp.McpTool;

/** Explores database structure (catalogs, schemas, tables, columns). */
public class GetSchemaTool implements McpTool {

    @Override
    public String name() {
        return "get_schema";
    }

    @Override
    public String description() {
        return "Explore the structure of an already-open DBeaver connection. Call with just connectionId "
                + "to list top-level catalogs/schemas. Pass a dotted path (e.g. \"public\" or "
                + "\"mydb.public\") to drill in; when the path points at a table, its columns are returned. "
                + "The tool never opens a closed connection.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "connectionId", "string", "The connection id from list_connections.");
        Schemas.prop(schema, "path", "string",
                "Optional dotted path to drill into, e.g. \"public\" or \"mydb.public.users\".");
        return Schemas.required(schema, "connectionId");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String connectionId = Schemas.requireString(arguments, "connectionId");
        String path = Schemas.optString(arguments, "path", "");

        DBPDataSourceContainer container = DBeaverConnections.find(connectionId);
        if (container == null) {
            throw new IllegalArgumentException("Unknown connectionId: " + connectionId);
        }
        JsonObject result = SchemaExplorer.explore(container, path);
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }
}
