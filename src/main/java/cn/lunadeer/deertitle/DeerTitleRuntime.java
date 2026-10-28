package cn.lunadeer.deertitle;

import cn.lunadeer.deertitle.config.Settings;
import cn.lunadeer.deertitle.domain.Currency;
import cn.lunadeer.deertitle.domain.TitleException;
import cn.lunadeer.deertitle.economy.CommerceService;
import cn.lunadeer.deertitle.economy.MoneyProvider;
import cn.lunadeer.deertitle.platform.PlayerCache;
import cn.lunadeer.deertitle.platform.FormattedTitle;
import cn.lunadeer.deertitle.storage.TitleStore;
import cn.lunadeer.deertitle.text.TitleText;
import cn.lunadeer.deertitle.ui.DialogUi;
import cn.lunadeer.deertitle.ui.UiAccess;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class DeerTitleRuntime implements UiAccess, Listener, AutoCloseable {
    private final DeerTitlePlugin plugin;
    private final Settings settings;
    private final TitleStore store;
    private final ThreadPoolExecutor executor;
    private final PlayerCache cache;
    private final CommerceService commerce;
    private final FormattedTitle emptyDisplay;
    private final DialogUi ui;
    private ScheduledTask refreshTask;
    private volatile boolean closed;
    private volatile long lastFailureLog;

    public DeerTitleRuntime(DeerTitlePlugin plugin, Settings settings, TitleStore store, MoneyProvider provider) {
        this.plugin = plugin; this.settings = settings; this.store = store;
        emptyDisplay = FormattedTitle.render(settings.emptyTitle());
        cache = new PlayerCache(java.time.Duration.ofSeconds(Math.max(15L, settings.refreshSeconds() * 3L)), java.time.Clock.systemUTC());
        executor = new ThreadPoolExecutor(settings.databaseThreads(), settings.databaseThreads(), 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(settings.queueCapacity()), Thread.ofPlatform().daemon().name("DeerTitle-db-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        commerce = new CommerceService(plugin, store, executor, settings, provider);
        ui = new DialogUi(this);
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) register(player);
        refreshTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, ignored -> {
            if (closed) return;
            cache.online().forEach(this::refresh);
            if (plugin.commands() != null) plugin.commands().expirePreview();
        }, 1, settings.refreshSeconds(), TimeUnit.SECONDS);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { register(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cache.quit(uuid);
        ui.forget(uuid);
        if (plugin.commands() != null) plugin.commands().forget(uuid);
    }

    private void register(Player player) {
        UUID id = player.getUniqueId();
        String name = player.getName();
        long generation = cache.join(id);
        submit(() -> store.ensurePlayer(id, name)).whenComplete((profile, failure) -> {
            if (failure == null) refresh(id, generation);
            else logFailure(failure);
        });
    }

    private void refresh(UUID id, long generation) {
        if (closed || !cache.begin(id, generation)) return;
        submit(() -> {
            var profile = store.profile(id);
            var owned = store.equippedOwnership(id).orElse(null);
            var previous = cache.get(id);
            FormattedTitle display = owned == null ? emptyDisplay : previous != null && owned.title().equals(previous.equipped())
                    ? previous.display() : FormattedTitle.render(settings.prefix() + owned.title().miniMessage() + settings.suffix());
            return new PlayerCache.Snapshot(profile, owned == null ? null : owned.title(),
                    owned == null ? null : owned.expiresAt(), generation, display);
        }).whenComplete((snapshot, failure) -> {
            if (failure == null && !closed) cache.put(id, snapshot);
            else { cache.finish(id, generation); if (failure != null) logFailure(failure); }
        });
    }

    public Settings settings() { return settings; }
    public DialogUi ui() { return ui; }
    public PlayerCache.Snapshot cached(UUID id) { return cache.get(id); }
    public FormattedTitle emptyDisplay() { return emptyDisplay; }
    @Override public TitleStore store() { return store; }
    @Override public Currency currency() { return settings.currency(); }
    @Override public String currencyLabel() { return settings.currencyName(); }
    @Override public BigDecimal creditPrice() { return settings.creditPrice(); }
    @Override public int maxTitleLength() { return settings.maxVisibleLength(); }
    @Override public int maxTitleSourceLength() { return settings.maxSourceLength(); }
    @Override public java.time.ZoneId zoneId() { return settings.displayTimeZone(); }

    @Override public void invalidate(UUID id) {
        Long generation = cache.invalidate(id);
        if (generation != null) refresh(id, generation);
    }

    public <T> CompletableFuture<T> submit(Supplier<T> work) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("插件正在关闭"));
        try { return CompletableFuture.supplyAsync(work, executor); }
        catch (RejectedExecutionException ex) { return CompletableFuture.failedFuture(ex); }
    }

    @Override public <T> void async(Player player, Supplier<T> work, Consumer<T> success, Consumer<String> failed) {
        // A command can be opened immediately after login, before background registration finishes.
        UUID id = player.getUniqueId();
        String name = player.getName();
        submit(() -> { store.ensurePlayer(id, name); return work.get(); }).whenComplete((value, failure) -> {
            if (failure != null) onPlayer(player, () -> failed.accept(describe(failure)));
            else onPlayer(player, () -> {
                try { success.accept(value); }
                catch (RuntimeException ex) {
                    // A dialog builder can fail after consuming the previous page. Close now on
                    // the same owning task, never queue a close that could affect a newer page.
                    player.closeDialog();
                    player.sendMessage(Component.text(describe(ex), NamedTextColor.RED));
                }
            });
        });
    }

    public <T> void commandAsync(CommandSender sender, Supplier<T> work, Consumer<T> success) {
        submit(work).whenComplete((value, failure) -> {
            if (failure != null) send(sender, Component.text(describe(failure), NamedTextColor.RED));
            else dispatch(sender, () -> {
                try { success.accept(value); }
                catch (RuntimeException ex) { send(sender, Component.text(describe(ex), NamedTextColor.RED)); }
            });
        });
    }

    @Override public void onPlayer(Player player, Runnable task) {
        if (closed || !plugin.isEnabled()) return;
        try {
            player.getScheduler().run(plugin, ignored -> {
                if (!closed && player.isOnline()) task.run();
            }, null);
        } catch (RuntimeException ignored) { /* Player retired or plugin disabling. */ }
    }

    public void dispatch(CommandSender sender, Runnable work) {
        if (sender instanceof Player player) onPlayer(player, work);
        else if (!closed && plugin.isEnabled()) Bukkit.getGlobalRegionScheduler().execute(plugin, work);
    }
    public void send(CommandSender sender, Component text) { dispatch(sender, () -> sender.sendMessage(text)); }
    @Override public void message(Player player, String miniMessage) { onPlayer(player, () -> player.sendMessage(TitleText.parse(miniMessage))); }
    @Override public void error(Player player, String plainMessage) { send(player, Component.text(plainMessage, NamedTextColor.RED)); }

    @Override public void purchase(Player player, long listingId, long expectedVersion) {
        if (!player.hasPermission("deertitle.use")) { error(player, "没有权限"); return; }
        UUID token = ui.beginExternalOperation(player);
        commerce.purchase(player, listingId, expectedVersion).whenComplete((ignored, failure) -> {
            if (failure != null) onPlayer(player, () -> ui.operationFailed(player, token, describe(failure)));
            else {
                invalidate(player.getUniqueId());
                onPlayer(player, () -> {
                    if (ui.isCurrentOperation(player, token)) { player.sendMessage(Component.text("购买成功", NamedTextColor.GREEN)); ui.openShop(player); }
                });
            }
        });
    }

    @Override public void buyCredits(Player player, int count) {
        if (!player.hasPermission("deertitle.use") || !player.hasPermission("deertitle.custom")) { error(player, "没有权限"); return; }
        UUID token = ui.beginExternalOperation(player);
        commerce.buyCredits(player, count).whenComplete((ignored, failure) -> {
            if (failure != null) onPlayer(player, () -> ui.operationFailed(player, token, describe(failure)));
            else {
                invalidate(player.getUniqueId());
                onPlayer(player, () -> {
                    if (ui.isCurrentOperation(player, token)) { player.sendMessage(Component.text("自定义次数已到账", NamedTextColor.GREEN)); ui.openCustom(player); }
                });
            }
        });
    }

    public String describe(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof TitleException title) return switch (title.code()) {
            case NOT_FOUND -> "记录不存在，请刷新页面";
            case NOT_OWNED -> "你没有这个称号的使用或编辑权限";
            case EXPIRED -> "称号已过期";
            case INSUFFICIENT_COINS -> "称号币不足";
            case INSUFFICIENT_CREDITS -> "自定义次数不足";
            case ALREADY_OWNED -> "你已经拥有这个称号";
            case SHOP_CLOSED -> "此称号当前未出售";
            case OUT_OF_STOCK -> "此称号已售罄";
            case STALE_VERSION -> "内容已更新，请刷新后再操作";
            case INVALID_INPUT -> "输入内容或数值无效";
            case PAYMENT_PENDING -> "你有一笔待核对的付款，请联系管理员";
            case INVALID_PAYMENT_STATE -> "订单状态不允许此操作，请先核对订单";
            case IMPORT_CONFLICT -> "导入来源与现有记录冲突，未修改数据";
            case STORAGE_FAILURE -> { logFailure(cause); yield "数据库操作失败，未确认的付款请联系管理员核对"; }
        };
        if (cause instanceof RejectedExecutionException) return "当前操作较多，请稍后重试";
        if (cause instanceof IllegalArgumentException || cause instanceof IllegalStateException)
            return cause.getMessage() == null ? "操作无法完成" : cause.getMessage();
        logFailure(cause);
        return "操作未完成，请联系管理员查看日志";
    }

    private void logFailure(Throwable cause) {
        long now = System.currentTimeMillis();
        if (now - lastFailureLog < 30000) return;
        lastFailureLog = now;
        // Do not log raw driver exceptions, source credentials, SQL data or entire response objects.
        plugin.getLogger().warning("后台操作失败（" + cause.getClass().getSimpleName() + "），请检查数据库连接和配置。");
    }

    @Override public void close() {
        closed = true;
        if (refreshTask != null) refreshTask.cancel();
        ui.close();
        cache.clear();
        executor.shutdown();
        try { if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException ex) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}
