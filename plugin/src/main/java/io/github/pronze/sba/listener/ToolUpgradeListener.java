
package io.github.pronze.sba.listener;

import io.github.pronze.sba.SBA;
import io.github.pronze.sba.manager.ToolLevels;
import io.github.pronze.sba.manager.ToolType;
import io.github.pronze.sba.manager.ToolUpgradeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.lib.utils.annotations.methods.OnPostEnable;

@Service
public class ToolUpgradeListener implements Listener {
    
    @OnPostEnable
    public void registerListener() {
        if (SBA.isBroken())
            return;
        SBA.getInstance().registerListener(this);
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        if (!Main.getInstance().isPlayerPlayingAnyGame(player)) {
            return;
        }
        
        var game = Main.getInstance().getGameOfPlayer(player);
        if (game.getStatus() != GameStatus.RUNNING) {
            return;
        }
        
        // Понижаем все инструменты при смерти
        ToolUpgradeManager.getInstance().downgradeAllTools(player);
    }
    
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        if (!Main.getInstance().isPlayerPlayingAnyGame(player)) {
            return;
        }
        
        // При респавне проверяем, что все инструменты на своих уровнях
        var levels = ToolLevels.getOrCreate(player.getUniqueId());
        
        // Обновляем инструменты в инвентаре после респавна
        player.getInventory().forEach(item -> {
            if (item != null && ToolUpgradeManager.getInstance().canUpgrade(item)) {
                ToolType type = ToolUpgradeManager.getInstance().getToolType(item);
                if (type != null) {
                    int currentLevel = ToolUpgradeManager.getInstance().getCurrentLevel(item);
                    int targetLevel = switch (type) {
                        case PICKAXE -> levels.getPickaxeLevel();
                        case AXE -> levels.getAxeLevel();
                        case SHEARS -> levels.getShearsLevel();
                    };
                    
                    if (targetLevel > currentLevel) {
                        // Нужно улучшить предмет
                        ItemStack newItem = ToolUpgradeManager.getInstance().createToolItem(type, targetLevel);
                        if (newItem != null) {
                            // Заменяем в том же слоте
                            int slot = player.getInventory().first(item);
                            if (slot != -1) {
                                player.getInventory().setItem(slot, newItem);
                            }
                        }
                    }
                }
            }
        });
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Очищаем данные при выходе
        ToolUpgradeManager.getInstance().removePlayerData(event.getPlayer().getUniqueId());
    }
}
