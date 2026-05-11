package cn.lunadeer.deertitle.command.player;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.SubCommand;
import cn.lunadeer.deertitle.service.ShopService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class BuyCommand extends SubCommand {

    @Override
    public String getName() {
        return "buy";
    }

    @Override
    public String getUsage() {
        return "<offerId>";
    }

    @Override
    public String getDescription() {
        return "Purchase a title offer from the shop.";
    }

    @Override
    public boolean requiresPlayer() {
        return true;
    }

    @Override
    public void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        Player player = requirePlayer(plugin, sender);
        int offerId = parseInt(args, 0);
        try {
            ShopService.PurchaseResult result = plugin.getShopService().purchase(player, offerId);
            plugin.getInteractionFeedbackService().onPurchaseSuccess(player, result.title().title());
        } catch (ShopService.PurchaseFailedException exception) {
            plugin.getInteractionFeedbackService().onPurchaseFailure(player, exception.reason());
        }
    }

    @Override
    public List<String> suggestions(DeerTitlePlugin plugin, CommandSender sender, String[] args) {
        List<String> result = new ArrayList<>();
        try {
            for (ShopService.ShopEntryView entry : plugin.getShopService().listEntries()) {
                if (!entry.active()) continue;
                result.add(entry.offer().id() + " " + entry.title().title());
            }
        } catch (Exception ignored) {
        }
        return result;
    }
}
