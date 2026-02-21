package io.github.pronze.sba.manager;

import io.github.pronze.sba.SBA;
import io.github.pronze.sba.config.SBAConfig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.screamingsandals.bedwars.api.game.Game;
import org.screamingsandals.bedwars.player.BedWarsPlayerManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ItemLimitManager {
    
    private static ItemLimitManager instance;
    private final Map<UUID, Map<String, Integer>> playerItemCounts = new HashMap<>();
    
    private ItemLimitManager() {}
    
    public static ItemLimitManager getInstance() {
        if (instance == null) {
            instance = new ItemLimitManager();
        }
        return instance;
    }
    
    public boolean canPurchase(Player player, String materialName) {
        if (!SBAConfig.getInstance().isItemLimitsEnabled()) {
            return true;
        }
        
        int limit = SBAConfig.getInstance().getItemLimit(materialName);
        
        if (limit == -1) {
            return true;
        }
        
        if (limit == 0) {
            return false;
        }
        
        if (!BedWarsPlayerManager.isPlayerInGame(player.getUniqueId())) {
            return true;
        }
        
        int currentCount = getPlayerItemCount(player, materialName);
        int inventoryCount = countItemsInInventory(player, materialName);
        currentCount = Math.max(currentCount, inventoryCount);
        
        return currentCount < limit;
    }
    
    public void addPurchase(Player player, String materialName) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> playerMap = playerItemCounts.getOrDefault(uuid, new HashMap<>());
        
        int current = playerMap.getOrDefault(materialName, 0);
        playerMap.put(materialName, current + 1);
        
        playerItemCounts.put(uuid, playerMap);
    }
    
    public int getPlayerItemCount(Player player, String materialName) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> playerMap = playerItemCounts.getOrDefault(uuid, new HashMap<>());
        return playerMap.getOrDefault(materialName, 0);
    }
    
    private int countItemsInInventory(Player player, String materialName) {
        PlayerInventory inv = player.getInventory();
        int count = 0;
        
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType().name().equals(materialName)) {
                count += item.getAmount();
            }
        }
        
        for (ItemStack item : inv.getArmorContents()) {
            if (item != null && item.getType().name().equals(materialName)) {
                count += item.getAmount();
            }
        }
        
        return count;
    }
    
    public void clearPlayer(UUID uuid) {
        playerItemCounts.remove(uuid);
    }
    
    public void resetPlayer(Player player) {
        playerItemCounts.remove(player.getUniqueId());
    }
    
    public boolean hasUpgradedVersion(Player player, String baseMaterial, String[] upgradePath) {
        for (String material : upgradePath) {
            if (countItemsInInventory(player, material) > 0) {
                return true;
            }
        }
        return false;
    }
}
