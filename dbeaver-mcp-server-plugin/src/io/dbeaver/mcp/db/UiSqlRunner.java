package io.dbeaver.mcp.db;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.data.DBDAttributeBinding;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.exec.DBCStatistics;
import org.jkiss.dbeaver.model.preferences.DBPPreferenceStore;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLQueryListener;
import org.jkiss.dbeaver.model.sql.SQLQueryResult;
import org.jkiss.dbeaver.model.sql.SqlJobResult;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.runtime.ui.UIServiceSQL;
import org.jkiss.dbeaver.ui.controls.resultset.IResultSetController;
import org.jkiss.dbeaver.ui.controls.resultset.ResultSetModel;
import org.jkiss.dbeaver.ui.editors.sql.SQLEditor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Executes SQL through a visible DBeaver SQL console and mirrors the
 * result-grid data back to the MCP response.
 */
public final class UiSqlRunner {

    private static final String RESULT_SET_MAX_ROWS = "resultset.maxrows";

    private UiSqlRunner() {
    }

    public static JsonObject query(DBPDataSourceContainer container, String sql, int maxRows) throws Exception {
        DBPDataSource dataSource = DBeaverConnections.requireOpen(container);
        requirePositiveMaxRows(maxRows);
        DBCExecutionContext context = dataSource.getDefaultInstance().getDefaultContext(new VoidProgressMonitor(),
                false);
        CompletableFuture<JsonObject> result = new CompletableFuture<>();

        DBWorkbench.getPlatformUI().executeInMainThread(() -> startQuery(container, context, sql, maxRows, result));

        try {
            return result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(cause);
        }
    }

    private static void startQuery(DBPDataSourceContainer container, DBCExecutionContext context, String sql,
            int maxRows, CompletableFuture<JsonObject> result) {
        try {
            DBeaverConnections.requireOpen(container);

            UIServiceSQL sqlService = DBWorkbench.findService(UIServiceSQL.class);
            if (sqlService == null) {
                throw new IllegalStateException("DBeaver SQL UI service is unavailable");
            }

            Object console = sqlService.openSQLConsole(container, context, null, "MCP: " + container.getName(), sql);
            if (!(console instanceof SQLEditor editor)) {
                throw new IllegalStateException("DBeaver did not open an SQL editor");
            }

            IResultSetController initialController = editor.getResultSetController();
            if (initialController != null) {
                initialController.setSegmentFetchSize(maxRows);
            }

            AtomicReference<SQLQueryResult> queryResult = new AtomicReference<>();
            AtomicReference<DBCStatistics> statistics = new AtomicReference<>();

            SQLQueryListener listener = new SQLQueryListener() {
                @Override
                public void onEndQuery(DBCSession session, SQLQueryResult executionResult,
                        DBCStatistics executionStatistics) {
                    queryResult.set(executionResult);
                    statistics.set(executionStatistics);
                }

                @Override
                public void onEndSqlJob(DBCSession session, SqlJobResult jobResult) {
                    DBWorkbench.getPlatformUI().executeInMainThread(() -> completeResult(editor, queryResult.get(),
                            statistics.get(), jobResult, maxRows, result));
                }

                @Override
                public void onEndScript(DBCStatistics executionStatistics, boolean hadErrors) {
                    statistics.set(executionStatistics);
                    if (queryResult.get() == null) {
                        SqlJobResult jobResult = hadErrors ? SqlJobResult.FAILURE : SqlJobResult.SUCCESS;
                        DBWorkbench.getPlatformUI().executeInMainThread(() -> completeResult(editor, null,
                                executionStatistics, jobResult, maxRows, result));
                    }
                }
            };

            DBPPreferenceStore preferenceStore = container.getPreferenceStore();
            boolean maxRowsWasDefault = preferenceStore.isDefault(RESULT_SET_MAX_ROWS);
            int previousMaxRows = preferenceStore.getInt(RESULT_SET_MAX_ROWS);
            preferenceStore.setValue(RESULT_SET_MAX_ROWS, maxRows);
            try {
                if (!editor.processSQL(false, false, null, listener)) {
                    throw new IllegalStateException("DBeaver did not start the SQL statement");
                }
            } finally {
                if (maxRowsWasDefault) {
                    preferenceStore.setToDefault(RESULT_SET_MAX_ROWS);
                } else {
                    preferenceStore.setValue(RESULT_SET_MAX_ROWS, previousMaxRows);
                }
            }
        } catch (Throwable e) {
            result.completeExceptionally(e);
        }
    }

    private static void completeResult(SQLEditor editor, SQLQueryResult executionResult, DBCStatistics statistics,
            SqlJobResult jobResult, int maxRows, CompletableFuture<JsonObject> result) {
        if (result.isDone()) {
            return;
        }
        try {
            if (executionResult == null) {
                Throwable error = statistics == null ? null : statistics.getError();
                if (error != null) {
                    throw new Exception(error.getMessage(), error);
                }
                throw new IllegalStateException("DBeaver SQL job finished without an execution result: " + jobResult);
            }
            if (executionResult.hasError()) {
                throw new Exception(executionResult.getError().getMessage(), executionResult.getError());
            }

            JsonObject out = new JsonObject();
            JsonArray columns = new JsonArray();
            JsonArray rows = new JsonArray();

            if (executionResult.hasResultSet()) {
                IResultSetController controller = editor.getResultSetController();
                if (controller == null) {
                    throw new IllegalStateException("DBeaver SQL editor has no result-set controller");
                }

                ResultSetModel model = controller.getModel();
                DBDAttributeBinding[] attributes = model.getAttributes();
                for (DBDAttributeBinding attribute : attributes) {
                    columns.add(attribute.getName());
                }

                int rowCount = Math.min(model.getRowCount(), maxRows);
                for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                    Object[] values = model.getRowData(rowIndex);
                    JsonArray row = new JsonArray();
                    for (int columnIndex = 0; columnIndex < attributes.length; columnIndex++) {
                        row.add(SqlRunner.toJson(columnIndex < values.length ? values[columnIndex] : null));
                    }
                    rows.add(row);
                }
                out.addProperty("rowCount", rowCount);
                out.addProperty("truncated", controller.isHasMoreData() || model.getRowCount() > maxRows);
            } else {
                out.addProperty("updateCount", findUpdateCount(executionResult, statistics));
            }

            out.add("columns", columns);
            out.add("rows", rows);
            result.complete(out);
        } catch (Throwable e) {
            result.completeExceptionally(e);
        }
    }

    private static void requirePositiveMaxRows(int maxRows) {
        if (maxRows <= 0) {
            throw new IllegalArgumentException("maxRows must be greater than zero");
        }
    }

    private static long findUpdateCount(SQLQueryResult executionResult, DBCStatistics statistics) {
        for (SQLQueryResult.ExecuteResult executeResult : executionResult.getExecuteResults()) {
            if (executeResult.getUpdateCount() != null) {
                return executeResult.getUpdateCount();
            }
        }
        return statistics == null ? -1 : statistics.getRowsUpdated();
    }
}
