package io.dbeaver.mcp.ui;

/**
 * Generates ready-to-paste MCP client configuration snippets for popular
 * agents, pointed at this plugin's HTTP endpoint. When auth is enabled, the
 * generated snippet also carries the bearer token in whatever form that
 * agent expects.
 */
public final class McpConfigGenerator {

    /** One target agent/client we know how to generate a config snippet for. */
    public enum Agent {
        CLAUDE_CODE_CLI("Claude Code (CLI)"),
        CLAUDE_DESKTOP("Claude Desktop (claude_desktop_config.json)"),
        CODEX_CLI("Codex CLI (config.toml)"),
        RAW_URL("Raw endpoint URL");

        private final String label;

        Agent(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private McpConfigGenerator() {
    }

    public static String generate(Agent agent, String host, int port, boolean authEnabled, String token) {
        String url = endpointUrl(host, port);
        boolean withAuth = authEnabled && token != null && !token.isEmpty();
        switch (agent) {
        case CLAUDE_CODE_CLI:
            return withAuth
                    ? "claude mcp add --transport http dbeaver " + url + " --header \"Authorization: Bearer " + token
                            + "\""
                    : "claude mcp add --transport http dbeaver " + url;
        case CLAUDE_DESKTOP:
            // Claude Desktop has no native "http" transport type; bridge via mcp-remote
            // (spawned over stdio) instead of the raw HTTP block Claude Code accepts.
            return withAuth
                    ? "{\n" + "  \"mcpServers\": {\n" + "    \"dbeaver\": {\n"
                            + "      \"command\": \"npx\",\n" + "      \"args\": [\n" + "        \"-y\",\n"
                            + "        \"mcp-remote\",\n" + "        \"" + url + "\",\n"
                            + "        \"--header\",\n"
                            + "        \"Authorization: Bearer " + token + "\"\n" + "      ]\n" + "    }\n"
                            + "  }\n" + "}"
                    : "{\n" + "  \"mcpServers\": {\n" + "    \"dbeaver\": {\n" + "      \"command\": \"npx\",\n"
                            + "      \"args\": [\n" + "        \"-y\",\n" + "        \"mcp-remote\",\n"
                            + "        \"" + url + "\"\n" + "      ]\n" + "    }\n" + "  }\n" + "}";
        case CODEX_CLI:
            return withAuth
                    ? "[mcp_servers.dbeaver]\n" + "url = \"" + url + "\"\n" + "[mcp_servers.dbeaver.http_headers]\n"
                            + "Authorization = \"Bearer " + token + "\""
                    : "[mcp_servers.dbeaver]\n" + "url = \"" + url + "\"";
        case RAW_URL:
        default:
            return withAuth ? url + "\nAuthorization: Bearer " + token : url;
        }
    }

    private static String endpointUrl(String host, int port) {
        String connectHost = "0.0.0.0".equals(host) ? "127.0.0.1" : host;
        return "http://" + connectHost + ":" + port + "/mcp";
    }
}
