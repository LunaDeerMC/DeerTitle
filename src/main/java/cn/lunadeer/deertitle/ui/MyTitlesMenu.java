package cn.lunadeer.deertitle.ui;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.service.TitleService;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.List;

public final class MyTitlesMenu extends Menu {

    private final int page;

    public MyTitlesMenu(DeerTitlePlugin plugin, Player viewer, int page) {
        super(plugin, viewer, 54, plugin.getConfigService().language().ui.myTitlesTitle);
        this.page = Math.max(0, page);
    }

    @Override
    protected void redraw() throws Exception {
        clearMenu();
        List<TitleService.OwnedTitleView> titles = plugin.getTitleService().ownedTitles(viewer.getUniqueId());
        int pageSize = 45;
        int start = page * pageSize;
        int end = Math.min(titles.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            TitleService.OwnedTitleView view = titles.get(index);
            int slot = index - start;
            String expiry = view.ownership().expireAt().isPermanent() ? plugin.getConfigService().language().shop.unlimited : view.ownership().expireAt().asLocalDate().toString();
            setButton(slot, item(Material.NAME_TAG, view.title().title(), List.of(
                    view.title().description().isBlank() ? plugin.getConfigService().language().ui.noDescription : view.title().description(),
                    plugin.getConfigService().language().ui.labelExpires.replace("{0}", expiry),
                    view.active() ? plugin.getConfigService().language().ui.myTitlesActiveLore : plugin.getConfigService().language().ui.myTitlesExpiredLore
            )), event -> {
                try {
                    if (view.active()) {
                        plugin.getTitleService().equipTitle(viewer, view.title().id());
                        plugin.getInteractionFeedbackService().onTitleEquipped(viewer, view.title().title());
                    } else {
                        plugin.getInteractionFeedbackService().onFailure(viewer, plugin.getConfigService().language().title.expired, view.title().title());
                    }
                    redraw();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
        setButton(45, item(Material.ARROW, plugin.getConfigService().language().ui.backButton, List.of()), event -> {
            try {
                new MainMenu(plugin, viewer).open();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        if (page > 0) {
            setButton(48, item(Material.SPECTRAL_ARROW, plugin.getConfigService().language().ui.prevPageButton, List.of()), event -> {
                try {
                    new MyTitlesMenu(plugin, viewer, page - 1).open();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
        if (end < titles.size()) {
            setButton(50, item(Material.ARROW, plugin.getConfigService().language().ui.nextPageButton, List.of()), event -> {
                try {
                    new MyTitlesMenu(plugin, viewer, page + 1).open();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
        setStaticItem(53, item(Material.PAPER, plugin.getConfigService().language().ui.pageInfo.replace("{0}", Integer.toString(page + 1)), List.of()));
        fillEmpty();
    }
}
