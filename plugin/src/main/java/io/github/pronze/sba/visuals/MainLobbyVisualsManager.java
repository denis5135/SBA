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
        final var db = SBA.getInstance().getPlayerWrapperService().get(player).orElseThrow();

        if (SBAConfig.getInstance().node("main-lobby", "enabled").getBoolean(false)
                && MainLobbyVisualsManager.isInWorld(e.getPlayer().getLocation())) {
            if (Main.isPlayerInGame(player))
                return;
            var chatFormat = LanguageService.getInstance().get(MessageKeys.MAIN_LOBBY_CHAT_FORMAT).toString();

            if (chatFormat != null) {
                var format = chatFormat
                        .replace("%level%", String.valueOf(db.getLevel()))
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

        // Анимированный заголовок "Bedwars"
        List<String> animatedTitle = new ArrayList<>();
        animatedTitle.add("&6&lB&e&led&6&lw&e&lar&6&ls");
        animatedTitle.add("&e&lB&6&led&e&lw&6&lar&e&ls");
        animatedTitle.add("&6&lBe&e&ldw&6&lar&e&ls");
        animatedTitle.add("&e&lBed&6&lw&e&lar&e&ls");
        animatedTitle.add("&6&lBedw&e&lar&6&ls");
        animatedTitle.add("&e&lBedwa&6&lr&e&ls");
        animatedTitle.add("&6&lBedwar&e&ls");
        animatedTitle.add("&e&lBedwars");

        final var scoreboard = Scoreboard.builder()
                .animate(true)
                .player(player)
                .title(animatedTitle.get(0)) // Обычный заголовок
                .displayObjective(MAIN_LOBBY_OBJECTIVE)
                .updateInterval(20L)
                .lines(getScoreboardLines())
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
                    int totalGames = playerStatistic.getWins() + playerStatistic.getDeaths(); // или другое значение
                    int losses = totalGames - playerStatistic.getWins();
                    double winRate = totalGames > 0 ? (double) playerStatistic.getWins() / totalGames * 100 : 0;

                    return hook.getLine()
                            .replace("%sba_version%", SBA.getInstance().getVersion())
                            .replace("%kills%", String.valueOf(playerStatistic.getKills()))
                            .replace("%beds%", String.valueOf(playerStatistic.getDestroyedBeds()))
                            .replace("%deaths%", String.valueOf(playerStatistic.getDeaths()))
                            // Уровни
                            .replace("%level%", playerPrefix + " " + playerLevel + "✫")
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
                            // Привилегия (заглушка, можно заменить на реальную систему доната)
                            .replace("%rank%", "&6[VIP]")
                            .replace("%donate%", "&6[VIP]");
                })
                .build();

        // Устанавливаем анимированный заголовок отдельно
        scoreboard.setAnimatedTitle(animatedTitle);

        scoreboardMap.put(player, scoreboard);
    }

    private List<String> getScoreboardLines() {
        List<String> lines = new ArrayList<>();

        // Твоя привилегия
        lines.add("&6%rank%");
        // Пустая строка
        lines.add("");
        // Уровень
        lines.add("&7Уровень: %level%");
        // Опыт
        lines.add("&7Опыт: %xp%/%xp_required%");
        // Прогресс-бар
        lines.add("%bar%");
        // Пустая строка
        lines.add("");
        // Статистика
        lines.add("&7Всего убийств: &a%kills%");
        lines.add("&7Всего побед: &a%wins%");
        lines.add("&7Всего кроватей: &a%beds%");
        lines.add("&7У/С: &a%kdr%");
        lines.add("&7В/П: &a%wins%&7/&c%losses% &8(%winrate%)");
        lines.add("&7Всего игр: &a%games%");
        // Пустая строка
        lines.add("");
        // Айпи
        lines.add("&bplay.yourserver.com");

        return lines;
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
