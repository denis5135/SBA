package io.github.pronze.sba.listener;

import io.github.pronze.sba.MessageKeys;
import io.github.pronze.sba.Permissions;
import io.github.pronze.sba.SBA;
import io.github.pronze.sba.UpdateChecker;
import io.github.pronze.sba.config.SBAConfig;
import io.github.pronze.sba.data.DegradableItem;
import io.github.pronze.sba.game.ArenaManager;
import io.github.pronze.sba.lib.lang.LanguageService;
import io.github.pronze.sba.manager.ToolType;
import io.github.pronze.sba.manager.ToolUpgradeManager;
import io.github.pronze.sba.utils.Logger;
import io.github.pronze.sba.utils.SBAUtil;
import io.github.pronze.sba.utils.ShopUtil;
import io.github.pronze.sba.wrapper.SBAPlayerWrapper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.game.GamePlayer;
import org.screamingsandals.lib.Server;
import org.screamingsandals.lib.impl.bukkit.utils.Version;
import org.screamingsandals.lib.player.Players;
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.lib.utils.annotations.methods.OnPostEnable;
import org.screamingsandals.lib.utils.reflect.Reflect;

import io.github.pronze.lib.pronzelib.scoreboards.Scoreboard;
import io.github.pronze.lib.pronzelib.scoreboards.ScoreboardManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class PlayerListener implements Listener {
    private final List<Material> allowedDropItems = new ArrayList<>();
    private final List<Material> generatorDropItems = new ArrayList<>();

    @OnPostEnable
    public void registerListener() {
        if (SBA.isBroken())
            return;
        SBA.getInstance().registerListener(this);
        allowedDropItems.clear();
        generatorDropItems.clear();
        allowedDropItems.addAll(SBAUtil.parseMaterialFromConfig("allowed-item-drops"));
        generatorDropItems.addAll(SBAUtil.parseMaterialFromConfig("running-generator-drops"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        final var player = e.getEntity();

        // Проверяем, играет ли игрок в BedWars
        if (!Main.getInstance().isPlayerPlayingAnyGame(player)) {
            return;
        }

        final var game = Main.getInstance().getGameOfPlayer(player);
        if (game == null || game.getStatus() != GameStatus.RUNNING) {
            return;
        }

        final var arena = ArenaManager.getInstance().get(game.getName());
        if (!arena.isPresent()) {
            return;
        }

        final var itemArr = new ArrayList<ItemStack>();
        final var sword = Main.isLegacy() ? new ItemStack(Material.valueOf("WOOD_SWORD"))
                : new ItemStack(Material.WOODEN_SWORD);

        // Обрабатываем инвентарь с защитой от ошибок
        try {
            // Получаем содержимое инвентаря безопасно
            ItemStack[] contents;
            ItemStack[] armorContents;
            
            try {
                contents = player.getInventory().getContents();
                armorContents = player.getInventory().getArmorContents();
            } catch (Exception ex) {
                Logger.error("Failed to get inventory contents: " + ex.getMessage());
                contents = new ItemStack[0];
                armorContents = new ItemStack[0];
            }

            // Создаём поток для обработки предметов
            Stream<ItemStack> stream;
            if (Server.isVersion(1, 9)) {
                stream = Arrays.stream(contents);
            } else {
                stream = Stream.concat(
                        Arrays.stream(contents),
                        Arrays.stream(armorContents)
                );
            }
            
            // Обрабатываем каждый предмет с защитой от ошибок
            stream.filter(Objects::nonNull)
                    .forEach(stack -> {
                        try {
                            if (stack == null || stack.getType() == null || stack.getType() == Material.AIR) {
                                return;
                            }

                            final String name = stack.getType().name();
                            if (name == null || name.isEmpty()) {
                                itemArr.add(stack);
                                return;
                            }

                            var endStr = name.substring(name.contains("_") ? name.indexOf("_") + 1 : 0);
                            if (endStr == null || endStr.isEmpty()) {
                                itemArr.add(stack);
                                return;
                            }

                            switch (endStr) {
                                case "SWORD":
                                    itemArr.add(ShopUtil.downgradeItem(stack, DegradableItem.WEAPONARY));
                                    break;
                                    
                                case "PICKAXE":
                                case "AXE":
                                case "SHEARS":
                                    // Проверяем, включены ли улучшения инструментов
                                    if (SBAConfig.getInstance().isToolUpgradeEnabled()) {
                                        // Проверяем, можно ли улучшать этот инструмент
                                        if (ToolUpgradeManager.getInstance().canUpgrade(stack)) {
                                            // Если можно - понижаем уровень в данных
                                            ToolType type = ToolUpgradeManager.getInstance().getToolType(stack);
                                            if (type != null && SBAConfig.getInstance().isToolDowngradeOnDeathEnabled()) {
                                                ToolUpgradeManager.getInstance().downgradeTool(player, type);
                                            }
                                        }
                                    }
                                    // Сохраняем предмет как есть
                                    itemArr.add(stack);
                                    break;
                                    
                                case "LEGGINGS":
                                case "BOOTS":
                                case "CHESTPLATE":
                                case "HELMET":
                                    itemArr.add(ShopUtil.downgradeItem(stack, DegradableItem.ARMOR));
                                    break;
                                    
                                default:
                                    itemArr.add(stack);
                                    break;
                            }
                        } catch (Exception ex) {
                            // Игнорируем ошибки при обработке отдельных предметов
                            Logger.trace("Error processing item on death: " + ex.getMessage());
                            // Всё равно добавляем предмет, чтобы не потерять его
                            try {
                                itemArr.add(stack);
                            } catch (Exception ignored) {}
                        }
                    });
        } catch (Exception ex) {
            Logger.error("Error processing player inventory on death: " + ex.getMessage());
            // В случае критической ошибки, пытаемся сохранить хотя бы меч
            itemArr.add(sword);
        }

        // Добавляем меч, если его ещё нет
        if (!itemArr.contains(sword)) {
            itemArr.add(sword);
        }

        // Сохраняем инвентарь в данные арены
        try {
            arena.get().getPlayerData(player.getUniqueId()).ifPresent(playerData -> {
                try {
                    playerData.setInventory(itemArr);
                } catch (Exception ex) {
                    Logger.error("Failed to save inventory to player data: " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            Logger.error("Failed to get player data: " + ex.getMessage());
        }

        // Понижаем все инструменты в данных (даже если не сохраняем в инвентарь)
        if (SBAConfig.getInstance().isToolUpgradeEnabled() && 
            SBAConfig.getInstance().isToolDowngradeOnDeathEnabled()) {
            try {
                ToolUpgradeManager.getInstance().downgradeAllTools(player);
            } catch (Exception ex) {
                Logger.error("Error downgrading tools on death: " + ex.getMessage());
            }
        }

        // Даём ресурсы убийце
        if (SBAConfig.getInstance().getBoolean("give-killer-resources", true)) {
            final var killer = e.getEntity().getKiller();

            if (killer != null && Main.getInstance().isPlayerPlayingAnyGame(killer)
                    && killer.getGameMode() == GameMode.SURVIVAL) {
                try {
                    ItemStack[] killerContents;
                    try {
                        killerContents = player.getInventory().getContents();
                    } catch (Exception ex) {
                        killerContents = new ItemStack[0];
                    }
                    
                    Arrays.stream(killerContents)
                            .filter(Objects::nonNull)
                            .forEach(drop -> {
                                try {
                                    if (generatorDropItems.contains(drop.getType())) {
                                        killer.sendMessage("+" + drop.getAmount() + " "
                                                + drop.getType().name().toLowerCase().replace("_", " "));
                                        killer.getInventory().addItem(drop.clone());
                                    }
                                } catch (Exception ex) {
                                    Logger.trace("Error giving resource to killer: " + ex.getMessage());
                                }
                            });
                } catch (Exception ex) {
                    Logger.trace("Error processing killer resources: " + ex.getMessage());
                }
            }
        }

        final var gVictim = Main.getPlayerGameProfile(player);
        final var victimTeam = game.getTeamOfPlayer(player);

        if (victimTeam == null)
            return;
            
        if (SBAConfig.getInstance().getBoolean("respawn-cooldown.enabled", true) &&
                victimTeam.isAlive() && game.isPlayerInAnyTeam(player) &&
                game.getTeamOfPlayer(player).isTargetBlockExists()) {

            new BukkitRunnable() {
                final GamePlayer gamePlayer = gVictim;
                final Player player = gamePlayer.player;

                final SBAPlayerWrapper wrappedPlayer = Players.wrapPlayer(player).as(SBAPlayerWrapper.class);
                int livingTime = Main.getInstance().getConfig().getInt("respawn-cooldown.time", 5);
                byte buffer = 2;

                @Override
                public void run() {
                    try {
                        if (!Main.isPlayerInGame(player)) {
                            this.cancel();
                            return;
                        }
                        final org.screamingsandals.lib.spectator.Component respawnTitle = LanguageService
                                .getInstance()
                                .get(MessageKeys.RESPAWN_COUNTDOWN_TITLE)
                                .replace("%time%", String.valueOf(livingTime))
                                .toComponent();
                        final org.screamingsandals.lib.spectator.Component respawnSubtitle = LanguageService
                                .getInstance()
                                .get(MessageKeys.RESPAWN_COUNTDOWN_SUBTITLE)
                                .replace("%time%", String.valueOf(livingTime))
                                .toComponent();
                        
                        if (livingTime > 0) {
                            SBAUtil.sendTitle(wrappedPlayer, respawnTitle,
                                    respawnSubtitle,
                                    0, 20, 0);

                            LanguageService
                                    .getInstance()
                                    .get(MessageKeys.RESPAWN_COUNTDOWN_MESSAGE)
                                    .replace("%time%", String.valueOf(livingTime))
                                    .send(wrappedPlayer);
                            livingTime--;
                        }

                        if (livingTime <= 0) {
                            if (gVictim.isSpectator && buffer > 0) {
                                buffer--;
                            } else {
                                LanguageService
                                        .getInstance()
                                        .get(MessageKeys.RESPAWNED_MESSAGE)
                                        .send(wrappedPlayer);

                                var respawnedTitle = LanguageService
                                        .getInstance()
                                        .get(MessageKeys.RESPAWNED_TITLE)
                                        .toComponent();

                                SBAUtil.sendTitle(wrappedPlayer, respawnedTitle,
                                        org.screamingsandals.lib.spectator.Component.empty(),
                                        5, 40, 5);
                                
                                try {
                                    ShopUtil.giveItemToPlayer(itemArr, player,
                                            Main.getInstance().getGameByName(game.getName()).getTeamOfPlayer(player)
                                                    .getColor());
                                    ShopUtil.applyTeamUpgrades(player, game);
                                } catch (Exception ex) {
                                    Logger.error("Error giving items on respawn: " + ex.getMessage());
                                }
                                this.cancel();
                            }
                        }
                    } catch (Exception ex) {
                        Logger.error("Error in respawn countdown: " + ex.getMessage());
                        this.cancel();
                    }
                }
            }.runTaskTimer(SBA.getPluginInstance(), 0L, 20L);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null)
            return;

        if (!(event.getWhoClicked() instanceof Player))
            return;

        final var player = (Player) event.getWhoClicked();

        if (!Main.isPlayerInGame(player))
            return;

        if (SBAConfig.getInstance().getBoolean("disable-armor-inventory-movement", true) &&
                event.getSlotType() == SlotType.ARMOR)
            event.setCancelled(true);

        final var topSlot = event.getView().getTopInventory();
        final var bottomSlot = event.getView().getBottomInventory();
        final var clickedInventory = event.getClickedInventory();
        final var typeName = event.getCurrentItem().getType().name();

        if (clickedInventory == null)
            return;

        if (clickedInventory.equals(bottomSlot)
                && SBAConfig.getInstance().getBoolean("block-players-putting-certain-items-onto-chest", true)
                && (topSlot.getType() == InventoryType.CHEST || topSlot.getType() == InventoryType.ENDER_CHEST)
                && bottomSlot.getType() == InventoryType.PLAYER) {
            if (typeName.endsWith("AXE") || typeName.endsWith("SWORD") || typeName.endsWith("PICKAXE")) {
                event.setResult(Event.Result.DENY);
                LanguageService
                        .getInstance()
                        .get(MessageKeys.CANNOT_PUT_ITEM_IN_CHEST)
                        .send(Players.wrapPlayer(player));
            }
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent evt) {
        final var player = evt.getPlayer();

        if (!Main.isPlayerInGame(player))
            return;
        if (!SBAConfig.getInstance().getBoolean("block-item-drops", true))
            return;

        final var ItemDrop = evt.getItemDrop().getItemStack();
        final var type = ItemDrop.getType();

        if (!allowedDropItems.contains(type) && !type.name().endsWith("WOOL")) {
            evt.setCancelled(true);
            player.getInventory().remove(ItemDrop);
        }
    }

    @EventHandler
    public void itemDamage(PlayerItemDamageEvent event) {
        if (!Main.isPlayerInGame(event.getPlayer())) {
            return;
        }

        if (SBAConfig.getInstance().node("disable-item-damage").getBoolean(true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent e) {
        final var player = e.getPlayer();
        final var uuid = player.getUniqueId();
        
        try {
            ScoreboardManager
                    .getInstance()
                    .fromCache(uuid)
                    .ifPresent(Scoreboard::destroy);
        } catch (Exception ex) {
            Logger.trace("Error destroying scoreboard: " + ex.getMessage());
        }

        final var wrappedPlayer = Players.wrapPlayer(player)
                .as(SBAPlayerWrapper.class);
        
        try {
            SBA.getInstance()
                    .getPartyManager()
                    .getPartyOf(wrappedPlayer)
                    .ifPresent(party -> {
                        try {
                            party.removePlayer(wrappedPlayer);
                            if (party.getMembers().size() == 1) {
                                SBA.getInstance()
                                        .getPartyManager()
                                        .disband(party.getUUID());
                                return;
                            }
                            if (party.getPartyLeader().equals(wrappedPlayer)) {
                                party
                                        .getMembers()
                                        .stream()
                                        .findAny()
                                        .ifPresentOrElse(member -> {
                                            party.setPartyLeader(member);
                                            LanguageService
                                                    .getInstance()
                                                    .get(MessageKeys.PARTY_MESSAGE_PROMOTED_LEADER)
                                                    .replace("%player%",
                                                            member.as(Player.class).getDisplayName() + ChatColor.RESET)
                                                    .send(party.getMembers().toArray(new SBAPlayerWrapper[0]));

                                        }, () -> SBA.getInstance().getPartyManager()
                                                .disband(party.getUUID()));
                            }
                            LanguageService
                                    .getInstance()
                                    .get(MessageKeys.PARTY_MESSAGE_OFFLINE_LEFT)
                                    .replace("%player%", player.getDisplayName() + ChatColor.RESET)
                                    .send(party.getMembers().stream().filter(member -> !wrappedPlayer.equals(member))
                                            .toArray(SBAPlayerWrapper[]::new));
                        } catch (Exception ex) {
                            Logger.error("Error processing party leave: " + ex.getMessage());
                        }
                    });
        } catch (Exception ex) {
            Logger.trace("Error getting party: " + ex.getMessage());
        }
        
        // Очищаем данные инструментов при выходе
        try {
            ToolUpgradeManager.getInstance().removePlayerData(uuid);
        } catch (Exception ex) {
            Logger.trace("Error removing tool data: " + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterPlayerLeave(PlayerQuitEvent event) {
        try {
            SBA.getInstance().getPlayerWrapperService().unregister(event.getPlayer());
        } catch (Exception ex) {
            Logger.trace("Error unregistering player: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        final var entity = event.getEntity();
        if (entity instanceof Player) {
            final var player = (Player) entity;

            if (Main.isPlayerInGame(player)) {
                try {
                    final var game = Main.getInstance().getGameOfPlayer(player);
                    ArenaManager
                            .getInstance()
                            .get(game.getName())
                            .ifPresent(arena -> arena.removeHiddenPlayer(player));
                } catch (Exception ex) {
                    Logger.trace("Error removing hidden player: " + ex.getMessage());
                }

                if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                    event.setDamage(SBAConfig.getInstance().node("explosion-damage").getDouble(1.0D));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        final var item = event.getItem();
        final var player = event.getPlayer();

        if (!Main.isPlayerInGame(player))
            return;

        if (item.getType() == Material.POTION) {
            try {
                final var potionMeta = (PotionMeta) item.getItemMeta();
                boolean isInvis = false;
                if (Version.isVersion(1, 20, 5)) {
                    var result = (NamespacedKey) Reflect.fastInvokeResulted(potionMeta, "getBasePotionType").fastInvoke("getKey");
                    if (result != null && ("invisibility".equals(result.getKey()) || "long_invisibility".equals(result.getKey()))) {
                        isInvis = true;
                    }
                } else if (Version.isVersion(1, 9)) {
                    if (potionMeta.getBasePotionData().getType() == PotionType.INVISIBILITY) {
                        isInvis = true;
                    }
                } else {
                    if (org.bukkit.potion.Potion.fromItemStack(item).getType() == PotionType.INVISIBILITY) {
                        isInvis = true;
                    }
                }

                if (!isInvis && potionMeta.hasCustomEffects()) {
                    isInvis = potionMeta
                            .getCustomEffects()
                            .stream()
                            .anyMatch(potionEffect -> potionEffect.getType().getName()
                                    .equalsIgnoreCase(PotionEffectType.INVISIBILITY.getName()));
                }

                if (isInvis) {
                    final var playerGame = Main.getInstance().getGameOfPlayer(player);
                    ArenaManager
                            .getInstance()
                            .get(playerGame.getName())
                            .ifPresent(arena -> arena.addHiddenPlayer(player));
                }

                try {
                    Reflect.setField(event.getClass(), "replacement", event, new ItemStack(Material.AIR));
                } catch (Throwable t) {
                    // Игнорируем
                }
            } catch (Exception ex) {
                Logger.trace("Error processing potion: " + ex.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEquipt(PlayerItemHeldEvent event) {
        final var player = event.getPlayer();

        if (!Main.isPlayerInGame(player))
            return;

        try {
            final var playerGame = Main.getInstance().getGameOfPlayer(player);
            ArenaManager
                    .getInstance()
                    .get(playerGame.getName())
                    .ifPresent(arena -> {
                        if (arena.isPlayerHidden(player)) {
                            arena.updateHiddenPlayer(player);
                        }
                    });
        } catch (Exception ex) {
            Logger.trace("Error updating hidden player: " + ex.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractPlace(PlayerInteractEvent event) {
        if (!Main.isPlayerInGame(event.getPlayer()))
            return;
        if (event.isCancelled())
            return;
        if (!event.isBlockInHand())
            return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (!event.hasBlock())
            return;

        try {
            BlockFace face = event.getBlockFace();
            Location loc = event.getClickedBlock().getRelative(face).getLocation();
            Collection<Entity> players = loc.getNearbyEntitiesByType(Player.class, 1.5, 1.5, 1.5, null);
            for (Entity playerEntity : players) {
                Player player = (Player) playerEntity;
                if (player.getGameMode() != GameMode.SURVIVAL) {
                    player.teleport(player.getLocation().add(0, 1.5, 0), TeleportCause.SPECTATE);
                }
            }
        } catch (Throwable t) {
            //Does not work on spigot
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractPlaceOnEntity(PlayerInteractEntityEvent event) {
        if (!Main.isPlayerInGame(event.getPlayer()))
            return;
        if (event.isCancelled())
            return;
            
        Player player = event.getPlayer();
        if (player.getItemInHand() == null || !player.getItemInHand().getType().isBlock()) {
            return;
        }
        
        if (event.getRightClicked() instanceof Player) {
            try {
                Player target = (Player) event.getRightClicked();
                if (target.getGameMode() == GameMode.SURVIVAL) {
                    return;
                }
                Block replaced = target.getLocation().getBlock();
                BlockState state = replaced.getState();
                byte rawData = state.getRawData();

                BlockState newState = replaced.getState();

                newState.setType(player.getItemInHand().getType());

                BlockPlaceEvent event_ = new BlockPlaceEvent(replaced, state, replaced, event.getPlayer().getItemInHand(),
                        player, true);

                Bukkit.getServer().getPluginManager().callEvent(event_);

                if (event_.isCancelled()) {
                    newState.setType(state.getType());
                    newState.setRawData(rawData);
                }
                newState.update(true);
            } catch (Exception ex) {
                Logger.trace("Error in interact place on entity: " + ex.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        final var player = e.getPlayer();
        
        try {
            SBA.getInstance().getPlayerWrapperService().register(player);
        } catch (Exception ex) {
            Logger.error("Failed to register player wrapper: " + ex.getMessage());
        }

        if (player.hasPermission(Permissions.UPGRADE.getKey())) {
            if (SBA.getInstance().isPendingUpgrade()) {
                Bukkit.getScheduler().runTaskLater(SBA.getPluginInstance(), () -> {
                    try {
                        player.sendMessage(
                                "§6[SBA]: Plugin has detected a version change, do you want to upgrade internal files?");
                        player.sendMessage("Type /sba upgrade to upgrade file");
                        player.sendMessage("§cif you want to cancel the upgrade files do /sba cancel");
                    } catch (Exception ex) {
                        Logger.trace("Error sending upgrade message: " + ex.getMessage());
                    }
                }, 40L);
            }
        }
        
        if (player.hasPermission(Permissions.UPDATE.getKey())) {
            if (SBA.getInstance().isPendingUpdate() && SBAConfig.getInstance().shouldWarnPlayerAboutUpdate()) {
                Bukkit.getScheduler().runTaskLater(SBA.getPluginInstance(), () -> {
                    try {
                        UpdateChecker.getInstance().sendToUser(player);
                    } catch (Exception ex) {
                        Logger.trace("Error sending update message: " + ex.getMessage());
                    }
                }, 40L);
            }
        }
    }
}
