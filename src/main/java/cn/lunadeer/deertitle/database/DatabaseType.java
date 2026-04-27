package cn.lunadeer.deertitle.database;

public enum DatabaseType {
    SQLITE,
    MARIADB;

    public static DatabaseType fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return SQLITE;
        }
        return switch (raw.trim().toLowerCase()) {
            case "sqlite" -> SQLITE;
            case "mariadb", "mysql" -> MARIADB;
            default -> throw new IllegalArgumentException("Unsupported database type: " + raw);
        };
    }
}
