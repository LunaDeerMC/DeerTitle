package cn.lunadeer.deertitle.platform;

import cn.lunadeer.deertitle.config.Settings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.nio.file.Files;

public final class DatabaseFactory {
    private DatabaseFactory() {}

    public static HikariDataSource create(Settings.Database db) throws IOException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("DeerTitle");
        config.setMaximumPoolSize(db.type().equals("sqlite") ? 1 : db.poolSize());
        config.setMinimumIdle(1);
        config.setConnectionTimeout(db.timeoutSeconds() * 1000L);
        config.setValidationTimeout(2000);
        config.setInitializationFailTimeout(db.timeoutSeconds() * 1000L);
        config.setMaxLifetime(1800000);
        config.setKeepaliveTime(120000);
        String host = db.host().contains(":") ? "[" + db.host() + "]" : db.host();
        switch (db.type()) {
            case "sqlite" -> {
                Files.createDirectories(db.sqliteFile().getParent());
                config.setDriverClassName("org.sqlite.JDBC");
                config.setJdbcUrl("jdbc:sqlite:" + db.sqliteFile());
                config.addDataSourceProperty("foreign_keys", "true");
                config.addDataSourceProperty("busy_timeout", "10000");
                config.addDataSourceProperty("journal_mode", "WAL");
            }
            case "pgsql" -> {
                config.setDriverClassName("org.postgresql.Driver");
                config.setJdbcUrl("jdbc:postgresql://" + host + ":" + db.port() + "/" + db.name());
                config.addDataSourceProperty("sslmode", db.ssl() ? "verify-full" : "disable");
                config.addDataSourceProperty("connectTimeout", db.timeoutSeconds());
                config.addDataSourceProperty("socketTimeout", 30);
                config.addDataSourceProperty("tcpKeepAlive", true);
            }
            case "mysql" -> {
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                config.setJdbcUrl("jdbc:mysql://" + host + ":" + db.port() + "/" + db.name());
                config.addDataSourceProperty("sslMode", db.ssl() ? "VERIFY_IDENTITY" : "DISABLED");
                config.addDataSourceProperty("allowPublicKeyRetrieval", db.allowPublicKeyRetrieval());
                config.addDataSourceProperty("connectTimeout", db.timeoutSeconds() * 1000);
                config.addDataSourceProperty("socketTimeout", 30000);
                config.addDataSourceProperty("connectionTimeZone", "UTC");
                config.addDataSourceProperty("characterEncoding", "UTF-8");
            }
            default -> throw new IllegalArgumentException("不支持的数据库类型");
        }
        if (!db.type().equals("sqlite")) {
            config.setUsername(db.username());
            config.setPassword(db.password());
        }
        return new HikariDataSource(config);
    }
}
