package de.popcornsmp.popcornsmp;

import java.util.*;

public class PlayerHomeManager {
    private final Map<Integer, HomeData> homes;
    private final Set<Integer> unlockedSlots;

    public PlayerHomeManager() {
        this.homes = new HashMap<>();
        this.unlockedSlots = new HashSet<>();
        for (int i = 0; i < 7; i++) {
            unlockedSlots.add(i);
        }
    }

    public boolean hasHome(int slot) {
        return homes.containsKey(slot);
    }

    public HomeData getHome(int slot) {
        return homes.get(slot);
    }

    public HomeData getHomeByName(String name) {
        for (HomeData home : homes.values()) {
            if (home.getName().equalsIgnoreCase(name)) {
                return home;
            }
        }
        return null;
    }

    public void setHome(int slot, HomeData home) {
        homes.put(slot, home);
    }

    public boolean removeHomeByName(String name) {
        for (Map.Entry<Integer, HomeData> entry : homes.entrySet()) {
            if (entry.getValue().getName().equalsIgnoreCase(name)) {
                homes.remove(entry.getKey());
                return true;
            }
        }
        return false;
    }

    public void removeHome(int slot) {
        homes.remove(slot);
    }

    public boolean isSlotUnlocked(int slot) {
        return unlockedSlots.contains(slot);
    }

    public void unlockSlot(int slot) {
        unlockedSlots.add(slot);
    }

    public Map<Integer, HomeData> getAllHomes() {
        return new HashMap<>(homes);
    }

    public Set<Integer> getUnlockedSlots() {
        return new HashSet<>(unlockedSlots);
    }

    public int getNextFreeSlot() {
        for (int i = 0; i < 14; i++) {
            if (unlockedSlots.contains(i) && !homes.containsKey(i)) {
                return i;
            }
        }
        return -1;
    }

    public int getNextFreeSlotInRange(int start, int end) {
        for (int i = start; i <= end; i++) {
            if (unlockedSlots.contains(i) && !homes.containsKey(i)) {
                return i;
            }
        }
        return -1;
    }

    public int getHomeCount() {
        return homes.size();
    }

    public enum SlotType {
        FREE,
        PURCHASABLE,
        ELITE,
        POPCORN
    }

    public static SlotType getSlotType(int slot) {
        if (slot < 7) {
            return SlotType.FREE;
        } else if (slot >= 7 && slot <= 8) {
            return SlotType.PURCHASABLE;
        } else if (slot >= 9 && slot <= 10) {
            return SlotType.ELITE;
        } else if (slot >= 11 && slot <= 13) {
            return SlotType.POPCORN;
        }
        return SlotType.FREE;
    }
}
