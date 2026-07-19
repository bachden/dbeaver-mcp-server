package io.dbeaver.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Tiny helpers for hand-building JSON Schema objects for tool inputs. */
final class Schemas {

    private Schemas() {
    }

    static JsonObject object() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }

    static JsonObject prop(JsonObject schema, String name, String type, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", type);
        property.addProperty("description", description);
        schema.getAsJsonObject("properties").add(name, property);
        return schema;
    }

    static JsonObject required(JsonObject schema, String... names) {
        JsonArray required = new JsonArray();
        for (String name : names) {
            required.add(name);
        }
        schema.add("required", required);
        return schema;
    }

    /** Read a required string argument, throwing a clear error when missing. */
    static String requireString(JsonObject args, String name) {
        if (args == null || !args.has(name) || args.get(name).isJsonNull()) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return args.get(name).getAsString();
    }

    static String optString(JsonObject args, String name, String fallback) {
        if (args == null || !args.has(name) || args.get(name).isJsonNull()) {
            return fallback;
        }
        return args.get(name).getAsString();
    }

    static int optInt(JsonObject args, String name, int fallback) {
        if (args == null || !args.has(name) || args.get(name).isJsonNull()) {
            return fallback;
        }
        return args.get(name).getAsInt();
    }

    static boolean optBoolean(JsonObject args, String name, boolean fallback) {
        if (args == null || !args.has(name) || args.get(name).isJsonNull()) {
            return fallback;
        }
        return args.get(name).getAsBoolean();
    }
}
