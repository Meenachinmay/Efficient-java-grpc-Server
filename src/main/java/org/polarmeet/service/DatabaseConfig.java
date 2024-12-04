package org.polarmeet.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class DatabaseConfig {
    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/grpctest");
        config.setUsername("postgres");
        config.setPassword("password");

        // Connection pool settings
        config.setMaximumPoolSize(50);
        config.setMinimumIdle(10);

        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        // Batch operation optimizations
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("useWriteBehindBuffer", "true");
        config.addDataSourceProperty("writeBufferSize", "16384");

        dataSource = new HikariDataSource(config);
    }

    public static DataSource getDataSource() {
        return dataSource;
    }

    // Method to perform batch inserts
    public static void batchInsert(List<RequestData> batch) throws SQLException {
        String sql = "INSERT INTO requests (id, request_data, timestamp) VALUES (?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (RequestData data : batch) {
                stmt.setObject(1, UUID.randomUUID());
                stmt.setString(2, data.data());
                stmt.setLong(3, System.currentTimeMillis());
                stmt.addBatch();
            }

            stmt.executeBatch();
        }
    }
}
