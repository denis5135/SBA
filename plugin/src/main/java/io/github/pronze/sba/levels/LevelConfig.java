package io.github.pronze.sba.levels;

import io.github.pronze.sba.SBA;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class LevelConfig {
    private static LevelConfig instance;
    private final File configFile;
    private YamlConfiguration config;
    
    // Используем TreeMap для автоматической сортировки по уровням
    private final NavigableMap<Integer, LevelData> levels = new TreeMap<>();
    private int maxLevel = 0;

    private LevelConfig() {
        configFile = new File(SBA.getInstance().getDataFolder(), "levels/levels.yml");
        reload();
    }

    public static LevelConfig getInstance() {
        if (instance == null) {
            instance = new LevelConfig();
        }
        return instance;
    }

    public void reload() {
        if (!configFile.exists()) {
            // Создаём дефолтный файл, если его нет
            SBA.getInstance().saveResource("levels/levels.yml", false);
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        loadLevels();
    }

    private void loadLevels() {
        levels.clear();
        
        if (!config.contains("levels")) {
            createDefaultLevels();
            return;
        }

        var levelsSection = config.getConfigurationSection("levels");
        if (levelsSection == null) {
            createDefaultLevels();
            return;
        }

        for (String levelStr : levelsSection.getKeys(false)) {
            try {
                int level = Integer.parseInt(levelStr);
                String path = "levels." + level;
                
                String prefix = config.getString(path + ".prefix", "&7[✩]");
                int xpRequired = config.getInt(path + ".xp_required", 1000 * level);
                
                levels.put(level, new LevelData(level, prefix, xpRequired));
                
                if (level > maxLevel) {
                    maxLevel = level;
                }
            } catch (NumberFormatException e) {
                // Пропускаем некорректные ключи
            }
        }
        
        // Если уровней нет, создаём дефолтные
        if (levels.isEmpty()) {
            createDefaultLevels();
        }
    }

    private void createDefaultLevels() {
        levels.clear();
        
        // Создаём 10 дефолтных уровней
        for (int i = 1; i <= 10; i++) {
            String prefix = switch (i) {
                case 1 -> "&7[✩]";
                case 2 -> "&8[✩]";
                case 3 -> "&f[✩]";
                case 4 -> "&e[✩]";
                case 5 -> "&6[✩]";
                case 6 -> "&c[✩]";
                case 7 -> "&a[✩]";
                case 8 -> "&b[✩]";
                case 9 -> "&d[✩]";
                case 10 -> "&7[★]";
                default -> "&7[✩]";
            };
            
            int xpRequired = i == 1 ? 0 : 1000 * (i - 1);
            
            levels.put(i, new LevelData(i, prefix, xpRequired));
        }
        
        maxLevel = 10;
        
        // Сохраняем дефолтные в файл
        save();
    }

    public void save() {
        for (Map.Entry<Integer, LevelData> entry : levels.entrySet()) {
            String path = "levels." + entry.getKey();
            config.set(path + ".prefix", entry.getValue().getPrefix());
            config.set(path + ".xp_required", entry.getValue().getXpRequired());
        }
        
        try {
            config.save(configFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Получить префикс для уровня
     */
    public String getLevelPrefix(int level) {
        LevelData data = levels.get(level);
        if (data == null) {
            // Если уровень не найден, берём ближайший меньший
            Map.Entry<Integer, LevelData> lower = levels.lowerEntry(level);
            if (lower != null) {
                return ChatColor.translateAlternateColorCodes('&', lower.getValue().getPrefix());
            }
            return "&7[✩]";
        }
        return ChatColor.translateAlternateColorCodes('&', data.getPrefix());
    }

    /**
     * Получить требуемый опыт для уровня
     */
    public int getRequiredXP(int level) {
        LevelData data = levels.get(level);
        if (data == null) {
            // Если уровень не найден, интерполируем
            Map.Entry<Integer, LevelData> lower = levels.lowerEntry(level);
            Map.Entry<Integer, LevelData> higher = levels.higherEntry(level);
            
            if (lower != null && higher != null) {
                // Линейная интерполяция между ближайшими уровнями
                int lowerLevel = lower.getKey();
                int higherLevel = higher.getKey();
                int lowerXP = lower.getValue().getXpRequired();
                int higherXP = higher.getValue().getXpRequired();
                
                double progress = (double)(level - lowerLevel) / (higherLevel - lowerLevel);
                return lowerXP + (int)((higherXP - lowerXP) * progress);
            } else if (lower != null) {
                // Если есть только меньший уровень, экстраполируем
                return lower.getValue().getXpRequired() + 1000;
            } else if (higher != null) {
                // Если есть только больший уровень
                return higher.getValue().getXpRequired() - 1000;
            }
            return 1000 * level;
        }
        return data.getXpRequired();
    }

    /**
     * Получить максимальный уровень
     */
    public int getMaxLevel() {
        return maxLevel;
    }

    /**
     * Проверить, существует ли уровень
     */
    public boolean hasLevel(int level) {
        return levels.containsKey(level);
    }

    /**
     * Получить уровень по количеству опыта
     */
    public int getLevelByXP(int xp) {
        int level = 1;
        for (Map.Entry<Integer, LevelData> entry : levels.entrySet()) {
            if (entry.getValue().getXpRequired() <= xp) {
                level = entry.getKey();
            } else {
                break;
            }
        }
        return level;
    }

    /**
     * Получить данные уровня
     */
    public LevelData getLevelData(int level) {
        return levels.get(level);
    }

    /**
     * Класс данных уровня
     */
    public static class LevelData {
        private final int level;
        private final String prefix;
        private final int xpRequired;

        public LevelData(int level, String prefix, int xpRequired) {
            this.level = level;
            this.prefix = prefix;
            this.xpRequired = xpRequired;
        }

        public int getLevel() {
            return level;
        }

        public String getPrefix() {
            return prefix;
        }

        public int getXpRequired() {
            return xpRequired;
        }
    }
}
