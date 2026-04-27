package cn.lunadeer.deertitle.ui;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.service.ShopService;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class ShopMenu extends Menu {

    private final int page;

    public ShopMenu(DeerTitlePlugin plugin, Player viewer, int page) {
        super(plugin, viewer, 54, plugin.getConfigService().language().ui.shopTitle);
        this.page = Math.max(0, page);
    }

    @Override
    protected void redraw() throws Exception {
        clearMenu();
        List<ShopService.ShopEntryView> entries = plugin.getShopService().listEntries();
        int pageSize = 45;
        int start = page * pageSize;
        int end = Math.min(entries.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            ShopService.ShopEntryView entry = entries.get(index);
            int slot = index - start;
            String amount = entry.offer().amount() < 0 ? plugin.getConfigService().language().shop.unlimited : Integer.toString(entry.offer().amount());
            String days = entry.offer().days() < 0 ? plugin.getConfigService().language().shop.unlimited : Integer.toString(entry.offer().days());
            String saleEnd = entry.offer().saleEndAt().isPermanent() ? plugin.getConfigService().language().shop.unlimited : entry.offer().saleEndAt().asLocalDate().toString();
            setButton(slot, item(Material.EMERALD, entry.title().title(), List.of(
                    entry.title().description().isBlank() ? plugin.getConfigService().language().ui.noDescription : entry.title().description(),
                    plugin.getConfigService().language().ui.labelPrice.replace("{0}", plugin.getEconomyService().format(entry.offer().price())),
                    plugin.getConfigService().language().ui.labelStock.replace("{0}", amount),
                    plugin.getConfigService().language().ui.labelDays.replace("{0}", days),
                    plugin.getConfigService().language().ui.labelSaleEnd.replace("{0}", saleEnd),
                    entry.active() ? plugin.getConfigService().language().ui.shopBuyLore : plugin.getConfigService().language().ui.shopExpiredLore
            )), event -> {
                try {
                    if (entry.active()) {
                        plugin.getShopService().purchase(viewer, entry.offer().id());
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
                    new ShopMenu(plugin, viewer, page - 1).open();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
        if (end < entries.size()) {
            setButton(50, item(Material.ARROW, plugin.getConfigService().language().ui.nextPageButton, List.of()), event -> {
                try {
                    new ShopMenu(plugin, viewer, page + 1).open();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
        setStaticItem(53, item(Material.PAPER, plugin.getConfigService().language().ui.pageInfo.replace("{0}", Integer.toString(page + 1)), List.of()));
        fillEmpty();
    }
}
