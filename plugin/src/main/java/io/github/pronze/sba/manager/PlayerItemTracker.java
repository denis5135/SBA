package io.github.pronze.sba.manager;

import io.github.pronze.sba.SBA;
import io.github.pronze.sba.config.SBAConfig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerItemTracker {
    
    private static PlayerItemTracker instance;
    
    // Карта: UUID игрока -> (название предмета -> количество)
    private final Map<UUID, Map<String, Integer>> playerItems = new ConcurrentHashMap<>();
    
    // Карта: UUID игрока -> (название предмета -> следующий уровень)
    private final Map<UUID, Map<String, String>> playerUpgrades = new ConcurrentHashMap<>();
    
    // Карта: UUID игрока -> список предметов для очистки при смерти
    private final Map<UUID, List<ItemStack>> deathItems = new ConcurrentHashMap<>();
    
    private PlayerItemTracker() {}
    
    public static PlayerItemTracker getInstance() {
        if (instance == null) {
            instance = new PlayerItemTracker();
        }
        return instance;
    }
    
    /**
     * Добавляет предмет в трекер
     */
    public void trackItem(Player player, String materialName, int amount) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> items = playerItems.getOrDefault(uuid, new HashMap<>());
        
        int current = items.getOrDefault(materialName, 0);
        items.put(materialName, current + amount);
        
        playerItems.put(uuid, items);
    }
    
    /**
     * Удаляет предмет из трекера
     */
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
    
    /**
     * Получает количество предметов у игрока
     */
    public int getItemCount(Player player, String materialName) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> items = playerItems.get(uuid);
        
        if (items != null) {
            return items.getOrDefault(materialName, 0);
        }
        return 0;
    }
    
    /**
     * Сохраняет следующий уровень для улучшаемого предмета
     */
    public void setNextUpgrade(Player player, String currentItem, String nextItem) {
        UUID uuid = player.getUniqueId();
        Map<String, String> upgrades = playerUpgrades.getOrDefault(uuid, new HashMap<>());
        upgrades.put(currentItem, nextItem);
        playerUpgrades.put(uuid, upgrades);
    }
    
    /**
     * Получает следующий уровень для предмета
     */
    public String getNextUpgrade(Player player, String currentItem) {
        UUID uuid = player.getUniqueId();
        Map<String, String> upgrades = playerUpgrades.get(uuid);
        if (upgrades != null) {
            return upgrades.get(currentItem);
        }
        return null;
    }
    
    /**
     * Проверяет, купил ли игрок этот предмет (для лимитов)
     */
    public boolean hasReachedLimit(Player player, String materialName, int limit) {
        if (limit <= 0) return false;
        
        int currentCount = getItemCount(player, materialName);
        int inventoryCount = countItemsInInventory(player, materialName);
        
        return (currentCount + inventoryCount) >= limit;
    }
    
    /**
     * Подсчитывает количество предметов в инвентаре
     */
    public int countItemsInInventory(Player player, String materialName) {
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
    
    /**
     * Проверяет наличие предмета в инвентаре
     */
    public boolean hasItemInInventory(Player player, String materialName) {
        return countItemsInInventory(player, materialName) > 0;
    }
    
    /**
     * Сохраняет предметы, которые нужно удалить при смерти
     */
    public void trackDeathItems(Player player, List<ItemStack> items) {
        deathItems.put(player.getUniqueId(), new ArrayList<>(items));
    }
    
    /**
     * Получает предметы для удаления при смерти
     */
    public List<ItemStack> getDeathItems(Player player) {
        return deathItems.getOrDefault(player.getUniqueId(), new ArrayList<>());
    }
    
    /**
     * Очищает данные игрока при смерти
     */
    public void clearDeathItems(Player player) {
        deathItems.remove(player.getUniqueId());
    }
    
    /**
     * Сбрасывает все данные игрока
     */
    public void resetPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        playerItems.remove(uuid);
        playerUpgrades.remove(uuid);
        deathItems.remove(uuid);
    }
    
    /**
     * Очищает данные игрока при выходе
     */
    public void clearPlayer(UUID uuid) {
        playerItems.remove(uuid);
        playerUpgrades.remove(uuid);
        deathItems.remove(uuid);
    }
    
    /**
     * Получает статистику по предметам для отладки
     */
    public Map<String, Object> getPlayerStats(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("items", playerItems.getOrDefault(uuid, new HashMap<>()));
        stats.put("upgrades", playerUpgrades.getOrDefault(uuid, new HashMap<>()));
        stats.put("deathItems", deathItems.containsKey(uuid));
        
        return stats;
    }
}
