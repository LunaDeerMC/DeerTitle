package cn.lunadeer.deertitle.utils.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import cn.lunadeer.deertitle.text.TextFormatter;
import net.kyori.adventure.text.Component;

public final class BukkitCompat {

    private static final Method PLAYER_LIST_NAME_COMPONENT = findMethod(Player.class, "playerListName", Component.class);
    private static final Method COMMAND_SENDER_SEND_MESSAGE_COMPONENT = findMethod(CommandSender.class, "sendMessage", Component.class);
    private static final Method CREATE_INVENTORY_COMPONENT = findMethod(Bukkit.class, "createInventory", InventoryHolder.class, int.class, Component.class);
    private static final Method ITEM_META_DISPLAY_NAME_COMPONENT = findMethod(ItemMeta.class, "displayName", Component.class);
    private static final Method ITEM_META_LORE_COMPONENT = findMethod(ItemMeta.class, "lore", List.class);

    private BukkitCompat() {
    }

    public static void sendMessage(CommandSender sender, Component message, TextFormatter textFormatter) {
        if (!invokeVoid(COMMAND_SENDER_SEND_MESSAGE_COMPONENT, sender, message)) {
            sender.sendMessage(textFormatter.serializeLegacy(message));
        }
    }

    public static void setPlayerListName(Player player, Component name, TextFormatter textFormatter) {
        if (!invokeVoid(PLAYER_LIST_NAME_COMPONENT, player, name)) {
            player.setPlayerListName(textFormatter.serializeLegacy(name));
        }
    }

    public static Inventory createInventory(InventoryHolder holder, int size, Component title, TextFormatter textFormatter) {
        Object inventory = invoke(CREATE_INVENTORY_COMPONENT, null, holder, size, title);
        if (inventory instanceof Inventory created) {
            return created;
        }
        return Bukkit.createInventory(holder, size, textFormatter.serializeLegacy(title));
    }

    public static void setDisplayName(ItemMeta itemMeta, Component name, TextFormatter textFormatter) {
        if (!invokeVoid(ITEM_META_DISPLAY_NAME_COMPONENT, itemMeta, name)) {
            itemMeta.setDisplayName(textFormatter.serializeLegacy(name));
        }
    }

    public static void setLore(ItemMeta itemMeta, List<Component> lore, TextFormatter textFormatter) {
        if (!invokeVoid(ITEM_META_LORE_COMPONENT, itemMeta, lore)) {
            List<String> legacyLore = new ArrayList<>(lore.size());
            for (Component line : lore) {
                legacyLore.add(textFormatter.serializeLegacy(line));
            }
            itemMeta.setLore(legacyLore);
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static boolean invokeVoid(Method method, Object target, Object... arguments) {
        if (method == null) {
            return false;
        }
        try {
            method.invoke(target, arguments);
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            return false;
        }
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            return null;
        }
    }
}