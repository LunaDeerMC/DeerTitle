package cn.lunadeer.deertitle.listener;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class TitleCardListener implements Listener {

    private final DeerTitlePlugin plugin;

    public TitleCardListener(DeerTitlePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerUseCard(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        try {
            if (plugin.getTitleCardService().tryUseTitleCard(event.getPlayer())) {
                event.setCancelled(true);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to use title card for " + event.getPlayer().getName() + ": " + exception.getMessage());
        }
    }
}
