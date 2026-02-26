package io.github.pronze.sba.inventories;

import io.github.pronze.sba.MessageKeys;
import io.github.pronze.sba.SBA;
import io.github.pronze.sba.config.SBAConfig;
import io.github.pronze.sba.game.ArenaManager;
import io.github.pronze.sba.game.StoreType;
import io.github.pronze.sba.lib.lang.LanguageService;
import io.github.pronze.sba.utils.Logger;
import io.github.pronze.sba.utils.ShopUtil;
import io.github.pronze.sba.wrapper.SBAPlayerWrapper;
import lombok.SneakyThrows;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.events.BedwarsOpenShopEvent;
import org.screamingsandals.bedwars.game.GameStore;
import org.screamingsandals.lib.player.Players;
import org.screamingsandals.lib.plugin.ServiceManager;
import org.screamingsandals.lib.utils.Controllable;
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.lib.utils.annotations.ServiceDependencies;
import org.screamingsandals.simpleinventories.SimpleInventoriesCore;
import org.screamingsandals.simpleinventories.builder.InventorySetBuilder;
import org.screamingsandals.simpleinventories.events.ItemRenderEvent;
import org.screamingsandals.simpleinventories.inventory.Include;
import org.screamingsandals.simpleinventories.inventory.InventorySet;
import org.screamingsandals.simpleinventories.inventory.PlayerItemInfo;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
@ServiceDependencies(dependsOn = {
        SimpleInventoriesCore.class,
        SBAConfig.class
})
public class SBAUpgradeStoreInventory extends AbstractStoreInventory {

    public static SBAUpgradeStoreInventory getInstance() {
        return ServiceManager.get(SBAUpgradeStoreInventory.class);
    }

    public SBAUpgradeStoreInventory(Controllable controllable) {
        super("upgradeShop.yml");
    }

    @Override
    public void onPostGenerateItem(ItemRenderEvent event) { }

    @Override
    public void onPreGenerateItem(ItemRenderEvent event) { }

    @Override
    public boolean isUpgradeShop() { return true; }

    @Override
    public Map.Entry<Boolean, Boolean> handlePurchase(Player player, AtomicReference<ItemStack> newItem,
            AtomicReference<org.screamingsandals.lib.item.ItemStack> materialItem, PlayerItemInfo itemInfo,
            org.screamingsandals.bedwars.api.game.ItemSpawnerType type, AtomicReference<String[]> messageOnFail) {
        return Map.entry(true, true);
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
                        .rows(6)
                        .renderActualRows(6)
                        .renderOffset(0)
                        .renderHeaderStart(9)
                        .renderFooterStart(600)
                        .itemsOnRow(9)
                        .showPageNumber(false)
                        .inventoryType("CHEST")
                        .prefix(LanguageService.getInstance().get(MessageKeys.SHOP_NAME).toComponent()))
                .allowAccessToConsole(true);
    }

    @Override
    public int getShopRows() { return 6; }
    
    @Override
    public int getShopRenderActualRows() { return 6; }
    
    @Override
    public int getShopRenderOffset() { return 0; }
    
    @Override
    public int getShopRenderHeaderStart() { return 9; }
    
    @Override
    public int getShopRenderFooterStart() { return 600; }
    
    @Override
    public int getShopItemsOnRow() { return 9; }
    
    @Override
    public String getShopBack() { return "BARRIER"; }
    
    @Override
    public String getShopPageBack() { return "ARROW"; }
    
    @Override
    public String getShopPageForward() { return "BARRIER"; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBedWarsOpenShop(BedwarsOpenShopEvent event) {
        Logger.info("========== SHOP DEBUG ==========");
        Logger.info("Event fired for player: " + event.getPlayer().getName());
        Logger.info("Shop file: " + event.getStore().getShopFile());
        Logger.info("=================================");
        
        // Для теста открываем ЛЮБОЙ магазин как upgrade
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
                (GameStore) event.getStore());
    }

    // Тестовая команда для открытия магазина
    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (e.getMessage().equalsIgnoreCase("/testupgrade")) {
            e.setCancelled(true);
            Player player = e.getPlayer();
            
            // Создаем фейковый GameStore
            GameStore fakeStore = new GameStore(null, null, "upgradeShop.yml", true);
            
            SBAUpgradeStoreInventory inventory = getInstance();
            inventory.openForPlayer(Players.wrapPlayer(player).as(SBAPlayerWrapper.class), fakeStore);
            
            player.sendMessage("§aТестовый магазин открыт!");
        }
    }
}
