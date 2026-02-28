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
        
        if (title.contains("Shop") || title.contains("Магазин") || 
            title.contains("Tools") || title.contains("Инструменты")) {
            
            if (!SBAConfig.getInstance().isToolUpgradeEnabled()) return;
            
            scheduleInventoryFilter(player, "inventory open");
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
            ItemStack clicked = event.getCurrentItem();
            
            if (!isNavigationItem(clicked)) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        scheduleInventoryFilter(player, "item click");
                    }
                }.runTaskLater(SBA.getPluginInstance(), 5L);
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
        }.runTaskLater(SBA.getPluginInstance(), 5L);
    }
    
    private void filterShopInventory(Player player, String reason) {
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv == null) return;
        
        String title = player.getOpenInventory().getTitle();
        Logger.info("🔍 Filtering inventory: '" + title + "' (reason: " + reason + ")");
        
        // Считаем инструменты
        int toolCount = 0;
        for (ItemStack item : openInv.getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (ToolUpgradeManager.getInstance().getToolType(item) != null) {
                toolCount++;
            }
        }
        
        boolean isToolsCategory = toolCount >= 3;
        Logger.info("toolCount: " + toolCount + ", isToolsCategory: " + isToolsCategory);
        
        int hiddenCount = 0;
        int visibleCount = 0;
        
        for (int i = 0; i < openInv.getSize(); i++) {
            ItemStack item = openInv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            
            if (isNavigationItem(item)) continue;
            
            // Золотая кирка - это иконка категории, её НИКОГДА не скрываем
            if (item.getType() == Material.GOLDEN_PICKAXE) {
                visibleCount++;
                continue;
            }
            
            ToolType toolType = ToolUpgradeManager.getInstance().getToolType(item);
            if (toolType != null) {
                int itemLevel = ToolUpgradeManager.getInstance().getCurrentLevel(item);
                int playerLevel = ToolUpgradeManager.getInstance().getToolLevel(player, toolType);
                
                boolean shouldShow = true;
                
                if (isToolsCategory) {
                    // В категории инструментов фильтруем!
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
