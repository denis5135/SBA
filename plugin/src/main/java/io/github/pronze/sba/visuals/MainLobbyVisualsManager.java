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
import io.github.pronze.sba.levels.PlayerLevelManager;
import io.github.pronze.sba.utils.Logger;

import java.util.*;

@Service
public class MainLobbyVisualsManager implements Listener {
    private final static String MAIN_LOBBY_OBJECTIVE = "sbascoreboard";
    private static Location location;
    private final Map<Player, Scoreboard> scoreboardMap = new HashMap<>();
    private final Map<Player, BukkitTask> updateTasks = new HashMap<>();
    private boolean enabled;
    private boolean debugMode = false; // Включи в true только для отладки

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
            Bukkit.getScheduler().runTaskLater(SBA.getPluginInstance(),
                    () -> Bukkit.getOnlinePlayers().forEach(this::create), 3L);
        }, () -> {
            disable();
            Bukkit.getServer().getLogger().warning("Could not find lobby world!");
        });
    }

    public static boolean isInWorld(Location loc) {
        try {
            return loc.getWorld().equals(location.getWorld());
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasMainLobbyObjective(Player player) {
        try {
            return player.getScoreboard().getObjective(MAIN_LOBBY_OBJECTIVE) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!enabled)
            return;
        if (!SBAConfig.getInstance().node("main-lobby", "custom-chat").getBoolean(true))
            return;

        final var player = e.getPlayer();

        if (SBAConfig.getInstance().node("main-lobby", "enabled").getBoolean(false)
                && MainLobbyVisualsManager.isInWorld(e.getPlayer().getLocation())) {
            if (Main.isPlayerInGame(player))
                return;
            
            try {
                // Используем формат чата из language.yml
                var chatFormat = LanguageService.getInstance()
                        .get(MessageKeys.MAIN_LOBBY_CHAT_FORMAT)
                        .toString();

                if (chatFormat != null) {
                    // Получаем уровень из новой системы
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
            // Отменяем все задачи обновления
            for (BukkitTask task : updateTasks.values()) {
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
            }
            updateTasks.clear();
            
            // Безопасно удаляем все скорборды
            new ArrayList<>(scoreboardMap.keySet()).forEach(this::remove);
            scoreboardMap.clear();
        } catch (Exception e) {
            Logger.error("Error disabling MainLobbyVisualsManager: " + e.getMessage());
        }
        enabled = false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (!enabled)
            return;

        final var player = e.getPlayer();

        Bukkit.getServer().getScheduler().runTaskLater(SBA.getPluginInstance(), () -> {
            try {
                if (hasMainLobbyObjective(player))
                    return;
                if (isInWorld(player.getLocation()) && !Main.isPlayerInGame(player) && player.isOnline()) {
                    create(player);
                }
            } catch (Exception ex) {
                Logger.error("Error in player join: " + ex.getMessage());
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (!enabled)
            return;

        final var player = e.getPlayer();
        try {
            if (player.isOnline() && isInWorld(player.getLocation()) && !scoreboardMap.containsKey(player)) {
                create(player);
            } else {
                remove(player);
            }
        } catch (Exception ex) {
            Logger.error("Error in world change: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent e) {
        remove(e.getPlayer());
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
            
            // Отладка только если включен режим
            if (debugMode) {
                Logger.info("Player version detected: " + version + " | Legacy: " + legacy);
            }
            
            return legacy;
        } catch (Exception e) {
            if (debugMode) {
                Logger.error("Error detecting version: " + e.getMessage());
            }
            return false;
        }
    }

    // Создаёт бар для старых версий (8 квадратов)
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

    // Создаёт бар для новых версий (12 квадратов)
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
        // Для всех версий используем один языковой файл
        try {
            return LanguageService.getInstance()
                    .get(MessageKeys.MAIN_LOBBY_SCOREBOARD_LINES)
                    .toStringList();
        } catch (Exception e) {
            Logger.error("Failed to load scoreboard lines from language file: " + e.getMessage());
            // Возвращаем дефолтные строки
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
        
        // Отменяем старую задачу если есть
        BukkitTask oldTask = updateTasks.remove(player);
        if (oldTask != null) {
            try {
                if (!oldTask.isCancelled()) {
                    oldTask.cancel();
                }
            } catch (Exception e) {
                // Игнорируем ошибки при отмене
            }
        }

        // Создаём новую задачу обновления
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(SBA.getPluginInstance(), () -> {
            try {
                // Проверяем, что игрок всё ещё валиден
                if (player == null || !player.isOnline()) {
                    // Игрок вышел - удаляем задачу
                    BukkitTask t = updateTasks.remove(player);
                    if (t != null && !t.isCancelled()) {
                        t.cancel();
                    }
                    return;
                }
                
                if (!isInWorld(player.getLocation()) || Main.isPlayerInGame(player)) {
                    return; // Игрок не в лобби - пропускаем обновление
                }
                
                Scoreboard board = scoreboardMap.get(player);
                if (board != null) {
                    try {
                        board.refresh(); // Обновляем скорборд
                    } catch (Exception e) {
                        // Ошибка при обновлении - удаляем скорборд
                        Logger.error("Error refreshing scoreboard for " + player.getName() + ": " + e.getMessage());
                        remove(player);
                    }
                } else {
                    // Если скорборд пропал, создаём заново
                    create(player);
                }
            } catch (Exception e) {
                Logger.error("Error in auto-update for " + (player != null ? player.getName() : "null") + ": " + e.getMessage());
            }
        }, 100L, 100L); // Обновление каждые 5 секунд (100 тиков)

        updateTasks.put(player, task);
    }

    public void create(Player player) {
        if (!enabled)
            return;
        if (player == null || !player.isOnline())
            return;
        if (!isInWorld(player.getLocation()))
            return;
        
        // Удаляем старый скорборд если есть
        remove(player);
        
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

            // Получаем заголовок, анимированный заголовок и строки из language.yml
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

            // Если анимированный заголовок пустой, используем обычный
            if (animatedTitle == null || animatedTitle.isEmpty()) {
                animatedTitle = new ArrayList<>();
                animatedTitle.add(scoreboardTitle);
            }

            // Получаем строки для текущей версии
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
                            // Используем нашу новую систему уровней
                            var levelManager = PlayerLevelManager.getInstance();
                            int playerLevel = levelManager.getPlayerLevel(player);
                            String playerPrefix = levelManager.getPlayerPrefix(player);
                            int playerXP = levelManager.getPlayerXP(player);
                            int xpToNext = levelManager.getXPToNextLevel(player);
                            double progress = levelManager.getLevelProgress(player);

                            // Создаём два варианта бара
                            String barLegacy = createLegacyBar(progress); // 8 квадратов
                            String barModern = createModernBar(progress); // 12 квадратов

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

                            // Поражения = всего игр - победы
                            int totalGames = playerStatistic.getWins() + playerStatistic.getDeaths();
                            int losses = totalGames - playerStatistic.getWins();
                            double winRate = totalGames > 0 ? (double) playerStatistic.getWins() / totalGames * 100 : 0;

                            // Форматируем числа
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
                                    // Уровни
                                    .replace("%level%", playerPrefix + " " + playerLevel + "✫")
                                    .replace("%sba_player_level_prefix%", playerPrefix)
                                    .replace("%sba_player_level_number%", String.valueOf(playerLevel))
                                    .replace("%sba_player_xp%", formattedXP)
                                    .replace("%sba_player_level_required%", formattedRequired)
                                    .replace("%xp%", formattedXP)
                                    .replace("%req%", formattedRequired)
                                    .replace("%xp_required%", formattedRequired)
                                    .replace("%progress%", String.valueOf((int) (progress * 100)) + "%")
                                    .replace("%bar%", barModern)          // стандартный бар (12 квадратов)
                                    .replace("%bar_legacy%", barLegacy)   // бар для старых версий (8 квадратов)
                                    // Победы/поражения
                                    .replace("%wins%", formattedWins)
                                    .replace("%losses%", formattedLosses)
                                    .replace("%games%", formattedGames)
                                    .replace("%winrate%", String.format("%.1f", winRate) + "%")
                                    // K/D
                                    .replace("%kdr%", formattedKDR)
                                    // Привилегия
                                    .replace("%rank%", "§6[VIP]")
                                    .replace("%donate%", "§6[VIP]");
                        } catch (Exception e) {
                            Logger.error("Error in scoreboard placeholder for " + player.getName() + ": " + e.getMessage());
                            return hook.getLine();
                        }
                    })
                    .build();

            // Устанавливаем анимированный заголовок
            if (animatedTitle != null && animatedTitle.size() > 1) {
                try {
                    scoreboard.setAnimatedTitle(animatedTitle);
                } catch (Exception e) {
                    Logger.error("Failed to set animated title: " + e.getMessage());
                }
            }

            scoreboardMap.put(player, scoreboard);
            
            // Запускаем автообновление
            startAutoUpdate(player);
            
        } catch (Exception e) {
            Logger.error("Failed to create scoreboard for player " + player.getName() + ": " + e.getMessage());
        }
    }

    public void remove(Player player) {
        if (player == null)
            return;
            
        try {
            // Отменяем задачу обновления
            BukkitTask task = updateTasks.remove(player);
            if (task != null) {
                try {
                    if (!task.isCancelled()) {
                        task.cancel();
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки при отмене
                }
            }
            
            // Удаляем скорборд
            Scoreboard board = scoreboardMap.remove(player);
            if (board != null) {
                try {
                    board.destroy();
                } catch (Exception e) {
                    // Игнорируем - скорборд уже мог быть уничтожен
                }
            }
            
            // Сбрасываем скорборд игрока на основной
            try {
                if (player.isOnline()) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
            } catch (Exception e) {
                // Игнорируем
            }
            
        } catch (Exception e) {
            Logger.error("Error removing player " + player.getName() + " from scoreboard: " + e.getMessage());
        }
    }

    @EventHandler
    public void onBedWarsPlayerJoin(BedwarsPlayerJoinedEvent e) {
        final var player = e.getPlayer();
        remove(player);
    }

    @EventHandler
    public void onBedWarsPlayerLeaveEvent(BedwarsPlayerLeaveEvent e) {
        final var player = e.getPlayer();
        if (!enabled)
            return;
            
        // Сначала удаляем старый скорборд
        remove(player);
        
        // Потом создаём новый с задержкой
        Bukkit.getScheduler().runTaskLater(SBA.getPluginInstance(), () -> {
            try {
                if (player != null && player.isOnline() && isInWorld(player.getLocation()) && !Main.isPlayerInGame(player)) {
                    create(player);
                }
            } catch (Exception ex) {
                Logger.error("Error recreating scoreboard for " + (player != null ? player.getName() : "null") + ": " + ex.getMessage());
            }
        }, 20L);
    }
}
