package cn.lunadeer.deertitle.command.admin;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.SubCommand;
import cn.lunadeer.deertitle.database.model.TitleRecord;
import cn.lunadeer.deertitle.database.model.TitleShopRecord;
import cn.lunadeer.deertitle.service.ShopService;
import org.bukkit.command.CommandSender;

import java.time.LocalDate;
import java.util.*;

public final class AdminShopCommand extends SubCommand {

    private static final Set<String> ADD_KEYS = Set.of("price", "days", "stock", "saleEnd");
    private static final Set<String> SET_KEYS = Set.of("price", "days", "stock", "saleEnd");

    @Override
    public String getName() {
        return "shop";
    }

    @Override
    public String getUsage() {
        return "<add|set|remove> ...";
    }

    @Override
    public String getDescription() {
        return "Manage shop offers. add/set use key=value params.";
    }

    @Override
    public boolean requiresAdmin() {
        return true;
    }

    @Override
    public void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        requireAdmin(plugin, sender);
        if (args.length < 1) {
            send(plugin, sender, plugin.getConfigService().language().command.unknownSubcommand, "");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "add" -> handleAdd(plugin, sender, args);
            case "set" -> handleSet(plugin, sender, args);
            case "remove" -> handleRemove(plugin, sender, args);
            default -> send(plugin, sender, plugin.getConfigService().language().command.unknownSubcommand, args[0]);
        }
    }

    private void handleAdd(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        int titleId = parseInt(args, 1);
        Map<String, String> params = parseKeyValues(args, 2);
        if (!params.containsKey("price") || !params.containsKey("days") || !params.containsKey("stock")) {
            send(plugin, sender, "<red>Missing required params. Usage: /title admin shop add <titleId> price=<price> days=<days> stock=<stock> [saleEnd=<yyyy-MM-dd|-1>]</red>");
            return;
        }
        double price = Double.parseDouble(params.get("price"));
        int days = Integer.parseInt(params.get("days"));
        int stock = Integer.parseInt(params.get("stock"));
        LocalDate saleEnd = parseOptionalDate(params.get("saleEnd"));
        TitleShopRecord offer = plugin.getShopService().addOffer(titleId, price, days, stock, saleEnd);
        send(plugin, sender, plugin.getConfigService().language().shop.saleSaved, offer.id(), offer.titleId());
    }

    private void handleSet(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        int shopId = parseInt(args, 1);
        Map<String, String> params = parseKeyValues(args, 2);
        if (params.isEmpty()) {
            send(plugin, sender, "<red>At least one key=value must be provided for set.</red>");
            return;
        }
        Double price = params.containsKey("price") ? Double.parseDouble(params.get("price")) : null;
        Integer days = params.containsKey("days") ? Integer.parseInt(params.get("days")) : null;
        Integer stock = params.containsKey("stock") ? Integer.parseInt(params.get("stock")) : null;
        LocalDate saleEnd = params.containsKey("saleEnd") ? parseOptionalDate(params.get("saleEnd")) : null;
        TitleShopRecord offer = plugin.getShopService().updateOffer(shopId, price, days, stock, saleEnd);
        send(plugin, sender, plugin.getConfigService().language().shop.saleSaved, offer.id(), offer.titleId());
    }

    private void handleRemove(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        int shopId = parseInt(args, 1);
        plugin.getShopService().deleteOffer(shopId);
        send(plugin, sender, plugin.getConfigService().language().shop.saleRemoved, shopId);
    }

    @Override
    public List<String> suggestions(DeerTitlePlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of("add", "set", "remove");
        }
        return switch (args[0].toLowerCase()) {
            case "add" -> suggestionsAdd(plugin, args);
            case "set" -> suggestionsSet(plugin, args);
            case "remove" -> suggestionsRemove(plugin, args);
            default -> List.of();
        };
    }

    private List<String> suggestionsAdd(DeerTitlePlugin plugin, String[] args) {
        if (args.length == 1) {
            return titlesNotInShop(plugin);
        }
        return suggestKeys(ADD_KEYS, args);
    }

    private List<String> suggestionsSet(DeerTitlePlugin plugin, String[] args) {
        if (args.length == 1) {
            return shopIds(plugin);
        }
        return suggestKeys(SET_KEYS, args);
    }

    private List<String> suggestionsRemove(DeerTitlePlugin plugin, String[] args) {
        if (args.length == 1) {
            return shopIds(plugin);
        }
        return List.of();
    }

    private List<String> suggestKeys(Set<String> available, String[] args) {
        String lastArg = args[args.length - 1];
        Set<String> used = usedKeys(args);
        List<String> result = new ArrayList<>();
        for (String key : available) {
            if (used.contains(key)) continue;
            if (lastArg.contains("=")) {
                String partialKey = lastArg.substring(0, Math.max(lastArg.indexOf('='), 0));
                if (key.startsWith(partialKey)) {
                    result.add(key + "=");
                }
            } else {
                result.add(key + "=");
            }
        }
        return result;
    }

    private Set<String> usedKeys(String[] args) {
        Set<String> used = new HashSet<>();
        for (int i = 2; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (eq > 0) {
                used.add(args[i].substring(0, eq));
            }
        }
        return used;
    }

    private List<String> titlesNotInShop(DeerTitlePlugin plugin) {
        List<String> result = new ArrayList<>();
        try {
            Set<Integer> shopTitleIds = new HashSet<>();
            for (ShopService.ShopEntryView entry : plugin.getShopService().listEntries()) {
                shopTitleIds.add(entry.title().id());
            }
            for (TitleRecord title : plugin.getRepositories().titles().findAll(true)) {
                if (!shopTitleIds.contains(title.id())) {
                    result.add(title.id() + " " + title.title());
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private List<String> shopIds(DeerTitlePlugin plugin) {
        List<String> result = new ArrayList<>();
        try {
            for (ShopService.ShopEntryView entry : plugin.getShopService().listEntries()) {
                result.add(entry.offer().id() + " " + entry.title().title());
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private Map<String, String> parseKeyValues(String[] args, int start) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = start; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (eq > 0 && eq < args[i].length() - 1) {
                params.put(args[i].substring(0, eq), args[i].substring(eq + 1));
            }
        }
        return params;
    }
}
