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
import io.github.pronze.sba.utils.Logger;
import io.github.pronze.sba.utils.SBAUtil;
import io.github.pronze.sba.utils.ShopUtil;
import io.github.pronze.sba.wrapper.SBAPlayerWrapper;
import lombok.SneakyThrows;

import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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
import java.util.stream.Collectors;

@Service
@ServiceDependencies(dependsOn = {
        SimpleInventoriesCore.class,
        SBAConfig.class
})
public class SBAUpgradeStoreInventory extends AbstractStoreInventory {

    public static SBAUpgradeStoreInventory getInstance() {
        return ServiceManager.get(SBAUpgradeStoreInventory.class);
    }

    public static List<Integer> sharpnessPrices = new ArrayList<>();
    public static List<Integer> protectionPrices = new ArrayList<>();
    public static List<Integer> efficiencyPrices = new ArrayList<>();
    public static List<Integer> knockbackPrices = new ArrayList<>();
    public static Map<String, List<Integer>> otherPrices = new HashMap<>();

    public SBAUpgradeStoreInventory(Controllable controllable) {
        super("upgradeShop.yml");
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

    @Override
    public void onPostGenerateItem(ItemRenderEvent event) {
        event.setStack(ShopUtil.applyTeamUpgradeEnchantsToItem(event.getStack(), event, StoreType.UPGRADES));
    }

    @Override
    public void onPreGenerateItem(ItemRenderEvent event) { }

    @Override
    public boolean isUpgradeShop() { return true; }

    @Override
    public Map.Entry<Boolean, Boolean> handlePurchase(Player player, AtomicReference<ItemStack> newItem,
            AtomicReference<org.screamingsandals.lib.item.ItemStack> materialItem, PlayerItemInfo itemInfo,
            ItemSpawnerType type, AtomicReference<String[]> messageOnFail) {
        
        boolean shouldSellStack = true;
        String materialName = newItem.get().getType().name();
        
        final var game = Main.getInstance().getGameOfPlayer(player);
        final var gameStorage = ArenaManager.getInstance().get(game.getName()).orElseThrow().getStorage();
        final var team = game.getTeamOfPlayer(player);
        final var wrappedPlayer = Players.wrapPlayer(player);

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
            
            switch (propertyName) {
                case "sharpness":
                    if (isAdd) {
                        team.getConnectedPlayers().forEach(teamPlayer -> {
                            LanguageService.getInstance().get(MessageKeys.UGPRADE_TEAM_SHARPNESS)
                                    .replace("%player%", player.getDisplayName() + ChatColor.RESET).send(Players.wrapPlayer(teamPlayer));
                            Arrays.stream(teamPlayer.getInventory().getContents())
                                    .filter(Objects::nonNull)
                                    .forEach(item -> ShopUtil.increaseTeamEnchant(teamPlayer, item, Enchantment.DAMAGE_ALL, levelToAddInt));
                        });
                        break;
                    }
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
                    break;
                    
                case "knockback":
                    if (isAdd) {
                        team.getConnectedPlayers().forEach(teamPlayer -> {
                            LanguageService.getInstance().get(MessageKeys.UPGRADE_TEAM_KNOCKBACK)
                                    .replace("%player%", player.getDisplayName() + ChatColor.RESET).send(Players.wrapPlayer(teamPlayer));
                            Arrays.stream(teamPlayer.getInventory().getContents())
                                    .filter(Objects::nonNull)
                                    .forEach(item -> ShopUtil.increaseTeamEnchant(teamPlayer, item, Enchantment.KNOCKBACK, levelToAddInt));
                        });
                        break;
                    }
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
                    break;

                case "efficiency":
                    if (isAdd) {
                        team.getConnectedPlayers().forEach(teamPlayer -> {
                            LanguageService.getInstance().get(MessageKeys.UPGRADE_TEAM_EFFICIENCY)
                                    .replace("%player%", player.getDisplayName() + ChatColor.RESET).send(Players.wrapPlayer(teamPlayer));
                            Arrays.stream(teamPlayer.getInventory().getContents())
                                    .filter(Objects::nonNull)
                                    .forEach(item -> ShopUtil.increaseTeamEnchant(teamPlayer, item, Enchantment.DIG_SPEED, levelToAddInt));
                        });
                        break;
                    }
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
                    break;
                    
                case "blindtrap":
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
                    break;

                case "minertrap":
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
                    break;

                case "healpool":
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
                    break;
                    
                case "forge":
                    try {
                        var map = property.getPropertyData().childrenMap();
                        if (map != null) {
                            double addLevels = 0.2;
                            double maxLevel = 3.0;
                            List<String> types = new ArrayList<>();

                            if (map.containsKey("type")) {
                                try {
                                    types = map.get("type").getList(String.class, new ArrayList<>());
                                } catch (SerializationException e) {
                                    e.printStackTrace();
                                }
                            }
                            if (map.containsKey("add-levels")) {
                                addLevels = map.get("add-levels").getDouble(0.2);
                            }
                            if (map.containsKey("max-level")) {
                                maxLevel = map.get("max-level").getDouble(3.0);
                            }

                            Logger.trace("Forge upgrade: addLevels={}, maxLevel={}, types={}", addLevels, maxLevel, types);

                            List<ItemSpawner> spawnersToUpgrade = new ArrayList<>();

                            // Ищем спавнеры команды
                            for (var spawner : game.getItemSpawners()) {
                                if (spawner.getItemSpawnerType() == null) continue;
                                
                                String material = spawner.getItemSpawnerType().getName().toLowerCase();
                                
                                // Если типы не указаны, апгрейдим все спавнеры команды
                                if (types.isEmpty() || types.contains(material)) {
                                    // Проверяем, принадлежит ли спавнер команде
                                    if (spawner.getTeam() != null && spawner.getTeam().getName().equals(team.getName())) {
                                        if (spawner.getCurrentLevel() < maxLevel || maxLevel == 0) {
                                            spawnersToUpgrade.add(spawner);
                                            Logger.trace("Found team spawner: {} at level {}", material, spawner.getCurrentLevel());
                                        }
                                    }
                                }
                            }

                            // Если не нашли спавнеры команды, ищем ближайшие к базе
                            if (spawnersToUpgrade.isEmpty() && !types.isEmpty()) {
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
                                    
                                    if (closestSpawner != null && (closestSpawner.getCurrentLevel() < maxLevel || maxLevel == 0)) {
                                        spawnersToUpgrade.add(closestSpawner);
                                        Logger.trace("Found closest spawner: {} at level {}", spawnerType, closestSpawner.getCurrentLevel());
                                    }
                                }
                            }

                            // Применяем улучшение
                            for (var spawner : spawnersToUpgrade) {
                                double newLevel = spawner.getCurrentLevel() + addLevels;
                                if (newLevel > maxLevel && maxLevel > 0) {
                                    newLevel = maxLevel;
                                }
                                spawner.setCurrentLevel(newLevel);
                                Logger.trace("Upgraded spawner to level {}", newLevel);
                            }

                            if (spawnersToUpgrade.isEmpty()) {
                                messageOnFail.set(MessageKeys.GREATEST_SPAWNER);
                                shouldSellStack = false;
                                Logger.trace("No spawners to upgrade");
                            } else {
                                // Сообщение команде об улучшении
                                String forgeMessage = "§6✦ Улучшение генератора! §7Скорость спавна увеличена.";
                                for (Player teamPlayer : team.getConnectedPlayers()) {
                                    teamPlayer.sendMessage(forgeMessage);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Logger.error("Error in forge upgrade: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                    
                case "protection":
                    if (isAdd) {
                        team.getConnectedPlayers().forEach(teamPlayer -> {
                            LanguageService.getInstance().get(MessageKeys.UPGRADE_TEAM_PROTECTION)
                                    .replace("%player%", player.getDisplayName() + ChatColor.RESET).send(Players.wrapPlayer(teamPlayer));
                            Arrays.stream(teamPlayer.getInventory().getContents())
                                    .filter(Objects::nonNull)
                                    .forEach(item -> ShopUtil.increaseTeamEnchant(teamPlayer, item, Enchantment.PROTECTION_ENVIRONMENTAL, levelToAddInt));
                        });
                        break;
                    }
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
                    break;
                    
                default:
                    if (Arrays.stream(Enchantment.values())
                            .anyMatch(x -> x.getName().equalsIgnoreCase(propertyName)
                                    || EnchantmentType.of(x).location().path().equalsIgnoreCase(propertyName))) {

                        if (isAdd) {
                            team.getConnectedPlayers().forEach(teamPlayer -> {
                                LanguageService.getInstance().get(MessageKeys.UPGRADE_TEAM_ENCHANT)
                                        .replace("%player%", player.getDisplayName() + ChatColor.RESET).send(Players.wrapPlayer(teamPlayer));
                                Optional<Enchantment> ech = Arrays.stream(Enchantment.values())
                                        .filter(x -> x.getName().equalsIgnoreCase(propertyName) || EnchantmentType.of(x).location().path().equalsIgnoreCase(propertyName))
                                        .findAny();
                                Arrays.stream(teamPlayer.getInventory().getContents())
                                        .filter(Objects::nonNull)
                                        .forEach(item -> ShopUtil.increaseTeamEnchant(teamPlayer, item, ech.get(), levelToAddInt));
                            });
                            break;
                        }
                        var teamOtherLevel = gameStorage.getEnchantLevel(team, propertyName).orElseThrow();
                        var maxOtherLevel = SBAConfig.getInstance().node("upgrades", "limit", propertyName).getInt(1);
                        maxOtherLevel = Math.min(maxOtherLevel, otherPrices.get(propertyName).size());

                        if (teamOtherLevel >= maxOtherLevel) {
                            shouldSellStack = false;
                            messageOnFail.set(MessageKeys.GREATEST_ENCHANTMENT);
                        } else {
                            var ePrice = otherPrices.get(propertyName).get(teamOtherLevel);
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
                    break;
            }
        }

        if (shouldSellStack && SBAConfig.getInstance().isItemLimitsEnabled()) {
            PlayerItemTracker.getInstance().trackItem(player, materialName, 1);
        }

        return Map.entry(shouldSellStack, false);
    }

    @Override
    public @NotNull InventorySetBuilder getInventorySetBuilder() {
        // Возвращаем размер 6x6 как в обычном магазине
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
                        .rows(6)
                        .renderActualRows(6)
                        .renderOffset(0)
                        .renderHeaderStart(9)
                        .renderFooterStart(600)
                        .itemsOnRow(9)
                        .showPageNumber(false)
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
        return 6;
    }
    
    @Override
    public int getShopRenderActualRows() {
        return 6;
    }
    
    @Override
    public int getShopRenderOffset() {
        return 0;
    }
    
    @Override
    public int getShopRenderHeaderStart() {
        return 9;
    }
    
    @Override
    public int getShopRenderFooterStart() {
        return 600;
    }
    
    @Override
    public int getShopItemsOnRow() {
        return 9;
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBedWarsOpenShop(BedwarsOpenShopEvent event) {
        final var store = event.getStore();
        final var shopFile = store.getShopFile();
        
        boolean isUpgradeShop = false;
        
        if (shopFile != null) {
            String lowerFile = shopFile.toLowerCase();
            isUpgradeShop = lowerFile.contains("upgrade") || 
                           lowerFile.contains("upgrades") ||
                           lowerFile.equals("upgradeshop.yml");
        }
        
        if (isUpgradeShop) {
            event.setResult(BedwarsOpenShopEvent.Result.DISALLOW_UNKNOWN);
            
            if (!Main.getInstance().isPlayerPlayingAnyGame(event.getPlayer())) {
                LanguageService.getInstance().get(MessageKeys.MESSAGE_NOT_IN_GAME).send(Players.wrapPlayer(event.getPlayer()));
                return;
            }
            
            var game = Main.getInstance().getGameOfPlayer(event.getPlayer());
            if (game == null || game.getTeamOfPlayer(event.getPlayer()) == null) {
                LanguageService.getInstance().get(MessageKeys.MESSAGE_NOT_IN_GAME).send(Players.wrapPlayer(event.getPlayer()));
                return;
            }
            
            SBAUpgradeStoreInventory inventory = getInstance();
            inventory.openForPlayer(Players.wrapPlayer(event.getPlayer()).as(SBAPlayerWrapper.class),
                    (GameStore) store);
        }
    }
}
