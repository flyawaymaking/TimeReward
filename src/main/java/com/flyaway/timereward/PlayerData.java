package com.flyaway.timereward;

import java.util.HashMap;
import java.util.Map;

public class PlayerData {
    private long totalTime;
    private long periodTime;
    private Map<String, Long> lastRewardTimes;

    public PlayerData(long totalTime, long periodTime, Map<String, Long> lastRewardTimes) {
        this.totalTime = totalTime;
        this.periodTime = periodTime;
        this.lastRewardTimes = lastRewardTimes != null ? new HashMap<>(lastRewardTimes) : new HashMap<>();
    }

    public long getTotalTime() {
        return totalTime;
    }

    public void setTotalTime(long totalTime) {
        this.totalTime = totalTime;
    }

    public long getPeriodTime() {
        return periodTime;
    }

    public void setPeriodTime(long periodTime) {
        this.periodTime = periodTime;
    }

    public Long getLastRewardTime(String currencyType) {
        return lastRewardTimes.get(currencyType);
    }

    public void setLastRewardTime(String currencyType, long timestamp) {
        lastRewardTimes.put(currencyType, timestamp);
    }

    public Map<String, Long> getLastRewardTimes() {
        return new HashMap<>(lastRewardTimes);
    }

    public String getFormattedTotalTime() {
        long days = totalTime / 86400;
        long hours = (totalTime % 86400) / 3600;
        long minutes = (totalTime % 3600) / 60;
        long seconds = totalTime % 60;

        if (days > 0) {
            return String.format("%dд %dч %dм %dс", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%dч %dм %dс", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dм %dс", minutes, seconds);
        }
        return String.format("%dс", seconds);
    }

    public String getFormattedPeriodTime() {
        long days = periodTime / 86400;
        long hours = (periodTime % 86400) / 3600;
        long minutes = (periodTime % 3600) / 60;
        long seconds = periodTime % 60;

        if (days > 0) {
            return String.format("%dд %dч %dм %dс", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%dч %dм %dс", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dм %dс", minutes, seconds);
        }
        return String.format("%dс", seconds);
    }
}
