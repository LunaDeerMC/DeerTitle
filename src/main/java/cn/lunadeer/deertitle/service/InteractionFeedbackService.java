package cn.lunadeer.deertitle.service;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.utils.compat.BukkitCompat;

public final class InteractionFeedbackService {

    private final DeerTitlePlugin plugin;
    private final Set<String> invalidSounds = new HashSet<>();

    public InteractionFeedbackService(DeerTitlePlugin plugin) {
        this.plugin = plugin;
    }

    public void onTitleEquipped(Player player, String titleName) {
        send(player, plugin.getConfigService().language().title.equipped, titleName);
        playConfiguredSound(player, plugin.getConfigService().config().feedback.equipSuccessSound);
    }

    public void onTitleRemoved(Player player) {
        send(player, plugin.getConfigService().language().title.removed);
        playConfiguredSound(player, plugin.getConfigService().config().feedback.removeSuccessSound);
    }

    public void onPurchaseSuccess(Player player, String titleName) {
        send(player, plugin.getConfigService().language().shop.purchased, titleName);
        playConfiguredSound(player, plugin.getConfigService().config().feedback.purchaseSuccessSound);
    }

    public void onPurchaseFailure(Player player, ShopService.PurchaseFailureReason reason) {
        switch (reason) {
            case EXPIRED -> onFailure(player, plugin.getConfigService().language().shop.saleExpired);
            case OUT_OF_STOCK -> onFailure(player, plugin.getConfigService().language().shop.outOfStock);
            case INSUFFICIENT_FUNDS -> onFailure(player, plugin.getConfigService().language().shop.insufficientFunds);
            case UNAVAILABLE -> onFailure(player, plugin.getConfigService().language().shop.unavailable);
        }
    }

    public void onTitleCardUsed(Player player, String titleName, boolean consumed) {
        send(player, plugin.getConfigService().language().card.cardUsed, titleName);
        if (consumed) {
            send(player, plugin.getConfigService().language().card.cardConsumed);
        }
        playConfiguredSound(player, plugin.getConfigService().config().feedback.cardUseSuccessSound);
    }

    public void onFailure(Player player, String template, Object... arguments) {
        send(player, template, arguments);
        playConfiguredSound(player, plugin.getConfigService().config().feedback.failureSound);
    }

    private void send(Player player, String template, Object... arguments) {
        BukkitCompat.sendMessage(player, plugin.getTextFormatter().deserializeTemplate(template, arguments), plugin.getTextFormatter());
    }

    private void playConfiguredSound(Player player, String soundName) {
        var feedbackConfig = plugin.getConfigService().config().feedback;
        if (!feedbackConfig.enableSounds || soundName == null || soundName.isBlank()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), Sound.valueOf(soundName), feedbackConfig.soundVolume, feedbackConfig.soundPitch);
        } catch (IllegalArgumentException exception) {
            if (invalidSounds.add(soundName)) {
                plugin.getLogger().warning("Invalid feedback sound configured: " + soundName);
            }
        }
    }
}