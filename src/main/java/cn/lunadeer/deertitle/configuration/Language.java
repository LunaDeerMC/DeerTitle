package cn.lunadeer.deertitle.configuration;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.utils.configuration.ConfigurationFile;
import cn.lunadeer.deertitle.utils.configuration.ConfigurationManager;
import cn.lunadeer.deertitle.utils.configuration.ConfigurationPart;
import cn.lunadeer.deertitle.utils.configuration.Headers;

import java.io.File;
import java.util.List;

@Headers({
        "Language file for DeerTitle.",
        "Messages support legacy color codes, native Minecraft formatting, hex colors, and MiniMessage tags.",
        "GUI button labels are also customizable from here.",
})
public final class Language extends ConfigurationFile {

    public GeneralText general = new GeneralText();
    public CommandText command = new CommandText();
    public TitleText title = new TitleText();
    public ShopText shop = new ShopText();
    public CardText card = new CardText();
    public UiText ui = new UiText();

    public static Language load(DeerTitlePlugin plugin, String code) throws Exception {
        String languageCode = (code == null || code.isBlank()) ? "zh_cn" : code;
        File languagesFolder = new File(plugin.getDataFolder(), "languages");
        if (!languagesFolder.exists() && !languagesFolder.mkdirs()) {
            throw new IllegalStateException("Failed to create language directory: " + languagesFolder.getAbsolutePath());
        }
        return (Language) ConfigurationManager.load(Language.class, new File(languagesFolder, languageCode + ".yml"));
    }

    public static final class GeneralText extends ConfigurationPart {
        public String prefix = "<gold>[DeerTitle]</gold> ";
        public String noPermission = "<red>You do not have permission to do that.</red>";
        public String playerOnly = "<red>This action can only be used by players.</red>";
        public String reloaded = "<green>Configuration reloaded.</green>";
        public String databaseError = "<red>Database operation failed. Check the server log.</red>";
        public String internalError = "<red>An unexpected error occurred.</red>";
        public String balanceLine = "<green>Balance:</green> <white>{0}</white>";
        public String statusActive = "active";
        public String statusExpired = "expired";
        public String permanent = "permanent";
    }

    public static final class CommandText extends ConfigurationPart {
        public String rootDescription = "Open the title system or use subcommands.";
        public String adminDescription = "Administrative title operations.";
        public String usageHelp = "<yellow>/title help</yellow>";
        public String unknownSubcommand = "<red>Unknown subcommand: <white>{0}</white></red>";
        public String invalidNumber = "<red>Invalid number: <white>{0}</white></red>";
        public String reloaded = "<green>DeerTitle reloaded successfully.</green>";
        public List<String> helpLines = List.of(
                "<gold>=== DeerTitle Commands ===</gold>",
                "<yellow>/title list</yellow> <gray>- list owned titles</gray>",
                "<yellow>/title wear <id></yellow> <gray>- equip a title you own</gray>",
                "<yellow>/title remove</yellow> <gray>- remove current title</gray>",
                "<yellow>/title current</yellow> <gray>- show current title</gray>",
                "<yellow>/title balance</yellow> <gray>- show current balance</gray>",
                "<yellow>/title shop</yellow> <gray>- list shop offers</gray>",
                "<yellow>/title buy <offerId></yellow> <gray>- purchase a title offer</gray>"
        );
        public List<String> adminHelpLines = List.of(
                "<gold>=== DeerTitle Admin ===</gold>",
                "<yellow>/title reload</yellow>",
                "<yellow>/title admin create <title> || <description></yellow>",
                "<yellow>/title admin setdesc <titleId> <description></yellow>",
                "<yellow>/title admin setenabled <titleId> <true|false></yellow>",
                "<yellow>/title admin grant <player> <titleId> [days|-1]</yellow>",
                "<yellow>/title admin revoke <player> <titleId></yellow>",
                "<yellow>/title admin shop set <titleId> <price> <days|-1> <amount|-1> <saleEnd|-1></yellow>",
                "<yellow>/title admin shop clear <titleId></yellow>",
                "<yellow>/title admin coin <set|add> <player> <amount></yellow>",
                "<yellow>/title admin card <player> <titleId> [days|-1]</yellow>"
        );
    }

    public static final class TitleText extends ConfigurationPart {
        public String equipped = "<green>You equipped title <white>{0}</white>.</green>";
        public String removed = "<yellow>Your title has been removed.</yellow>";
        public String expired = "<yellow>Your title <white>{0}</white> has expired.</yellow>";
        public String notFound = "<red>Title not found: <white>{0}</white></red>";
        public String alreadyOwned = "<yellow>You already own this title.</yellow>";
        public String notOwned = "<red>You do not own this title.</red>";
        public String currentNone = "<yellow>You do not have a title equipped.</yellow>";
        public String currentLine = "<green>Current title:</green> <white>{0}</white>";
        public String created = "<green>Created title <white>#{0}</white>: {1}</green>";
        public String descriptionUpdated = "<green>Updated description for title <white>#{0}</white>.</green>";
        public String enabledUpdated = "<green>Updated title <white>#{0}</white> enabled=<white>{1}</white>.</green>";
        public String granted = "<green>Granted title <white>#{1}</white> to <white>{0}</white>.</green>";
        public String revoked = "<yellow>Revoked title <white>#{1}</white> from <white>{0}</white>.</yellow>";
        public String listHeader = "<gold>Your titles:</gold>";
        public String listEntry = "<gray>#{0}</gray> {1} <dark_gray>|</dark_gray> <gray>expires:</gray> <white>{2}</white> <dark_gray>|</dark_gray> <gray>{3}</gray>";
        public String noOwnedTitles = "<yellow>You do not own any titles yet.</yellow>";
    }

    public static final class ShopText extends ConfigurationPart {
        public String purchased = "<green>You purchased title <white>{0}</white>.</green>";
        public String insufficientFunds = "<red>You do not have enough money.</red>";
        public String outOfStock = "<red>This title is sold out.</red>";
        public String saleExpired = "<red>This offer has expired.</red>";
        public String unavailable = "<red>This offer is no longer available.</red>";
        public String free = "<green>Free</green>";
        public String unlimited = "<green>Unlimited</green>";
        public String shopHeader = "<gold>Title Shop:</gold>";
        public String shopEntry = "<gray>#{0}</gray> {1} <dark_gray>|</dark_gray> <gray>price:</gray> <white>{2}</white> <dark_gray>|</dark_gray> <gray>stock:</gray> <white>{3}</white> <dark_gray>|</dark_gray> <gray>days:</gray> <white>{4}</white> <dark_gray>|</dark_gray> <gray>until:</gray> <white>{5}</white> <dark_gray>|</dark_gray> <gray>{6}</gray>";
        public String saleSaved = "<green>Saved shop offer <white>#{0}</white> for title <white>#{1}</white>.</green>";
        public String saleRemoved = "<yellow>Removed shop offer for title <white>#{0}</white>.</yellow>";
    }

    public static final class CardText extends ConfigurationPart {
        public String exported = "<green>Exported title <white>{0}</white> as a title card for <white>{1}</white>.</green>";
        public String invalidCard = "<red>This title card is invalid.</red>";
        public String cardUsed = "<green>You used a title card for <white>{0}</white>.</green>";
        public String cardConsumed = "<yellow>The title card was consumed.</yellow>";
        public String itemName = "<gold>Title Card</gold>: {0}";
        public String itemTitleLine = "<gray>Title:</gray> {0}";
        public String itemDurationLine = "<gray>Duration:</gray> <green>{0} day(s)</green>";
        public String itemDurationPermanent = "<gray>Duration:</gray> <green>Permanent</green>";
    }

    public static final class UiText extends ConfigurationPart {
        public String mainTitle = "<gold>DeerTitle</gold>";
        public String myTitlesTitle = "<gold>My Titles</gold>";
        public String shopTitle = "<gold>Title Shop</gold>";
        public String adminTitle = "<gold>Title Admin</gold>";
        public String myTitlesButton = "<green>My Titles</green>";
        public String myTitlesButtonLore = "<gray>Browse the titles you already own.</gray>";
        public String currentTitleButton = "<yellow>Current Title</yellow>";
        public String currentTitleButtonLore = "<gray>Click to remove your current title.</gray>";
        public String shopButton = "<green>Shop</green>";
        public String shopButtonLore = "<gray>Browse title offers and buy them.</gray>";
        public String adminButton = "<aqua>Admin</aqua>";
        public String adminButtonLore = "<gray>Open administrative title tools.</gray>";
        public String adminBrowseTitlesButton = "<aqua>Browse All Titles</aqua>";
        public String adminBrowseTitlesLore = "<gray>Left click entries to grant permanently to yourself, right click to create a permanent card, shift click to toggle enabled.</gray>";
        public String adminTitleEntryLoreLine1 = "<gray>Left click: grant to self</gray>";
        public String adminTitleEntryLoreLine2 = "<gray>Right click: create permanent card</gray>";
        public String adminTitleEntryLoreLine3 = "<gray>Shift click: toggle enabled</gray>";
        public String backButton = "<yellow>Back</yellow>";
        public String closeButton = "<red>Close</red>";
        public String nextPageButton = "<green>Next Page</green>";
        public String prevPageButton = "<green>Previous Page</green>";
        public String equipButton = "<green>Equip</green>";
        public String removeButton = "<yellow>Remove</yellow>";
        public String buyButton = "<green>Buy</green>";
        public String exportCardButton = "<aqua>Export Card</aqua>";
        public String pageInfo = "<gray>Page {0}</gray>";
        public String myTitlesActiveLore = "<green>Click to equip this title.</green>";
        public String myTitlesExpiredLore = "<red>This title has expired.</red>";
        public String shopBuyLore = "<green>Click to buy this offer.</green>";
        public String shopSoldOutLore = "<red>This offer is sold out.</red>";
        public String shopExpiredLore = "<red>This offer is no longer active.</red>";
        public String noDescription = "<gray>No description</gray>";
        public String labelId = "<gray>ID:</gray> <white>{0}</white>";
        public String labelEnabled = "<gray>Enabled:</gray> <white>{0}</white>";
        public String labelExpires = "<gray>Expires:</gray> <white>{0}</white>";
        public String labelPrice = "<gray>Price:</gray> <white>{0}</white>";
        public String labelStock = "<gray>Stock:</gray> <white>{0}</white>";
        public String labelDays = "<gray>Days:</gray> <white>{0}</white>";
        public String labelSaleEnd = "<gray>Sale end:</gray> <white>{0}</white>";
        public String fillerName = "<gray> </gray>";
    }
}
