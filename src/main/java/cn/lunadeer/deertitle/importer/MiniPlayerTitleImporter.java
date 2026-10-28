package cn.lunadeer.deertitle.importer;

import cn.lunadeer.deertitle.domain.ImportBatch;
import cn.lunadeer.deertitle.domain.ImportListing;
import cn.lunadeer.deertitle.domain.ImportOwnership;
import cn.lunadeer.deertitle.domain.ImportPlayer;
import cn.lunadeer.deertitle.domain.ImportReport;
import cn.lunadeer.deertitle.domain.ImportTitle;
import cn.lunadeer.deertitle.domain.Currency;
import cn.lunadeer.deertitle.storage.TitleStore;
import cn.lunadeer.deertitle.text.LegacyTextConverter;
import cn.lunadeer.deertitle.text.TitleText;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Legacy database reader. Target writes belong exclusively to TitleStore's transaction. */
public final class MiniPlayerTitleImporter {
    private static final int MAX_ROWS_PER_TABLE = 100_000;
    private static final int MAX_TEXT_LENGTH = 8192;
    private final Clock clock;

    public MiniPlayerTitleImporter() {
        this(Clock.systemUTC());
    }

    public MiniPlayerTitleImporter(Clock clock) {
        this.clock = clock;
    }

    public ImportPreview preview(SourceConfig config) {
        Instant readAt = clock.instant();
        List<ImportIssue> issues = new ArrayList<>();
        List<ImportTitle> titles = new ArrayList<>();
        List<ImportPlayer> players = new ArrayList<>();
        List<ImportOwnership> ownerships = new ArrayList<>();
        List<ImportListing> listings = new ArrayList<>();
        Set<String> titleIds = new HashSet<>();
        Set<UUID> playerIds = new HashSet<>();
        ReadBudget budget = new ReadBudget();

        if (config.externalEconomy()) {
            warning(issues, "EXTERNAL_BALANCES_SKIPPED", "players",
                    "来源使用外部经济，旧内部余额与 Vault 余额均不导入为称号币。");
        }
        warning(issues, "CUSTOM_METADATA_UNAVAILABLE", "titles",
                "旧库没有可靠的自定义创建者或剩余次数；保留称号与拥有关系，不授予编辑权限或虚构次数。");
        warning(issues, "PHYSICAL_CARDS_NOT_IN_DATABASE", "cards",
                "尚未使用的旧称号卡存放在物品中，不属于数据库导入范围。");

        try (Connection connection = open(config)) {
            read(connection, "mplt_title", "id", row -> {
                String id = row.id("id");
                if (id.equals("-1")) return;
                String raw = row.text("title", "");
                String description = row.text("description", "");
                if (raw.isBlank()) throw new RowProblem("EMPTY_TITLE", "称号文本为空。");
                String display = convert("&#ffffff" + config.prefix() + raw + "&#ffffff" + config.suffix());
                String convertedDescription = convert(description);
                String name = TitleText.plain(LegacyTextConverter.convert(raw)).strip();
                if (name.isEmpty() || name.length() > 128) {
                    name = "MiniPlayerTitle #" + id;
                    warning(issues, "TITLE_NAME_REPLACED", "title:" + id,
                            "内部名称过长或为空，改用来源编号；完整显示文本保留。");
                }
                budget.text(display.length() + convertedDescription.length() + name.length());
                titles.add(new ImportTitle(id, name, display, convertedDescription, row.bool("enabled", true)));
                titleIds.add(id);
            }, issues, budget);

            read(connection, "mplt_player_info", "uuid", row -> {
                UUID id = row.uuid("uuid");
                if (playerIds.contains(id)) throw new RowProblem("DUPLICATE_PLAYER", "多个来源记录对应相同玩家 UUID。");
                String name = row.text("last_use_name", "");
                if (name.isBlank() || name.equalsIgnoreCase("null")) name = id.toString();
                if (name.length() > 64) throw new RowProblem("INVALID_PLAYER_NAME", "玩家名称超过支持长度。");
                budget.text(name.length() + 36);
                Long coins = config.externalEconomy() ? null : coins(row, config, issues, id);
                String equipped = row.has("using_title_id") ? row.id("using_title_id") : null;
                if ("-1".equals(equipped)) equipped = null;
                if (equipped != null && !titleIds.contains(equipped)) {
                    warning(issues, "MISSING_EQUIPPED_TITLE", "player:" + id,
                            "佩戴记录指向不存在的称号，不导入该佩戴状态。");
                    equipped = null;
                }
                players.add(new ImportPlayer(id, name, coins, equipped));
                playerIds.add(id);
            }, issues, budget);

            read(connection, "mplt_player_title", "id", row -> {
                String id = row.id("id");
                UUID player = row.uuid("player_uuid");
                String title = row.id("title_id");
                if (title.equals("-1")) return;
                if (!playerIds.contains(player) || !titleIds.contains(title)) {
                    throw new RowProblem("ORPHAN_OWNERSHIP", "拥有记录引用不存在的玩家或称号。");
                }
                Instant expiry = date(row, "expire_at", config);
                ownerships.add(new ImportOwnership(id, player, title, expiry));
            }, issues, budget);

            if (config.importShop()) {
                read(connection, "mplt_title_shop", "id", row -> {
                    String id = row.id("id");
                    String title = row.id("title_id");
                    if (title.equals("-1")) return;
                    if (!titleIds.contains(title)) throw new RowProblem("ORPHAN_LISTING", "商店记录引用不存在的称号。");
                    BigDecimal price = row.decimal(row.has("price_d") ? "price_d" : "price");
                    if (price.signum() < 0) throw new RowProblem("NEGATIVE_PRICE", "商店价格不能为负数。");
                    if (config.currency() == Currency.COINS && price.stripTrailingZeros().scale() > 0) {
                        if (config.coinFractionPolicy() == SourceConfig.CoinFractionPolicy.REJECT) {
                            throw new RowProblem("NON_INTEGER_PRICE", "称号币商品价格不是整数，请明确选择小数处理策略。");
                        }
                        RoundingMode rounding = config.coinFractionPolicy() == SourceConfig.CoinFractionPolicy.FLOOR
                                ? RoundingMode.FLOOR : RoundingMode.HALF_UP;
                        price = price.setScale(0, rounding);
                        warning(issues, "PRICE_ROUNDED", "listing:" + id,
                                "商品价格按明确配置的 " + config.coinFractionPolicy().name() + " 策略转换为整数。");
                    }
                    if (price.compareTo(new BigDecimal("1000000000000")) > 0 || price.stripTrailingZeros().scale() > 2) {
                        throw new RowProblem("INVALID_PRICE_PRECISION", "商品价格超出上限或包含超过两位小数。");
                    }
                    long days = row.number("days");
                    long stock = row.number("amount");
                    if (days < -1 || days > 365000) throw new RowProblem("INVALID_DURATION", "称号有效天数无效。");
                    if (stock < -1) throw new RowProblem("INVALID_STOCK", "商店剩余库存无效。");
                    boolean enabled = days != 0;
                    Instant end = date(row, "sale_end_at", config);
                    listings.add(new ImportListing(id, title, price, days <= 0 ? null : (int) days,
                            stock == -1 ? null : stock, end, enabled));
                }, issues, budget);
            }
            connection.rollback();
        } catch (BudgetExceeded exception) {
            error(issues, "SOURCE_TOO_LARGE", "source",
                    "来源超过单次预览预算（100000 条记录或 8000000 个文本字符），未执行部分导入。");
        } catch (SQLException | IllegalStateException exception) {
            // Do not expose JDBC diagnostics: some drivers include credentials or URL properties.
            error(issues, "SOURCE_READ_FAILED", "source",
                    "无法完整读取来源，请检查只读权限、数据库配置及 MiniPlayerTitle 表结构。");
        }

        // A stale/equipped definition is not authority to grant a missing or expired entitlement.
        Set<String> activeOwned = new HashSet<>();
        for (ImportOwnership ownership : ownerships) {
            if (ownership.expiresAt() == null || ownership.expiresAt().isAfter(readAt)) {
                activeOwned.add(ownership.playerId() + ":" + ownership.legacyTitleId());
            }
        }
        for (int index = 0; index < players.size(); index++) {
            ImportPlayer player = players.get(index);
            if (player.equippedLegacyTitleId() != null
                    && !activeOwned.contains(player.uuid() + ":" + player.equippedLegacyTitleId())) {
                players.set(index, new ImportPlayer(player.uuid(), player.name(), player.coins(), null));
                warning(issues, "INACTIVE_EQUIPPED_TITLE", "player:" + player.uuid(),
                        "佩戴称号没有有效拥有记录，保留背包历史但不导入佩戴状态。");
            }
        }
        ImportBatch batch = new ImportBatch(config.sourceId(), config.currency(), List.copyOf(titles),
                List.copyOf(players), List.copyOf(ownerships), List.copyOf(listings));
        return new ImportPreview(batch, issues, fingerprint(batch), readAt);
    }

    public ImportReport execute(ImportPreview preview, TitleStore store) {
        if (!preview.valid()) {
            throw new IllegalArgumentException("导入预览存在错误，未写入任何目标数据。");
        }
        return store.importData(preview.batch());
    }

    private static String convert(String raw) {
        if (raw.length() > MAX_TEXT_LENGTH) throw new RowProblem("TEXT_TOO_LONG", "来源文本超过 8192 字符。");
        String converted = LegacyTextConverter.convert(raw);
        if (converted.length() > MAX_TEXT_LENGTH) throw new RowProblem("TEXT_TOO_LONG", "转换后的文本超过 8192 字符。");
        return converted;
    }

    private static Long coins(Row row, SourceConfig config, List<ImportIssue> issues, UUID player) throws SQLException {
        if (!row.has("coin_d") && !row.has("coin")) {
            warning(issues, "COIN_COLUMN_MISSING", "player:" + player, "旧库缺少余额字段，不导入该玩家余额。");
            return null;
        }
        BigDecimal amount = row.decimal(row.has("coin_d") ? "coin_d" : "coin");
        if (amount.signum() < 0) throw new RowProblem("NEGATIVE_BALANCE", "旧称号币余额不能为负数。");
        if (amount.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            throw new RowProblem("BALANCE_OVERFLOW", "旧余额超出称号币整数范围。");
        }
        try {
            return amount.longValueExact();
        } catch (ArithmeticException exception) {
            if (config.coinFractionPolicy() == SourceConfig.CoinFractionPolicy.REJECT) {
                throw new RowProblem("NON_INTEGER_BALANCE", "旧余额不是可表示的整数；请明确选择小数处理策略后重新预览。");
            }
            RoundingMode rounding = config.coinFractionPolicy() == SourceConfig.CoinFractionPolicy.FLOOR
                    ? RoundingMode.FLOOR : RoundingMode.HALF_UP;
            try {
                long rounded = amount.setScale(0, rounding).longValueExact();
                warning(issues, "BALANCE_ROUNDED", "player:" + player,
                        "余额按明确配置的 " + config.coinFractionPolicy().name() + " 策略转换为整数。");
                return rounded;
            } catch (ArithmeticException overflow) {
                throw new RowProblem("BALANCE_OVERFLOW", "旧余额超出称号币整数范围。");
            }
        }
    }

    private static Instant date(Row row, String prefix, SourceConfig config) throws SQLException {
        long year;
        long month;
        long day;
        boolean hasYear = row.has(prefix + "_y");
        boolean hasMonth = row.has(prefix + "_m");
        boolean hasDay = row.has(prefix + "_d");
        if (hasYear || hasMonth || hasDay) {
            if (!(hasYear && hasMonth && hasDay)) throw new RowProblem("INCOMPLETE_DATE", "旧日期字段不完整。");
            year = row.number(prefix + "_y");
            month = row.number(prefix + "_m");
            day = row.number(prefix + "_d");
        } else if (row.has(prefix)) {
            long packed = row.number(prefix);
            if (packed == -1) return null;
            year = packed / 10000;
            month = packed % 10000 / 100;
            day = packed % 100;
        } else {
            throw new RowProblem("MISSING_DATE", "缺少旧到期字段，不能将未知期限自动视为永久。");
        }
        if (year == -1 && month == -1 && day == -1) return null;
        try {
            if (year < 1 || year > 9999) throw new DateTimeException("year");
            return LocalDate.of((int) year, Math.toIntExact(month), Math.toIntExact(day))
                    .atStartOfDay(config.timeZone()).toInstant();
        } catch (DateTimeException | ArithmeticException exception) {
            throw new RowProblem("INVALID_DATE", "旧日期无效，未自动扩大为永久期限。");
        }
    }

    private static Connection open(SourceConfig config) throws SQLException {
        Properties properties = new Properties();
        String url;
        if (config.type() == SourceConfig.DatabaseType.SQLITE) {
            if (!Files.isRegularFile(config.sqliteFile())) throw new IllegalStateException("Source file unavailable");
            url = "jdbc:sqlite:" + config.sqliteFile().toUri().toASCIIString() + "?mode=ro";
        } else {
            String protocol = config.type() == SourceConfig.DatabaseType.PGSQL ? "postgresql" : "mysql";
            url = "jdbc:" + protocol + "://" + config.host() + ":" + config.port() + "/" + config.database();
            properties.setProperty("user", config.username());
            properties.setProperty("password", config.password());
            properties.setProperty("connectTimeout", config.type() == SourceConfig.DatabaseType.MYSQL ? "10000" : "10");
            properties.setProperty("socketTimeout", config.type() == SourceConfig.DatabaseType.MYSQL ? "30000" : "30");
            if (config.type() == SourceConfig.DatabaseType.MYSQL) {
                properties.setProperty("useCursorFetch", "true");
                properties.setProperty("useServerPrepStmts", "true");
            }
        }
        Connection connection = DriverManager.getConnection(url, properties);
        try {
            if (config.type() == SourceConfig.DatabaseType.SQLITE) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA query_only = ON");
                    statement.execute("PRAGMA busy_timeout = 5000");
                }
            } else {
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            }
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException failure) {
            connection.close();
            throw failure;
        }
    }

    private static void read(Connection connection, String table, String sort,
                             RowConsumer action, List<ImportIssue> issues, ReadBudget budget) throws SQLException {
        // Table/sort identifiers are fixed constants above, never sourced from user SQL.
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + table + " ORDER BY " + sort)) {
            statement.setQueryTimeout(30);
            statement.setFetchSize(1000);
            statement.setMaxRows(MAX_ROWS_PER_TABLE + 1);
            try (ResultSet result = statement.executeQuery()) {
                Row row = new Row(result);
                int count = 0;
                while (result.next()) {
                    budget.row();
                    if (++count > MAX_ROWS_PER_TABLE) {
                        error(issues, "SOURCE_TOO_LARGE", table, "单表超过 100000 条，已停止预览；没有执行部分导入。");
                        return;
                    }
                    try {
                        action.accept(row);
                    } catch (RowProblem problem) {
                        error(issues, problem.code, table + ":row" + count, problem.getMessage());
                    } catch (IllegalArgumentException exception) {
                        error(issues, "INVALID_ROW", table + ":row" + count, "记录内容、UUID 或格式无效。");
                    }
                }
            }
        }
    }

    private static String fingerprint(ImportBatch batch) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(batch.sourceKey().getBytes(StandardCharsets.UTF_8));
            hash.update(batch.currency().name().getBytes(StandardCharsets.UTF_8));
            for (List<?> group : List.of(batch.titles(), batch.players(), batch.ownerships(), batch.listings())) {
                hash.update((byte) 0);
                for (Object value : group) {
                    hash.update(value.toString().getBytes(StandardCharsets.UTF_8));
                    hash.update((byte) '\n');
                }
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static void warning(List<ImportIssue> issues, String code, String record, String message) {
        issues.add(new ImportIssue(ImportIssue.Severity.WARNING, code, record, message));
    }

    private static void error(List<ImportIssue> issues, String code, String record, String message) {
        issues.add(new ImportIssue(ImportIssue.Severity.ERROR, code, record, message));
    }

    @FunctionalInterface
    private interface RowConsumer { void accept(Row row) throws SQLException; }

    private static final class BudgetExceeded extends RuntimeException { }

    private static final class ReadBudget {
        private int rows;
        private long characters;
        void row() { if (++rows > MAX_ROWS_PER_TABLE) throw new BudgetExceeded(); }
        void text(int count) { if ((characters += count) > 8_000_000) throw new BudgetExceeded(); }
    }

    private static final class RowProblem extends IllegalArgumentException {
        private final String code;
        private RowProblem(String code, String message) { super(message); this.code = code; }
    }

    private static final class Row {
        private final ResultSet result;
        private final Map<String, Integer> columns = new HashMap<>();

        Row(ResultSet result) throws SQLException {
            this.result = result;
            ResultSetMetaData metadata = result.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                columns.put(metadata.getColumnLabel(i).toLowerCase(Locale.ROOT), i);
            }
        }

        boolean has(String column) { return columns.containsKey(column); }

        Object get(String column) throws SQLException {
            Integer index = columns.get(column);
            if (index == null) throw new RowProblem("MISSING_COLUMN", "缺少必要的来源字段。");
            return result.getObject(index);
        }

        String text(String column, String fallback) throws SQLException {
            if (!has(column)) return fallback;
            Object value = get(column);
            return value == null ? fallback : value.toString();
        }

        BigDecimal decimal(String column) throws SQLException {
            Object value = get(column);
            if (value == null) throw new RowProblem("NULL_NUMBER", "必要数值为空。");
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException invalid) {
                throw new RowProblem("INVALID_NUMBER", "旧数值不是有效的十进制数字。");
            }
        }

        long number(String column) throws SQLException {
            try { return decimal(column).longValueExact(); }
            catch (ArithmeticException invalid) { throw new RowProblem("INVALID_INTEGER", "必要整数无效或越界。"); }
        }

        String id(String column) throws SQLException { return Long.toString(number(column)); }
        UUID uuid(String column) throws SQLException {
            String raw = text(column, "");
            UUID id = UUID.fromString(raw);
            if (!id.toString().equalsIgnoreCase(raw)) throw new RowProblem("INVALID_UUID", "玩家 UUID 必须是完整的标准格式。");
            return id;
        }

        boolean bool(String column, boolean fallback) throws SQLException {
            if (!has(column)) return fallback;
            Object value = get(column);
            if (value instanceof Boolean bool) return bool;
            if (value != null && (value.toString().equals("1") || value.toString().equalsIgnoreCase("true"))) return true;
            if (value != null && (value.toString().equals("0") || value.toString().equalsIgnoreCase("false"))) return false;
            throw new RowProblem("INVALID_BOOLEAN", "旧布尔字段无效。");
        }
    }
}
