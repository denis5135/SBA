package io.github.pronze.sba.listener;

import io.github.pronze.sba.SBA;
import io.github.pronze.sba.manager.ToolLevels;
import io.github.pronze.sba.manager.ToolUpgradeManager;
import io.github.pronze.sba.utils.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.screamingsandals.bedwars.api.events.BedwarsPlayerJoinedEvent;
import org.screamingsandals.bedwars.api.events.BedwarsPlayerLeaveEvent;
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.lib.utils.annotations.methods.OnPostEnable;

@Service
public class ToolCacheListener implements Listener {
    
    @OnPostEnable
    public void registerListener() {
        if (SBA.isBroken()) return;
        SBA.getInstance().registerListener(this);
        Logger.info("✅ ToolCacheListener registered!");
    }
    
    /**
     * Создаём кэш при входе в игру (как в BedWars1058 onArenaJoin)
     */
    @EventHandler
    public void onPlayerJoinArena(BedwarsPlayerJoinedEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        
        // Создаём или очищаем данные при входе
        ToolLevels levels = ToolLevels.getOrCreate(player.getUniqueId());
        levels.setPickaxeLevel(0);
        levels.setAxeLevel(0);
        levels.setShearsLevel(0);
        levels.setPickaxeSlot(-1);
        levels.setAxeSlot(-1);
        levels.setShearsSlot(-1);
        
        Logger.info("📦 Tool cache created for player: " + player.getName());
    }
    
    /**
     * Удаляем кэш при выходе из игры (как в BedWars1058 onArenaLeave)
     */
    @EventHandler
    public void onPlayerLeaveArena(BedwarsPlayerLeaveEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        
        ToolUpgradeManager.getInstance().removePlayerData(player.getUniqueId());
        Logger.info("🗑️ Tool cache removed for player: " + player.getName());
    }
}
