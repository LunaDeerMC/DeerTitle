package cn.lunadeer.deertitle.command;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.configuration.Language;
import cn.lunadeer.deertitle.service.ShopService;
import cn.lunadeer.deertitle.service.TitleService;
import cn.lunadeer.deertitle.ui.MainMenu;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public final class TitleCommand implements TabExecutor {

    private final DeerTitlePlugin plugin;

    public TitleCommand(DeerTitlePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 0) {
                if (sender instanceof Player player) {
                    new MainMenu(plugin, player).open();
                    return true;
                }
                sendHelp(sender);
                return true;
            }
            if (args[0].equalsIgnoreCase("help")) {
                sendHelp(sender);
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "list" -> {
                    Player player = requirePlayer(sender);
                    List<TitleService.OwnedTitleView> ownedTitles = plugin.getTitleService().ownedTitles(player.getUniqueId());
                    send(sender, plugin.getConfigService().language().title.listHeader);
                    if (ownedTitles.isEmpty()) {
                        send(sender, plugin.getConfigService().language().title.noOwnedTitles);
                        return true;
                    }
                    for (TitleService.OwnedTitleView ownedTitle : ownedTitles) {
                        send(sender, plugin.getConfigService().language().title.listEntry,
                                ownedTitle.title().id(),
                                ownedTitle.title().title(),
                                formatExpiry(ownedTitle.ownership().expireAt().asLocalDate()),
                                ownedTitle.active() ? plugin.getConfigService().language().general.statusActive : plugin.getConfigService().language().general.statusExpired);
                    }
                    return true;
                }
                case "wear", "equip" -> {
                    Player player = requirePlayer(sender);
                    int titleId = parseInt(args, 1);
                    var title = plugin.getTitleService().equipTitle(player, titleId);
                    plugin.getInteractionFeedbackService().onTitleEquipped(player, title.title());
                    return true;
                }
                case "remove" -> {
                    Player player = requirePlayer(sender);
                    plugin.getTitleService().unequipTitle(player);
                    plugin.getInteractionFeedbackService().onTitleRemoved(player);
                    return true;
                }
                case "current" -> {
                    Player player = requirePlayer(sender);
                    String current = plugin.getTitleService().currentTitlePlain(player.getUniqueId());
                    if (current.isBlank()) {
                        send(sender, plugin.getConfigService().language().title.currentNone);
                    } else {
                        send(sender, plugin.getConfigService().language().title.currentLine, current);
                    }
                    return true;
                }
                case "balance" -> {
                    Player player = requirePlayer(sender);
                    send(sender, plugin.getConfigService().language().general.balanceLine,
                            plugin.getEconomyService().format(plugin.getEconomyService().getBalance(player)));
                    return true;
                }
                case "shop" -> {
                    if (sender instanceof Player player) {
                        new cn.lunadeer.deertitle.ui.ShopMenu(plugin, player, 0).open();
                        return true;
                    }
                    send(sender, plugin.getConfigService().language().shop.shopHeader);
                    for (ShopService.ShopEntryView entry : plugin.getShopService().listEntries()) {
                        send(sender, plugin.getConfigService().language().shop.shopEntry,
                                entry.offer().id(),
                                entry.title().title(),
                                plugin.getEconomyService().format(entry.offer().price()),
                                entry.offer().amount() < 0 ? plugin.getConfigService().language().shop.unlimited : Integer.toString(entry.offer().amount()),
                                entry.offer().days() < 0 ? plugin.getConfigService().language().shop.unlimited : Integer.toString(entry.offer().days()),
                                formatExpiry(entry.offer().saleEndAt().asLocalDate()),
                                entry.active() ? plugin.getConfigService().language().general.statusActive : plugin.getConfigService().language().general.statusExpired);
                    }
                    return true;
                }
                case "buy" -> {
                    Player player = requirePlayer(sender);
                    int offerId = parseInt(args, 1);
                    try {
                        ShopService.PurchaseResult result = plugin.getShopService().purchase(player, offerId);
                        plugin.getInteractionFeedbackService().onPurchaseSuccess(player, result.title().title());
                    } catch (ShopService.PurchaseFailedException exception) {
                        plugin.getInteractionFeedbackService().onPurchaseFailure(player, exception.reason());
                    }
                    return true;
                }
                case "reload" -> {
                    requireAdmin(sender);
                    plugin.reloadRuntime();
                    send(sender, plugin.getConfigService().language().command.reloaded);
                    return true;
                }
                case "admin" -> {
                    requireAdmin(sender);
                    handleAdmin(sender, args);
                    return true;
                }
                default -> {
                    send(sender, plugin.getConfigService().language().command.unknownSubcommand, args[0]);
                    return true;
                }
            }
        } catch (Exception exception) {
            send(sender, plugin.getConfigService().language().general.internalError + " <gray>" + exception.getMessage() + "</gray>");
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> candidates = new ArrayList<>();
        if (args.length == 1) {
            candidates.addAll(List.of("help", "list", "wear", "remove", "current", "balance", "shop", "buy"));
            if (sender.hasPermission("deertitle.admin")) {
                candidates.addAll(List.of("reload", "admin"));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            candidates.addAll(List.of("create", "setdesc", "setenabled", "grant", "revoke", "shop", "coin", "card"));
        } else if (args.length >= 3 && args[0].equalsIgnoreCase("admin")) {
            switch (args[1].toLowerCase()) {
                case "grant", "revoke", "coin", "card" -> Bukkit.getOnlinePlayers().forEach(player -> candidates.add(player.getName()));
                case "shop" -> candidates.addAll(List.of("set", "clear"));
                default -> {
                }
            }
        }
        List<String> result = new ArrayList<>();
        StringUtil.copyPartialMatches(args[args.length - 1], candidates, result);
        return result;
    }

    private void handleAdmin(CommandSender sender, String[] args) throws Exception {
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> {
                String[] parts = joinFrom(args, 2).split("\\|\\|", 2);
                String titleText = parts[0].trim();
                String description = parts.length > 1 ? parts[1].trim() : "";
                var title = plugin.getTitleService().createTitle(titleText, description);
                send(sender, plugin.getConfigService().language().title.created, title.id(), title.title());
            }
            case "setdesc" -> {
                int titleId = parseInt(args, 2);
                var title = plugin.getTitleService().setDescription(titleId, joinFrom(args, 3));
                send(sender, plugin.getConfigService().language().title.descriptionUpdated, title.id());
            }
            case "setenabled" -> {
                int titleId = parseInt(args, 2);
                boolean enabled = Boolean.parseBoolean(args[3]);
                plugin.getTitleService().setEnabled(titleId, enabled);
                send(sender, plugin.getConfigService().language().title.enabledUpdated, titleId, enabled);
            }
            case "grant" -> {
                OfflinePlayer target = resolvePlayer(args[2]);
                int titleId = parseInt(args, 3);
                Integer days = args.length > 4 ? Integer.parseInt(args[4]) : null;
                plugin.getTitleService().grantTitle(target.getUniqueId(), target.getName(), titleId, days);
                send(sender, plugin.getConfigService().language().title.granted, target.getName(), titleId);
            }
            case "revoke" -> {
                OfflinePlayer target = resolvePlayer(args[2]);
                int titleId = parseInt(args, 3);
                plugin.getTitleService().revokeTitle(target.getUniqueId(), titleId);
                send(sender, plugin.getConfigService().language().title.revoked, target.getName(), titleId);
            }
            case "shop" -> handleAdminShop(sender, args);
            case "coin" -> handleAdminCoin(sender, args);
            case "card" -> {
                Player target = requireOnlinePlayer(args[2]);
                int titleId = parseInt(args, 3);
                Integer days = args.length > 4 ? Integer.parseInt(args[4]) : null;
                var card = plugin.getTitleCardService().createCard(titleId, days);
                target.getInventory().addItem(card);
                var title = plugin.getRepositories().titles().findById(titleId)
                        .orElseThrow(() -> new IllegalArgumentException("Title not found: " + titleId));
                send(sender, plugin.getConfigService().language().card.exported, title.title(), target.getName());
            }
            default -> send(sender, plugin.getConfigService().language().command.unknownSubcommand, args[1]);
        }
    }

    private void handleAdminShop(CommandSender sender, String[] args) throws Exception {
        if (args.length < 3) {
            sendHelp(sender);
            return;
        }
        switch (args[2].toLowerCase()) {
            case "set" -> {
                int titleId = parseInt(args, 3);
                double price = Double.parseDouble(args[4]);
                int days = Integer.parseInt(args[5]);
                int amount = Integer.parseInt(args[6]);
                LocalDate saleEnd = parseOptionalDate(args.length > 7 ? args[7] : "-1");
                var offer = plugin.getShopService().saveOffer(titleId, price, days, amount, saleEnd);
                send(sender, plugin.getConfigService().language().shop.saleSaved, offer.id(), offer.titleId());
            }
            case "clear" -> {
                int titleId = parseInt(args, 3);
                plugin.getShopService().clearOffer(titleId);
                send(sender, plugin.getConfigService().language().shop.saleRemoved, titleId);
            }
            default -> send(sender, plugin.getConfigService().language().command.unknownSubcommand, args[2]);
        }
    }

    private void handleAdminCoin(CommandSender sender, String[] args) throws Exception {
        OfflinePlayer target = resolvePlayer(args[3]);
        double amount = Double.parseDouble(args[4]);
        switch (args[2].toLowerCase()) {
            case "set" -> plugin.getEconomyService().setBuiltInBalance(target, amount);
            case "add" -> plugin.getEconomyService().deposit(target, amount);
            default -> {
                send(sender, plugin.getConfigService().language().command.unknownSubcommand, args[2]);
                return;
            }
        }
        send(sender, plugin.getConfigService().language().general.balanceLine,
                plugin.getEconomyService().format(plugin.getEconomyService().getBalance(target)));
    }

    private void sendHelp(CommandSender sender) {
        Language language = plugin.getConfigService().language();
        for (String line : language.command.helpLines) {
            sender.sendMessage(plugin.getTextFormatter().deserialize(line));
        }
        if (sender.hasPermission("deertitle.admin")) {
            for (String line : language.command.adminHelpLines) {
                sender.sendMessage(plugin.getTextFormatter().deserialize(line));
            }
        }
    }

    private void send(CommandSender sender, String template, Object... arguments) {
        sender.sendMessage(plugin.getTextFormatter().deserializeTemplate(template, arguments));
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        throw new IllegalStateException(plugin.getConfigService().language().general.playerOnly);
    }

    private void requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("deertitle.admin")) {
            throw new IllegalStateException(plugin.getConfigService().language().general.noPermission);
        }
    }

    private Player requireOnlinePlayer(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            throw new IllegalArgumentException("Player must be online: " + name);
        }
        return player;
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayer(name);
    }

    private int parseInt(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing integer argument at index " + index);
        }
        return Integer.parseInt(args[index]);
    }

    private String joinFrom(String[] args, int index) {
        if (index >= args.length) {
            return "";
        }
        return String.join(" ", java.util.Arrays.copyOfRange(args, index, args.length));
    }

    private LocalDate parseOptionalDate(String raw) {
        if (raw == null || raw.equals("-1")) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid date: " + raw);
        }
    }

    private String formatExpiry(LocalDate expiry) {
        return expiry == null ? plugin.getConfigService().language().general.permanent : expiry.toString();
    }
}
