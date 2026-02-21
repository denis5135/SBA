package io.github.pronze.sba.manager;

import io.github.pronze.sba.SBA;
import io.github.pronze.sba.config.SBAConfig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.game.Game;
import org.screamingsandals.bedwars.player.PlayerManagerImpl;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Трекер для отслеживания предметов игроков
 * Сохраняет информацию о купленных предметах и их улучшениях
 */
public class PlayerItemTracker {
    
    private static PlayerItemTracker instance;
    
    // Карта: UUID игрока -> (название предмета -> уровень/количество)
    private final Map<UUID, Map<String, Integer>> playerItems = new ConcurrentHashMap<>();
    
    // Карта: UUID игрока -> (тип инструмента -> текущий уровень)
    private final Map<UUID, Map<String, String>> playerToolLevels = new ConcurrentHashMap<>();
    
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
     * Устанавливает уровень инструмента
     */
    public void setToolLevel(Player player, String toolType, String level) {
        UUID uuid = player.getUniqueId();
        Map<String, String> tools = playerToolLevels.getOrDefault(uuid, new HashMap<>());
        tools.put(toolType, level);
        playerToolLevels.put(uuid, tools);
    }
    
    /**
     * Получает уровень инструмента
     */
    public String getToolLevel(Player player, String toolType) {
        UUID uuid = player.getUniqueId();
        Map<String, String> tools = playerToolLevels.get(uuid);
        
        if (tools != null) {
            return tools.get(toolType);
        }
        return null;
    }
    
    /**
     * Проверяет, есть ли у игрока этот предмет
     */
    public boolean hasItem(Player player, String materialName) {
        return getItemCount(player, materialName) > 0 || hasItemInInventory(player, materialName);
    }
    
    /**
     * Проверяет наличие предмета в инвентаре
     */
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
        playerToolLevels.remove(uuid);
        deathItems.remove(uuid);
    }
    
    /**
     * Очищает данные игрока при выходе
     */
    public void clearPlayer(UUID uuid) {
        playerItems.remove(uuid);
        playerToolLevels.remove(uuid);
        deathItems.remove(uuid);
    }
    
    /**
     * Получает статистику по предметам для отладки
     */
    public Map<String, Object> getPlayerStats(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("items", playerItems.getOrDefault(uuid, new HashMap<>()));
        stats.put("tools", playerToolLevels.getOrDefault(uuid, new HashMap<>()));
        stats.put("deathItems", deathItems.containsKey(uuid));
        
        return stats;
    }
}