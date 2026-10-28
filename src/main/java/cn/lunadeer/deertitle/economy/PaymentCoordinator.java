package cn.lunadeer.deertitle.economy;

import cn.lunadeer.deertitle.domain.PaymentOperation;
import cn.lunadeer.deertitle.storage.TitleStore;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Database-backed payment orchestration, independent of Bukkit and thread ownership.
 * The supplied withdrawal function is responsible for reaching its economy thread.
 */
public final class PaymentCoordinator {
    private final TitleStore store;
    private final Executor executor;

    public PaymentCoordinator(TitleStore store, Executor executor) {
        this.store = Objects.requireNonNull(store, "store");
        this.executor = Objects.requireNonNull(executor, "executor");
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
                    case DECLINED -> submit(() -> {
                        store.cancelPayment(operation.id(), "provider-declined-or-player-retired");
                        throw new IllegalArgumentException("付款未完成，请检查余额或经济插件状态");
                    });
                    case UNKNOWN -> CompletableFuture.failedFuture(new IllegalStateException(
                            "付款结果待核对，请联系管理员；订单 " + operation.id()));
                }));
    }

    private static CompletableFuture<MoneyProvider.Result> withdrawal(PaymentOperation operation,
            Function<PaymentOperation, CompletableFuture<MoneyProvider.Result>> withdraw) {
        CompletableFuture<MoneyProvider.Result> future;
        try {
            future = withdraw.apply(operation);
        } catch (Throwable failure) {
            // Even a throwing provider may already have moved money. Never infer rejection.
            return CompletableFuture.completedFuture(MoneyProvider.Result.UNKNOWN);
        }
        if (future == null) return CompletableFuture.completedFuture(MoneyProvider.Result.UNKNOWN);
        return future.handle((result, failure) -> failure == null && result != null
                ? result : MoneyProvider.Result.UNKNOWN);
    }

    private <T> CompletableFuture<T> submit(Supplier<T> work) {
        try {
            return CompletableFuture.supplyAsync(work, executor);
        } catch (RuntimeException rejected) {
            return CompletableFuture.failedFuture(rejected);
        }
    }
}
