package cn.lunadeer.deertitle.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

final class SqliteTitleStoreTest extends TitleStoreContract {
    @TempDir Path directory;
    @Override protected SqlDialect dialect() { return SqlDialect.SQLITE; }
    @Override protected HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + directory.resolve("titles.sqlite"));
        config.setMaximumPoolSize(4);
        config.setPoolName("test-sqlite");
        return new HikariDataSource(config);
    }
}
