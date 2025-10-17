package com.flyaway.timereward;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import com.flyaway.timereward.TimeReward.CurrencyConfig;

import java.util.*;
import java.util.stream.Collectors;

public class TimeRewardCommand implements CommandExecutor, TabCompleter {
    private final TimeReward plugin;
    private final List<String> mainCommands = Arrays.asList("reload", "stats", "help");
    private final List<String> adminCommands = Arrays.asList("reload", "stats");
    private final List<String> playerCommands = Arrays.asList("stats");

    public TimeRewardCommand(TimeReward plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("timereward.admin")) {
                    sender.sendMessage(ChatColor.RED + "Недостаточно прав!");
                    return true;
                }
                plugin.reloadPluginConfig();
                sender.sendMessage(ChatColor.GREEN + "Конфиг перезагружен!");
                break;

            case "stats":
                if (args.length == 1 && sender instanceof Player) {
                    showStats(sender, (Player) sender);
                } else if (args.length == 2 && sender.hasPermission("timereward.admin")) {
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target != null) {
                        showStats(sender, target);
                    } else {
                        sender.sendMessage(ChatColor.RED + "Игрок не найден или не в сети!");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Использование: /timereward stats [игрок]");
                }
                break;

            case "help":
                sendHelp(sender);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Неизвестная команда. Используйте /timereward help");
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> availableCommands = new ArrayList<>();
            if (sender.hasPermission("timereward.admin")) availableCommands.addAll(adminCommands);
            if (sender instanceof Player) availableCommands.addAll(playerCommands);
            availableCommands = availableCommands.stream().distinct().collect(Collectors.toList());
            availableCommands.add("help");

            StringUtil.copyPartialMatches(args[0], availableCommands, completions);
            Collections.sort(completions);

        } else if (args.length == 2 && args[0].equalsIgnoreCase("stats") && sender.hasPermission("timereward.admin")) {
            String partialName = args[1];
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            StringUtil.copyPartialMatches(partialName, playerNames, completions);
            Collections.sort(completions);
        }
        return completions;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== TimeReward Команды ===");
        if (sender.hasPermission("timereward.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/timereward reload - Перезагрузить конфиг");
            sender.sendMessage(ChatColor.YELLOW + "/timereward stats [игрок] - Статистика игрока");
        }
        if (sender instanceof Player) {
            sender.sendMessage(ChatColor.YELLOW + "/timereward stats - Ваша статистика");
        }
        sender.sendMessage(ChatColor.YELLOW + "/timereward help - Показать эту помощь");
    }

    private void showStats(CommandSender sender, Player target) {
        PlayerData data = plugin.getPlayerData(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(ChatColor.RED + "Данные игрока не найдены!");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Статистика " + target.getName() + " ===");
        sender.sendMessage(ChatColor.GREEN + "Общее время: " + ChatColor.WHITE + data.getFormattedTotalTime());
        sender.sendMessage(ChatColor.GREEN + "Время за период: " + ChatColor.WHITE + data.getFormattedPeriodTime());

        // Показываем время последней награды для каждой валюты
        for (String currency : data.getLastRewardTimes().keySet()) {
            long lastReward = data.getLastRewardTime(currency);
            CurrencyConfig currencyConfig = plugin.getCurrencyConfig(currency);
            String currencyName = currencyConfig != null ? currencyConfig.getCurrencySymbol() : currency;
            sender.sendMessage(ChatColor.GREEN + "Последняя награда " + currencyName + ": " +
                             ChatColor.WHITE + formatTime(lastReward));
        }
    }

    private String formatTime(long timestamp) {
        if (timestamp == 0) return "никогда";
        long diff = (System.currentTimeMillis() / 1000) - timestamp;
        if (diff < 60) return diff + " сек назад";
        if (diff < 3600) return (diff / 60) + " мин назад";
        if (diff < 86400) return (diff / 3600) + " ч назад";
        return (diff / 86400) + " дн назад";
    }
}
