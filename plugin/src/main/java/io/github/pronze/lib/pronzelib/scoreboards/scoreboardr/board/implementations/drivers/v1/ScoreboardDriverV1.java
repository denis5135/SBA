package io.github.pronze.lib.pronzelib.scoreboards.scoreboardr.board.implementations.drivers.v1;
//https://github.com/RienBijl/Scoreboard-revision/blob/master/src/main/java/rien/bijl/Scoreboard/r/Board/Implementations/Drivers/V1/ScoreboardDriverV1.java

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

    @Override
    public void setPlayer(Player player) {
        this.player = player;

        this.board = Objects.requireNonNull(Session.getSession().plugin.getServer().getScoreboardManager())
                .getNewScoreboard();
        this.objective = this.board.registerNewObjective(objectiveName, "dummy");
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        this.objective.setDisplayName("");

        this.createTeams();
        this.setBoard();

        LineLimits.getLineLimit();
    }

    @Override
    public void setTitle(String title) {
        if (title == null) {
            title = "";
        }

        if (title.length() > LineLimits.getLineLimit() * 2) {
            title = title.substring(0, LineLimits.getLineLimit() * 2);
        }

        this.objective.setDisplayName(title);
    }

    @Override
    public void setLine(int line, String content) {
        if (content == null) {
            content = "";
        }
        if (!shouldUpdate(line, content)) {
            return;
        }

        Team team = board.getTeam(line + "");
        String[] split = split(content);

        assert team != null;

        team.setPrefix(split[0]);
        team.setSuffix(split[1]);
    }

    private String[] split(String line) {
        int cutPoint = LineLimits.getLineLimit();
        if (line.length() <= 32) {
            cutPoint = line.length() / 2;
        }
        ;
        if (line.length() <= cutPoint || line.length() == 0) {
            return new String[] { line, "" };
        }

        String prefix = line.substring(0, cutPoint);
        String suffix = line.substring(cutPoint);

        if (prefix.endsWith("§")) { // Check if we accidentally cut off a color
            prefix = ScoreboardStrings.removeLastCharacter(prefix);
            suffix = "§" + suffix;
        } else if (prefix.contains("§")) { // Are there any colors we need to continue?
            suffix = ChatColor.getLastColors(prefix) + suffix;
        } else { // Just make sure the team color doesn't mess up anything
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
        return this.getPlayer();
    }

    private void createTeams() {
        if (board != null) {
            int score = this.lines;

            for (int i = 0; i < this.lines; i++) {
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
            
            // ДОБАВЛЕНА ПРОВЕРКА НА NULL
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
            } else {
                Logger.warn("board.getTeams() returned null for player " + player.getName());
            }
        } else {
            Logger.warn("Board is null in createTeams() for player " + (player != null ? player.getName() : "unknown"));
        }
    }

    private void setBoard() {
        if (this.player != null && this.board != null) {
            this.player.setScoreboard(this.board);
        }
    }

    @Override
    public void setObjective(String objectiveName) {
        if (objectiveName == null)
            objectiveName = "sbascoreboard";
        /*
         * if (this.board != null)
         * {
         * this.objective = this.board.registerNewObjective(objectiveName, "dummy");
         * this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
         * this.objective.setDisplayName("");
         * }
         * else
         * this.objectiveName = objectiveName;
         */
    }

    public boolean hasTeamEntry(String invisTeamName) {
        return this.board != null && this.board.getTeam(invisTeamName) != null;
    }

    public Team addTeam(String invisTeamName, ChatColor chatColor) {
        if (this.board == null) return null;
        Team t = this.board.registerNewTeam(invisTeamName);
        // t.setColor(chatColor);
        return t;
    }

    public Optional<Team> getTeamEntry(String invisTeamName) {
        if (this.board == null) return Optional.empty();
        return Optional.ofNullable(this.board.getTeam(invisTeamName));
    }

    public Team getTeamOrRegister(String invisTeamName) {
        if (this.board == null) return null;
        Team t = this.board.getTeam(invisTeamName);
        if (t == null)
            try {
                t = addTeam(invisTeamName, ChatColor.GRAY);
            } catch (Throwable t_) {
                t_.printStackTrace();
                return null;
            }
        return t;
    }
}
