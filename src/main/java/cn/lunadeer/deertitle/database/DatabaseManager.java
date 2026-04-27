package cn.lunadeer.deertitle.database;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.configuration.PluginConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseManager implements AutoCloseable {

    private final DeerTitlePlugin plugin;
    private final PluginConfig.Database settings;
    private final DatabaseType type;
    private HikariDataSource dataSource;

    public DatabaseManager(DeerTitlePlugin plugin, PluginConfig.Database settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.type = DatabaseType.fromConfig(settings.type);
    }

    public void start() {
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("DeerTitle-" + type.name());
        config.setMaximumPoolSize(resolveMaximumPoolSize());
        config.setMinimumIdle(1);
        config.setConnectionTimeout(settings.connectionTimeoutMillis);
        config.setAutoCommit(true);
        switch (type) {
            case SQLITE -> configureSqlite(config);
            case MARIADB -> configureMariaDb(config);
        }
        this.dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            start();
        }
        return dataSource.getConnection();
    }

    public DatabaseType getType() {
        return type;
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dataSource = null;
    }

    private int resolveMaximumPoolSize() {
        if (type == DatabaseType.SQLITE) {
            return Math.max(1, Math.min(4, settings.maximumPoolSize));
        }
        return Math.max(2, settings.maximumPoolSize);
    }

    private void configureSqlite(HikariConfig config) {
        File databaseFile = new File(plugin.getDataFolder(), settings.sqliteFile);
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create sqlite directory: " + parent.getAbsolutePath());
        }
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        config.setConnectionInitSql("PRAGMA foreign_keys = ON;");
    }

    private void configureMariaDb(HikariConfig config) {
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setJdbcUrl(
                "jdbc:mariadb://"
                        + settings.host
                        + ":"
                        + settings.port
                        + "/"
                        + settings.database
                        + "?"
                        + settings.parameters
        );
        config.setUsername(settings.username);
        config.setPassword(settings.password);
    }
}
