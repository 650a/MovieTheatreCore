package com._650a.movietheatrecore.theatre;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class AudioZone {

    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final int radius;

    public AudioZone(String world, double x, double y, double z, int radius) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public int getRadius() {
        return radius;
    }

    public Location toLocation() {
        if (world == null || world.isBlank()) {
            return null;
        }
        World worldInstance = Bukkit.getWorld(world);
        if (worldInstance == null) {
            return null;
        }
        return new Location(worldInstance, x, y, z);
    }

    public boolean isValid() {
        return world != null && !world.isBlank() && radius > 0;
    }

    public static AudioZone fromLocation(Location location, int radius) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new AudioZone(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), radius);
    }
}
