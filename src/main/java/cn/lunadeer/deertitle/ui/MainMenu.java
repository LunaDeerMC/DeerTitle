package cn.lunadeer.deertitle.ui;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class MainMenu extends Menu {

    public MainMenu(DeerTitlePlugin plugin, Player viewer) {
        super(plugin, viewer, 27, plugin.getConfigService().language().ui.mainTitle);
    }

    @Override
    protected void redraw() throws Exception {
        clearMenu();
        String currentTitle = plugin.getTitleService().currentTitlePlain(viewer.getUniqueId());
        setButton(10, item(Material.NAME_TAG, plugin.getConfigService().language().ui.myTitlesButton, List.of(
                plugin.getConfigService().language().ui.myTitlesButtonLore
        )), event -> {
            try {
                new MyTitlesMenu(plugin, viewer, 0).open();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        setButton(13, item(Material.PAPER, plugin.getConfigService().language().ui.currentTitleButton, List.of(
                currentTitle.isBlank() ? plugin.getConfigService().language().title.currentNone : plugin.getConfigService().language().title.currentLine.replace("{0}", currentTitle),
                plugin.getConfigService().language().ui.currentTitleButtonLore
        )), event -> {
            try {
                plugin.getTitleService().unequipTitle(viewer);
                redraw();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        setButton(16, item(Material.EMERALD, plugin.getConfigService().language().ui.shopButton, List.of(
                plugin.getConfigService().language().ui.shopButtonLore
        )), event -> {
            try {
                new ShopMenu(plugin, viewer, 0).open();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        if (viewer.hasPermission("deertitle.admin")) {
            setButton(22, item(Material.COMPARATOR, plugin.getConfigService().language().ui.adminButton, List.of(
                    plugin.getConfigService().language().ui.adminButtonLore
            )), event -> {
                try {
                    new AdminMenu(plugin, viewer).open();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
        setButton(26, item(Material.BARRIER, plugin.getConfigService().language().ui.closeButton, List.of()), event -> viewer.closeInventory());
        fillEmpty();
    }
}
