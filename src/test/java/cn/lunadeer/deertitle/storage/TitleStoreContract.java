package cn.lunadeer.deertitle.storage;

import cn.lunadeer.deertitle.domain.*;
import cn.lunadeer.deertitle.domain.Currency;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static cn.lunadeer.deertitle.domain.ErrorCode.*;
import static org.junit.jupiter.api.Assertions.*;

/** Same transactional contract runs against SQLite and opt-in isolated SQL servers. */
abstract class TitleStoreContract {
    protected HikariDataSource pool;
    protected TitleStore store;
    protected SqlDialect dialect;
    protected MutableClock clock;
    protected UUID player;

    protected abstract HikariDataSource dataSource() throws Exception;
    protected abstract SqlDialect dialect();

    @BeforeEach void setup() throws Exception {
        dialect = dialect();
        pool = dataSource();
        try (Connection c = pool.getConnection(); Statement s = c.createStatement()) {
            for (String name : List.of("dt_import_map", "dt_payment", "dt_listing", "dt_ownership", "dt_player", "dt_title", "dt_mutex", "dt_schema"))
                s.execute("DROP TABLE IF EXISTS " + name);
        }
        clock = new MutableClock(Instant.parse("2026-10-28T00:00:00Z"));
        store = new TitleStore(pool, dialect, clock);
        store.initialize();
        store.initialize(); // Startup migration is idempotent.
        player = UUID.randomUUID();
        store.ensurePlayer(player, "Alice");
    }

    @AfterEach void teardown() {
        if (store != null) store.close();
        else if (pool != null) pool.close();
    }

    protected Title title() { return store.createAdminTitle(player, new TitleDraft("晨光", "<gradient:#ffaa00:#ffffff>晨光</gradient>", "", true)); }

    protected ShopListing listing(Title title, Currency currency, long price, Long stock, Integer days) {
        return store.saveListing(null, 0, new ListingDraft(title.id(), currency, BigDecimal.valueOf(price), stock, null, null, days, true));
    }

    protected void fails(ErrorCode code, Runnable action) {
        assertEquals(code, assertThrows(TitleException.class, action::run).code());
    }

    @Test void firstSchemaInitializationIsSafeAcrossIndependentConnectionPools() throws Exception {
        try (Connection c = pool.getConnection(); Statement statement = c.createStatement()) {
            for (String name : List.of("dt_import_map", "dt_payment", "dt_listing", "dt_ownership",
                    "dt_player", "dt_title", "dt_mutex", "dt_schema")) {
                statement.execute("DROP TABLE IF EXISTS " + name);
            }
        }
        try (HikariDataSource otherPool = dataSource();
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            TitleStore otherServer = new TitleStore(otherPool, dialect, clock);
            CountDownLatch start = new CountDownLatch(1);
            Future<?> first = executor.submit(() -> {
                start.await();
                store.initialize();
                return null;
            });
            Future<?> second = executor.submit(() -> {
                start.await();
                otherServer.initialize();
                return null;
            });
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
            store.ensurePlayer(player, "Alice");
            assertEquals("Alice", otherServer.profile(player).name());
            try (Connection c = pool.getConnection(); Statement statement = c.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM dt_schema")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
    }

    @Test void coinsInventoryAndStockCommitTogether() {
        Title title = title();
        ShopListing listing = listing(title, Currency.COINS, 100, 2L, null);
        store.adjustCoins(player, 99);
        fails(INSUFFICIENT_COINS, () -> store.purchaseCoins(player, listing.id(), listing.version()));
        assertEquals(99, store.profile(player).coins());
        assertEquals(0, store.listing(listing.id()).orElseThrow().sold());
        assertTrue(store.inventory(player).isEmpty());
        store.adjustCoins(player, 1);
        store.purchaseCoins(player, listing.id(), listing.version());
        assertEquals(0, store.profile(player).coins());
        assertEquals(1, store.listing(listing.id()).orElseThrow().sold());
        assertEquals(title.id(), store.inventory(player).getFirst().title().id());
        fails(ALREADY_OWNED, () -> store.purchaseCoins(player, listing.id(), listing.version()));
        store.equip(player, title.id());
        assertEquals(title.id(), store.equippedTitle(player).orElseThrow().id());
        store.unequip(player);
        assertTrue(store.equippedTitle(player).isEmpty());
    }

    @Test void initialCoinsAreAtomicAcrossServersAndNeverReissued() throws Exception {
        UUID newcomer = UUID.randomUUID();
        TitleStore first = new TitleStore(pool, dialect, clock, 100);
        try (HikariDataSource secondPool = dataSource(); ExecutorService executor = Executors.newFixedThreadPool(8)) {
            TitleStore second = new TitleStore(secondPool, dialect, clock, 100);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> joins = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                TitleStore server = i % 2 == 0 ? first : second;
                joins.add(executor.submit(() -> { start.await(); server.ensurePlayer(newcomer, "NewPlayer"); return null; }));
            }
            start.countDown();
            for (Future<?> join : joins) join.get(30, TimeUnit.SECONDS);
            assertEquals(100, first.profile(newcomer).coins());
            assertEquals(100, second.ensurePlayer(newcomer, "Renamed").coins());
        }
        TitleStore changedConfig = new TitleStore(pool, dialect, clock, 200);
        assertEquals(100, changedConfig.ensurePlayer(newcomer, "Renamed").coins());
        assertEquals(0, changedConfig.ensurePlayer(player, "Alice").coins()); // Existing 1.0.1 account.
        UUID offline = UUID.randomUUID();
        changedConfig.adjustCoins(offline, 5); // Offline administration also creates an account only once.
        assertEquals(205, changedConfig.ensurePlayer(offline, "OfflinePlayer").coins());
        changedConfig.adjustCredits(offline, 1);
        assertEquals(205, changedConfig.profile(offline).coins());
        assertEquals(200, changedConfig.ensurePlayer(UUID.randomUUID(), "OtherNewPlayer").coins());
    }

    @Test void migrationNeverAddsSignupCoinsAndPreservesUsedNativeBalances() {
        TitleStore gifts = new TitleStore(pool, dialect, clock, 100);
        UUID fresh = UUID.randomUUID(), empty = UUID.randomUUID(), joined = UUID.randomUUID(), used = UUID.randomUUID();
        gifts.ensurePlayer(joined, "Joined");
        gifts.ensurePlayer(used, "Used");
        gifts.adjustCoins(used, -30);
        TitleStore changedConfig = new TitleStore(pool, dialect, clock, 200);
        var batch = new ImportBatch("initial-coins-source", Currency.COINS, List.of(),
                List.of(new ImportPlayer(fresh, "Fresh", 37L, null), new ImportPlayer(empty, "Empty", 0L, null),
                        new ImportPlayer(joined, "Joined", 37L, null), new ImportPlayer(used, "Used", 37L, null)),
                List.of(), List.of());
        changedConfig.importData(batch);
        assertEquals(37, changedConfig.ensurePlayer(fresh, "Fresh").coins());
        assertEquals(0, changedConfig.ensurePlayer(empty, "Empty").coins());
        assertEquals(37, changedConfig.profile(joined).coins()); // Replace unused initialization, even after config change.
        assertEquals(107, changedConfig.profile(used).coins()); // Keep genuinely changed native balance + source balance.
        assertEquals(new ImportReport(0, 0, 0, 0, 4), changedConfig.importData(batch));
        assertEquals(37, changedConfig.profile(joined).coins());
    }

    @Test void remainingStockEditRejectsSalesSinceTheAdminOpenedTheForm() {
        ShopListing original = listing(title(), Currency.COINS, 10, 10L, null);
        store.adjustCoins(player, 10);
        store.purchaseCoins(player, original.id(), original.version());
        ListingDraft edit = new ListingDraft(original.title().id(), Currency.COINS, BigDecimal.TEN,
                5L, null, null, null, true);
        fails(STALE_VERSION, () -> store.saveListing(original.id(), original.version(), original.sold(), edit));
        assertEquals(9L, store.listing(original.id()).orElseThrow().remainingStock());
    }

    @Test void stockIsGlobalAcrossIndependentStoreInstances() throws Exception {
        ShopListing listing = listing(title(), Currency.COINS, 10, 1L, null);
        List<UUID> buyers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            UUID uuid = UUID.randomUUID();
            buyers.add(uuid);
            store.ensurePlayer(uuid, "Buyer" + i);
            store.adjustCoins(uuid, 10);
        }
        TitleStore otherServer = new TitleStore(pool, dialect, clock);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(12)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < buyers.size(); i++) {
                UUID uuid = buyers.get(i);
                TitleStore selected = i % 2 == 0 ? store : otherServer;
                results.add(executor.submit(() -> {
                    start.await();
                    try { selected.purchaseCoins(uuid, listing.id(), listing.version()); return true; }
                    catch (TitleException e) { assertEquals(OUT_OF_STOCK, e.code()); return false; }
                }));
            }
            start.countDown();
            int success = 0;
            for (Future<Boolean> result : results) if (result.get(30, TimeUnit.SECONDS)) success++;
            assertEquals(1, success);
        }
        assertEquals(1, store.listing(listing.id()).orElseThrow().sold());
        assertEquals(110, buyers.stream().mapToLong(uuid -> store.profile(uuid).coins()).sum());
        assertEquals(1, buyers.stream().mapToLong(uuid -> store.inventory(uuid).size()).sum());
    }

    @Test void versionTimeAndOwnershipExpiryAreEnforced() {
        Title title = title();
        ShopListing listing = listing(title, Currency.COINS, 0, null, 2);
        ListingDraft future = new ListingDraft(title.id(), Currency.COINS, BigDecimal.ZERO, null,
                clock.instant().plusSeconds(60), clock.instant().plusSeconds(120), 2, true);
        ShopListing changed = store.saveListing(listing.id(), listing.version(), future);
        fails(STALE_VERSION, () -> store.purchaseCoins(player, listing.id(), listing.version()));
        fails(SHOP_CLOSED, () -> store.purchaseCoins(player, changed.id(), changed.version()));
        clock.advance(Duration.ofSeconds(60));
        store.purchaseCoins(player, changed.id(), changed.version());
        Instant expiry = clock.instant().plus(Duration.ofDays(2));
        store.equip(player, title.id());
        assertEquals(expiry, store.equippedOwnership(player).orElseThrow().expiresAt());
        clock.advance(Duration.ofDays(2));
        assertTrue(store.equippedOwnership(player).isEmpty());
        fails(EXPIRED, () -> store.equip(player, title.id()));
    }

    @Test void customEditsConsumeCreditsAndRejectForeignAndStaleRequests() {
        TitleDraft draft = new TitleDraft("自定义", "<red>自定义</red>", "", true);
        fails(INSUFFICIENT_CREDITS, () -> store.createCustomTitle(player, draft));
        store.adjustCoins(player, 20);
        store.buyCreditsCoins(player, 2, 20);
        assertEquals(0, store.profile(player).coins());
        Title custom = store.createCustomTitle(player, draft);
        assertEquals(1, store.profile(player).customCredits());
        UUID stranger = UUID.randomUUID();
        store.ensurePlayer(stranger, "Bob");
        store.adjustCredits(stranger, 2);
        fails(NOT_OWNED, () -> store.updateCustomTitle(stranger, custom.id(), custom.version(), draft));
        assertEquals(2, store.profile(stranger).customCredits());
        Title updated = store.updateCustomTitle(player, custom.id(), custom.version(), draft);
        assertEquals(0, store.profile(player).customCredits());
        store.adjustCredits(player, 1);
        fails(STALE_VERSION, () -> store.updateCustomTitle(player, custom.id(), custom.version(), draft));
        assertEquals(1, store.profile(player).customCredits());
        assertEquals(custom.version() + 1, updated.version());
        fails(INVALID_INPUT, () -> listing(custom, Currency.COINS, 1, null, null));
    }

    @Test void externalPaymentsKeepLiveReservationsAndAutomaticallyFinishRecordedSuccess() {
        ShopListing listing = listing(title(), Currency.VAULT, 5, 1L, 30);
        PaymentOperation prepared = store.prepareShopPayment(player, listing.id(), listing.version());
        assertEquals(1, store.listing(listing.id()).orElseThrow().sold());
        assertTrue(store.inventory(player).isEmpty());
        fails(PAYMENT_PENDING, () -> store.prepareCreditPayment(player, 1, BigDecimal.ONE));
        PaymentOperation charging = store.markCharging(prepared.id());
        assertEquals(PaymentState.CHARGING, charging.state());
        fails(INVALID_PAYMENT_STATE, () -> store.markCharging(prepared.id()));
        TitleStore restarted = new TitleStore(pool, dialect, clock);
        assertEquals(PaymentState.CHARGING, restarted.unresolvedPayments().getFirst().state());
        // A fresh operation on another server is still running; housekeeping leaves it alone.
        assertEquals(new TitleStore.MaintenanceResult(0, 0, 0), restarted.maintainPayments(100));
        fails(INVALID_PAYMENT_STATE, () -> restarted.completePayment(prepared.id()));
        restarted.markCharged(prepared.id());
        fails(INVALID_PAYMENT_STATE, () -> store.cancelPayment(prepared.id(), "must not cancel money already withdrawn"));
        assertEquals(new TitleStore.MaintenanceResult(1, 0, 0), restarted.maintainPayments(100));
        restarted.completePayment(prepared.id());
        assertEquals(1, store.inventory(player).size());
        assertEquals(PaymentState.COMPLETE, store.payment(prepared.id()).orElseThrow().state());
        assertTrue(store.unresolvedPayments().isEmpty());
        PaymentOperation credits = store.prepareCreditPayment(player, 3, BigDecimal.ONE);
        store.markCharging(credits.id());
        store.markCharged(credits.id());
        assertEquals(new TitleStore.MaintenanceResult(1, 0, 0), restarted.maintainPayments(100));
        assertEquals(new TitleStore.MaintenanceResult(0, 0, 0), restarted.maintainPayments(100));
        assertEquals(3, store.profile(player).customCredits());
    }

    @Test void expirationCannotCancelAnAttemptWhoseChargingTransitionWasJustRefreshed() throws Exception {
        PaymentOperation operation = store.prepareCreditPayment(player, 1, BigDecimal.ONE);
        clock.advance(Duration.ofMinutes(5));
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> charging = executor.submit(() -> {
                start.await();
                try { store.markCharging(operation.id()); return true; }
                catch (TitleException e) { assertEquals(INVALID_PAYMENT_STATE, e.code()); return false; }
            });
            Future<Boolean> cancel = executor.submit(() -> {
                start.await();
                return store.maintainPayments(100).cancelled() == 1;
            });
            start.countDown();
            boolean mayWithdraw = charging.get(30, TimeUnit.SECONDS);
            boolean cancelled = cancel.get(30, TimeUnit.SECONDS);
            assertNotEquals(mayWithdraw, cancelled);
            PaymentState state = store.payment(operation.id()).orElseThrow().state();
            assertEquals(mayWithdraw ? PaymentState.CHARGING : PaymentState.CANCELLED, state);
        }
    }

    @Test void abandonedReservationsExpireAtFiveMinutesAndReleaseStockOnce() {
        ShopListing listing = listing(title(), Currency.VAULT, 5, 1L, null);
        PaymentOperation prepared = store.prepareShopPayment(player, listing.id(), listing.version());
        clock.advance(Duration.ofSeconds(299));
        TitleStore restarted = new TitleStore(pool, dialect, clock);
        assertEquals(new TitleStore.MaintenanceResult(0, 0, 0), restarted.maintainPayments(100));
        fails(PAYMENT_PENDING, () -> restarted.prepareCreditPayment(player, 1, BigDecimal.ONE));
        clock.advance(Duration.ofSeconds(1));
        assertEquals(new TitleStore.MaintenanceResult(0, 1, 0), restarted.maintainPayments(100));
        assertEquals(PaymentState.CANCELLED, store.payment(prepared.id()).orElseThrow().state());
        assertEquals(0, store.listing(listing.id()).orElseThrow().sold());
        assertEquals(new TitleStore.MaintenanceResult(0, 0, 0), restarted.maintainPayments(100));
        fails(INVALID_PAYMENT_STATE, () -> store.markCharging(prepared.id()));

        PaymentOperation charging = restarted.prepareShopPayment(player, listing.id(), listing.version());
        restarted.markCharging(charging.id());
        clock.advance(Duration.ofMinutes(5));
        assertEquals(new TitleStore.MaintenanceResult(0, 1, 0), store.maintainPayments(100));
        assertEquals(0, store.listing(listing.id()).orElseThrow().sold());
        assertTrue(store.inventory(player).isEmpty());
        // A late provider result after the accepted timeout cannot resurrect the old attempt.
        fails(INVALID_PAYMENT_STATE, () -> store.markCharged(charging.id()));
    }

    @Test void paymentExpirationUsesLastTransitionRatherThanCreationTime() {
        PaymentOperation payment = store.prepareCreditPayment(player, 1, BigDecimal.ONE);
        clock.advance(Duration.ofSeconds(290));
        store.markCharging(payment.id());
        clock.advance(Duration.ofSeconds(299));
        assertEquals(new TitleStore.MaintenanceResult(0, 0, 0), store.maintainPayments(100));
        assertEquals(PaymentState.CHARGING, store.payment(payment.id()).orElseThrow().state());
        clock.advance(Duration.ofSeconds(1));
        assertEquals(new TitleStore.MaintenanceResult(0, 1, 0), store.maintainPayments(100));
        assertDoesNotThrow(() -> store.prepareCreditPayment(player, 1, BigDecimal.ONE));
    }

    @Test void maintenanceAcrossServersDeliversAndReleasesExactlyOnce() throws Exception {
        ShopListing listing = listing(title(), Currency.VAULT, 5, 1L, null);
        PaymentOperation abandoned = store.prepareShopPayment(player, listing.id(), listing.version());
        UUID paidPlayer = UUID.randomUUID();
        store.ensurePlayer(paidPlayer, "Paid");
        PaymentOperation paid = store.prepareCreditPayment(paidPlayer, 3, BigDecimal.ONE);
        store.markCharging(paid.id());
        store.markCharged(paid.id());
        clock.advance(Duration.ofMinutes(5));
        TitleStore other = new TitleStore(pool, dialect, clock);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<TitleStore.MaintenanceResult> first = executor.submit(() -> { start.await(); return store.maintainPayments(100); });
            Future<TitleStore.MaintenanceResult> second = executor.submit(() -> { start.await(); return other.maintainPayments(100); });
            start.countDown();
            var a = first.get(30, TimeUnit.SECONDS);
            var b = second.get(30, TimeUnit.SECONDS);
            assertEquals(1, a.completed() + b.completed());
            assertEquals(1, a.cancelled() + b.cancelled());
            assertEquals(0, a.failed() + b.failed());
        }
        assertEquals(PaymentState.CANCELLED, store.payment(abandoned.id()).orElseThrow().state());
        assertEquals(PaymentState.COMPLETE, store.payment(paid.id()).orElseThrow().state());
        assertEquals(3, store.profile(paidPlayer).customCredits());
        assertEquals(0, store.listing(listing.id()).orElseThrow().sold());
        assertEquals(new TitleStore.MaintenanceResult(0, 0, 0), other.maintainPayments(100));
    }

    @Test void failedDeliveryDoesNotRollBackOtherRowsOrStarveFollowingWork() {
        PaymentOperation invalid = store.prepareCreditPayment(player, 1, BigDecimal.ONE);
        store.markCharging(invalid.id());
        store.markCharged(invalid.id());
        store.adjustCredits(player, Integer.MAX_VALUE);

        clock.advance(Duration.ofSeconds(1));
        UUID waitingPlayer = UUID.randomUUID();
        store.ensurePlayer(waitingPlayer, "Waiting");
        PaymentOperation abandoned = store.prepareCreditPayment(waitingPlayer, 1, BigDecimal.ONE);
        clock.advance(Duration.ofSeconds(1));
        UUID paidPlayer = UUID.randomUUID();
        store.ensurePlayer(paidPlayer, "Paid");
        PaymentOperation paid = store.prepareCreditPayment(paidPlayer, 2, BigDecimal.ONE);
        store.markCharging(paid.id());
        store.markCharged(paid.id());
        clock.advance(Duration.ofMinutes(5));

        assertEquals(new TitleStore.MaintenanceResult(1, 1, 1), store.maintainPayments(100));
        assertEquals(PaymentState.CHARGED, store.payment(invalid.id()).orElseThrow().state());
        assertEquals(PaymentState.CANCELLED, store.payment(abandoned.id()).orElseThrow().state());
        assertEquals(PaymentState.COMPLETE, store.payment(paid.id()).orElseThrow().state());
        assertEquals(2, store.profile(paidPlayer).customCredits());
        assertEquals(new TitleStore.MaintenanceResult(0, 0, 0), store.maintainPayments(100));
        store.adjustCredits(player, -1);
        clock.advance(Duration.ofSeconds(29));
        assertEquals(new TitleStore.MaintenanceResult(0, 0, 0), store.maintainPayments(100));
        clock.advance(Duration.ofSeconds(1));
        assertEquals(new TitleStore.MaintenanceResult(1, 0, 0), store.maintainPayments(100));
        assertEquals(Integer.MAX_VALUE, store.profile(player).customCredits());
    }

    @Test void maintenanceProcessesOnlyItsBoundedBatch() {
        for (int i = 0; i < 3; i++) {
            UUID owner = UUID.randomUUID();
            store.ensurePlayer(owner, "Pending" + i);
            store.prepareCreditPayment(owner, 1, BigDecimal.ONE);
        }
        clock.advance(Duration.ofMinutes(5));
        assertEquals(new TitleStore.MaintenanceResult(0, 1, 0), store.maintainPayments(1));
        assertEquals(2, store.unresolvedPaymentCount());
        assertEquals(new TitleStore.MaintenanceResult(0, 1, 0), store.maintainPayments(1));
        assertEquals(1, store.unresolvedPaymentCount());
        assertEquals(new TitleStore.MaintenanceResult(0, 1, 0), store.maintainPayments(1));
        fails(INVALID_INPUT, () -> store.maintainPayments(0));
        fails(INVALID_INPUT, () -> store.maintainPayments(101));
    }

    @Test void definiteFailedPaymentReleasesOnlyItsReservation() {
        ShopListing listing = listing(title(), Currency.VAULT, 5, 1L, null);
        PaymentOperation payment = store.prepareShopPayment(player, listing.id(), listing.version());
        store.markCharging(payment.id());
        store.cancelPayment(payment.id(), "provider-declined");
        store.cancelPayment(payment.id(), "provider-declined");
        assertEquals(0, store.listing(listing.id()).orElseThrow().sold());
        assertEquals(PaymentState.CANCELLED, store.payment(payment.id()).orElseThrow().state());
        assertTrue(store.inventory(player).isEmpty());
        assertNotEquals(payment.id(), store.prepareShopPayment(player, listing.id(), listing.version()).id());
    }

    @Test void unresolvedPaymentsCanBeCountedAndReadInBoundedPages() {
        List<UUID> operations = new ArrayList<>();
        assertEquals(0, store.unresolvedPaymentCount());
        for (int i = 0; i < 4; i++) {
            UUID owner = UUID.randomUUID();
            store.ensurePlayer(owner, "Pending" + i);
            operations.add(store.prepareCreditPayment(owner, 1, BigDecimal.ONE).id());
            clock.advance(Duration.ofSeconds(1));
        }
        assertEquals(4, store.unresolvedPaymentCount());
        assertEquals(operations.subList(0, 2), store.unresolvedPayments(2, 0).stream().map(PaymentOperation::id).toList());
        assertEquals(operations.subList(2, 4), store.unresolvedPayments(2, 2).stream().map(PaymentOperation::id).toList());
        assertTrue(store.unresolvedPayments(2, 4).isEmpty());
        store.cancelPayment(operations.getFirst(), "test-declined-before-withdrawal");
        assertEquals(3, store.unresolvedPaymentCount());
        fails(INVALID_INPUT, () -> store.unresolvedPayments(501, 0));
        fails(INVALID_INPUT, () -> store.unresolvedPayments(50, -1));
    }

    @Test void importIsAtomicIdempotentAndDoesNotResetSpentBalancesOrStock() {
        UUID migrated = UUID.randomUUID();
        ImportBatch batch = new ImportBatch("legacy-a", Currency.COINS,
                List.of(new ImportTitle("5", "旧称号", "<green>旧称号</green>", "描述", true),
                        new ImportTitle("6", "旧商店", "<gold>旧商店</gold>", "", true)),
                List.of(new ImportPlayer(migrated, "Migrated", 100L, "5")),
                List.of(new ImportOwnership("88", migrated, "5", null)),
                List.of(new ImportListing("9", "6", BigDecimal.TEN, 30, 2L, null, true)));
        ImportReport first = store.importData(batch);
        assertEquals(new ImportReport(2, 1, 1, 1, 0), first);
        long importedTitle = store.inventory(migrated).getFirst().title().id();
        assertFalse(store.inventory(migrated).getFirst().title().custom());
        assertEquals(importedTitle, store.profile(migrated).equippedTitleId());
        store.adjustCoins(migrated, -50);
        store.unequip(migrated);
        ShopListing listing = store.shop(true).getFirst();
        store.purchaseCoins(migrated, listing.id(), listing.version());
        assertEquals(new ImportReport(0, 0, 0, 0, 5), store.importData(batch));
        assertEquals(40, store.profile(migrated).coins());
        assertNull(store.profile(migrated).equippedTitleId());
        assertEquals(1, store.listing(listing.id()).orElseThrow().sold());
        assertEquals(2, store.listTitles().size());

        ImportBatch invalid = new ImportBatch("invalid-source", Currency.COINS,
                List.of(new ImportTitle("new", "MustRollback", "MustRollback", "", true)), List.of(),
                List.of(new ImportOwnership("bad", migrated, "missing", null)), List.of());
        fails(IMPORT_CONFLICT, () -> store.importData(invalid));
        assertEquals(2, store.listTitles().size());
    }

    @Test void existingNativeSelectionAndImportedExpiryArePreserved() {
        Title nativeTitle = title();
        store.grantTitle(player, nativeTitle.id(), null);
        store.equip(player, nativeTitle.id());
        Instant until = clock.instant().plusSeconds(30);
        ImportBatch batch = new ImportBatch("source-two", Currency.COINS,
                List.of(new ImportTitle("1", "旧", "旧", "", true)),
                List.of(new ImportPlayer(player, "OldName", null, "1")),
                List.of(new ImportOwnership("1", player, "1", until)), List.of());
        store.importData(batch);
        assertEquals(nativeTitle.id(), store.profile(player).equippedTitleId());
        assertEquals("Alice", store.profile(player).name());
        assertEquals(until, store.inventory(player).stream().filter(o -> o.title().id() != nativeTitle.id()).findFirst().orElseThrow().expiresAt());
    }

    @Test void firstLoginBeforeImportDoesNotDiscardLegacyEquippedTitle() {
        assertEquals(0, store.profile(player).revision());
        store.importData(new ImportBatch("fresh-login-source", Currency.COINS,
                List.of(new ImportTitle("1", "旧", "旧", "", true)),
                List.of(new ImportPlayer(player, "Alice", 10L, "1")),
                List.of(new ImportOwnership("1", player, "1", null)), List.of()));
        assertEquals("旧", store.equippedTitle(player).orElseThrow().name());
        store.unequip(player);
        assertTrue(store.equippedTitle(player).isEmpty());
    }

    @Test void validationPreventsOverflowAndFractionalCoins() {
        store.adjustCoins(player, Long.MAX_VALUE);
        fails(INVALID_INPUT, () -> store.adjustCoins(player, 1));
        assertEquals(Long.MAX_VALUE, store.profile(player).coins());
        store.adjustCredits(player, Integer.MAX_VALUE);
        fails(INVALID_INPUT, () -> store.adjustCredits(player, 1));
        Title title = title();
        fails(INVALID_INPUT, () -> store.saveListing(null, 0, new ListingDraft(title.id(), Currency.COINS,
                new BigDecimal("1.25"), null, null, null, null, true)));
        fails(INVALID_INPUT, () -> store.saveListing(null, 0, new ListingDraft(title.id(), Currency.VAULT,
                new BigDecimal("1.001"), null, null, null, null, true)));
        fails(INVALID_INPUT, () -> store.prepareCreditPayment(player, 0, BigDecimal.ZERO));
    }

    @Test void repeatedQueriesAndFailuresReturnAllConnections() {
        Title title = title();
        store.grantTitle(player, title.id(), null);
        for (int i = 0; i < 200; i++) {
            store.profile(player);
            store.inventory(player);
            store.equip(player, title.id());
            store.equippedOwnership(player);
            fails(INSUFFICIENT_COINS, () -> store.adjustCoins(player, -1));
        }
        assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> time;
        MutableClock(Instant initial) { time = new AtomicReference<>(initial); }
        void advance(Duration duration) { time.updateAndGet(now -> now.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return time.get(); }
    }
}
