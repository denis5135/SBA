package io.github.pronze.lib.pronzelib.scoreboards.scoreboardr.board.implementations.drivers.v1;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import io.github.pronze.lib.pronzelib.scoreboards.scoreboardr.board.implementations.IBoard;
import io.github.pronze.lib.pronzelib.scoreboards.scoreboardr.plugin.Session;
import io.github.pronze.lib.pronzelib.scoreboards.scoreboardr.plugin.utility.LineLimits;
import io.github.pronze.lib.pronzelib.scoreboards.scoreboardr.plugin.utility.ScoreboardStrings;
import io.github.pronze.sba.utils.Logger;

import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

public class ScoreboardDriverV1 implements IBoard {

    private Player player;
    private Scoreboard board;
    private Objective objective;
    private int lines;
    private HashMap<Integer, String> cache = new HashMap<>();
    private String objectiveName = "sbascoreboard";
    private boolean initialized = false;

    @Override
    public void setPlayer(Player player) {
        this.player = player;

        try {
            var manager = Session.getSession().plugin.getServer().getScoreboardManager();
            if (manager == null) {
                Logger.error("Scoreboard manager is null for player " + player.getName());
                return;
            }
            
            this.board = manager.getNewScoreboard();
            if (this.board == null) {
                Logger.error("Could not create new scoreboard for player " + player.getName());
                return;
            }
            
            this.objective = this.board.registerNewObjective(objectiveName, "dummy");
            if (this.objective == null) {
                Logger.error("Could not register objective for player " + player.getName());
                return;
            }
            
            this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            this.objective.setDisplayName("");
            this.initialized = true;

            this.createTeams();
            this.setBoard();
            
            Logger.trace("Scoreboard initialized for player " + player.getName());
        } catch (Exception e) {
            Logger.error("Failed to initialize scoreboard for player " + player.getName() + ": " + e.getMessage());
            this.initialized = false;
        }

        LineLimits.getLineLimit();
    }

    @Override
    public void setTitle(String title) {
        if (!isValid()) return;
        
        if (title == null) title = "";
        if (title.length() > LineLimits.getLineLimit() * 2) {
            title = title.substring(0, LineLimits.getLineLimit() * 2);
        }

        try {
            this.objective.setDisplayName(title);
        } catch (Exception e) {
            Logger.error("Failed to set title for player " + (player != null ? player.getName() : "unknown"));
        }
    }

    @Override
    public void setLine(int line, String content) {
        if (!isValid()) return;
        
        if (content == null) content = "";
        if (!shouldUpdate(line, content)) return;

        try {
            Team team = board.getTeam(line + "");
            if (team == null) return;
            
            String[] split = split(content);
            team.setPrefix(split[0]);
            team.setSuffix(split[1]);
        } catch (Exception e) {
            Logger.error("Failed to set line " + line + " for player " + (player != null ? player.getName() : "unknown"));
        }
    }

    private String[] split(String line) {
        int cutPoint = LineLimits.getLineLimit();
        if (line.length() <= 32) {
            cutPoint = line.length() / 2;
        }
        if (line.length() <= cutPoint || line.length() == 0) {
            return new String[] { line, "" };
        }

        String prefix = line.substring(0, cutPoint);
        String suffix = line.substring(cutPoint);

        if (prefix.endsWith("§")) {
            prefix = ScoreboardStrings.removeLastCharacter(prefix);
            suffix = "§" + suffix;
        } else if (prefix.contains("§")) {
            suffix = ChatColor.getLastColors(prefix) + suffix;
        } else {
            suffix = "§f" + suffix;
        }

        if (suffix.length() > LineLimits.getLineLimit()) {
            suffix = suffix.substring(0, LineLimits.getLineLimit());
        }

        return new String[] { prefix, suffix };
    }

    private boolean shouldUpdate(int line, String content) {
        if (!cache.containsKey(line)) {
            cache.put(line, content);
            return true;
        }

        if (cache.get(line).equals(content)) {
            return false;
        }

        cache.put(line, content);
        return true;
    }

    @Override
    public void setLineCount(int lines) {
        this.lines = lines;
        createTeams();
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    private boolean isValid() {
        if (!initialized) {
            Logger.trace("Board not initialized for player " + (player != null ? player.getName() : "unknown"));
            return false;
        }
        if (player == null || !player.isOnline()) {
            Logger.trace("Player is offline or null for board");
            return false;
        }
        if (board == null || objective == null) {
            Logger.trace("Board or objective is null for player " + player.getName());
            return false;
        }
        return true;
    }

    private void createTeams() {
        if (!isValid()) return;

        try {
            int score = this.lines;

            for (int i = 0; i < this.lines; i++) {
                if (i >= ChatColor.values().length) break;
                
                Team team = board.getTeam(i + "");
                if (team == null) {
                    try {
                        Team t = this.board.registerNewTeam(i + "");
                        t.addEntry(ChatColor.values()[i] + "");
                        this.objective.getScore(ChatColor.values()[i] + "").setScore(score);
                    } catch (Throwable tr) {
                        Logger.error("Failed to create team for line " + i + ": " + tr.getMessage());
                    }
                } else {
                    try {
                        this.objective.getScore(ChatColor.values()[i] + "").setScore(score);
                    } catch (Throwable tr) {
                        Logger.error("Failed to set score for line " + i + ": " + tr.getMessage());
                    }
                }
                score--;
            }
            
            try {
                if (board.getTeams() != null) {
                    for (int i = board.getTeams().size() - 1; i >= this.lines; i--) {
                        Team team = board.getTeam(i + "");
                        if (team != null) {
                            try {
                                team.unregister();
                            } catch (Throwable tr) {
                                Logger.error("Failed to unregister team: " + tr.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Logger.error("Error cleaning up teams: " + e.getMessage());
            }
            
        } catch (Exception e) {
            Logger.error("Error in createTeams for player " + (player != null ? player.getName() : "unknown") + ": " + e.getMessage());
        }
    }

    private void setBoard() {
        if (isValid()) {
            try {
                this.player.setScoreboard(this.board);
            } catch (Exception e) {
                Logger.error("Failed to set board for player " + player.getName());
            }
        }
    }

    @Override
    public void setObjective(String objectiveName) {
        // Not needed
    }

    public boolean hasTeamEntry(String invisTeamName) {
        return isValid() && this.board.getTeam(invisTeamName) != null;
    }

    public Team addTeam(String invisTeamName, ChatColor chatColor) {
        if (!isValid()) return null;
        return this.board.registerNewTeam(invisTeamName);
    }

    public Optional<Team> getTeamEntry(String invisTeamName) {
        if (!isValid()) return Optional.empty();
        return Optional.ofNullable(this.board.getTeam(invisTeamName));
    }

    public Team getTeamOrRegister(String invisTeamName) {
        if (!isValid()) return null;
        Team t = this.board.getTeam(invisTeamName);
        if (t == null) {
            try {
                t = addTeam(invisTeamName, ChatColor.GRAY);
            } catch (Throwable t_) {
                t_.printStackTrace();
                return null;
            }
        }
        return t;
    }
}
