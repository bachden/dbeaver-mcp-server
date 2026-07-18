package io.dbeaver.mcp.db;

import java.util.List;

import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.exec.DBCAttributeMetaData;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.exec.DBCResultSet;
import org.jkiss.dbeaver.model.exec.DBCResultSetMetaData;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.exec.DBCStatement;
import org.jkiss.dbeaver.model.exec.DBCStatementType;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Executes SQL against a DBeaver connection using DBeaver's own execution
 * pipeline (so it works for any driver DBeaver supports, JDBC or otherwise).
 */
public final class SqlRunner {

    private SqlRunner() {
    }

    /**
     * Run a query and return {columns, rows, rowCount, truncated}. Also used for
     * statements that happen to produce a result set.
     */
    public static JsonObject query(DBPDataSourceContainer container, String sql, int maxRows) throws Exception {
        DBRProgressMonitor monitor = new VoidProgressMonitor();
        DBPDataSource dataSource = DBeaverConnections.requireOpen(container);
        DBCExecutionContext context = dataSource.getDefaultInstance().getDefaultContext(monitor, false);

        JsonObject out = new JsonObject();
        JsonArray columns = new JsonArray();
        JsonArray rows = new JsonArray();

        DBCSession session = context.openSession(monitor, DBCExecutionPurpose.USER, "MCP query");
        try {
            DBCStatement stmt = session.prepareStatement(DBCStatementType.SCRIPT, sql, false, false, false);
            try {
                boolean hasResultSet = stmt.executeStatement();
                if (hasResultSet) {
                    DBCResultSet rs = stmt.openResultSet();
                    try {
                        DBCResultSetMetaData meta = rs.getMeta();
                        List<? extends DBCAttributeMetaData> attrs = meta.getAttributes();
                        for (DBCAttributeMetaData attr : attrs) {
                            columns.add(attr.getName());
                        }
                        int count = 0;
                        boolean truncated = false;
                        while (rs.nextRow()) {
                            if (count >= maxRows) {
                                truncated = true;
                                break;
                            }
                            JsonArray row = new JsonArray();
                            for (int i = 0; i < attrs.size(); i++) {
                                row.add(toJson(rs.getAttributeValue(i)));
                            }
                            rows.add(row);
                            count++;
                        }
                        out.addProperty("rowCount", count);
                        out.addProperty("truncated", truncated);
                    } finally {
                        rs.close();
                    }
                } else {
                    out.addProperty("updateCount", stmt.getUpdateRowCount());
                }
            } finally {
                stmt.close();
            }
        } finally {
            session.close();
        }

        out.add("columns", columns);
        out.add("rows", rows);
        return out;
    }

    private static com.google.gson.JsonElement toJson(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof Number) {
            return new JsonPrimitive((Number) value);
        }
        if (value instanceof Boolean) {
            return new JsonPrimitive((Boolean) value);
        }
        return new JsonPrimitive(String.valueOf(value));
    }
}
