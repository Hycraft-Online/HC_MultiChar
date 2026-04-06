package com.hcmultichar.gui;

import com.hcmultichar.HC_MultiCharPlugin;
import com.hcmultichar.database.CharacterRepository;
import com.hcmultichar.models.CharacterInfo;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.List;
import java.util.UUID;

public class CharacterSelectGui extends InteractiveCustomUIPage<CharacterSelectGui.EventDataObj> {

    private final HC_MultiCharPlugin plugin;

    public CharacterSelectGui(HC_MultiCharPlugin plugin, PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, EventDataObj.CODEC);
        this.plugin = plugin;
    }

    @Override
    public void build(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl UICommandBuilder cmd,
                     @NonNullDecl UIEventBuilder events, @NonNullDecl Store<EntityStore> store) {
        cmd.append("Pages/HC_MultiChar_Select.ui");

        UUID accountUuid = playerRef.getUuid();
        List<CharacterInfo> chars = plugin.getRepository().getCharacters(accountUuid);
        int activeSlot = plugin.getRepository().getActiveSlot(accountUuid);

        for (int slot = 0; slot < 3; slot++) {
            CharacterInfo info = findBySlot(chars, slot);

            if (info != null) {
                enrichCharacterInfo(info);

                cmd.set("#SlotName" + slot + ".Text", "Slot #" + (slot + 1));

                StringBuilder details = new StringBuilder();
                details.append("Lv. ").append(info.getLevel());
                if (info.getClassName() != null && !info.getClassName().isEmpty()) {
                    details.append(" ").append(info.getClassName());
                }
                if (info.getFactionName() != null) {
                    details.append(" | ").append(info.getFactionName());
                }
                if (info.getGuildName() != null) {
                    details.append(" [").append(info.getGuildName()).append("]");
                }
                if (info.getHonorRank() != null) {
                    details.append(" | ").append(info.getHonorRank());
                }
                details.append(" | ").append(info.getPlayTimeFormatted()).append(" played");
                cmd.set("#SlotDetails" + slot + ".Text", details.toString());

                if (slot == activeSlot) {
                    cmd.set("#ActiveBar" + slot + ".Visible", true);
                    cmd.set("#SlotStatus" + slot + ".Text", "CURRENTLY PLAYING");
                    cmd.set("#PlayBtnLabel" + slot + ".Text", "ACTIVE");
                    // Don't bind event for active slot - no action needed
                } else {
                    cmd.set("#SlotStatus" + slot + ".Text", "Last played " + info.getLastPlayedFormatted());
                    cmd.set("#PlayBtnLabel" + slot + ".Text", "PLAY");

                    events.addEventBinding(CustomUIEventBindingType.Activating, "#PlayBtn" + slot,
                        EventData.of("Action", "switch:" + slot), false);
                }
            } else {
                // Empty slot
                cmd.set("#SlotName" + slot + ".Text", "Empty Slot");
                cmd.set("#SlotDetails" + slot + ".Text", "Create a new character");
                cmd.set("#CharSlot" + slot + ".Background", "#0f172a(0.45)");
                cmd.set("#PlayBtnLabel" + slot + ".Text", "CREATE");

                events.addEventBinding(CustomUIEventBindingType.Activating, "#PlayBtn" + slot,
                    EventData.of("Action", "create:" + slot), false);
            }
        }
    }

    @Override
    public void handleDataEvent(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl Store<EntityStore> store,
                               @NonNullDecl EventDataObj data) {
        super.handleDataEvent(ref, store, data);

        if (data.action == null) return;

        if (data.action.startsWith("switch:")) {
            int slot = Integer.parseInt(data.action.substring(7));
            // switchCharacter handles closing the page and clearing InteractionManager
            plugin.switchCharacter(playerRef, slot);

        } else if (data.action.startsWith("create:")) {
            int slot = Integer.parseInt(data.action.substring(7));
            UUID accountUuid = playerRef.getUuid();
            UUID charUuid = CharacterRepository.characterUuid(accountUuid, slot);
            String name = playerRef.getUsername();

            CharacterInfo created = plugin.getRepository().createCharacter(accountUuid, slot, name, charUuid);
            if (created != null) {
                playerRef.sendMessage(Message.raw(
                    "Created character '" + name + "' in slot " + slot + ".").color(java.awt.Color.GREEN));
                // Refresh the page
                sendUpdate();
            } else {
                playerRef.sendMessage(Message.raw("Failed to create character.").color(java.awt.Color.RED));
                sendUpdate();
            }
        }
    }

    private CharacterInfo findBySlot(List<CharacterInfo> chars, int slot) {
        return chars.stream().filter(c -> c.getSlot() == slot).findFirst().orElse(null);
    }

    private void enrichCharacterInfo(CharacterInfo info) {
        UUID charUuid = info.getCharacterUuid();

        // Level lookup — HC_LevelingAPI.getPlayerLevel is static, no getInstance needed
        try {
            Class<?> apiClass = Class.forName("com.hcleveling.api.HC_LevelingAPI");
            var getLevel = apiClass.getMethod("getPlayerLevel", UUID.class);
            int level = (int) getLevel.invoke(null, charUuid);
            info.setLevel(level);
        } catch (Exception e) {
            info.setLevel(1);
        }

        // Class name lookup — HC_ClassesAPI.getPlayerClass returns PlayerClass enum
        try {
            Class<?> apiClass = Class.forName("com.hcclasses.api.HC_ClassesAPI");
            var getPlayerClass = apiClass.getMethod("getPlayerClass", UUID.class);
            Object playerClass = getPlayerClass.invoke(null, charUuid);
            if (playerClass != null) {
                var getDisplayName = playerClass.getClass().getMethod("getDisplayName");
                info.setClassName((String) getDisplayName.invoke(playerClass));
            }
        } catch (Exception ignored) {}

        // Faction and guild lookup
        try {
            Class<?> factionPluginClass = Class.forName("com.hcfactions.HC_FactionsPlugin");
            Object factionPlugin = factionPluginClass.getMethod("getInstance").invoke(null);
            if (factionPlugin != null) {
                var repo = factionPluginClass.getMethod("getPlayerDataRepository").invoke(factionPlugin);
                var data = repo.getClass().getMethod("getPlayerData", UUID.class).invoke(repo, charUuid);
                if (data != null) {
                    String factionId = (String) data.getClass().getMethod("getFactionId").invoke(data);
                    if (factionId != null) {
                        var factionMgr = factionPluginClass.getMethod("getFactionManager").invoke(factionPlugin);
                        var faction = factionMgr.getClass().getMethod("getFaction", String.class).invoke(factionMgr, factionId);
                        if (faction != null) {
                            info.setFactionName((String) faction.getClass().getMethod("getDisplayName").invoke(faction));
                        }
                    }
                    UUID guildId = (UUID) data.getClass().getMethod("getGuildId").invoke(data);
                    if (guildId != null) {
                        var guildMgr = factionPluginClass.getMethod("getGuildManager").invoke(factionPlugin);
                        var guild = guildMgr.getClass().getMethod("getGuild", UUID.class).invoke(guildMgr, guildId);
                        if (guild != null) {
                            info.setGuildName((String) guild.getClass().getMethod("getName").invoke(guild));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Honor rank lookup
        try {
            Class<?> honorPluginClass = Class.forName("com.hchonor.HC_HonorPlugin");
            Object honorPlugin = honorPluginClass.getMethod("getInstance").invoke(null);
            if (honorPlugin != null) {
                var manager = honorPluginClass.getMethod("getHonorManager").invoke(honorPlugin);
                if (manager != null) {
                    var honorData = manager.getClass().getMethod("getHonorData", UUID.class, String.class)
                        .invoke(manager, charUuid, "");
                    if (honorData != null) {
                        var rank = honorData.getClass().getMethod("getRank").invoke(honorData);
                        if (rank != null) {
                            int rankNumber = (int) rank.getClass().getMethod("getRankNumber").invoke(rank);
                            if (rankNumber > 0) {
                                // Get faction ID for faction-specific title
                                String factionId = null;
                                try {
                                    Class<?> fp = Class.forName("com.hcfactions.HC_FactionsPlugin");
                                    Object fPlugin = fp.getMethod("getInstance").invoke(null);
                                    if (fPlugin != null) {
                                        var repo = fp.getMethod("getPlayerDataRepository").invoke(fPlugin);
                                        var pd = repo.getClass().getMethod("getPlayerData", UUID.class).invoke(repo, charUuid);
                                        if (pd != null) factionId = (String) pd.getClass().getMethod("getFactionId").invoke(pd);
                                    }
                                } catch (Exception ignored2) {}
                                String title = (String) rank.getClass().getMethod("getTitle", String.class).invoke(rank, factionId);
                                info.setHonorRank(title);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    // Event data codec
    public static class EventDataObj {
        public static final BuilderCodec<EventDataObj> CODEC =
            BuilderCodec.builder(EventDataObj.class, EventDataObj::new)
                .append(new KeyedCodec<>("Action", Codec.STRING),
                    (d, v) -> d.action = v, d -> d.action)
                .add()
                .build();

        private String action;
    }
}
