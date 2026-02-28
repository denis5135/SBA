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
        
        if (!SBAConfig.getInstance().isToolUpgradeEnabled()) return;
        
        scheduleInventoryFilter(player, "initial open");
    }
    
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();
        
        Logger.info("📂 Inventory opened: '" + title + "' by " + player.getName());
        
        // Проверяем, открыт ли магазин (любая страница)
        if (title.contains("Shop") || title.contains("Магазин") || 
            title.contains("Tools") || title.contains("Инструменты") ||
            title.contains("Upgrade") || title.contains("Улучшения")) {
            
            if (!SBAConfig.getInstance().isToolUpgradeEnabled()) return;
            
            scheduleInventoryFilter(player, "inventory open: " + title);
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        
        Logger.info("🖱️ Click in inventory: '" + title + "' by " + player.getName());
        
        // Если кликнули по предмету
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
            ItemStack clicked = event.getCurrentItem();
            Logger.info("Clicked item: " + clicked.getType().name());
            
            // Проверяем, является ли кликнутый предмет иконкой категории инструментов
            if (clicked.getType().name().contains("STONE_PICKAXE")) {
                Logger.info("🔧 TOOLS CATEGORY CLICKED!");
                
                // Даём время на открытие новой категории
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Проверяем новый заголовок
                        String newTitle = player.getOpenInventory().getTitle();
                        Logger.info("New inventory title after click: '" + newTitle + "'");
                        scheduleInventoryFilter(player, "category click - new title: " + newTitle);
                    }
                }.runTaskLater(SBA.getPluginInstance(), 20L); // 20 тиков задержки
            }
        }
    }
    
    private void scheduleInventoryFilter(Player player, String reason) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Logger.info("Starting shop filter for: " + player.getName() + " (reason: " + reason + ")");
                    filterShopInventory(player);
                } catch (Exception e) {
                    Logger.error("Error filtering shop inventory: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskLater(SBA.getPluginInstance(), 10L);
    }
    
    private void filterShopInventory(Player player) {
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv == null) {
            Logger.info("Open inventory is null");
            return;
        }
        
        String title = player.getOpenInventory().getTitle();
        Logger.info("Filtering inventory: '" + title + "' (size: " + openInv.getSize() + ")");
        
        boolean isToolsCategory = title.contains("Tools") || title.contains("Инструменты");
        boolean isMainMenu = title.contains("Shop") || title.contains("Магазин") || title.contains("Item Shop");
        
        Logger.info("isMainMenu: " + isMainMenu + ", isToolsCategory: " + isToolsCategory);
        
        int hiddenCount = 0;
        int visibleCount = 0;
        
        for (int i = 0; i < openInv.getSize(); i++) {
            ItemStack item = openInv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            
            // Пропускаем навигационные предметы
            if (isNavigationItem(item)) {
                continue;
            }
            
            ToolType toolType = ToolUpgradeManager.getInstance().getToolType(item);
            if (toolType != null) {
                int itemLevel = ToolUpgradeManager.getInstance().getCurrentLevel(item);
                int playerLevel = ToolUpgradeManager.getInstance().getToolLevel(player, toolType);
                
                Logger.info("Slot " + i + ": " + item.getType().name() + 
                           " | Tool: " + toolType + " | ItemLevel: " + itemLevel + 
                           " | PlayerLevel: " + playerLevel);
                
                boolean shouldShow = false;
                
                if (isMainMenu) {
                    // В главном меню показываем всё (иконки категорий)
                    shouldShow = true;
                } else if (isToolsCategory) {
                    // В категории инструментов фильтруем
                    if (playerLevel == 0) {
                        // Нет инструмента - показываем только деревянный (уровень 0)
                        shouldShow = (itemLevel == 0);
                    } else {
                        // Есть инструмент - показываем текущий и следующий уровень
                        shouldShow = (itemLevel == playerLevel || itemLevel == playerLevel + 1);
                    }
                    
                    // Особый случай для ножниц
                    if (toolType == ToolType.SHEARS) {
                        if (playerLevel == 0) {
                            shouldShow = (itemLevel == 0);
                        } else {
                            shouldShow = (itemLevel == 0 || itemLevel == 1);
                        }
                    }
                } else {
                    // Другие категории - показываем всё
                    shouldShow = true;
                }
                
                if (!shouldShow) {
                    openInv.setItem(i, createPlaceholderItem());
                    hiddenCount++;
                    Logger.info("  ❌ HIDDEN (reason: shouldShow=" + shouldShow + ")");
                } else {
                    visibleCount++;
                    Logger.info("  ✅ VISIBLE");
                }
            }
        }
        
        Logger.info("Filter complete: " + visibleCount + " visible, " + hiddenCount + " hidden");
    }
    
    private boolean isNavigationItem(ItemStack item) {
        if (item == null) return false;
        
        Material type = item.getType();
        String name = "";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            name = item.getItemMeta().getDisplayName();
        }
        
        return type.name().contains("STAINED_GLASS_PANE") ||
               type == Material.ARROW ||
               type == Material.BARRIER ||
               type == Material.NETHER_STAR ||
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
