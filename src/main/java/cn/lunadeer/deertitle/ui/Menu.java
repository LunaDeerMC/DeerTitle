package cn.lunadeer.deertitle.ui;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class Menu implements InventoryHolder {

    protected final DeerTitlePlugin plugin;
    protected final Player viewer;
    protected final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(DeerTitlePlugin plugin, Player viewer, int size, String title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, size, plugin.getTextFormatter().deserialize(title));
    }

    public void open() throws Exception {
        redraw();
        viewer.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) throws Exception {
        if (event.getRawSlot() < 0 || event.getRawSlot() >= inventory.getSize()) {
            return;
        }
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    protected abstract void redraw() throws Exception;

    protected void clearMenu() {
        inventory.clear();
        actions.clear();
    }

    protected void setButton(int slot, ItemStack itemStack, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, itemStack);
        actions.put(slot, action);
    }

    protected void setStaticItem(int slot, ItemStack itemStack) {
        inventory.setItem(slot, itemStack);
    }

    protected ItemStack item(Material material, String name, List<String> lore) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(plugin.getTextFormatter().deserialize(name));
        List<Component> loreComponents = new ArrayList<>();
        if (lore != null) {
            for (String line : lore) {
                loreComponents.add(plugin.getTextFormatter().deserialize(line));
            }
        }
        meta.lore(loreComponents);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    protected void fillEmpty() {
        if (!plugin.getConfigService().config().ui.fillEmptySlots) {
            return;
        }
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, plugin.getConfigService().language().ui.fillerName, List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }
}
