package cn.lunadeer.deertitle.importer;

import cn.lunadeer.deertitle.domain.Currency;
import cn.lunadeer.deertitle.domain.ImportListing;
import cn.lunadeer.deertitle.domain.ImportReport;
import cn.lunadeer.deertitle.domain.ShopListing;
import cn.lunadeer.deertitle.domain.Title;
import cn.lunadeer.deertitle.domain.TitleDraft;
import cn.lunadeer.deertitle.storage.SqlDialect;
import cn.lunadeer.deertitle.storage.TitleStore;
import cn.lunadeer.deertitle.text.TitleText;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import static org.junit.jupiter.api.Assertions.*;

class MiniPlayerTitleImporterTest {
    private static final UUID PLAYER = UUID.fromString("516867fb-0000-4000-8000-000000000001");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T02:00:00Z"), ZoneOffset.UTC);
    @TempDir Path directory;
    private Path source;
    private MiniPlayerTitleImporter importer;

    @BeforeEach
    void fixture() throws Exception {
        source = directory.resolve("legacy.db");
        importer = new MiniPlayerTitleImporter(CLOCK);
        sql("CREATE TABLE mplt_title(id INTEGER PRIMARY KEY,title TEXT,description TEXT,enabled INTEGER)",
            "CREATE TABLE mplt_player_info(uuid TEXT PRIMARY KEY,coin_d REAL,using_title_id INTEGER,last_use_name TEXT)",
            "CREATE TABLE mplt_player_title(id INTEGER PRIMARY KEY,player_uuid TEXT,title_id INTEGER,expire_at_y INTEGER,expire_at_m INTEGER,expire_at_d INTEGER)",
            "CREATE TABLE mplt_title_shop(id INTEGER PRIMARY KEY,title_id INTEGER,price_d REAL,days INTEGER,amount INTEGER,sale_end_at_y INTEGER,sale_end_at_m INTEGER,sale_end_at_d INTEGER)",
            "INSERT INTO mplt_title VALUES(-1,'default','default',1)",
            "INSERT INTO mplt_title VALUES(1,'&#ff8800鹿','&a欢迎',1)",
            "INSERT INTO mplt_title VALUES(2,'<gradient:#ff0000:#0000ff>鹿鸣</gradient>','旧称号',1)",
            "INSERT INTO mplt_player_info VALUES('" + PLAYER + "',12.0,1,'Deer')",
            "INSERT INTO mplt_player_title VALUES(10,'" + PLAYER + "',1,-1,-1,-1)",
            "INSERT INTO mplt_player_title VALUES(11,'" + PLAYER + "',2,2020,1,2)",
            "INSERT INTO mplt_title_shop VALUES(20,1,5.0,-1,4,2030,1,2)",
            "INSERT INTO mplt_title_shop VALUES(21,2,0,0,-1,-1,-1,-1)");
    }

    private SourceConfig config() {
        return config(false, SourceConfig.CoinFractionPolicy.REJECT, Currency.COINS);
    }

    private SourceConfig config(boolean external, SourceConfig.CoinFractionPolicy fractions, Currency currency) {
        return new SourceConfig("survival-legacy", SourceConfig.DatabaseType.SQLITE, source,
                null, 0, null, "", "", ZoneId.of("Asia/Shanghai"), external,
                "[", "]", fractions, currency, true);
    }

    private void sql(String... statements) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source);
             Statement statement = connection.createStatement()) {
            for (String sql : statements) statement.execute(sql);
        }
    }

    private TitleStore target() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + directory.resolve("target.db"));
        TitleStore store = new TitleStore(dataSource, SqlDialect.SQLITE, CLOCK);
        store.initialize();
        return store;
    }

    @Test
    void readsActualSchemaReadOnlyAndPreservesDateAndSentinelSemantics() throws Exception {
        byte[] original = Files.readAllBytes(source);
        ImportPreview preview = importer.preview(config());
        assertTrue(preview.valid(), preview.issues().toString());
        assertEquals(2, preview.batch().titles().size());
        assertEquals("[鹿]", TitleText.plain(preview.batch().titles().getFirst().miniMessage()));
        assertEquals(12L, preview.batch().players().getFirst().coins());
        assertEquals("1", preview.batch().players().getFirst().equippedLegacyTitleId());
        assertNull(preview.batch().ownerships().getFirst().expiresAt());
        assertEquals(Instant.parse("2020-01-01T16:00:00Z"), preview.batch().ownerships().get(1).expiresAt());
        ImportListing listing = preview.batch().listings().getFirst();
        assertNull(listing.grantDurationDays());
        assertEquals(4L, listing.remainingStock());
        assertEquals(Instant.parse("2030-01-01T16:00:00Z"), listing.endsAt());
        assertFalse(preview.batch().listings().get(1).enabled());
        assertNull(preview.batch().listings().get(1).remainingStock());
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    void dryRunDoesNotCreateTargetOrMutateSourceAndSnapshotsAreRepeatable() throws Exception {
        ImportPreview first = importer.preview(config());
        ImportPreview second = importer.preview(config());
        assertEquals(first.sourceFingerprint(), second.sourceFingerprint());
        assertFalse(Files.exists(directory.resolve("target.db")));
        assertThrows(UnsupportedOperationException.class, () -> first.batch().titles().clear());
        sql("UPDATE mplt_player_info SET coin_d=13");
        assertNotEquals(first.sourceFingerprint(), importer.preview(config()).sourceFingerprint());
        assertEquals(12L, first.batch().players().getFirst().coins());
    }

    @Test
    void rejectsFractionalCoinsUnlessRoundingWasExplicitlySelected() throws Exception {
        sql("UPDATE mplt_player_info SET coin_d=12.8", "UPDATE mplt_title_shop SET price_d=2.7 WHERE id=20");
        ImportPreview rejected = importer.preview(config());
        assertFalse(rejected.valid());
        assertTrue(rejected.issues().stream().anyMatch(i -> i.code().equals("NON_INTEGER_BALANCE")));
        ImportPreview floored = importer.preview(config(false, SourceConfig.CoinFractionPolicy.FLOOR, Currency.COINS));
        assertTrue(floored.valid(), floored.issues().toString());
        assertEquals(12L, floored.batch().players().getFirst().coins());
        assertEquals(0, new BigDecimal("2").compareTo(floored.batch().listings().getFirst().price()));
        assertTrue(floored.issues().stream().anyMatch(i -> i.code().equals("BALANCE_ROUNDED")));
        assertTrue(floored.issues().stream().anyMatch(i -> i.code().equals("PRICE_ROUNDED")));
        ImportPreview rounded = importer.preview(config(false, SourceConfig.CoinFractionPolicy.ROUND_HALF_UP, Currency.COINS));
        assertEquals(13L, rounded.batch().players().getFirst().coins());
    }

    @Test
    void externalEconomyNeverTurnsStaleInternalValueIntoCoins() throws Exception {
        sql("UPDATE mplt_player_info SET coin_d=12345.6", "UPDATE mplt_title_shop SET price_d=2.75 WHERE id=20");
        ImportPreview preview = importer.preview(config(true, SourceConfig.CoinFractionPolicy.REJECT, Currency.VAULT));
        assertTrue(preview.valid(), preview.issues().toString());
        assertNull(preview.batch().players().getFirst().coins());
        assertEquals(0, new BigDecimal("2.75").compareTo(preview.batch().listings().getFirst().price()));
        assertTrue(preview.issues().stream().anyMatch(i -> i.code().equals("EXTERNAL_BALANCES_SKIPPED")));
    }

    @Test
    void rejectsInvalidDateInsteadOfGrantingPermanentOwnership() throws Exception {
        sql("UPDATE mplt_player_title SET expire_at_y=2030,expire_at_m=2,expire_at_d=30 WHERE id=10");
        ImportPreview preview = importer.preview(config());
        assertFalse(preview.valid());
        assertTrue(preview.issues().stream().anyMatch(i -> i.code().equals("INVALID_DATE")));
        try (TitleStore store = target()) {
            assertThrows(IllegalArgumentException.class, () -> importer.execute(preview, store));
            assertTrue(store.listTitles().isEmpty());
        }
    }

    @Test
    void expiredEquippedTitleDoesNotGainNewOwnership() throws Exception {
        sql("UPDATE mplt_player_info SET using_title_id=2");
        ImportPreview preview = importer.preview(config());
        assertTrue(preview.valid());
        assertNull(preview.batch().players().getFirst().equippedLegacyTitleId());
        assertEquals(2, preview.batch().ownerships().size());
    }

    @Test
    void supportsLegacyIntegerAmountsAndPackedCalendarDates() throws Exception {
        sql("ALTER TABLE mplt_player_info RENAME COLUMN coin_d TO coin",
            "ALTER TABLE mplt_title_shop RENAME COLUMN price_d TO price",
            "ALTER TABLE mplt_player_title DROP COLUMN expire_at_y",
            "ALTER TABLE mplt_player_title DROP COLUMN expire_at_m",
            "ALTER TABLE mplt_player_title DROP COLUMN expire_at_d",
            "ALTER TABLE mplt_player_title ADD COLUMN expire_at BIGINT DEFAULT -1",
            "UPDATE mplt_player_title SET expire_at=20300102 WHERE id=11");
        ImportPreview preview = importer.preview(config());
        assertTrue(preview.valid(), preview.issues().toString());
        assertEquals(12L, preview.batch().players().getFirst().coins());
        assertEquals(Instant.parse("2030-01-01T16:00:00Z"), preview.batch().ownerships().get(1).expiresAt());
    }

    @Test
    void repeatImportDoesNotRestoreSpentCoinsStockOrUnequippedSelection() {
        ImportPreview preview = importer.preview(config());
        assertTrue(preview.valid(), preview.issues().toString());
        try (TitleStore store = target()) {
            ImportReport first = importer.execute(preview, store);
            assertEquals(2, first.titles());
            assertEquals(2, store.inventory(PLAYER).size());
            assertTrue(store.equippedTitle(PLAYER).isPresent());
            store.adjustCoins(PLAYER, -4);
            store.unequip(PLAYER);
            UUID buyer = UUID.randomUUID();
            store.ensurePlayer(buyer, "Buyer");
            store.adjustCoins(buyer, 10);
            ShopListing listing = store.shop(true).stream().filter(s -> s.enabled()).findFirst().orElseThrow();
            store.purchaseCoins(buyer, listing.id(), listing.version());
            assertEquals(3L, store.listing(listing.id()).orElseThrow().remainingStock());
            ImportReport second = importer.execute(preview, store);
            assertEquals(0, second.titles());
            assertEquals(0, second.players());
            assertEquals(8, store.profile(PLAYER).coins());
            assertTrue(store.equippedTitle(PLAYER).isEmpty());
            assertEquals(3L, store.listing(listing.id()).orElseThrow().remainingStock());
            assertEquals(2, store.inventory(PLAYER).size());
        }
    }

    @Test
    void existingNativePlayerKeepsSelectionAndGetsImportedBalanceOnlyOnce() {
        try (TitleStore store = target()) {
            store.ensurePlayer(PLAYER, "NativeName");
            store.adjustCoins(PLAYER, 5);
            Title nativeTitle = store.createAdminTitle(UUID.randomUUID(), new TitleDraft("Native", "<green>Native", "native", true));
            store.grantTitle(PLAYER, nativeTitle.id(), null);
            store.equip(PLAYER, nativeTitle.id());
            ImportPreview preview = importer.preview(config());
            importer.execute(preview, store);
            importer.execute(preview, store);
            assertEquals(17, store.profile(PLAYER).coins());
            assertEquals(nativeTitle.id(), store.equippedTitle(PLAYER).orElseThrow().id());
            assertEquals(3, store.inventory(PLAYER).size());
        }
    }

    @Test
    void missingSourceIsNotCreatedAndCredentialsAreNeverInConfigString() throws Exception {
        Files.delete(source);
        ImportPreview preview = importer.preview(config());
        assertFalse(preview.valid());
        assertFalse(Files.exists(source));
        SourceConfig remote = new SourceConfig("remote", SourceConfig.DatabaseType.PGSQL, null,
                "localhost", 5432, "legacy", "private-user", "private-password", ZoneOffset.UTC,
                false, "", "", SourceConfig.CoinFractionPolicy.REJECT, Currency.COINS, true);
        assertFalse(remote.toString().contains("private-user"));
        assertFalse(remote.toString().contains("private-password"));
    }

    @Test
    void loadingLegacyConfigurationPreservesExternalEconomyAndDatabaseName() throws Exception {
        Files.writeString(directory.resolve("config.yml"), """
                Database:
                  Type: sqlite
                  Name: custom-legacy
                ExternalEco: true
                Prefix: '&a['
                Suffix: ']'
                """);
        SourceConfig loaded = LegacySourceConfigLoader.load(directory, "old-server", ZoneOffset.UTC,
                SourceConfig.CoinFractionPolicy.REJECT, Currency.COINS, true);
        assertTrue(loaded.externalEconomy());
        assertEquals(directory.resolve("custom-legacy.db"), loaded.sqliteFile());
        assertEquals("&a[", loaded.prefix());
    }
}
