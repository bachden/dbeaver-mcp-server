# DBeaver MCP Server

DBeaver MCP Server is an Eclipse/OSGi plugin that runs inside DBeaver and exposes
configured database connections to AI agents through a local MCP Streamable HTTP
server.

The plugin uses DBeaver's connection registry, drivers, and live sessions
directly. It does not read or decrypt `credentials-config.json`.

## Install From GitHub Pages

1. In DBeaver, open **Help -> Install New Software...**.
2. Add the following update site:

   ```text
   https://bachden.github.io/dbeaver-mcp-server/
   ```

3. Select **DBeaver MCP Server** and complete the installation wizard.
4. Restart DBeaver when prompted.

This URL is a p2 repository for DBeaver. The root may not render an HTML page in
a browser; DBeaver reads `p2.index`, `artifacts.xml.xz`, and
`content.xml.xz` directly.

## Human In The Loop

The plugin follows a **what you see is what you get** rule:

- `list_connections` lists both open and closed connections and includes a
  `connected` field.
- `get_schema`, `run_query`, and `execute_sql` only use connections that
  are already open in DBeaver.
- The plugin never opens a database connection automatically or presents a
  credential prompt on the user's behalf.
- When a connection is closed, the tool returns an error and asks the user to
  open it manually in DBeaver first.
- Tunnels, credentials, MFA, and the decision to connect remain under the
  user's control in the DBeaver UI.

## Tools

| Tool | Description |
|------|-------------|
| `list_connections` | Lists connections, their `connectionId`, endpoint, and `connected` state. |
| `get_schema` | Browses catalogs, schemas, tables, and columns through a dotted `path`. |
| `run_query` | Runs read-only SQL such as SELECT, WITH, SHOW, EXPLAIN, and DESCRIBE. |
| `execute_sql` | Runs any SQL, including INSERT, UPDATE, DELETE, and DDL. It can modify or delete data. |

All three database-access tools reject closed connections.

`list_connections` accepts an optional `name` filter. The default
`nameMode: "ilike"` supports SQL-style `%` and `_` wildcards with
case-insensitive matching. Set `nameMode: "regex"` to use a Java regular
expression with substring matching.

`run_query` and `execute_sql` accept an optional `showOnUi` boolean
(default `false`). When it is `true`, the statement executes once through a
visible DBeaver SQL editor: its result appears in the DBeaver result grid and
the same data is returned to the MCP caller. DBeaver's normal execution
confirmations remain active, and the connection must already be open.

## Server And UI

The default endpoint is:

```text
POST http://127.0.0.1:8722/mcp
```

The **DBeaver MCP** menu provides:

- **Start Server**
- **Stop Server**
- **Connections...**
- **Settings...**

The status bar displays the current server state. The Start/Stop choice is
persisted in the workspace: a stopped server remains stopped after DBeaver
restarts, while an enabled server starts again automatically.

The bundle is activated through the `org.jkiss.dbeaver.pluginService`
lifecycle after the DBeaver platform is ready. The p2 metadata does not force
`markStarted`, which avoids Equinox trying to start a duplicate singleton
module.

The **Window -> Preferences -> DBeaver MCP Server** page configures the listener
host, port, bearer-token authentication, and client snippets for Claude Code,
Claude Desktop, Codex CLI, or raw HTTP.

The server supports `initialize`, `ping`, `tools/list`, `tools/call`, and
JSON-RPC batches. It does not provide an SSE stream.

## Project Layout

```text
dbeaver-mcp-server-plugin-parent
├── pom.xml
├── .github/workflows/
├── dbeaver-mcp-server-plugin
│   ├── META-INF/MANIFEST.MF
│   ├── plugin.xml
│   ├── dbeaver-ci.target
│   ├── pom.xml
│   └── src/
├── dbeaver-mcp-server-plugin.feature
│   ├── feature.xml
│   └── pom.xml
└── dbeaver-mcp-server-plugin.updatesite
    ├── category.xml
    └── pom.xml
```

The modules are:

- `dbeaver-mcp-server-plugin-parent`: the Maven/Tycho reactor.
- `dbeaver-mcp-server-plugin`: the OSGi bundle.
- `dbeaver-mcp-server-plugin.feature`: the installable p2 feature.
- `dbeaver-mcp-server-plugin.updatesite`: the p2 repository.

## Development Setup

Requirements:

- DBeaver 23+ installed.
- Java 21.
- Eclipse IDE with PDE and m2e.
- Maven 3.9 or newer.

Import the repository root with **File -> Import -> Existing Maven Projects**.
Eclipse will discover the four reactor projects.

Open `dbeaver-mcp-server-plugin/dbeaver-ci.target`, select
**Set as Active Target Platform**, and wait for PDE to finish resolving it. This
is the same public p2 target definition consumed by Tycho and GitHub Actions.
No local DBeaver installation path is part of the build.

## Build The p2 Update Site

Run from the repository root:

```bash
mvn clean verify
```

Tycho builds the bundle, feature, and p2 repository. The complete repository is
copied to:

```text
dist/
├── artifacts.jar
├── content.jar
├── features/
├── plugins/
└── p2.index
```

GitHub Actions runs the same `mvn clean verify` command and uses
`dbeaver-ci.target` through the parent POM.
`dist/` is the stable local update-site location. Each build replaces the
`0.1.6.qualifier` qualifier with a timestamp so p2 can identify a new update.

## Install From A Local Update Site

1. Build the project to create `dist/`.
2. In DBeaver, open **Help -> Install New Software...**.
3. Select **Add... -> Local...** and choose the `dist/` directory.
4. Give the repository a name such as **Local DBeaver MCP**.
5. Select **DBeaver MCP Server** and complete the wizard.
6. Restart DBeaver when prompted.

There is no need to copy JARs, edit `bundles.info`, or use `dropins/`.
Normal installation and update flows do not require `-clean`.

## Update The Plugin

For local development:

1. Rebuild the update site:

   ```bash
   mvn clean verify
   ```

2. In DBeaver, select **Help -> Check for Updates**.
3. Install the new version and restart DBeaver when prompted.

The local repository remains at `dist/`, so it does not need to be added again
after every build. Do not uninstall and reinstall the same timestamped version.
Create a new qualifier or increment the base version, then use
**Check for Updates**.

If the Error Log reports `Another singleton bundle selected` after the exact
same version has been reinstalled, close DBeaver completely and run it once
with:

```bash
/Applications/DBeaver.app/Contents/MacOS/dbeaver -clean
```

DBeaver can be started normally after that one clean launch.

## GitHub Actions And Pages

The repository contains two workflows:

- **Build and Publish DBeaver Update Site** runs on pushes to `main`, builds
  with the shared `dbeaver-ci.target`, and deploys `dist/` to GitHub Pages.
- **Validate DBeaver Update Site** runs for pull requests and uploads `dist/`
  as a build artifact.

The published update site is:

```text
https://bachden.github.io/dbeaver-mcp-server/
```

Do not commit `target/` or `dist/`; Maven recreates these artifacts in CI.

## Connect An AI Agent

Claude Code example with authentication disabled:

```bash
claude mcp add --transport http dbeaver http://127.0.0.1:8722/mcp
```

When authentication is enabled, clients must send:

```text
Authorization: Bearer <token>
```

Codex CLI example:

```toml
[mcp_servers.dbeaver]
url = "http://127.0.0.1:8722/mcp"

[mcp_servers.dbeaver.http_headers]
Authorization = "Bearer <token>"
```

## Security

- The server binds to `127.0.0.1` by default.
- Enable bearer-token authentication when binding to a LAN address or
  `0.0.0.0`.
- `execute_sql` accepts unrestricted SQL and can modify or delete data.
- The `run_query` filter is not a replacement for database permissions. Use a
  read-only database account when a hard write restriction is required.
- The user must open a connection in DBeaver before an agent can access that
  database.
- The server can be stopped at any time from the menu or status bar.
