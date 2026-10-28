package cn.lunadeer.deertitle.config;

import cn.lunadeer.deertitle.domain.Currency;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Locale;

public record Settings(Database database, boolean crossServer, int refreshSeconds,
                       Currency currency, String currencyName, BigDecimal creditPrice,
                       int maxCreditsPerPurchase, int maxVisibleLength, int maxSourceLength,
                       String prefix, String suffix, String emptyTitle, String placeholderFormat, ZoneId displayTimeZone,
                       Path importDirectory, String importSourceId, ZoneId importTimeZone,
                       String fractionalCoins, boolean importShop, int databaseThreads, int queueCapacity) {
    public record Database(String type, Path sqliteFile, String host, int port, String name,
                           String username, String password, boolean ssl, boolean allowPublicKeyRetrieval, int poolSize, int timeoutSeconds) {
        @Override public String toString() { return "Database[type=" + type + ", credentials=redacted]"; }
    }

    public static Settings load(JavaPlugin plugin) {
        FileConfiguration c = plugin.getConfig();
        String type = c.getString("database.type", "sqlite").toLowerCase(Locale.ROOT);
        if (!java.util.Set.of("sqlite", "pgsql", "mysql").contains(type))
            throw new IllegalArgumentException("database.type 必须为 sqlite、pgsql 或 mysql");
        boolean crossServer = c.getBoolean("cross-server.enabled");
        if (crossServer && type.equals("sqlite"))
            throw new IllegalArgumentException("跨服务器模式必须使用 pgsql 或 mysql");
        String mode = c.getString("economy.mode", "coins").toLowerCase(Locale.ROOT);
        Currency currency = switch (mode) {
            case "coins" -> Currency.COINS;
            case "vault" -> Currency.VAULT;
            default -> throw new IllegalArgumentException("economy.mode 必须为 coins 或 vault");
        };
        BigDecimal creditPrice;
        try { creditPrice = new BigDecimal(c.getString("custom-title.price-per-credit", "100")).stripTrailingZeros(); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("自定义次数价格必须是数字"); }
        if (creditPrice.signum() < 0 || creditPrice.compareTo(new BigDecimal("1000000000")) > 0
                || creditPrice.scale() > (currency == Currency.COINS ? 0 : 2))
            throw new IllegalArgumentException("自定义次数价格必须非负；称号币为整数，Vault 最多两位小数");
        Path data = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path sqlite = data.resolve(c.getString("database.sqlite-file", "deertitle.db")).normalize();
        if (!sqlite.startsWith(data) || sqlite.equals(data))
            throw new IllegalArgumentException("database.sqlite-file 必须位于插件数据目录内");
        String host = c.getString("database.host", "localhost");
        String name = c.getString("database.name", "deertitle");
        if (!host.matches("[a-zA-Z0-9.\\-:]+") || !name.matches("[a-zA-Z0-9_\\-]+"))
            throw new IllegalArgumentException("数据库主机或库名格式无效");
        Database db = new Database(type, sqlite, host,
                bounded(c, "database.port", type.equals("mysql") ? 3306 : 5432, 1, 65535), name,
                c.getString("database.username", "deertitle"), c.getString("database.password", ""),
                c.getBoolean("database.ssl"), c.getBoolean("database.allow-public-key-retrieval", false), bounded(c, "database.pool-size", 4, 1, 32),
                bounded(c, "database.connection-timeout-seconds", 10, 2, 60));
        String fraction = c.getString("import.mini-player-title.fractional-coins", "reject");
        if (!java.util.Set.of("reject", "floor", "round-half-up").contains(fraction))
            throw new IllegalArgumentException("fractional-coins 必须为 reject、floor 或 round-half-up");
        String format = c.getString("display.placeholder-format", "legacy");
        if (!java.util.Set.of("legacy", "minimessage", "plain").contains(format))
            throw new IllegalArgumentException("placeholder-format 必须为 legacy、minimessage 或 plain");
        String sourceId = c.getString("import.mini-player-title.source-id", "mini-player-title-main");
        if (!sourceId.matches("[a-zA-Z0-9_.-]{1,100}"))
            throw new IllegalArgumentException("导入 source-id 必须为 1–100 位字母、数字、点、短横线或下划线");
        String currencyName = c.getString("economy.currency-name", "").trim();
        if (currencyName.isEmpty()) currencyName = currency == Currency.COINS ? "称号币" : "金币";
        return new Settings(db, crossServer, bounded(c, "cross-server.refresh-seconds", 3, 1, 300),
                currency, currencyName,
                creditPrice, bounded(c, "custom-title.max-credits-per-purchase", 100, 1, 10000),
                bounded(c, "custom-title.max-visible-length", 24, 1, 256),
                bounded(c, "custom-title.max-source-length", 1024, 32, 8192),
                c.getString("display.prefix", ""), c.getString("display.suffix", ""),
                c.getString("display.empty-title", ""), format, ZoneId.of(c.getString("display.time-zone", "Asia/Shanghai")),
                Path.of(c.getString("import.mini-player-title.source-directory", "plugins/MiniPlayerTitle")).toAbsolutePath().normalize(),
                sourceId, ZoneId.of(c.getString("import.mini-player-title.source-time-zone", "Asia/Shanghai")),
                fraction, c.getBoolean("import.mini-player-title.include-shop", true),
                bounded(c, "runtime.database-threads", 2, 1, 16),
                bounded(c, "runtime.database-queue-capacity", 256, 16, 4096));
    }

    private static int bounded(FileConfiguration c, String key, int fallback, int min, int max) {
        int value = c.getInt(key, fallback);
        if (value < min || value > max) throw new IllegalArgumentException(key + " 超出允许范围 " + min + "–" + max);
        return value;
    }
}
