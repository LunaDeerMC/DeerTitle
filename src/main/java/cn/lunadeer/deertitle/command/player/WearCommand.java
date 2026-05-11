package cn.lunadeer.deertitle.command.player;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.SubCommand;
import cn.lunadeer.deertitle.service.TitleService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class WearCommand extends SubCommand {

    @Override
    public String getName() {
        return "wear";
    }

    @Override
    public String getUsage() {
        return "<titleId>";
    }

    @Override
    public String getDescription() {
        return "Equip a title you own.";
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }

    @Override
    public void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        Player player = requirePlayer(plugin, sender);
        int titleId = parseInt(args, 0);
        var title = plugin.getTitleService().equipTitle(player, titleId);
        plugin.getInteractionFeedbackService().onTitleEquipped(player, title.title());
    }

    @Override
    public List<String> suggestions(DeerTitlePlugin plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        try {
            for (TitleService.OwnedTitleView owned : plugin.getTitleService().ownedTitles(player.getUniqueId())) {
                if (!owned.active()) continue;
                result.add(owned.title().id() + " " + owned.title().title());
            }
        } catch (Exception ignored) {
        }
        return result;
    }
}
