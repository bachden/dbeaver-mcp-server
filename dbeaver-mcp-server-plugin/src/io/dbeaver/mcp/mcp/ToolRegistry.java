package io.dbeaver.mcp.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.dbeaver.mcp.tools.ExecuteSqlTool;
import io.dbeaver.mcp.tools.GetSchemaTool;
import io.dbeaver.mcp.tools.ListConnectionsTool;
import io.dbeaver.mcp.tools.RunQueryTool;

/** Registry of all MCP tools exposed by the server. */
public final class ToolRegistry {

    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        register(new ListConnectionsTool());
        register(new GetSchemaTool());
        register(new RunQueryTool());
        register(new ExecuteSqlTool());
    }

    private void register(McpTool tool) {
        tools.put(tool.name(), tool);
    }

    public McpTool get(String name) {
        return tools.get(name);
    }

    /** Build the {@code tools} array for a {@code tools/list} response. */
    public JsonArray listJson() {
        JsonArray array = new JsonArray();
        for (McpTool tool : tools.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", tool.name());
            entry.addProperty("description", tool.description());
            entry.add("inputSchema", tool.inputSchema());
            array.add(entry);
        }
        return array;
    }
}
