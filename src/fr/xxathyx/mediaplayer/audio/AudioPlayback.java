package fr.xxathyx.mediaplayer.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import fr.xxathyx.mediaplayer.util.Scheduler;

public class AudioPlayback {

    private final Scheduler scheduler;
    private final AudioTrack track;
    private final Supplier<Set<UUID>> viewersSupplier;
    private final BooleanSupplier activeSupplier;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public AudioPlayback(Scheduler scheduler, AudioTrack track, Supplier<Set<UUID>> viewersSupplier, BooleanSupplier activeSupplier) {
        this.scheduler = scheduler;
        this.track = track;
        this.viewersSupplier = viewersSupplier;
        this.activeSupplier = activeSupplier;
    }

    public void start() {
        stop();
        for (int i = 0; i < track.getChunkCount(); i++) {
            long delayTicks = Math.round(track.getChunkSeconds() * 20L * i);
            int chunkIndex = i;
            BukkitTask task = scheduler.runSyncLater(() -> playChunk(chunkIndex), delayTicks);
            if (task != null) {
                tasks.add(task);
            }
        }
    }

    public void stop() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }

    private void playChunk(int index) {
        if (!activeSupplier.getAsBoolean()) {
            return;
        }
        String chunkName = String.format("chunk_%03d", index);
        String soundKey = "mediaplayer." + track.getMediaId() + "." + chunkName;
        for (UUID uuid : viewersSupplier.get()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.playSound(player.getLocation(), soundKey, 10f, 1f);
            }
        }
    }
}
