package cn.lunadeer.deertitle.ui;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.database.model.TitleRecord;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class AdminTitlesMenu extends Menu {

    private final int page;

    public AdminTitlesMenu(DeerTitlePlugin plugin, Player viewer, int page) {
        super(plugin, viewer, 54, plugin.getConfigService().language().ui.adminTitle);
        this.page = Math.max(0, page);
    }

    @Override
    protected void redraw() throws Exception {
        clearMenu();
        List<TitleRecord> titles = plugin.getTitleService().allTitles(true);
        int pageSize = 45;
        int start = page * pageSize;
        int end = Math.min(titles.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            TitleRecord title = titles.get(index);
            int slot = index - start;
            setButton(slot, item(Material.NAME_TAG, title.title(), List.of(
                    title.description().isBlank() ? plugin.getConfigService().language().ui.noDescription : title.description(),
                    plugin.getConfigService().language().ui.labelId.replace("{0}", Integer.toString(title.id())),
                    plugin.getConfigService().language().ui.labelEnabled.replace("{0}", Boolean.toString(title.enabled())),
                    plugin.getConfigService().language().ui.adminTitleEntryLoreLine1,
                    plugin.getConfigService().language().ui.adminTitleEntryLoreLine2,
                    plugin.getConfigService().language().ui.adminTitleEntryLoreLine3
            )), event -> {
                try {
                    if (event.isShiftClick()) {
                        plugin.getTitleService().setEnabled(title.id(), !title.enabled());
                    } else if (event.isLeftClick()) {
                        plugin.getTitleService().grantTitle(viewer.getUniqueId(), viewer.getName(), title.id(), null);
                    } else if (event.isRightClick()) {
                        viewer.getInventory().addItem(plugin.getTitleCardService().createCard(title.id(), null));
                    }
                    redraw();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
        setButton(45, item(Material.ARROW, plugin.getConfigService().language().ui.backButton, List.of()), event -> {
            try {
                new AdminMenu(plugin, viewer).open();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        if (page > 0) {
            setButton(48, item(Material.SPECTRAL_ARROW, plugin.getConfigService().language().ui.prevPageButton, List.of()), event -> {
                try {
                    new AdminTitlesMenu(plugin, viewer, page - 1).open();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
        if (end < titles.size()) {
            setButton(50, item(Material.ARROW, plugin.getConfigService().language().ui.nextPageButton, List.of()), event -> {
                try {
                    new AdminTitlesMenu(plugin, viewer, page + 1).open();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
        setStaticItem(53, item(Material.PAPER, plugin.getConfigService().language().ui.pageInfo.replace("{0}", Integer.toString(page + 1)), List.of()));
        fillEmpty();
    }
}
