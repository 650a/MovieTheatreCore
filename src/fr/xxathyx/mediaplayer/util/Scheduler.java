package fr.xxathyx.mediaplayer.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class Scheduler {

    private final Plugin plugin;

    public Scheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    public BukkitTask runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return null;
        }
        return Bukkit.getScheduler().runTask(plugin, runnable);
    }

    public BukkitTask runAsync(Runnable runnable) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getLogger().warning("[MediaPlayer]: runAsync invoked from an async thread. Verify threading assumptions.");
        }
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    public BukkitTask runSyncRepeating(Runnable runnable, long delayTicks, long periodTicks) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getLogger().warning("[MediaPlayer]: runSyncRepeating invoked off the main thread.");
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
    }

    public BukkitTask runSyncLater(Runnable runnable, long delayTicks) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getLogger().warning("[MediaPlayer]: runSyncLater invoked off the main thread.");
        }
        return Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
    }

    public void ensureMainThread(String context) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getLogger().warning("[MediaPlayer]: Bukkit API call off the main thread (" + context + ").");
        }
    }
}
