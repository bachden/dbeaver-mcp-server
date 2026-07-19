package io.dbeaver.mcp.tools;

import org.jkiss.dbeaver.model.DBPDataSourceContainer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import io.dbeaver.mcp.db.DBeaverConnections;
import io.dbeaver.mcp.db.SqlRunner;
import io.dbeaver.mcp.db.UiSqlRunner;
import io.dbeaver.mcp.mcp.McpTool;

/**
 * Full read-write SQL tool: INSERT/UPDATE/DELETE/DDL are all allowed. This is
 * powerful and unguarded — expose it only to trusted agents.
 */
public class ExecuteSqlTool implements McpTool {

    private static final int DEFAULT_MAX_ROWS = 1000;

    @Override
    public String name() {
        return "execute_sql";
    }

    @Override
    public String description() {
        return "Execute ANY SQL statement (INSERT/UPDATE/DELETE/DDL as well as SELECT) against an "
                + "already-open DBeaver connection. Closed connections are rejected. Returns affected row count, "
                + "or rows for statements that produce a result set. WARNING: this can modify or drop data.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "connectionId", "string", "The connection id from list_connections.");
        Schemas.prop(schema, "sql", "string", "The SQL statement to execute.");
        Schemas.prop(schema, "maxRows", "integer",
                "Maximum rows to return if the statement yields a result set (default " + DEFAULT_MAX_ROWS + ").");
        Schemas.prop(schema, "showOnUi", "boolean",
                "Also execute in a visible DBeaver SQL editor and show its result grid (default false).");
        return Schemas.required(schema, "connectionId", "sql");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String connectionId = Schemas.requireString(arguments, "connectionId");
        String sql = Schemas.requireString(arguments, "sql");
        int maxRows = Schemas.optInt(arguments, "maxRows", DEFAULT_MAX_ROWS);
        boolean showOnUi = Schemas.optBoolean(arguments, "showOnUi", false);

        DBPDataSourceContainer container = DBeaverConnections.find(connectionId);
        if (container == null) {
            throw new IllegalArgumentException("Unknown connectionId: " + connectionId);
        }
        JsonObject result = showOnUi ? UiSqlRunner.query(container, sql, maxRows)
                : SqlRunner.query(container, sql, maxRows);
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }
}
