package io.github.pronze.lib.pronzelib.scoreboards;

import io.github.pronze.lib.pronzelib.scoreboards.scoreboardr.board.BoardPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;
import java.util.Objects;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.UUID;
import org.bukkit.event.Listener;

import io.github.pronze.lib.pronzelib.scoreboards.scoreboardr.plugin.Session;
import io.github.pronze.sba.utils.Logger;

public class ScoreboardManager implements Listener {
    private static ScoreboardManager instance;
    private final Map<UUID, Scoreboard> cachedBoards;
    private boolean toReset;
    private boolean legacy;
    private JavaPlugin plugin;
    
    public ScoreboardManager() {
        this.cachedBoards = new ConcurrentHashMap<>();
        this.toReset = true;
    }
    
    public static ScoreboardManager init(final JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "Plugin instance cannot be null");
        Session.makeSession(plugin);
        if (ScoreboardManager.instance != null) {
            ScoreboardManager.instance.onDisable();
        }
        ScoreboardManager.instance = new ScoreboardManager();
        ScoreboardManager.instance.plugin = plugin;
        Bukkit.getServer().getPluginManager().registerEvents(ScoreboardManager.instance, plugin);
        
        final String[] bukkitVersion = Bukkit.getBukkitVersion().split("-")[0].split("\\.");
        int versionNumber = 0;
        for (int i = 0; i < 2; ++i) {
            versionNumber += Integer.parseInt(bukkitVersion[i]) * ((i == 0) ? 100 : 1);
        }
        ScoreboardManager.instance.legacy = (versionNumber < 113);
        return ScoreboardManager.instance;
    }
    
    public static boolean isLegacy() {
        return ScoreboardManager.instance.legacy;
    }
    
    public static void setResetBoardsOnDisabled(final boolean boardsOnDisabled) {
        ScoreboardManager.instance.toReset = boardsOnDisabled;
    }
    
    public static JavaPlugin getPluginInstance() {
        return ScoreboardManager.instance.plugin;
    }
    
    public static ScoreboardManager getInstance() {
        return ScoreboardManager.instance;
    }
    
    public void onDisable() {
        HandlerList.unregisterAll(ScoreboardManager.instance);
        if (!this.toReset) {
            return;
        }
        List.copyOf(this.cachedBoards.values()).forEach(Scoreboard::destroy);
        this.cachedBoards.clear();
    }
    
    public void addToCache(final Scoreboard board) {
        Objects.requireNonNull(board, "Board cannot be null!");
        this.cachedBoards.put(board.getPlayer().getUniqueId(), board);
    }
    
    public void removeFromCache(final UUID uuid) {
        Scoreboard board = this.cachedBoards.remove(uuid);
        if (board != null) {
            try {
                board.destroy();
            } catch (Exception e) {
                Logger.error("Error destroying board: " + e.getMessage());
            }
        }
    }
    
    public Optional<Scoreboard> fromCache(final UUID uuid) {
        return Optional.ofNullable(this.cachedBoards.get(uuid));
    }
    
    public void resetPlayerBoard(Player player) {
        if (player == null || !player.isOnline()) return;
        
        UUID uuid = player.getUniqueId();
        removeFromCache(uuid);
        
        // Устанавливаем пустой скорборд
        try {
            var manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                player.setScoreboard(manager.getNewScoreboard());
            }
        } catch (Exception e) {
            Logger.error("Error resetting player board: " + e.getMessage());
        }
    }
    
    @EventHandler
    public void onQuit(final PlayerQuitEvent e) {
        removeFromCache(e.getPlayer().getUniqueId());
        
        var bp = BoardPlayer.getBoardPlayerOrNull(e.getPlayer());
        if (bp != null) {
            bp.kill();
        }
    }
    
    @EventHandler
    public void onJoin(final PlayerJoinEvent e) {
        // Очищаем кэш при входе, чтобы создать новый скорборд
        removeFromCache(e.getPlayer().getUniqueId());
    }
    
    @EventHandler
    public void onWorldChange(final PlayerChangedWorldEvent e) {
        // При смене мира удаляем из кэша, чтобы создать новый скорборд если нужно
        removeFromCache(e.getPlayer().getUniqueId());
    }
}
