package com.hcmultichar.database;

import com.hcmultichar.models.CharacterInfo;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CharacterRepository {

    private static final Logger LOGGER = Logger.getLogger("HC_MultiChar");
    private final HikariDataSource dataSource;

    public CharacterRepository() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://postgres:5432/factionwars");
        config.setUsername("factionwars");
        config.setPassword("factionwars_secret");
        config.setMaximumPoolSize(5);
        config.setPoolName("HC_MultiChar");
        config.setConnectionTimeout(5000);
        config.setDriverClassName("org.postgresql.Driver");
        this.dataSource = new HikariDataSource(config);
    }

    public void initialize() {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS mc_characters (" +
                    "character_uuid UUID PRIMARY KEY, " +
                    "account_uuid UUID NOT NULL, " +
                    "slot SMALLINT NOT NULL CHECK (slot BETWEEN 0 AND 2), " +
                    "character_name VARCHAR(32), " +
                    "created_at TIMESTAMPTZ DEFAULT NOW(), " +
                    "last_played_at TIMESTAMPTZ DEFAULT NOW(), " +
                    "play_time_secs BIGINT DEFAULT 0, " +
                    "deleted BOOLEAN DEFAULT FALSE, " +
                    "UNIQUE (account_uuid, slot)" +
                    ")")) {
                stmt.execute();
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS mc_active_character (" +
                    "account_uuid UUID PRIMARY KEY, " +
                    "active_slot SMALLINT NOT NULL DEFAULT 0" +
                    ")")) {
                stmt.execute();
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_mc_characters_account ON mc_characters(account_uuid)")) {
                stmt.execute();
            }
            LOGGER.info("MultiChar tables initialized");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize MultiChar tables", e);
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * Get all (non-deleted) characters for an account.
     */
    public List<CharacterInfo> getCharacters(UUID accountUuid) {
        List<CharacterInfo> chars = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT character_uuid, account_uuid, slot, character_name, " +
                 "created_at, last_played_at, play_time_secs " +
                 "FROM mc_characters WHERE account_uuid = ? AND deleted = FALSE " +
                 "ORDER BY slot")) {
            stmt.setObject(1, accountUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    chars.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load characters for " + accountUuid, e);
        }
        return chars;
    }

    /**
     * Get a specific character by account + slot.
     */
    public CharacterInfo getCharacter(UUID accountUuid, int slot) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT character_uuid, account_uuid, slot, character_name, " +
                 "created_at, last_played_at, play_time_secs " +
                 "FROM mc_characters WHERE account_uuid = ? AND slot = ? AND deleted = FALSE")) {
            stmt.setObject(1, accountUuid);
            stmt.setInt(2, slot);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to load character for " + accountUuid + " slot " + slot, e);
        }
        return null;
    }

    /**
     * Get the active slot for an account (defaults to 0 if not set).
     */
    public int getActiveSlot(UUID accountUuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT active_slot FROM mc_active_character WHERE account_uuid = ?")) {
            stmt.setObject(1, accountUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get active slot for " + accountUuid, e);
        }
        return 0;
    }

    /**
     * Set the active slot for an account.
     */
    public void setActiveSlot(UUID accountUuid, int slot) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO mc_active_character (account_uuid, active_slot) VALUES (?, ?) " +
                 "ON CONFLICT (account_uuid) DO UPDATE SET active_slot = EXCLUDED.active_slot")) {
            stmt.setObject(1, accountUuid);
            stmt.setInt(2, slot);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to set active slot for " + accountUuid, e);
        }
    }

    /**
     * Create a new character in a slot.
     */
    public CharacterInfo createCharacter(UUID accountUuid, int slot, String characterName, UUID characterUuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO mc_characters (character_uuid, account_uuid, slot, character_name) " +
                 "VALUES (?, ?, ?, ?) " +
                 "ON CONFLICT (account_uuid, slot) DO NOTHING")) {
            stmt.setObject(1, characterUuid);
            stmt.setObject(2, accountUuid);
            stmt.setInt(3, slot);
            stmt.setString(4, characterName);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                return new CharacterInfo(characterUuid, accountUuid, slot, characterName,
                    Instant.now(), Instant.now(), 0);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to create character for " + accountUuid + " slot " + slot, e);
        }
        return null;
    }

    /**
     * Soft-delete a character.
     */
    public boolean deleteCharacter(UUID accountUuid, int slot) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE mc_characters SET deleted = TRUE WHERE account_uuid = ? AND slot = ? AND deleted = FALSE")) {
            stmt.setObject(1, accountUuid);
            stmt.setInt(2, slot);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete character for " + accountUuid + " slot " + slot, e);
        }
        return false;
    }

    /**
     * Update last_played_at timestamp.
     */
    public void touchLastPlayed(UUID characterUuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE mc_characters SET last_played_at = NOW() WHERE character_uuid = ?")) {
            stmt.setObject(1, characterUuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to touch last_played for " + characterUuid, e);
        }
    }

    /**
     * Add play time to a character.
     */
    public void addPlayTime(UUID characterUuid, long additionalSecs) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE mc_characters SET play_time_secs = play_time_secs + ?, last_played_at = NOW() " +
                 "WHERE character_uuid = ?")) {
            stmt.setLong(1, additionalSecs);
            stmt.setObject(2, characterUuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to add play time for " + characterUuid, e);
        }
    }

    /**
     * Ensure a character exists for slot 0 (auto-provision for first-time players).
     * Returns the character UUID for slot 0.
     */
    public UUID ensureDefaultCharacter(UUID accountUuid, String playerName) {
        UUID charUuid = characterUuid(accountUuid, 0);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO mc_characters (character_uuid, account_uuid, slot, character_name) " +
                 "VALUES (?, ?, 0, ?) ON CONFLICT (account_uuid, slot) DO NOTHING")) {
            stmt.setObject(1, charUuid);
            stmt.setObject(2, accountUuid);
            stmt.setString(3, playerName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to ensure default character for " + accountUuid, e);
        }
        // Also ensure active_character entry
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO mc_active_character (account_uuid, active_slot) VALUES (?, 0) " +
                 "ON CONFLICT (account_uuid) DO NOTHING")) {
            stmt.setObject(1, accountUuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to ensure active character for " + accountUuid, e);
        }
        return charUuid;
    }

    /**
     * Deterministic character UUID from account UUID + slot.
     */
    public static UUID characterUuid(UUID accountUuid, int slot) {
        return UUID.nameUUIDFromBytes(
            (accountUuid.toString() + ":" + slot).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Resolve the active character UUID for an account.
     * Returns the account UUID itself if no character system entry exists (pre-migration fallback).
     */
    public UUID resolveActiveCharacterUuid(UUID accountUuid) {
        int slot = getActiveSlot(accountUuid);
        CharacterInfo info = getCharacter(accountUuid, slot);
        if (info != null) return info.getCharacterUuid();
        // Fallback: use account UUID directly (pre-migration player)
        return accountUuid;
    }

    private CharacterInfo mapRow(ResultSet rs) throws SQLException {
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp lastPlayedTs = rs.getTimestamp("last_played_at");
        return new CharacterInfo(
            rs.getObject("character_uuid", UUID.class),
            rs.getObject("account_uuid", UUID.class),
            rs.getInt("slot"),
            rs.getString("character_name"),
            createdTs != null ? createdTs.toInstant() : null,
            lastPlayedTs != null ? lastPlayedTs.toInstant() : null,
            rs.getLong("play_time_secs")
        );
    }
}
