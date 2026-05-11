package cn.lunadeer.deertitle.command.admin;

import cn.lunadeer.deertitle.DeerTitlePlugin;
import cn.lunadeer.deertitle.command.SubCommand;
import cn.lunadeer.deertitle.database.model.TitleRecord;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public final class AdminSetDescCommand extends SubCommand {

    @Override
    public String getName() {
        return "setdesc";
    }

    @Override
    public String getUsage() {
        return "<titleId> <description>";
    }

    @Override
    public String getDescription() {
        return "Update a title's description.";
    }

    @Override
    public boolean requiresAdmin() {
        return true;
    }

    @Override
    public void execute(DeerTitlePlugin plugin, CommandSender sender, String[] args) throws Exception {
        requireAdmin(plugin, sender);
        int titleId = parseInt(args, 0);
        var title = plugin.getTitleService().setDescription(titleId, joinFrom(args, 1));
        send(plugin, sender, plugin.getConfigService().language().title.descriptionUpdated, title.id());
    }

    @Override
    public List<String> suggestions(DeerTitlePlugin plugin, CommandSender sender, String[] args) {
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
