package com.hcmultichar.models;

import java.time.Instant;
import java.util.UUID;

public class CharacterInfo {
    private final UUID characterUuid;
    private final UUID accountUuid;
    private final int slot;
    private String characterName;
    private Instant createdAt;
    private Instant lastPlayedAt;
    private long playTimeSecs;

    // Denormalized display fields (populated from other plugin tables)
    private int level;
    private String className;
    private String factionName;
    private String guildName;
    private String honorRank;

    public CharacterInfo(UUID characterUuid, UUID accountUuid, int slot, String characterName,
                         Instant createdAt, Instant lastPlayedAt, long playTimeSecs) {
        this.characterUuid = characterUuid;
        this.accountUuid = accountUuid;
        this.slot = slot;
        this.characterName = characterName;
        this.createdAt = createdAt;
        this.lastPlayedAt = lastPlayedAt;
        this.playTimeSecs = playTimeSecs;
    }

    public UUID getCharacterUuid() { return characterUuid; }
    public UUID getAccountUuid() { return accountUuid; }
    public int getSlot() { return slot; }
    public String getCharacterName() { return characterName; }
    public void setCharacterName(String characterName) { this.characterName = characterName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastPlayedAt() { return lastPlayedAt; }
    public void setLastPlayedAt(Instant lastPlayedAt) { this.lastPlayedAt = lastPlayedAt; }
    public long getPlayTimeSecs() { return playTimeSecs; }
    public void setPlayTimeSecs(long playTimeSecs) { this.playTimeSecs = playTimeSecs; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getFactionName() { return factionName; }
    public void setFactionName(String factionName) { this.factionName = factionName; }
    public String getGuildName() { return guildName; }
    public void setGuildName(String guildName) { this.guildName = guildName; }
    public String getHonorRank() { return honorRank; }
    public void setHonorRank(String honorRank) { this.honorRank = honorRank; }

    public String getPlayTimeFormatted() {
        long hours = playTimeSecs / 3600;
        long minutes = (playTimeSecs % 3600) / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    public String getLastPlayedFormatted() {
        if (lastPlayedAt == null) return "Never";
        long secondsAgo = Instant.now().getEpochSecond() - lastPlayedAt.getEpochSecond();
        if (secondsAgo < 60) return "Just now";
        if (secondsAgo < 3600) return (secondsAgo / 60) + "m ago";
        if (secondsAgo < 86400) return (secondsAgo / 3600) + "h ago";
        return (secondsAgo / 86400) + "d ago";
    }
}
