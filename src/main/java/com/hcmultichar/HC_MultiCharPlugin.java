package com.hcmultichar;

import com.hcmultichar.api.HC_MultiCharAPI;
import com.hcmultichar.commands.CharacterCommand;
import com.hcmultichar.database.CharacterRepository;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class HC_MultiCharPlugin extends JavaPlugin {

    private static volatile HC_MultiCharPlugin instance;
    private CharacterRepository repository;

    // Track session start times for play time tracking
    private final Map<UUID, Long> sessionStartTimes = new ConcurrentHashMap<>();

    // Switch cooldown: account UUID -> last switch timestamp
    private final Map<UUID, Long> switchCooldowns = new ConcurrentHashMap<>();
    private static final long SWITCH_COOLDOWN_MS = 30_000; // 30 seconds

    public HC_MultiCharPlugin(@NonNullDecl JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        super.setup();
        getLogger().at(Level.INFO).log("HC_MultiChar setting up...");

        // Initialize database
        repository = new CharacterRepository();
        repository.initialize();
        HC_MultiCharAPI.init(repository);

        // Register commands
        getCommandRegistry().registerCommand(new CharacterCommand(this));

        // Register events
        getEventRegistry().register(PlayerConnectEvent.class, this::onPlayerConnect);
        getEventRegistry().register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
    }

    @Override
    protected void start() {
        super.start();
        getLogger().at(Level.INFO).log("HC_MultiChar started");
    }

    @Override
    protected void shutdown() {
        super.shutdown();
        // Flush all play times
        for (var entry : sessionStartTimes.entrySet()) {
            flushPlayTime(entry.getKey(), entry.getValue());
        }
        sessionStartTimes.clear();

        if (repository != null) {
            repository.close();
        }
        HC_MultiCharAPI.init(null);
        instance = null;
        getLogger().at(Level.INFO).log("HC_MultiChar shutdown");
    }

    private void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        UUID accountUuid = playerRef.getUuid();
        String username = playerRef.getUsername();

        // Ensure this player has a default character (slot 0)
        // This is idempotent - does nothing if already exists
        CompletableFuture.runAsync(() -> {
            repository.ensureDefaultCharacter(accountUuid, username);
        });

        // Start tracking play time
        sessionStartTimes.put(accountUuid, System.currentTimeMillis());
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        UUID accountUuid = event.getPlayerRef().getUuid();

        // If this is a character switch, don't flush play time yet
        if (HC_MultiCharAPI.isSwitching(accountUuid)) {
            return;
        }

        // Flush play time
        Long startTime = sessionStartTimes.remove(accountUuid);
        if (startTime != null) {
            flushPlayTime(accountUuid, startTime);
        }

        // Clean up switch cooldown
        switchCooldowns.remove(accountUuid);
    }

    private void flushPlayTime(UUID accountUuid, long startTimeMs) {
        long elapsedSecs = (System.currentTimeMillis() - startTimeMs) / 1000;
        if (elapsedSecs > 0) {
            UUID charUuid = repository.resolveActiveCharacterUuid(accountUuid);
            repository.addPlayTime(charUuid, elapsedSecs);
        }
    }

    /**
     * Switch a player to a different character slot.
     * Returns a future that completes when the switch is done.
     */
    public CompletableFuture<Boolean> switchCharacter(PlayerRef playerRef, int targetSlot) {
        UUID accountUuid = playerRef.getUuid();

        // Check cooldown
        Long lastSwitch = switchCooldowns.get(accountUuid);
        if (lastSwitch != null && System.currentTimeMillis() - lastSwitch < SWITCH_COOLDOWN_MS) {
            long remaining = (SWITCH_COOLDOWN_MS - (System.currentTimeMillis() - lastSwitch)) / 1000;
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                "Please wait " + remaining + "s before switching characters.").color(java.awt.Color.RED));
            return CompletableFuture.completedFuture(false);
        }

        // Verify target slot has a character
        var targetChar = repository.getCharacter(accountUuid, targetSlot);
        if (targetChar == null) {
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                "No character in slot " + targetSlot + ".").color(java.awt.Color.RED));
            return CompletableFuture.completedFuture(false);
        }

        int currentSlot = repository.getActiveSlot(accountUuid);
        if (currentSlot == targetSlot) {
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw(
                "Already playing on that character.").color(java.awt.Color.YELLOW));
            return CompletableFuture.completedFuture(false);
        }

        getLogger().at(Level.INFO).log("Switching %s from slot %d to slot %d", playerRef.getUsername(), currentSlot, targetSlot);

        // Mark as switching so other plugins skip disconnect cleanup
        HC_MultiCharAPI.SWITCHING_PLAYERS.add(accountUuid);
        switchCooldowns.put(accountUuid, System.currentTimeMillis());

        // Flush play time for current character before switching
        Long startTime = sessionStartTimes.remove(accountUuid);
        if (startTime != null) {
            flushPlayTime(accountUuid, startTime);
        }

        // Disconnect+reconnect instead of resetPlayer because resetPlayer causes
        // a client-side NullReferenceException during the JoinWorld packet sequence.
        //
        // ORDERING IS CRITICAL:
        // 1. Disconnect first → engine auto-saves current data to CURRENT slot's UUID
        // 2. After save completes, update active slot → next connect loads new character
        // If we update the slot first, the auto-save overwrites the target slot's data.

        // Step 1: Disconnect - triggers auto-save of current character to current slot
        getLogger().at(Level.INFO).log("Disconnecting %s for character switch to slot %d", playerRef.getUsername(), targetSlot);
        playerRef.getPacketHandler().disconnect("Switching to character: " + targetChar.getCharacterName());

        // Step 2: After a delay (to let the disconnect save complete), update active slot
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            repository.setActiveSlot(accountUuid, targetSlot);
            repository.touchLastPlayed(targetChar.getCharacterUuid());
            HC_MultiCharAPI.SWITCHING_PLAYERS.remove(accountUuid);
            getLogger().at(Level.INFO).log("Active slot updated to %d for %s - ready for reconnect",
                targetSlot, playerRef.getUsername());
        }, 2, TimeUnit.SECONDS);

        return CompletableFuture.completedFuture(true);
    }

    public CharacterRepository getRepository() { return repository; }
    public static HC_MultiCharPlugin getInstance() { return instance; }
}
