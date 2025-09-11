package com.db2api;

import com.mongodb.client.*;
import org.bson.Document;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.Jedis;

import java.sql.*;
import java.time.Instant;
import java.util.*;

@SpringBootApplication
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class DB2API {

    public static void main(String[] args) {
        SpringApplication.run(DB2API.class, args);
    }

    // --- Header names for DB connection info ---
    private static final String H_ENGINE = "X-DB-Engine";
    private static final String H_HOST = "X-DB-Host";
    private static final String H_PORT = "X-DB-Port";
    private static final String H_NAME = "X-DB-Name";
    private static final String H_USER = "X-DB-User";
    private static final String H_PASSWORD = "X-DB-Password";
    private static final String H_OPTIONS = "X-DB-Options";

    @GetMapping("/connect-test")
    public ResponseEntity<?> connectTest(
            @RequestHeader(H_ENGINE) String engine,
            @RequestHeader(H_HOST) String host,
            @RequestHeader(value = H_PORT, required = false) String port,
            @RequestHeader(H_NAME) String name,
            @RequestHeader(value = H_USER, required = false) String user,
            @RequestHeader(value = H_PASSWORD, required = false) String password,
            @RequestHeader(value = H_OPTIONS, required = false) String options
    ) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("requestedAt", Instant.now().toString());

        String eng = engine.trim().toLowerCase(Locale.ROOT);

        try {
            switch (eng) {
                case "mongodb" -> {
                    String mongoUri = "mongodb://" +
                            (user != null ? user + ":" + password + "@" : "") +
                            host + (port != null ? ":" + port : "") + "/" + name;
                    try (MongoClient client = MongoClients.create(mongoUri)) {
                        MongoDatabase db = client.getDatabase(name);
                        db.listCollectionNames().first(); // test connection
                        resp.put("success", true);
                        resp.put("engine", "mongodb");
                        resp.put("uri", mongoUri);
                    }
                }
                case "redis" -> {
                    int p = (port == null || port.isBlank()) ? 6379 : Integer.parseInt(port);
                    try (Jedis jedis = new Jedis(host, p)) {
                        if (password != null && !password.isBlank()) jedis.auth(password);
                        String pong = jedis.ping();
                        resp.put("success", "PONG".equals(pong));
                        resp.put("engine", "redis");
                    }
                }
                default -> {
                    String jdbcUrl = buildJdbcUrl(engine, host, port, name, options);
                    resp.put("jdbcUrl", jdbcUrl);
                    try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
                        DatabaseMetaData md = conn.getMetaData();
                        resp.put("success", true);
                        resp.put("databaseProductName", md.getDatabaseProductName());
                        resp.put("databaseProductVersion", md.getDatabaseProductVersion());
                        resp.put("driverName", md.getDriverName());
                        resp.put("driverVersion", md.getDriverVersion());
                        resp.put("userName", md.getUserName());
                        try { String catalog = conn.getCatalog(); if(catalog!=null) resp.put("catalog", catalog); } catch(SQLException ignored){}
                        try { String schema = conn.getSchema(); if(schema!=null) resp.put("schema", schema); } catch(SQLException ignored){}
                    }
                }
            }
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
        }
    }

    @PostMapping(path = "/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> query(
            @RequestHeader(H_ENGINE) String engine,
            @RequestHeader(H_HOST) String host,
            @RequestHeader(value = H_PORT, required = false) String port,
            @RequestHeader(H_NAME) String name,
            @RequestHeader(value = H_USER, required = false) String user,
            @RequestHeader(value = H_PASSWORD, required = false) String password,
            @RequestHeader(value = H_OPTIONS, required = false) String options,
            @RequestBody QueryRequest request
    ) {
        if (request == null || request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "query must be provided in request body"));
        }

        String eng = engine.trim().toLowerCase(Locale.ROOT);

        try {
            switch (eng) {
                // ----------------- MONGODB -----------------
                case "mongodb" -> {
                    String mongoUri = "mongodb://" +
                            (user != null ? user + ":" + password + "@" : "") +
                            host + (port != null ? ":" + port : "") + "/" + name;
                    try (MongoClient client = MongoClients.create(mongoUri)) {
                        MongoDatabase db = client.getDatabase(name);
                        MongoCollection<Document> col = db.getCollection(request.getQuery());

                        // Filter zusammenbauen
                        Document filter = new Document();
                        if (request.getParams() != null && !request.getParams().isEmpty()) {
                            filter.putAll(request.getParams().get(0));
                        }

                        // Projection zusammenbauen
                        Document projection = new Document();
                        if (request.getFields() != null) {
                            request.getFields().forEach(f -> projection.append(f, 1));
    			
			    // _id nur ausschließen, wenn es nicht explizit angefragt wurde
			    if (!request.getFields().contains("_id")) {
			        projection.append("_id", 0);
			    }			    
                        }

                        List<Document> docs = !projection.isEmpty()
                                ? col.find(filter).projection(projection)
                                    .limit(request.getMaxRows() != null ? request.getMaxRows() : 1000)
                                    .into(new ArrayList<>())
                                : col.find(filter)
                                    .limit(request.getMaxRows() != null ? request.getMaxRows() : 1000)
                                    .into(new ArrayList<>());

                        return ResponseEntity.ok(Map.of(
                                "success", true,
                                "rowCount", docs.size(),
                                "rows", docs
                        ));
                    }
                }

                // ----------------- REDIS -----------------
                case "redis" -> {
                    int p = (port == null || port.isBlank()) ? 6379 : Integer.parseInt(port);
                    try (Jedis jedis = new Jedis(host, p)) {
                        if (password != null && !password.isBlank()) jedis.auth(password);
                        String value = jedis.get(request.getQuery());
                        return ResponseEntity.ok(Map.of(
                                "success", true,
                                "key", request.getQuery(),
                                "value", value
                        ));
                    }
                }

                // ----------------- JDBC -----------------
                default -> {
                    String jdbcUrl = buildJdbcUrl(engine, host, port, name, options);
                    try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
                        String sql = request.getQuery().trim();
                        boolean isSelect = sql.regionMatches(true, 0, "select", 0, 6);

                        // JDBC-Parameter vorbereiten
                        List<Object> jdbcParams = null;
                        if (request.getParams() != null && !request.getParams().isEmpty()) {
                            jdbcParams = new ArrayList<>();
                            for (Map<String, Object> m : request.getParams()) {
                                jdbcParams.addAll(m.values());
                            }
                        }

                        if (isSelect) {
                            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                                setParameters(ps, jdbcParams);
                                int max = request.getMaxRows() != null ? request.getMaxRows() : 1000;
                                ps.setMaxRows(max);
                                try (ResultSet rs = ps.executeQuery()) {
                                    List<Map<String, Object>> rows = resultSetToList(rs);
                                    return ResponseEntity.ok(Map.of(
                                            "success", true,
                                            "rowCount", rows.size(),
                                            "rows", rows
                                    ));
                                }
                            }
                        } else {
                            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                                setParameters(ps, jdbcParams);
                                int affected = ps.executeUpdate();
                                return ResponseEntity.ok(Map.of(
                                        "success", true,
                                        "affectedRows", affected
                                ));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // --- helpers ---
    private static List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                String col = md.getColumnLabel(i);
                if (col == null || col.isBlank()) col = md.getColumnName(i);
                Object val = rs.getObject(i);
                if (val instanceof Timestamp) val = val.toString();
                row.put(col, val);
            }
            list.add(row);
        }
        return list;
    }

    private static void setParameters(PreparedStatement ps, List<Object> params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private static String buildJdbcUrl(String engine, String host, String port, String name, String options) {
        String eng = engine.trim().toLowerCase(Locale.ROOT);
        String opts = (options != null && !options.isBlank()) ? (eng.equals("mssql") || eng.equals("microsoft") ? ";" + options : "?" + options) : "";

        return switch (eng) {
            case "mysql", "connector_j" -> String.format("jdbc:mysql://%s:%s/%s%s", host, port == null ? "3306" : port, name, opts);
            case "mariadb" -> String.format("jdbc:mariadb://%s:%s/%s%s", host, port == null ? "3306" : port, name, opts);
            case "postgres", "postgresql" -> String.format("jdbc:postgresql://%s:%s/%s%s", host, port == null ? "5432" : port, name, opts);
            case "pgjdbc_ng" -> String.format("jdbc:pgsql://%s:%s/%s%s", host, port == null ? "5432" : port, name, opts);
            case "mssql", "sqlserver", "microsoft" -> String.format("jdbc:sqlserver://%s:%s;databaseName=%s%s", host, port == null ? "1433" : port, name, opts);
            case "oracle" -> String.format("jdbc:oracle:thin:@//%s:%s/%s%s", host, port == null ? "1521" : port, name, opts);
            case "derby" -> String.format("jdbc:derby://%s:%s/%s%s", host, port == null ? "1527" : port, name, opts);
            case "h2" -> String.format("jdbc:h2:tcp://%s:%s/%s%s", host, port == null ? "9092" : port, name, opts);
            case "hsqldb" -> String.format("jdbc:hsqldb:hsql://%s:%s/%s%s", host, port == null ? "9001" : port, name, opts);
            case "jaybird", "firebird" -> String.format("jdbc:firebirdsql://%s:%s/%s%s", host, port == null ? "3050" : port, name, opts);
            case "ibm_jcc", "db2" -> String.format("jdbc:db2://%s:%s/%s%s", host, port == null ? "50000" : port, name, opts);
            case "ibm_informix", "informix" -> String.format("jdbc:informix-sqli://%s:%s/%s%s", host, port == null ? "1526" : port, name, opts);
            case "sap" -> String.format("jdbc:sap://%s:%s/?databaseName=%s%s", host, port == null ? "30015" : port, name, opts);
            case "xerial", "sqlite" -> String.format("jdbc:sqlite:%s", name);
            case "orientdb" -> String.format("jdbc:orient:remote:%s/%s%s", host, name, opts);
            default -> throw new IllegalArgumentException("Unsupported DB engine: " + engine);
        };
    }
    // --- request DTO ---
    public static class QueryRequest {
    	private String query;               // Collection-Name / Query
    	private List<Map<String, Object>> params; // Filter als JSON-Objekt
    	private List<String> fields;        // Felder, die zurückgegeben werden sollen
    	private Integer maxRows;

    	public QueryRequest() {}

   	public String getQuery() { return query; }
    	public void setQuery(String query) { this.query = query; }

    	public List<Map<String, Object>> getParams() { return params; }
    	public void setParams(List<Map<String, Object>> params) { this.params = params; }

    	public List<String> getFields() { return fields; }
    	public void setFields(List<String> fields) { this.fields = fields; }

    	public Integer getMaxRows() { return maxRows; }
    	public void setMaxRows(Integer maxRows) { this.maxRows = maxRows; }
    }

}

