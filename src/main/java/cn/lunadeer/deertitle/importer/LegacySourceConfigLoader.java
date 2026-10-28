package cn.lunadeer.deertitle.importer;

import cn.lunadeer.deertitle.domain.Currency;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.io.IOException;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Reads the old configuration without loading, enabling or migrating MiniPlayerTitle. */
public final class LegacySourceConfigLoader {
    private LegacySourceConfigLoader() { }

    public static SourceConfig load(Path sourceDirectory, String sourceId, ZoneId timeZone,
                                    SourceConfig.CoinFractionPolicy fractions,
                                    Currency currency, boolean importShop) {
        Path directory = sourceDirectory.toAbsolutePath().normalize();
        Path file = directory.resolve("config.yml");
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("找不到 MiniPlayerTitle 的 config.yml，请检查来源目录。");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
        } catch (IOException | InvalidConfigurationException exception) {
            // YAML parser exceptions may contain excerpts of a password-bearing document.
            throw new IllegalArgumentException("无法读取 MiniPlayerTitle 配置，请检查 YAML 格式和权限。");
        }
        SourceConfig.DatabaseType type = SourceConfig.DatabaseType.parse(yaml.getString("Database.Type", "sqlite"));
        String database = yaml.getString("Database.Name", "miniplayertitle");
        int defaultPort = type == SourceConfig.DatabaseType.MYSQL ? 3306 : 5432;
        int port;
        try {
            port = Integer.parseInt(yaml.getString("Database.Port", Integer.toString(defaultPort)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("MiniPlayerTitle 数据库端口必须是整数。");
        }
        Path sqliteFile = directory.resolve(database + ".db").normalize();
        if (type == SourceConfig.DatabaseType.SQLITE && !sqliteFile.startsWith(directory)) {
            throw new IllegalArgumentException("来源 SQLite 文件必须位于 MiniPlayerTitle 数据目录内。");
        }
        return new SourceConfig(sourceId, type, sqliteFile,
                yaml.getString("Database.Host", "localhost"), port, database,
                yaml.getString("Database.User", "postgres"), yaml.getString("Database.Pass", ""),
                timeZone, yaml.getBoolean("ExternalEco", false), yaml.getString("Prefix", "["),
                yaml.getString("Suffix", "]"), fractions, currency, importShop);
    }
}
