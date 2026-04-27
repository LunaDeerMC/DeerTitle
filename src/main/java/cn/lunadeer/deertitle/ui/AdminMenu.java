package cn.lunadeer.deertitle.ui;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class AdminMenu extends Menu {

    public AdminMenu(DeerTitlePlugin plugin, Player viewer) {
        super(plugin, viewer, 27, plugin.getConfigService().language().ui.adminTitle);
    }

    @Override
    protected void redraw() throws Exception {
        clearMenu();
        setButton(10, item(Material.BOOK, plugin.getConfigService().language().ui.adminBrowseTitlesButton, List.of(
                plugin.getConfigService().language().ui.adminBrowseTitlesLore
        )), event -> {
            try {
                new AdminTitlesMenu(plugin, viewer, 0).open();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        setButton(13, item(Material.EMERALD, plugin.getConfigService().language().ui.shopButton, List.of(
                plugin.getConfigService().language().ui.shopButtonLore
        )), event -> {
            try {
                new ShopMenu(plugin, viewer, 0).open();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        setButton(16, item(Material.NAME_TAG, plugin.getConfigService().language().ui.myTitlesButton, List.of(
                plugin.getConfigService().language().ui.myTitlesButtonLore
        )), event -> {
            try {
                new MyTitlesMenu(plugin, viewer, 0).open();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        setButton(26, item(Material.BARRIER, plugin.getConfigService().language().ui.closeButton, List.of()), event -> viewer.closeInventory());
        fillEmpty();
    }
}
