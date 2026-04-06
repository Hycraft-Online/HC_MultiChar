package com.hcmultichar.api;

import com.hcmultichar.database.CharacterRepository;
import com.hcmultichar.models.CharacterInfo;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static API for other plugins to interact with the multi-character system.
 */
public class HC_MultiCharAPI {

    private static volatile CharacterRepository repository;

    /**
     * Players currently in the process of switching characters.
     * Other plugins should check this in their disconnect handlers to skip cleanup.
     */
    public static final Set<UUID> SWITCHING_PLAYERS = ConcurrentHashMap.newKeySet();

    public static void init(CharacterRepository repo) {
        repository = repo;
    }

    public static boolean isAvailable() {
        return repository != null;
    }

    /**
     * Check if a player is currently switching characters.
     * Plugins should call this in disconnect handlers to avoid cleaning up state.
     */
    public static boolean isSwitching(UUID accountUuid) {
        return SWITCHING_PLAYERS.contains(accountUuid);
    }

    /**
     * Get the active character UUID for an account.
     * Returns the account UUID if multi-char is not set up for this player.
     */
    public static UUID getActiveCharacterId(UUID accountUuid) {
        if (repository == null) return accountUuid;
        return repository.resolveActiveCharacterUuid(accountUuid);
    }

    /**
     * Get all characters for an account.
     */
    public static List<CharacterInfo> getCharacters(UUID accountUuid) {
        if (repository == null) return List.of();
        return repository.getCharacters(accountUuid);
    }

    /**
     * Get the active slot number for an account.
     */
    public static int getActiveSlot(UUID accountUuid) {
        if (repository == null) return 0;
        return repository.getActiveSlot(accountUuid);
    }

    /**
     * Deterministic character UUID from account UUID + slot.
     */
    public static UUID characterUuid(UUID accountUuid, int slot) {
        return CharacterRepository.characterUuid(accountUuid, slot);
    }

    public static CharacterRepository getRepository() {
        return repository;
    }
}
