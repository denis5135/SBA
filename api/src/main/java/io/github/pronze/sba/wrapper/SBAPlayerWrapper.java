package io.github.pronze.sba.wrapper;

import com.google.common.base.Strings;
import io.github.pronze.sba.AddonAPI;
import io.github.pronze.sba.MessageKeys;
import io.github.pronze.sba.Permissions;
import io.github.pronze.sba.data.ToggleableSetting;
import lombok.Getter;
import lombok.Setter;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.lib.player.Players;
import org.screamingsandals.lib.spectator.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

@Getter
@Setter
public class SBAPlayerWrapper extends org.screamingsandals.lib.player.ExtendablePlayer {
    private int shoutCooldown;
    private final ToggleableSetting<PlayerSetting> settings;

    public SBAPlayerWrapper(Player player) {
        super(Players.wrapPlayer(player));

        this.shoutCooldown = 0;
        this.settings = ToggleableSetting.of(PlayerSetting.class);
    }
   

    public void sendMessage(String message) {
        getInstance().sendMessage(message);
    }

    public Player getInstance() {
        return as(Player.class);
    }

    public boolean canShout() {
        return shoutCooldown == 0;
    }

    public void shout(Component message) {
        System.out.println("Player shouting with cooldown "+shoutCooldown);
        if (shoutCooldown == 0) {
            //sendMessage(message);
            Collection<? extends Player> receivers = Bukkit.getOnlinePlayers();
            if(Main.isPlayerInGame(as(Player.class)))
                receivers=Main.getInstance().getGameOfPlayer(as(Player.class)).getConnectedPlayers();
            receivers.forEach(receiver->Players.wrapPlayer(receiver).sendMessage(message));
            if (getInstance().hasPermission(Permissions.SHOUT_BYPASS.getKey()) || getDefaultShoutCoolDownTime() == 0) {
                return;
            }

            shoutCooldown = getDefaultShoutCoolDownTime();

            new BukkitRunnable() {
                @Override
                public void run() {
                    shoutCooldown = shoutCooldown - 1;
                    if (shoutCooldown == 0) {
                        this.cancel();
                    }
                }
            }.runTaskTimer(AddonAPI.getInstance().getJavaPlugin(), 0L, 20L);
        } else {
            AddonAPI
                    .getInstance()
                    .getLanguageService()
                    .get(MessageKeys.MESSAGE_SHOUT_WAIT)
                    .replace("%seconds%", String.valueOf(getShoutCooldown()))
                    .send(this);
        }
    }

    // ========== ВРЕМЕННЫЕ МЕТОДЫ (будут заменены плагином) ==========
    
    public int getXP() {
        // Эти методы будут переопределены плагином
        return 0;
    }

    public int getLevel() {
        return 1;
    }

    public String getProgress() {
        return "0%";
    }

    public int getIntegerProgress() {
        return 0;
    }

    public String getCompletedBoxes() {
        return "&7[&b■■■■■■■■■■&7]";
    }

    protected static String round(double toRound) {
        if (toRound >= 1000.0D) {
            var bd = new BigDecimal(String.valueOf(toRound / 1000));
            bd = bd.setScale(1, RoundingMode.HALF_DOWN);
            return bd.doubleValue() + "k";
        }
        return String.valueOf(toRound);
    }

    protected static String round(int toRound) {
        return round((double) toRound);
    }

    public static int getTotalXPToLevelUp() {
        return 5000;
    }

    protected static int getDefaultShoutCoolDownTime() {
        return AddonAPI.getInstance().getConfigurator().getInt("shout.time-out", 60);
    }
}
