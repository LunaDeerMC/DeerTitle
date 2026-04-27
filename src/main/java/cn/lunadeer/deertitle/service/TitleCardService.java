package cn.lunadeer.deertitle.service;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.database.model.TitleRecord;
import cn.lunadeer.deertitle.database.repository.RepositoryRegistry;
import cn.lunadeer.deertitle.text.TextFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class TitleCardService {

    private final DeerTitlePlugin plugin;
    private final RepositoryRegistry repositories;
    private final TitleService titleService;
    private final TextFormatter textFormatter;
    private final NamespacedKey titleIdKey;
    private final NamespacedKey daysKey;

    public TitleCardService(DeerTitlePlugin plugin, RepositoryRegistry repositories, TitleService titleService, TextFormatter textFormatter) {
        this.plugin = plugin;
        this.repositories = repositories;
        this.titleService = titleService;
        this.textFormatter = textFormatter;
        this.titleIdKey = new NamespacedKey(plugin, "title-card-title-id");
        this.daysKey = new NamespacedKey(plugin, "title-card-days");
    }

    public ItemStack createCard(int titleId, Integer days) throws Exception {
        TitleRecord title = repositories.titles().findById(titleId)
                .orElseThrow(() -> new IllegalArgumentException("Title not found: " + titleId));
        Material material = Material.matchMaterial(plugin.getConfigService().config().card.material);
        if (material == null) {
            material = Material.NAME_TAG;
        }
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(textFormatter.deserializeTemplate(plugin.getConfigService().language().card.itemName, title.title()));
        itemMeta.lore(List.of(
                textFormatter.deserializeTemplate(plugin.getConfigService().language().card.itemTitleLine, title.title()),
                days == null || days < 0
                        ? textFormatter.deserialize(plugin.getConfigService().language().card.itemDurationPermanent)
                        : textFormatter.deserializeTemplate(plugin.getConfigService().language().card.itemDurationLine, days)
        ));
        itemMeta.getPersistentDataContainer().set(titleIdKey, PersistentDataType.INTEGER, title.id());
        itemMeta.getPersistentDataContainer().set(daysKey, PersistentDataType.INTEGER, days == null ? -1 : days);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public boolean tryUseTitleCard(Player player) throws Exception {
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        if (itemStack.getType().isAir() || !isTitleCard(itemStack)) {
            return false;
        }
        if (plugin.getConfigService().config().card.requireSneakToUse && !player.isSneaking()) {
            return false;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        Integer titleId = itemMeta.getPersistentDataContainer().get(titleIdKey, PersistentDataType.INTEGER);
        Integer days = itemMeta.getPersistentDataContainer().get(daysKey, PersistentDataType.INTEGER);
        if (titleId == null) {
            player.sendMessage(textFormatter.deserialize(plugin.getConfigService().language().card.invalidCard));
            return false;
        }
        TitleService.TitleGrantResult grant = titleService.grantTitle(player.getUniqueId(), player.getName(), titleId, days == null || days < 0 ? null : days);
        titleService.equipTitle(player, titleId);
        player.sendMessage(textFormatter.deserializeTemplate(plugin.getConfigService().language().card.cardUsed, grant.title().title()));
        if (plugin.getConfigService().config().card.consumeOnUse) {
            int amount = itemStack.getAmount();
            if (amount <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                itemStack.setAmount(amount - 1);
            }
            player.sendMessage(textFormatter.deserialize(plugin.getConfigService().language().card.cardConsumed));
        }
        return true;
    }

    public boolean isTitleCard(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        return itemMeta.getPersistentDataContainer().has(titleIdKey, PersistentDataType.INTEGER);
    }
}
