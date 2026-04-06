package com.hcmultichar.commands;

import com.hcmultichar.HC_MultiCharPlugin;
import com.hcmultichar.api.HC_MultiCharAPI;
import com.hcmultichar.database.CharacterRepository;
import com.hcmultichar.gui.CharacterSelectGui;
import com.hcmultichar.models.CharacterInfo;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.awt.Color;
import java.util.List;
import java.util.UUID;

/**
 * /character [subcommand] [args]
 * Subcommands: list, switch <slot>, create <name>, delete <slot>
 */
public class CharacterCommand extends AbstractPlayerCommand {

    private final HC_MultiCharPlugin plugin;
    private final DefaultArg<String> subcommandArg =
        this.withDefaultArg("subcommand", "list/switch/create/delete", ArgTypes.STRING, "list", "");
    private final OptionalArg<String> argArg =
        this.withOptionalArg("arg", "Slot number or character name", ArgTypes.STRING);

    public CharacterCommand(HC_MultiCharPlugin plugin) {
        super("character", "Manage your characters");
        this.addAliases("char", "chars");
        this.plugin = plugin;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@NonNullDecl CommandContext ctx, @NonNullDecl Store<EntityStore> store,
                           @NonNullDecl Ref<EntityStore> ref, @NonNullDecl PlayerRef playerRef,
                           @NonNullDecl World playerWorld) {
        String sub = (String) subcommandArg.get(ctx);
        String arg = argArg.get(ctx);
        UUID accountUuid = playerRef.getUuid();

        switch (sub.toLowerCase()) {
            case "list" -> handleList(playerRef, accountUuid);
            case "switch" -> handleSwitch(playerRef, accountUuid, arg, ref, store, playerWorld);
            case "create" -> handleCreate(playerRef, accountUuid, arg);
            case "delete" -> handleDelete(playerRef, accountUuid, arg);
            case "gui", "menu", "select" -> handleGui(playerRef, ref, store, playerWorld);
            default -> playerRef.sendMessage(Message.raw(
                "Usage: /character <list|switch|create|delete|gui> [arg]").color(Color.YELLOW));
        }
    }

    private void handleList(PlayerRef playerRef, UUID accountUuid) {
        List<CharacterInfo> chars = plugin.getRepository().getCharacters(accountUuid);
        int activeSlot = plugin.getRepository().getActiveSlot(accountUuid);

        playerRef.sendMessage(Message.raw("--- Your Characters ---").color(Color.decode("#d4af37")));

        for (int slot = 0; slot < 3; slot++) {
            CharacterInfo info = findBySlot(chars, slot);
            if (info != null) {
                enrichCharacterInfo(info);
                String prefix = (slot == activeSlot) ? " > " : "   ";
                String active = (slot == activeSlot) ? " [ACTIVE]" : "";
                String line = String.format("%sSlot %d: %s (Lv.%d %s) - %s%s",
                    prefix, slot, info.getCharacterName(),
                    info.getLevel(), info.getClassName() != null ? info.getClassName() : "No Class",
                    info.getPlayTimeFormatted(), active);
                playerRef.sendMessage(Message.raw(line).color(
                    slot == activeSlot ? Color.decode("#22d3ee") : Color.WHITE));
            } else {
                playerRef.sendMessage(Message.raw("   Slot " + slot + ": [Empty]").color(Color.GRAY));
            }
        }
    }

    private void handleSwitch(PlayerRef playerRef, UUID accountUuid, String arg,
                              Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        if (arg == null) {
            playerRef.sendMessage(Message.raw("Usage: /character switch <0|1|2>").color(Color.YELLOW));
            return;
        }

        int slot;
        try {
            slot = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            playerRef.sendMessage(Message.raw("Slot must be 0, 1, or 2.").color(Color.RED));
            return;
        }

        if (slot < 0 || slot > 2) {
            playerRef.sendMessage(Message.raw("Slot must be 0, 1, or 2.").color(Color.RED));
            return;
        }

        playerRef.sendMessage(Message.raw("Switching character...").color(Color.decode("#22d3ee")));
        plugin.switchCharacter(playerRef, slot);
    }

    private void handleCreate(PlayerRef playerRef, UUID accountUuid, String arg) {
        String name = (arg != null && !arg.isEmpty()) ? arg : playerRef.getUsername();

        // Find first empty slot
        List<CharacterInfo> chars = plugin.getRepository().getCharacters(accountUuid);
        int targetSlot = -1;
        for (int slot = 0; slot < 3; slot++) {
            if (findBySlot(chars, slot) == null) {
                targetSlot = slot;
                break;
            }
        }

        if (targetSlot == -1) {
            playerRef.sendMessage(Message.raw("All 3 character slots are full.").color(Color.RED));
            return;
        }

        UUID charUuid = CharacterRepository.characterUuid(accountUuid, targetSlot);
        CharacterInfo created = plugin.getRepository().createCharacter(accountUuid, targetSlot, name, charUuid);
        if (created != null) {
            playerRef.sendMessage(Message.raw(
                "Created character '" + name + "' in slot " + targetSlot + ".").color(Color.GREEN));
            playerRef.sendMessage(Message.raw(
                "Use /character switch " + targetSlot + " to play this character.").color(Color.decode("#22d3ee")));
        } else {
            playerRef.sendMessage(Message.raw("Failed to create character.").color(Color.RED));
        }
    }

    private void handleDelete(PlayerRef playerRef, UUID accountUuid, String arg) {
        if (arg == null) {
            playerRef.sendMessage(Message.raw("Usage: /character delete <0|1|2>").color(Color.YELLOW));
            return;
        }

        int slot;
        try {
            slot = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            playerRef.sendMessage(Message.raw("Slot must be 0, 1, or 2.").color(Color.RED));
            return;
        }

        int activeSlot = plugin.getRepository().getActiveSlot(accountUuid);
        if (slot == activeSlot) {
            playerRef.sendMessage(Message.raw("Cannot delete your active character. Switch first.").color(Color.RED));
            return;
        }

        if (plugin.getRepository().deleteCharacter(accountUuid, slot)) {
            playerRef.sendMessage(Message.raw("Character in slot " + slot + " deleted.").color(Color.YELLOW));
        } else {
            playerRef.sendMessage(Message.raw("No character in slot " + slot + ".").color(Color.RED));
        }
    }

    private void handleGui(PlayerRef playerRef, Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        world.execute(() -> {
            Ref<EntityStore> freshRef = playerRef.getReference();
            if (freshRef == null || !freshRef.isValid()) return;
            Store<EntityStore> freshStore = freshRef.getStore();
            if (freshStore == null) return;
            Player player = freshStore.getComponent(freshRef, Player.getComponentType());
            if (player == null) return;

            CharacterSelectGui gui = new CharacterSelectGui(plugin, playerRef);
            player.getPageManager().openCustomPage(freshRef, freshStore, gui);
        });
    }

    private CharacterInfo findBySlot(List<CharacterInfo> chars, int slot) {
        return chars.stream().filter(c -> c.getSlot() == slot).findFirst().orElse(null);
    }

    /**
     * Populate level/class from other plugin APIs via reflection.
     */
    private void enrichCharacterInfo(CharacterInfo info) {
        UUID charUuid = info.getCharacterUuid();

        // Level from HC_Leveling
        try {
            Class<?> apiClass = Class.forName("com.hcleveling.api.HC_LevelingAPI");
            var getInstance = apiClass.getMethod("getInstance");
            var api = getInstance.invoke(null);
            if (api != null) {
                var getLevel = apiClass.getMethod("getPlayerLevel", UUID.class);
                info.setLevel((int) getLevel.invoke(api, charUuid));
            }
        } catch (Exception ignored) {
            info.setLevel(1);
        }

        // Class from HC_Classes
        try {
            Class<?> apiClass = Class.forName("com.hcclasses.api.HC_ClassesAPI");
            var getInstance = apiClass.getMethod("getInstance");
            var api = getInstance.invoke(null);
            if (api != null) {
                var getClassName = apiClass.getMethod("getPlayerClassName", UUID.class);
                info.setClassName((String) getClassName.invoke(api, charUuid));
            }
        } catch (Exception ignored) {}
    }
}
