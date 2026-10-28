package cn.lunadeer.deertitle.importer;

import cn.lunadeer.deertitle.domain.Currency;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

/** Credentials belong to the connection only, never to migration records or logs. */
public record SourceConfig(
        String sourceId, DatabaseType type, Path sqliteFile, String host, int port,
        String database, String username, String password, ZoneId timeZone,
        boolean externalEconomy, String prefix, String suffix,
        CoinFractionPolicy coinFractionPolicy, Currency currency, boolean importShop) {

    public enum DatabaseType {
        SQLITE, PGSQL, MYSQL;

        public static DatabaseType parse(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "sqlite" -> SQLITE;
                case "pgsql", "postgres", "postgresql" -> PGSQL;
                case "mysql", "mariadb" -> MYSQL;
                default -> throw new IllegalArgumentException("不支持的来源数据库类型。");
            };
        }
    }

    public enum CoinFractionPolicy { REJECT, FLOOR, ROUND_HALF_UP }

    public SourceConfig {
        Objects.requireNonNull(sourceId, "sourceId");
        if (!sourceId.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new IllegalArgumentException("来源标识只能包含 1–100 个英文字母、数字、点、下划线或连字符。");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(timeZone, "timeZone");
        Objects.requireNonNull(coinFractionPolicy, "coinFractionPolicy");
        Objects.requireNonNull(currency, "currency");
        prefix = Objects.requireNonNullElse(prefix, "");
        suffix = Objects.requireNonNullElse(suffix, "");
        username = Objects.requireNonNullElse(username, "");
        password = Objects.requireNonNullElse(password, "");
        if (type == DatabaseType.SQLITE) {
            sqliteFile = Objects.requireNonNull(sqliteFile, "sqliteFile").toAbsolutePath().normalize();
        } else {
            if (host == null || !host.matches("[A-Za-z0-9.\\-:\\[\\]]+") || port < 1 || port > 65535) {
                throw new IllegalArgumentException("来源数据库地址或端口无效。");
            }
            if (database == null || !database.matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException("来源数据库名称无效。");
            }
        }
    }

    @Override
    public String toString() {
        return "SourceConfig[sourceId=" + sourceId + ", type=" + type
                + ", credentials=<redacted>, timeZone=" + timeZone + "]";
    }
}
