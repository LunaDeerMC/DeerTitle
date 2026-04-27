package cn.lunadeer.deertitle.database;

import java.util.List;

public final class SchemaStatements {

    private SchemaStatements() {
    }

    public static List<String> sqliteBaseline() {
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS mplt_title (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL UNIQUE,
                    title TEXT NOT NULL DEFAULT 'unknown',
                    description TEXT NOT NULL DEFAULT 'unknown',
                    enabled INTEGER NOT NULL DEFAULT true
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS mplt_player_info (
                    uuid TEXT PRIMARY KEY NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
                    using_title_id INTEGER NOT NULL DEFAULT -1,
                    last_use_name TEXT NOT NULL DEFAULT 'null',
                    coin_d REAL NOT NULL DEFAULT 0,
                    FOREIGN KEY (using_title_id) REFERENCES mplt_title(id) ON DELETE CASCADE
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS mplt_player_title (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL UNIQUE,
                    player_uuid TEXT NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
                    title_id INTEGER NOT NULL DEFAULT 0,
                    expire_at_y INTEGER NOT NULL DEFAULT -1,
                    expire_at_m INTEGER NOT NULL DEFAULT -1,
                    expire_at_d INTEGER NOT NULL DEFAULT -1,
                    FOREIGN KEY (title_id) REFERENCES mplt_title(id) ON DELETE CASCADE,
                    FOREIGN KEY (player_uuid) REFERENCES mplt_player_info(uuid) ON DELETE CASCADE
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS mplt_title_shop (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL UNIQUE,
                    title_id INTEGER NOT NULL DEFAULT 0,
                    days INTEGER NOT NULL DEFAULT 0,
                    amount INTEGER NOT NULL DEFAULT -1,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    sale_end_at_y INTEGER NOT NULL DEFAULT -1,
                    sale_end_at_m INTEGER NOT NULL DEFAULT -1,
                    sale_end_at_d INTEGER NOT NULL DEFAULT -1,
                    price_d REAL NOT NULL DEFAULT 0,
                    FOREIGN KEY (title_id) REFERENCES mplt_title(id) ON DELETE CASCADE
                )
                """
        );
    }

    public static String createMigrationTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                    CREATE TABLE IF NOT EXISTS mplt_migration (
                        version INTEGER PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """;
            case MARIADB -> """
                    CREATE TABLE IF NOT EXISTS mplt_migration (
                        version INT PRIMARY KEY NOT NULL,
                        name VARCHAR(128) NOT NULL,
                        applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """;
        };
    }

    public static List<String> mariaDbBaseline() {
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS mplt_title (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT NOT NULL UNIQUE,
                    title VARCHAR(255) NOT NULL DEFAULT 'unknown',
                    description VARCHAR(255) NOT NULL DEFAULT 'unknown',
                    enabled BOOLEAN NOT NULL DEFAULT TRUE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS mplt_player_info (
                    uuid VARCHAR(36) PRIMARY KEY NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
                    using_title_id INTEGER NOT NULL DEFAULT -1,
                    last_use_name VARCHAR(255) NOT NULL DEFAULT 'null',
                    coin_d DOUBLE NOT NULL DEFAULT 0,
                    CONSTRAINT fk_mplt_player_info_title FOREIGN KEY (using_title_id) REFERENCES mplt_title(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS mplt_player_title (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT NOT NULL UNIQUE,
                    player_uuid VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
                    title_id INTEGER NOT NULL DEFAULT 0,
                    expire_at_y INTEGER NOT NULL DEFAULT -1,
                    expire_at_m INTEGER NOT NULL DEFAULT -1,
                    expire_at_d INTEGER NOT NULL DEFAULT -1,
                    CONSTRAINT fk_mplt_player_title_title FOREIGN KEY (title_id) REFERENCES mplt_title(id) ON DELETE CASCADE,
                    CONSTRAINT fk_mplt_player_title_player FOREIGN KEY (player_uuid) REFERENCES mplt_player_info(uuid) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """,
                """
                CREATE TABLE IF NOT EXISTS mplt_title_shop (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT NOT NULL UNIQUE,
                    title_id INTEGER NOT NULL DEFAULT 0,
                    days INTEGER NOT NULL DEFAULT 0,
                    amount INTEGER NOT NULL DEFAULT -1,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    sale_end_at_y INTEGER NOT NULL DEFAULT -1,
                    sale_end_at_m INTEGER NOT NULL DEFAULT -1,
                    sale_end_at_d INTEGER NOT NULL DEFAULT -1,
                    price_d DOUBLE NOT NULL DEFAULT 0,
                    CONSTRAINT fk_mplt_title_shop_title FOREIGN KEY (title_id) REFERENCES mplt_title(id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
        );
    }

    public static List<String> sqlitePlayerInfoNullableMigration() {
        return List.of(
                "PRAGMA foreign_keys = OFF",
                """
                CREATE TABLE mplt_player_info_new (
                    uuid TEXT PRIMARY KEY NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000',
                    using_title_id INTEGER DEFAULT NULL,
                    last_use_name TEXT NOT NULL DEFAULT 'null',
                    coin_d REAL NOT NULL DEFAULT 0,
                    FOREIGN KEY (using_title_id) REFERENCES mplt_title(id) ON DELETE CASCADE
                )
                """,
                """
                INSERT INTO mplt_player_info_new (uuid, using_title_id, last_use_name, coin_d)
                SELECT uuid,
                       CASE WHEN using_title_id < 0 THEN NULL ELSE using_title_id END,
                       last_use_name,
                       coin_d
                FROM mplt_player_info
                """,
                "DROP TABLE mplt_player_info",
                "ALTER TABLE mplt_player_info_new RENAME TO mplt_player_info",
                "PRAGMA foreign_keys = ON"
        );
    }

    public static List<String> mariaDbPlayerInfoNullableMigration() {
        return List.of(
                "UPDATE mplt_player_info SET using_title_id = NULL WHERE using_title_id < 0",
                "ALTER TABLE mplt_player_info MODIFY using_title_id INTEGER NULL DEFAULT NULL"
        );
    }
}
