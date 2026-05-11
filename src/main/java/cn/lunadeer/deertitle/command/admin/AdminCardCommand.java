package cn.lunadeer.deertitle.command.admin;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.SubCommand;
import cn.lunadeer.deertitle.database.model.TitleRecord;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class AdminCardCommand extends SubCommand {

    @Override
    public String getName() {
        return "card";
    }

    @Override
    public String getUsage() {
        return "<player> <titleId> [days|-1]";
    }

    @Override
    public String getDescription() {
        return "Give a title card item to an online player.";
    }

    @Override
    public boolean requiresAdmin() {
        return true;
    }

    @Override
    public void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        requireAdmin(plugin, sender);
        Player target = requireOnlinePlayer(args[0]);
        int titleId = parseInt(args, 1);
        Integer days = args.length > 2 ? Integer.parseInt(args[2]) : null;
        var card = plugin.getTitleCardService().createCard(titleId, days);
        target.getInventory().addItem(card);
        var title = plugin.getRepositories().titles().findById(titleId)
                .orElseThrow(() -> new IllegalArgumentException("Title not found: " + titleId));
        send(plugin, sender, plugin.getConfigService().language().card.exported, title.title(), target.getName());
    }

    @Override
    public List<String> suggestions(DeerTitlePlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 0) {
            return onlinePlayerNames();
        }
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            try {
                for (TitleRecord title : plugin.getRepositories().titles().findAll(true)) {
                    result.add(title.id() + " " + title.title());
                }
            } catch (Exception ignored) {
            }
            return result;
        }
        return List.of();
    }
}
