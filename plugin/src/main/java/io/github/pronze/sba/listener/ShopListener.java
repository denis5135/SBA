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
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();
        
        // Логируем открытие инвентаря
        Logger.info("📂 Inventory opened: '" + title + "' by " + player.getName());
        
        // Фильтруем ЛЮБОЙ инвентарь магазина
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
        
        // Если кликнули по предмету
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
            ItemStack clicked = event.getCurrentItem();
            
            // Проверяем, является ли кликнутый предмет иконкой категории
            if (isCategoryIcon(clicked)) {
                Logger.info("🖱️ Category clicked: " + clicked.getType().name() + " in inventory: '" + title + "'");
                
                // Даём время на обновление инвентаря (SimpleInventories обновляет содержимое)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        String currentTitle = player.getOpenInventory().getTitle();
                        Logger.info("Inventory after click: '" + currentTitle + "'");
                        scheduleInventoryFilter(player, "category click - " + clicked.getType().name());
                    }
                }.runTaskLater(SBA.getPluginInstance(), 10L);
            }
        }
    }
    
    private boolean isCategoryIcon(ItemStack item) {
        if (item == null) return false;
        
        Material type = item.getType();
        // Иконки категорий - это основные предметы (каменная кирка, меч, лук и т.д.)
        return type.name().contains("STONE_PICKAXE") ||
               type.name().contains("WOODEN_PICKAXE") ||
               type.name().contains("STONE_SWORD") ||
               type.name().contains("BOW") ||
               type.name().contains("CHAINMAIL_BOOTS") ||
               type == Material.SHEARS;
    }
    
    private void scheduleInventoryFilter(Player player, String reason) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    filterShopInventory(player, reason);
                } catch (Exception e) {
                    Logger.error("Error filtering shop inventory: " + e.getMessage());
                }
            }
        }.runTaskLater(SBA.getPluginInstance(), 5L);
    }
    
    private void filterShopInventory(Player player, String reason) {
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv == null) {
            Logger.info("Open inventory is null");
            return;
        }
        
        String title = player.getOpenInventory().getTitle();
        Logger.info("🔍 Filtering inventory: '" + title + "' (reason: " + reason + ")");
        
        // Определяем, в какой мы категории
        boolean isToolsCategory = title.contains("Tools") || title.contains("Инструменты");
        boolean isMainMenu = title.contains("Shop") || title.contains("Магазин") || title.contains("Item Shop");
        
        Logger.info("isMainMenu: " + isMainMenu + ", isToolsCategory: " + isToolsCategory);
        
        int hiddenCount = 0;
        int visibleCount = 0;
        
        for (int i = 0; i < openInv.getSize(); i++) {
            ItemStack item = openInv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            
            // Пропускаем навигационные предметы (стекла, стрелки)
            if (isNavigationItem(item)) {
                continue;
            }
            
            ToolType toolType = ToolUpgradeManager.getInstance().getToolType(item);
            if (toolType != null) {
                int itemLevel = ToolUpgradeManager.getInstance().getCurrentLevel(item);
                int playerLevel = ToolUpgradeManager.getInstance().getToolLevel(player, toolType);
                
                Logger.info("   Tool: " + toolType + " | " + item.getType().name() + 
                           " | ItemLevel: " + itemLevel + " | PlayerLevel: " + playerLevel);
                
                boolean shouldShow = false;
                
                if (isMainMenu) {
                    // В главном меню показываем всё
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
                    Logger.info("  ❌ HIDDEN");
                } else {
                    visibleCount++;
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
               name.toLowerCase().contains("page");
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
