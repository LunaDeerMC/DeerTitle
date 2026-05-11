package cn.lunadeer.deertitle.command.admin;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.SubCommand;
import cn.lunadeer.deertitle.database.model.TitleRecord;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public final class AdminSetEnabledCommand extends SubCommand {

    @Override
    public String getName() {
        return "setenabled";
    }

    @Override
    public String getUsage() {
        return "<titleId> <true|false>";
    }

    @Override
    public String getDescription() {
        return "Enable or disable a title.";
    }

    @Override
    public boolean requiresAdmin() {
        return true;
    }

    @Override
    public void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        requireAdmin(plugin, sender);
        int titleId = parseInt(args, 0);
        boolean enabled = Boolean.parseBoolean(args[1]);
        plugin.getTitleService().setEnabled(titleId, enabled);
        send(plugin, sender, plugin.getConfigService().language().title.enabledUpdated, titleId, enabled);
    }

    @Override
    public List<String> suggestions(DeerTitlePlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 0) {
            return titleIds(plugin);
        }
        if (args.length == 1) {
            return List.of("true", "false");
        }
        return List.of();
    }

    private List<String> titleIds(DeerTitlePlugin plugin) {
        List<String> result = new ArrayList<>();
        try {
            for (TitleRecord title : plugin.getRepositories().titles().findAll(true)) {
                result.add(title.id() + " " + title.title());
            }
        } catch (Exception ignored) {
        }
        return result;
    }
}
