package cn.lunadeer.deertitle.command.player;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.SubCommand;
import cn.lunadeer.deertitle.service.TitleService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class ListCommand extends SubCommand {

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getUsage() {
        return "";
    }

    @Override
    public String getDescription() {
        return "List your owned titles.";
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }

    @Override
    public void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        Player player = requirePlayer(plugin, sender);
        var ownedTitles = plugin.getTitleService().ownedTitles(player.getUniqueId());
        send(plugin, sender, plugin.getConfigService().language().title.listHeader);
        if (ownedTitles.isEmpty()) {
            send(plugin, sender, plugin.getConfigService().language().title.noOwnedTitles);
            return;
        }
        for (TitleService.OwnedTitleView ownedTitle : ownedTitles) {
            send(plugin, sender, plugin.getConfigService().language().title.listEntry,
                    ownedTitle.title().id(),
                    ownedTitle.title().title(),
                    formatExpiry(plugin, ownedTitle.ownership().expireAt().asLocalDate()),
                    ownedTitle.active() ? plugin.getConfigService().language().general.statusActive : plugin.getConfigService().language().general.statusExpired);
        }
    }

    @Override
    public List<String> suggestions(DeerTitlePlugin plugin, CommandSender sender, String[] args) {
        return List.of();
    }
}
