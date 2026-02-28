package io.github.pronze.sba.manager;

import io.github.pronze.sba.SBA;
import io.github.pronze.sba.config.SBAConfig;
import io.github.pronze.sba.events.PlayerToolUpgradeEvent;
import io.github.pronze.sba.lib.lang.LanguageService;
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
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.lib.utils.annotations.methods.OnPostEnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
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
    
    // Цены (можно будет загружать из конфига позже)
    @Getter
    private final List<Integer> pickaxePrices = Arrays.asList(10, 20, 30); // wood->stone, stone->iron, iron->diamond
    @Getter
    private final List<Integer> axePrices = Arrays.asList(10, 20, 30);
    @Getter
    private final List<Integer> shearsPrices = Arrays.asList(20); // normal->efficiency
    
    @OnPostEnable
    public void init() {
        instance = this;
        loadConfig();
        SBA.getInstance().getLogger().info("ToolUpgradeManager initialized!");
    }
    
    public static ToolUpgradeManager getInstance() {
        return instance;
    }
    
    private void loadConfig() {
        // Загружаем настройки из конфига SBA
        var config = SBAConfig.getInstance();
        
        // Проверяем, включены ли улучшения для инструментов
        // В SBA конфиге это обычно в upgrade-item секции
        upgradePickaxe = true; // По умолчанию включено
        upgradeAxe = true;
        upgradeShears = config.node("upgrade-item", "shears").getBoolean(true);
        
        // Можно добавить загрузку цен из конфига позже
    }
    
    /**
     * Получить уровень инструмента игрока
     */
    public int getToolLevel(Player player, ToolType type) {
        var levels = ToolLevels.getOrCreate(player.getUniqueId());
        return switch (type) {
            case PICKAXE -> levels.getPickaxeLevel();
            case AXE -> levels.getAxeLevel();
            case SHEARS -> levels.getShearsLevel();
        };
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
        
        return getMaterialLevel(item.getType());
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
     * Попытка улучшить инструмент
     */
    public boolean upgradeTool(Player player, ToolType type, ItemSpawnerType currencyType) {
        var levels = ToolLevels.getOrCreate(player.getUniqueId());
        int currentLevel = switch (type) {
            case PICKAXE -> levels.getPickaxeLevel();
            case AXE -> levels.getAxeLevel();
            case SHEARS -> levels.getShearsLevel();
        };
        
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
            case PICKAXE -> levels.setPickaxeLevel(newLevel);
            case AXE -> levels.setAxeLevel(newLevel);
            case SHEARS -> levels.setShearsLevel(newLevel);
        }
        
        // Обновляем предмет в инвентаре
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
        return switch (type) {
            case PICKAXE -> currentLevel < pickaxePrices.size() ? pickaxePrices.get(currentLevel) : -1;
            case AXE -> currentLevel < axePrices.size() ? axePrices.get(currentLevel) : -1;
            case SHEARS -> currentLevel < shearsPrices.size() ? shearsPrices.get(currentLevel) : -1;
        };
    }
    
    /**
     * Обновить инструмент в инвентаре
     */
    private void updateToolInInventory(Player player, ToolType type, int newLevel, ToolLevels levels) {
        PlayerInventory inv = player.getInventory();
        int slot = switch (type) {
            case PICKAXE -> levels.getPickaxeSlot();
            case AXE -> levels.getAxeSlot();
            case SHEARS -> levels.getShearsSlot();
        };
        
        // Если слот не запомнен, ищем инструмент
        if (slot == -1) {
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && getToolType(item) == type) {
                    slot = i;
                    switch (type) {
                        case PICKAXE -> levels.setPickaxeSlot(i);
                        case AXE -> levels.setAxeSlot(i);
                        case SHEARS -> levels.setShearsSlot(i);
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
     * Понизить уровень инструмента при смерти
     */
    public void downgradeTool(Player player, ToolType type) {
        var levels = ToolLevels.getOrCreate(player.getUniqueId());
        int currentLevel = switch (type) {
            case PICKAXE -> levels.getPickaxeLevel();
            case AXE -> levels.getAxeLevel();
            case SHEARS -> levels.getShearsLevel();
        };
        
        if (currentLevel > 0) {
            int newLevel = currentLevel - 1;
            switch (type) {
                case PICKAXE -> levels.setPickaxeLevel(newLevel);
                case AXE -> levels.setAxeLevel(newLevel);
                case SHEARS -> levels.setShearsLevel(newLevel);
            }
            updateToolInInventory(player, type, newLevel, levels);
        }
    }
    
    /**
     * Понизить все инструменты при смерти
     */
    public void downgradeAllTools(Player player) {
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
