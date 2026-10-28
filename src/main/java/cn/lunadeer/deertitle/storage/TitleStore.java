package cn.lunadeer.deertitle.storage;

import cn.lunadeer.deertitle.domain.*;
import cn.lunadeer.deertitle.domain.Currency;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static cn.lunadeer.deertitle.domain.ErrorCode.*;

/**
 * Synchronous JDBC boundary. Run calls on the plugin's bounded database executor.
 * A database mutex serializes mutations across servers: titles are low-frequency
 * human operations, so one explicit lock is preferable to fragile lock ordering.
 * Reads never acquire this mutex. Every JDBC resource has lexical ownership.
 */
public final class TitleStore implements AutoCloseable {
    private static final Object SQLITE_SCHEMA_LOCK = new Object();
    private static final String DELIVERY_RETRY = "automatic-delivery-retry";
    private static final long PAYMENT_EXPIRY_SECONDS = 300;
    private static final long DELIVERY_RETRY_SECONDS = 30;
    private final DataSource source;
    private final SqlDialect dialect;
    private final Clock clock;
    private final long initialCoins;

    public TitleStore(DataSource source, SqlDialect dialect) {
        this(source, dialect, Clock.systemUTC());
    }

    public TitleStore(DataSource source, SqlDialect dialect, Clock clock) {
        this(source, dialect, clock, 0);
    }

    public TitleStore(DataSource source, SqlDialect dialect, long initialCoins) {
        this(source, dialect, Clock.systemUTC(), initialCoins);
    }

    public TitleStore(DataSource source, SqlDialect dialect, Clock clock, long initialCoins) {
        if (initialCoins < 0 || initialCoins > 1_000_000_000_000L)
            throw new IllegalArgumentException("初始称号币必须为 0–1000000000000 的整数");
        this.source = Objects.requireNonNull(source);
        this.dialect = Objects.requireNonNull(dialect);
        this.clock = Objects.requireNonNull(clock);
        this.initialCoins = initialCoins;
    }

    public void initialize() {
        // SQLite is deliberately single-server. This also covers independent pools
        // opened by that server, while network DBs use cross-process advisory locks.
        if (dialect == SqlDialect.SQLITE) {
            synchronized (SQLITE_SCHEMA_LOCK) { initializeSchema(); }
        } else initializeSchema();
    }

    private void initializeSchema() {
        read(c -> {
            lockSchema(c);
            try {
            if (dialect == SqlDialect.SQLITE) pragma(c, "PRAGMA journal_mode=WAL");
            String id = dialect.generatedId();
            String[] statements = {
                "CREATE TABLE IF NOT EXISTS dt_schema (version INTEGER PRIMARY KEY)",
                "CREATE TABLE IF NOT EXISTS dt_mutex (id INTEGER PRIMARY KEY)",
                "CREATE TABLE IF NOT EXISTS dt_title (id " + id + ", name VARCHAR(128) NOT NULL, mini_message TEXT NOT NULL, description TEXT NOT NULL, creator VARCHAR(36), custom_flag INTEGER NOT NULL, enabled INTEGER NOT NULL, version BIGINT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS dt_player (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(64) NOT NULL, equipped_title_id BIGINT, coins BIGINT NOT NULL, custom_credits INTEGER NOT NULL, revision BIGINT NOT NULL, FOREIGN KEY (equipped_title_id) REFERENCES dt_title(id))",
                "CREATE TABLE IF NOT EXISTS dt_ownership (player_uuid VARCHAR(36) NOT NULL, title_id BIGINT NOT NULL, expires_at BIGINT, created_at BIGINT NOT NULL, PRIMARY KEY(player_uuid,title_id), FOREIGN KEY(player_uuid) REFERENCES dt_player(uuid), FOREIGN KEY(title_id) REFERENCES dt_title(id))",
                "CREATE TABLE IF NOT EXISTS dt_listing (id " + id + ", title_id BIGINT NOT NULL, currency VARCHAR(16) NOT NULL, price VARCHAR(40) NOT NULL, total_stock BIGINT, sold BIGINT NOT NULL, starts_at BIGINT, ends_at BIGINT, grant_days INTEGER, enabled INTEGER NOT NULL, version BIGINT NOT NULL, FOREIGN KEY(title_id) REFERENCES dt_title(id))",
                "CREATE TABLE IF NOT EXISTS dt_payment (id VARCHAR(36) PRIMARY KEY, player_uuid VARCHAR(36) NOT NULL, kind VARCHAR(16) NOT NULL, state VARCHAR(16) NOT NULL, listing_id BIGINT, title_id BIGINT, credits INTEGER NOT NULL, amount VARCHAR(40) NOT NULL, grant_days INTEGER, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, failure VARCHAR(512), FOREIGN KEY(player_uuid) REFERENCES dt_player(uuid), FOREIGN KEY(listing_id) REFERENCES dt_listing(id), FOREIGN KEY(title_id) REFERENCES dt_title(id))",
                "CREATE TABLE IF NOT EXISTS dt_import_map (source_key VARCHAR(128) NOT NULL, kind VARCHAR(16) NOT NULL, legacy_id VARCHAR(128) NOT NULL, target_id VARCHAR(64) NOT NULL, PRIMARY KEY(source_key,kind,legacy_id))"
            };
            for (String sql : statements) execute(c, sql);
            ensureIndex(c, "dt_payment", "dt_payment_player_state", "player_uuid", "state");
            ensureIndex(c, "dt_payment", "dt_payment_state_created", "state", "created_at");
            ensureIndex(c, "dt_payment", "dt_payment_state_updated", "state", "updated_at");
            ensureIndex(c, "dt_listing", "dt_listing_title", "title_id");
            ensureIndex(c, "dt_ownership", "dt_ownership_title", "title_id");
            execute(c, dialect.insertMutex());
            List<Integer> versions = query(c, "SELECT version FROM dt_schema", r -> r.getInt(1));
            if (versions.stream().anyMatch(v -> v != 1)) throw error(STORAGE_FAILURE);
            if (versions.isEmpty()) execute(c, "INSERT INTO dt_schema(version) VALUES (1)");
            return null;
            } finally { unlockSchema(c); }
        });
    }

    private void lockSchema(Connection c) throws SQLException {
        try {
            if (dialect == SqlDialect.POSTGRESQL)
                query(c, "SELECT pg_advisory_lock(?)", r -> true, 0x446565725469746cL);
            else if (dialect == SqlDialect.MYSQL) {
                int result = one(c, "SELECT GET_LOCK('deertitle-schema-v1', 15)", r -> r.getInt(1)).orElse(0);
                if (result != 1) throw new SQLException("Timed out acquiring schema initialization lock");
            }
        } catch (SQLException failure) {
            // Cancellation can race successful lock acquisition; discard that session.
            try { c.abort(Runnable::run); } catch (SQLException abort) { failure.addSuppressed(abort); }
            throw failure;
        }
    }

    private void ensureIndex(Connection c, String table, String name, String... columns) throws SQLException {
        Map<String, SortedMap<Integer, String>> indexes = new HashMap<>();
        String schema = dialect == SqlDialect.POSTGRESQL ? c.getSchema() : null;
        try (ResultSet metadata = c.getMetaData().getIndexInfo(c.getCatalog(), schema, table, false, false)) {
            while (metadata.next()) {
                String index = metadata.getString("INDEX_NAME");
                String column = metadata.getString("COLUMN_NAME");
                if (index != null && column != null)
                    indexes.computeIfAbsent(index, ignored -> new TreeMap<>()).put(metadata.getInt("ORDINAL_POSITION"), column.toLowerCase(Locale.ROOT));
            }
        }
        List<String> wanted = List.of(columns);
        for (SortedMap<Integer, String> index : indexes.values()) {
            List<String> actual = new ArrayList<>(index.values());
            if (actual.size() >= wanted.size() && actual.subList(0, wanted.size()).equals(wanted)) return;
        }
        // Identifiers are fixed internal constants; metadata and the schema lock make
        // this portable to MySQL, which has no CREATE INDEX IF NOT EXISTS syntax.
        execute(c, "CREATE INDEX " + name + " ON " + table + "(" + String.join(",", columns) + ")");
    }

    private void unlockSchema(Connection c) throws SQLException {
        try {
            if (dialect == SqlDialect.POSTGRESQL)
                query(c, "SELECT pg_advisory_unlock(?)", r -> true, 0x446565725469746cL);
            else if (dialect == SqlDialect.MYSQL)
                query(c, "SELECT RELEASE_LOCK('deertitle-schema-v1')", r -> true);
        } catch (SQLException failure) {
            // A session lock must never return to the pool still held.
            try { c.abort(Runnable::run); } catch (SQLException abort) { failure.addSuppressed(abort); }
            throw failure;
        }
    }

    public PlayerProfile ensurePlayer(UUID uuid, String name) {
        Objects.requireNonNull(uuid);
        String safeName = checkedName(name, 64);
        PlayerProfile existing = read(c -> findPlayer(c, uuid).orElse(null));
        if (existing != null && existing.name().equals(safeName)) return existing;
        return write(c -> {
            if (findPlayer(c, uuid).isEmpty()) insertPlayer(c, uuid, safeName);
            else execute(c, "UPDATE dt_player SET name=? WHERE uuid=?", safeName, uuid.toString());
            return player(c, uuid);
        });
    }

    public PlayerProfile profile(UUID uuid) { return read(c -> player(c, uuid)); }

    public Optional<Title> title(long id) { return read(c -> findTitle(c, id)); }

    public List<Title> listTitles() {
        return read(c -> query(c, "SELECT * FROM dt_title WHERE custom_flag=0 ORDER BY id DESC", TitleStore::mapTitle));
    }

    public List<OwnedTitle> inventory(UUID uuid) {
        return read(c -> query(c,
                "SELECT t.*,o.expires_at FROM dt_ownership o JOIN dt_title t ON t.id=o.title_id WHERE o.player_uuid=? ORDER BY t.id DESC",
                r -> new OwnedTitle(mapTitle(r), instant(r, "expires_at")), uuid.toString()));
    }

    public Optional<Title> equippedTitle(UUID uuid) {
        return equippedOwnership(uuid).map(OwnedTitle::title);
    }

    public Optional<OwnedTitle> equippedOwnership(UUID uuid) {
        return read(c -> one(c,
                "SELECT t.*,o.expires_at FROM dt_player p JOIN dt_ownership o ON o.player_uuid=p.uuid AND o.title_id=p.equipped_title_id JOIN dt_title t ON t.id=o.title_id WHERE p.uuid=? AND t.enabled=1 AND (o.expires_at IS NULL OR o.expires_at>?)",
                r -> new OwnedTitle(mapTitle(r), instant(r, "expires_at")), uuid.toString(), now().toEpochMilli()));
    }

    public void equip(UUID uuid, long titleId) {
        write(c -> {
            OwnedTitle owned = owned(c, uuid, titleId).orElseThrow(() -> error(NOT_OWNED));
            if (owned.expired(now())) throw error(EXPIRED);
            if (!owned.title().enabled()) throw error(NOT_FOUND);
            execute(c, "UPDATE dt_player SET equipped_title_id=?,revision=revision+1 WHERE uuid=?", titleId, uuid.toString());
            return null;
        });
    }

    public void unequip(UUID uuid) {
        write(c -> {
            player(c, uuid);
            execute(c, "UPDATE dt_player SET equipped_title_id=NULL,revision=revision+1 WHERE uuid=?", uuid.toString());
            return null;
        });
    }

    public Title createAdminTitle(UUID creator, TitleDraft draft) {
        validate(draft);
        return write(c -> insertTitle(c, creator, false, draft));
    }

    public Title updateAdminTitle(long id, long expectedVersion, TitleDraft draft) {
        validate(draft);
        return write(c -> {
            Title old = requireTitle(c, id);
            if (old.custom()) throw error(INVALID_INPUT);
            return updateTitle(c, old, expectedVersion, draft);
        });
    }

    public Title createCustomTitle(UUID uuid, TitleDraft draft) {
        validate(draft);
        return write(c -> {
            consumeCredit(c, uuid);
            Title title = insertTitle(c, uuid, true, draft);
            grant(c, uuid, title.id(), null);
            return title;
        });
    }

    public Title updateCustomTitle(UUID uuid, long id, long expectedVersion, TitleDraft draft) {
        validate(draft);
        return write(c -> {
            Title old = requireTitle(c, id);
            if (!old.custom() || !uuid.equals(old.creator())) throw error(NOT_OWNED);
            OwnedTitle ownership = owned(c, uuid, id).orElseThrow(() -> error(NOT_OWNED));
            if (ownership.expired(now())) throw error(EXPIRED);
            if (old.version() != expectedVersion) throw error(STALE_VERSION);
            consumeCredit(c, uuid);
            return updateTitle(c, old, expectedVersion, draft);
        });
    }

    public void grantTitle(UUID uuid, long titleId, Instant expiresAt) {
        write(c -> {
            requireTitle(c, titleId);
            ensurePlayer(c, uuid);
            grant(c, uuid, titleId, expiresAt);
            return null;
        });
    }

    public List<ShopListing> shop(boolean includeUnavailable) {
        return read(c -> {
            List<ShopListing> values = query(c, listingSelect() + " ORDER BY l.id DESC", TitleStore::mapListing);
            return includeUnavailable ? values : values.stream().filter(l -> l.available(now())).toList();
        });
    }

    public Optional<ShopListing> listing(long id) { return read(c -> findListing(c, id)); }

    public ShopListing saveListing(Long id, long expectedVersion, ListingDraft draft) {
        return saveListing(id, expectedVersion, null, draft);
    }

    /** Remaining-stock edits must match the sales counter observed by the administrator. */
    public ShopListing saveListing(Long id, long expectedVersion, Long expectedSold, ListingDraft draft) {
        validate(draft);
        return write(c -> {
            Title title = requireTitle(c, draft.titleId());
            if (title.custom()) throw error(INVALID_INPUT);
            BigDecimal price = checkedPrice(draft.price(), draft.currency());
            long target;
            if (id == null) {
                target = insert(c, "INSERT INTO dt_listing(title_id,currency,price,total_stock,sold,starts_at,ends_at,grant_days,enabled,version) VALUES (?,?,?,?,0,?,?,?,?,0)",
                        draft.titleId(), draft.currency().name(), price.toPlainString(), draft.totalStock(), epoch(draft.startsAt()), epoch(draft.endsAt()), draft.grantDurationDays(), bit(draft.enabled()));
            } else {
                ShopListing old = requireListing(c, id);
                if (old.version() != expectedVersion) throw error(STALE_VERSION);
                if (expectedSold != null && old.sold() != expectedSold) throw error(STALE_VERSION);
                if (draft.totalStock() != null && draft.totalStock() < old.sold()) throw error(INVALID_INPUT);
                // Existing reservations carry a title snapshot; never repurpose their listing.
                if (old.title().id() != draft.titleId()) throw error(INVALID_INPUT);
                execute(c, "UPDATE dt_listing SET currency=?,price=?,total_stock=?,starts_at=?,ends_at=?,grant_days=?,enabled=?,version=version+1 WHERE id=?",
                        draft.currency().name(), price.toPlainString(), draft.totalStock(), epoch(draft.startsAt()), epoch(draft.endsAt()), draft.grantDurationDays(), bit(draft.enabled()), id);
                target = id;
            }
            return requireListing(c, target);
        });
    }

    public void purchaseCoins(UUID uuid, long listingId, long expectedVersion) {
        write(c -> {
            ShopListing listing = purchasable(c, uuid, listingId, expectedVersion, Currency.COINS);
            takeCoins(c, uuid, listing.price().longValueExact());
            reserve(c, listing);
            grant(c, uuid, listing.title().id(), expiry(listing.grantDurationDays()));
            return null;
        });
    }

    public void buyCreditsCoins(UUID uuid, int count, long totalCost) {
        if (count <= 0 || totalCost < 0) throw error(INVALID_INPUT);
        write(c -> {
            checkNoPending(c, uuid);
            takeCoins(c, uuid, totalCost);
            addCredits(c, uuid, count);
            return null;
        });
    }

    public void adjustCoins(UUID uuid, long delta) {
        write(c -> {
            ensurePlayer(c, uuid);
            PlayerProfile old = player(c, uuid);
            long updated;
            try { updated = Math.addExact(old.coins(), delta); }
            catch (ArithmeticException e) { throw error(INVALID_INPUT); }
            if (updated < 0) throw error(INSUFFICIENT_COINS);
            execute(c, "UPDATE dt_player SET coins=?,revision=revision+1 WHERE uuid=?", updated, uuid.toString());
            return null;
        });
    }

    public void adjustCredits(UUID uuid, int delta) {
        write(c -> { ensurePlayer(c, uuid); addCredits(c, uuid, delta); return null; });
    }

    public PaymentOperation prepareShopPayment(UUID uuid, long listingId, long expectedVersion) {
        return write(c -> {
            ShopListing listing = purchasable(c, uuid, listingId, expectedVersion, Currency.VAULT);
            reserve(c, listing);
            return insertPayment(c, uuid, PaymentKind.SHOP, listing.id(), listing.title().id(), 0,
                    listing.price(), listing.grantDurationDays());
        });
    }

    public PaymentOperation prepareCreditPayment(UUID uuid, int credits, BigDecimal total) {
        if (credits <= 0) throw error(INVALID_INPUT);
        BigDecimal amount = checkedPrice(total, Currency.VAULT);
        return write(c -> {
            PlayerProfile player = player(c, uuid);
            checkNoPending(c, uuid);
            try { Math.addExact(player.customCredits(), credits); }
            catch (ArithmeticException e) { throw error(INVALID_INPUT); }
            return insertPayment(c, uuid, PaymentKind.CREDITS, null, null, credits, amount, null);
        });
    }

    /** Only the caller winning PREPARED -> CHARGING may invoke Vault withdrawal. */
    public PaymentOperation markCharging(UUID id) {
        return write(c -> {
            PaymentOperation old = requirePayment(c, id);
            if (old.state() != PaymentState.PREPARED) throw error(INVALID_PAYMENT_STATE);
            paymentState(c, id, PaymentState.CHARGING, null);
            return requirePayment(c, id);
        });
    }

    public PaymentOperation markCharged(UUID id) {
        return write(c -> {
            PaymentOperation old = requirePayment(c, id);
            if (old.state() == PaymentState.CHARGED || old.state() == PaymentState.COMPLETE) return old;
            if (old.state() != PaymentState.CHARGING) throw error(INVALID_PAYMENT_STATE);
            paymentState(c, id, PaymentState.CHARGED, null);
            return requirePayment(c, id);
        });
    }

    public void completePayment(UUID id) {
        write(c -> { completePayment(c, requirePayment(c, id)); return null; });
    }

    /** Ends an unsuccessful attempt and releases its reservation without calling the economy. */
    public void cancelPayment(UUID id, String reason) {
        write(c -> { cancelPayment(c, requirePayment(c, id), reason); return null; });
    }

    public record MaintenanceResult(int completed, int cancelled, int failed) {}

    /**
     * Bounded automatic housekeeping. This never withdraws or refunds money.
     * Normal Vault calls are assumed to finish promptly: five-minute abandoned
     * reservations are released, while recorded successful payments are delivered.
     */
    public MaintenanceResult maintainPayments(int limit) {
        if (limit < 1 || limit > 100) throw error(INVALID_INPUT);
        Instant current = now();
        long expired = current.minusSeconds(PAYMENT_EXPIRY_SECONDS).toEpochMilli();
        long retryBefore = current.minusSeconds(DELIVERY_RETRY_SECONDS).toEpochMilli();
        List<UUID> candidates = read(c -> query(c,
                "SELECT id FROM dt_payment WHERE (state IN ('PREPARED','CHARGING') AND updated_at<=?) "
                        + "OR (state='CHARGED' AND (failure IS NULL OR failure<>? OR updated_at<=?)) "
                        + "ORDER BY updated_at,id LIMIT ?",
                r -> UUID.fromString(r.getString(1)), expired, DELIVERY_RETRY, retryBefore, limit));
        int completed = 0, cancelled = 0, failed = 0;
        for (UUID id : candidates) {
            try {
                int action = write(c -> maintainPayment(c, id, expired, retryBefore));
                if (action == 1) completed++;
                else if (action == 2) cancelled++;
            } catch (TitleException failure) {
                failed++;
                // A bad delivery must neither roll back other rows nor occupy the
                // front of every batch. Leave its paid status intact for a later try.
                try {
                    write(c -> {
                        execute(c, "UPDATE dt_payment SET updated_at=?,failure=? WHERE id=? AND state='CHARGED'",
                                now().toEpochMilli(), DELIVERY_RETRY, id.toString());
                        return null;
                    });
                } catch (TitleException unavailable) {
                    // The caller reports the failure count; the next bounded pass retries.
                }
            }
        }
        return new MaintenanceResult(completed, cancelled, failed);
    }

    private int maintainPayment(Connection c, UUID id, long expired, long retryBefore) throws SQLException {
        Optional<PaymentOperation> found = findPayment(c, id);
        if (found.isEmpty()) return 0;
        PaymentOperation operation = found.get();
        long updated = one(c, "SELECT updated_at FROM dt_payment WHERE id=?", r -> r.getLong(1), id.toString()).orElseThrow();
        if (operation.state() == PaymentState.CHARGED) {
            if (DELIVERY_RETRY.equals(operation.failure()) && updated > retryBefore) return 0;
            completePayment(c, operation);
            return 1;
        }
        if ((operation.state() == PaymentState.PREPARED || operation.state() == PaymentState.CHARGING) && updated <= expired) {
            cancelPayment(c, operation, "attempt-expired");
            return 2;
        }
        return 0;
    }

    public Optional<PaymentOperation> payment(UUID id) { return read(c -> findPayment(c, id)); }

    public List<PaymentOperation> unresolvedPayments() {
        return read(c -> query(c, "SELECT * FROM dt_payment WHERE state IN ('PREPARED','CHARGING','CHARGED') ORDER BY created_at", TitleStore::mapPayment));
    }

    public long unresolvedPaymentCount() {
        return read(c -> one(c, "SELECT COUNT(*) FROM dt_payment WHERE state IN ('PREPARED','CHARGING','CHARGED')", r -> r.getLong(1)).orElseThrow(() -> error(STORAGE_FAILURE)));
    }

    public List<PaymentOperation> unresolvedPayments(int limit, int offset) {
        if (limit < 1 || limit > 500 || offset < 0) throw error(INVALID_INPUT);
        return read(c -> query(c,
                "SELECT * FROM dt_payment WHERE state IN ('PREPARED','CHARGING','CHARGED') ORDER BY created_at,id LIMIT ? OFFSET ?",
                TitleStore::mapPayment, limit, offset));
    }

    /** Entire batch commits atomically. Repeated imports never restore spent balances or stock. */
    public ImportReport importData(ImportBatch batch) {
        Objects.requireNonNull(batch);
        checkedName(batch.sourceKey(), 128);
        Objects.requireNonNull(batch.currency());
        return write(c -> {
            int titles = 0, players = 0, ownerships = 0, listings = 0, skipped = 0;
            Set<UUID> initializeSelection = new HashSet<>();
            for (ImportTitle item : batch.titles()) {
                if (mapped(c, batch.sourceKey(), "title", item.legacyId()).isPresent()) { skipped++; continue; }
                TitleDraft draft = new TitleDraft(item.name(), item.miniMessage(), item.description(), item.enabled());
                validate(draft);
                Title created = insertTitle(c, null, false, draft);
                map(c, batch.sourceKey(), "title", item.legacyId(), Long.toString(created.id()));
                titles++;
            }
            for (ImportPlayer item : batch.players()) {
                if (mapped(c, batch.sourceKey(), "player", item.uuid().toString()).isPresent()) { skipped++; continue; }
                Optional<PlayerProfile> existing = findPlayer(c, item.uuid());
                if (existing.isEmpty()) {
                    // Imported players already have their source balance; never issue a signup grant.
                    insertPlayer(c, item.uuid(), checkedName(item.name(), 64), 0);
                    initializeSelection.add(item.uuid());
                } else if (existing.get().revision() == 0 && existing.get().equippedTitleId() == null) {
                    initializeSelection.add(item.uuid());
                }
                if (item.coins() != null) {
                    if (item.coins() < 0) throw error(INVALID_INPUT);
                    long balance;
                    // Revision zero means initialization/name updates only, with no business changes.
                    // Replace an unused signup balance if the first login preceded the migration.
                    long nativeBalance = existing.isPresent() && existing.get().revision() != 0
                            ? player(c, item.uuid()).coins() : 0;
                    try { balance = Math.addExact(nativeBalance, item.coins()); }
                    catch (ArithmeticException e) { throw error(INVALID_INPUT); }
                    execute(c, "UPDATE dt_player SET coins=?,revision=revision+1 WHERE uuid=?", balance, item.uuid().toString());
                }
                map(c, batch.sourceKey(), "player", item.uuid().toString(), item.uuid().toString());
                players++;
            }
            for (ImportOwnership item : batch.ownerships()) {
                if (mapped(c, batch.sourceKey(), "ownership", item.legacyId()).isPresent()) { skipped++; continue; }
                long titleId = importedTitle(c, batch.sourceKey(), item.legacyTitleId());
                player(c, item.playerId());
                grant(c, item.playerId(), titleId, item.expiresAt());
                map(c, batch.sourceKey(), "ownership", item.legacyId(), Long.toString(titleId));
                ownerships++;
            }
            for (ImportPlayer item : batch.players()) {
                if (!initializeSelection.contains(item.uuid()) || item.equippedLegacyTitleId() == null) continue;
                long titleId = importedTitle(c, batch.sourceKey(), item.equippedLegacyTitleId());
                Optional<OwnedTitle> owned = owned(c, item.uuid(), titleId);
                if (owned.isPresent() && !owned.get().expired(now()) && owned.get().title().enabled())
                    execute(c, "UPDATE dt_player SET equipped_title_id=?,revision=revision+1 WHERE uuid=?", titleId, item.uuid().toString());
            }
            for (ImportListing item : batch.listings()) {
                if (mapped(c, batch.sourceKey(), "listing", item.legacyId()).isPresent()) { skipped++; continue; }
                long titleId = importedTitle(c, batch.sourceKey(), item.legacyTitleId());
                ListingDraft draft = new ListingDraft(titleId, batch.currency(), item.price(), item.remainingStock(), null, item.endsAt(), item.grantDurationDays(), item.enabled());
                validate(draft);
                long listingId = insert(c, "INSERT INTO dt_listing(title_id,currency,price,total_stock,sold,starts_at,ends_at,grant_days,enabled,version) VALUES (?,?,?,?,0,NULL,?,?,?,0)",
                        titleId, batch.currency().name(), checkedPrice(item.price(), batch.currency()).toPlainString(), item.remainingStock(), epoch(item.endsAt()), item.grantDurationDays(), bit(item.enabled()));
                map(c, batch.sourceKey(), "listing", item.legacyId(), Long.toString(listingId));
                listings++;
            }
            return new ImportReport(titles, players, ownerships, listings, skipped);
        });
    }

    private void completePayment(Connection c, PaymentOperation operation) throws SQLException {
        if (operation.state() == PaymentState.COMPLETE) return;
        if (operation.state() != PaymentState.CHARGED) throw error(INVALID_PAYMENT_STATE);
        if (operation.kind() == PaymentKind.SHOP)
            grant(c, operation.playerId(), Objects.requireNonNull(operation.titleId()), expiry(operation.grantDurationDays()));
        else addCredits(c, operation.playerId(), operation.credits());
        paymentState(c, operation.id(), PaymentState.COMPLETE, null);
    }

    private void cancelPayment(Connection c, PaymentOperation operation, String reason) throws SQLException {
        if (operation.state() == PaymentState.CANCELLED) return;
        if (operation.state() != PaymentState.PREPARED && operation.state() != PaymentState.CHARGING)
            throw error(INVALID_PAYMENT_STATE);
        if (operation.listingId() != null)
            execute(c, "UPDATE dt_listing SET sold=sold-1 WHERE id=? AND sold>0", operation.listingId());
        paymentState(c, operation.id(), PaymentState.CANCELLED, reason == null ? null : reason.substring(0, Math.min(512, reason.length())));
    }

    private PaymentOperation insertPayment(Connection c, UUID uuid, PaymentKind kind, Long listing, Long title, int credits, BigDecimal amount, Integer days) throws SQLException {
        UUID id = UUID.randomUUID();
        long time = now().toEpochMilli();
        execute(c, "INSERT INTO dt_payment(id,player_uuid,kind,state,listing_id,title_id,credits,amount,grant_days,created_at,updated_at,failure) VALUES (?,?,?,?,?,?,?,?,?,?,?,NULL)",
                id.toString(), uuid.toString(), kind.name(), PaymentState.PREPARED.name(), listing, title, credits, amount.toPlainString(), days, time, time);
        return requirePayment(c, id);
    }

    private void paymentState(Connection c, UUID id, PaymentState state, String reason) throws SQLException {
        execute(c, "UPDATE dt_payment SET state=?,failure=?,updated_at=? WHERE id=?", state.name(), reason, now().toEpochMilli(), id.toString());
    }

    private ShopListing purchasable(Connection c, UUID uuid, long id, long version, Currency currency) throws SQLException {
        player(c, uuid);
        checkNoPending(c, uuid);
        ShopListing listing = requireListing(c, id);
        if (listing.version() != version) throw error(STALE_VERSION);
        if (listing.currency() != currency) throw error(INVALID_INPUT);
        if (!listing.enabled() || !listing.title().enabled()
                || listing.startsAt() != null && listing.startsAt().isAfter(now())
                || listing.endsAt() != null && !listing.endsAt().isAfter(now())) throw error(SHOP_CLOSED);
        if (listing.totalStock() != null && listing.sold() >= listing.totalStock()) throw error(OUT_OF_STOCK);
        if (owned(c, uuid, listing.title().id()).filter(o -> !o.expired(now())).isPresent()) throw error(ALREADY_OWNED);
        return listing;
    }

    private void reserve(Connection c, ShopListing listing) throws SQLException {
        int updated = execute(c, "UPDATE dt_listing SET sold=sold+1 WHERE id=? AND (total_stock IS NULL OR sold<total_stock)", listing.id());
        if (updated != 1) throw error(OUT_OF_STOCK);
    }

    private void checkNoPending(Connection c, UUID uuid) throws SQLException {
        if (one(c, "SELECT id FROM dt_payment WHERE player_uuid=? AND state IN ('PREPARED','CHARGING','CHARGED')", r -> r.getString(1), uuid.toString()).isPresent())
            throw error(PAYMENT_PENDING);
    }

    private void takeCoins(Connection c, UUID uuid, long amount) throws SQLException {
        if (amount < 0) throw error(INVALID_INPUT);
        if (execute(c, "UPDATE dt_player SET coins=coins-?,revision=revision+1 WHERE uuid=? AND coins>=?", amount, uuid.toString(), amount) != 1)
            throw error(INSUFFICIENT_COINS);
    }

    private void consumeCredit(Connection c, UUID uuid) throws SQLException {
        if (execute(c, "UPDATE dt_player SET custom_credits=custom_credits-1,revision=revision+1 WHERE uuid=? AND custom_credits>=1", uuid.toString()) != 1)
            throw error(INSUFFICIENT_CREDITS);
    }

    private void addCredits(Connection c, UUID uuid, int amount) throws SQLException {
        int updated;
        try { updated = Math.addExact(player(c, uuid).customCredits(), amount); }
        catch (ArithmeticException e) { throw error(INVALID_INPUT); }
        if (updated < 0) throw error(INSUFFICIENT_CREDITS);
        execute(c, "UPDATE dt_player SET custom_credits=?,revision=revision+1 WHERE uuid=?", updated, uuid.toString());
    }

    private Title insertTitle(Connection c, UUID creator, boolean custom, TitleDraft draft) throws SQLException {
        long id = insert(c, "INSERT INTO dt_title(name,mini_message,description,creator,custom_flag,enabled,version) VALUES (?,?,?,?,?,?,0)",
                draft.name().trim(), draft.miniMessage(), draft.description(), creator == null ? null : creator.toString(), bit(custom), bit(draft.enabled()));
        return requireTitle(c, id);
    }

    private Title updateTitle(Connection c, Title old, long version, TitleDraft draft) throws SQLException {
        if (old.version() != version) throw error(STALE_VERSION);
        execute(c, "UPDATE dt_title SET name=?,mini_message=?,description=?,enabled=?,version=version+1 WHERE id=? AND version=?",
                draft.name().trim(), draft.miniMessage(), draft.description(), bit(draft.enabled()), old.id(), version);
        return requireTitle(c, old.id());
    }

    private void grant(Connection c, UUID uuid, long titleId, Instant requestedExpiry) throws SQLException {
        Optional<OwnedTitle> old = owned(c, uuid, titleId);
        Instant result = requestedExpiry;
        if (old.isPresent()) {
            Instant previous = old.get().expiresAt();
            result = previous == null || requestedExpiry == null ? null : previous.isAfter(requestedExpiry) ? previous : requestedExpiry;
        }
        if (old.isEmpty()) execute(c, "INSERT INTO dt_ownership(player_uuid,title_id,expires_at,created_at) VALUES (?,?,?,?)",
                uuid.toString(), titleId, epoch(result), now().toEpochMilli());
        else execute(c, "UPDATE dt_ownership SET expires_at=? WHERE player_uuid=? AND title_id=?", epoch(result), uuid.toString(), titleId);
        execute(c, "UPDATE dt_player SET revision=revision+1 WHERE uuid=?", uuid.toString());
    }

    private Optional<OwnedTitle> owned(Connection c, UUID uuid, long titleId) throws SQLException {
        return one(c, "SELECT t.*,o.expires_at FROM dt_ownership o JOIN dt_title t ON t.id=o.title_id WHERE o.player_uuid=? AND o.title_id=?",
                r -> new OwnedTitle(mapTitle(r), instant(r, "expires_at")), uuid.toString(), titleId);
    }

    private Optional<PlayerProfile> findPlayer(Connection c, UUID uuid) throws SQLException {
        return one(c, "SELECT * FROM dt_player WHERE uuid=?", r -> new PlayerProfile(UUID.fromString(r.getString("uuid")), r.getString("name"), nullableLong(r, "equipped_title_id"), r.getLong("coins"), r.getInt("custom_credits"), r.getLong("revision")), uuid.toString());
    }

    private PlayerProfile player(Connection c, UUID uuid) throws SQLException { return findPlayer(c, uuid).orElseThrow(() -> error(NOT_FOUND)); }
    private void ensurePlayer(Connection c, UUID uuid) throws SQLException { if (findPlayer(c, uuid).isEmpty()) insertPlayer(c, uuid, uuid.toString()); }
    private void insertPlayer(Connection c, UUID uuid, String name) throws SQLException {
        insertPlayer(c, uuid, name, initialCoins);
    }

    private void insertPlayer(Connection c, UUID uuid, String name, long startingCoins) throws SQLException {
        execute(c, "INSERT INTO dt_player(uuid,name,equipped_title_id,coins,custom_credits,revision) VALUES (?,?,NULL,?,0,0)", uuid.toString(), name, startingCoins);
    }

    private Optional<Title> findTitle(Connection c, long id) throws SQLException { return one(c, "SELECT * FROM dt_title WHERE id=?", TitleStore::mapTitle, id); }
    private Title requireTitle(Connection c, long id) throws SQLException { return findTitle(c, id).orElseThrow(() -> error(NOT_FOUND)); }
    private Optional<ShopListing> findListing(Connection c, long id) throws SQLException { return one(c, listingSelect() + " WHERE l.id=?", TitleStore::mapListing, id); }
    private ShopListing requireListing(Connection c, long id) throws SQLException { return findListing(c, id).orElseThrow(() -> error(NOT_FOUND)); }
    private Optional<PaymentOperation> findPayment(Connection c, UUID id) throws SQLException { return one(c, "SELECT * FROM dt_payment WHERE id=?", TitleStore::mapPayment, id.toString()); }
    private PaymentOperation requirePayment(Connection c, UUID id) throws SQLException { return findPayment(c, id).orElseThrow(() -> error(NOT_FOUND)); }

    private static String listingSelect() { return "SELECT t.*,l.id AS listing_id,l.currency,l.price,l.total_stock,l.sold,l.starts_at,l.ends_at,l.grant_days,l.enabled AS listing_enabled,l.version AS listing_version FROM dt_listing l JOIN dt_title t ON t.id=l.title_id"; }

    private static Title mapTitle(ResultSet r) throws SQLException {
        String creator = r.getString("creator");
        return new Title(r.getLong("id"), r.getString("name"), r.getString("mini_message"), r.getString("description"), creator == null ? null : UUID.fromString(creator), r.getInt("custom_flag") != 0, r.getInt("enabled") != 0, r.getLong("version"));
    }

    private static ShopListing mapListing(ResultSet r) throws SQLException {
        return new ShopListing(r.getLong("listing_id"), mapTitle(r), Currency.valueOf(r.getString("currency")), new BigDecimal(r.getString("price")), nullableLong(r, "total_stock"), r.getLong("sold"), instant(r, "starts_at"), instant(r, "ends_at"), nullableInt(r, "grant_days"), r.getInt("listing_enabled") != 0, r.getLong("listing_version"));
    }

    private static PaymentOperation mapPayment(ResultSet r) throws SQLException {
        return new PaymentOperation(UUID.fromString(r.getString("id")), UUID.fromString(r.getString("player_uuid")), PaymentKind.valueOf(r.getString("kind")), PaymentState.valueOf(r.getString("state")), nullableLong(r, "listing_id"), nullableLong(r, "title_id"), r.getInt("credits"), new BigDecimal(r.getString("amount")), nullableInt(r, "grant_days"), Instant.ofEpochMilli(r.getLong("created_at")), r.getString("failure"));
    }

    private Optional<String> mapped(Connection c, String sourceKey, String kind, String id) throws SQLException {
        return one(c, "SELECT target_id FROM dt_import_map WHERE source_key=? AND kind=? AND legacy_id=?", r -> r.getString(1), sourceKey, kind, id);
    }

    private void map(Connection c, String sourceKey, String kind, String legacyId, String target) throws SQLException {
        checkedName(legacyId, 128);
        execute(c, "INSERT INTO dt_import_map(source_key,kind,legacy_id,target_id) VALUES (?,?,?,?)", sourceKey, kind, legacyId, target);
    }

    private long importedTitle(Connection c, String sourceKey, String legacyId) throws SQLException {
        return Long.parseLong(mapped(c, sourceKey, "title", legacyId).orElseThrow(() -> error(IMPORT_CONFLICT)));
    }

    private static void validate(TitleDraft draft) {
        Objects.requireNonNull(draft);
        checkedName(draft.name(), 128);
        if (draft.miniMessage() == null || draft.miniMessage().isBlank() || draft.miniMessage().length() > 8192
                || draft.description() == null || draft.description().length() > 8192) throw error(INVALID_INPUT);
    }

    private static void validate(ListingDraft draft) {
        Objects.requireNonNull(draft);
        checkedPrice(draft.price(), draft.currency());
        if (draft.totalStock() != null && draft.totalStock() < 0 || draft.grantDurationDays() != null && (draft.grantDurationDays() <= 0 || draft.grantDurationDays() > 365000)
                || draft.startsAt() != null && draft.endsAt() != null && !draft.endsAt().isAfter(draft.startsAt())) throw error(INVALID_INPUT);
    }

    private static BigDecimal checkedPrice(BigDecimal price, Currency currency) {
        if (price == null || currency == null || price.signum() < 0 || price.compareTo(new BigDecimal("1000000000000")) > 0) throw error(INVALID_INPUT);
        try {
            BigDecimal normalized = price.setScale(2, RoundingMode.UNNECESSARY);
            if (currency == Currency.COINS) normalized.longValueExact();
            return normalized;
        } catch (ArithmeticException e) { throw error(INVALID_INPUT); }
    }

    private static String checkedName(String name, int limit) {
        if (name == null || name.isBlank() || name.trim().length() > limit) throw error(INVALID_INPUT);
        return name.trim();
    }

    private Instant expiry(Integer days) { return days == null ? null : now().plus(days, ChronoUnit.DAYS); }
    private Instant now() { return clock.instant(); }
    private static Long epoch(Instant value) { return value == null ? null : value.toEpochMilli(); }
    private static int bit(boolean value) { return value ? 1 : 0; }
    private static Long nullableLong(ResultSet r, String name) throws SQLException { long value = r.getLong(name); return r.wasNull() ? null : value; }
    private static Integer nullableInt(ResultSet r, String name) throws SQLException { int value = r.getInt(name); return r.wasNull() ? null : value; }
    private static Instant instant(ResultSet r, String name) throws SQLException { Long value = nullableLong(r, name); return value == null ? null : Instant.ofEpochMilli(value); }
    private static TitleException error(ErrorCode code) { return new TitleException(code); }

    private Connection connection() throws SQLException {
        Connection c = source.getConnection();
        try {
            if (dialect == SqlDialect.SQLITE) {
                pragma(c, "PRAGMA foreign_keys=ON");
                pragma(c, "PRAGMA busy_timeout=10000");
            }
            return c;
        } catch (Throwable e) {
            c.close();
            throw e;
        }
    }

    private <T> T read(SqlWork<T> work) {
        try (Connection c = connection()) { return work.run(c); }
        catch (SQLException e) { throw new TitleException(STORAGE_FAILURE, e); }
    }

    private <T> T write(SqlWork<T> work) {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                execute(c, "UPDATE dt_mutex SET id=id WHERE id=1");
                T result = work.run(c);
                c.commit();
                return result;
            } catch (Throwable failure) {
                try { c.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        } catch (SQLException e) { throw new TitleException(STORAGE_FAILURE, e); }
    }

    private static int execute(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            bind(statement, values);
            statement.setQueryTimeout(15);
            return statement.executeUpdate();
        }
    }

    private static void pragma(Connection c, String sql) throws SQLException {
        try (Statement statement = c.createStatement()) { statement.execute(sql); }
    }

    private static long insert(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, values);
            statement.setQueryTimeout(15);
            if (statement.executeUpdate() != 1) throw new SQLException("Insert did not create exactly one row");
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Insert did not return an identifier");
                return keys.getLong(1);
            }
        }
    }

    private static <T> List<T> query(Connection c, String sql, Row<T> mapper, Object... values) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            bind(statement, values);
            statement.setQueryTimeout(15);
            try (ResultSet result = statement.executeQuery()) {
                List<T> valuesOut = new ArrayList<>();
                while (result.next()) valuesOut.add(mapper.map(result));
                return List.copyOf(valuesOut);
            }
        }
    }

    private static <T> Optional<T> one(Connection c, String sql, Row<T> mapper, Object... values) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            bind(statement, values);
            statement.setMaxRows(1);
            statement.setQueryTimeout(15);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? Optional.of(mapper.map(result)) : Optional.empty(); }
        }
    }

    private static void bind(PreparedStatement statement, Object[] values) throws SQLException {
        for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
    }

    @Override public void close() {
        if (source instanceof AutoCloseable closeable) {
            try { closeable.close(); }
            catch (Exception e) { throw new TitleException(STORAGE_FAILURE, e); }
        }
    }

    @FunctionalInterface private interface SqlWork<T> { T run(Connection connection) throws SQLException; }
    @FunctionalInterface private interface Row<T> { T map(ResultSet result) throws SQLException; }
}
