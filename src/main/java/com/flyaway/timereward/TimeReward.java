package com.flyaway.timereward;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public class TimeReward extends JavaPlugin {

    private CoinsEngineHook coinsEngine;
    private Map<UUID, PlayerData> playerDataMap;
    private File dataFile;
    private YamlConfiguration dataConfig;
    private Object essentials;
    private PlayerListener playerListener;

    private long checkInterval;
    private Map<String, CurrencyConfig> currencyConfigs;
    private boolean requireAfkCheck;
    private boolean broadcastRewards;
    private String rewardMessage;
    private BukkitTask rewardTimerTask;
    private BukkitTask saveTask;
    private boolean debug;

    public static class CurrencyConfig {
        private final long rewardInterval;
        private final double rewardDefault;
        private final String currencyId;
        private final String currencySymbol;

        public CurrencyConfig(String currencyId, long rewardInterval, double rewardDefault, String currencySymbol) {
            this.currencyId = currencyId;
            this.rewardInterval = rewardInterval * 60;
            this.rewardDefault = rewardDefault;
            this.currencySymbol = currencySymbol;
        }

        public long getRewardInterval() { return rewardInterval; }
        public double getRewardDefault() { return rewardDefault; }
        public String getCurrencyId() { return currencyId; }
        public String getCurrencySymbol() { return currencySymbol; }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDataFile();

        coinsEngine = new CoinsEngineHook(this);
        if (!coinsEngine.setupCoinsEngine()) {
            getLogger().severe("CoinsEngine не найден! Плагин будет отключен.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadConfig();

        playerDataMap = new HashMap<>();

        essentials = getServer().getPluginManager().getPlugin("Essentials");
        if (essentials == null) {
            getLogger().warning("EssentialsX не найден, AFK проверка отключена");
        } else {
            getLogger().info("EssentialsX найден, AFK проверка активна");
        }

        playerListener = new PlayerListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        playerListener.initializeOnlinePlayers();

        TimeRewardCommand commandExecutor = new TimeRewardCommand(this);
        getCommand("timereward").setExecutor(commandExecutor);
        getCommand("timereward").setTabCompleter(commandExecutor);

        startRewardTimer();
        startSaveTask();

        getLogger().info("TimeReward плагин включен!");
    }

    @Override
    public void onDisable() {
        if (rewardTimerTask != null) rewardTimerTask.cancel();
        if (saveTask != null) saveTask.cancel();

        for (Player player : Bukkit.getOnlinePlayers()) {
            playerListener.updatePlayerSessionTime(player.getUniqueId());
        }
        savePlayersData();
        getLogger().info("TimeReward плагин выключен!");
    }

    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "playerdata.yml");
        if (!dataFile.exists()) saveResource("playerdata.yml", false);
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();

        checkInterval = config.getLong("settings.check-interval", 60) * 20L;
        requireAfkCheck = config.getBoolean("settings.require-afk-check", true);
        broadcastRewards = config.getBoolean("settings.broadcast-rewards", false);
        debug = config.getBoolean("debug", false);
        rewardMessage = ChatColor.translateAlternateColorCodes('&',
            config.getString("messages.reward-message", "&aВы получили &6{amount} {currency} &aза время на сервере!"));

        currencyConfigs = new HashMap<>();
        if (config.contains("settings.currencies")) {
            for (String currencyKey : config.getConfigurationSection("settings.currencies").getKeys(false)) {
                String path = "settings.currencies." + currencyKey + ".";
                long interval = config.getLong(path + "reward-interval", 60);
                double defaultValue = config.getDouble(path + "reward-default", 1.0);

                // Получаем символ валюты из CoinsEngine
                String symbol = coinsEngine.getCurrencySymbol(currencyKey);

                currencyConfigs.put(currencyKey, new CurrencyConfig(currencyKey, interval, defaultValue, symbol));
            }
        }

        if (currencyConfigs.isEmpty()) {
            getLogger().warning("Не найдено ни одной валюты в конфиге!");
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
        loadConfig();
        getLogger().info("Конфигурация плагина перезагружена");
    }

    public PlayerListener getPlayerListener() {
        return playerListener;
    }

    public void savePlayersData() {
        synchronized (playerDataMap) {
            for (Map.Entry<UUID, PlayerData> entry : playerDataMap.entrySet()) {
                savePlayerDataToFile(entry.getKey(), entry.getValue());
            }
        }
        if (debug) getLogger().info("Данные всех онлайн игроков сохранены");
    }

    public void savePlayerData(UUID uuid) {
        synchronized (playerDataMap) {
            PlayerData data = playerDataMap.get(uuid);
            if (data != null) {
                savePlayerDataToFile(uuid, data);
            }
        }
    }

    private void savePlayerDataToFile(UUID uuid, PlayerData data) {
        String basePath = "players." + uuid.toString() + ".";
        dataConfig.set(basePath + "totalTime", data.getTotalTime());
        dataConfig.set(basePath + "periodTime", data.getPeriodTime());

        dataConfig.set(basePath + "lastRewardTimes", null);
        for (Map.Entry<String, Long> entry : data.getLastRewardTimes().entrySet()) {
            dataConfig.set(basePath + "lastRewardTimes." + entry.getKey(), entry.getValue());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Ошибка при сохранении данных игрока " + uuid + ": " + e.getMessage());
        }
    }

    public void removePlayerDataFromMemory(UUID uuid) {
        synchronized (playerDataMap) {
            playerDataMap.remove(uuid);
        }
    }

    private void startRewardTimer() {
        this.rewardTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkAndRewardPlayer(player);
                }
            }
        }.runTaskTimer(this, checkInterval, checkInterval);
    }

    private void startSaveTask() {
        this.saveTask = new BukkitRunnable() {
            @Override
            public void run() {
                synchronized (playerDataMap) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        playerListener.updatePlayerSessionTime(player.getUniqueId());
                    }
                    savePlayersData();
                }
            }
        }.runTaskTimerAsynchronously(this, 20 * 60 * 10, 20 * 60 * 10);
    }

    private void checkAndRewardPlayer(Player player) {
        if (requireAfkCheck && isAfk(player)) return;

        UUID playerId = player.getUniqueId();

        // ОБНОВЛЯЕМ время сессии перед проверкой наград
        playerListener.updatePlayerSessionTime(playerId);

        PlayerData data = playerDataMap.get(playerId);
        if (data == null) return;

        for (CurrencyConfig currencyConfig : currencyConfigs.values()) {
            checkCurrencyReward(player, data, currencyConfig);
        }
    }

    private void checkCurrencyReward(Player player, PlayerData data, CurrencyConfig currencyConfig) {
        Long lastRewardPlayTime = data.getLastRewardTime(currencyConfig.getCurrencyId());
        if (lastRewardPlayTime == null) {
            // Первая награда - устанавливаем текущее игровое время как точку отсчета
            lastRewardPlayTime = data.getTotalTime();
            data.setLastRewardTime(currencyConfig.getCurrencyId(), lastRewardPlayTime);
        }

        long playTimeSinceLastReward = data.getTotalTime() - lastRewardPlayTime;
        if (playTimeSinceLastReward >= currencyConfig.getRewardInterval()) {
            double amount = getRewardAmount(player, currencyConfig.getCurrencyId());
            if (amount > 0) {
                giveReward(player, amount, currencyConfig.getCurrencyId());
                // Устанавливаем текущее игровое время как новую точку отсчета
                data.setLastRewardTime(currencyConfig.getCurrencyId(), data.getTotalTime());
            }
        }
    }

    public double getRewardAmount(Player player, String currencyType) {
        CurrencyConfig config = currencyConfigs.get(currencyType);
        if (config == null) return 0;

        double maxAmount = config.getRewardDefault();

        for (PermissionAttachmentInfo permInfo : player.getEffectivePermissions()) {
            String perm = permInfo.getPermission();
            if (perm.startsWith("timereward." + currencyType + ".")) {
                String[] parts = perm.split("\\.");
                if (parts.length == 3) {
                    try {
                        double value = Double.parseDouble(parts[2]);
                        if (value > maxAmount) maxAmount = value;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return maxAmount;
    }

    private void giveReward(Player player, double amount, String currencyType) {
        if (coinsEngine.depositPlayer(player, amount, currencyType)) {
            CurrencyConfig config = currencyConfigs.get(currencyType);
            String currencyName = config != null ? config.getCurrencySymbol() : currencyType;
            String message = rewardMessage
                    .replace("{amount}", String.format("%.0f", amount))
                    .replace("{currency}", currencyName)
                    .replace("{player}", player.getName());

            player.sendMessage(message);

            if (broadcastRewards) {
                Bukkit.broadcastMessage(ChatColor.GREEN + player.getName() +
                        " получил " + amount + " " + currencyName + " за время на сервере!");
            }
        } else {
            getLogger().warning("Не удалось выдать " + currencyType + " игроку " + player.getName());
        }
    }

    public boolean isAfk(Player player) {
        if (essentials == null) return false;

        try {
            Class<?> essentialsClass = essentials.getClass();
            Object user = essentialsClass.getMethod("getUser", Player.class).invoke(essentials, player);
            return (boolean) user.getClass().getMethod("isAfk").invoke(user);
        } catch (Exception e) {
            getLogger().warning("Ошибка при проверке AFK статуса игрока " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public boolean isRequireAfkCheck() {
        return requireAfkCheck;
    }

    public boolean isDebug() {
        return debug;
    }

    public PlayerData getPlayerData(UUID uuid) {
        synchronized (playerDataMap) {
            return playerDataMap.get(uuid);
        }
    }

    public PlayerData getOrCreatePlayerData(UUID uuid) {
        synchronized (playerDataMap) {
            return playerDataMap.computeIfAbsent(uuid, k -> {
                // Загружаем данные из файла только при первом обращении
                return loadPlayerDataFromFile(uuid);
            });
        }
    }

    private PlayerData loadPlayerDataFromFile(UUID uuid) {
        String basePath = "players." + uuid.toString() + ".";

        if (!dataConfig.contains(basePath + "totalTime")) {
            // Если данных нет в файле, создаем новые
            return new PlayerData(0, 0, new HashMap<>());
        }

        Map<String, Long> lastRewardTimes = new HashMap<>();
        if (dataConfig.contains(basePath + "lastRewardTimes")) {
            for (String currency : dataConfig.getConfigurationSection(basePath + "lastRewardTimes").getKeys(false)) {
                lastRewardTimes.put(currency, dataConfig.getLong(basePath + "lastRewardTimes." + currency));
            }
        }

        return new PlayerData(
            dataConfig.getLong(basePath + "totalTime", 0),
            dataConfig.getLong(basePath + "periodTime", 0),
            lastRewardTimes
        );
    }

    // API методы
    public Map<UUID, Long> getAllPlayersTotalTime() {
        Map<UUID, Long> result = new HashMap<>();

        // Сначала добавляем онлайн игроков из памяти
        synchronized (playerDataMap) {
            for (Map.Entry<UUID, PlayerData> entry : playerDataMap.entrySet()) {
                result.put(entry.getKey(), entry.getValue().getTotalTime());
            }
        }

        // Затем добавляем офлайн игроков из файла
        if (dataConfig.contains("players")) {
            for (String key : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    // Добавляем только если игрок не онлайн
                    if (!result.containsKey(uuid)) {
                        String basePath = "players." + key + ".";
                        long totalTime = dataConfig.getLong(basePath + "totalTime", 0);
                        result.put(uuid, totalTime);
                    }
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Неверный UUID в файле данных: " + key);
                }
            }
        }
        return result;
    }

    public Map<UUID, Long> getAllPlayersPeriodTime() {
        Map<UUID, Long> result = new HashMap<>();

        // Сначала добавляем онлайн игроков из памяти
        synchronized (playerDataMap) {
            for (Map.Entry<UUID, PlayerData> entry : playerDataMap.entrySet()) {
                result.put(entry.getKey(), entry.getValue().getPeriodTime());
            }
        }

        // Затем добавляем офлайн игроков из файла
        if (dataConfig.contains("players")) {
            for (String key : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    // Добавляем только если игрок не онлайн
                    if (!result.containsKey(uuid)) {
                        String basePath = "players." + key + ".";
                        long periodTime = dataConfig.getLong(basePath + "periodTime", 0);
                        result.put(uuid, periodTime);
                    }
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Неверный UUID в файле данных: " + key);
                }
            }
        }
        return result;
    }

    public void resetAllPlayersPeriodTime() {
        // Сохраняем текущие данные онлайн игроков
        savePlayersData();

        // Сбрасываем периодическое время для онлайн игроков в памяти
        synchronized (playerDataMap) {
            for (PlayerData data : playerDataMap.values()) {
                data.setPeriodTime(0);
            }
        }

        // Сбрасываем периодическое время для всех игроков в файле
        if (dataConfig.contains("players")) {
            for (String key : dataConfig.getConfigurationSection("players").getKeys(false)) {
                dataConfig.set("players." + key + ".periodTime", 0);
            }
            try {
                dataConfig.save(dataFile);
            } catch (IOException e) {
                getLogger().severe("Ошибка при сбросе периодического времени: " + e.getMessage());
            }
        }

        getLogger().info("Периодическое время всех игроков сброшено");
    }

    public long getPlayerTotalTime(UUID uuid) {
        // Сначала проверяем в памяти (если игрок онлайн)
        synchronized (playerDataMap) {
            PlayerData data = playerDataMap.get(uuid);
            if (data != null) {
                return data.getTotalTime();
            }
        }

        // Если не в памяти, загружаем из файла
        String basePath = "players." + uuid.toString() + ".";
        return dataConfig.getLong(basePath + "totalTime", 0);
    }

    public long getPlayerPeriodTime(UUID uuid) {
        // Сначала проверяем в памяти (если игрок онлайн)
        synchronized (playerDataMap) {
            PlayerData data = playerDataMap.get(uuid);
            if (data != null) {
                return data.getPeriodTime();
            }
        }

        // Если не в памяти, загружаем из файла
        String basePath = "players." + uuid.toString() + ".";
        return dataConfig.getLong(basePath + "periodTime", 0);
    }

    public CurrencyConfig getCurrencyConfig(String currencyType) {
        return currencyConfigs.get(currencyType);
    }
}
