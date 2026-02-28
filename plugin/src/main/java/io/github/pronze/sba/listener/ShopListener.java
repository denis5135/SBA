package io.github.pronze.sba.listener;

import io.github.pronze.sba.SBA;
import io.github.pronze.sba.config.SBAConfig;
import io.github.pronze.sba.manager.ToolType;
import io.github.pronze.sba.manager.ToolUpgradeManager;
import io.github.pronze.sba.utils.Logger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.screamingsandals.bedwars.api.events.BedwarsOpenShopEvent;
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.lib.utils.annotations.methods.OnPostEnable;

@Service
public class ShopListener implements Listener {
    
    @OnPostEnable
    public void registerListener() {
        if (SBA.isBroken()) return;
        SBA.getInstance().registerListener(this);
        Logger.info("✅ ShopListener registered!");
    }
    
    @EventHandler
    public void onShopOpen(BedwarsOpenShopEvent event) {
        Player player = event.getPlayer();
        
        Logger.info("🛒 Shop opened by: " + player.getName());
        
        // Проверяем, включены ли улучшения инструментов
        if (!SBAConfig.getInstance().isToolUpgradeEnabled()) {
            Logger.info("Tool upgrades are disabled");
            return;
        }
        
        // Откладываем фильтрацию для главного меню
        scheduleInventoryFilter(player);
    }
    
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        Inventory inv = event.getInventory();
        
        // Проверяем, открыт ли магазин (по названию)
        String title = event.getView().getTitle();
        if (title.contains("Shop") || title.contains("Магазин") || 
            title.contains("Tools") || title.contains("Инструменты")) {
            
            Logger.info("📂 Inventory opened: " + title + " by " + player.getName());
            
            // Откладываем фильтрацию для любого инвентаря магазина
            scheduleInventoryFilter(player);
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        
        // Если кликнули по категории, фильтруем новый инвентарь после открытия
        if (event.getCurrentItem() != null && isCategoryIcon(event.getCurrentItem())) {
            Logger.info("🖱️ Category clicked by: " + player.getName());
            
            // Откладываем фильтрацию для нового инвентаря
            new BukkitRunnable() {
                @Override
                public void run() {
                    filterShopInventory(player);
                }
            }.runTaskLater(SBA.getPluginInstance(), 5L);
        }
    }
    
    private void scheduleInventoryFilter(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Logger.info("Starting shop filter for: " + player.getName());
                    filterShopInventory(player);
                } catch (Exception e) {
                    Logger.error("Error filtering shop inventory: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskLater(SBA.getPluginInstance(), 10L);
    }
    
    private boolean isCategoryIcon(ItemStack item) {
        if (item == null) return false;
        
        // Иконки категорий - это обычно основные предметы (кирка, меч, лук и т.д.)
        Material type = item.getType();
        String name = type.name();
        
        return name.contains("PICKAXE") || 
               name.contains("SWORD") || 
               name.contains("BOW") || 
               name.contains("BOOTS") || 
               name.contains("CHESTPLATE") ||
               name.contains("HELMET") ||
               name.contains("TNT") ||
               name.contains("POTION") ||
               name.contains("BREWING") ||
               type == Material.SHEARS;
    }
    
    private void filterShopInventory(Player player) {
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv == null) {
            Logger.info("Open inventory is null");
            return;
        }
        
        Logger.info("Inventory size: " + openInv.getSize());
        
        // Получаем название инвентаря, чтобы понять, где мы находимся
        String title = player.getOpenInventory().getTitle();
        Logger.info("Inventory title: " + title);
        
        boolean isMainMenu = title.contains("Shop") || title.contains("Магазин") || title.contains("Item Shop");
        boolean isToolsCategory = title.contains("Tools") || title.contains("Инструменты");
        
        Logger.info("isMainMenu: " + isMainMenu + ", isToolsCategory: " + isToolsCategory);
        
        int hiddenCount = 0;
        int visibleCount = 0;
        
        for (int i = 0; i < openInv.getSize(); i++) {
            ItemStack item = openInv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            
            Logger.info("Slot " + i + ": " + item.getType().name());
            
            // Пропускаем иконки навигации и декоративные стекла
            if (isPlaceholderOrNavigation(item)) {
                Logger.info("  -> Navigation item, keeping");
                continue;
            }
            
            // Проверяем, является ли предмет инструментом
            ToolType toolType = ToolUpgradeManager.getInstance().getToolType(item);
            if (toolType != null) {
                int itemLevel = ToolUpgradeManager.getInstance().getCurrentLevel(item);
                int playerLevel = ToolUpgradeManager.getInstance().getToolLevel(player, toolType);
                
                Logger.info("  🔧 Tool: " + toolType + " | " + item.getType().name() + 
                           " | ItemLevel: " + itemLevel + " | PlayerLevel: " + playerLevel);
                
                boolean shouldShow = false;
                
                // Если это главное меню, показываем все иконки категорий
                if (isMainMenu) {
                    shouldShow = true;
                    Logger.info("  -> Main menu, keeping category icon");
                }
                // Если это категория инструментов, фильтруем
                else if (isToolsCategory) {
                    // Логика отображения внутри категории инструментов
                    if (playerLevel == 0) {
                        // Игрок не имеет инструмента - показываем только деревянный (уровень 0)
                        shouldShow = (itemLevel == 0);
                    } else {
                        // Игрок имеет инструмент - показываем текущий и следующий уровень
                        shouldShow = (itemLevel == playerLevel || itemLevel == playerLevel + 1);
                    }
                    
                    // Особый случай для ножниц (только 2 уровня)
                    if (toolType == ToolType.SHEARS) {
                        if (playerLevel == 0) {
                            shouldShow = (itemLevel == 0);
                        } else {
                            shouldShow = (itemLevel == 0 || itemLevel == 1);
                        }
                    }
                    
                    Logger.info("  -> Tools category: shouldShow=" + shouldShow);
                } else {
                    // В других категориях показываем всё
                    shouldShow = true;
                    Logger.info("  -> Other category, keeping");
                }
                
                if (!shouldShow) {
                    // Скрываем предмет, заменяя на стекло
                    openInv.setItem(i, createPlaceholderItem());
                    hiddenCount++;
                    Logger.info("  ❌ HIDDEN");
                } else {
                    visibleCount++;
                }
            } else {
                // Не инструмент
                if (item.getType().name().contains("PICKAXE") || 
                    item.getType().name().contains("AXE") || 
                    item.getType().name().contains("SHEARS")) {
                    Logger.warn("  ⚠️ Item looks like tool but not recognized: " + item.getType().name());
                }
            }
        }
        
        Logger.info("Filter complete: " + visibleCount + " visible, " + hiddenCount + " hidden");
    }
    
    private boolean isPlaceholderOrNavigation(ItemStack item) {
        if (item == null) return false;
        
        Material type = item.getType();
        String name = "";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            name = item.getItemMeta().getDisplayName();
        }
        
        // Проверяем, является ли предмет навигационным (стекла, стрелки назад и т.д.)
        return type.name().contains("STAINED_GLASS_PANE") ||
               type == Material.ARROW ||
               type == Material.BARRIER ||
               name.toLowerCase().contains("назад") || 
               name.toLowerCase().contains("back") ||
               name.toLowerCase().contains("страница") || 
               name.toLowerCase().contains("page") ||
               name.toLowerCase().contains("закрыть") ||
               name.toLowerCase().contains("close");
    }
    
    private ItemStack createPlaceholderItem() {
        ItemStack placeholder = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = placeholder.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            placeholder.setItemMeta(meta);
        }
        return placeholder;
    }
}
