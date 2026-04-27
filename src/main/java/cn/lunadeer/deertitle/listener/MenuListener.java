package cn.lunadeer.deertitle.listener;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.ui.Menu;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {

    private final DeerTitlePlugin plugin;

    public MenuListener(DeerTitlePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        try {
            menu.handleClick(event);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to handle menu click: " + exception.getMessage());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
