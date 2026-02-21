package io.github.pronze.sba.manager;

import io.github.pronze.sba.SBA;
import io.github.pronze.sba.config.SBAConfig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.screamingsandals.bedwars.api.game.Game;
import org.screamingsandals.bedwars.player.PlayerManagerImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер для проверки лимитов на предметы
 * Запрещает покупать больше определённого количества предметов
 */
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
            return false;
        }
        
        // Проверяем, в игре ли игрок
        if (!PlayerManagerImpl.getInstance().isPlayerInGame(player.getUniqueId())) {
            return true; // Вне игры не ограничиваем
        }
        
        // Получаем текущее количество предметов у игрока
        int currentCount = getPlayerItemCount(player, materialName);
        
        // Проверяем также наличие в инвентаре
        int inventoryCount = countItemsInInventory(player, materialName);
        currentCount = Math.max(currentCount, inventoryCount);
        
        return currentCount < limit;
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
}