# DBeaver MCP Server

Eclipse/OSGi plugin chạy bên trong DBeaver và expose các connection đã cấu hình thành một
MCP Streamable HTTP server cho AI agent.

Plugin dùng trực tiếp connection registry, driver và session của DBeaver. Nó không đọc hay
giải mã `credentials-config.json`.

## Cài từ GitHub Pages

1. Trong DBeaver mở **Help -> Install New Software...**.
2. Thêm update site:

   ```text
   https://bachden.github.io/dbeaver-mcp-server/
   ```

3. Chọn feature **DBeaver MCP Server** và hoàn tất wizard.
4. Restart DBeaver khi được yêu cầu.

URL trên là p2 repository dành cho DBeaver. Trang gốc có thể không hiển thị HTML trong browser;
DBeaver đọc trực tiếp `p2.index`, `artifacts.xml.xz` và `content.xml.xz`.

## Human in the loop

Plugin áp dụng nguyên tắc **what you see is what you get**:

- `list_connections` hiển thị cả connection đang mở và đang đóng, kèm trường `connected`.
- `get_schema`, `run_query` và `execute_sql` chỉ dùng connection đang mở sẵn.
- Plugin không tự động mở connection hoặc hiển thị credential prompt thay cho người dùng.
- Nếu connection đang đóng, tool trả lỗi và yêu cầu mở thủ công trong DBeaver trước.
- Tunnel, credential, MFA và quyết định kết nối luôn nằm trong UI của DBeaver.

## Tools

| Tool | Mô tả |
|------|-------|
| `list_connections` | Liệt kê connection, `connectionId`, endpoint và trạng thái `connected`. |
| `get_schema` | Duyệt catalog/schema/table/column theo dotted `path`. |
| `run_query` | Chạy SQL read-only như SELECT/WITH/SHOW/EXPLAIN/DESCRIBE. |
| `execute_sql` | Chạy mọi SQL, gồm INSERT/UPDATE/DELETE/DDL. Có thể sửa hoặc xóa dữ liệu. |

Ba tool truy cập database đều từ chối connection đang đóng.

## Server và UI

Endpoint mặc định:

```
POST http://127.0.0.1:8722/mcp
```

Menu **DBeaver MCP** cung cấp:

- **Start Server**
- **Stop Server**
- **Connections...**
- **Settings...**

Status bar hiển thị trạng thái server. Trạng thái Start/Stop được lưu trong workspace:
server đang dừng sẽ tiếp tục dừng sau khi khởi động lại DBeaver, và server đang bật sẽ tự bật lại.
Bundle được activate qua lifecycle `org.jkiss.dbeaver.pluginService` sau khi DBeaver platform sẵn sàng;
p2 không ép `markStarted`, tránh việc Equinox cố start một singleton module trùng lặp.

Preference page **Window → Preferences → DBeaver MCP Server** cho phép cấu hình host, port,
bearer-token authentication và sinh config cho Claude Code, Claude Desktop, Codex CLI hoặc
raw HTTP.

Server hỗ trợ `initialize`, `ping`, `tools/list`, `tools/call` và JSON-RPC batch.
Server không cung cấp SSE stream.

## Project layout

```
dbeaver-mcp-server-plugin-parent
├── pom.xml
├── .github/workflows/
├── dbeaver-mcp-server-plugin
│   ├── META-INF/MANIFEST.MF
│   ├── plugin.xml
│   ├── dbeaver.target
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

Các module:

- `dbeaver-mcp-server-plugin-parent`: Maven/Tycho reactor.
- `dbeaver-mcp-server-plugin`: OSGi bundle.
- `dbeaver-mcp-server-plugin.feature`: installable p2 feature.
- `dbeaver-mcp-server-plugin.updatesite`: p2 repository.

## Development setup

Yêu cầu:

- DBeaver Ultimate 26.x tại
  `/Applications/DBeaverUltimate.app/Contents/Eclipse`.
- Java 21.
- Eclipse IDE có PDE và m2e.
- Maven 3.9+.

Import root bằng **File → Import → Existing Maven Projects**. Eclipse sẽ nhận bốn project
trong reactor.

Mở `dbeaver-mcp-server-plugin/dbeaver.target`, chọn **Set as Active Target Platform** và
chờ PDE resolve xong. File này dùng DBeaver Ultimate đang cài trên macOS; build CI dùng
`dbeaver-ci.target` với các p2 repository công khai.

## Build p2 update site

Từ thư mục root:

```bash
mvn clean verify
```

Tycho build bundle, feature và p2 repository. Repository hoàn chỉnh được copy tới:

```
dist/
├── artifacts.jar
├── content.jar
├── features/
├── plugins/
└── p2.index
```

Build portable giống GitHub Actions:

```bash
mvn --batch-mode --update-snapshots \
  -Dtarget-definition=dbeaver-ci.target \
  clean verify
```

`dist/` luôn là local update site ổn định; mỗi build `0.1.6.qualifier` tạo timestamp version
mới để p2 nhận diện update.

## Cài từ local update site

1. Build project để tạo `dist/`.
2. Trong DBeaver mở **Help → Install New Software...**.
3. Chọn **Add... → Local...** và trỏ tới thư mục `dist/`.
4. Đặt tên repository, ví dụ **Local DBeaver MCP**.
5. Chọn feature **DBeaver MCP Server** và hoàn tất wizard.
6. Restart DBeaver khi được yêu cầu.

Không cần copy jar, sửa `bundles.info` hoặc dùng `dropins/`. Trong luồng cài đặt/cập nhật bình thường
cũng không cần chạy `-clean`.

## Update plugin

1. Chạy lại:

   ```bash
   mvn clean verify
   ```

2. Trong DBeaver chọn **Help → Check for Updates**.
3. Cài version mới và restart khi DBeaver yêu cầu.

Repository local giữ nguyên đường dẫn `dist/`, nên không cần Add lại sau mỗi build. Không uninstall rồi
install lại đúng cùng một timestamp version; hãy tăng base version hoặc tạo qualifier mới và dùng
**Check for Updates**.

Nếu Error Log báo `Another singleton bundle selected` sau khi đã reinstall cùng exact version,
đóng DBeaver hoàn toàn và chạy một lần:

```bash
/Applications/DBeaverUltimate.app/Contents/MacOS/dbeaver -clean
```

Sau lần khởi động đó có thể mở DBeaver bình thường.

## GitHub Actions và Pages

Repository có hai workflow:

- **Build and Publish DBeaver Update Site** chạy khi push vào `main`, build bằng
  `dbeaver-ci.target` và deploy `dist/` lên GitHub Pages.
- **Validate DBeaver Update Site** chạy cho pull request và upload `dist/` thành build artifact.

Update site được publish tại:

```text
https://bachden.github.io/dbeaver-mcp-server/
```

Không commit `target/` hoặc `dist/`; các artifact này được Maven tạo lại trong CI.

## Kết nối AI agent

Ví dụ Claude Code khi auth tắt:

```bash
claude mcp add --transport http dbeaver http://127.0.0.1:8722/mcp
```

Khi auth bật, client phải gửi:

```
Authorization: Bearer <token>
```

Ví dụ Codex CLI:

```toml
[mcp_servers.dbeaver]
url = "http://127.0.0.1:8722/mcp"

[mcp_servers.dbeaver.headers]
Authorization = "Bearer <token>"
```

## Bảo mật

- Mặc định server bind `127.0.0.1`.
- Khi bind LAN address hoặc `0.0.0.0`, nên bật bearer-token authentication.
- `execute_sql` không giới hạn SQL và có thể sửa hoặc xóa dữ liệu.
- Bộ lọc của `run_query` không thay thế database permission. Dùng read-only database user
  nếu cần giới hạn chắc chắn.
- Người dùng phải mở connection trong DBeaver trước khi agent truy cập database.
- Có thể dừng server bất kỳ lúc nào từ menu hoặc status bar.
