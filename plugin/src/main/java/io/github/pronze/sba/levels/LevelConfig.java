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
        
        if (levels.isEmpty()) {
            createDefaultLevels();
        }
    }

    private void createDefaultLevels() {
        levels.clear();
        
        // Старый добрый switch/case для Java 11
        for (int i = 1; i <= 10; i++) {
            String prefix;
            switch (i) {
                case 1:
                    prefix = "&7[✩]";
                    break;
                case 2:
                    prefix = "&8[✩]";
                    break;
                case 3:
                    prefix = "&f[✩]";
                    break;
                case 4:
                    prefix = "&e[✩]";
                    break;
                case 5:
                    prefix = "&6[✩]";
                    break;
                case 6:
                    prefix = "&c[✩]";
                    break;
                case 7:
                    prefix = "&a[✩]";
                    break;
                case 8:
                    prefix = "&b[✩]";
                    break;
                case 9:
                    prefix = "&d[✩]";
                    break;
                case 10:
                    prefix = "&7[★]";
                    break;
                default:
                    prefix = "&7[✩]";
            }
            
            int xpRequired = i == 1 ? 0 : 1000 * (i - 1);
            
            levels.put(i, new LevelData(i, prefix, xpRequired));
        }
        
        maxLevel = 10;
        
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

    public String getLevelPrefix(int level) {
        LevelData data = levels.get(level);
        if (data == null) {
            Map.Entry<Integer, LevelData> lower = levels.lowerEntry(level);
            if (lower != null) {
                return ChatColor.translateAlternateColorCodes('&', lower.getValue().getPrefix());
            }
            return "&7[✩]";
        }
        return ChatColor.translateAlternateColorCodes('&', data.getPrefix());
    }

    public int getRequiredXP(int level) {
        LevelData data = levels.get(level);
        if (data == null) {
            Map.Entry<Integer, LevelData> lower = levels.lowerEntry(level);
            Map.Entry<Integer, LevelData> higher = levels.higherEntry(level);
            
            if (lower != null && higher != null) {
                int lowerLevel = lower.getKey();
                int higherLevel = higher.getKey();
                int lowerXP = lower.getValue().getXpRequired();
                int higherXP = higher.getValue().getXpRequired();
                
                double progress = (double)(level - lowerLevel) / (higherLevel - lowerLevel);
                return lowerXP + (int)((higherXP - lowerXP) * progress);
            } else if (lower != null) {
                return lower.getValue().getXpRequired() + 1000;
            } else if (higher != null) {
                return higher.getValue().getXpRequired() - 1000;
            }
            return 1000 * level;
        }
        return data.getXpRequired();
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public boolean hasLevel(int level) {
        return levels.containsKey(level);
    }

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

    public LevelData getLevelData(int level) {
        return levels.get(level);
    }

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
