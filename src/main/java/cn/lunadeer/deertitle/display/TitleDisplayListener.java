package cn.lunadeer.deertitle.display;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class TitleDisplayListener implements Listener {

    private final DeerTitlePlugin plugin;

    public TitleDisplayListener(DeerTitlePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        try {
            plugin.getTitleService().ensurePlayerRecord(event.getPlayer());
            if (!plugin.hasPlaceholderApi()) {
                plugin.getTitleService().refreshPlayerListName(event.getPlayer());
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to initialize player title data for " + event.getPlayer().getName() + ": " + exception.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncChat(AsyncChatEvent event) {
        if (plugin.hasPlaceholderApi()) {
            return;
        }
        try {
            Component prefix = plugin.getTitleService().currentTitlePrefix(event.getPlayer().getUniqueId());
            if (prefix.equals(Component.empty())) {
                return;
            }
            event.renderer((source, sourceDisplayName, message, viewer) -> Component.empty()
                    .append(prefix)
                    .append(sourceDisplayName)
                    .append(Component.space())
                    .append(message));
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to render title chat prefix for " + event.getPlayer().getName() + ": " + exception.getMessage());
        }
    }
}
