package cn.lunadeer.deertitle.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface Migration {
    int version();

    String name();

    void apply(Connection connection, DatabaseType type) throws Exception;

    default void executeStatement(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
