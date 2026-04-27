package cn.lunadeer.deertitle.utils.scheduler;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public final class Scheduler {

    private static Scheduler instance;

    private final JavaPlugin plugin;
    private final boolean folia;

    public Scheduler(JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;
        this.folia = plugin.getServer().getName().equalsIgnoreCase("Folia")
                || isClassPresent("io.papermc.paper.threadedregions.RegionizedServer");
    }

    public static void cancelAll() {
        if (instance == null) {
            return;
        }
        if (instance.folia) {
            instance.plugin.getServer().getGlobalRegionScheduler().cancelTasks(instance.plugin);
            instance.plugin.getServer().getAsyncScheduler().cancelTasks(instance.plugin);
        } else {
            instance.plugin.getServer().getScheduler().cancelTasks(instance.plugin);
        }
    }

    public static CancellableTask runTask(Runnable task) {
        if (instance.folia) {
            return new PaperTask(instance.plugin.getServer().getGlobalRegionScheduler().run(instance.plugin, scheduledTask -> task.run()));
        }
        return new SpigotTask(instance.plugin.getServer().getScheduler().runTask(instance.plugin, task));
    }

    public static CancellableTask runTaskRepeat(Runnable task, long delay, long period) {
        if (instance.folia) {
            return new PaperTask(instance.plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(instance.plugin, scheduledTask -> task.run(), delay, period));
        }
        return new SpigotTask(instance.plugin.getServer().getScheduler().runTaskTimer(instance.plugin, task, delay, period));
    }

    public static CancellableTask runTaskAsync(Runnable task) {
        if (instance.folia) {
            return new PaperTask(instance.plugin.getServer().getAsyncScheduler().runNow(instance.plugin, scheduledTask -> task.run()));
        }
        return new SpigotTask(instance.plugin.getServer().getScheduler().runTaskAsynchronously(instance.plugin, task));
    }

    public static CancellableTask runTaskRepeatAsync(Runnable task, long delay, long period) {
        if (instance.folia) {
            return new PaperTask(instance.plugin.getServer().getAsyncScheduler().runAtFixedRate(instance.plugin, scheduledTask -> task.run(), delay * 50, period * 50, TimeUnit.MILLISECONDS));
        }
        return new SpigotTask(instance.plugin.getServer().getScheduler().runTaskTimerAsynchronously(instance.plugin, task, delay, period));
    }

    public static CancellableTask runEntityTask(Runnable task, Entity entity) {
        if (instance.folia) {
            return new PaperTask(entity.getScheduler().run(instance.plugin, scheduledTask -> task.run(), null));
        }
        return new SpigotTask(instance.plugin.getServer().getScheduler().runTask(instance.plugin, task));
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
