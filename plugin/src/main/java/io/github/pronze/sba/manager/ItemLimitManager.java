package io.github.pronze.sba.manager;

import io.github.pronze.sba.SBA;
import io.github.pronze.sba.config.SBAConfig;
import io.github.pronze.sba.lib.lang.LanguageService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.game.Game;
import org.screamingsandals.lib.player.Players;

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
    
    /**
     * Проверяет, может ли игрок купить предмет
     * @param player игрок
     * @param materialName название материала
     * @return true если может купить, false если лимит исчерпан
     */
    public boolean canPurchase(Player player, String materialName) {
        // Проверяем, включены ли лимиты
        if (!SBAConfig.getInstance().isItemLimitsEnabled()) {
            return true;
        }
        
        // Получаем лимит для предмета
        int limit = SBAConfig.getInstance().getItemLimit(materialName);
        
        // -1 значит безлимит
        if (limit == -1) {
            return true;
        }
        
        // 0 значит нельзя купить
        if (limit == 0) {
            LanguageService
                .getInstance()
                .get("item_limits.cannot_buy")
                .send(Players.wrapPlayer(player));
            return false;
        }
        
        // Проверяем, в игре ли игрок
        Game game = (Game) Main.getInstance().getGameOfPlayer(player);
        if (game == null) {
            return true; // Вне игры не ограничиваем
        }
        
        // Получаем текущее количество предметов у игрока
        int currentCount = getPlayerItemCount(player, materialName);
        
        // Проверяем также наличие в инвентаре
        int inventoryCount = countItemsInInventory(player, materialName);
        
        // Берем максимальное значение (учтенные покупки + то что уже в инвентаре)
        currentCount = Math.max(currentCount, inventoryCount);
        
        if (currentCount >= limit) {
            if (limit == 1) {
                // Для уникальных предметов (кирка, меч, топор и т.д.)
                LanguageService
                    .getInstance()
                    .get("item_limits.single_item")
                    .send(Players.wrapPlayer(player));
            } else {
                // Для количественных предметов (TNT, блоки и т.д.)
                LanguageService
                    .getInstance()
                    .get("item_limits.max_items")
                    .replace("%limit%", String.valueOf(limit))
                    .send(Players.wrapPlayer(player));
            }
            return false;
        }
        
        return true;
    }
    
    /**
     * Увеличивает счётчик предметов у игрока после покупки
     */
    public void addPurchase(Player player, String materialName) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> playerMap = playerItemCounts.getOrDefault(uuid, new HashMap<>());
        
        int current = playerMap.getOrDefault(materialName, 0);
        playerMap.put(materialName, current + 1);
        
        playerItemCounts.put(uuid, playerMap);
    }
    
    /**
     * Уменьшает счётчик предметов у игрока (например, при смерти)
     */
    public void removePurchase(Player player, String materialName, int amount) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> playerMap = playerItemCounts.get(uuid);
        
        if (playerMap != null) {
            int current = playerMap.getOrDefault(materialName, 0);
            int newValue = Math.max(0, current - amount);
            
            if (newValue <= 0) {
                playerMap.remove(materialName);
            } else {
                playerMap.put(materialName, newValue);
            }
            
            if (playerMap.isEmpty()) {
                playerItemCounts.remove(uuid);
            } else {
                playerItemCounts.put(uuid, playerMap);
            }
        }
    }
    
    /**
     * Получает текущее количество предметов у игрока
     */
    public int getPlayerItemCount(Player player, String materialName) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> playerMap = playerItemCounts.getOrDefault(uuid, new HashMap<>());
        return playerMap.getOrDefault(materialName, 0);
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
     * Очищает данные игрока при выходе
     */
    public void clearPlayer(UUID uuid) {
        playerItemCounts.remove(uuid);
    }
    
    /**
     * Сбрасывает счётчики для игрока (например, при новой игре)
     */
    public void resetPlayer(Player player) {
        playerItemCounts.remove(player.getUniqueId());
    }
    
    /**
     * Проверяет, есть ли у игрока улучшенная версия предмета
     * (для инструментов, которые прокачиваются)
     */
    public boolean hasUpgradedVersion(Player player, String baseMaterial, String[] upgradePath) {
        for (String material : upgradePath) {
            if (countItemsInInventory(player, material) > 0) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Получает статистику по предметам для отладки
     */
    public Map<String, Object> getPlayerStats(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("counts", playerItemCounts.getOrDefault(uuid, new HashMap<>()));
        stats.put("inventory", countAllItemsInInventory(player));
        
        return stats;
    }
    
    /**
     * Подсчитывает все предметы в инвентаре (для отладки)
     */
    private Map<String, Integer> countAllItemsInInventory(Player player) {
        Map<String, Integer> counts = new HashMap<>();
        PlayerInventory inv = player.getInventory();
        
        for (ItemStack item : inv.getContents()) {
            if (item != null) {
                String name = item.getType().name();
                counts.put(name, counts.getOrDefault(name, 0) + item.getAmount());
            }
        }
        
        return counts;
    }
}
