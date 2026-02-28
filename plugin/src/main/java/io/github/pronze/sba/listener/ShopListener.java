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
    }
    
    @EventHandler
    public void onShopOpen(BedwarsOpenShopEvent event) {
        Player player = event.getPlayer();
        
        // Проверяем, включены ли улучшения инструментов
        if (!SBAConfig.getInstance().isToolUpgradeEnabled()) return;
        
        // Откладываем фильтрацию с помощью BukkitRunnable
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    filterShopInventory(player);
                } catch (Exception e) {
                    Logger.error("Error filtering shop inventory: " + e.getMessage());
                }
            }
        }.runTaskLater(SBA.getPluginInstance(), 5L); // 5 тиков задержки
    }
    
    private void filterShopInventory(Player player) {
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv == null) return;
        
        // Получаем название инвентаря, чтобы понять, где мы находимся
        String title = player.getOpenInventory().getTitle();
        boolean isMainMenu = title.contains("Shop") || title.contains("Магазин") || title.contains("Item Shop");
        boolean isToolsCategory = title.contains("Tools") || title.contains("Инструменты");
        
        for (int i = 0; i < openInv.getSize(); i++) {
            ItemStack item = openInv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            
            // Пропускаем иконки навигации и декоративные стекла
            if (isPlaceholderOrNavigation(item)) continue;
            
            // Проверяем, является ли предмет инструментом
            ToolType toolType = ToolUpgradeManager.getInstance().getToolType(item);
            if (toolType != null) {
                int itemLevel = ToolUpgradeManager.getInstance().getCurrentLevel(item);
                int playerLevel = ToolUpgradeManager.getInstance().getToolLevel(player, toolType);
                
                boolean shouldShow = false;
                
                // Если это главное меню, показываем только иконку категории (каменная кирка)
                if (isMainMenu) {
                    // В главном меню показываем все иконки категорий
                    shouldShow = true;
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
                } else {
                    // В других категориях показываем всё
                    shouldShow = true;
                }
                
                if (!shouldShow) {
                    // Скрываем предмет, заменяя на стекло
                    openInv.setItem(i, createPlaceholderItem());
                    Logger.info("Hiding tool: " + item.getType() + " for player " + player.getName());
                } else {
                    Logger.info("Showing tool: " + item.getType() + " for player " + player.getName());
                }
            }
        }
    }
    
    private boolean isPlaceholderOrNavigation(ItemStack item) {
        if (item == null) return false;
        
        Material type = item.getType();
        String name = "";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            name = item.getItemMeta().getDisplayName();
        }
        
        // Проверяем, является ли предмет навигационным (стекла, стрелки назад и т.д.)
        return type == Material.GREEN_STAINED_GLASS_PANE ||
               type == Material.RED_STAINED_GLASS_PANE ||
               type == Material.GRAY_STAINED_GLASS_PANE ||
               type == Material.BLACK_STAINED_GLASS_PANE ||
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
