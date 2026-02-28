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
import org.bukkit.event.inventory.InventoryType;
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
        InventoryType type = event.getInventory().getType();
        
        Logger.info("📂 Inventory opened: '" + title + "' type: " + type + " by " + player.getName());
        
        // Фильтруем ЛЮБОЙ инвентарь, который может быть магазином
        if (title.contains("Shop") || title.contains("Магазин") || 
            title.contains("Tools") || title.contains("Инструменты") ||
            title.contains("Upgrade") || title.contains("Улучшения") ||
            type == InventoryType.CHEST || type == InventoryType.WORKBENCH) {
            
            if (!SBAConfig.getInstance().isToolUpgradeEnabled()) return;
            
            scheduleInventoryFilter(player, "inventory open: " + title + " (" + type + ")");
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        
        // Логируем каждый клик
        Logger.info("🖱️ Click in inventory: '" + title + "' by " + player.getName());
        Logger.info("   Slot: " + event.getSlot());
        Logger.info("   Raw Slot: " + event.getRawSlot());
        
        if (event.getCurrentItem() != null) {
            ItemStack clicked = event.getCurrentItem();
            Logger.info("   Clicked item: " + clicked.getType().name());
            
            if (clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
                Logger.info("   Item name: " + clicked.getItemMeta().getDisplayName());
            }
            
            // Проверяем все возможные иконки категорий
            if (clicked.getType().name().contains("PICKAXE") ||
                clicked.getType().name().contains("SWORD") ||
                clicked.getType().name().contains("BOW") ||
                clicked.getType().name().contains("BOOTS") ||
                clicked.getType() == Material.SHEARS) {
                
                Logger.info("🔧 CATEGORY ICON CLICKED! Type: " + clicked.getType().name());
                
                // Даём время на открытие новой категории
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        String newTitle = player.getOpenInventory().getTitle();
                        InventoryType newType = player.getOpenInventory().getType();
                        Logger.info("New inventory after click: '" + newTitle + "' type: " + newType);
                        
                        scheduleInventoryFilter(player, "category click - new title: " + newTitle);
                    }
                }.runTaskLater(SBA.getPluginInstance(), 20L);
            }
        }
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
        }.runTaskLater(SBA.getPluginInstance(), 10L);
    }
    
    private void filterShopInventory(Player player, String reason) {
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv == null) {
            Logger.info("Open inventory is null");
            return;
        }
        
        String title = player.getOpenInventory().getTitle();
        InventoryType type = player.getOpenInventory().getType();
        Logger.info("🔍 Filtering inventory: '" + title + "' type: " + type + " (reason: " + reason + ")");
        
        boolean isToolsCategory = title.contains("Tools") || title.contains("Инструменты") || type == InventoryType.WORKBENCH;
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
                
                boolean shouldShow = false;
                
                if (isMainMenu) {
                    // В главном меню показываем всё
                    shouldShow = true;
                } else if (isToolsCategory) {
                    // В категории инструментов фильтруем
                    if (playerLevel == 0) {
                        shouldShow = (itemLevel == 0);
                    } else {
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
                    
                    Logger.info("   Tool: " + toolType + " level " + itemLevel + " shouldShow: " + shouldShow);
                } else {
                    shouldShow = true;
                }
                
                if (!shouldShow) {
                    openInv.setItem(i, createPlaceholderItem());
                    hiddenCount++;
                    Logger.info("  ❌ Hidden " + item.getType().name() + " (level " + itemLevel + ")");
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
        return type.name().contains("STAINED_GLASS_PANE") ||
               type == Material.ARROW ||
               type == Material.BARRIER ||
               type == Material.NETHER_STAR;
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
