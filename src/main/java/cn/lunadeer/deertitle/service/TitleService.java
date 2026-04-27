package cn.lunadeer.deertitle.service;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.database.model.PlayerInfoRecord;
import cn.lunadeer.deertitle.database.model.PlayerTitleRecord;
import cn.lunadeer.deertitle.database.model.TitleRecord;
import cn.lunadeer.deertitle.database.model.DateParts;
import cn.lunadeer.deertitle.database.repository.RepositoryRegistry;
import cn.lunadeer.deertitle.text.TextFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class TitleService {

    private final DeerTitlePlugin plugin;
    private final RepositoryRegistry repositories;
    private final TextFormatter textFormatter;

    public TitleService(DeerTitlePlugin plugin, RepositoryRegistry repositories, TextFormatter textFormatter) {
        this.plugin = plugin;
        this.repositories = repositories;
        this.textFormatter = textFormatter;
    }

    public PlayerInfoRecord ensurePlayerRecord(Player player) throws Exception {
        return repositories.playerInfo().findOrCreate(
                player.getUniqueId(),
                player.getName(),
                plugin.getConfigService().config().economy.defaultBalance
        );
    }

    public Optional<TitleRecord> currentTitle(UUID playerId) throws Exception {
        Optional<PlayerInfoRecord> playerInfo = repositories.playerInfo().find(playerId);
        if (playerInfo.isEmpty() || playerInfo.get().usingTitleId() == null) {
            return Optional.empty();
        }
        Optional<PlayerTitleRecord> ownership = repositories.playerTitles().findOwnership(playerId, playerInfo.get().usingTitleId());
        if (ownership.isEmpty() || !isOwnershipActive(ownership.get(), LocalDate.now())) {
            return Optional.empty();
        }
        Optional<TitleRecord> title = repositories.titles().findById(playerInfo.get().usingTitleId());
        if (title.isEmpty() || !title.get().enabled()) {
            return Optional.empty();
        }
        return title;
    }

    public Component currentTitleComponent(UUID playerId) throws Exception {
        Optional<TitleRecord> title = currentTitle(playerId);
        return title.map(value -> decorateTitle(textFormatter.deserialize(value.title()))).orElse(Component.empty());
    }

    public List<OwnedTitleView> ownedTitles(UUID playerId) throws Exception {
        List<OwnedTitleView> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (PlayerTitleRecord ownership : repositories.playerTitles().findByPlayer(playerId)) {
            Optional<TitleRecord> title = repositories.titles().findById(ownership.titleId());
            if (title.isEmpty()) {
                continue;
            }
            result.add(new OwnedTitleView(title.get(), ownership, isOwnershipActive(ownership, today)));
        }
        return result;
    }

    public TitleRecord createTitle(String rawTitle, String description) throws Exception {
        return repositories.titles().save(new TitleRecord(0, rawTitle, description, true));
    }

    public TitleRecord setDescription(int titleId, String description) throws Exception {
        TitleRecord title = repositories.titles().findById(titleId)
                .orElseThrow(() -> new IllegalArgumentException("Title not found: " + titleId));
        return repositories.titles().save(new TitleRecord(title.id(), title.title(), description, title.enabled()));
    }

    public TitleRecord setEnabled(int titleId, boolean enabled) throws Exception {
        TitleRecord title = repositories.titles().findById(titleId)
                .orElseThrow(() -> new IllegalArgumentException("Title not found: " + titleId));
        return repositories.titles().save(new TitleRecord(title.id(), title.title(), title.description(), enabled));
    }

    public List<TitleRecord> allTitles(boolean includeDisabled) throws Exception {
        return repositories.titles().findAll(includeDisabled);
    }

    public TitleGrantResult grantTitle(UUID playerId, String playerName, int titleId, Integer days) throws Exception {
        ensurePlayerRecord(playerId, playerName);
        TitleRecord title = repositories.titles().findById(titleId)
                .orElseThrow(() -> new IllegalArgumentException("Title not found: " + titleId));
        Optional<PlayerTitleRecord> existing = repositories.playerTitles().findOwnership(playerId, titleId);
        DateParts expiry = mergeExpiry(existing.map(PlayerTitleRecord::expireAt).orElse(null), days);
        PlayerTitleRecord ownership = repositories.playerTitles().save(playerId, titleId, expiry);
        return new TitleGrantResult(title, ownership, existing.isPresent());
    }

    public void revokeTitle(UUID playerId, int titleId) throws Exception {
        repositories.playerTitles().deleteByPlayerAndTitle(playerId, titleId);
        Optional<PlayerInfoRecord> playerInfo = repositories.playerInfo().find(playerId);
        if (playerInfo.isPresent() && playerInfo.get().usingTitleId() != null && playerInfo.get().usingTitleId() == titleId) {
            repositories.playerInfo().updateCurrentTitle(playerId, null);
        }
    }

    public TitleRecord equipTitle(Player player, int titleId) throws Exception {
        ensurePlayerRecord(player);
        PlayerTitleRecord ownership = repositories.playerTitles().findOwnership(player.getUniqueId(), titleId)
                .orElseThrow(() -> new IllegalArgumentException("Player does not own title: " + titleId));
        if (!isOwnershipActive(ownership, LocalDate.now())) {
            throw new IllegalStateException("Title has expired: " + titleId);
        }
        TitleRecord title = repositories.titles().findById(titleId)
                .orElseThrow(() -> new IllegalArgumentException("Title not found: " + titleId));
        if (!title.enabled()) {
            throw new IllegalStateException("Title is disabled: " + titleId);
        }
        repositories.playerInfo().updateCurrentTitle(player.getUniqueId(), titleId);
        if (!plugin.hasPlaceholderApi()) {
            refreshPlayerListName(player);
        }
        return title;
    }

    public void unequipTitle(Player player) throws Exception {
        ensurePlayerRecord(player);
        repositories.playerInfo().updateCurrentTitle(player.getUniqueId(), null);
        if (!plugin.hasPlaceholderApi()) {
            refreshPlayerListName(player);
        }
    }

    public Component currentTitlePrefix(UUID playerId) throws Exception {
        Component title = currentTitleComponent(playerId);
        if (title.equals(Component.empty())) {
            return Component.empty();
        }
        String separator = plugin.getConfigService().config().display.titleSeparator;
        if (separator == null || separator.isEmpty()) {
            return title;
        }
        return Component.empty()
                .append(title)
                .append(textFormatter.deserialize(separator));
    }

    public String currentTitleLegacy(UUID playerId) throws Exception {
        return textFormatter.serializeLegacy(currentTitleComponent(playerId));
    }

    public String currentTitlePlain(UUID playerId) throws Exception {
        return textFormatter.serializePlain(currentTitleComponent(playerId));
    }

    public void refreshPlayerListName(Player player) throws Exception {
        Component prefix = currentTitlePrefix(player.getUniqueId());
        if (prefix.equals(Component.empty())) {
            player.playerListName(Component.text(player.getName()));
            return;
        }
        player.playerListName(Component.empty()
                .append(prefix)
                .append(Component.text(player.getName())));
    }

    private Component decorateTitle(Component title) {
        if (title.equals(Component.empty())) {
            return Component.empty();
        }
        String titlePrefix = plugin.getConfigService().config().display.titlePrefix;
        String titleSuffix = plugin.getConfigService().config().display.titleSuffix;
        Component decorated = Component.empty();
        if (titlePrefix != null && !titlePrefix.isEmpty()) {
            decorated = decorated.append(textFormatter.deserialize(titlePrefix));
        }
        decorated = decorated.append(title);
        if (titleSuffix != null && !titleSuffix.isEmpty()) {
            decorated = decorated.append(textFormatter.deserialize(titleSuffix));
        }
        return decorated;
    }

    public ExpiryCleanupResult pruneExpiredTitles() throws Exception {
        LocalDate today = LocalDate.now();
        Set<UUID> affectedPlayers = new HashSet<>();
        int removed = 0;
        for (PlayerTitleRecord record : repositories.playerTitles().findExpired(today)) {
            repositories.playerTitles().deleteById(record.id());
            affectedPlayers.add(record.playerUuid());
            removed++;
            Optional<PlayerInfoRecord> playerInfo = repositories.playerInfo().find(record.playerUuid());
            if (playerInfo.isPresent() && playerInfo.get().usingTitleId() != null && playerInfo.get().usingTitleId() == record.titleId()) {
                repositories.playerInfo().updateCurrentTitle(record.playerUuid(), null);
            }
        }
        return new ExpiryCleanupResult(removed, affectedPlayers);
    }

    private PlayerInfoRecord ensurePlayerRecord(UUID playerId, String playerName) throws Exception {
        return repositories.playerInfo().findOrCreate(
                playerId,
                playerName,
                plugin.getConfigService().config().economy.defaultBalance
        );
    }

    private DateParts mergeExpiry(DateParts existing, Integer days) {
        if (days == null || days < 0) {
            return DateParts.permanent();
        }
        if (existing != null && existing.isPermanent()) {
            return existing;
        }
        LocalDate base = LocalDate.now();
        if (existing != null && existing.asLocalDate() != null && existing.asLocalDate().isAfter(base)) {
            base = existing.asLocalDate();
        }
        return DateParts.from(base.plusDays(days));
    }

    private boolean isOwnershipActive(PlayerTitleRecord record, LocalDate today) {
        if (record.expireAt().isPermanent()) {
            return true;
        }
        LocalDate expireAt = record.expireAt().asLocalDate();
        return expireAt != null && !expireAt.isBefore(today);
    }

    public record ExpiryCleanupResult(int removedCount, Set<UUID> affectedPlayers) {
    }

    public record OwnedTitleView(TitleRecord title, PlayerTitleRecord ownership, boolean active) {
    }

    public record TitleGrantResult(TitleRecord title, PlayerTitleRecord ownership, boolean extendedExisting) {
    }
}
