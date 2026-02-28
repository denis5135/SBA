package io.github.pronze.sba.listener;

import io.github.pronze.sba.SBA;
import io.github.pronze.sba.config.SBAConfig;
import io.github.pronze.sba.manager.ToolType;
import io.github.pronze.sba.manager.ToolUpgradeManager;
import io.github.pronze.sba.utils.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.events.BedwarsOpenShopEvent;
import org.screamingsandals.bedwars.api.game.Game;
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
        
        // Откладываем проверку, чтобы инвентарь успел создаться
        org.bukkit.Bukkit.getScheduler().runTaskLater(SBA.getPluginInstance(), () -> {
            try {
                filterShopInventory(player);
            } catch (Exception e) {
                Logger.error("Error filtering shop inventory: " + e.getMessage());
            }
        }, 5L); // 5 тиков задержки
    }
    
    private void filterShopInventory(Player player) {
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv == null) return;
        
        if (!SBAConfig.getInstance().isToolUpgradeEnabled()) return;
        
        for (int i = 0; i < openInv.getSize(); i++) {
            ItemStack item = openInv.getItem(i);
            if (item == null) continue;
            
            ToolType toolType = ToolUpgradeManager.getInstance().getToolType(item);
            if (toolType != null) {
                int itemLevel = ToolUpgradeManager.getInstance().getCurrentLevel(item);
                int playerLevel = ToolUpgradeManager.getInstance().getToolLevel(player, toolType);
                
                boolean shouldShow = false;
                
                // Показываем только нужные уровни
                if (playerLevel == 0) {
                    shouldShow = (itemLevel == 0); // Только деревянные
                } else {
                    shouldShow = (itemLevel == playerLevel || itemLevel == playerLevel + 1); // Текущий и следующий
                }
                
                if (!shouldShow) {
                    // Заменяем на стекло, чтобы скрыть
                    openInv.setItem(i, createPlaceholderItem());
                } else {
                    Logger.info("Showing tool: " + item.getType() + " for player " + player.getName());
                }
            }
        }
    }
    
    private ItemStack createPlaceholderItem() {
        ItemStack placeholder = new ItemStack(org.bukkit.Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = placeholder.getItemMeta();
        meta.setDisplayName(" ");
        placeholder.setItemMeta(meta);
        return placeholder;
    }
}
