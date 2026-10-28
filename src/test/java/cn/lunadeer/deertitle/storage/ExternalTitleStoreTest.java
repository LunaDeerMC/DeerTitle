package cn.lunadeer.deertitle.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Requires an explicitly opted-in isolated local DB; never runs against arbitrary URLs. */
@EnabledIfEnvironmentVariable(named = "DEERTITLE_TEST_ALLOW_RESET", matches = "isolated-local")
final class ExternalTitleStoreTest extends TitleStoreContract {
    @Override protected SqlDialect dialect() {
        return SqlDialect.parse(System.getenv("DEERTITLE_TEST_DATABASE_TYPE"));
    }

    @Override protected HikariDataSource dataSource() {
        String url = System.getenv("DEERTITLE_TEST_JDBC_URL");
        if (url == null || !url.matches("jdbc:(postgresql|mysql)://127\\.0\\.0\\.1:[0-9]+/deertitle(?:\\?.*)?"))
            throw new IllegalArgumentException("Integration tests require their isolated loopback deertitle database");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(System.getenv("DEERTITLE_TEST_DATABASE_USERNAME"));
        config.setPassword(System.getenv("DEERTITLE_TEST_DATABASE_PASSWORD"));
        config.setMaximumPoolSize(8);
        config.setPoolName("test-" + dialect().name().toLowerCase());
        return new HikariDataSource(config);
    }
}
