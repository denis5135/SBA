package io.github.pronze.sba.manager;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
public class ToolLevels {
    private int pickaxeLevel = 0; // 0-3 (WOOD, STONE, IRON, DIAMOND)
    private int axeLevel = 0;      // 0-3 (WOOD, STONE, IRON, DIAMOND)
    private int shearsLevel = 0;   // 0-1 (NORMAL, EFFICIENCY 1)
    
    // Слоты, в которых находятся инструменты
    private int pickaxeSlot = -1;
    private int axeSlot = -1;
    private int shearsSlot = -1;
    
    private static final Map<UUID, ToolLevels> playerData = new HashMap<>();
    
    public static ToolLevels getOrCreate(UUID uuid) {
        return playerData.computeIfAbsent(uuid, k -> new ToolLevels());
    }
    
    public static void remove(UUID uuid) {
        playerData.remove(uuid);
    }
    
    public static boolean hasData(UUID uuid) {
        return playerData.containsKey(uuid);
    }
}
