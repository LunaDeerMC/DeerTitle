package cn.lunadeer.deertitle.placeholder;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeerTitlePlaceholderExpansion extends PlaceholderExpansion {

    private final DeerTitlePlugin plugin;

    public DeerTitlePlaceholderExpansion(DeerTitlePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "deertitle";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || player.getUniqueId() == null) {
            return "";
        }
        try {
            return switch (params.toLowerCase()) {
                case "current", "current_legacy" -> plugin.getTitleService().currentTitleLegacy(player.getUniqueId());
                case "current_plain" -> plugin.getTitleService().currentTitlePlain(player.getUniqueId());
                case "has_title" -> Boolean.toString(!plugin.getTitleService().currentTitlePlain(player.getUniqueId()).isBlank());
                default -> null;
            };
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to resolve placeholder '" + params + "' for " + player.getName() + ": " + exception.getMessage());
            return "";
        }
    }
}
