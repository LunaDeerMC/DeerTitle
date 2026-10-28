package cn.lunadeer.deertitle.command;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.DeerTitleRuntime;
import cn.lunadeer.deertitle.importer.ImportPreview;
import cn.lunadeer.deertitle.importer.LegacySourceConfigLoader;
import cn.lunadeer.deertitle.importer.MiniPlayerTitleImporter;
import cn.lunadeer.deertitle.importer.SourceConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class DeerTitleCommand implements CommandExecutor, TabCompleter {
    private record Pending(String owner, String token, Instant expires, ImportPreview preview) {}
    private final DeerTitlePlugin plugin;
    private final DeerTitleRuntime runtime;
    private final AtomicReference<Pending> pending = new AtomicReference<>();
    private final AtomicBoolean reading = new AtomicBoolean();
    private final Object previewLock = new Object();
    private long previewGeneration;
    private String previewOwner;
    private boolean closed;

    public DeerTitleCommand(DeerTitlePlugin plugin, DeerTitleRuntime runtime) { this.plugin = plugin; this.runtime = runtime; }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        try {
            String action = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
            if (List.of("menu", "inventory", "shop", "custom").contains(action)) {
                if (!(sender instanceof Player player)) { line(sender, "游戏内使用 /deertitle；控制台使用 /deertitle status"); return true; }
                require(sender, "deertitle.use");
                if (action.equals("custom")) require(sender, "deertitle.custom");
                runtime.onPlayer(player, () -> {
                    switch (action) {
                        case "inventory" -> runtime.ui().openInventory(player);
                        case "shop" -> runtime.ui().openShop(player);
                        case "custom" -> runtime.ui().openCustom(player);
                        default -> runtime.ui().openMain(player);
                    }
                });
                return true;
            }
            require(sender, "deertitle.admin");
            switch (action) {
                case "admin", "editor" -> {
                    if (!(sender instanceof Player player)) throw new IllegalArgumentException("请在游戏内打开管理页面");
                    runtime.onPlayer(player, () -> {
                        if (action.equals("editor")) runtime.ui().openEditorForNewAdmin(player);
                        else runtime.ui().openAdminShop(player);
                    });
                }
                case "status" -> line(sender, "DeerTitle " + plugin.getPluginMeta().getVersion() + " · " + runtime.settings().database().type()
                            + " · " + runtime.currency() + " · 跨服 " + runtime.settings().crossServer());
                case "coins", "credits" -> adjust(sender, action, args);
                case "grant" -> grant(sender, args);
                case "import" -> importData(sender, args);
                default -> help(sender);
            }
        } catch (RuntimeException ex) { runtime.send(sender, Component.text(runtime.describe(ex), NamedTextColor.RED)); }
        return true;
    }

    private void adjust(CommandSender sender, String kind, String[] args) {
        if (args.length != 4 || !List.of("add", "take").contains(args[2]))
            throw new IllegalArgumentException("/deertitle " + kind + " <玩家名或 UUID> <add|take> <数量>");
        UUID player = target(args[1]);
        long quantity = positiveLong(args[3]);
        long delta = args[2].equals("take") ? -quantity : quantity;
        if (kind.equals("credits") && quantity > Integer.MAX_VALUE) throw new IllegalArgumentException("次数过大");
        runtime.commandAsync(sender, () -> {
            if (kind.equals("coins")) runtime.store().adjustCoins(player, delta);
            else runtime.store().adjustCredits(player, (int) delta);
            return runtime.store().profile(player);
        }, profile -> {
            runtime.invalidate(player);
            line(sender, kind.equals("coins") ? "称号币：" + profile.coins() : "自定义次数：" + profile.customCredits());
        });
    }

    private void grant(CommandSender sender, String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("/deertitle grant <玩家名或 UUID> <称号 ID>");
        UUID player = target(args[1]);
        long title = positiveLong(args[2]);
        runtime.commandAsync(sender, () -> { runtime.store().grantTitle(player, title, null); return player; }, id -> {
            runtime.invalidate(id); line(sender, "称号已发放");
        });
    }

    private void importData(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("miniplayertitle"))
            throw new IllegalArgumentException("/deertitle import miniplayertitle <preview|confirm>");
        if (args[2].equalsIgnoreCase("preview") && args.length == 3) {
            if (!reading.compareAndSet(false, true)) throw new IllegalArgumentException("已有导入预览正在读取");
            final long generation;
            final String requester = owner(sender);
            synchronized (previewLock) {
                if (closed) { reading.set(false); return; }
                generation = ++previewGeneration;
                previewOwner = requester;
                pending.set(null);
            }
            line(sender, "正在只读分析 MiniPlayerTitle 数据……");
            var settings = runtime.settings();
            runtime.submit(() -> {
                    var policy = SourceConfig.CoinFractionPolicy.valueOf(settings.fractionalCoins().toUpperCase(Locale.ROOT).replace('-', '_'));
                    var source = LegacySourceConfigLoader.load(settings.importDirectory(), settings.importSourceId(),
                            settings.importTimeZone(), policy, settings.currency(), settings.importShop());
                    return new MiniPlayerTitleImporter().preview(source);
            }).whenComplete((preview, failure) -> {
                try {
                if (failure != null) { line(sender, runtime.describe(failure)); return; }
                String token = UUID.randomUUID().toString().substring(0, 8);
                Pending value = new Pending(requester, token, Instant.now().plusSeconds(300), preview);
                synchronized (previewLock) {
                    if (closed || previewGeneration != generation || !requester.equals(previewOwner)) return;
                    pending.set(preview.valid() ? value : null);
                }
                runtime.dispatch(sender, () -> {
                    var batch = preview.batch();
                    line(sender, "导入预览：称号 " + batch.titles().size() + "，玩家 " + batch.players().size()
                            + "，拥有关系 " + batch.ownerships().size() + "，商店 " + batch.listings().size());
                    line(sender, "警告 " + preview.warnings() + "，错误 " + preview.errors() + "；来源时区 " + settings.importTimeZone());
                    preview.issues().stream().limit(12).forEach(issue -> line(sender, issue.severity() + " " + issue.record() + "：" + issue.message()));
                    if (preview.issues().size() > 12) line(sender, "另有 " + (preview.issues().size() - 12) + " 条问题；请检查来源数据。");
                    if (preview.valid()) line(sender, "确认导入：/deertitle import miniplayertitle confirm " + token);
                    else line(sender, "存在错误，未执行导入。修正来源或导入配置后重新预览。");
                });
                } finally { reading.set(false); }
            });
            return;
        }
        if (args[2].equalsIgnoreCase("confirm") && args.length == 4) {
            Pending value = pending.get();
            if (value == null || !value.owner().equals(owner(sender)) || !value.token().equals(args[3]) || !value.expires().isAfter(Instant.now()))
                throw new IllegalArgumentException("确认码无效或已过期，请重新预览");
            if (!pending.compareAndSet(value, null)) throw new IllegalArgumentException("此预览已被使用");
            runtime.commandAsync(sender, () -> new MiniPlayerTitleImporter().execute(value.preview(), runtime.store()), report -> {
                line(sender, "导入完成：称号 " + report.titles() + "，玩家 " + report.players() + "，拥有关系 " + report.ownerships()
                        + "，商店 " + report.listings() + "，跳过 " + report.skipped());
                for (Player player : Bukkit.getOnlinePlayers()) runtime.invalidate(player.getUniqueId());
            });
            return;
        }
        throw new IllegalArgumentException("先执行 /deertitle import miniplayertitle preview，再使用返回的确认码");
    }

    public void expirePreview() { Pending old = pending.get(); if (old != null && !old.expires().isAfter(Instant.now())) pending.compareAndSet(old, null); }
    public void forget(UUID id) {
        synchronized (previewLock) {
            if (id.toString().equals(previewOwner)) { previewGeneration++; previewOwner = null; pending.set(null); }
        }
    }
    public void clear() {
        synchronized (previewLock) { closed = true; previewGeneration++; previewOwner = null; pending.set(null); }
    }
    private static String owner(CommandSender sender) { return sender instanceof Player player ? player.getUniqueId().toString() : "console"; }
    private static void require(CommandSender sender, String permission) { if (!sender.hasPermission(permission)) throw new IllegalArgumentException("没有权限"); }
    private void line(CommandSender sender, String text) { runtime.send(sender, Component.text(text)); }
    private static long positiveLong(String text) {
        long value;
        try { value = Long.parseLong(text); } catch (NumberFormatException ex) { throw new IllegalArgumentException("数量或 ID 必须为正整数"); }
        if (value < 1) throw new IllegalArgumentException("数量或 ID 必须为正整数");
        return value;
    }
    private static UUID target(String text) {
        try { return UUID.fromString(text); }
        catch (IllegalArgumentException ex) {
            Player player = Bukkit.getPlayerExact(text);
            if (player == null) throw new IllegalArgumentException("离线玩家请使用 UUID；在线玩家可使用完整名字");
            return player.getUniqueId();
        }
    }
    private void help(CommandSender sender) {
        line(sender, "/deertitle [menu|inventory|shop|custom|admin|editor|status]");
        line(sender, "/deertitle coins|credits <玩家名或 UUID> add|take <数量>");
        line(sender, "/deertitle grant <玩家名或 UUID> <称号 ID>");
        line(sender, "/deertitle import miniplayertitle preview");
    }
    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                               @NotNull String alias, @NotNull String[] args) {
        List<String> choices = new ArrayList<>();
        if (args.length == 1) {
            choices.addAll(List.of("menu", "inventory", "shop", "custom"));
            if (sender.hasPermission("deertitle.admin")) choices.addAll(List.of("admin", "editor", "status", "coins", "credits", "grant", "import"));
        } else if (sender.hasPermission("deertitle.admin")) {
            if (args[0].equalsIgnoreCase("import")) choices = args.length == 2 ? List.of("miniplayertitle") : args.length == 3 ? List.of("preview", "confirm") : List.of();
            else if (List.of("coins", "credits").contains(args[0]) && args.length == 3) choices = List.of("add", "take");
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
