package com.flyaway.timereward;

import net.ess3.api.events.AfkStatusChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final TimeReward plugin;
    private final Map<UUID, Long> joinTimes; // Только для активных игроков (не AFK)

    public PlayerListener(TimeReward plugin) {
        this.plugin = plugin;
        this.joinTimes = new HashMap<>();
    }

    public void initializeOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();

            // Инициализируем данные игрока если их нет
            plugin.getOrCreatePlayerData(playerId);

            // Добавляем в joinTimes только если игрок не в AFK
            if (!plugin.isAfk(player)) {
                joinTimes.put(playerId, System.currentTimeMillis() / 1000);
                plugin.getLogger().info("Игрок " + player.getName() + " инициализирован как активный");
            } else {
                plugin.getLogger().info("Игрок " + player.getName() + " пропущен (AFK)");
            }
        }
        plugin.getLogger().info("Инициализировано " + joinTimes.size() + " онлайн-игроков");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Запоминаем время входа
        joinTimes.put(playerId, System.currentTimeMillis() / 1000);

        plugin.getOrCreatePlayerData(playerId);
        plugin.getLogger().info("Данные загружены для игрока: " + player.getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        updatePlayerSessionTime(playerId); // Обновляем время при выходе
        joinTimes.remove(playerId); // Удаляем из карты сессий

        plugin.savePlayerData(playerId);
        plugin.getLogger().info("Данные сохранены при выходе игрока: " + player.getName());
    }

    // Метод для обновления времени сессии
    public void updatePlayerSessionTime(UUID playerId) {
        Long joinTime = joinTimes.get(playerId);
        if (joinTime != null) {
            long sessionTime = (System.currentTimeMillis() / 1000) - joinTime;
            PlayerData data = plugin.getPlayerData(playerId);
            if (data != null) {
                data.setTotalTime(data.getTotalTime() + sessionTime);
                data.setPeriodTime(data.getPeriodTime() + sessionTime);
            }
            joinTimes.put(playerId, System.currentTimeMillis() / 1000); // Сбрасываем время входа
        }
    }

    @EventHandler
    public void onAfkStatusChange(AfkStatusChangeEvent event) {
        Player player = event.getAffected().getBase();
        UUID playerId = player.getUniqueId();
        boolean isAfk = event.getValue(); // true = стал AFK, false = перестал быть AFK

        if (isAfk) {
            // Игрок ушел в AFK - сохраняем сессию и удаляем из joinTimes
            if (joinTimes.containsKey(playerId)) {
                updatePlayerSessionTime(playerId);
                joinTimes.remove(playerId);
                plugin.getLogger().info("Игрок " + player.getName() + " ушел в AFK, сессия сохранена");
            }
        } else {
            // Игрок вышел из AFK - добавляем в joinTimes только если онлайн
            if (player.isOnline() && !joinTimes.containsKey(playerId)) {
                joinTimes.put(playerId, System.currentTimeMillis() / 1000);
                plugin.getLogger().info("Игрок " + player.getName() + " вышел из AFK, сессия возобновлена");
            }
        }
    }
}
