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

import java.util.ArrayList;
import java.util.List;

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
                // Обновляем после клика несколько раз для надёжности
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        scheduleInventoryFilter(player, "item click 1");
                    }
                }.runTaskLater(SBA.getPluginInstance(), 5L);
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        scheduleInventoryFilter(player, "item click 2");
                    }
                }.runTaskLater(SBA.getPluginInstance(), 10L);
                
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        scheduleInventoryFilter(player, "item click 3");
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
        }.runTaskLater(SBA.getPluginInstance(), 5L);
    }
    
    private void filterShopInventory(Player player, String reason) {
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv == null) return;
        
        String title = player.getOpenInventory().getTitle();
        
        boolean isToolsCategory = title.contains("Tools") || title.contains("Инструменты");
        if (!isToolsCategory) return;
        
        Logger.info("🔧 Обновляем инструменты для: " + player.getName() + " (причина: " + reason + ")");
        
        boolean updated = false;
        
        for (int i = 0; i < openInv.getSize(); i++) {
            ItemStack item = openInv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            
            // Пропускаем навигационные предметы
            if (isNavigationItem(item)) continue;
            
            ToolType toolType = ToolUpgradeManager.getInstance().getToolType(item);
            if (toolType != null) {
                int playerLevel = ToolUpgradeManager.getInstance().getToolLevel(player, toolType);
                int nextLevel = playerLevel + 1;
                
                // Создаём предмет СЛЕДУЮЩЕГО уровня (который будет доступен для покупки)
                ItemStack newItem = ToolUpgradeManager.getInstance().createToolItem(toolType, playerLevel);
                
                if (newItem != null) {
                    ItemMeta meta = newItem.getItemMeta();
                    
                    // Обновляем название с уровнем
                    String displayName = "§f" + getToolDisplayName(toolType) + " §a§l" + getRomanNumber(nextLevel);
                    meta.setDisplayName(displayName);
                    
                    // Обновляем цену в lore
                    int price = ToolUpgradeManager.getInstance().getPriceForLevel(toolType, playerLevel);
                    String currency = ToolUpgradeManager.getInstance().getCurrencyForLevel(toolType, playerLevel);
                    
                    List<String> lore = new ArrayList<>();
                    lore.add("§7Цена: §e" + price + " " + getCurrencyDisplay(currency));
                    if (!ToolUpgradeManager.getInstance().isMaxLevel(toolType, playerLevel)) {
                        lore.add("§7Следующий уровень: §a" + getRomanNumber(nextLevel + 1));
                    } else {
                        lore.add("§c§lМАКСИМАЛЬНЫЙ УРОВЕНЬ");
                    }
                    meta.setLore(lore);
                    
                    newItem.setItemMeta(meta);
                    
                    // Заменяем предмет в магазине
                    openInv.setItem(i, newItem);
                    Logger.info("  Заменён на " + newItem.getType().name() + " (уровень " + nextLevel + ") с ценой " + price + " " + currency);
                    updated = true;
                }
            }
        }
        
        if (updated) {
            player.updateInventory();
        }
    }
    
    private String getToolDisplayName(ToolType type) {
        switch (type) {
            case PICKAXE: return "Кирка";
            case AXE: return "Топор";
            case SHEARS: return "Ножницы";
            default: return "Инструмент";
        }
    }
    
    private String getCurrencyDisplay(String currency) {
        switch (currency) {
            case "iron": return "железа";
            case "gold": return "золота";
            case "diamond": return "алмазов";
            case "emerald": return "изумрудов";
            default: return currency;
        }
    }
    
    private String getRomanNumber(int level) {
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            default: return String.valueOf(level);
        }
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
}
