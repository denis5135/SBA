package io.github.pronze.sba.levels;

import io.github.pronze.sba.SBA;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlayerLevelManager {
    private static PlayerLevelManager instance;
    private final File dataFile;
    private YamlConfiguration data;
    private final Map<UUID, PlayerData> cache = new HashMap<>();
    private LevelConfig levelConfig;

    private PlayerLevelManager() {
        dataFile = new File(SBA.getPluginInstance().getDataFolder(), "player-levels.yml");
        levelConfig = LevelConfig.getInstance();
        load();
    }

    public static PlayerLevelManager getInstance() {
        if (instance == null) {
            instance = new PlayerLevelManager();
        }
        return instance;
    }

    private void load() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void save() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void savePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData playerData = cache.get(uuid);
        if (playerData != null) {
            data.set(uuid.toString() + ".xp", playerData.xp);
        }
    }

    public int getPlayerLevel(Player player) {
        return getPlayerLevel(player.getUniqueId());
    }
    
    public int getPlayerLevel(UUID uuid) {
        PlayerData data = getPlayerData(uuid);
        return data.level;
    }

    public int getPlayerXP(Player player) {
        return getPlayerXP(player.getUniqueId());
    }
    
    public int getPlayerXP(UUID uuid) {
        PlayerData data = getPlayerData(uuid);
        return data.xp;
    }

    public String getPlayerPrefix(Player player) {
        int level = getPlayerLevel(player);
        return levelConfig.getLevelPrefix(level);
    }
    
    public String getPlayerPrefix(UUID uuid) {
        int level = getPlayerLevel(uuid);
        return levelConfig.getLevelPrefix(level);
    }

    public void addXP(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        PlayerData playerData = getPlayerData(uuid);
        
        int oldLevel = playerData.level;
        playerData.xp += amount;
        
        playerData.level = levelConfig.getLevelByXP(playerData.xp);
        
        if (playerData.level > oldLevel) {
            player.sendMessage("§aВы достигли " + playerData.level + " уровня!");
        }
        
        cache.put(uuid, playerData);
        data.set(uuid.toString() + ".xp", playerData.xp);
        save();
    }

    public void setXP(Player player, int xp) {
        UUID uuid = player.getUniqueId();
        int level = levelConfig.getLevelByXP(xp);
        
        cache.put(uuid, new PlayerData(xp, level));
        data.set(uuid.toString() + ".xp", xp);
        save();
    }

    public int getXPToNextLevel(Player player) {
        int currentLevel = getPlayerLevel(player);
        int currentXP = getPlayerXP(player);
        
        if (currentLevel >= levelConfig.getMaxLevel()) {
            return 0;
        }
        
        int requiredForNext = levelConfig.getRequiredXP(currentLevel + 1);
        return Math.max(0, requiredForNext - currentXP);
    }

    public double getLevelProgress(Player player) {
        int currentLevel = getPlayerLevel(player);
        int currentXP = getPlayerXP(player);
        
        if (currentLevel >= levelConfig.getMaxLevel()) {
            return 1.0;
        }
        
        int currentLevelXP = levelConfig.getRequiredXP(currentLevel);
        int nextLevelXP = levelConfig.getRequiredXP(currentLevel + 1);
        
        return (double)(currentXP - currentLevelXP) / (nextLevelXP - currentLevelXP);
    }

    private PlayerData getPlayerData(UUID uuid) {
        if (cache.containsKey(uuid)) {
            return cache.get(uuid);
        }
        
        int xp = data.getInt(uuid.toString() + ".xp", 0);
        int level = levelConfig.getLevelByXP(xp);
        
        PlayerData playerData = new PlayerData(xp, level);
        cache.put(uuid, playerData);
        return playerData;
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        int xp = data.getInt(uuid.toString() + ".xp", 0);
        int level = levelConfig.getLevelByXP(xp);
        cache.put(uuid, new PlayerData(xp, level));
    }

    public void unloadPlayer(Player player) {
        savePlayer(player);
        cache.remove(player.getUniqueId());
    }

    public void reloadConfig() {
        levelConfig.reload();
        
        for (Map.Entry<UUID, PlayerData> entry : cache.entrySet()) {
            entry.getValue().level = levelConfig.getLevelByXP(entry.getValue().xp);
        }
    }
    
    /**
     * Получить топ игроков по опыту
     * @param limit количество игроков в топе (максимум 20)
     * @return список Map.Entry с UUID и опытом, отсортированный по убыванию опыта
     */
    public List<Map.Entry<UUID, Integer>> getTopPlayers(int limit) {
        // Ограничиваем максимум 20
        int maxLimit = Math.min(limit, 20);
        
        Map<UUID, Integer> allPlayersXP = new HashMap<>();
        
        // Добавляем игроков из кэша
        for (Map.Entry<UUID, PlayerData> entry : cache.entrySet()) {
            allPlayersXP.put(entry.getKey(), entry.getValue().xp);
        }
        
        // Сортируем по убыванию опыта
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(allPlayersXP.entrySet());
        sorted.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
        
        // Возвращаем только первые maxLimit элементов
        return sorted.stream().limit(maxLimit).collect(Collectors.toList());
    }

    private static class PlayerData {
        int xp;
        int level;
        
        PlayerData(int xp, int level) {
            this.xp = xp;
            this.level = level;
        }
    }
}
