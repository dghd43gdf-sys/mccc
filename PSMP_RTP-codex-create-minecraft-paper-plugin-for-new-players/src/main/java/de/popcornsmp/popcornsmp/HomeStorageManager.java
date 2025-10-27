package de.popcornsmp.popcornsmp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HomeStorageManager {

    private final File dataFolder;
    private final Logger logger;

    public HomeStorageManager(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public void savePlayerHomes(UUID playerId, PlayerHomeManager homeManager) {
        File playerFile = new File(dataFolder, playerId.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        config.set("unlocked-slots", homeManager.getUnlockedSlots().stream().toList());

        for (int slot = 0; slot < 14; slot++) {
            if (homeManager.hasHome(slot)) {
                HomeData home = homeManager.getHome(slot);
                String path = "homes." + slot;

                config.set(path + ".name", home.getName());
                config.set(path + ".icon", home.getIcon().name());

                Location loc = home.getLocation();
                config.set(path + ".world", loc.getWorld().getName());
                config.set(path + ".x", loc.getX());
                config.set(path + ".y", loc.getY());
                config.set(path + ".z", loc.getZ());
                config.set(path + ".yaw", loc.getYaw());
                config.set(path + ".pitch", loc.getPitch());
            }
        }

        try {
            config.save(playerFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Fehler beim Speichern der Homes für Spieler " + playerId, e);
        }
    }

    public PlayerHomeManager loadPlayerHomes(UUID playerId) {
        File playerFile = new File(dataFolder, playerId.toString() + ".yml");
        PlayerHomeManager homeManager = new PlayerHomeManager();

        if (!playerFile.exists()) {
            return homeManager;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

        if (config.contains("unlocked-slots")) {
            for (int slot : config.getIntegerList("unlocked-slots")) {
                homeManager.unlockSlot(slot);
            }
        } else {
            for (int i = 0; i < 7; i++) {
                homeManager.unlockSlot(i);
            }
        }

        ConfigurationSection homesSection = config.getConfigurationSection("homes");
        if (homesSection != null) {
            for (String slotKey : homesSection.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotKey);
                    String path = "homes." + slot;

                    String name = config.getString(path + ".name");
                    String iconName = config.getString(path + ".icon");
                    String worldName = config.getString(path + ".world");
                    double x = config.getDouble(path + ".x");
                    double y = config.getDouble(path + ".y");
                    double z = config.getDouble(path + ".z");
                    float yaw = (float) config.getDouble(path + ".yaw");
                    float pitch = (float) config.getDouble(path + ".pitch");

                    World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        logger.warning("Welt " + worldName + " nicht gefunden für Home in Slot " + slot);
                        continue;
                    }

                    Material icon = Material.valueOf(iconName);
                    Location location = new Location(world, x, y, z, yaw, pitch);
                    HomeData home = new HomeData(name, location, icon);
                    homeManager.setHome(slot, home);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Fehler beim Laden von Home in Slot " + slotKey, e);
                }
            }
        }

        return homeManager;
    }

    public void deletePlayerHomes(UUID playerId) {
        File playerFile = new File(dataFolder, playerId.toString() + ".yml");
        if (playerFile.exists()) {
            playerFile.delete();
        }
    }
}
