package io.dbeaver.mcp.tools;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
                + "whether it is currently connected. Optionally filters connections by name using "
                + "SQL ILIKE syntax or a regular expression.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = Schemas.object();
        Schemas.prop(schema, "name", "string",
                "Optional connection-name pattern. Uses SQL ILIKE syntax by default (% and _ wildcards).");
        JsonObject mode = Schemas.prop(schema, "nameMode", "string",
                "How to interpret name: ilike (default) or regex. Regex patterns use Java syntax and find().");
        JsonArray modes = new JsonArray();
        modes.add("ilike");
        modes.add("regex");
        mode.add("enum", modes);
        mode.addProperty("default", "ilike");
        return schema;
    }

    @Override
    public String execute(JsonObject arguments) {
        String name = Schemas.optString(arguments, "name", null);
        boolean regexMode = parseRegexMode(Schemas.optString(arguments, "nameMode", "ilike"));
        Pattern namePattern = compileNamePattern(name, regexMode);

        JsonArray connections = new JsonArray();
        for (DBPDataSourceContainer container : DBeaverConnections.all()) {
            if (!matchesName(container.getName(), namePattern, regexMode)) {
                continue;
            }
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

    private static boolean parseRegexMode(String mode) {
        return switch (mode.toLowerCase(Locale.ROOT)) {
        case "ilike" -> false;
        case "regex" -> true;
        default -> throw new IllegalArgumentException("nameMode must be either 'ilike' or 'regex'");
        };
    }

    private static Pattern compileNamePattern(String name, boolean regexMode) {
        if (name == null) {
            return null;
        }
        try {
            if (regexMode) {
                return Pattern.compile(name);
            }
            return Pattern.compile(toIlikeRegex(name),
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid name regex: " + e.getDescription(), e);
        }
    }

    private static boolean matchesName(String name, Pattern pattern, boolean regexMode) {
        if (pattern == null) {
            return true;
        }
        if (name == null) {
            return false;
        }
        return regexMode ? pattern.matcher(name).find() : pattern.matcher(name).matches();
    }

    private static String toIlikeRegex(String pattern) {
        StringBuilder regex = new StringBuilder("^");
        StringBuilder literal = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (escaped) {
                literal.append(ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '%' || ch == '_') {
                appendLiteral(regex, literal);
                regex.append(ch == '%' ? ".*" : ".");
            } else {
                literal.append(ch);
            }
        }
        if (escaped) {
            literal.append('\\');
        }
        appendLiteral(regex, literal);
        return regex.append('$').toString();
    }

    private static void appendLiteral(StringBuilder regex, StringBuilder literal) {
        if (literal.length() > 0) {
            regex.append(Pattern.quote(literal.toString()));
            literal.setLength(0);
        }
    }
}
