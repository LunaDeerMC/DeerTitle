package cn.lunadeer.deertitle.ui;

import cn.lunadeer.deertitle.domain.Currency;
import cn.lunadeer.deertitle.domain.ErrorCode;
import cn.lunadeer.deertitle.domain.ListingDraft;
import cn.lunadeer.deertitle.domain.OwnedTitle;
import cn.lunadeer.deertitle.domain.PlayerProfile;
import cn.lunadeer.deertitle.domain.ShopListing;
import cn.lunadeer.deertitle.domain.Title;
import cn.lunadeer.deertitle.domain.TitleDraft;
import cn.lunadeer.deertitle.domain.TitleException;
import cn.lunadeer.deertitle.text.TitleText;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/** Native Paper dialogs. Each shown page is one consumable, player-bound capability. */
public final class DialogUi implements AutoCloseable {
    private static final String USE = "deertitle.use";
    private static final String ADMIN = "deertitle.admin";
    private static final String CUSTOM = "deertitle.custom";
    private static final int PAGE_SIZE = 8;
    private static final int BUTTON_WIDTH = 170;
    private static final List<String> STYLES = List.of("solid", "gradient", "rainbow");
    private static final List<ColorChoice> COLORS = List.of(
        new ColorChoice("白色", "#ffffff"), new ColorChoice("灰色", "#aaaaaa"),
        new ColorChoice("红色", "#ff5555"), new ColorChoice("橙色", "#ffaa00"),
        new ColorChoice("黄色", "#ffff55"), new ColorChoice("绿色", "#55ff55"),
        new ColorChoice("薄荷", "#77d9c7"), new ColorChoice("青色", "#55ffff"),
        new ColorChoice("蓝色", "#5599ff"), new ColorChoice("紫色", "#a5a2ff"),
        new ColorChoice("粉色", "#ff77cc"), new ColorChoice("玫红", "#ff55ff"));
    private final UiAccess access;
    private final Map<UUID, UUID> sessions = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public DialogUi(UiAccess access) { this.access = Objects.requireNonNull(access); }

    public void openMain(Player player) {
        UUID id = player.getUniqueId();
        load(player, USE, () -> new Home(access.store().profile(id), access.store().equippedTitle(id)),
            (p, home) -> {
                Screen screen = begin(p);
                List<DialogBody> body = new ArrayList<>();
                body.add(body(home.equipped().map(this::title).orElse(Component.text("未佩戴称号", NamedTextColor.GRAY))));
                body.add(body(balance(home.profile())));
                List<ActionButton> buttons = new ArrayList<>();
                buttons.add(button(screen, "称号背包", USE, (who, r) -> openInventory(who)));
                buttons.add(button(screen, "称号商店", USE, (who, r) -> openShop(who)));
                if (p.hasPermission(CUSTOM)) buttons.add(button(screen, "自定义称号", CUSTOM, (who, r) -> openCustom(who)));
                if (p.hasPermission(ADMIN)) buttons.add(button(screen, "商店管理", ADMIN, (who, r) -> openAdminShop(who)));
                show(p, "DeerTitle", body, List.of(), buttons, close(screen), 2);
            });
    }

    public void openInventory(Player player) { openInventory(player, 0); }

    private void openInventory(Player player, int page) {
        UUID id = player.getUniqueId();
        load(player, USE, () -> new Backpack(access.store().profile(id), access.store().inventory(id)),
            (p, backpack) -> {
                Screen screen = begin(p);
                List<OwnedTitle> all = backpack.titles();
                int index = page(page, all.size());
                List<ActionButton> buttons = new ArrayList<>();
                for (OwnedTitle owned : slice(all, index)) {
                    boolean equipped = Objects.equals(backpack.profile().equippedTitleId(), owned.title().id());
                    Component label = (equipped ? Component.text("✓ ", NamedTextColor.GREEN) : Component.empty())
                        .append(title(owned.title()));
                    if (owned.expired(Instant.now())) label = label.append(Component.text(" · 已到期", NamedTextColor.GRAY));
                    buttons.add(button(screen, label, ownershipInfo(owned), USE,
                        (who, r) -> openOwned(who, owned.title().id())));
                }
                pagination(screen, buttons, index, all.size(), (who, next) -> openInventory(who, next), USE);
                if (backpack.profile().equippedTitleId() != null) {
                    buttons.add(button(screen, "卸下称号", USE, (who, r) -> {
                        UUID owner = who.getUniqueId();
                        mutate(who, USE, () -> { access.store().unequip(owner); return null; },
                            ignored -> openInventory(who, index));
                    }));
                }
                List<DialogBody> body = all.isEmpty() ? List.of(body(Component.text("背包为空", NamedTextColor.GRAY))) : List.of();
                show(p, pageTitle("称号背包", index, all.size()), body, List.of(), buttons,
                    back(screen, (who, r) -> openMain(who)), 2);
            });
    }

    private void openOwned(Player player, long titleId) {
        UUID id = player.getUniqueId();
        load(player, USE, () -> {
            OwnedTitle owned = access.store().inventory(id).stream().filter(t -> t.title().id() == titleId)
                .findFirst().orElseThrow(() -> new TitleException(ErrorCode.NOT_OWNED));
            return new OwnedView(access.store().profile(id), owned);
        }, (p, view) -> {
            Screen screen = begin(p);
            OwnedTitle owned = view.owned();
            List<DialogBody> body = new ArrayList<>(List.of(body(title(owned.title())), body(ownershipInfo(owned))));
            List<ActionButton> buttons = new ArrayList<>();
            boolean equipped = Objects.equals(view.profile().equippedTitleId(), titleId);
            if (equipped || (!owned.expired(Instant.now()) && owned.title().enabled())) {
                buttons.add(button(screen, equipped ? "卸下" : "佩戴", USE, (who, r) -> {
                    UUID owner = who.getUniqueId();
                    mutate(who, USE, () -> {
                        if (equipped) access.store().unequip(owner); else access.store().equip(owner, titleId);
                        return null;
                    }, ignored -> openInventory(who));
                }));
            }
            if (p.hasPermission(CUSTOM) && owned.title().custom() && id.equals(owned.title().creator()) && !owned.expired(Instant.now())) {
                buttons.add(button(screen, "编辑 · 1 次", CUSTOM, (who, r) -> openExistingEditor(who, titleId, false)));
            }
            show(p, "称号", body, List.of(), buttons, back(screen, (who, r) -> openInventory(who)), 2);
        });
    }

    public void openShop(Player player) { openShop(player, 0); }

    private void openShop(Player player, int requestedPage) {
        load(player, USE, () -> access.store().shop(false).stream()
            .filter(listing -> listing.currency() == access.currency()).toList(), (p, listings) -> {
                Screen screen = begin(p);
                int page = page(requestedPage, listings.size());
                List<ActionButton> buttons = new ArrayList<>();
                for (ShopListing listing : slice(listings, page)) {
                    Component label = title(listing.title()).append(Component.text(" · " + amount(listing.price())
                        + " " + access.currencyLabel(), NamedTextColor.GRAY));
                    buttons.add(button(screen, label, saleInfo(listing), USE,
                        (who, r) -> openPurchase(who, listing.id())));
                }
                pagination(screen, buttons, page, listings.size(), (who, next) -> openShop(who, next), USE);
                List<DialogBody> body = listings.isEmpty() ? List.of(body(Component.text("暂无在售称号", NamedTextColor.GRAY))) : List.of();
                show(p, pageTitle("称号商店", page, listings.size()), body, List.of(), buttons,
                    back(screen, (who, r) -> openMain(who)), 2);
            });
    }

    private void openPurchase(Player player, long listingId) {
        load(player, USE, () -> access.store().listing(listingId)
            .orElseThrow(() -> new TitleException(ErrorCode.NOT_FOUND)), (p, listing) -> {
                if (listing.currency() != access.currency() || !listing.available(Instant.now())) {
                    access.error(p, "这个称号已停止销售。");
                    openShop(p);
                    return;
                }
                Screen screen = begin(p);
                show(p, "购买称号", List.of(body(title(listing.title())), body(saleInfo(listing))), List.of(),
                    List.of(button(screen, "购买 · " + amount(listing.price()) + " " + access.currencyLabel(), USE,
                        (who, r) -> access.purchase(who, listing.id(), listing.version()))),
                    back(screen, (who, r) -> openShop(who)), 1);
            });
    }

    public void openCustom(Player player) { openCustom(player, 0); }

    private void openCustom(Player player, int requestedPage) {
        UUID id = player.getUniqueId();
        load(player, CUSTOM, () -> new Backpack(access.store().profile(id), access.store().inventory(id).stream()
            .filter(t -> t.title().custom() && id.equals(t.title().creator()) && !t.expired(Instant.now())).toList()),
            (p, backpack) -> {
                Screen screen = begin(p);
                int page = page(requestedPage, backpack.titles().size());
                List<ActionButton> buttons = new ArrayList<>();
                buttons.add(button(screen, "创建称号 · 1 次", CUSTOM, (who, r) -> openEditorForNewCustom(who)));
                buttons.add(button(screen, "购买次数", CUSTOM, (who, r) -> openCreditPurchase(who)));
                for (OwnedTitle owned : slice(backpack.titles(), page)) {
                    buttons.add(button(screen, title(owned.title()), Component.text("编辑 · 1 次"), CUSTOM,
                        (who, r) -> openExistingEditor(who, owned.title().id(), false)));
                }
                pagination(screen, buttons, page, backpack.titles().size(), (who, next) -> openCustom(who, next), CUSTOM);
                show(p, pageTitle("自定义称号", page, backpack.titles().size()),
                    List.of(body(Component.text("剩余 " + backpack.profile().customCredits() + " 次"))), List.of(), buttons,
                    back(screen, (who, r) -> openMain(who)), 2);
            });
    }

    private void openCreditPurchase(Player player) {
        if (!allowed(player, CUSTOM)) return;
        Screen screen = begin(player);
        show(player, "购买自定义次数",
            List.of(body(Component.text(amount(access.creditPrice()) + " " + access.currencyLabel() + " / 次"))),
            List.of(input("count", "次数", "1", 5)),
            List.of(button(screen, "继续", CUSTOM,
                (who, response) -> openCreditConfirmation(who, FormValues.count(response.getText("count"))))),
            back(screen, CUSTOM, (who, r) -> openCustom(who)), 1);
    }

    private void openCreditConfirmation(Player player, int count) {
        if (!allowed(player, CUSTOM)) return;
        Screen screen = begin(player);
        BigDecimal total = access.creditPrice().multiply(BigDecimal.valueOf(count));
        show(player, "确认购买", List.of(body(Component.text(count + " 次自定义"))), List.of(),
            List.of(button(screen, "购买 · " + amount(total) + " " + access.currencyLabel(), CUSTOM,
                (who, r) -> access.buyCredits(who, count))),
            back(screen, CUSTOM, (who, r) -> openCreditPurchase(who)), 1);
    }

    public void openEditorForNewCustom(Player player) {
        UUID id = player.getUniqueId();
        load(player, CUSTOM, () -> access.store().profile(id), (p, profile) -> {
            if (profile.customCredits() < 1) {
                access.error(p, "自定义次数不足。");
                openCreditPurchase(p);
                return;
            }
            openEditor(p, EditorDraft.create(false));
        });
    }

    public void openEditorForNewAdmin(Player player) {
        if (allowed(player, ADMIN)) openEditor(player, EditorDraft.create(true));
    }

    private void openExistingEditor(Player player, long id, boolean admin) {
        UUID owner = player.getUniqueId();
        load(player, admin ? ADMIN : CUSTOM, () -> {
            Title title = access.store().title(id).orElseThrow(() -> new TitleException(ErrorCode.NOT_FOUND));
            if (admin ? title.custom() : !title.custom() || !owner.equals(title.creator())) {
                throw new TitleException(ErrorCode.NOT_OWNED);
            }
            if (!admin && access.store().profile(owner).customCredits() < 1) {
                throw new TitleException(ErrorCode.INSUFFICIENT_CREDITS);
            }
            return title;
        }, (p, title) -> openEditor(p, EditorDraft.edit(title, admin)));
    }

    private void openEditor(Player player, EditorDraft draft) {
        String permission = draft.admin() ? ADMIN : CUSTOM;
        if (!allowed(player, permission)) return;
        Screen screen = begin(player);
        List<DialogBody> body = new ArrayList<>();
        try {
            body.add(body(TitleText.parse(validate(draft.source()))));
        } catch (IllegalArgumentException exception) {
            body.add(body(Component.text(exception.getMessage(), NamedTextColor.RED)));
        }
        List<DialogInput> inputs = new ArrayList<>();
        if (draft.admin()) inputs.add(input("name", "名称", draft.name(), 80));
        boolean advanced = draft.mode().equals("minimessage");
        if (advanced) {
            inputs.add(DialogInput.text("source", Component.text("MiniMessage"))
                .initial(draft.source()).maxLength(access.maxTitleSourceLength()).width(340)
                .multiline(TextDialogInput.MultilineOptions.create(4, 64)).build());
        } else {
            inputs.add(input("text", "文字", draft.text(), Math.min(512, access.maxTitleSourceLength())));
            inputs.add(DialogInput.singleOption("style", Component.text("配色"), List.of(
                option("solid", "纯色", draft.mode()), option("gradient", "渐变", draft.mode()),
                option("rainbow", "彩虹", draft.mode()))).width(300).build());
            inputs.add(DialogInput.bool("bold", Component.text("粗体")).initial(draft.bold()).build());
            inputs.add(DialogInput.bool("italic", Component.text("斜体")).initial(draft.italic()).build());
            inputs.add(DialogInput.bool("underline", Component.text("下划线")).initial(draft.underline()).build());
            inputs.add(DialogInput.bool("strike", Component.text("删除线")).initial(draft.strike()).build());
        }
        if (draft.admin()) inputs.add(DialogInput.bool("enabled", Component.text("启用称号")).initial(draft.enabled()).build());
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(screen, "预览", permission, (p, response) -> editAction(p, draft, response, next -> openEditor(p, next))));
        buttons.add(button(screen, draft.admin() ? "保存" : "保存 · 1 次", permission,
            (p, response) -> editAction(p, draft, response, next -> saveEditor(p, next))));
        if (!advanced) {
            buttons.add(button(screen, colorLabel("起始颜色", draft.startColor()), null, permission,
                (p, response) -> editAction(p, draft, response, next -> openColorPicker(p, next, true))));
            buttons.add(button(screen, colorLabel("结束颜色", draft.endColor()), null, permission,
                (p, response) -> editAction(p, draft, response, next -> openColorPicker(p, next, false))));
        }
        buttons.add(button(screen, advanced ? "配色编辑" : "MiniMessage", permission,
            (p, response) -> editAction(p, draft, response,
                next -> openEditor(p, next.mode(advanced ? "gradient" : "minimessage")))));
        show(player, "称号编辑器", body, inputs, buttons,
            back(screen, permission, (p, r) -> { if (draft.admin()) openAdminShop(p); else openCustom(p); }), 2);
    }

    private void editAction(Player player, EditorDraft current, DialogResponseView response, Consumer<EditorDraft> next) {
        try {
            next.accept(readEditor(current, response));
        } catch (IllegalArgumentException exception) {
            access.error(player, exception.getMessage());
            openEditor(player, current);
        }
    }

    private EditorDraft readEditor(EditorDraft old, DialogResponseView response) {
        String name = old.admin() ? FormValues.text(response.getText("name"), 80, "名称").trim() : old.name();
        if (name.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("名称必须是单行文字。");
        boolean enabled = old.admin() ? FormValues.bool(response.getBoolean("enabled"), "启用称号") : old.enabled();
        if (old.mode().equals("minimessage")) {
            String source = FormValues.text(response.getText("source"), access.maxTitleSourceLength(), "MiniMessage");
            return new EditorDraft(old.titleId(), old.version(), old.admin(), name, source, old.text(), old.mode(),
                old.startColor(), old.endColor(), old.bold(), old.italic(), old.underline(), old.strike(), old.description(), enabled);
        }
        String text = FormValues.text(response.getText("text"), Math.min(512, access.maxTitleSourceLength()), "文字");
        String mode = FormValues.choice(response.getText("style"), STYLES, "配色");
        String start = old.startColor();
        String end = old.endColor();
        boolean bold = FormValues.bool(response.getBoolean("bold"), "粗体");
        boolean italic = FormValues.bool(response.getBoolean("italic"), "斜体");
        boolean underline = FormValues.bool(response.getBoolean("underline"), "下划线");
        boolean strike = FormValues.bool(response.getBoolean("strike"), "删除线");
        String source = TitleText.compose(text, mode, start, end, bold, italic, underline, strike);
        return new EditorDraft(old.titleId(), old.version(), old.admin(), name, source, text, mode, start, end,
            bold, italic, underline, strike, old.description(), enabled);
    }

    private void openColorPicker(Player player, EditorDraft draft, boolean start) {
        String permission = draft.admin() ? ADMIN : CUSTOM;
        if (!allowed(player, permission)) return;
        Screen screen = begin(player);
        List<ActionButton> buttons = new ArrayList<>();
        for (ColorChoice choice : COLORS) {
            buttons.add(button(screen, colorLabel("● " + choice.label(), choice.hex()), null, permission,
                (p, response) -> chooseColor(p, draft, start, choice.hex())));
        }
        buttons.add(button(screen, "使用颜色值", permission, (p, response) -> {
            String color = FormValues.text(response.getText("hex"), 32, "颜色值").trim();
            chooseColor(p, draft, start, color);
        }));
        String initial = start ? draft.startColor() : draft.endColor();
        show(player, start ? "起始颜色" : "结束颜色", List.of(),
            List.of(input("hex", "颜色值", initial, 32)), buttons,
            back(screen, permission, (p, response) -> openEditor(p, draft)), 3);
    }

    private void chooseColor(Player player, EditorDraft draft, boolean start, String color) {
        try {
            // Validate even when the current mode (e.g. rainbow) does not consume this color.
            TitleText.compose("色", "solid", color, color, false, false, false, false);
            openEditor(player, draft.colors(start ? color : draft.startColor(), start ? draft.endColor() : color));
        } catch (IllegalArgumentException exception) {
            access.error(player, exception.getMessage());
            openColorPicker(player, draft, start);
        }
    }

    private void saveEditor(Player player, EditorDraft draft) {
        String source;
        try {
            source = validate(draft.source());
        } catch (IllegalArgumentException exception) {
            access.error(player, exception.getMessage());
            openEditor(player, draft);
            return;
        }
        String name = draft.name().isBlank() ? TitleText.plain(source) : draft.name();
        if (name.length() > 80) {
            int end = Character.isHighSurrogate(name.charAt(79)) ? 79 : 80;
            name = name.substring(0, end);
        }
        TitleDraft values = new TitleDraft(name, source, draft.description(), draft.enabled());
        UUID owner = player.getUniqueId();
        mutate(player, draft.admin() ? ADMIN : CUSTOM, () -> {
            if (draft.admin()) {
                return draft.titleId() == null ? access.store().createAdminTitle(owner, values)
                    : access.store().updateAdminTitle(draft.titleId(), draft.version(), values);
            }
            return draft.titleId() == null ? access.store().createCustomTitle(owner, values)
                : access.store().updateCustomTitle(owner, draft.titleId(), draft.version(), values);
        }, saved -> {
            access.message(player, "<green>称号已保存。</green>");
            if (draft.admin() && draft.titleId() == null) openListingSettings(player, null, saved);
            else if (draft.admin()) openAdminShop(player);
            else openCustom(player);
        });
    }

    public void openAdminShop(Player player) { openAdminShop(player, 0); }

    private void openAdminShop(Player player, int requestedPage) {
        load(player, ADMIN, () -> access.store().shop(true), (p, listings) -> {
            Screen screen = begin(p);
            int page = page(requestedPage, listings.size());
            List<ActionButton> buttons = new ArrayList<>();
            buttons.add(button(screen, "创建称号", ADMIN, (who, r) -> openEditorForNewAdmin(who)));
            buttons.add(button(screen, "称号库", ADMIN, (who, r) -> openTitleLibrary(who, 0)));
            for (ShopListing listing : slice(listings, page)) {
                String state = !listing.enabled() ? "已下架" : listing.remainingStock() != null && listing.remainingStock() == 0
                    ? "售罄" : !listing.available(Instant.now()) ? "未在售" : "在售";
                Component label = title(listing.title()).append(Component.text(" · " + state, NamedTextColor.GRAY));
                buttons.add(button(screen, label, saleInfo(listing), ADMIN, (who, r) -> openListing(who, listing.id())));
            }
            pagination(screen, buttons, page, listings.size(), (who, next) -> openAdminShop(who, next), ADMIN);
            show(p, pageTitle("商店管理", page, listings.size()), List.of(), List.of(), buttons,
                back(screen, (who, r) -> openMain(who)), 2);
        });
    }

    private void openTitleLibrary(Player player, int requestedPage) {
        load(player, ADMIN, () -> access.store().listTitles(), (p, titles) -> {
            Screen screen = begin(p);
            int page = page(requestedPage, titles.size());
            List<ActionButton> buttons = new ArrayList<>();
            for (Title title : slice(titles, page)) {
                buttons.add(button(screen, title(title), Component.text(title.name()), ADMIN,
                    (who, r) -> openLibraryTitle(who, title.id())));
            }
            buttons.add(button(screen, "创建称号", ADMIN, (who, r) -> openEditorForNewAdmin(who)));
            pagination(screen, buttons, page, titles.size(), (who, next) -> openTitleLibrary(who, next), ADMIN);
            show(p, pageTitle("称号库", page, titles.size()), List.of(), List.of(), buttons,
                back(screen, ADMIN, (who, r) -> openAdminShop(who)), 2);
        });
    }

    private void openLibraryTitle(Player player, long titleId) {
        load(player, ADMIN, () -> access.store().title(titleId).orElseThrow(() -> new TitleException(ErrorCode.NOT_FOUND)),
            (p, title) -> {
                if (title.custom()) throw new IllegalArgumentException("玩家自定义称号不能上架。");
                Screen screen = begin(p);
                show(p, title.name(), List.of(body(title(title))), List.of(), List.of(
                    button(screen, "编辑称号", ADMIN, (who, r) -> openExistingEditor(who, title.id(), true)),
                    button(screen, "新建上架方案", ADMIN, (who, r) -> openListingSettings(who, null, title))),
                    back(screen, ADMIN, (who, r) -> openTitleLibrary(who, 0)), 2);
            });
    }

    private void openListing(Player player, long listingId) {
        load(player, ADMIN, () -> access.store().listing(listingId)
            .orElseThrow(() -> new TitleException(ErrorCode.NOT_FOUND)),
            (p, listing) -> openListingSettings(p, listing, listing.title()));
    }

    private void openListingSettings(Player player, ShopListing listing, Title title) {
        if (!allowed(player, ADMIN)) return;
        Screen screen = begin(player);
        String remaining = listing == null || listing.remainingStock() == null ? "" : listing.remainingStock().toString();
        List<DialogInput> inputs = List.of(
            input("price", "价格 · " + access.currencyLabel(), listing == null ? "0" : amount(listing.price()), 32),
            input("stock", "剩余库存（留空不限）", remaining, 20),
            input("starts_at", "开始时间（留空即刻）", listing == null ? "" : date(listing.startsAt()), 32),
            input("ends_at", "结束时间（留空不限）", listing == null ? "" : date(listing.endsAt()), 32),
            input("duration", "持有天数（留空永久）",
                listing == null || listing.grantDurationDays() == null ? "" : listing.grantDurationDays().toString(), 6),
            DialogInput.bool("enabled", Component.text("上架")).initial(listing == null || listing.enabled()).build());
        List<DialogBody> body = new ArrayList<>();
        body.add(body(title(title)));
        body.add(body(Component.text("yyyy-MM-dd HH:mm · " + access.zoneId().getId(), NamedTextColor.GRAY)));
        if (listing != null) body.add(body(Component.text("已售 " + listing.sold(), NamedTextColor.GRAY)));
        List<ActionButton> buttons = List.of(
            button(screen, "保存", ADMIN, (p, response) -> {
                try {
                    Instant start = FormValues.date(response.getText("starts_at"), access.zoneId());
                    Instant end = FormValues.date(response.getText("ends_at"), access.zoneId());
                    FormValues.dateRange(start, end);
                    ListingDraft values = new ListingDraft(title.id(), access.currency(),
                        FormValues.price(response.getText("price"), access.currency()),
                        FormValues.totalStock(response.getText("stock"), listing == null ? 0 : listing.sold()),
                        start, end, FormValues.duration(response.getText("duration")),
                        FormValues.bool(response.getBoolean("enabled"), "上架"));
                    mutate(p, ADMIN, () -> access.store().saveListing(listing == null ? null : listing.id(),
                        listing == null ? 0 : listing.version(), listing == null ? null : listing.sold(), values), saved -> {
                            access.message(p, "<green>上架设置已保存。</green>");
                            openAdminShop(p);
                        });
                } catch (IllegalArgumentException exception) {
                    access.error(p, exception.getMessage());
                    openListingSettings(p, listing, title);
                }
            }),
            button(screen, "编辑称号", ADMIN, (p, r) -> openExistingEditor(p, title.id(), true)));
        show(player, "上架设置", body, inputs, buttons,
            back(screen, ADMIN, (p, r) -> openAdminShop(p)), 2);
    }

    private <T> void load(Player player, String permission, Supplier<T> work, BiConsumer<Player, T> success) {
        if (!allowed(player, permission)) return;
        Screen request = begin(player);
        access.async(player, work, value -> {
            if (isCurrentOperation(player, request.token())) {
                if (!permissionGranted(player, permission)) {
                    operationFailed(player, request.token(), "你没有使用此功能的权限。");
                    return;
                }
                success.accept(player, value);
            }
        }, message -> operationFailed(player, request.token(), message));
    }

    private <T> void mutate(Player player, String permission, Supplier<T> work, Consumer<T> success) {
        if (!allowed(player, permission)) return;
        Screen request = begin(player);
        access.async(player, work, value -> {
            access.invalidate(request.playerId());
            if (isCurrentOperation(player, request.token())) {
                if (!permissionGranted(player, permission)) {
                    operationFailed(player, request.token(), "你没有使用此功能的权限。");
                    return;
                }
                success.accept(value);
            }
        }, message -> operationFailed(player, request.token(), message));
    }

    /** Begin a commerce operation; call on the player's owning thread before handing work off. */
    public UUID beginExternalOperation(Player player) {
        return closed ? UUID.randomUUID() : begin(player).token();
    }

    /** Old operations cannot replace a newer page, including after a reconnect. */
    public boolean isCurrentOperation(Player player, UUID operation) {
        return !closed && operation != null && operation.equals(sessions.get(player.getUniqueId()));
    }

    /** Replace a waiting dialog only if this failure belongs to the still-current operation. */
    public void operationFailed(Player player, UUID operation, String message) {
        if (!isCurrentOperation(player, operation)) return;
        Screen screen = begin(player);
        String explanation = message == null || message.isBlank() ? "请稍后重试。" : message;
        ActionButton back = button(screen, "返回", "", (who, response) -> {
            if (who.hasPermission(USE)) openMain(who);
            else who.closeDialog();
        });
        show(player, "无法完成", List.of(body(Component.text(explanation, NamedTextColor.RED))),
            List.of(), List.of(), back, 1);
    }

    private boolean permissionGranted(Player player, String permission) {
        return player.hasPermission(permission) && (!CUSTOM.equals(permission) || player.hasPermission(USE));
    }

    private boolean allowed(Player player, String permission) {
        if (closed) return false;
        if (!permissionGranted(player, permission)) {
            UUID operation = sessions.get(player.getUniqueId());
            if (operation != null) operationFailed(player, operation, "你没有使用此功能的权限。");
            else access.error(player, "你没有使用此功能的权限。");
            return false;
        }
        return true;
    }

    private Screen begin(Player player) {
        Screen screen = new Screen(player.getUniqueId(), UUID.randomUUID());
        sessions.put(screen.playerId(), screen.token());
        return screen;
    }

    private ActionButton button(Screen screen, String label, String permission, BiConsumer<Player, DialogResponseView> callback) {
        return button(screen, Component.text(label), null, permission, callback);
    }

    private ActionButton button(Screen screen, Component label, Component tooltip, String permission,
                                BiConsumer<Player, DialogResponseView> callback) {
        DialogAction action = DialogAction.customClick((response, audience) -> {
            if (closed || !(audience instanceof Player player) || !screen.playerId().equals(player.getUniqueId())) return;
            access.onPlayer(player, () -> {
                if (closed || !screen.token().equals(sessions.get(screen.playerId()))) return;
                if (!permission.isEmpty() && !permissionGranted(player, permission)) {
                    operationFailed(player, screen.token(), "你没有使用此功能的权限。");
                    return;
                }
                if (!sessions.remove(screen.playerId(), screen.token())) return;
                try {
                    callback.accept(player, response);
                } catch (IllegalArgumentException exception) {
                    operationFailed(player, beginExternalOperation(player), exception.getMessage());
                }
            });
        }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(3)).build());
        return ActionButton.create(label, tooltip, BUTTON_WIDTH, action);
    }

    private ActionButton back(Screen screen, BiConsumer<Player, DialogResponseView> callback) {
        return back(screen, USE, callback);
    }

    private ActionButton back(Screen screen, String permission, BiConsumer<Player, DialogResponseView> callback) {
        return button(screen, "返回", permission, callback);
    }

    private ActionButton close(Screen screen) {
        return button(screen, "关闭", "", (player, response) -> player.closeDialog());
    }

    private void pagination(Screen screen, List<ActionButton> buttons, int page, int size,
                            BiConsumer<Player, Integer> open, String permission) {
        if (page > 0) buttons.add(button(screen, "上一页", permission, (p, r) -> open.accept(p, page - 1)));
        if ((page + 1L) * PAGE_SIZE < size) buttons.add(button(screen, "下一页", permission, (p, r) -> open.accept(p, page + 1)));
    }

    private void show(Player player, String title, List<DialogBody> body, List<DialogInput> inputs,
                      List<ActionButton> actions, ActionButton exit, int columns) {
        Dialog dialog = Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(Component.text(title)).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE).body(body).inputs(inputs).build())
            .type(actions.isEmpty() ? DialogType.notice(exit) : DialogType.multiAction(actions, exit, columns)));
        player.showDialog(dialog);
    }

    private Component title(Title title) {
        try { return TitleText.parse(title.miniMessage()); }
        catch (IllegalArgumentException exception) { return Component.text(title.name(), NamedTextColor.RED); }
    }

    private Component balance(PlayerProfile profile) {
        String credits = "自定义 " + profile.customCredits() + " 次";
        return Component.text(access.currency() == Currency.COINS ? profile.coins() + " " + access.currencyLabel() + "  ·  " + credits : credits);
    }

    private Component ownershipInfo(OwnedTitle owned) {
        if (owned.expired(Instant.now())) return Component.text("已到期", NamedTextColor.GRAY);
        return Component.text(owned.expiresAt() == null ? "永久拥有" : "到期 " + date(owned.expiresAt()), NamedTextColor.GRAY);
    }

    private Component saleInfo(ShopListing listing) {
        String stock = listing.remainingStock() == null ? "不限量" : "剩余 " + listing.remainingStock();
        String duration = listing.grantDurationDays() == null ? "永久拥有" : "持有 " + listing.grantDurationDays() + " 天";
        String deadline = listing.endsAt() == null ? "" : "\n停售 " + date(listing.endsAt());
        return Component.text(stock + "  ·  " + duration + deadline, NamedTextColor.GRAY);
    }

    private String validate(String source) { return TitleText.validate(source, access.maxTitleLength(), access.maxTitleSourceLength()); }
    private String date(Instant value) { return value == null ? "" : FormValues.DATE.format(value.atZone(access.zoneId())); }
    private static String amount(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static Component colorLabel(String label, String color) {
        TextColor parsed = TextColor.fromHexString(color);
        if (parsed == null) parsed = NamedTextColor.NAMES.value(color);
        return Component.text(label, parsed == null ? NamedTextColor.WHITE : parsed);
    }
    private static DialogBody body(Component value) { return DialogBody.plainMessage(value, 340); }
    private static TextDialogInput input(String key, String label, String initial, int maxLength) {
        return DialogInput.text(key, Component.text(label)).initial(initial).maxLength(maxLength).width(320).build();
    }
    private static SingleOptionDialogInput.OptionEntry option(String id, String label, String selected) {
        return SingleOptionDialogInput.OptionEntry.create(id, Component.text(label), id.equals(selected));
    }
    private static int page(int requested, int size) { return Math.max(0, Math.min(requested, Math.max(0, (size - 1) / PAGE_SIZE))); }
    private static <T> List<T> slice(List<T> values, int page) {
        int from = page * PAGE_SIZE;
        return values.subList(from, Math.min(values.size(), from + PAGE_SIZE));
    }
    private static String pageTitle(String title, int page, int size) {
        return size <= PAGE_SIZE ? title : title + " · " + (page + 1) + "/" + ((size - 1) / PAGE_SIZE + 1);
    }

    public void forget(UUID playerId) { sessions.remove(playerId); }
    @Override public void close() { closed = true; sessions.clear(); }
    private record Screen(UUID playerId, UUID token) {}
    private record Home(PlayerProfile profile, Optional<Title> equipped) {}
    private record Backpack(PlayerProfile profile, List<OwnedTitle> titles) {}
    private record OwnedView(PlayerProfile profile, OwnedTitle owned) {}
    private record ColorChoice(String label, String hex) {}
}
