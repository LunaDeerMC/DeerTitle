package cn.lunadeer.deertitle.storage;

import java.util.Locale;

public enum SqlDialect {
    SQLITE, POSTGRESQL, MYSQL;

    public static SqlDialect parse(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "sqlite" -> SQLITE;
            case "pgsql", "postgres", "postgresql" -> POSTGRESQL;
            case "mysql" -> MYSQL;
            default -> throw new IllegalArgumentException("Unsupported database type");
        };
    }

    String generatedId() {
        return switch (this) {
            case SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT";
            case POSTGRESQL -> "BIGSERIAL PRIMARY KEY";
            case MYSQL -> "BIGINT AUTO_INCREMENT PRIMARY KEY";
        };
    }

    String insertMutex() {
        return this == MYSQL ? "INSERT IGNORE INTO dt_mutex(id) VALUES (1)"
                : "INSERT INTO dt_mutex(id) VALUES (1) ON CONFLICT(id) DO NOTHING";
    }
}
