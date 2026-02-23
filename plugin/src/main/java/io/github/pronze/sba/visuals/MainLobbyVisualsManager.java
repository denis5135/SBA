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

import java.util.*;

@Service
public class MainLobbyVisualsManager implements Listener {
    private final static String MAIN_LOBBY_OBJECTIVE = "sbascoreboard";
    private static Location location;
    private final Map<Player, Scoreboard> scoreboardMap = new HashMap<>();
    private boolean enabled;

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
        return player.getScoreboard().getObjective(MAIN_LOBBY_OBJECTIVE) != null;
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
        }
    }

    @OnPreDisable
    public void disable() {
        Set.copyOf(scoreboardMap.keySet()).forEach(this::remove);
        scoreboardMap.clear();
        enabled = false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (!enabled)
            return;

        final var player = e.getPlayer();

        Bukkit.getServer().getScheduler().runTaskLater(SBA.getPluginInstance(), () -> {
            if (hasMainLobbyObjective(player))
                return;
            if (isInWorld(player.getLocation()) && !Main.isPlayerInGame(player) && player.isOnline()) {
                create(player);
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        if (!enabled)
            return;

        final var player = e.getPlayer();
        if (player.isOnline() && isInWorld(player.getLocation()) && !scoreboardMap.containsKey(player)) {
            create(player);
        } else {
            remove(player);
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent e) {
        remove(e.getPlayer());
    }

    public void create(Player player) {
        if (!enabled)
            return;
        if (!isInWorld(player.getLocation()))
            return;

        final var playerData = SBA.getInstance().getPlayerWrapperService().get(player).orElseThrow();

        if (SBAConfig.getInstance().node("main-lobby", "tablist-modifications").getBoolean()) {
            var header = LanguageService.getInstance().get(MessageKeys.MAIN_LOBBY_TABLIST_HEADER)
                    .replace("%sba_version%", SBA.getInstance().getVersion()).toComponent();

            var footer = LanguageService.getInstance().get(MessageKeys.MAIN_LOBBY_TABLIST_FOOTER)
                    .replace("%sba_version%", SBA.getInstance().getVersion()).toComponent();
            playerData.sendPlayerListHeaderFooter(header, footer);
        }

        // Получаем заголовок, анимированный заголовок и строки из language.yml
        String scoreboardTitle = LanguageService.getInstance()
                .get(MessageKeys.MAIN_LOBBY_SCOREBOARD_TITLE)
                .toString();
        
        List<String> animatedTitle = LanguageService.getInstance()
                .get(MessageKeys.MAIN_LOBBY_SCOREBOARD_ANIMATED_TITLE)
                .toStringList();
        
        List<String> scoreboardLines = LanguageService.getInstance()
                .get(MessageKeys.MAIN_LOBBY_SCOREBOARD_LINES)
                .toStringList();

        // Если анимированный заголовок пустой или只有一个 элемент, используем обычный
        if (animatedTitle == null || animatedTitle.isEmpty()) {
            animatedTitle = new ArrayList<>();
            animatedTitle.add(scoreboardTitle);
        }

        final var scoreboard = Scoreboard.builder()
                .animate(true)
                .player(player)
                .title(scoreboardTitle) // Используем заголовок из language.yml
                .displayObjective(MAIN_LOBBY_OBJECTIVE)
                .updateInterval(20L)
                .lines(scoreboardLines) // Используем строки из language.yml
                .placeholderHook(hook -> {
                    // Используем нашу новую систему уровней
                    var levelManager = PlayerLevelManager.getInstance();
                    int playerLevel = levelManager.getPlayerLevel(player);
                    String playerPrefix = levelManager.getPlayerPrefix(player);
                    int playerXP = levelManager.getPlayerXP(player);
                    int xpToNext = levelManager.getXPToNextLevel(player);
                    double progress = levelManager.getLevelProgress(player);

                    // Создаём прогресс-бар (10 символов)
                    int barLength = 10;
                    int filledBars = (int) Math.round(progress * barLength);
                    StringBuilder bar = new StringBuilder("§a");
                    for (int i = 0; i < barLength; i++) {
                        if (i < filledBars) {
                            bar.append("■");
                        } else {
                            bar.append("§7■");
                        }
                    }

                    final var playerStatistic = Main.getPlayerStatisticsManager().getStatistic(player);

                    // Поражения = всего игр - победы
                    int totalGames = playerStatistic.getWins() + playerStatistic.getDeaths();
                    int losses = totalGames - playerStatistic.getWins();
                    double winRate = totalGames > 0 ? (double) playerStatistic.getWins() / totalGames * 100 : 0;

                    return hook.getLine()
                            .replace("%sba_version%", SBA.getInstance().getVersion())
                            .replace("%kills%", String.valueOf(playerStatistic.getKills()))
                            .replace("%beds%", String.valueOf(playerStatistic.getDestroyedBeds()))
                            .replace("%deaths%", String.valueOf(playerStatistic.getDeaths()))
                            // Уровни
                            .replace("%level%", playerPrefix + " " + playerLevel + "✫")
                            .replace("%sba_player_level_prefix%", playerPrefix)
                            .replace("%sba_player_level_number%", String.valueOf(playerLevel))
                            .replace("%sba_player_xp%", String.valueOf(playerXP))
                            .replace("%sba_player_level_required%", String.valueOf(xpToNext))
                            .replace("%xp%", String.valueOf(playerXP))
                            .replace("%xp_required%", String.valueOf(xpToNext))
                            .replace("%progress%", String.valueOf((int) (progress * 100)) + "%")
                            .replace("%bar%", bar.toString())
                            // Победы/поражения
                            .replace("%wins%", String.valueOf(playerStatistic.getWins()))
                            .replace("%losses%", String.valueOf(losses))
                            .replace("%games%", String.valueOf(totalGames))
                            .replace("%winrate%", String.format("%.1f", winRate) + "%")
                            // K/D
                            .replace("%kdr%", String.valueOf(playerStatistic.getKD()))
                            // Привилегия (заглушка)
                            .replace("%rank%", "&6[VIP]")
                            .replace("%donate%", "&6[VIP]");
                })
                .build();

        // Устанавливаем анимированный заголовок из language.yml
        if (animatedTitle.size() > 1) {
            scoreboard.setAnimatedTitle(animatedTitle);
        }

        scoreboardMap.put(player, scoreboard);
    }

    public void remove(Player player) {
        if (player == null)
            return;
        final var scoreboard = scoreboardMap.get(player);
        if (scoreboard != null) {
            scoreboard.destroy();
            scoreboardMap.remove(player);
        }
        if (hasMainLobbyObjective(player)) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        if (SBAConfig.getInstance().node("main-lobby", "tablist-modifications").getBoolean()) {
            Players.wrapPlayer(player).sendPlayerListHeaderFooter(Component.empty(), Component.empty());
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
    }
}
