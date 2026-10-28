package cn.lunadeer.deertitle.economy;

import cn.lunadeer.deertitle.config.Settings;
import cn.lunadeer.deertitle.domain.Currency;
import cn.lunadeer.deertitle.domain.PaymentOperation;
import cn.lunadeer.deertitle.storage.TitleStore;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Durable intent precedes external money movement; ambiguous charges require reconciliation. */
public final class CommerceService {
    private final JavaPlugin plugin;
    private final TitleStore store;
    private final Executor executor;
    private final Settings settings;
    private final MoneyProvider provider;
    private final PaymentCoordinator coordinator;

    public CommerceService(JavaPlugin plugin, TitleStore store, Executor executor, Settings settings, MoneyProvider provider) {
        this.plugin = plugin; this.store = store; this.executor = executor; this.settings = settings; this.provider = provider;
        this.coordinator = new PaymentCoordinator(store, executor);
    }

    public CompletableFuture<Void> purchase(Player player, long listingId, long expectedVersion) {
        if (settings.currency() == Currency.COINS)
            return submit(() -> { store.purchaseCoins(player.getUniqueId(), listingId, expectedVersion); return null; });
        return settle(player, submit(() -> store.prepareShopPayment(player.getUniqueId(), listingId, expectedVersion)));
    }

    public CompletableFuture<Void> buyCredits(Player player, int count) {
        if (count < 1 || count > settings.maxCreditsPerPurchase())
            return CompletableFuture.failedFuture(new IllegalArgumentException("购买次数超出允许范围"));
        BigDecimal total = settings.creditPrice().multiply(BigDecimal.valueOf(count));
        if (settings.currency() == Currency.COINS)
            return submit(() -> { store.buyCreditsCoins(player.getUniqueId(), count, total.longValueExact()); return null; });
        return settle(player, submit(() -> store.prepareCreditPayment(player.getUniqueId(), count, total)));
    }

    private CompletableFuture<Void> settle(Player player, CompletableFuture<PaymentOperation> prepared) {
        return coordinator.settle(prepared, operation -> withdraw(player, operation));
    }

    private CompletableFuture<MoneyProvider.Result> withdraw(Player player, PaymentOperation operation) {
        var result = new CompletableFuture<MoneyProvider.Result>();
        if (!plugin.isEnabled() || provider == null) {
            result.complete(MoneyProvider.Result.DECLINED);
            return result;
        }
        try {
            var task = player.getScheduler().run(plugin, ignored -> {
                if (!player.isOnline() || !player.hasPermission("deertitle.use")
                        || (operation.kind() == cn.lunadeer.deertitle.domain.PaymentKind.CREDITS && !player.hasPermission("deertitle.custom"))) {
                    result.complete(MoneyProvider.Result.DECLINED);
                    return;
                }
                try { result.complete(provider.withdraw(player, operation.amount())); }
                catch (RuntimeException ex) { result.complete(MoneyProvider.Result.UNKNOWN); }
            }, () -> result.complete(MoneyProvider.Result.DECLINED));
            if (task == null) result.complete(MoneyProvider.Result.DECLINED);
        } catch (RuntimeException ex) { result.complete(MoneyProvider.Result.DECLINED); }
        return result;
    }

    private <T> CompletableFuture<T> submit(Supplier<T> task) {
        try { return CompletableFuture.supplyAsync(task, executor); }
        catch (RuntimeException ex) { return CompletableFuture.failedFuture(ex); }
    }
}
