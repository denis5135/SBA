package io.github.pronze.sba.levels;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class LevelListener implements Listener {
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Загружаем игрока в кэш при входе
        PlayerLevelManager.getInstance().loadPlayer(event.getPlayer());
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Сохраняем и выгружаем игрока при выходе
        PlayerLevelManager.getInstance().unloadPlayer(event.getPlayer());
    }
}
