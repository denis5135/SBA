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
    
    // Цены для каждого уровня
    @Getter
    private final List<Integer> pickaxePrices = Arrays.asList(10, 10, 3, 6);
    @Getter
    private final List<Integer> axePrices = Arrays.asList(10, 10, 3, 6);
    @Getter
    private final List<Integer> shearsPrices = Arrays.asList(16, 32);
    
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
        
        if (name.equals("WOODEN_PICKAXE") || name.equals("WOODEN_AXE")) return 0;
        if (name.equals("STONE_PICKAXE") || name.equals("STONE_AXE")) return 1;
        if (name.equals("IRON_PICKAXE") || name.equals("IRON_AXE")) return 2;
        if (name.equals("DIAMOND_PICKAXE") || name.equals("DIAMOND_AXE")) return 3;
        
        return -1;
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
     * Получить цену для указанного уровня
     */
    public int getPriceForLevel(ToolType type, int level) {
        switch (type) {
            case PICKAXE:
                return level < pickaxePrices.size() ? pickaxePrices.get(level) : -1;
            case AXE:
                return level < axePrices.size() ? axePrices.get(level) : -1;
            case SHEARS:
                return level < shearsPrices.size() ? shearsPrices.get(level) : -1;
            default:
                return -1;
        }
    }
    
    /**
     * Получить валюту для указанного уровня
     */
    public String getCurrencyForLevel(ToolType type, int level) {
        // Валюты для каждого уровня
        List<String> pickaxeCurrencies = Arrays.asList("iron", "iron", "gold", "gold");
        List<String> axeCurrencies = Arrays.asList("iron", "iron", "gold", "gold");
        List<String> shearsCurrencies = Arrays.asList("iron", "iron");
        
        switch (type) {
            case PICKAXE:
                return level < pickaxeCurrencies.size() ? pickaxeCurrencies.get(level) : "iron";
            case AXE:
                return level < axeCurrencies.size() ? axeCurrencies.get(level) : "iron";
            case SHEARS:
                return level < shearsCurrencies.size() ? shearsCurrencies.get(level) : "iron";
            default:
                return "iron";
        }
    }
    
    /**
     * Получить отображаемое название валюты
     */
    private String getCurrencyDisplay(String currency) {
        switch (currency) {
            case "iron": return "железа";
            case "gold": return "золота";
            case "diamond": return "алмазов";
            case "emerald": return "изумрудов";
            default: return currency;
        }
    }
    
    /**
     * Получить римское число
     */
    private String getRomanNumber(int level) {
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            default: return String.valueOf(level);
        }
    }
    
    /**
     * Получить отображаемое название инструмента
     */
    private String getToolDisplayName(ToolType type) {
        switch (type) {
            case PICKAXE: return "Кирка";
            case AXE: return "Топор";
            case SHEARS: return "Ножницы";
            default: return type.name().toLowerCase();
        }
    }
    
    /**
     * Проверить, достигнут ли максимальный уровень
     */
    public boolean isMaxLevel(ToolType type, int level) {
        switch (type) {
            case PICKAXE:
                return level >= PICKAXE_MATERIALS.length - 1;
            case AXE:
                return level >= AXE_MATERIALS.length - 1;
            case SHEARS:
                return level >= 1;
            default:
                return true;
        }
    }
    
    /**
     * Выдать инструмент игроку (ищет слот или выдаёт в первый свободный)
     */
    public void giveToolItem(Player player, ToolType type, int level) {
        if (player == null) return;
        
        ItemStack tool = createToolItem(type, level);
        if (tool == null) return;
        
        // Пытаемся найти слот, где уже есть такой тип инструмента
        PlayerInventory inv = player.getInventory();
        int targetSlot = -1;
        
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && getToolType(item) == type) {
                targetSlot = i;
                Logger.info("Found existing " + type + " at slot " + i);
                break;
            }
        }
        
        // Если не нашли, ищем первый пустой слот
        if (targetSlot == -1) {
            targetSlot = inv.firstEmpty();
            Logger.info("No existing tool found, using first empty slot: " + targetSlot);
        }
        
        if (targetSlot != -1) {
            inv.setItem(targetSlot, tool);
            Logger.info("Gave " + type + " level " + level + " to " + player.getName() + " at slot " + targetSlot);
        } else {
            // Если нет свободных слотов, выбрасываем на землю
            player.getWorld().dropItemNaturally(player.getLocation(), tool);
            Logger.info("Dropped " + type + " level " + level + " for " + player.getName());
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
                    Logger.info("Found " + type + " at slot " + slot);
                    break;
                }
            }
        }
        
        if (slot != -1) {
            ItemStack newTool = createToolItem(type, newLevel);
            if (newTool != null) {
                inv.setItem(slot, newTool);
                Logger.info("Updated " + type + " to level " + newLevel + " at slot " + slot);
            }
        } else {
            Logger.info("No slot found for " + type + ", giving new item");
            giveToolItem(player, type, newLevel);
        }
    }
    
    /**
     * Попытка купить/улучшить инструмент
     */
    public boolean upgradeTool(Player player, ToolType type, ItemSpawnerType currencyType) {
        Logger.info("========== UPGRADE TOOL CALLED ==========");
        Logger.info("Player: " + player.getName());
        Logger.info("Tool type: " + type);
        Logger.info("Currency from parameter: " + currencyType.getName());
        Logger.info("Currency class: " + currencyType.getClass().getName());
        
        if (player == null) {
            Logger.info("Player is null!");
            return false;
        }
        
        var levels = ToolLevels.getOrCreate(player.getUniqueId());
        int currentLevel = getToolLevel(player, type);
        Logger.info("Current level in data: " + currentLevel);
        
        // Проверяем, не максимальный ли уже уровень
        if (isMaxLevel(type, currentLevel)) {
            Logger.info("Max level reached - cannot upgrade further");
            LanguageService.getInstance().get("max_tool_level")
                .replace("%tool%", type.name().toLowerCase())
                .send(Players.wrapPlayer(player));
            return false;
        }
        
        // Определяем цену в зависимости от текущего уровня
        int price;
        String expectedCurrency;
        int levelToGive;
        int nextLevel;
        
        if (currentLevel == 0) {
            // Первая покупка - базовый инструмент
            price = getPriceForLevel(type, 0);
            expectedCurrency = getCurrencyForLevel(type, 0);
            levelToGive = 0;
            nextLevel = 1;
            Logger.info("FIRST PURCHASE - Buying level 0 tool");
            Logger.info("Price: " + price + " " + expectedCurrency);
        } else {
            // Улучшение - покупаем следующий уровень
            price = getPriceForLevel(type, currentLevel);
            expectedCurrency = getCurrencyForLevel(type, currentLevel);
            levelToGive = currentLevel;
            nextLevel = currentLevel + 1;
            Logger.info("UPGRADE - Buying level " + currentLevel + " tool (will become level " + nextLevel + " after purchase)");
            Logger.info("Price: " + price + " " + expectedCurrency);
        }
        
        if (price <= 0) {
            Logger.info("Invalid price: " + price);
            return false;
        }
        
        // Получаем название валюты разными способами
        String currencyName = currencyType.getName();
        String currencyItemName = currencyType.getItemName();
        String currencyLowerCase = currencyName.toLowerCase();
        
        Logger.info("Currency name: '" + currencyName + "'");
        Logger.info("Currency item name: '" + currencyItemName + "'");
        Logger.info("Currency lower case: '" + currencyLowerCase + "'");
        
        // Проверяем валюту
        boolean currencyMatches = false;
        
        if (expectedCurrency.equals("iron")) {
            if (currencyLowerCase.contains("iron") || currencyLowerCase.contains("желез")) {
                currencyMatches = true;
                Logger.info("Currency matches IRON");
            }
        } else if (expectedCurrency.equals("gold")) {
            if (currencyLowerCase.contains("gold") || currencyLowerCase.contains("золот")) {
                currencyMatches = true;
                Logger.info("Currency matches GOLD");
            }
        } else if (expectedCurrency.equals("diamond")) {
            if (currencyLowerCase.contains("diamond") || currencyLowerCase.contains("алмаз")) {
                currencyMatches = true;
                Logger.info("Currency matches DIAMOND");
            }
        } else if (expectedCurrency.equals("emerald")) {
            if (currencyLowerCase.contains("emerald") || currencyLowerCase.contains("изумруд")) {
                currencyMatches = true;
                Logger.info("Currency matches EMERALD");
            }
        }

        if (!currencyMatches) {
            Logger.info("CURRENCY MISMATCH! Expected: " + expectedCurrency + ", got: '" + currencyLowerCase + "'");
            LanguageService.getInstance().get("not_enough_money")
                .replace("%resource%", getCurrencyDisplay(expectedCurrency))
                .replace("%price%", String.valueOf(price))
                .send(Players.wrapPlayer(player));
            return false;
        }
        
        // Проверяем ресурсы
        var game = Main.getInstance().getGameOfPlayer(player);
        if (game == null) {
            Logger.info("Game is null!");
            return false;
        }
        
        var stack = currencyType.getStack(price);
        Logger.info("Stack type: " + stack.getType().name());
        Logger.info("Stack amount: " + stack.getAmount());
        
        // Считаем ресурсы в инвентаре
        int hasAmount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == stack.getType()) {
                hasAmount += item.getAmount();
                Logger.info("Found " + item.getType().name() + " x" + item.getAmount());
            }
        }
        Logger.info("Player has " + hasAmount + " " + currencyType.getName() + ", needs " + price);
        
        if (!player.getInventory().containsAtLeast(stack, price)) {
            Logger.info("NOT ENOUGH MONEY!");
            LanguageService.getInstance().get("not_enough_money")
                .replace("%resource%", getCurrencyDisplay(expectedCurrency))
                .replace("%price%", String.valueOf(price))
                .send(Players.wrapPlayer(player));
            return false;
        }
        
        // Вызываем событие
        var event = new PlayerToolUpgradeEvent(player, stack, type.name(), 
                game.getTeamOfPlayer(player), game, currencyType);
        event.setPrice(String.valueOf(price));
        
        if (event.isCancelled()) {
            Logger.info("Event was cancelled!");
            return false;
        }
        
        // Снимаем ресурсы
        player.getInventory().removeItem(stack);
        Logger.info("Removed " + price + " " + currencyType.getName() + " from inventory");
        
        // Выдаём предмет
        giveToolItem(player, type, levelToGive);
        Logger.info("Gave item of level " + levelToGive);
        
        // Сообщение о покупке
        String toolName = getToolDisplayName(type);
        String levelText;
        if (currentLevel == 0) {
            levelText = "I";
        } else {
            levelText = getRomanNumber(currentLevel + 1);
        }
        Logger.info("Sending success message for level " + levelText);
        
        LanguageService.getInstance().get("tool_upgraded")
            .replace("%tool%", toolName)
            .replace("%level%", levelText)
            .send(Players.wrapPlayer(player));
        
        // Повышаем уровень
        setToolLevel(player, type, nextLevel);
        Logger.info("New level set to: " + nextLevel);
        
        Logger.info("========== UPGRADE TOOL SUCCESS ==========");
        return true;
    }
    
    /**
     * Понизить уровень инструмента при смерти
     */
    public void downgradeTool(Player player, ToolType type) {
        if (player == null) return;
        
        var levels = ToolLevels.getOrCreate(player.getUniqueId());
        int currentLevel = getToolLevel(player, type);
        Logger.info("Downgrading " + type + " for " + player.getName() + " from level " + currentLevel);
        
        if (currentLevel > 0) {
            int newLevel = currentLevel - 1;
            setToolLevel(player, type, newLevel);
            updateToolInInventory(player, type, newLevel, levels);
            Logger.info("Downgraded to level " + newLevel);
        } else {
            Logger.info("Already at minimum level, cannot downgrade");
        }
    }
    
    /**
     * Понизить все инструменты при смерти
     */
    public void downgradeAllTools(Player player) {
        if (player == null) return;
        
        Logger.info("Downgrading all tools for " + player.getName());
        
        if (upgradePickaxe) {
            downgradeTool(player, ToolType.PICKAXE);
        }
        if (upgradeAxe) {
            downgradeTool(player, ToolType.AXE);
        }
        if (upgradeShears) {
            downgradeTool(player, ToolType.SHEARS);
        }
    }
    
    /**
     * Очистить данные игрока
     */
    public void removePlayerData(UUID uuid) {
        ToolLevels.remove(uuid);
        Logger.info("Removed tool data for UUID: " + uuid);
    }
}
