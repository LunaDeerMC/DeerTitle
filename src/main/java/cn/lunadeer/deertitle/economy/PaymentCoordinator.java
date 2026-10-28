package cn.lunadeer.deertitle.economy;

import cn.lunadeer.deertitle.domain.PaymentOperation;
import cn.lunadeer.deertitle.storage.TitleStore;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.Consumer;

/**
 * Database-backed payment orchestration, independent of Bukkit and thread ownership.
 * The supplied withdrawal function is responsible for reaching its economy thread.
 */
public final class PaymentCoordinator {
    private final TitleStore store;
    private final Executor executor;
    private final Consumer<PaymentOperation> reportProviderError;

    public PaymentCoordinator(TitleStore store, Executor executor) {
        this(store, executor, operation -> {});
    }

    public PaymentCoordinator(TitleStore store, Executor executor, Consumer<PaymentOperation> reportProviderError) {
        this.store = Objects.requireNonNull(store, "store");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.reportProviderError = Objects.requireNonNull(reportProviderError, "reportProviderError");
    }

    public CompletableFuture<Void> settle(CompletableFuture<PaymentOperation> prepared,
            Function<PaymentOperation, CompletableFuture<MoneyProvider.Result>> withdraw) {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(withdraw, "withdraw");
        return prepared.thenCompose(operation -> submit(() -> store.markCharging(operation.id())))
                .thenCompose(operation -> withdrawal(operation, withdraw).thenCompose(result -> switch (result) {
                    case SUCCESS -> submit(() -> {
                        store.markCharged(operation.id());
                        store.completePayment(operation.id());
                        return null;
                    });
                    case DECLINED, ERROR -> submit(() -> {
                        store.cancelPayment(operation.id(), result == MoneyProvider.Result.ERROR
                                ? "provider-error" : "provider-declined-or-player-retired");
                        if (result == MoneyProvider.Result.ERROR) reportProviderError.accept(operation);
                        throw new IllegalArgumentException(result == MoneyProvider.Result.ERROR
                                ? "经济插件暂时不可用，本次购买未完成" : "余额不足或付款未完成");
                    });
                }));
    }

    private static CompletableFuture<MoneyProvider.Result> withdrawal(PaymentOperation operation,
            Function<PaymentOperation, CompletableFuture<MoneyProvider.Result>> withdraw) {
        CompletableFuture<MoneyProvider.Result> future;
        try {
            future = withdraw.apply(operation);
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(MoneyProvider.Result.ERROR);
        }
        if (future == null) return CompletableFuture.completedFuture(MoneyProvider.Result.ERROR);
        return future.handle((result, failure) -> failure == null && result != null
                ? result : MoneyProvider.Result.ERROR);
    }

    private <T> CompletableFuture<T> submit(Supplier<T> work) {
        try {
            return CompletableFuture.supplyAsync(work, executor);
        } catch (RuntimeException rejected) {
            return CompletableFuture.failedFuture(rejected);
        }
    }
}
