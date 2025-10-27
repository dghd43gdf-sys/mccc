package de.popcornsmp.popcornsmp;

import org.bukkit.Location;
import org.bukkit.Material;

public class HomeData {
    private final String name;
    private final Location location;
    private final Material icon;

    public HomeData(String name, Location location, Material icon) {
        this.name = name;
        this.location = location;
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public Material getIcon() {
        return icon;
    }
}
