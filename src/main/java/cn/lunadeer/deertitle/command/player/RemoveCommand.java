package cn.lunadeer.deertitle.command.player;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.SubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class RemoveCommand extends SubCommand {

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public String getUsage() {
        return "";
    }

    @Override
    public String getDescription() {
        return "Remove your current title.";
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }

    @Override
    public void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        Player player = requirePlayer(plugin, sender);
        plugin.getTitleService().unequipTitle(player);
        plugin.getInteractionFeedbackService().onTitleRemoved(player);
    }

    @Override
    public List<String> suggestions(DeerTitlePlugin plugin, CommandSender sender, String[] args) {
        return List.of();
    }
}
