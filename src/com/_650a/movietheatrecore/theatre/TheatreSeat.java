package com._650a.movietheatrecore.theatre;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class TheatreSeat {

    private final String world;
    private final double x;
    private final double y;
    private final double z;

    public TheatreSeat(String world, double x, double y, double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
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

    public Location toLocation() {
        World worldInstance = world == null ? null : Bukkit.getWorld(world);
        if (worldInstance == null) {
            return null;
        }
        return new Location(worldInstance, x, y, z);
    }

    public static TheatreSeat fromLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new TheatreSeat(location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
    }

    public static TheatreSeat parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 4) {
            return null;
        }
        try {
            String world = parts[0];
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            return new TheatreSeat(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("%s,%.3f,%.3f,%.3f", world, x, y, z);
    }
}
