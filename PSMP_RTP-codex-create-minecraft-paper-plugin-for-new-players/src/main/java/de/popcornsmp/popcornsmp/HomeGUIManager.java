package de.popcornsmp.popcornsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class HomeGUIManager {

    private static final String HOME_GUI_TITLE = "§6§lDeine Homes";
    private static final String ICON_GUI_TITLE = "§6§lWähle ein Icon";

    public static Inventory createHomeGUI(Player player, PlayerHomeManager homeManager) {
        Inventory inv = Bukkit.createInventory(null, 27, HOME_GUI_TITLE);

        boolean hasElite = player.hasPermission("popcorn.rank.elite") ||
                          player.hasPermission("popcorn.rank.popcorn") ||
                          player.hasPermission("popcorn.rank.admin") ||
                          player.hasPermission("popcorn.rank.owner");
        boolean hasPopcorn = player.hasPermission("popcorn.rank.popcorn") ||
                            player.hasPermission("popcorn.rank.admin") ||
                            player.hasPermission("popcorn.rank.owner");

        for (int i = 0; i < 7; i++) {
            if (homeManager.hasHome(i)) {
                HomeData home = homeManager.getHome(i);
                ItemStack item = new ItemStack(home.getIcon());
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text(home.getName(), NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false));
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.text("Standard Slot", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Klicke zum Teleportieren", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Shift + Rechtsklick zum Löschen", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                    item.setItemMeta(meta);
                }
                inv.setItem(i + 10, item);
            } else if (homeManager.isSlotUnlocked(i)) {
                ItemStack greenPane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                ItemMeta meta = greenPane.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text("Home setzen", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false));
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.text("Standard Slot", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Klicke um ein Home zu setzen", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                    greenPane.setItemMeta(meta);
                }
                inv.setItem(i + 10, greenPane);
            }
        }

        for (int i = 7; i < 14; i++) {
            PlayerHomeManager.SlotType slotType = PlayerHomeManager.getSlotType(i);

            if (homeManager.hasHome(i)) {
                HomeData home = homeManager.getHome(i);
                ItemStack item = new ItemStack(home.getIcon());
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text(home.getName(), NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false));
                    List<Component> lore = new ArrayList<>();

                    if (slotType == PlayerHomeManager.SlotType.PURCHASABLE) {
                        lore.add(Component.text("Gekaufter Slot", NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false));
                    } else if (slotType == PlayerHomeManager.SlotType.ELITE) {
                        lore.add(Component.text("Elite Slot", NamedTextColor.LIGHT_PURPLE)
                                .decoration(TextDecoration.ITALIC, false));
                    } else if (slotType == PlayerHomeManager.SlotType.POPCORN) {
                        lore.add(Component.text("Popcorn Slot", NamedTextColor.GOLD)
                                .decoration(TextDecoration.ITALIC, false));
                    }

                    lore.add(Component.text("", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Klicke zum Teleportieren", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Shift + Rechtsklick zum Löschen", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                    item.setItemMeta(meta);
                }
                inv.setItem(i + 12, item);
            } else if (homeManager.isSlotUnlocked(i)) {
                ItemStack greenPane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                ItemMeta meta = greenPane.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text("Home setzen", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false));
                    List<Component> lore = new ArrayList<>();

                    if (slotType == PlayerHomeManager.SlotType.PURCHASABLE) {
                        lore.add(Component.text("Gekaufter Slot", NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false));
                    } else if (slotType == PlayerHomeManager.SlotType.ELITE) {
                        lore.add(Component.text("Elite Slot", NamedTextColor.LIGHT_PURPLE)
                                .decoration(TextDecoration.ITALIC, false));
                    } else if (slotType == PlayerHomeManager.SlotType.POPCORN) {
                        lore.add(Component.text("Popcorn Slot", NamedTextColor.GOLD)
                                .decoration(TextDecoration.ITALIC, false));
                    }

                    lore.add(Component.text("", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text("Klicke um ein Home zu setzen", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                    greenPane.setItemMeta(meta);
                }
                inv.setItem(i + 12, greenPane);
            } else {
                if (slotType == PlayerHomeManager.SlotType.PURCHASABLE) {
                    ItemStack redPane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                    ItemMeta meta = redPane.getItemMeta();
                    if (meta != null) {
                        meta.displayName(Component.text("Slot kaufen", NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false));
                        List<Component> lore = new ArrayList<>();
                        lore.add(Component.text("Kosten: 32 Diamanten", NamedTextColor.GOLD)
                                .decoration(TextDecoration.ITALIC, false));
                        lore.add(Component.text("Klicke zum Kaufen", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false));
                        meta.lore(lore);
                        redPane.setItemMeta(meta);
                    }
                    inv.setItem(i + 12, redPane);
                } else if (slotType == PlayerHomeManager.SlotType.ELITE) {
                    if (hasElite) {
                        ItemStack purplePane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                        ItemMeta meta = purplePane.getItemMeta();
                        if (meta != null) {
                            meta.displayName(Component.text("Home setzen", NamedTextColor.GREEN)
                                    .decoration(TextDecoration.ITALIC, false));
                            List<Component> lore = new ArrayList<>();
                            lore.add(Component.text("Elite Slot", NamedTextColor.LIGHT_PURPLE)
                                    .decoration(TextDecoration.ITALIC, false));
                            lore.add(Component.text("Klicke um ein Home zu setzen", NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false));
                            meta.lore(lore);
                            purplePane.setItemMeta(meta);
                        }
                        homeManager.unlockSlot(i);
                        inv.setItem(i + 12, purplePane);
                    } else {
                        ItemStack purplePane = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
                        ItemMeta meta = purplePane.getItemMeta();
                        if (meta != null) {
                            meta.displayName(Component.text("Elite Slot", NamedTextColor.LIGHT_PURPLE)
                                    .decoration(TextDecoration.ITALIC, false));
                            List<Component> lore = new ArrayList<>();
                            lore.add(Component.text("Benötigt Elite Rang", NamedTextColor.RED)
                                    .decoration(TextDecoration.ITALIC, false));
                            meta.lore(lore);
                            purplePane.setItemMeta(meta);
                        }
                        inv.setItem(i + 12, purplePane);
                    }
                } else if (slotType == PlayerHomeManager.SlotType.POPCORN) {
                    if (hasPopcorn) {
                        ItemStack orangePane = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
                        ItemMeta meta = orangePane.getItemMeta();
                        if (meta != null) {
                            meta.displayName(Component.text("Home setzen", NamedTextColor.GREEN)
                                    .decoration(TextDecoration.ITALIC, false));
                            List<Component> lore = new ArrayList<>();
                            lore.add(Component.text("Popcorn Slot", NamedTextColor.GOLD)
                                    .decoration(TextDecoration.ITALIC, false));
                            lore.add(Component.text("Klicke um ein Home zu setzen", NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false));
                            meta.lore(lore);
                            orangePane.setItemMeta(meta);
                        }
                        homeManager.unlockSlot(i);
                        inv.setItem(i + 12, orangePane);
                    } else {
                        ItemStack orangePane = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
                        ItemMeta meta = orangePane.getItemMeta();
                        if (meta != null) {
                            meta.displayName(Component.text("Popcorn Slot", NamedTextColor.GOLD)
                                    .decoration(TextDecoration.ITALIC, false));
                            List<Component> lore = new ArrayList<>();
                            lore.add(Component.text("Benötigt Popcorn Rang", NamedTextColor.RED)
                                    .decoration(TextDecoration.ITALIC, false));
                            meta.lore(lore);
                            orangePane.setItemMeta(meta);
                        }
                        inv.setItem(i + 12, orangePane);
                    }
                }
            }
        }

        return inv;
    }

    public static Inventory createIconSelectionGUI() {
        Inventory inv = Bukkit.createInventory(null, 54, ICON_GUI_TITLE);

        Material[] icons = {
            Material.RED_BED, Material.BLUE_BED, Material.GREEN_BED, Material.YELLOW_BED,
            Material.OAK_LOG, Material.STONE, Material.COBBLESTONE, Material.DIRT,
            Material.GRASS_BLOCK, Material.SAND, Material.GLASS, Material.NETHERRACK,
            Material.END_STONE, Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.GOLD_ORE,
            Material.IRON_ORE, Material.COAL_ORE, Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK,
            Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.CHEST, Material.CRAFTING_TABLE,
            Material.FURNACE, Material.ANVIL, Material.ENCHANTING_TABLE, Material.BOOKSHELF,
            Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE, Material.DIAMOND_SHOVEL,
            Material.IRON_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE, Material.IRON_SHOVEL,
            Material.BOW, Material.ARROW, Material.SHIELD, Material.FISHING_ROD,
            Material.COMPASS, Material.CLOCK, Material.ENDER_PEARL, Material.ENDER_EYE,
            Material.CAKE, Material.COOKIE, Material.APPLE, Material.GOLDEN_APPLE,
            Material.BEACON, Material.NETHER_STAR, Material.ELYTRA, Material.TRIDENT
        };

        for (int i = 0; i < icons.length && i < 54; i++) {
            ItemStack item = new ItemStack(icons[i]);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String materialName = icons[i].name().replace("_", " ");
                meta.displayName(Component.text(materialName, NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }

        return inv;
    }

    public static int getSlotFromInventorySlot(int inventorySlot) {
        if (inventorySlot >= 10 && inventorySlot <= 16) {
            return inventorySlot - 10;
        } else if (inventorySlot >= 19 && inventorySlot <= 25) {
            return inventorySlot - 12;
        }
        return -1;
    }
}
