package cn.lunadeer.deertitle.command.admin;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.SubCommand;
import cn.lunadeer.deertitle.database.model.TitleRecord;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public final class AdminRevokeCommand extends SubCommand {

    @Override
    public String getName() {
        return "revoke";
    }

    @Override
    public String getUsage() {
        return "<player> <titleId>";
    }

    @Override
    public String getDescription() {
        return "Revoke a title from a player.";
    }

    @Override
    public boolean requiresAdmin() {
        return true;
    }

    @Override
    public void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        requireAdmin(plugin, sender);
        OfflinePlayer target = resolvePlayer(args[0]);
        int titleId = parseInt(args, 1);
        plugin.getTitleService().revokeTitle(target.getUniqueId(), titleId);
        send(plugin, sender, plugin.getConfigService().language().title.revoked, target.getName(), titleId);
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
