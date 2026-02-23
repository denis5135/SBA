package io.github.pronze.sba.wrapper;

import io.github.pronze.sba.levels.PlayerLevelManager;
import org.bukkit.entity.Player;

public class SBAPluginPlayerWrapper extends SBAPlayerWrapper {
    
    public SBAPluginPlayerWrapper(Player player) {
        super(player);
    }
    
    @Override
    public int getXP() {
        return PlayerLevelManager.getInstance().getPlayerXP(getInstance());
    }
    
    @Override
    public int getLevel() {
        return PlayerLevelManager.getInstance().getPlayerLevel(getInstance());
    }
    
    @Override
    public String getProgress() {
        var levelManager = PlayerLevelManager.getInstance();
        double progress = levelManager.getLevelProgress(getInstance());
        return String.valueOf((int)(progress * 100)) + "%";
    }
    
    @Override
    public int getIntegerProgress() {
        var levelManager = PlayerLevelManager.getInstance();
        double progress = levelManager.getLevelProgress(getInstance());
        return (int)(progress * 100);
    }
    
    @Override
    public String getCompletedBoxes() {
        int progress = getIntegerProgress();
        if (progress < 1) progress = 1;
        int numberOfBoxesFilled = progress / 10;
        return "&7[&b" + "■".repeat(numberOfBoxesFilled)
                + "§7" + "■".repeat(10 - numberOfBoxesFilled) + "&7]";
    }
}
