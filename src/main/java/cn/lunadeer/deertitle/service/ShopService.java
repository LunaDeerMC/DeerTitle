package cn.lunadeer.deertitle.service;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.database.model.DateParts;
import cn.lunadeer.deertitle.database.model.TitleRecord;
import cn.lunadeer.deertitle.database.model.TitleShopRecord;
import cn.lunadeer.deertitle.database.repository.RepositoryRegistry;
import cn.lunadeer.deertitle.economy.EconomyService;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class ShopService {

    private final DeerTitlePlugin plugin;
    private final RepositoryRegistry repositories;
    private final TitleService titleService;
    private final EconomyService economyService;

    public ShopService(DeerTitlePlugin plugin, RepositoryRegistry repositories, TitleService titleService, EconomyService economyService) {
        this.plugin = plugin;
        this.repositories = repositories;
        this.titleService = titleService;
        this.economyService = economyService;
    }

    public List<ShopEntryView> listEntries() throws Exception {
        List<ShopEntryView> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (TitleShopRecord record : repositories.titleShop().findAll()) {
            TitleRecord title = repositories.titles().findById(record.titleId()).orElse(null);
            if (title == null) {
                continue;
            }
            result.add(new ShopEntryView(record, title, isSaleActive(record, today)));
        }
        return result;
    }

    public TitleShopRecord saveOffer(int titleId, double price, int days, int amount, LocalDate saleEndAt) throws Exception {
        TitleRecord title = repositories.titles().findById(titleId)
                .orElseThrow(() -> new IllegalArgumentException("Title not found: " + titleId));
        TitleShopRecord existing = repositories.titleShop().findByTitleId(title.id()).orElse(null);
        TitleShopRecord updated = new TitleShopRecord(
                existing == null ? 0 : existing.id(),
                title.id(),
                days,
                amount,
                DateParts.from(saleEndAt),
                price
        );
        return repositories.titleShop().save(updated);
    }

    public void clearOffer(int titleId) throws Exception {
        repositories.titleShop().findByTitleId(titleId).ifPresent(record -> {
            try {
                repositories.titleShop().delete(record.id());
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    public PurchaseResult purchase(Player player, int offerId) throws Exception {
        TitleShopRecord offer = repositories.titleShop().findById(offerId)
                .orElseThrow(() -> new PurchaseFailedException(PurchaseFailureReason.UNAVAILABLE));
        TitleRecord title = repositories.titles().findById(offer.titleId())
                .orElseThrow(() -> new PurchaseFailedException(PurchaseFailureReason.UNAVAILABLE));
        if (!title.enabled()) {
            throw new PurchaseFailedException(PurchaseFailureReason.UNAVAILABLE);
        }
        if (!isSaleActive(offer, LocalDate.now())) {
            throw new PurchaseFailedException(PurchaseFailureReason.EXPIRED);
        }
        if (offer.amount() == 0) {
            throw new PurchaseFailedException(PurchaseFailureReason.OUT_OF_STOCK);
        }
        if (!economyService.withdraw(player, offer.price())) {
            throw new PurchaseFailedException(PurchaseFailureReason.INSUFFICIENT_FUNDS);
        }
        TitleService.TitleGrantResult grant = titleService.grantTitle(player.getUniqueId(), player.getName(), title.id(), offer.days() < 0 ? null : offer.days());
        if (offer.amount() > 0) {
            repositories.titleShop().decrementAmount(offer.id());
        }
        return new PurchaseResult(offer, title, grant);
    }

    private boolean isSaleActive(TitleShopRecord offer, LocalDate today) {
        if (offer.saleEndAt().isPermanent()) {
            return true;
        }
        LocalDate saleEndAt = offer.saleEndAt().asLocalDate();
        return saleEndAt != null && !saleEndAt.isBefore(today);
    }

    public record ShopEntryView(TitleShopRecord offer, TitleRecord title, boolean active) {
    }

    public record PurchaseResult(TitleShopRecord offer, TitleRecord title, TitleService.TitleGrantResult grant) {
    }

    public enum PurchaseFailureReason {
        UNAVAILABLE,
        EXPIRED,
        OUT_OF_STOCK,
        INSUFFICIENT_FUNDS
    }

    public static final class PurchaseFailedException extends Exception {

        private final PurchaseFailureReason reason;

        public PurchaseFailedException(PurchaseFailureReason reason) {
            super(reason.name());
            this.reason = reason;
        }

        public PurchaseFailureReason reason() {
            return reason;
        }
    }
}
