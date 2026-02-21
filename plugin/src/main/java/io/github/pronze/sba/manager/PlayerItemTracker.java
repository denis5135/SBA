package io.github.pronze.sba.manager;

import io.github.pronze.sba.SBA;
import io.github.pronze.sba.config.SBAConfig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.game.Game;
import org.screamingsandals.bedwars.player.BedWarsPlayerManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerItemTracker {
    
    private static PlayerItemTracker instance;
    private final Map<UUID, Map<String, Integer>> playerItems = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> playerToolLevels = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> deathItems = new ConcurrentHashMap<>();
    
    private PlayerItemTracker() {}
    
    public static PlayerItemTracker getInstance() {
        if (instance == null) {
            instance = new PlayerItemTracker();
        }
        return instance;
    }
    
    public void trackItem(Player player, String materialName, int amount) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> items = playerItems.getOrDefault(uuid, new HashMap<>());
        
        int current = items.getOrDefault(materialName, 0);
        items.put(materialName, current + amount);
        
        playerItems.put(uuid, items);
    }
    
    public void untrackItem(Player player, String materialName, int amount) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> items = playerItems.get(uuid);
        
        if (items != null) {
            int current = items.getOrDefault(materialName, 0);
            int newValue = Math.max(0, current - amount);
            
            if (newValue <= 0) {
                items.remove(materialName);
            } else {
                items.put(materialName, newValue);
            }
            
            if (items.isEmpty()) {
                playerItems.remove(uuid);
            } else {
                playerItems.put(uuid, items);
            }
        }
    }
    
    public int getItemCount(Player player, String materialName) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> items = playerItems.get(uuid);
        
        if (items != null) {
            return items.getOrDefault(materialName, 0);
        }
        return 0;
    }
    
    public void setToolLevel(Player player, String toolType, String level) {
        UUID uuid = player.getUniqueId();
        Map<String, String> tools = playerToolLevels.getOrDefault(uuid, new HashMap<>());
        tools.put(toolType, level);
        playerToolLevels.put(uuid, tools);
    }
    
    public String getToolLevel(Player player, String toolType) {
        UUID uuid = player.getUniqueId();
        Map<String, String> tools = playerToolLevels.get(uuid);
        
        if (tools != null) {
            return tools.get(toolType);
        }
        return null;
    }
    
    public boolean hasItem(Player player, String materialName) {
        return getItemCount(player, materialName) > 0 || hasItemInInventory(player, materialName);
    }
    
    private boolean hasItemInInventory(Player player, String materialName) {
        PlayerInventory inv = player.getInventory();
        
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType().name().equals(materialName)) {
                return true;
            }
        }
        
        for (ItemStack item : inv.getArmorContents()) {
            if (item != null && item.getType().name().equals(materialName)) {
                return true;
            }
        }
        
        return false;
    }
    
    public void trackDeathItems(Player player, List<ItemStack> items) {
        deathItems.put(player.getUniqueId(), new ArrayList<>(items));
    }
    
    public List<ItemStack> getDeathItems(Player player) {
        return deathItems.getOrDefault(player.getUniqueId(), new ArrayList<>());
    }
    
    public void clearDeathItems(Player player) {
        deathItems.remove(player.getUniqueId());
    }
    
    public void resetPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        playerItems.remove(uuid);
        playerToolLevels.remove(uuid);
        deathItems.remove(uuid);
    }
    
    public void clearPlayer(UUID uuid) {
        playerItems.remove(uuid);
        playerToolLevels.remove(uuid);
        deathItems.remove(uuid);
    }
    
    public Map<String, Object> getPlayerStats(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("items", playerItems.getOrDefault(uuid, new HashMap<>()));
        stats.put("tools", playerToolLevels.getOrDefault(uuid, new HashMap<>()));
        stats.put("deathItems", deathItems.containsKey(uuid));
        
        return stats;
    }
}
