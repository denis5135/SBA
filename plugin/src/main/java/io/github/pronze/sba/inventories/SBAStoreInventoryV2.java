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
            if (name.contains("SWORD") && !name.contains("WOODEN")) {
                return item;
            }
        }
        return null;
    }

    @Override
    public Map.Entry<Boolean, Boolean> handlePurchase(Player player, AtomicReference<ItemStack> newItem,
            AtomicReference<org.screamingsandals.lib.item.ItemStack> materialItem, PlayerItemInfo itemInfo,
            ItemSpawnerType type, AtomicReference<String[]> messageOnFail) {
        
        // ОТЛАДКА
        Logger.info("=== PURCHASE DEBUG ===");
        Logger.info("Player: " + player.getName());
        Logger.info("Item: " + newItem.get().getType().name());
        Logger.info("Properties size: " + itemInfo.getProperties().size());
        
        for (var property : itemInfo.getProperties()) {
            if (property.hasName()) {
                Logger.info("  Property: " + property.getPropertyName());
            }
        }
        Logger.info("=====================");
        
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

        final var game = Main.getInstance().getGameOfPlayer(player);
        final var gameStorage = ArenaManager.getInstance().get(game.getName()).orElseThrow().getStorage();
        final var team = game.getTeamOfPlayer(player);
        final var wrappedPlayer = Players.wrapPlayer(player);
        
        // Проверяем, есть ли у предмета свойства
        if (itemInfo.getProperties().size() > 0) {
            // Обработка предметов со свойствами (включая инструменты)
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
                    // ... обработка ловушек ...
                } else if (propertyName.equals("sharpness")) {
                    // ... обработка sharpness ...
                } else if (propertyName.equals("knockback")) {
                    // ... обработка knockback ...
                } else if (propertyName.equals("efficiency")) {
                    // ... обработка efficiency ...
                } else if (propertyName.equals("blindtrap")) {
                    // ... обработка blindtrap ...
                } else if (propertyName.equals("minertrap")) {
                    // ... обработка minertrap ...
                } else if (propertyName.equals("healpool")) {
                    // ... обработка healpool ...
                } else if (propertyName.equals("forge")) {
                    // ... обработка forge ...
                } else if (propertyName.equals("protection")) {
                    // ... обработка protection ...
                } else if (propertyName.equals("pickaxe") || 
                           propertyName.equals("axe") || 
                           propertyName.equals("shears")) {
                    
                    // Обработка улучшения инструментов
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
                            boolean success = ToolUpgradeManager.getInstance().upgradeTool(player, toolType, type);
                            if (!success) {
                                shouldSellStack = false;
                            }
                        }
                    }
                } else {
                    // ... обработка энчантов ...
                }
            }
        } else {
            // Обработка обычных предметов (без свойств)
            final var typeName = newItem.get().getType().name();
            final var afterUnderscore = typeName.substring(typeName.contains("_") ? typeName.indexOf("_") + 1 : 0);
            
            ShopUtil.applyTeamEnchants(player, newItem.get());
            
            String afterUnderscoreLower = afterUnderscore.toLowerCase();
            
            if (afterUnderscoreLower.equals("sword")) {
                // Логика для мечей
                ItemStack currentSword = findExistingSword(player);
                int newSwordLevel = getSwordLevel(newItem.get());
                
                if (currentSword != null) {
                    int currentSwordLevel = getSwordLevel(currentSword);
                    
                    if (newSwordLevel <= currentSwordLevel) {
                        // Меч хуже или такой же - не даём купить
                        LanguageService.getInstance().get("shop.sword_not_better")
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
            // Инструменты НЕ обрабатываем здесь, только в секции со свойствами!
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
