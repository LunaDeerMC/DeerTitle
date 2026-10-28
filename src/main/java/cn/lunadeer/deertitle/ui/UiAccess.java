package cn.lunadeer.deertitle.ui;

import cn.lunadeer.deertitle.domain.Currency;
import cn.lunadeer.deertitle.storage.TitleStore;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/** Runtime boundary: SQL runs off-thread; callbacks and player operations run on the owning region. */
public interface UiAccess {
    TitleStore store();
    <T> void async(Player player, Supplier<T> work, Consumer<T> success, Consumer<String> failure);
    default <T> void async(Player player, Supplier<T> work, Consumer<T> success) {
        async(player, work, success, message -> error(player, message));
    }
    void onPlayer(Player player, Runnable task);
    void message(Player player, String miniMessage);
    void error(Player player, String plainMessage);
    void purchase(Player player, long listingId, long expectedVersion);
    void buyCredits(Player player, int count);
    Currency currency();
    String currencyLabel();
    BigDecimal creditPrice();
    int maxTitleLength();
    int maxTitleSourceLength();
    void invalidate(UUID playerId);
    default ZoneId zoneId() { return ZoneId.of("Asia/Shanghai"); }
}
