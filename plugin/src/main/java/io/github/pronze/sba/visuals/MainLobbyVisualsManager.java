package io.github.pronze.sba.visuals;

import io.github.pronze.sba.MessageKeys;
import io.github.pronze.sba.lib.lang.LanguageService;
import me.clip.placeholderapi.PlaceholderAPI;
import org.screamingsandals.lib.player.Players;
import org.screamingsandals.lib.spectator.Component;
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
    private final Map<Player, BukkitTask> updateTasks = new ConcurrentHashMap<>();
    private boolean enabled;
    private boolean debugMode = false;

    public static MainLobbyVisualsManager getInstance() {
        return ServiceManager.get(MainLobbyVisualsManager.class);
    }

    @OnPostEnable
    public void registerListener() {
        if (SBA.isBroken())
            return;
        SBA.getInstance().registerListener(this);
        load();
    }

    public void reload() {
        disable();
        load();
    }

    public void load() {
        if (!SBAConfig.getInstance().getBoolean("main-lobby.enabled", false)) {
            enabled = false;
            return;
        }
        enabled = true;
        SBAUtil.readLocationFromConfig("main-lobby").ifPresentOrElse(location -> {
            MainLobbyVisualsManager.location = location;
            // Создаем скорборды для всех онлайн игроков мгновенно
            Bukkit.getOnlinePlayers().forEach(this::create);
        }, () -> {
            disable();
            Bukkit.getServer().getLogger().warning("Could not find lobby world!");
        });
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

    public static boolean hasMainLobbyObjective(Player player) {
        try {
            return player != null && player.getScoreboard() != null && 
                   player.getScoreboard().getObjective(MAIN_LOBBY_OBJECTIVE) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!enabled) return;
        if (!SBAConfig.getInstance().node("main-lobby", "custom-chat").getBoolean(true)) return;

        final var player = e.getPlayer();

        if (SBAConfig.getInstance().node("main-lobby", "enabled").getBoolean(false)
                && MainLobbyVisualsManager.isInWorld(e.getPlayer().getLocation())) {
            if (Main.isPlayerInGame(player)) return;
            
            try {
                var chatFormat = LanguageService.getInstance()
                        .get(MessageKeys.MAIN_LOBBY_CHAT_FORMAT)
                        .toString();

                if (chatFormat != null) {
                    var levelManager = PlayerLevelManager.getInstance();
                    int playerLevel = levelManager.getPlayerLevel(player);
                    String playerPrefix = levelManager.getPlayerPrefix(player);
                    
                    var format = chatFormat
                            .replace("%level%", String.valueOf(playerLevel))
                            .replace("%prefix%", playerPrefix)
                            .replace("%name%", e.getPlayer().getDisplayName() + ChatColor.RESET)
                            .replace("%message%", e.getMessage())
                            .replace("%color%", ShopUtil.ChatColorChanger(e.getPlayer()));

                    if (SBA.getPluginInstance().getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                        format = PlaceholderAPI.setPlaceholders(player, format);
                    }
                    final var msgToSend = format;
                    Bukkit.getServer().getOnlinePlayers().forEach(p -> {
                        if (MainLobbyVisualsManager.isInWorld(p.getLocation())
                                && Main.getInstance().getGameOfPlayer(p) == null)
                            p.sendMessage(msgToSend);
                    });

                    e.setCancelled(true);
                }
            } catch (Exception ex) {
                Logger.error("Error in chat formatting: " + ex.getMessage());
            }
        }
    }

    @OnPreDisable
    public void disable() {
        try {
            for (BukkitTask task : updateTasks.values()) {
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
            }
            updateTasks.clear();
            
            for (Scoreboard board : scoreboardMap.values()) {
                if (board != null) {
                    try {
                        board.destroy();
                    } catch (Exception e) {
                        // Игнорируем
                    }
                }
            }
            scoreboardMap.clear();
        } catch (Exception e) {
            Logger.error("Error disabling MainLobbyVisualsManager: " + e.getMessage());
        }
        enabled = false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (!enabled) return;

        final var player = e.getPlayer();

        // Уменьшаем задержку с 20 до 2 тиков (0.1 секунды)
        Bukkit.getServer().getScheduler().runTaskLater(SBA.getPluginInstance(), () -> {
            try {
                if (player != null && player.isOnline() && 
                    isInWorld(player.getLocation()) && !Main.isPlayerInGame(player)) {
                    
                    ScoreboardManager.getInstance().removeFromCache(player.getUniqueId());
                    
                    if (!scoreboardMap.containsKey(player)) {
                        create(player);
                    }
                }
            } catch (Exception ex) {
                Logger.error("Error in player join: " + ex.getMessage());
            }
        }, 2L); // Было 20L, стало 2L
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (!enabled) return;

        final var player = e.getPlayer();
        try {
            if (player != null && player.isOnline()) {
                if (isInWorld(player.getLocation()) && !Main.isPlayerInGame(player)) {
                    // Мгновенное создание при входе в мир лобби
                    if (!scoreboardMap.containsKey(player)) {
                        create(player);
                    } else {
                        Scoreboard board = scoreboardMap.get(player);
                        if (board != null) {
                            try {
                                board.setVisibility(true);
                                board.refresh();
                            } catch (Exception ex) {
                                destroyPlayerBoard(player);
                                create(player);
                            }
                        }
                    }
                } else {
                    Scoreboard board = scoreboardMap.get(player);
                    if (board != null) {
                        try {
                            board.setVisibility(false);
                        } catch (Exception ex) {
                            // Игнорируем
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Logger.error("Error in world change: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        
        BukkitTask task = updateTasks.remove(player);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        
        Scoreboard board = scoreboardMap.remove(player);
        if (board != null) {
            try {
                board.destroy();
            } catch (Exception ex) {
                // Игнорируем
            }
        }
    }

    private void destroyPlayerBoard(Player player) {
        Scoreboard oldBoard = scoreboardMap.remove(player);
        if (oldBoard != null) {
            try {
                oldBoard.destroy();
            } catch (Exception e) {
                // Игнорируем
            }
        }
        
        BukkitTask task = updateTasks.remove(player);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        
        ScoreboardManager.getInstance().removeFromCache(player.getUniqueId());
    }

    private String formatNumber(int number) {
        if (number >= 1_000_000) {
            double millions = number / 1_000_000.0;
            return String.format("%.1fM", millions);
        } else if (number >= 1_000) {
            double thousands = number / 1_000.0;
            return String.format("%.1fk", thousands);
        }
        return String.valueOf(number);
    }

    private boolean isLegacyVersion() {
        try {
            String version = Bukkit.getBukkitVersion().split("-")[0];
            String[] ver = version.split("\\.");
            int major = Integer.parseInt(ver[0]);
            int minor = Integer.parseInt(ver[1]);
            
            boolean legacy = major == 1 && minor <= 12;
            
            if (debugMode) {
                Logger.info("Player version detected: " + version + " | Legacy: " + legacy);
            }
            
            return legacy;
        } catch (Exception e) {
            return false;
        }
    }

    private String createLegacyBar(double progress) {
        int barLength = 8;
        int filledBars = (int) Math.round(progress * barLength);
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                bar.append("§b■");
            } else {
                bar.append("§7■");
            }
        }
        bar.append("§8]");
        return bar.toString();
    }

    private String createModernBar(double progress) {
        int barLength = 12;
        int filledBars = (int) Math.round(progress * barLength);
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                bar.append("§b■");
            } else {
                bar.append("§7■");
            }
        }
        bar.append("§8]");
        return bar.toString();
    }

    private List<String> getScoreboardLines(Player player) {
        try {
            return LanguageService.getInstance()
                    .get(MessageKeys.MAIN_LOBBY_SCOREBOARD_LINES)
                    .toStringList();
        } catch (Exception e) {
            Logger.error("Failed to load scoreboard lines from language file: " + e.getMessage());
            return Arrays.asList(
                "<gray>» <gold>%rank%</gold> <gray>«</gray>",
                "",
                "<white>Уровень:</white> <green>%sba_player_level_prefix% %sba_player_level_number%</green>",
                "<white>Опыт:</white> <green>%sba_player_xp%</green><gray>/</gray><green>%sba_player_level_required%</green>",
                "%bar%",
                "",
                "<white>Всего Убийств:</white> <green>%kills%</green>",
                "<white>Всего Побед:</white> <green>%wins%</green>",
                "<white>Сломано Кроватей:</white> <green>%beds%</green>",
                "<white>Убийства/Смерти:</white> <green>%kdr%</green>",
                "<white>Выигрыши/Поражения:</white> <green>%wins%</green><gray>/</gray><green>%losses%</green> <gray>(</gray><yellow>%winrate%</yellow><gray>)</gray>",
                "<white>Игр сыграно:</white> <green>%games%</green>",
                "",
                "<yellow>play.BenGangGames.com</yellow>"
            );
        }
    }

    private void startAutoUpdate(Player player) {
        if (player == null) return;
        
        BukkitTask oldTask = updateTasks.remove(player);
        if (oldTask != null && !oldTask.isCancelled()) {
            oldTask.cancel();
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(SBA.getPluginInstance(), () -> {
            try {
                if (player == null || !player.isOnline()) {
                    BukkitTask t = updateTasks.remove(player);
                    if (t != null && !t.isCancelled()) {
                        t.cancel();
                    }
                    return;
                }
                
                if (!isInWorld(player.getLocation()) || Main.isPlayerInGame(player)) {
                    return;
                }
                
                Scoreboard board = scoreboardMap.get(player);
                if (board != null) {
                    try {
                        board.setVisibility(true);
                        board.refresh();
                    } catch (Exception e) {
                        Logger.error("Error refreshing scoreboard for " + player.getName() + ": " + e.getMessage());
                        destroyPlayerBoard(player);
                        create(player);
                    }
                } else {
                    create(player);
                }
            } catch (Exception e) {
                Logger.error("Error in auto-update for " + player.getName() + ": " + e.getMessage());
            }
        }, 20L, 20L); // Первое обновление через 1 секунду, потом каждую секунду

        updateTasks.put(player, task);
    }

    public void create(Player player) {
        if (!enabled) return;
        if (player == null || !player.isOnline()) return;
        if (!isInWorld(player.getLocation())) return;
        
        destroyPlayerBoard(player);
        
        try {
            final var playerData = SBA.getInstance().getPlayerWrapperService().get(player).orElse(null);
            if (playerData == null) {
                Logger.error("PlayerData is null for " + player.getName());
                return;
            }

            if (SBAConfig.getInstance().node("main-lobby", "tablist-modifications").getBoolean()) {
                try {
                    var header = LanguageService.getInstance().get(MessageKeys.MAIN_LOBBY_TABLIST_HEADER)
                            .replace("%sba_version%", SBA.getInstance().getVersion()).toComponent();

                    var footer = LanguageService.getInstance().get(MessageKeys.MAIN_LOBBY_TABLIST_FOOTER)
                            .replace("%sba_version%", SBA.getInstance().getVersion()).toComponent();
                    playerData.sendPlayerListHeaderFooter(header, footer);
                } catch (Exception e) {
                    Logger.error("Failed to set tablist: " + e.getMessage());
                }
            }

            String scoreboardTitle = LanguageService.getInstance()
                    .get(MessageKeys.MAIN_LOBBY_SCOREBOARD_TITLE)
                    .toString();
            
            List<String> animatedTitle = null;
            try {
                animatedTitle = LanguageService.getInstance()
                        .get(MessageKeys.MAIN_LOBBY_SCOREBOARD_ANIMATED_TITLE)
                        .toStringList();
            } catch (Exception e) {
                Logger.error("Failed to load animated title: " + e.getMessage());
            }

            if (animatedTitle == null || animatedTitle.isEmpty()) {
                animatedTitle = new ArrayList<>();
                animatedTitle.add(scoreboardTitle);
            }

            List<String> scoreboardLines = getScoreboardLines(player);

            final var scoreboard = Scoreboard.builder()
                    .animate(true)
                    .player(player)
                    .title(scoreboardTitle)
                    .displayObjective(MAIN_LOBBY_OBJECTIVE)
                    .updateInterval(20L)
                    .lines(scoreboardLines)
                    .placeholderHook(hook -> {
                        try {
                            var levelManager = PlayerLevelManager.getInstance();
                            int playerLevel = levelManager.getPlayerLevel(player);
                            String playerPrefix = levelManager.getPlayerPrefix(player);
                            int playerXP = levelManager.getPlayerXP(player);
                            int xpToNext = levelManager.getXPToNextLevel(player);
                            double progress = levelManager.getLevelProgress(player);

                            String barLegacy = createLegacyBar(progress);
                            String barModern = createModernBar(progress);

                            final var playerStatistic = Main.getPlayerStatisticsManager().getStatistic(player);
                            if (playerStatistic == null) {
                                return hook.getLine()
                                        .replace("%kills%", "0")
                                        .replace("%beds%", "0")
                                        .replace("%wins%", "0")
                                        .replace("%losses%", "0")
                                        .replace("%games%", "0")
                                        .replace("%winrate%", "0%")
                                        .replace("%kdr%", "0.0")
                                        .replace("%level%", playerPrefix + " " + playerLevel + "✫")
                                        .replace("%sba_player_level_prefix%", playerPrefix)
                                        .replace("%sba_player_level_number%", String.valueOf(playerLevel))
                                        .replace("%sba_player_xp%", formatNumber(playerXP))
                                        .replace("%sba_player_level_required%", formatNumber(xpToNext))
                                        .replace("%xp%", formatNumber(playerXP))
                                        .replace("%req%", formatNumber(xpToNext))
                                        .replace("%progress%", String.valueOf((int) (progress * 100)) + "%")
                                        .replace("%bar%", barModern)
                                        .replace("%bar_legacy%", barLegacy)
                                        .replace("%rank%", "§6[VIP]");
                            }

                            int totalGames = playerStatistic.getWins() + playerStatistic.getDeaths();
                            int losses = totalGames - playerStatistic.getWins();
                            double winRate = totalGames > 0 ? (double) playerStatistic.getWins() / totalGames * 100 : 0;

                            String formattedXP = formatNumber(playerXP);
                            String formattedRequired = formatNumber(xpToNext);
                            String formattedKills = formatNumber(playerStatistic.getKills());
                            String formattedWins = formatNumber(playerStatistic.getWins());
                            String formattedBeds = formatNumber(playerStatistic.getDestroyedBeds());
                            String formattedLosses = formatNumber(losses);
                            String formattedGames = formatNumber(totalGames);
                            String formattedKDR = String.format("%.2f", playerStatistic.getKD());

                            return hook.getLine()
                                    .replace("%sba_version%", SBA.getInstance().getVersion())
                                    .replace("%kills%", formattedKills)
                                    .replace("%beds%", formattedBeds)
                                    .replace("%deaths%", String.valueOf(playerStatistic.getDeaths()))
                                    .replace("%level%", playerPrefix + " " + playerLevel + "✫")
                                    .replace("%sba_player_level_prefix%", playerPrefix)
                                    .replace("%sba_player_level_number%", String.valueOf(playerLevel))
                                    .replace("%sba_player_xp%", formattedXP)
                                    .replace("%sba_player_level_required%", formattedRequired)
                                    .replace("%xp%", formattedXP)
                                    .replace("%req%", formattedRequired)
                                    .replace("%xp_required%", formattedRequired)
                                    .replace("%progress%", String.valueOf((int) (progress * 100)) + "%")
                                    .replace("%bar%", barModern)
                                    .replace("%bar_legacy%", barLegacy)
                                    .replace("%wins%", formattedWins)
                                    .replace("%losses%", formattedLosses)
                                    .replace("%games%", formattedGames)
                                    .replace("%winrate%", String.format("%.1f", winRate) + "%")
                                    .replace("%kdr%", formattedKDR)
                                    .replace("%rank%", "§6[VIP]")
                                    .replace("%donate%", "§6[VIP]");
                        } catch (Exception e) {
                            Logger.error("Error in scoreboard placeholder for " + player.getName() + ": " + e.getMessage());
                            return hook.getLine();
                        }
                    })
                    .build();

            if (animatedTitle != null && animatedTitle.size() > 1) {
                try {
                    scoreboard.setAnimatedTitle(animatedTitle);
                } catch (Exception e) {
                    Logger.error("Failed to set animated title: " + e.getMessage());
                }
            }

            scoreboardMap.put(player, scoreboard);
            ScoreboardManager.getInstance().addToCache(scoreboard);
            startAutoUpdate(player);
            
            Logger.trace("Scoreboard created for player " + player.getName());
            
        } catch (Exception e) {
            Logger.error("Failed to create scoreboard for player " + player.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onBedWarsPlayerJoin(BedwarsPlayerJoinedEvent e) {
        Player player = e.getPlayer();
        Scoreboard board = scoreboardMap.get(player);
        if (board != null) {
            try {
                board.setVisibility(false);
            } catch (Exception ex) {
                // Игнорируем
            }
        }
    }

    @EventHandler
    public void onBedWarsPlayerLeaveEvent(BedwarsPlayerLeaveEvent e) {
        final var player = e.getPlayer();
        if (!enabled) return;
        if (player == null) return;
            
        // Уменьшаем задержку с 40 до 5 тиков
        Bukkit.getScheduler().runTaskLater(SBA.getPluginInstance(), () -> {
            try {
                if (player != null && player.isOnline() && 
                    isInWorld(player.getLocation()) && !Main.isPlayerInGame(player)) {
                    
                    destroyPlayerBoard(player);
                    create(player);
                    
                    Logger.trace("Scoreboard recreated for player " + player.getName() + " after game");
                }
            } catch (Exception ex) {
                Logger.error("Error recreating scoreboard for " + player.getName() + ": " + ex.getMessage());
            }
        }, 5L); // Было 40L, стало 5L
    }
}
