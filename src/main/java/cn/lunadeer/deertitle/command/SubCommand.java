package cn.lunadeer.deertitle.command;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.utils.compat.BukkitCompat;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

public abstract class SubCommand {

    public abstract String getName();
    public abstract String getUsage();
    public abstract String getDescription();

    public boolean requiresPlayer() {
        return false;
    }

    public boolean requiresAdmin() {
        return false;
    }

    public abstract void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception;

    public abstract List<String> suggestions(DeerTitlePlugin plugin, CommandSender sender, String[] args);

    protected void send(DeerTitlePlugin plugin, CommandSender sender, String template, Object... arguments) {
        BukkitCompat.sendMessage(sender, plugin.getTextFormatter().deserializeTemplate(template, arguments), plugin.getTextFormatter());
    }

    protected Player requirePlayer(DeerTitlePlugin plugin, CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        throw new IllegalStateException(plugin.getConfigService().language().general.playerOnly);
    }

    protected void requireAdmin(DeerTitlePlugin plugin, CommandSender sender) {
        if (!sender.hasPermission("deertitle.admin")) {
            throw new IllegalStateException(plugin.getConfigService().language().general.noPermission);
        }
    }

    protected Player requireOnlinePlayer(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            throw new IllegalArgumentException("Player must be online: " + name);
        }
        return player;
    }

    protected OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayer(name);
    }

    protected int parseInt(String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing integer argument at index " + index);
        }
        return Integer.parseInt(args[index]);
    }

    protected String joinFrom(String[] args, int index) {
        if (index >= args.length) {
            return "";
        }
        return String.join(" ", java.util.Arrays.copyOfRange(args, index, args.length));
    }

    protected LocalDate parseOptionalDate(String raw) {
        if (raw == null || raw.equals("-1")) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid date: " + raw);
        }
    }

    protected String formatExpiry(DeerTitlePlugin plugin, LocalDate expiry) {
        return expiry == null ? plugin.getConfigService().language().general.permanent : expiry.toString();
    }

    protected List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }
}
