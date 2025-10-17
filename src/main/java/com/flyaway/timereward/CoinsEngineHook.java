package com.flyaway.timereward;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;

public class CoinsEngineHook {
    private JavaPlugin plugin;
    private boolean enabled = false;

    public CoinsEngineHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setupCoinsEngine() {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("CoinsEngine") == null) {
                plugin.getLogger().info("CoinsEngine не найден");
                return false;
            }

            enabled = true;
            plugin.getLogger().info("Успешная интеграция с CoinsEngine API");
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при подключении к CoinsEngine: " + e.getMessage());
            return false;
        }
    }

    public String getCurrencySymbol(String currencyType) {
        if (!enabled) return currencyType;

        Currency currency = CoinsEngineAPI.getCurrency(currencyType);
        if (currency == null) {
            plugin.getLogger().warning("Валюта '" + currencyType + "' не найдена в CoinsEngine");
            return currencyType;
        }
        return currency.getSymbol();
    }

    public boolean depositPlayer(Player player, double amount, String currencyType) {
        if (!enabled) return false;

        try {
            // Получаем валюту по ID
            Currency currency = CoinsEngineAPI.getCurrency(currencyType);
            if (currency == null) {
                plugin.getLogger().warning("Валюта '" + currencyType + "' не найдена в CoinsEngine");
                return false;
            }

            // Добавляем баланс игроку
            CoinsEngineAPI.addBalance(player, currency, amount);

            plugin.getLogger().info("Выдано " + amount + " " + currency.getName() + " игроку " + player.getName());
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при выдаче валюты игроку " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
