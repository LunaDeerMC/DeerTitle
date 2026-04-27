package cn.lunadeer.deertitle.database;

import cn.lunadeer.deertitle.DeerTitlePlugin;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

public final class MigrationRunner {

    private static final List<String> CORE_TABLES = List.of(
            "mplt_title",
            "mplt_player_info",
            "mplt_player_title",
            "mplt_title_shop"
    );

    private final DeerTitlePlugin plugin;
    private final DatabaseManager databaseManager;
    private final List<Migration> migrations = List.of(
            new BaselineSchemaMigration(),
            new PlayerInfoNullabilityMigration()
    );

    public MigrationRunner(DeerTitlePlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void migrate() throws Exception {
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            ensureMigrationTable(connection);
            int currentVersion = getCurrentVersion(connection);
            if (currentVersion == 0 && coreTablesExist(connection)) {
                recordMigration(connection, 1, "baseline-schema");
                currentVersion = 1;
            }
            for (Migration migration : migrations) {
                if (migration.version() <= currentVersion) {
                    continue;
                }
                plugin.getLogger().info("Applying migration v" + migration.version() + ": " + migration.name());
                migration.apply(connection, databaseManager.getType());
                recordMigration(connection, migration.version(), migration.name());
                connection.commit();
                currentVersion = migration.version();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
    }

    private void ensureMigrationTable(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(SchemaStatements.createMigrationTable(databaseManager.getType()));
        }
    }

    private int getCurrentVersion(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT MAX(version) FROM mplt_migration");
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        }
        return 0;
    }

    private boolean coreTablesExist(Connection connection) throws SQLException {
        for (String table : CORE_TABLES) {
            if (!tableExists(connection, table)) {
                return false;
            }
        }
        return true;
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName, null)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName.toUpperCase(Locale.ROOT), null)) {
            return resultSet.next();
        }
    }

    private void recordMigration(Connection connection, int version, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO mplt_migration (version, name) VALUES (?, ?)"
        )) {
            statement.setInt(1, version);
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    private final class BaselineSchemaMigration implements Migration {
        @Override
        public int version() {
            return 1;
        }

        @Override
        public String name() {
            return "baseline-schema";
        }

        @Override
        public void apply(Connection connection, DatabaseType type) throws Exception {
            if (coreTablesExist(connection)) {
                return;
            }
            List<String> statements = switch (type) {
                case SQLITE -> SchemaStatements.sqliteBaseline();
                case MARIADB -> SchemaStatements.mariaDbBaseline();
            };
            for (String statement : statements) {
                executeStatement(connection, statement);
            }
        }
    }

    private final class PlayerInfoNullabilityMigration implements Migration {
        @Override
        public int version() {
            return 2;
        }

        @Override
        public String name() {
            return "player-info-using-title-nullable";
        }

        @Override
        public void apply(Connection connection, DatabaseType type) throws Exception {
            if (!tableExists(connection, "mplt_player_info")) {
                return;
            }
            List<String> statements = switch (type) {
                case SQLITE -> SchemaStatements.sqlitePlayerInfoNullableMigration();
                case MARIADB -> SchemaStatements.mariaDbPlayerInfoNullableMigration();
            };
            for (String statement : statements) {
                executeStatement(connection, statement);
            }
        }
    }
}
