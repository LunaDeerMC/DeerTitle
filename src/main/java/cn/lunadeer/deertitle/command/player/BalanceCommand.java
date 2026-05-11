package cn.lunadeer.deertitle.command.player;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.SubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class BalanceCommand extends SubCommand {

    @Override
    public String getName() {
        return "balance";
    }

    @Override
    public String getUsage() {
        return "";
    }

    @Override
    public String getDescription() {
        return "Show your balance.";
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }

    @Override
    public void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        Player player = requirePlayer(plugin, sender);
        send(plugin, sender, plugin.getConfigService().language().general.balanceLine,
                plugin.getEconomyService().format(plugin.getEconomyService().getBalance(player)));
    }

    @Override
    public List<String> suggestions(DeerTitlePlugin plugin, CommandSender sender, String[] args) {
        return List.of();
    }
}
