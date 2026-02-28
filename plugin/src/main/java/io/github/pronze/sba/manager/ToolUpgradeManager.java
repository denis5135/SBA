package io.github.pronze.sba.manager;

import io.github.pronze.sba.SBA;
import io.github.pronze.sba.config.SBAConfig;
import io.github.pronze.sba.events.PlayerToolUpgradeEvent;
import io.github.pronze.sba.lib.lang.LanguageService;
import io.github.pronze.sba.utils.Logger;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.game.Game;
import org.screamingsandals.bedwars.api.game.ItemSpawnerType;
import org.screamingsandals.lib.player.Players;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ToolUpgradeManager {
    private static ToolUpgradeManager instance;
    
    // Материалы для каждого уровня кирки
    private static final Material[] PICKAXE_MATERIALS = {
        Material.WOODEN_PICKAXE,
        Material.STONE_PICKAXE,
        Material.IRON_PICKAXE,
        Material.DIAMOND_PICKAXE
    };
    
    // Материалы для каждого уровня топора
    private static final Material[] AXE_MATERIALS = {
        Material.WOODEN_AXE,
        Material.STONE_AXE,
        Material.IRON_AXE,
        Material.DIAMOND_AXE
    };
    
    // Включены ли улучшения
    private boolean upgradePickaxe = true;
    private boolean upgradeAxe = true;
    private boolean upgradeShears = true;
    
    // Цены
    @Getter
    private final List<Integer> pickaxePrices = Arrays.asList(10, 20, 30);
    @Getter
    private final List<Integer> axePrices = Arrays.asList(10, 20, 30);
    @Getter
    private final List<Integer> shearsPrices = Arrays.asList(20);
    
    private boolean initialized = false;
    
    public static ToolUpgradeManager getInstance() {
        if (instance == null) {
            instance = new ToolUpgradeManager();
            instance.init();
        }
        return instance;
    }
    
    private void init() {
        if (initialized) return;
        
        loadConfig();
        Logger.info("ToolUpgradeManager initialized!");
        initialized = true;
    }
    
    private void loadConfig() {
        try {
            var config = SBAConfig.getInstance();
            if (config != null) {
                upgradeShears = config.node("upgrade-item", "shears").getBoolean(true);
                upgradePickaxe = true;
                upgradeAxe = true;
            }
        } catch (Exception e) {
            Logger.error("Failed to load tool config: " + e.getMessage());
        }
    }
    
    /**
     * Получить уровень инструмента игрока
     */
    public int getToolLevel(Player player, ToolType type) {
        if (player == null) return 0;
        var levels = ToolLevels.getOrCreate(player.getUniqueId());
        switch (type) {
            case PICKAXE:
                return levels.getPickaxeLevel();
            case AXE:
                return levels.getAxeLevel();
            case SHEARS:
                return levels.getShearsLevel();
            default:
                return 0;
        }
    }
    
    /**
     * Установить уровень инструмента игрока
     */
    public void setToolLevel(Player player, ToolType type, int level) {
        if (player == null) return;
        var levels = ToolLevels.getOrCreate(player.getUniqueId());
        switch (type) {
            case PICKAXE:
                levels.setPickaxeLevel(level);
                break;
            case AXE:
                levels.setAxeLevel(level);
                break;
            case SHEARS:
                levels.setShearsLevel(level);
                break;
        }
    }
    
    /**
     * Проверить, можно ли улучшать этот предмет
     */
    public boolean canUpgrade(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        String name = type.name();
        
        if (name.endsWith("PICKAXE") && upgradePickaxe) return true;
        if (name.endsWith("AXE") && upgradeAxe) return true;
        if (name.equals("SHEARS") && upgradeShears) return true;
        
        return false;
    }
    
    /**
     * Определить тип инструмента
     */
    public ToolType getToolType(ItemStack item) {
        if (item == null) return null;
        String name = item.getType().name();
        
        if (name.endsWith("PICKAXE")) return ToolType.PICKAXE;
        if (name.endsWith("AXE")) return ToolType.AXE;
        if (name.equals("SHEARS")) return ToolType.SHEARS;
        
        return null;
    }
    
    /**
     * Получить уровень по материалу
     */
    public int getMaterialLevel(Material material) {
        if (material == Material.WOODEN_PICKAXE || material == Material.WOODEN_AXE) return 0;
        if (material == Material.STONE_PICKAXE || material == Material.STONE_AXE) return 1;
        if (material == Material.IRON_PICKAXE || material == Material.IRON_AXE) return 2;
        if (material == Material.DIAMOND_PICKAXE || material == Material.DIAMOND_AXE) return 3;
        return -1;
    }
    
    /**
     * Получить текущий уровень предмета
     */
    public int getCurrentLevel(ItemStack item) {
        if (item == null) return -1;
        
        ToolType type = getToolType(item);
        if (type == null) return -1;
        
        if (type == ToolType.SHEARS) {
            return item.containsEnchantment(Enchantment.DIG_SPEED) ? 1 : 0;
        }
        
        Material material = item.getType();
        String name = material.name();
        
        // Кирки
        if (name.equals("WOODEN_PICKAXE")) return 0;
        if (name.equals("STONE_PICKAXE")) return 1;
        if (name.equals("IRON_PICKAXE")) return 2;
        if (name.equals("DIAMOND_PICKAXE")) return 3;
        
        // Топоры
        if (name.equals("WOODEN_AXE")) return 0;
        if (name.equals("STONE_AXE")) return 1;
        if (name.equals("IRON_AXE")) return 2;
        if (name.equals("DIAMOND_AXE")) return 3;
        
        return -1;
    }
    
    /**
     * Получить уровень игрока для данного предмета
     */
    public int getPlayerLevelForItem(Player player, ItemStack item) {
        if (player == null || item == null) return -1;
        ToolType type = getToolType(item);
        if (type == null) return -1;
        return getToolLevel(player, type);
    }
    
    /**
     * Проверить, должен ли предмет быть виден в магазине
     */
    public boolean shouldShowInShop(Player player, ItemStack item) {
        if (player == null || item == null) return false;
        
        ToolType type = getToolType(item);
        if (type == null) return true; // Не инструмент - показываем всегда
        
        int itemLevel = getCurrentLevel(item);
        int playerLevel = getToolLevel(player, type);
        
        // Для ножниц особый случай (только 2 уровня)
        if (type == ToolType.SHEARS) {
            if (playerLevel == 0) {
                // Игрок не имеет ножниц - показываем обычные
                return itemLevel == 0;
            } else {
                // Игрок имеет обычные ножницы - показываем обычные и улучшенные
                return itemLevel == 0 || itemLevel == 1;
            }
        }
        
        // Для кирки и топора (4 уровня)
        if (playerLevel == 0) {
            // Игрок не имеет инструмента - показываем только деревянный
            return itemLevel == 0;
        } else {
            // Игрок имеет инструмент - показываем текущий и следующий уровень
            return itemLevel == playerLevel || itemLevel == playerLevel + 1;
        }
    }
    
    /**
     * Создать предмет для указанного уровня
     */
    public ItemStack createToolItem(ToolType type, int level) {
        Material material;
        
        switch (type) {
            case PICKAXE:
                if (level < 0 || level >= PICKAXE_MATERIALS.length) return null;
                material = PICKAXE_MATERIALS[level];
                break;
            case AXE:
                if (level < 0 || level >= AXE_MATERIALS.length) return null;
                material = AXE_MATERIALS[level];
                break;
            case SHEARS:
                material = Material.SHEARS;
                break;
            default:
                return null;
        }
        
        ItemStack item = new ItemStack(material);
        
        // Для ножниц добавляем эффективность на 1 уровне
        if (type == ToolType.SHEARS && level == 1) {
            ItemMeta meta = item.getItemMeta();
            meta.addEnchant(Enchantment.DIG_SPEED, 1, true);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Аналог CategoryContent.execute() из BedWars1058
     * Попытка улучшить инструмент
     */
    public boolean upgradeTool(Player player, ToolType type, ItemSpawnerType currencyType) {
        if (player == null) return false;
        
        var levels = ToolLevels.getOrCreate(player.getUniqueId());
        int currentLevel;
        switch (type) {
            case PICKAXE:
                currentLevel = levels.getPickaxeLevel();
                break;
            case AXE:
                currentLevel = levels.getAxeLevel();
                break;
            case SHEARS:
                currentLevel = levels.getShearsLevel();
                break;
            default:
                return false;
        }
        
        int maxLevel = (type == ToolType.SHEARS) ? 1 : 3;
        if (currentLevel >= maxLevel) {
            LanguageService.getInstance().get("shop.max_tool_level")
                .replace("%tool%", type.name().toLowerCase())
                .send(Players.wrapPlayer(player));
            return false;
        }
        
        // Получаем цену
        int price = getPrice(type, currentLevel);
        if (price <= 0) return false;
        
        // Проверяем ресурсы
        var game = Main.getInstance().getGameOfPlayer(player);
        if (game == null) return false;
        
        var stack = currencyType.getStack(price);
        
        if (!player.getInventory().containsAtLeast(stack, price)) {
            LanguageService.getInstance().get("shop.not_enough_money")
                .replace("%resource%", currencyType.getName())
                .replace("%price%", String.valueOf(price))
                .send(Players.wrapPlayer(player));
            return false;
        }
        
        // Вызываем событие
        var event = new PlayerToolUpgradeEvent(player, stack, type.name(), 
                game.getTeamOfPlayer(player), game, currencyType);
        event.setPrice(String.valueOf(price));
        
        if (event.isCancelled()) {
            return false;
        }
        
        // Снимаем ресурсы
        player.getInventory().removeItem(stack);
        
        // Увеличиваем уровень
        int newLevel = currentLevel + 1;
        switch (type) {
            case PICKAXE:
                levels.setPickaxeLevel(newLevel);
                break;
            case AXE:
                levels.setAxeLevel(newLevel);
                break;
            case SHEARS:
                levels.setShearsLevel(newLevel);
                break;
        }
        
        // Обновляем предмет в инвентаре (как в BedWars1058 upgrade() + giveItems())
        updateToolInInventory(player, type, newLevel, levels);
        
        // Сообщение
        LanguageService.getInstance().get("shop.tool_upgraded")
            .replace("%tool%", type.name().toLowerCase())
            .replace("%level%", String.valueOf(newLevel + 1))
            .send(Players.wrapPlayer(player));
        
        return true;
    }
    
    /**
     * Получить цену для улучшения
     */
    private int getPrice(ToolType type, int currentLevel) {
        switch (type) {
            case PICKAXE:
                return currentLevel < pickaxePrices.size() ? pickaxePrices.get(currentLevel) : -1;
            case AXE:
                return currentLevel < axePrices.size() ? axePrices.get(currentLevel) : -1;
            case SHEARS:
                return currentLevel < shearsPrices.size() ? shearsPrices.get(currentLevel) : -1;
            default:
                return -1;
        }
    }
    
    /**
     * Обновить инструмент в инвентаре (giveItems из BedWars1058)
     */
    public void giveToolItem(Player player, ToolType type, int level) {
        if (player == null) return;
        
        var levels = ToolLevels.getOrCreate(player.getUniqueId());
        int slot = -1;
        
        switch (type) {
            case PICKAXE:
                slot = levels.getPickaxeSlot();
                break;
            case AXE:
                slot = levels.getAxeSlot();
                break;
            case SHEARS:
                slot = levels.getShearsSlot();
                break;
        }
        
        // Если слот не запомнен, ищем первый пустой слот
        if (slot == -1) {
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                    slot = i;
                    break;
                }
            }
        }
        
        if (slot != -1) {
            ItemStack newTool = createToolItem(type, level);
            if (newTool != null) {
                player.getInventory().setItem(slot, newTool);
            }
        }
    }
    
    /**
     * Обновить инструмент в инвентаре (замена в том же слоте)
     */
    private void updateToolInInventory(Player player, ToolType type, int newLevel, ToolLevels levels) {
        if (player == null) return;
        
        PlayerInventory inv = player.getInventory();
        int slot = -1;
        
        switch (type) {
            case PICKAXE:
                slot = levels.getPickaxeSlot();
                break;
            case AXE:
                slot = levels.getAxeSlot();
                break;
            case SHEARS:
                slot = levels.getShearsSlot();
                break;
        }
        
        // Если слот не запомнен, ищем инструмент
        if (slot == -1) {
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && getToolType(item) == type) {
                    slot = i;
                    switch (type) {
                        case PICKAXE:
                            levels.setPickaxeSlot(i);
                            break;
                        case AXE:
                            levels.setAxeSlot(i);
                            break;
                        case SHEARS:
                            levels.setShearsSlot(i);
                            break;
                    }
                    break;
                }
            }
        }
        
        if (slot != -1) {
            ItemStack newTool = createToolItem(type, newLevel);
            if (newTool != null) {
                inv.setItem(slot, newTool);
            }
        }
    }
    
    /**
     * Аналог manageDeath из BedWars1058
     * Понизить уровень инструмента при смерти
     */
    public void downgradeTool(Player player, ToolType type) {
        if (player == null) return;
        
        var levels = ToolLevels.getOrCreate(player.getUniqueId());
        int currentLevel;
        switch (type) {
            case PICKAXE:
                currentLevel = levels.getPickaxeLevel();
                break;
            case AXE:
                currentLevel = levels.getAxeLevel();
                break;
            case SHEARS:
                currentLevel = levels.getShearsLevel();
                break;
            default:
                return;
        }
        
        if (currentLevel > 0) {
            int newLevel = currentLevel - 1;
            switch (type) {
                case PICKAXE:
                    levels.setPickaxeLevel(newLevel);
                    break;
                case AXE:
                    levels.setAxeLevel(newLevel);
                    break;
                case SHEARS:
                    levels.setShearsLevel(newLevel);
                    break;
            }
            updateToolInInventory(player, type, newLevel, levels);
        }
    }
    
    /**
     * Понизить все инструменты при смерти
     */
    public void downgradeAllTools(Player player) {
        if (player == null) return;
        
        var levels = ToolLevels.getOrCreate(player.getUniqueId());
        
        if (levels.getPickaxeLevel() > 0 && upgradePickaxe) {
            levels.setPickaxeLevel(levels.getPickaxeLevel() - 1);
            updateToolInInventory(player, ToolType.PICKAXE, levels.getPickaxeLevel(), levels);
        }
        
        if (levels.getAxeLevel() > 0 && upgradeAxe) {
            levels.setAxeLevel(levels.getAxeLevel() - 1);
            updateToolInInventory(player, ToolType.AXE, levels.getAxeLevel(), levels);
        }
        
        if (levels.getShearsLevel() > 0 && upgradeShears) {
            levels.setShearsLevel(0);
            updateToolInInventory(player, ToolType.SHEARS, 0, levels);
        }
    }
    
    /**
     * Очистить данные игрока
     */
    public void removePlayerData(UUID uuid) {
        ToolLevels.remove(uuid);
    }
}
