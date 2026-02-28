package io.github.pronze.sba.listener;

import io.github.pronze.sba.SBA;
import io.github.pronze.sba.manager.ToolLevels;
import io.github.pronze.sba.manager.ToolType;
import io.github.pronze.sba.manager.ToolUpgradeManager;
import io.github.pronze.sba.utils.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
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
        if (SBA.isBroken()) return;
        SBA.getInstance().registerListener(this);
        Logger.info("✅ ToolUpgradeListener registered!");
    }
    
    /**
     * Обработка смерти - понижение инструментов
     */
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
        Logger.info("💀 Tools downgraded for player: " + player.getName());
    }
    
    /**
     * При респавне выдаём инструменты текущего уровня
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        if (!Main.getInstance().isPlayerPlayingAnyGame(player)) {
            return;
        }
        
        var levels = ToolLevels.getOrCreate(player.getUniqueId());
        
        // Выдаём инструменты текущего уровня
        if (levels.getPickaxeLevel() > 0) {
            ToolUpgradeManager.getInstance().giveToolItem(player, ToolType.PICKAXE, levels.getPickaxeLevel());
        }
        if (levels.getAxeLevel() > 0) {
            ToolUpgradeManager.getInstance().giveToolItem(player, ToolType.AXE, levels.getAxeLevel());
        }
        if (levels.getShearsLevel() > 0) {
            ToolUpgradeManager.getInstance().giveToolItem(player, ToolType.SHEARS, levels.getShearsLevel());
        }
        
        Logger.info("🔄 Tools restored for player: " + player.getName());
    }
}
