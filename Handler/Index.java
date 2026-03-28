package Handler;

import Repository.GenericTableRepository;
import Repository.GenericTableRepository.ColumnMeta;
import Service.templating.Templater;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Index {
    private static final GenericTableRepository REPOSITORY = new GenericTableRepository();

    public static void get(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            List<String> tables = REPOSITORY.listTables();
            String selectedTable = selectTable(query.get("table"), tables);
            String message = query.getOrDefault("message", "");

            String html = Templater.render("index.html", Map.of(
                    "title", "Java Autumn SSR",
                    "subtitle", "Server-side rendering with JDBC and dynamic table metadata.",
                    "message", escapeHtml(message),
                    "table_navigation", buildTableNavigation(tables, selectedTable),
                    "table_panel", buildTablePanel(selectedTable),
                    "create_form", buildCreateForm(selectedTable)
            ));
            sendHtml(exchange, 200, html);
        } catch (IllegalArgumentException illegalArgumentException) {
            sendHtml(exchange, 400, "<h1>Bad Request</h1><p>" + escapeHtml(illegalArgumentException.getMessage()) + "</p>");
        } catch (SQLException sqlException) {
            sendHtml(exchange, 500, "<h1>Database Error</h1><p>" + escapeHtml(sqlException.getMessage()) + "</p>");
        }
    }

    public static void create(HttpExchange exchange) throws IOException {
        mutateAndRedirect(exchange, MutationType.CREATE);
    }

    public static void update(HttpExchange exchange) throws IOException {
        mutateAndRedirect(exchange, MutationType.UPDATE);
    }

    public static void delete(HttpExchange exchange) throws IOException {
        mutateAndRedirect(exchange, MutationType.DELETE);
    }

    private static void mutateAndRedirect(HttpExchange exchange, MutationType mutationType) throws IOException {
        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String table = REPOSITORY.requireKnownTable(query.get("table"));
            Map<String, String> form = parseFormUrlEncoded(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            switch (mutationType) {
                case CREATE -> {
                    REPOSITORY.insertRow(table, extractEditableValues(table, form));
                    redirect(exchange, table, "Created row in " + table + ".");
                }
                case UPDATE -> {
                    REPOSITORY.updateRow(table, required(form, "_id"), extractEditableValues(table, form));
                    redirect(exchange, table, "Updated row in " + table + ".");
                }
                case DELETE -> {
                    REPOSITORY.deleteRow(table, required(form, "_id"));
                    redirect(exchange, table, "Deleted row in " + table + ".");
                }
            }
        } catch (IllegalArgumentException illegalArgumentException) {
            redirect(exchange, null, illegalArgumentException.getMessage());
        } catch (SQLException sqlException) {
            redirect(exchange, null, "Database error: " + sqlException.getMessage());
        }
    }

    private static Map<String, String> extractEditableValues(String table, Map<String, String> form) throws SQLException {
        List<ColumnMeta> columns = REPOSITORY.getColumns(table);
        Map<String, String> values = new LinkedHashMap<>();

        for (ColumnMeta column : columns) {
            if (column.primaryKey() && column.autoIncrement()) {
                continue;
            }
            if (form.containsKey(column.name())) {
                values.put(column.name(), form.get(column.name()));
            }
        }

        return values;
    }

    private static String buildTableNavigation(List<String> tables, String selectedTable) {
        if (tables.isEmpty()) {
            return "<p>No user tables found. Create a table in the SQLite database to start.</p>";
        }

        StringBuilder html = new StringBuilder("<div class=\"buttons\">");
        for (String table : tables) {
            String activeClass = table.equals(selectedTable) ? " active" : "";
            html.append("<a class=\"table-link")
                    .append(activeClass)
                    .append("\" href=\"/?table=")
                    .append(urlEncode(table))
                    .append("\">")
                    .append(escapeHtml(table))
                    .append("</a>");
        }
        html.append("</div>");
        return html.toString();
    }

    private static String buildTablePanel(String table) throws SQLException {
        if (table == null || table.isBlank()) {
            return "<p>Please create and select a table.</p>";
        }

        List<ColumnMeta> columns = REPOSITORY.getColumns(table);
        List<Map<String, Object>> rows = REPOSITORY.getRows(table);
        ColumnMeta primaryKey = REPOSITORY.getPrimaryKey(columns);

        String headers = columns.stream()
                .map(column -> "<th>" + escapeHtml(column.name()) + "</th>")
                .collect(Collectors.joining());

        StringBuilder body = new StringBuilder();
        for (Map<String, Object> row : rows) {
            body.append("<tr>");
            for (ColumnMeta column : columns) {
                Object value = row.get(column.name());
                body.append("<td>").append(escapeHtml(value == null ? "" : value.toString())).append("</td>");
            }
            body.append("<td>")
                    .append(buildUpdateForm(table, columns, row, primaryKey.name()))
                    .append(buildDeleteForm(table, row.get(primaryKey.name())))
                    .append("</td>");
            body.append("</tr>");
        }

        if (rows.isEmpty()) {
            body.append("<tr><td colspan=\"")
                    .append(columns.size() + 1)
                    .append("\">No rows found.</td></tr>");
        }

        return "<h2>Table: " + escapeHtml(table) + "</h2>"
                + "<div class=\"table-container\"><table><thead><tr>"
                + headers
                + "<th>Actions</th></tr></thead><tbody>"
                + body
                + "</tbody></table></div>";
    }

    private static String buildCreateForm(String table) throws SQLException {
        if (table == null || table.isBlank()) {
            return "";
        }

        List<ColumnMeta> columns = REPOSITORY.getColumns(table);
        List<ColumnMeta> createColumns = columns.stream()
                .filter(column -> !(column.primaryKey() && column.autoIncrement()))
                .toList();

        if (createColumns.isEmpty()) {
            return "<p>No writable columns for table " + escapeHtml(table) + ".</p>";
        }

        StringBuilder fields = new StringBuilder();
        for (ColumnMeta column : createColumns) {
            fields.append("<label>")
                    .append(escapeHtml(column.name()))
                    .append("</label>")
                    .append("<input name=\"")
                    .append(escapeHtml(column.name()))
                    .append("\" placeholder=\"")
                    .append(escapeHtml(column.typeName()))
                    .append("\" ")
                    .append(column.nullable() ? "" : "required")
                    .append(">");
        }

        return "<h3>Create Row</h3>"
                + "<form method=\"post\" action=\"/create?table=" + urlEncode(table) + "\" class=\"inline-form\">"
                + fields
                + "<button type=\"submit\">Create</button>"
                + "</form>";
    }

    private static String buildUpdateForm(String table, List<ColumnMeta> columns, Map<String, Object> row, String primaryKeyName) {
        StringBuilder inputs = new StringBuilder();
        Object idValue = row.get(primaryKeyName);

        for (ColumnMeta column : columns) {
            if (column.primaryKey()) {
                continue;
            }

            Object value = row.get(column.name());
            inputs.append("<label>")
                    .append(escapeHtml(column.name()))
                    .append("</label>")
                    .append("<input name=\"")
                    .append(escapeHtml(column.name()))
                    .append("\" value=\"")
                    .append(escapeHtml(value == null ? "" : value.toString()))
                    .append("\" ")
                    .append(column.nullable() ? "" : "required")
                    .append(">");
        }

        return "<form method=\"post\" action=\"/update?table=" + urlEncode(table) + "\" class=\"row-form\">"
                + "<input type=\"hidden\" name=\"_id\" value=\"" + escapeHtml(idValue == null ? "" : idValue.toString()) + "\">"
                + inputs
                + "<button type=\"submit\">Save</button>"
                + "</form>";
    }

    private static String buildDeleteForm(String table, Object idValue) {
        return "<form method=\"post\" action=\"/delete?table=" + urlEncode(table) + "\" class=\"row-form delete-form\">"
                + "<input type=\"hidden\" name=\"_id\" value=\"" + escapeHtml(idValue == null ? "" : idValue.toString()) + "\">"
                + "<button type=\"submit\">Delete</button>"
                + "</form>";
    }

    private static String selectTable(String requestedTable, List<String> tables) {
        if (tables.isEmpty()) {
            return null;
        }
        if (requestedTable == null || requestedTable.isBlank()) {
            return tables.get(0);
        }
        for (String table : tables) {
            if (table.equalsIgnoreCase(requestedTable.trim())) {
                return table;
            }
        }
        return tables.get(0);
    }

    private static void redirect(HttpExchange exchange, String table, String message) throws IOException {
        StringBuilder location = new StringBuilder("/?");
        if (table != null && !table.isBlank()) {
            location.append("table=").append(urlEncode(table)).append("&");
        }
        location.append("message=").append(urlEncode(message == null ? "" : message));

        exchange.getResponseHeaders().set("Location", location.toString());
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }
        return parseFormUrlEncoded(rawQuery);
    }

    private static Map<String, String> parseFormUrlEncoded(String encoded) {
        Map<String, String> values = new LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return values;
        }

        String[] pairs = encoded.split("&");
        for (String pair : pairs) {
            String[] split = pair.split("=", 2);
            String key = urlDecode(split[0]);
            String value = split.length == 2 ? urlDecode(split[1]) : "";
            values.put(key, value);
        }

        return values;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value.trim();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private static void sendHtml(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] payload = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (var outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private enum MutationType {
        CREATE,
        UPDATE,
        DELETE
    }
}
