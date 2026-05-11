package cn.lunadeer.deertitle.command.player;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.SubCommand;
import cn.lunadeer.deertitle.service.ShopService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class ShopCommand extends SubCommand {

    @Override
    public String getName() {
        return "shop";
    }

    @Override
    public String getUsage() {
        return "";
    }

    @Override
    public String getDescription() {
        return "Open the title shop.";
    }

    @Override
    public void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        if (sender instanceof Player player) {
            new cn.lunadeer.deertitle.ui.ShopMenu(plugin, player, 0).open();
            return;
        }
        send(plugin, sender, plugin.getConfigService().language().shop.shopHeader);
        for (ShopService.ShopEntryView entry : plugin.getShopService().listEntries()) {
            send(plugin, sender, plugin.getConfigService().language().shop.shopEntry,
                    entry.offer().id(),
                    entry.title().title(),
                    plugin.getEconomyService().format(entry.offer().price()),
                    entry.offer().amount() < 0 ? plugin.getConfigService().language().shop.unlimited : Integer.toString(entry.offer().amount()),
                    entry.offer().days() < 0 ? plugin.getConfigService().language().shop.unlimited : Integer.toString(entry.offer().days()),
                    formatExpiry(plugin, entry.offer().saleEndAt().asLocalDate()),
                    entry.active() ? plugin.getConfigService().language().general.statusActive : plugin.getConfigService().language().general.statusExpired);
        }
    }

    @Override
    public List<String> suggestions(DeerTitlePlugin plugin, CommandSender sender, String[] args) {
        return List.of();
    }
}
