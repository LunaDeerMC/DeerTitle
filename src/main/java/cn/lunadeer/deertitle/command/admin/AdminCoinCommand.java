package cn.lunadeer.deertitle.command.admin;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.SubCommand;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class AdminCoinCommand extends SubCommand {

    @Override
    public String getName() {
        return "coin";
    }

    @Override
    public String getUsage() {
        return "<set|add> <player> <amount>";
    }

    @Override
    public String getDescription() {
        return "Manage a player's built-in coin balance.";
    }

    @Override
    public boolean requiresAdmin() {
        return true;
    }

    @Override
    public void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        requireAdmin(plugin, sender);
        if (args.length < 3) {
            send(plugin, sender, plugin.getConfigService().language().command.unknownSubcommand, args.length > 0 ? args[0] : "");
            return;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        double amount = Double.parseDouble(args[2]);
        switch (args[0].toLowerCase()) {
            case "set" -> plugin.getEconomyService().setBuiltInBalance(target, amount);
            case "add" -> plugin.getEconomyService().deposit(target, amount);
            default -> {
                send(plugin, sender, plugin.getConfigService().language().command.unknownSubcommand, args[0]);
                return;
            }
        }
        send(plugin, sender, plugin.getConfigService().language().general.balanceLine,
                plugin.getEconomyService().format(plugin.getEconomyService().getBalance(target)));
    }

    @Override
    public List<String> suggestions(DeerTitlePlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of("set", "add");
        }
        if (args.length == 1) {
            return onlinePlayerNames();
        }
        return List.of();
    }
}
