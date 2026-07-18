package io.dbeaver.mcp.tools;

import org.jkiss.dbeaver.model.DBPDataSourceContainer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import io.dbeaver.mcp.db.DBeaverConnections;
import io.dbeaver.mcp.db.SqlRunner;
import io.dbeaver.mcp.mcp.McpTool;

/**
 * Read-only query tool. Rejects anything that is not a SELECT-style statement
 * so an agent cannot mutate data through this entry point.
 */
public class RunQueryTool implements McpTool {

    private static final int DEFAULT_MAX_ROWS = 1000;

    @Override
    public String name() {
        return "run_query";
    }

    @Override
    public String description() {
        return "Run a READ-ONLY SQL query (SELECT / WITH / SHOW / EXPLAIN / DESCRIBE) against an "
                + "already-open DBeaver connection and return the rows. Closed connections are rejected. "
                + "Writes and DDL are rejected; use execute_sql for those.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "connectionId", "string", "The connection id from list_connections.");
        Schemas.prop(schema, "sql", "string", "A read-only SQL statement.");
        Schemas.prop(schema, "maxRows", "integer", "Maximum rows to return (default " + DEFAULT_MAX_ROWS + ").");
        return Schemas.required(schema, "connectionId", "sql");
    }

    @Override
    public String execute(JsonObject arguments) throws Exception {
        String connectionId = Schemas.requireString(arguments, "connectionId");
        String sql = Schemas.requireString(arguments, "sql");
        int maxRows = Schemas.optInt(arguments, "maxRows", DEFAULT_MAX_ROWS);

        if (!isReadOnly(sql)) {
            throw new IllegalArgumentException(
                    "run_query only accepts read-only statements (SELECT/WITH/SHOW/EXPLAIN/DESCRIBE). "
                            + "Use execute_sql for writes.");
        }

        DBPDataSourceContainer container = DBeaverConnections.find(connectionId);
        if (container == null) {
            throw new IllegalArgumentException("Unknown connectionId: " + connectionId);
        }
        JsonObject result = SqlRunner.query(container, sql, maxRows);
        return new GsonBuilder().setPrettyPrinting().create().toJson(result);
    }

    private static boolean isReadOnly(String sql) {
        String s = stripLeadingComments(sql).toLowerCase();
        return s.startsWith("select") || s.startsWith("with") || s.startsWith("show") || s.startsWith("explain")
                || s.startsWith("describe") || s.startsWith("desc") || s.startsWith("values") || s.startsWith("table")
                || s.startsWith("pragma");
    }

    private static String stripLeadingComments(String sql) {
        String s = sql.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            if (s.startsWith("--")) {
                int nl = s.indexOf('\n');
                s = (nl < 0 ? "" : s.substring(nl + 1)).trim();
                changed = true;
            } else if (s.startsWith("/*")) {
                int end = s.indexOf("*/");
                s = (end < 0 ? "" : s.substring(end + 2)).trim();
                changed = true;
            }
        }
        return s;
    }
}
