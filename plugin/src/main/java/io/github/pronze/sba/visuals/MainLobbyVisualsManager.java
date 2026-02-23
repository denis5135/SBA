    public void create(Player player) {
        if (!enabled)
            return;
        if (!isInWorld(player.getLocation()))
            return;

        final var playerData = SBA.getInstance().getPlayerWrapperService().get(player).orElseThrow();

        if (SBAConfig.getInstance().node("main-lobby", "tablist-modifications").getBoolean()) {
            var header = LanguageService.getInstance().get(MessageKeys.MAIN_LOBBY_TABLIST_HEADER)
                    .replace("%sba_version%", SBA.getInstance().getVersion()).toComponent();

            var footer = LanguageService.getInstance().get(MessageKeys.MAIN_LOBBY_TABLIST_FOOTER)
                    .replace("%sba_version%", SBA.getInstance().getVersion()).toComponent();
            playerData.sendPlayerListHeaderFooter(header, footer);
        }

        // Анимированный заголовок "Bedwars" - используем setAnimatedTitle вместо title
        List<String> animatedTitle = new ArrayList<>();
        animatedTitle.add("&6&lB&e&led&6&lw&e&lar&6&ls");
        animatedTitle.add("&e&lB&6&led&e&lw&6&lar&e&ls");
        animatedTitle.add("&6&lBe&e&ldw&6&lar&e&ls");
        animatedTitle.add("&e&lBed&6&lw&e&lar&e&ls");
        animatedTitle.add("&6&lBedw&e&lar&6&ls");
        animatedTitle.add("&e&lBedwa&6&lr&e&ls");
        animatedTitle.add("&6&lBedwar&e&ls");
        animatedTitle.add("&e&lBedwars");

        final var scoreboard = Scoreboard.builder()
                .animate(true)
                .player(player)
                .title(animatedTitle.get(0)) // Обычный заголовок
                .displayObjective(MAIN_LOBBY_OBJECTIVE)
                .updateInterval(20L)
                .lines(getScoreboardLines())
                .placeholderHook(hook -> {
                    // Используем нашу новую систему уровней
                    var levelManager = PlayerLevelManager.getInstance();
                    int playerLevel = levelManager.getPlayerLevel(player);
                    String playerPrefix = levelManager.getPlayerPrefix(player);
                    int playerXP = levelManager.getPlayerXP(player);
                    int xpToNext = levelManager.getXPToNextLevel(player);
                    double progress = levelManager.getLevelProgress(player);

                    // Создаём прогресс-бар (10 символов)
                    int barLength = 10;
                    int filledBars = (int) Math.round(progress * barLength);
                    StringBuilder bar = new StringBuilder("§a");
                    for (int i = 0; i < barLength; i++) {
                        if (i < filledBars) {
                            bar.append("■");
                        } else {
                            bar.append("§7■");
                        }
                    }

                    final var playerStatistic = Main.getPlayerStatisticsManager().getStatistic(player);

                    // Поражения = всего игр - победы (или другое вычисление)
                    int totalGames = playerStatistic.getWins() + playerStatistic.getDeaths(); // или другое
                    int losses = totalGames - playerStatistic.getWins();
                    double winRate = totalGames > 0 ? (double) playerStatistic.getWins() / totalGames * 100 : 0;

                    return hook.getLine()
                            .replace("%sba_version%", SBA.getInstance().getVersion())
                            .replace("%kills%", String.valueOf(playerStatistic.getKills()))
                            .replace("%beds%", String.valueOf(playerStatistic.getDestroyedBeds()))
                            .replace("%deaths%", String.valueOf(playerStatistic.getDeaths()))
                            // Уровни
                            .replace("%level%", playerPrefix + " " + playerLevel + "✫")
                            .replace("%xp%", String.valueOf(playerXP))
                            .replace("%xp_required%", String.valueOf(xpToNext))
                            .replace("%progress%", String.valueOf((int) (progress * 100)) + "%")
                            .replace("%bar%", bar.toString())
                            // Победы/поражения
                            .replace("%wins%", String.valueOf(playerStatistic.getWins()))
                            .replace("%losses%", String.valueOf(losses))
                            .replace("%games%", String.valueOf(totalGames))
                            .replace("%winrate%", String.format("%.1f", winRate) + "%")
                            // K/D
                            .replace("%kdr%", String.valueOf(playerStatistic.getKD()))
                            // Привилегия (заглушка, можно заменить на реальную систему доната)
                            .replace("%rank%", "&6[VIP]")
                            .replace("%donate%", "&6[VIP]");
                })
                .build();

        // Устанавливаем анимированный заголовок отдельно
        scoreboard.setAnimatedTitle(animatedTitle);

        scoreboardMap.put(player, scoreboard);
    }

    private List<String> getScoreboardLines() {
        List<String> lines = new ArrayList<>();

        // Твоя привилегия
        lines.add("&6%rank%");
        // Пустая строка
        lines.add("");
        // Уровень
        lines.add("&7Уровень: %level%");
        // Опыт
        lines.add("&7Опыт: %xp%/%xp_required%");
        // Прогресс-бар
        lines.add("%bar%");
        // Пустая строка
        lines.add("");
        // Статистика
        lines.add("&7Всего убийств: &a%kills%");
        lines.add("&7Всего побед: &a%wins%");
        lines.add("&7Всего кроватей: &a%beds%");
        lines.add("&7У/С: &a%kdr%");
        lines.add("&7В/П: &a%wins%&7/&c%losses% &8(%winrate%)");
        lines.add("&7Всего игр: &a%games%");
        // Пустая строка
        lines.add("");
        // Айпи
        lines.add("&bplay.yourserver.com");

        return lines;
    }
