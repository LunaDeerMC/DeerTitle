package cn.lunadeer.deertitle.platform;

import cn.lunadeer.deertitle.DeerTitleRuntime;
import cn.lunadeer.deertitle.config.Settings;
import cn.lunadeer.deertitle.text.TitleText;
import java.time.Instant;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public final class TitleExpansion extends PlaceholderExpansion {
    private static final java.util.Set<String> TITLE_KEYS = java.util.Set.of("title", "player_title", "title_minimessage", "title_plain", "title_legacy");
    private final DeerTitleRuntime runtime;
    private final String version;

    public TitleExpansion(DeerTitleRuntime runtime, String version) { this.runtime = runtime; this.version = version; }
    @Override public @NotNull String getIdentifier() { return "deertitle"; }
    @Override public @NotNull String getAuthor() { return "LunaDeer"; }
    @Override public @NotNull String getVersion() { return version; }
    @Override public boolean persist() { return true; }

    @Override public String onRequest(OfflinePlayer player, @NotNull String parameter) {
        if (player == null) return "";
        PlayerCache.Snapshot snapshot = runtime.cached(player.getUniqueId());
        String key = parameter.toLowerCase(Locale.ROOT);
        if (key.equals("coins")) return snapshot == null ? "0" : Long.toString(snapshot.profile().coins());
        if (key.equals("custom_credits")) return snapshot == null ? "0" : Integer.toString(snapshot.profile().customCredits());
        boolean active = snapshot != null && snapshot.equipped() != null
                && (snapshot.expiresAt() == null || snapshot.expiresAt().isAfter(Instant.now()));
        if (key.equals("title_id")) return active ? Long.toString(snapshot.equipped().id()) : "";
        if (!TITLE_KEYS.contains(key)) return null;
        Settings settings = runtime.settings();
        FormattedTitle rendered = active && snapshot.display() != null ? snapshot.display() : runtime.emptyDisplay();
        String format = switch (key) {
            case "title_minimessage" -> "minimessage";
            case "title_plain" -> "plain";
            case "title_legacy" -> "legacy";
            default -> settings.placeholderFormat();
        };
        return switch (format) {
            case "plain" -> rendered.plain();
            case "minimessage" -> rendered.miniMessage();
            default -> rendered.legacy();
        };
    }
}
