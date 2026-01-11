package fr.xxathyx.mediaplayer.screen;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import fr.xxathyx.mediaplayer.Main;
import fr.xxathyx.mediaplayer.configuration.Configuration;
import fr.xxathyx.mediaplayer.items.ItemStacks;
import fr.xxathyx.mediaplayer.render.ScalingMode;
import fr.xxathyx.mediaplayer.screen.part.Part;
import fr.xxathyx.mediaplayer.util.FacingLocation;

public class ScreenManager {

    private final Main plugin;
    private final Configuration configuration;
    private final ItemStacks itemStacks = new ItemStacks();

    private final Map<UUID, Screen> screens = new HashMap<>();
    private final Map<UUID, ScreenState> states = new HashMap<>();

    public ScreenManager(Main plugin) {
        this.plugin = plugin;
        this.configuration = new Configuration();
    }

    public void loadAll() {
        File[] files = configuration.getScreensFolder().listFiles(File::isDirectory);

        plugin.getRegisteredScreens().clear();
        plugin.getScreensBlocks().clear();
        plugin.getScreensFrames().clear();
        screens.clear();
        states.clear();

        if (files == null) {
            return;
        }

        for (File file : files) {
            File screenConfiguration = new File(configuration.getScreensFolder() + "/" + file.getName(), file.getName() + ".yml");
            if (!screenConfiguration.exists()) {
                continue;
            }

            Screen screen = new Screen(screenConfiguration);
            if (screen.getLocation() == null || screen.getLocation().getWorld() == null) {
                Bukkit.getLogger().warning("[MediaPlayer]: Screen configuration " + screenConfiguration.getName() + " references a missing world.");
                continue;
            }

            ensureScaleMode(screen);

            String entityName = configuration.glowing_screen_frames_support() ? "glow_item_frame" : "item_frame";

            ArrayList<ItemFrame> frames = new ArrayList<>();
            ArrayList<Location> existing = new ArrayList<>();

            for (Part part : screen.getParts()) {
                Location location = part.getItemFrameLocation();
                Chunk chunk = location.getChunk();
                if (!chunk.isLoaded()) {
                    chunk.setForceLoaded(true);
                    chunk.load();
                }

                if (!part.getBlock().getType().equals(screen.getBlockType())) {
                    part.getBlock().setType(screen.getBlockType());
                }

                ItemFrame itemFrame = part.getItemFrame();
                if (itemFrame == null) {
                    for (Entity entity : chunk.getEntities()) {
                        if (entity.getType() == EntityType.ITEM_FRAME || entity.getType() == EntityType.GLOW_ITEM_FRAME) {
                            if (entity.getLocation().distance(location) < 0.01) {
                                plugin.getScreensFrames().put((ItemFrame) entity, screen);
                                frames.add((ItemFrame) entity);
                                existing.add(entity.getLocation());
                            }
                        }
                    }
                } else {
                    plugin.getScreensFrames().put(itemFrame, screen);
                }
                plugin.getScreensBlocks().put(part.getBlock(), screen);
            }

            ArrayList<Location> missing = new ArrayList<>();
            for (Part part : screen.getParts()) {
                Location frameLocation = part.getItemFrameLocation();
                boolean contains = false;
                for (Location location : existing) {
                    if (location.distance(frameLocation) == 0) {
                        contains = true;
                        break;
                    }
                }
                if (!contains) {
                    missing.add(frameLocation);
                }
            }

            for (Location location : missing) {
                if (getNearbyEntities(location, 0).isEmpty()) {
                    ItemFrame frame = (ItemFrame) screen.getLocation().getWorld().spawnEntity(location, EntityType.fromName(entityName));
                    frames.add(frame);
                    plugin.getScreensFrames().put(frame, screen);
                }
            }

            screen.loadThumbnail();

            List<ItemFrame> targetFrames = frames.isEmpty() ? screen.getFrames() : frames;
            int[] ids = screen.getIds();
            for (int i = 0; i < targetFrames.size() && i < ids.length; i++) {
                ItemFrame frame = targetFrames.get(i);
                if (frame != null && frame.getItem().getType().equals(Material.AIR)) {
                    frame.setItem(itemStacks.getMap(ids[i]));
                }
            }

            screen.setFrames(frames.isEmpty() ? new ArrayList<>(targetFrames) : frames);

            plugin.getRegisteredScreens().add(screen);
            screens.put(screen.getUUID(), screen);
            states.put(screen.getUUID(), ScreenState.IDLE);
        }

        Bukkit.getServer().getConsoleSender().sendMessage(ChatColor.DARK_GRAY + "[MediaPlayer]: " + ChatColor.GRAY
                + "Screens successfully registered. (" + plugin.getRegisteredScreens().size() + ")");
    }

    private void ensureScaleMode(Screen screen) {
        if (screen.getConfigFile().getString("screen.scale-mode") == null) {
            screen.setScaleMode(ScalingMode.FIT);
        }
    }

    public Map<UUID, Screen> getScreens() {
        return screens;
    }

    public Screen getScreen(UUID uuid) {
        return screens.get(uuid);
    }

    public Screen getScreenByName(String name) {
        for (Screen screen : screens.values()) {
            if (screen.getName().equalsIgnoreCase(name)) {
                return screen;
            }
        }
        return null;
    }

    public ScreenState getState(UUID screenId) {
        return states.getOrDefault(screenId, ScreenState.IDLE);
    }

    public void setState(UUID screenId, ScreenState state) {
        states.put(screenId, state);
    }

    public void deleteScreen(Screen screen) {
        screens.remove(screen.getUUID());
        states.remove(screen.getUUID());
        plugin.getRegisteredScreens().remove(screen);
        screen.delete();
    }

    public Screen createScreen(Player player, String name, int width, int height) {
        ArrayList<ItemFrame> frames = new ArrayList<>();
        ArrayList<org.bukkit.block.Block> blocks = new ArrayList<>();

        Vector vector = player.getEyeLocation().getDirection();
        vector.multiply(3);

        Location location = player.getEyeLocation().add(vector);

        String entityName = configuration.glowing_screen_frames_support() ? "glow_item_frame" : "item_frame";
        Screen screen = null;

        try {
            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {

                    ItemFrame itemFrame = null;
                    Location locBlock = new Location(location.getWorld(), location.getX(), location.getY() + j, location.getZ());

                    if (FacingLocation.getCardinalDirection(player).equals("N")) {
                        locBlock.add(i, 0, 0);
                        locBlock.getBlock().setType(Material.getMaterial(configuration.screen_block()));
                        itemFrame = (ItemFrame) player.getWorld().spawnEntity(new Location(player.getWorld(), locBlock.getBlockX(), locBlock.getBlockY(), locBlock.getBlockZ() + 1), EntityType.fromName(entityName));
                    }
                    if (FacingLocation.getCardinalDirection(player).equals("E")) {
                        locBlock.subtract(0, 0, i);
                        locBlock.getBlock().setType(Material.getMaterial(configuration.screen_block()));
                        itemFrame = (ItemFrame) player.getWorld().spawnEntity(new Location(player.getWorld(), locBlock.getBlockX() - 1, locBlock.getBlockY(), locBlock.getBlockZ()), EntityType.fromName(entityName));
                    }
                    if (FacingLocation.getCardinalDirection(player).equals("S")) {
                        locBlock.subtract(i, 0, 0);
                        locBlock.getBlock().setType(Material.getMaterial(configuration.screen_block()));
                        itemFrame = (ItemFrame) player.getWorld().spawnEntity(new Location(player.getWorld(), locBlock.getBlockX(), locBlock.getBlockY(), locBlock.getBlockZ() - 1), EntityType.fromName(entityName));
                    }
                    if (FacingLocation.getCardinalDirection(player).equals("W")) {
                        locBlock.add(0, 0, i);
                        locBlock.getBlock().setType(Material.getMaterial(configuration.screen_block()));
                        itemFrame = (ItemFrame) player.getWorld().spawnEntity(new Location(player.getWorld(), locBlock.getBlockX() + 1, locBlock.getBlockY(), locBlock.getBlockZ()), EntityType.fromName(entityName));
                    }

                    frames.add(itemFrame);
                    blocks.add(locBlock.getBlock());
                }
            }

            screen = new Screen(UUID.randomUUID(), width, height, frames, blocks);
            screen.createConfiguration(FacingLocation.getCardinalDirection(player), frames.get(0).getLocation());
            screen.setName(name);
            screen.setScaleMode(ScalingMode.FIT);

            for (org.bukkit.block.Block block : blocks) {
                plugin.getScreensBlocks().put(block, screen);
            }
            for (ItemFrame frame : frames) {
                plugin.getScreensFrames().put(frame, screen);
            }

            plugin.getRegisteredScreens().add(screen);
            screens.put(screen.getUUID(), screen);
            states.put(screen.getUUID(), ScreenState.IDLE);
        } catch (IllegalArgumentException | NullPointerException e) {
            if (screen != null) {
                screen.delete();
            }
        }
        return screen;
    }

    public Collection<Entity> getNearbyEntities(Location location, int radius) {
        if (plugin.isOld()) {
            int chunkRadius = radius < 16 ? 1 : (radius - (radius % 16)) / 16;
            HashSet<Entity> radiusEntities = new HashSet<>();

            for (int chunkX = 0 - chunkRadius; chunkX <= chunkRadius; chunkX++) {
                for (int chunkZ = 0 - chunkRadius; chunkZ <= chunkRadius; chunkZ++) {
                    int x = (int) location.getX(), y = (int) location.getY(), z = (int) location.getZ();
                    for (Entity entity : new Location(location.getWorld(), x + (chunkX * 16), y, z + (chunkZ * 16)).getChunk().getEntities()) {
                        if (entity.getLocation().distance(location) <= radius && entity.getLocation().getBlock() != location.getBlock()) {
                            radiusEntities.add(entity);
                        }
                    }
                }
            }
            return radiusEntities;
        }
        return location.getWorld().getNearbyEntities(location, radius, radius, radius);
    }
}
