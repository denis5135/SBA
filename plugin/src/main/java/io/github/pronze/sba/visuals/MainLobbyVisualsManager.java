package io.github.pronze.sba.visuals;

import io.github.pronze.sba.MessageKeys;
import io.github.pronze.sba.lib.lang.LanguageService;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.events.BedwarsPlayerJoinedEvent;
import org.screamingsandals.bedwars.api.events.BedwarsPlayerLeaveEvent;
import org.screamingsandals.lib.plugin.ServiceManager;
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.lib.utils.annotations.methods.OnPostEnable;
import org.screamingsandals.lib.utils.annotations.methods.OnPreDisable;
import io.github.pronze.sba.SBA;
import io.github.pronze.sba.config.SBAConfig;
import io.github.pronze.sba.utils.SBAUtil;
import io.github.pronze.sba.utils.ShopUtil;
import io.github.pronze.lib.pronzelib.scoreboards.Scoreboard;
import io.github.pronze.lib.pronzelib.scoreboards.ScoreboardManager;
import io.github.pronze.sba.levels.PlayerLevelManager;
import io.github.pronze.sba.utils.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MainLobbyVisualsManager implements Listener {
    private final static String MAIN_LOBBY_OBJECTIVE = "sbascoreboard";
    private static Location location;
    private final Map<Player, Scoreboard> scoreboardMap = new ConcurrentHashMap<>();
    private boolean enabled;

    public static MainLobbyVisualsManager getInstance() {
        return ServiceManager.get(MainLobbyVisualsManager.class);
    }

    @OnPostEnable
    public void registerListener() {
        if (SBA.isBroken()) return;
        SBA.getInstance().registerListener(this);
        load();
    }

    public void load() {
        if (!SBAConfig.getInstance().getBoolean("main-lobby.enabled", false)) {
            enabled = false;
            return;
        }
        enabled = true;
        SBAUtil.readLocationFromConfig("main-lobby").ifPresentOrElse(loc -> {
            location = loc;
            // Создаем скорборды для всех в лобби
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (isInWorld(p.getLocation()) && !Main.isPlayerInGame(p)) {
                    create(p);
                }
            });
        }, () -> {
            enabled = false;
            Bukkit.getLogger().warning("Could not find lobby world!");
        });
    }

    @OnPreDisable
    public void disable() {
        // Просто очищаем всё
        for (Scoreboard board : scoreboardMap.values()) {
            if (board != null) {
                try { board.destroy(); } catch (Exception ignored) {}
            }
        }
        scoreboardMap.clear();
    }

    public static boolean isInWorld(Location loc) {
        try {
            return loc != null && location != null && 
                   loc.getWorld() != null && location.getWorld() != null &&
                   loc.getWorld().equals(location.getWorld());
        } catch (Throwable t) {
            return false;
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!enabled) return;
        // ... остальной код чата без изменений ...
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (!enabled) return;

        Player player = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(SBA.getPluginInstance(), () -> {
            if (player == null || !player.isOnline()) return;
            
            // Если игрок в лобби и не в игре
            if (isInWorld(player.getLocation()) && !Main.isPlayerInGame(player)) {
                // Удаляем старый скорборд если есть
                Scoreboard old = scoreboardMap.remove(player);
                if (old != null) {
                    try { old.destroy(); } catch (Exception ignored) {}
                }
                ScoreboardManager.getInstance().removeFromCache(player.getUniqueId());
                
                // Создаем новый
                create(player);
            } 
            // Если игрок не в лобби
            else {
                Scoreboard old = scoreboardMap.remove(player);
                if (old != null) {
                    try { old.destroy(); } catch (Exception ignored) {}
                }
                ScoreboardManager.getInstance().removeFromCache(player.getUniqueId());
            }
        }, 10L); // Задержка полсекунды
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (!enabled) return;
        
        Player player = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(SBA.getPluginInstance(), () -> {
            if (player == null || !player.isOnline()) return;
            
            if (isInWorld(player.getLocation()) && !Main.isPlayerInGame(player)) {
                create(player);
            }
        }, 10L);
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        Scoreboard board = scoreboardMap.remove(player);
        if (board != null) {
            try { board.destroy(); } catch (Exception ignored) {}
        }
        ScoreboardManager.getInstance().removeFromCache(player.getUniqueId());
    }

    @EventHandler
    public void onBedWarsPlayerJoin(BedwarsPlayerJoinedEvent e) {
        Player player = e.getPlayer();
        Scoreboard board = scoreboardMap.remove(player);
        if (board != null) {
            try { board.destroy(); } catch (Exception ignored) {}
        }
        ScoreboardManager.getInstance().removeFromCache(player.getUniqueId());
    }

    @EventHandler
    public void onBedWarsPlayerLeaveEvent(BedwarsPlayerLeaveEvent e) {
        if (!enabled) return;
        
        Player player = e.getPlayer();
        if (player == null) return;
        
        // Ждем телепортацию в лобби
        Bukkit.getScheduler().runTaskLater(SBA.getPluginInstance(), () -> {
            if (player == null || !player.isOnline()) return;
            
            // Проверяем что игрок в лобби и не в игре
            if (isInWorld(player.getLocation()) && !Main.isPlayerInGame(player)) {
                // Удаляем старый и создаем новый скорборд
                Scoreboard old = scoreboardMap.remove(player);
                if (old != null) {
                    try { old.destroy(); } catch (Exception ignored) {}
                }
                ScoreboardManager.getInstance().removeFromCache(player.getUniqueId());
                
                create(player);
                Logger.trace("Scoreboard recreated for " + player.getName());
            }
        }, 20L); // Секунда после игры
    }

    public void create(Player player) {
        if (!enabled) return;
        if (player == null || !player.isOnline()) return;
        if (!isInWorld(player.getLocation())) return;
        if (Main.isPlayerInGame(player)) return;
        
        // Если уже есть скорборд - не создаем новый
        if (scoreboardMap.containsKey(player)) return;
        
        try {
            // Таблистер
            if (SBAConfig.getInstance().node("main-lobby", "tablist-modifications").getBoolean()) {
                try {
                    var playerData = SBA.getInstance().getPlayerWrapperService().get(player).orElse(null);
                    if (playerData != null) {
                        var header = LanguageService.getInstance().get(MessageKeys.MAIN_LOBBY_TABLIST_HEADER)
                                .replace("%sba_version%", SBA.getInstance().getVersion()).toComponent();
                        var footer = LanguageService.getInstance().get(MessageKeys.MAIN_LOBBY_TABLIST_FOOTER)
                                .replace("%sba_version%", SBA.getInstance().getVersion()).toComponent();
                        playerData.sendPlayerListHeaderFooter(header, footer);
                    }
                } catch (Exception e) {
                    Logger.error("Failed to set tablist: " + e.getMessage());
                }
            }

            // Создаем скорборд
            String title = LanguageService.getInstance().get(MessageKeys.MAIN_LOBBY_SCOREBOARD_TITLE).toString();
            List<String> lines = getScoreboardLines(player);

            final var scoreboard = Scoreboard.builder()
                    .animate(true)
                    .player(player)
                    .title(title)
                    .displayObjective(MAIN_LOBBY_OBJECTIVE)
                    .updateInterval(100L) // Обновление раз в 5 секунд
                    .lines(lines)
                    .placeholderHook(hook -> {
                        try {
                            var lm = PlayerLevelManager.getInstance();
                            int level = lm.getPlayerLevel(player);
                            String prefix = lm.getPlayerPrefix(player);
                            int xp = lm.getPlayerXP(player);
                            int required = lm.getXPToNextLevel(player);
                            double progress = lm.getLevelProgress(player);

                            String bar = createModernBar(progress);
                            var stats = Main.getPlayerStatisticsManager().getStatistic(player);

                            if (stats == null) {
                                return hook.getLine()
                                        .replace("%kills%", "0")
                                        .replace("%beds%", "0")
                                        .replace("%wins%", "0")
                                        .replace("%losses%", "0")
                                        .replace("%games%", "0")
                                        .replace("%winrate%", "0%")
                                        .replace("%kdr%", "0.0")
                                        .replace("%level%", prefix + " " + level + "✫")
                                        .replace("%sba_player_level_prefix%", prefix)
                                        .replace("%sba_player_level_number%", String.valueOf(level))
                                        .replace("%sba_player_xp%", formatNumber(xp))
                                        .replace("%sba_player_level_required%", formatNumber(required))
                                        .replace("%progress%", (int)(progress*100) + "%")
                                        .replace("%bar%", bar);
                            }

                            int total = stats.getWins() + stats.getDeaths();
                            int losses = total - stats.getWins();
                            double winRate = total > 0 ? (double) stats.getWins() / total * 100 : 0;

                            return hook.getLine()
                                    .replace("%kills%", formatNumber(stats.getKills()))
                                    .replace("%beds%", formatNumber(stats.getDestroyedBeds()))
                                    .replace("%wins%", formatNumber(stats.getWins()))
                                    .replace("%losses%", formatNumber(losses))
                                    .replace("%games%", formatNumber(total))
                                    .replace("%winrate%", String.format("%.1f", winRate) + "%")
                                    .replace("%kdr%", String.format("%.2f", stats.getKD()))
                                    .replace("%level%", prefix + " " + level + "✫")
                                    .replace("%sba_player_level_prefix%", prefix)
                                    .replace("%sba_player_level_number%", String.valueOf(level))
                                    .replace("%sba_player_xp%", formatNumber(xp))
                                    .replace("%sba_player_level_required%", formatNumber(required))
                                    .replace("%progress%", (int)(progress*100) + "%")
                                    .replace("%bar%", bar);
                        } catch (Exception e) {
                            return hook.getLine();
                        }
                    })
                    .build();

            // Анимированный заголовок
            try {
                List<String> animated = LanguageService.getInstance()
                        .get(MessageKeys.MAIN_LOBBY_SCOREBOARD_ANIMATED_TITLE)
                        .toStringList();
                if (animated != null && animated.size() > 1) {
                    scoreboard.setAnimatedTitle(animated);
                }
            } catch (Exception ignored) {}

            scoreboardMap.put(player, scoreboard);
            ScoreboardManager.getInstance().addToCache(scoreboard);
            
        } catch (Exception e) {
            Logger.error("Failed to create scoreboard: " + e.getMessage());
        }
    }

    private String formatNumber(int n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fk", n / 1_000.0);
        return String.valueOf(n);
    }

    private String createModernBar(double progress) {
        int len = 10;
        int filled = (int) Math.round(progress * len);
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < len; i++) bar.append(i < filled ? "§b■" : "§7■");
        bar.append("§8]");
        return bar.toString();
    }

    private List<String> getScoreboardLines(Player player) {
        try {
            return LanguageService.getInstance()
                    .get(MessageKeys.MAIN_LOBBY_SCOREBOARD_LINES)
                    .toStringList();
        } catch (Exception e) {
            return Arrays.asList(
                "<gray>» <gold>%rank%</gold> <gray>«</gray>",
                "",
                "<white>Уровень:</white> <green>%level%</green>",
                "<white>Опыт:</white> <green>%sba_player_xp%</green><gray>/</gray><green>%sba_player_level_required%</green>",
                "%bar%",
                "",
                "<white>Убийств:</white> <green>%kills%</green>",
                "<white>Побед:</white> <green>%wins%</green>",
                "<white>Кроватей:</white> <green>%beds%</green>",
                "",
                "<yellow>play.server.com</yellow>"
            );
        }
    }
}
