package io.github.pronze.sba.inventories;

import io.github.pronze.sba.MessageKeys;
import io.github.pronze.sba.SBA;
import io.github.pronze.sba.config.SBAConfig;
import io.github.pronze.sba.game.ArenaManager;
import io.github.pronze.sba.game.StoreType;
import io.github.pronze.sba.game.tasks.CustomTrap;
import io.github.pronze.sba.game.tasks.CustomTrapTask;
import io.github.pronze.sba.lib.lang.LanguageService;
import io.github.pronze.sba.manager.PlayerItemTracker;
import io.github.pronze.sba.manager.ToolType;
import io.github.pronze.sba.manager.ToolUpgradeManager;
import io.github.pronze.sba.utils.Logger;
import io.github.pronze.sba.utils.SBAUtil;
import io.github.pronze.sba.utils.ShopUtil;
import io.github.pronze.sba.wrapper.SBAPlayerWrapper;
import lombok.SneakyThrows;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.events.BedwarsOpenShopEvent;
import org.screamingsandals.bedwars.api.game.ItemSpawner;
import org.screamingsandals.bedwars.api.game.ItemSpawnerType;
import org.screamingsandals.bedwars.game.GameStore;
import org.screamingsandals.lib.item.builder.ItemStackFactory;
import org.screamingsandals.lib.item.meta.EnchantmentType;
import org.screamingsandals.lib.player.Players;
import org.screamingsandals.lib.plugin.ServiceManager;
import org.screamingsandals.lib.utils.ConfigurateUtils;
import org.screamingsandals.lib.utils.Controllable;
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.lib.utils.annotations.ServiceDependencies;
import org.screamingsandals.simpleinventories.SimpleInventoriesCore;
import org.screamingsandals.simpleinventories.builder.InventorySetBuilder;
import org.screamingsandals.simpleinventories.events.ItemRenderEvent;
import org.screamingsandals.simpleinventories.inventory.Include;
import org.screamingsandals.simpleinventories.inventory.InventorySet;
import org.screamingsandals.simpleinventories.inventory.PlayerItemInfo;
import org.spongepowered.configurate.serialize.SerializationException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
@ServiceDependencies(dependsOn = {
        SimpleInventoriesCore.class,
        SBAConfig.class
})
public class SBAStoreInventoryV2 extends AbstractStoreInventory {

    public static SBAStoreInventoryV2 getInstance() {
        return ServiceManager.get(SBAStoreInventoryV2.class);
    }

    public static List<Integer> sharpnessPrices = new ArrayList<>();
    public static List<Integer> protectionPrices = new ArrayList<>();
    public static List<Integer> efficiencyPrices = new ArrayList<>();
    public static List<Integer> knockbackPrices = new ArrayList<>();
    public static Map<String, List<Integer>> otherPrices = new HashMap<>();

    public SBAStoreInventoryV2(Controllable controllable) {
        super("");
        controllable.postEnable(this::loadPrices);
    }

    private void loadPrices() {
        SBAConfig.getInstance().node("upgrades", "prices").childrenMap()
                .forEach((key, val) -> {
                    final var castedKey = ((String) key).toLowerCase();
                    final var value = val.getInt(4);
                    if (castedKey.startsWith("sharpness")) {
                        sharpnessPrices.add(value);
                    } else if (castedKey.startsWith("prot")) {
                        protectionPrices.add(value);
                    } else if (castedKey.startsWith("efficiency")) {
                        efficiencyPrices.add(value);
                    } else if (castedKey.startsWith("knockback")) {
                        knockbackPrices.add(value);
                    } else {
                        final var otherKey = castedKey.split("-", 2)[0];
                        otherPrices.computeIfAbsent(otherKey, k -> new ArrayList<>());
                        otherPrices.get(otherKey).add(value);
                    }
                });
        if (sharpnessPrices.isEmpty()) sharpnessPrices.add(4);
        if (protectionPrices.isEmpty()) protectionPrices.add(4);
        if (efficiencyPrices.isEmpty()) efficiencyPrices.add(4);
        if (knockbackPrices.isEmpty()) knockbackPrices.add(4);
    }

    @SneakyThrows
    private void loadDefault(InventorySet inventorySet) {
        inventorySet.getMainSubInventory().dropContents();
        inventorySet.getMainSubInventory().getWaitingQueue()
                .add(Include.of(Path.of(SBAStoreInventoryV2.class.getResource("/shop.yml").toURI())));
        inventorySet.getMainSubInventory().process();
    }

    @Override
    public void onPostGenerateItem(ItemRenderEvent event) {
        Player player = event.getPlayer().as(Player.class);
        if (player == null) return;
        
        ItemStack originalItem = event.getStack().as(ItemStack.class);
        String itemName = event.getStack().getMaterial().platformName();
        
        String upgradedTo = PlayerItemTracker.getInstance().getNextUpgrade(player, itemName);
        
        if (upgradedTo != null) {
            event.setStack(org.screamingsandals.lib.item.builder.ItemStackFactory.getAir());
            return;
        }
        
        event.setStack(ShopUtil.applyTeamUpgradeEnchantsToItem(event.getStack(), event, StoreType.UPGRADES));
        
        // Просто логируем инструменты
        if (SBAConfig.getInstance().isToolUpgradeEnabled() && originalItem != null) {
            try {
                ToolType toolType = ToolUpgradeManager.getInstance().getToolType(originalItem);
                if (toolType != null) {
                    int itemLevel = ToolUpgradeManager.getInstance().getCurrentLevel(originalItem);
                    int playerLevel = ToolUpgradeManager.getInstance().getToolLevel(player, toolType);
                    
                    Logger.info("🔧 Tool in shop: " + toolType + " Level " + itemLevel + 
                               " (Player has level " + playerLevel + ")");
                }
            } catch (Exception e) {
                Logger.trace("Error checking tool: " + e.getMessage());
            }
        }
    }

    @Override
    public void onPreGenerateItem(ItemRenderEvent event) { }

    @Override
    public boolean isUpgradeShop() { return false; }

    /**
     * Определить уровень меча по материалу
     */
    private int getSwordLevel(ItemStack sword) {
        if (sword == null) return -1;
        Material type = sword.getType();
        String name = type.name();
        
        if (name.contains("WOODEN_SWORD")) return 0;
        if (name.contains("STONE_SWORD")) return 1;
        if (name.contains("IRON_SWORD")) return 2;
        if (name.contains("DIAMOND_SWORD")) return 3;
        
        return -1;
    }

    /**
     * Найти существующий меч в инвентаре
     */
    private ItemStack findExistingSword(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            String name = item.getType().name();
            if (name.contains("SWORD")) {
                return item;
            }
        }
        return null;
    }

    @Override
    public Map.Entry<Boolean, Boolean> handlePurchase(Player player, AtomicReference<ItemStack> newItem,
            AtomicReference<org.screamingsandals.lib.item.ItemStack> materialItem, PlayerItemInfo itemInfo,
            ItemSpawnerType type, AtomicReference<String[]> messageOnFail) {
        
        boolean shouldSellStack = true;
        String materialName = newItem.get().getType().name();
        
        if (SBAConfig.getInstance().isItemLimitsEnabled()) {
            int limit = SBAConfig.getInstance().getItemLimit(materialName);
            if (limit != -1) {
                if (PlayerItemTracker.getInstance().hasReachedLimit(player, materialName, limit)) {
                    if (limit == 1) {
                        LanguageService.getInstance().get("item_limits.single_item").send(Players.wrapPlayer(player));
                    } else {
                        LanguageService.getInstance().get("item_limits.max_items")
                                .replace("%limit%", String.valueOf(limit)).send(Players.wrapPlayer(player));
                    }
                    return Map.entry(false, false);
                }
            }
        }
        
        boolean isUpgradeable = false;
        String nextItemId = null;
        
        for (var property : itemInfo.getProperties()) {
            if (property.hasName()) {
                String propName = property.getPropertyName().toLowerCase();
                if (propName.equals("upgradeable")) isUpgradeable = true;
                if (propName.equals("next")) nextItemId = property.getPropertyData().getString();
            }
        }
        
        if (isUpgradeable && nextItemId != null) {
            String alreadyUpgraded = PlayerItemTracker.getInstance().getNextUpgrade(player, materialName);
            if (alreadyUpgraded != null && !alreadyUpgraded.equals(nextItemId)) {
                LanguageService.getInstance().get("shop.already_upgraded").send(Players.wrapPlayer(player));
                return Map.entry(false, false);
            }
        }

        final var game = Main.getInstance().getGameOfPlayer(player);
        final var gameStorage = ArenaManager.getInstance().get(game.getName()).orElseThrow().getStorage();
        final var team = game.getTeamOfPlayer(player);
        final var wrappedPlayer = Players.wrapPlayer(player);
        
        if (itemInfo.getProperties().size() == 0) {
            final var typeName = newItem.get().getType().name();
            final var afterUnderscore = typeName.substring(typeName.contains("_") ? typeName.indexOf("_") + 1 : 0);
            
            ShopUtil.applyTeamEnchants(player, newItem.get());
            
            String afterUnderscoreLower = afterUnderscore.toLowerCase();
            
            if (afterUnderscoreLower.equals("sword")) {
                // Логика для мечей - ищем любой меч
                ItemStack currentSword = null;
                int currentSwordLevel = -1;
                
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item == null) continue;
                    String name = item.getType().name();
                    if (name.contains("SWORD")) {
                        currentSword = item;
                        currentSwordLevel = getSwordLevel(item);
                        break;
                    }
                }
                
                int newSwordLevel = getSwordLevel(newItem.get());
                
                if (currentSword != null) {
                    if (newSwordLevel <= currentSwordLevel) {
                        // Меч хуже или такой же - не даём купить
                        LanguageService.getInstance().get("sword_not_better")
                            .replace("%current%", currentSword.getType().name().replace("_", " ").toLowerCase())
                            .send(Players.wrapPlayer(player));
                        return Map.entry(false, false);
                    } else {
                        // Меч лучше - убираем старый
                        player.getInventory().removeItem(currentSword);
                    }
                }
                
            } else if (afterUnderscoreLower.equals("boots") || 
                       afterUnderscoreLower.equals("chestplate") || 
                       afterUnderscoreLower.equals("helmet") || 
                       afterUnderscoreLower.equals("leggings")) {
                return Map.entry(ShopUtil.buyArmor(player, newItem.get().getType(), gameStorage, game), false);
            }

            if (shouldSellStack) {
                if (SBAConfig.getInstance().isItemLimitsEnabled()) {
                    PlayerItemTracker.getInstance().trackItem(player, materialName, 1);
                }
                if (isUpgradeable && nextItemId != null) {
                    PlayerItemTracker.getInstance().setNextUpgrade(player, materialName, nextItemId);
                }
            }
            return Map.entry(true, true);
        }
        
        for (var property : itemInfo.getProperties()) {
            if (!property.hasName()) continue;
            
            final var propertyName = property.getPropertyName().toLowerCase();
            var converted = ConfigurateUtils.raw(property.getPropertyData());

            if (!(converted instanceof Map)) {
                converted = ShopUtil.nullValuesAllowingMap("value", converted);
            }
            var propertyData = (Map<String, Object>) converted;
            propertyData.putIfAbsent("name", propertyName);

            var isAdd = false;
            double levelToAdd = 0;
            if (property.getPropertyData() != null && property.getPropertyData().childrenMap() != null)
                isAdd = property.getPropertyData().childrenMap().containsKey("add-levels");
            if (isAdd) {
                levelToAdd = property.getPropertyData().childrenMap().get("add-levels").getDouble(1);
            }
            final int levelToAddInt = (int) levelToAdd;
            
            if (propertyName.equals("trap")) {
                if (!property.getPropertyData().hasChild("identifier") && property.getPropertyData().hasChild("data")) {
                    return Map.entry(true, true);
                }
                String trap_identifier = property.getPropertyData().childrenMap().get("identifier").getString();
                if (gameStorage.areTrapEnabled(team, trap_identifier)) {
                    messageOnFail.set(MessageKeys.WAIT_FOR_TRAP);
                    shouldSellStack = false;
                } else {
                    final var blindnessTrapTitle = LanguageService.getInstance()
                            .get(MessageKeys.CUSTOM_TRAP_PURCHASED_TITLE)
                            .replace("%trap%", trap_identifier).toComponent();

                    CustomTrap trap = new CustomTrap();
                    trap.setIdentifier(trap_identifier);
                    trap.setTarget(property.getPropertyData().childrenMap().get("target").getString("enemy"));
                    trap.setEffects(new ArrayList<>());
                    property.getPropertyData().childrenMap().get("effects").childrenList().forEach(effectItem -> {
                        try {
                            String effectType = effectItem.childrenMap().get("type").getString();
                            var slibEffect = org.screamingsandals.lib.item.meta.PotionEffectType.ofNullable(effectType);
                            PotionEffectType type_;
                            if (slibEffect != null) {
                                type_ = slibEffect.as(PotionEffectType.class);
                            } else {
                                type_ = PotionEffectType.getByName(effectType);
                            }
                            if (type_ == null) {
                                Logger.error("{} is not a recognized Potion effect", effectType);
                                return;
                            }
                            PotionEffect pe = new PotionEffect(type_,
                                    effectItem.childrenMap().get("duration").getInt(),
                                    effectItem.childrenMap().get("level").getInt());
                            trap.getEffects().add(pe);
                        } catch (Throwable t) {
                            Logger.error("Cannot parse potion effect, verify your custom trap configuration");
                        }
                    });
                    CustomTrapTask.registerTrap(trap);
                    gameStorage.setPurchasedTrap(team, true, trap_identifier);
                    if (SBAConfig.getInstance().trapTitleEnabled())
                        team.getConnectedPlayers().forEach(pl -> SBAUtil.sendTitle(Players.wrapPlayer(pl),
                                blindnessTrapTitle, org.screamingsandals.lib.spectator.Component.empty(), 20, 40, 20));
                    if (SBAConfig.getInstance().trapMessageEnabled())
                        team.getConnectedPlayers().forEach(pl -> Players.wrapPlayer(pl).sendMessage(blindnessTrapTitle));
                }
            } else if (propertyName.equals("sharpness")) {
                if (isAdd) {
                    team.getConnectedPlayers().forEach(teamPlayer -> {
                        LanguageService.getInstance().get(MessageKeys.UGPRADE_TEAM_SHARPNESS)
                                .replace("%player%", player.getDisplayName() + ChatColor.RESET).send(Players.wrapPlayer(teamPlayer));
                        Arrays.stream(teamPlayer.getInventory().getContents())
                                .filter(Objects::nonNull)
                                .forEach(item -> ShopUtil.increaseTeamEnchant(teamPlayer, item, Enchantment.DAMAGE_ALL, levelToAddInt));
                    });
                } else {
                    var teamSharpnessLevel = gameStorage.getSharpnessLevel(team).orElseThrow();
                    var maxSharpnessLevel = SBAConfig.getInstance().node("upgrades", "limit", "Sharpness").getInt(1);
                    maxSharpnessLevel = Math.min(maxSharpnessLevel, sharpnessPrices.size());

                    if (teamSharpnessLevel >= maxSharpnessLevel) {
                        messageOnFail.set(MessageKeys.GREATEST_ENCHANTMENT);
                        shouldSellStack = false;
                    } else {
                        var ePrice = sharpnessPrices.get(teamSharpnessLevel);
                        teamSharpnessLevel = teamSharpnessLevel + 1;
                        materialItem.set(ItemStackFactory.build(type.getStack(ePrice)));

                        if (player.getInventory().containsAtLeast(materialItem.get().as(ItemStack.class), ePrice)) {
                            gameStorage.setSharpnessLevel(team, teamSharpnessLevel);
                            Integer finalTeamSharpnessLevel = teamSharpnessLevel;
                            team.getConnectedPlayers().forEach(teamPlayer -> {
                                LanguageService.getInstance().get(MessageKeys.UGPRADE_TEAM_SHARPNESS)
                                        .replace("%player%", player.getDisplayName() + ChatColor.RESET).send(Players.wrapPlayer(teamPlayer));
                                Arrays.stream(teamPlayer.getInventory().getContents())
                                        .filter(Objects::nonNull)
                                        .forEach(item -> ShopUtil.applyTeamEnchants(teamPlayer, item));
                            });
                        } else shouldSellStack = false;
                    }
                }
            } else if (propertyName.equals("knockback")) {
                if (isAdd) {
                    team.getConnectedPlayers().forEach(teamPlayer -> {
                        LanguageService.getInstance().get(MessageKeys.UPGRADE_TEAM_KNOCKBACK)
                                .replace("%player%", player.getDisplayName() + ChatColor.RESET).send(Players.wrapPlayer(teamPlayer));
                        Arrays.stream(teamPlayer.getInventory().getContents())
                                .filter(Objects::nonNull)
                                .forEach(item -> ShopUtil.increaseTeamEnchant(teamPlayer, item, Enchantment.KNOCKBACK, levelToAddInt));
                    });
                } else {
                    var teamKnockbackLevel = gameStorage.getSharpnessLevel(team).orElseThrow();
                    var maxKnockbackLevel = SBAConfig.getInstance().node("upgrades", "limit", "Knockback").getInt(1);

                    if (teamKnockbackLevel >= maxKnockbackLevel) {
                        shouldSellStack = false;
                        messageOnFail.set(MessageKeys.GREATEST_ENCHANTMENT);
                    } else {
                        var ePrice = knockbackPrices.get(teamKnockbackLevel);
                        teamKnockbackLevel = teamKnockbackLevel + 1;
                        materialItem.set(ItemStackFactory.build(type.getStack(ePrice)));

                        if (player.getInventory().containsAtLeast(materialItem.get().as(ItemStack.class), ePrice)) {
                            gameStorage.setSharpnessLevel(team, teamKnockbackLevel);
                            Integer finalTeamSharpnessLevel = teamKnockbackLevel;
                            team.getConnectedPlayers().forEach(teamPlayer -> {
                                LanguageService.getInstance().get(MessageKeys.UPGRADE_TEAM_KNOCKBACK)
                                        .replace("%player%", player.getDisplayName() + ChatColor.RESET).send(Players.wrapPlayer(teamPlayer));
                                Arrays.stream(teamPlayer.getInventory().getContents())
                                        .filter(Objects::nonNull)
                                        .forEach(item -> ShopUtil.applyTeamEnchants(teamPlayer, item));
                            });
                        } else shouldSellStack = false;
                    }
                }
            } else if (propertyName.equals("efficiency")) {
                if (isAdd) {
                    team.getConnectedPlayers().forEach(teamPlayer -> {
                        LanguageService.getInstance().get(MessageKeys.UPGRADE_TEAM_EFFICIENCY)
                                .replace("%player%", player.getDisplayName() + ChatColor.RESET).send(Players.wrapPlayer(teamPlayer));
                        Arrays.stream(teamPlayer.getInventory().getContents())
                                .filter(Objects::nonNull)
                                .forEach(item -> ShopUtil.increaseTeamEnchant(teamPlayer, item, Enchantment.DIG_SPEED, levelToAddInt));
                    });
                } else {
                    var efficiencyLevel = gameStorage.getEfficiencyLevel(team).orElseThrow();
                    var maxEfficiencyLevel = SBAConfig.getInstance().node("upgrades", "limit", "Efficiency").getInt(2);
                    maxEfficiencyLevel = Math.min(maxEfficiencyLevel, efficiencyPrices.size());
                    
                    if (efficiencyLevel >= maxEfficiencyLevel) {
                        shouldSellStack = false;
                        messageOnFail.set(MessageKeys.GREATEST_ENCHANTMENT);
                    } else {
                        var ePrice = efficiencyPrices.get(efficiencyLevel);
                        efficiencyLevel = efficiencyLevel + 1;
                        materialItem.set(ItemStackFactory.build(type.getStack(ePrice)));

                        if (player.getInventory().containsAtLeast(materialItem.get().as(ItemStack.class), ePrice)) {
                            gameStorage.setEfficiencyLevel(team, efficiencyLevel);
                            team.getConnectedPlayers().forEach(teamPlayer -> {
                                LanguageService.getInstance().get(MessageKeys.UPGRADE_TEAM_EFFICIENCY)
                                        .replace("%player%", player.getDisplayName() + ChatColor.RESET).send(Players.wrapPlayer(teamPlayer));
                                Arrays.stream(teamPlayer.getInventory().getContents())
                                        .filter(Objects::nonNull)
                                        .forEach(item -> ShopUtil.applyTeamEnchants(teamPlayer, item));
                            });
                        } else shouldSellStack = false;
                    }
                }
            } else if (propertyName.equals("blindtrap")) {
                if (gameStorage.areBlindTrapEnabled(team)) {
                    shouldSellStack = false;
                    messageOnFail.set(MessageKeys.WAIT_FOR_TRAP);
                } else {
                    final var blindnessTrapTitle = LanguageService.getInstance().get(MessageKeys.BLINDNESS_TRAP_PURCHASED_TITLE).toComponent();
                    gameStorage.setPurchasedBlindTrap(team, true);
                    if (SBAConfig.getInstance().trapTitleEnabled())
                        team.getConnectedPlayers().forEach(pl -> SBAUtil.sendTitle(Players.wrapPlayer(pl),
                                blindnessTrapTitle, org.screamingsandals.lib.spectator.Component.empty(), 20, 40, 20));
                    if (SBAConfig.getInstance().trapMessageEnabled())
                        team.getConnectedPlayers().forEach(pl -> Players.wrapPlayer(pl).sendMessage(blindnessTrapTitle));
                }
            } else if (propertyName.equals("minertrap")) {
                if (gameStorage.areMinerTrapEnabled(team)) {
                    shouldSellStack = false;
                    messageOnFail.set(MessageKeys.WAIT_FOR_TRAP);
                } else {
                    final var minerTrapTitle = LanguageService.getInstance().get(MessageKeys.MINER_TRAP_PURCHASED_TITLE).toComponent();
                    gameStorage.setPurchasedMinerTrap(team, true);
                    if (SBAConfig.getInstance().trapTitleEnabled())
                        team.getConnectedPlayers().forEach(pl -> SBAUtil.sendTitle(Players.wrapPlayer(pl),
                                minerTrapTitle, org.screamingsandals.lib.spectator.Component.empty(), 20, 40, 20));
                    if (SBAConfig.getInstance().trapMessageEnabled())
                        team.getConnectedPlayers().forEach(pl -> Players.wrapPlayer(pl).sendMessage(minerTrapTitle));
                }
            } else if (propertyName.equals("healpool")) {
                shouldSellStack = false;
                messageOnFail.set(null);
                if (gameStorage.arePoolEnabled(team)) {
                    messageOnFail.set(MessageKeys.WAIT_FOR_TRAP);
                } else {
                    var purchaseHealPoolMessage = LanguageService.getInstance().get(MessageKeys.PURCHASED_HEAL_POOL_MESSAGE)
                            .replace("%player%", player.getDisplayName() + ChatColor.RESET).toComponent();
                    gameStorage.setPurchasedPool(team, true);
                    shouldSellStack = true;
                    team.getConnectedPlayers().forEach(pl -> Players.wrapPlayer(pl).sendMessage(purchaseHealPoolMessage));
                }
            } else if (propertyName.equals("forge")) {
                var map = property.getPropertyData().childrenMap();
                if (map != null) {
                    double addLevels = 0.2;
                    double maxLevel = 0;
                    List<String> types = new ArrayList<>();

                    if (map.containsKey("type")) {
                        try { 
                            types = map.get("type").getList(String.class, new ArrayList<>()); 
                        } catch (SerializationException e) { 
                            e.printStackTrace(); 
                        }
                    }
                    if (map.containsKey("add-levels")) addLevels = map.get("add-levels").getDouble(0.2);
                    if (map.containsKey("max-level")) maxLevel = map.get("max-level").getDouble(0);

                    List<ItemSpawner> spawnersToUpgrade = new ArrayList<>();

                    for (var spawner : game.getItemSpawners()) {
                        if (spawner.getItemSpawnerType() == null) continue;
                        var material = spawner.getItemSpawnerType().getName().toLowerCase();
                        if (types.contains(material)) {
                            if (spawner.getTeam() != null && spawner.getTeam().getName().equals(team.getName())) {
                                if (spawner.getCurrentLevel() < maxLevel || maxLevel == 0)
                                    spawnersToUpgrade.add(spawner);
                            }
                        }
                    }
                    
                    double maxLevelF = maxLevel;
                    if (spawnersToUpgrade.isEmpty()) {
                        for (String spawnerType : types) {
                            double closestDistance = Double.MAX_VALUE;
                            ItemSpawner closestSpawner = null;
                            for (var spawner : game.getItemSpawners()) {
                                if (spawner.getItemSpawnerType() == null) continue;
                                if (spawner.getItemSpawnerType().getName().toLowerCase().equals(spawnerType)) {
                                    double distance = team.getTeamSpawn().distance(spawner.getLocation());
                                    if (distance < closestDistance) {
                                        closestDistance = distance;
                                        closestSpawner = spawner;
                                    }
                                }
                            }
                            if (closestSpawner != null) {
                                double newLevel = closestSpawner.getCurrentLevel();
                                if (newLevel < maxLevelF || maxLevelF == 0) spawnersToUpgrade.add(closestSpawner);
                            }
                        }
                    }

                    for (var spawner : spawnersToUpgrade) {
                        double newLevel = spawner.getCurrentLevel() + addLevels;
                        if (newLevel > maxLevel && maxLevel > 0) newLevel = maxLevel;
                        spawner.setCurrentLevel(newLevel);
                    }

                    if (spawnersToUpgrade.isEmpty()) {
                        messageOnFail.set(MessageKeys.GREATEST_SPAWNER);
                        shouldSellStack = false;
                    }
                }
            } else if (propertyName.equals("protection")) {
                if (isAdd) {
                    team.getConnectedPlayers().forEach(teamPlayer -> {
                        LanguageService.getInstance().get(MessageKeys.UPGRADE_TEAM_PROTECTION)
                                .replace("%player%", player.getDisplayName() + ChatColor.RESET).send(Players.wrapPlayer(teamPlayer));
                        Arrays.stream(teamPlayer.getInventory().getContents())
                                .filter(Objects::nonNull)
                                .forEach(item -> ShopUtil.increaseTeamEnchant(teamPlayer, item, Enchantment.PROTECTION_ENVIRONMENTAL, levelToAddInt));
                    });
                } else {
                    var teamProtectionLevel = gameStorage.getProtectionLevel(team).orElseThrow();
                    var maxProtectionLevel = SBAConfig.getInstance().node("upgrades", "limit", "Protection").getInt(4);
                    maxProtectionLevel = Math.min(maxProtectionLevel, protectionPrices.size());

                    if (teamProtectionLevel >= maxProtectionLevel) {
                        shouldSellStack = false;
                        messageOnFail.set(MessageKeys.GREATEST_ENCHANTMENT);
                    } else {
                        var ePrice = protectionPrices.get(teamProtectionLevel);
                        teamProtectionLevel = teamProtectionLevel + 1;
                        materialItem.set(ItemStackFactory.build(type.getStack(ePrice)));

                        if (player.getInventory().containsAtLeast(materialItem.get().as(ItemStack.class), ePrice)) {
                            gameStorage.setProtectionLevel(team, teamProtectionLevel);
                            ShopUtil.addEnchantsToPlayerArmor(player, teamProtectionLevel);

                            var upgradeMessage = LanguageService.getInstance().get(MessageKeys.UPGRADE_TEAM_PROTECTION)
                                    .replace("%player%", player.getDisplayName() + ChatColor.RESET).toComponent();

                            team.getConnectedPlayers().forEach(teamPlayer -> {
                                Arrays.stream(teamPlayer.getInventory().getContents())
                                        .filter(Objects::nonNull)
                                        .forEach(item -> ShopUtil.applyTeamEnchants(teamPlayer, item));
                                Players.wrapPlayer(teamPlayer).sendMessage(upgradeMessage);
                            });
                        } else shouldSellStack = false;
                    }
                }
            } else if (propertyName.equals("pickaxe") || 
                       propertyName.equals("axe") || 
                       propertyName.equals("shears")) {
                
                // Обработка улучшения инструментов как в upgradeShop
                if (SBAConfig.getInstance().isToolUpgradeEnabled()) {
                    ToolType toolType = null;
                    if (propertyName.equals("pickaxe")) {
                        toolType = ToolType.PICKAXE;
                    } else if (propertyName.equals("axe")) {
                        toolType = ToolType.AXE;
                    } else if (propertyName.equals("shears")) {
                        toolType = ToolType.SHEARS;
                    }
                    
                    if (toolType != null) {
                        // Получаем цену из amount материала
                        int price = materialItem.get().getAmount();
                        boolean success = ToolUpgradeManager.getInstance().upgradeTool(player, toolType, type, price);
                        if (!success) {
                            shouldSellStack = false;
                        }
                    }
                }
            } else {
                // Проверяем, является ли свойство энчантом
                boolean isEnchant = false;
                Optional<Enchantment> foundEnchant = Optional.empty();
                
                for (Enchantment ench : Enchantment.values()) {
                    if (ench.getName().equalsIgnoreCase(propertyName) ||
                            EnchantmentType.of(ench).location().path().equalsIgnoreCase(propertyName)) {
                        isEnchant = true;
                        foundEnchant = Optional.of(ench);
                        break;
                    }
                }
                
                if (isEnchant) {
                    if (isAdd) {
                        Enchantment finalEnch = foundEnchant.get();
                        team.getConnectedPlayers().forEach(teamPlayer -> {
                            LanguageService.getInstance().get(MessageKeys.UPGRADE_TEAM_ENCHANT)
                                    .replace("%player%", player.getDisplayName() + ChatColor.RESET).send(Players.wrapPlayer(teamPlayer));
                            Arrays.stream(teamPlayer.getInventory().getContents())
                                    .filter(Objects::nonNull)
                                    .forEach(item -> ShopUtil.increaseTeamEnchant(teamPlayer, item, finalEnch, levelToAddInt));
                        });
                    } else {
                        var teamOtherLevel = gameStorage.getEnchantLevel(team, propertyName).orElseThrow();
                        var maxOtherLevel = SBAConfig.getInstance().node("upgrades", "limit", propertyName).getInt(1);
                        
                        List<Integer> priceList = otherPrices.get(propertyName);
                        if (priceList != null) {
                            maxOtherLevel = Math.min(maxOtherLevel, priceList.size());

                            if (teamOtherLevel >= maxOtherLevel) {
                                shouldSellStack = false;
                                messageOnFail.set(MessageKeys.GREATEST_ENCHANTMENT);
                            } else {
                                var ePrice = priceList.get(teamOtherLevel);
                                teamOtherLevel = teamOtherLevel + 1;
                                materialItem.set(ItemStackFactory.build(type.getStack(ePrice)));
                                
                                if (player.getInventory().containsAtLeast(materialItem.get().as(ItemStack.class), ePrice)) {
                                    gameStorage.setEnchantLevel(team, propertyName, teamOtherLevel);
                                    team.getConnectedPlayers().forEach(teamPlayer -> {
                                        LanguageService.getInstance().get(MessageKeys.UPGRADE_TEAM_ENCHANT)
                                                .replace("%player%", player.getDisplayName() + ChatColor.RESET).send(Players.wrapPlayer(teamPlayer));
                                        Arrays.stream(teamPlayer.getInventory().getContents())
                                                .filter(Objects::nonNull)
                                                .forEach(item -> ShopUtil.applyTeamEnchants(teamPlayer, item));
                                    });
                                } else shouldSellStack = false;
                            }
                        } else {
                            return Map.entry(true, true);
                        }
                    }
                } else {
                    return Map.entry(true, true);
                }
            }
        }

        if (shouldSellStack && SBAConfig.getInstance().isItemLimitsEnabled()) {
            PlayerItemTracker.getInstance().trackItem(player, materialName, 1);
        }

        return Map.entry(shouldSellStack, false);
    }

    @Override
    public @NotNull InventorySetBuilder getInventorySetBuilder() {
        return SimpleInventoriesCore
                .builder()
                .categoryOptions(localOptionsBuilder -> localOptionsBuilder
                        .backItem(
                                SBAConfig.getInstance().readDefinedItem(
                                        SBAConfig.getInstance().node("shop", "shopback"), "BARRIER"),
                                itemBuilder -> itemBuilder.name(
                                        LanguageService.getInstance().get(MessageKeys.SHOP_PAGE_BACK).toComponent()))
                        .pageBackItem(
                                SBAConfig.getInstance().readDefinedItem(
                                        SBAConfig.getInstance().node("shop", "pageback"), "ARROW"),
                                itemBuilder -> itemBuilder.name(
                                        LanguageService.getInstance().get(MessageKeys.SHOP_PAGE_BACK).toComponent()))
                        .pageForwardItem(
                                SBAConfig.getInstance().readDefinedItem(
                                        SBAConfig.getInstance().node("shop", "pageforward"), "BARRIER"),
                                itemBuilder -> itemBuilder.name(
                                        LanguageService.getInstance().get(MessageKeys.SHOP_PAGE_FORWARD).toComponent()))
                        .cosmeticItem(SBAConfig.getInstance().readDefinedItem(
                                SBAConfig.getInstance().node("shop", "shopcosmetic"),
                                "GRAY_STAINED_GLASS_PANE"))
                        .rows(getShopRows())
                        .renderActualRows(getShopRenderActualRows())
                        .renderOffset(SBAConfig.getInstance().getShopRenderOffset())
                        .renderHeaderStart(SBAConfig.getInstance().getShopRenderHeaderStart())
                        .renderFooterStart(SBAConfig.getInstance().getShopRenderFooterStart())
                        .itemsOnRow(SBAConfig.getInstance().getShopItemsOnRow())
                        .showPageNumber(SBAConfig.getInstance().node("shop", "show-page-numbers").getBoolean(false))
                        .inventoryType("CHEST")
                        .prefix(LanguageService.getInstance().get(MessageKeys.SHOP_NAME).toComponent()))
                .allowAccessToConsole(true)
                .variableToProperty("upgrade", "upgrade")
                .variableToProperty("generate-lore", "generateLore")
                .variableToProperty("generated-lore-text", "generatedLoreText")
                .variableToProperty("currency-changer", "currencyChanger");
    }

    @Override
    public int getShopRows() {
        return SBAConfig.getInstance().getNormalShopRows();
    }
    
    @Override
    public int getShopRenderActualRows() {
        return SBAConfig.getInstance().getNormalShopRenderActualRows();
    }
    
    @Override
    public int getShopRenderOffset() {
        return SBAConfig.getInstance().getNormalShopRenderOffset();
    }
    
    @Override
    public int getShopRenderHeaderStart() {
        return SBAConfig.getInstance().getNormalShopRenderHeaderStart();
    }
    
    @Override
    public int getShopRenderFooterStart() {
        return SBAConfig.getInstance().getNormalShopRenderFooterStart();
    }
    
    @Override
    public int getShopItemsOnRow() {
        return SBAConfig.getInstance().getNormalShopItemsOnRow();
    }
    
    @Override
    public String getShopBack() {
        return SBAConfig.getInstance().getShopBack();
    }
    
    @Override
    public String getShopPageBack() {
        return SBAConfig.getInstance().getShopPageBack();
    }
    
    @Override
    public String getShopPageForward() {
        return SBAConfig.getInstance().getShopPageForward();
    }

    @EventHandler
    public void onBedWarsOpenShop(BedwarsOpenShopEvent event) {
        final var shopFile = event.getStore().getShopFile();
        
        if (shopFile != null && shopFile.toLowerCase().contains("upgrade")) {
            if (!Main.getInstance().isPlayerPlayingAnyGame(event.getPlayer())) {
                LanguageService.getInstance().get(MessageKeys.MESSAGE_NOT_IN_GAME).send(Players.wrapPlayer(event.getPlayer()));
                return;
            }
            if (Main.getInstance().getGameOfPlayer(event.getPlayer()).getTeamOfPlayer(event.getPlayer()) == null) {
                LanguageService.getInstance().get(MessageKeys.MESSAGE_NOT_IN_GAME).send(Players.wrapPlayer(event.getPlayer()));
                return;
            }
            
            openForPlayer(Players.wrapPlayer(event.getPlayer()).as(SBAPlayerWrapper.class),
                    (GameStore) event.getStore());
        } else {
            event.setResult(BedwarsOpenShopEvent.Result.DISALLOW_UNKNOWN);
            if (!Main.getInstance().isPlayerPlayingAnyGame(event.getPlayer())) {
                LanguageService.getInstance().get(MessageKeys.MESSAGE_NOT_IN_GAME).send(Players.wrapPlayer(event.getPlayer()));
                return;
            }
            if (Main.getInstance().getGameOfPlayer(event.getPlayer()).getTeamOfPlayer(event.getPlayer()) == null) {
                LanguageService.getInstance().get(MessageKeys.MESSAGE_NOT_IN_GAME).send(Players.wrapPlayer(event.getPlayer()));
                return;
            }
            
            openForPlayer(Players.wrapPlayer(event.getPlayer()).as(SBAPlayerWrapper.class),
                    (GameStore) event.getStore());
        }
    }
}
