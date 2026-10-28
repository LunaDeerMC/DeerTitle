package cn.lunadeer.deertitle.economy;

import cn.lunadeer.deertitle.domain.Currency;
import cn.lunadeer.deertitle.domain.ErrorCode;
import cn.lunadeer.deertitle.domain.ListingDraft;
import cn.lunadeer.deertitle.domain.PaymentOperation;
import cn.lunadeer.deertitle.domain.PaymentState;
import cn.lunadeer.deertitle.domain.ShopListing;
import cn.lunadeer.deertitle.domain.Title;
import cn.lunadeer.deertitle.domain.TitleDraft;
import cn.lunadeer.deertitle.domain.TitleException;
import cn.lunadeer.deertitle.storage.SqlDialect;
import cn.lunadeer.deertitle.storage.TitleStore;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import static org.junit.jupiter.api.Assertions.*;

class PaymentCoordinatorTest {
    @TempDir Path directory;
    private TitleStore store;
    private PaymentCoordinator coordinator;
    private UUID player;
    private ShopListing listing;

    @BeforeEach
    void initialize() {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + directory.resolve("payments.db"));
        source.getConfig().setBusyTimeout(5000);
        store = new TitleStore(source, SqlDialect.SQLITE);
        store.initialize();
        coordinator = new PaymentCoordinator(store, Runnable::run);
        player = UUID.randomUUID();
        store.ensurePlayer(player, "Buyer");
        Title title = store.createAdminTitle(UUID.randomUUID(), new TitleDraft("Title", "<gold>Title", "", true));
        listing = store.saveListing(null, 0, new ListingDraft(title.id(), Currency.VAULT,
                new BigDecimal("7.50"), 1L, null, null, null, true));
    }

    @AfterEach
    void close() { store.close(); }

    private PaymentOperation reserve() {
        return store.prepareShopPayment(player, listing.id(), listing.version());
    }

    private PaymentState state(PaymentOperation operation) {
        return store.payment(operation.id()).orElseThrow().state();
    }

    @Test
    void successMovesMoneyOnceAndGrantsAfterTheCommittedChargingState() {
        PaymentOperation operation = reserve();
        AtomicInteger charges = new AtomicInteger();
        Function<PaymentOperation, CompletableFuture<MoneyProvider.Result>> withdraw = current -> {
            assertEquals(PaymentState.CHARGING, state(current));
            assertEquals(0L, store.listing(listing.id()).orElseThrow().remainingStock());
            assertTrue(store.inventory(player).isEmpty());
            charges.incrementAndGet();
            return CompletableFuture.completedFuture(MoneyProvider.Result.SUCCESS);
        };
        coordinator.settle(CompletableFuture.completedFuture(operation), withdraw).join();
        assertEquals(PaymentState.COMPLETE, state(operation));
        assertEquals(1, store.inventory(player).size());
        assertEquals(1, charges.get());
        assertThrows(CompletionException.class,
                () -> coordinator.settle(CompletableFuture.completedFuture(operation), withdraw).join());
        assertEquals(1, charges.get());
        assertEquals(1, store.inventory(player).size());
    }

    @Test
    void concurrentSettlementOfTheSameReservationHasExactlyOneWithdrawal() throws Exception {
        PaymentOperation operation = reserve();
        AtomicInteger charges = new AtomicInteger();
        CountDownLatch invoked = new CountDownLatch(1);
        CompletableFuture<MoneyProvider.Result> provider = new CompletableFuture<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            PaymentCoordinator concurrent = new PaymentCoordinator(store, executor);
            Function<PaymentOperation, CompletableFuture<MoneyProvider.Result>> withdraw = current -> {
                charges.incrementAndGet();
                invoked.countDown();
                return provider;
            };
            var first = concurrent.settle(CompletableFuture.completedFuture(operation), withdraw);
            var second = concurrent.settle(CompletableFuture.completedFuture(operation), withdraw);
            assertTrue(invoked.await(5, TimeUnit.SECONDS));
            provider.complete(MoneyProvider.Result.SUCCESS);
            var firstResult = first.handle((value, error) -> error == null);
            var secondResult = second.handle((value, error) -> error == null);
            assertNotEquals(firstResult.get(5, TimeUnit.SECONDS), secondResult.get(5, TimeUnit.SECONDS));
        }
        assertEquals(1, charges.get());
        assertEquals(PaymentState.COMPLETE, state(operation));
        assertEquals(1, store.inventory(player).size());
    }

    @Test
    void definiteDeclineReleasesTheReservationAndAllowsANewPurchase() {
        PaymentOperation operation = reserve();
        assertThrows(CompletionException.class, () -> coordinator.settle(CompletableFuture.completedFuture(operation),
                current -> CompletableFuture.completedFuture(MoneyProvider.Result.DECLINED)).join());
        assertEquals(PaymentState.CANCELLED, state(operation));
        assertEquals(1L, store.listing(listing.id()).orElseThrow().remainingStock());
        assertTrue(store.inventory(player).isEmpty());
        assertNotEquals(operation.id(), reserve().id());
    }

    @Test
    void playerRetirementBeforeWithdrawalUsesTheSameDefiniteDeclinePath() {
        PaymentOperation operation = reserve();
        AtomicInteger moneyMovements = new AtomicInteger();
        Function<PaymentOperation, CompletableFuture<MoneyProvider.Result>> retiredPlayer = current -> {
            boolean retired = true;
            if (retired) return CompletableFuture.completedFuture(MoneyProvider.Result.DECLINED);
            moneyMovements.incrementAndGet();
            return CompletableFuture.completedFuture(MoneyProvider.Result.SUCCESS);
        };
        assertThrows(CompletionException.class,
                () -> coordinator.settle(CompletableFuture.completedFuture(operation), retiredPlayer).join());
        assertEquals(0, moneyMovements.get());
        assertEquals(PaymentState.CANCELLED, state(operation));
        assertEquals(1L, store.listing(listing.id()).orElseThrow().remainingStock());
    }

    @Test
    void providerErrorReleasesStockAndAllowsAnotherPurchase() {
        assertCancelledAndRetryable(current -> CompletableFuture.completedFuture(MoneyProvider.Result.ERROR));
    }

    @Test
    void exceptionAfterDebitCancelsThePurchaseWithoutAutomaticRetry() {
        AtomicInteger movements = new AtomicInteger();
        assertCancelledAndRetryable(current -> {
            movements.incrementAndGet();
            throw new IllegalStateException("provider failure after debit");
        });
        assertEquals(1, movements.get());
    }

    @Test
    void failedProviderFutureEndsThePurchase() {
        assertCancelledAndRetryable(current -> CompletableFuture.failedFuture(new IllegalStateException("network reply lost")));
    }

    @Test
    void missingProviderFutureEndsThePurchase() {
        assertCancelledAndRetryable(current -> null);
    }

    @Test
    void nullProviderResultEndsThePurchase() {
        assertCancelledAndRetryable(current -> CompletableFuture.completedFuture(null));
    }

    @Test
    void cancelledProviderFutureEndsThePurchaseWithoutRetry() {
        assertCancelledAndRetryable(current -> {
            CompletableFuture<MoneyProvider.Result> future = new CompletableFuture<>();
            future.cancel(false);
            return future;
        });
    }

    private void assertCancelledAndRetryable(Function<PaymentOperation, CompletableFuture<MoneyProvider.Result>> withdraw) {
        PaymentOperation operation = reserve();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger logged = new AtomicInteger();
        coordinator = new PaymentCoordinator(store, Runnable::run, failed -> {
            assertEquals(operation.id(), failed.id());
            logged.incrementAndGet();
        });
        CompletionException error = assertThrows(CompletionException.class,
                () -> coordinator.settle(CompletableFuture.completedFuture(operation), current -> {
                    calls.incrementAndGet();
                    return withdraw.apply(current);
                }).join());
        assertTrue(error.getCause().getMessage().contains("购买未完成"));
        assertEquals(PaymentState.CANCELLED, state(operation));
        assertEquals(1L, store.listing(listing.id()).orElseThrow().remainingStock());
        assertTrue(store.inventory(player).isEmpty());
        assertEquals(1, calls.get());
        assertEquals(1, logged.get());
        assertNotEquals(operation.id(), reserve().id());
        assertEquals(1, calls.get());
    }

    @Test
    void failedPreparationNeverInvokesTheExternalProvider() {
        AtomicInteger withdrawals = new AtomicInteger();
        assertThrows(CompletionException.class, () -> coordinator.settle(
                CompletableFuture.failedFuture(new TitleException(ErrorCode.OUT_OF_STOCK)), current -> {
                    withdrawals.incrementAndGet();
                    return CompletableFuture.completedFuture(MoneyProvider.Result.SUCCESS);
                }).join());
        assertEquals(0, withdrawals.get());
        assertTrue(store.unresolvedPayments().isEmpty());
    }

    @Test
    void creditPurchaseUsesTheSameExactlyOnceSettlement() {
        PaymentOperation operation = store.prepareCreditPayment(player, 3, new BigDecimal("15.00"));
        AtomicInteger withdrawals = new AtomicInteger();
        Function<PaymentOperation, CompletableFuture<MoneyProvider.Result>> withdraw = current -> {
            withdrawals.incrementAndGet();
            return CompletableFuture.completedFuture(MoneyProvider.Result.SUCCESS);
        };
        coordinator.settle(CompletableFuture.completedFuture(operation), withdraw).join();
        assertEquals(3, store.profile(player).customCredits());
        assertThrows(CompletionException.class,
                () -> coordinator.settle(CompletableFuture.completedFuture(operation), withdraw).join());
        assertEquals(3, store.profile(player).customCredits());
        assertEquals(1, withdrawals.get());
    }

    @Test
    void confirmedPaymentWithFailedDeliveryIsAutomaticallyCompletedWithoutAnotherWithdrawal() throws Exception {
        PaymentOperation operation = reserve();
        AtomicInteger withdrawals = new AtomicInteger();
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("payments.db"));
             var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER reject_grant BEFORE INSERT ON dt_ownership BEGIN SELECT RAISE(FAIL, 'test delivery failure'); END");
            assertThrows(CompletionException.class, () -> coordinator.settle(CompletableFuture.completedFuture(operation), current -> {
                withdrawals.incrementAndGet();
                return CompletableFuture.completedFuture(MoneyProvider.Result.SUCCESS);
            }).join());
            assertEquals(PaymentState.CHARGED, state(operation));
            assertTrue(store.inventory(player).isEmpty());
            statement.execute("DROP TRIGGER reject_grant");
        }
        assertEquals(1, store.maintainPayments(10).completed());
        assertEquals(0, store.maintainPayments(10).completed());
        assertEquals(PaymentState.COMPLETE, state(operation));
        assertEquals(1, store.inventory(player).size());
        assertEquals(1, withdrawals.get());
    }
}
