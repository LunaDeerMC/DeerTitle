package cn.lunadeer.deertitle.command.player;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.SubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class CurrentCommand extends SubCommand {

    @Override
    public String getName() {
        return "current";
    }

    @Override
    public String getUsage() {
        return "";
    }

    @Override
    public String getDescription() {
        return "Show your current equipped title.";
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }

    @Override
    public void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        Player player = requirePlayer(plugin, sender);
        String current = plugin.getTitleService().currentTitlePlain(player.getUniqueId());
        if (current.isBlank()) {
            send(plugin, sender, plugin.getConfigService().language().title.currentNone);
        } else {
            send(plugin, sender, plugin.getConfigService().language().title.currentLine, current);
        }
    }

    @Override
    public List<String> suggestions(DeerTitlePlugin plugin, CommandSender sender, String[] args) {
        return List.of();
    }
}
