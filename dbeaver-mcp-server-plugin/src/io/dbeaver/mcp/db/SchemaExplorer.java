package io.dbeaver.mcp.db;

import java.util.Collection;

import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntity;
import org.jkiss.dbeaver.model.struct.DBSEntityAttribute;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectContainer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Navigator-style schema explorer built on DBeaver's structural model. Lets an
 * agent drill into catalogs/schemas/tables one level at a time (cheap) and read
 * column metadata for a specific table.
 */
public final class SchemaExplorer {

    private SchemaExplorer() {
    }

    /**
     * List the children under an optional dotted {@code path} (e.g. "myschema" or
     * "mycatalog.myschema"). If a path segment resolves to a table, its columns are
     * returned instead of children.
     */
    public static JsonObject explore(DBPDataSourceContainer container, String path) throws Exception {
        DBRProgressMonitor monitor = new VoidProgressMonitor();
        DBPDataSource dataSource = DBeaverConnections.requireOpen(container);

        DBSObject current = dataSource;
        if (path != null && !path.isBlank()) {
            for (String segment : path.split("\\.")) {
                segment = segment.trim();
                if (segment.isEmpty()) {
                    continue;
                }
                current = childByName(monitor, current, segment);
                if (current == null) {
                    throw new IllegalArgumentException("Path segment not found: " + segment);
                }
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("path", path == null ? "" : path);

        if (current instanceof DBSEntity) {
            result.addProperty("type", "table");
            JsonArray cols = new JsonArray();
            Collection<? extends DBSEntityAttribute> attributes = ((DBSEntity) current).getAttributes(monitor);
            if (attributes != null) {
                for (DBSEntityAttribute attr : attributes) {
                    JsonObject col = new JsonObject();
                    col.addProperty("name", attr.getName());
                    col.addProperty("type", attr.getTypeName());
                    col.addProperty("nullable", !attr.isRequired());
                    cols.add(col);
                }
            }
            result.add("columns", cols);
            return result;
        }

        result.addProperty("type", "container");
        JsonArray children = new JsonArray();
        if (current instanceof DBSObjectContainer) {
            Collection<? extends DBSObject> objects = ((DBSObjectContainer) current).getChildren(monitor);
            if (objects != null) {
                for (DBSObject child : objects) {
                    JsonObject node = new JsonObject();
                    node.addProperty("name", child.getName());
                    node.addProperty("kind", child instanceof DBSEntity ? "table"
                            : (child instanceof DBSObjectContainer ? "container" : "object"));
                    children.add(node);
                }
            }
        }
        result.add("children", children);
        return result;
    }

    private static DBSObject childByName(DBRProgressMonitor monitor, DBSObject parent, String name) throws Exception {
        if (!(parent instanceof DBSObjectContainer)) {
            return null;
        }
        DBSObject child = ((DBSObjectContainer) parent).getChild(monitor, name);
        if (child != null) {
            return child;
        }
        // Fall back to a case-insensitive scan when getChild is case-sensitive.
        Collection<? extends DBSObject> objects = ((DBSObjectContainer) parent).getChildren(monitor);
        if (objects != null) {
            for (DBSObject candidate : objects) {
                if (name.equalsIgnoreCase(candidate.getName())) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
