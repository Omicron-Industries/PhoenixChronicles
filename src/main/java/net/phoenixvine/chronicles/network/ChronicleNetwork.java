package net.phoenixvine.chronicles.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.phoenixvine.chronicles.network.packet.C2SAcknowledgeInfoTasksPacket;
import net.phoenixvine.chronicles.network.packet.C2SClaimQuestRewardPacket;
import net.phoenixvine.chronicles.network.packet.C2SCompleteCheckmarkTaskPacket;
import net.phoenixvine.chronicles.network.packet.C2SPhantasiaTaskCompletePacket;
import net.phoenixvine.chronicles.network.packet.C2SSetQuestStatePacket;
import net.phoenixvine.chronicles.network.packet.C2STogglePinPacket;
import net.phoenixvine.chronicles.network.packet.S2CReloadQuestsFromDiskPacket;
import net.phoenixvine.chronicles.network.packet.S2CSyncPlayerProgressPacket;
import net.phoenixvine.chronicles.network.packet.S2CSyncQuestsPacket;

import java.util.Optional;

public class ChronicleNetwork {

    private static final String PROTOCOL = "1";

    public static SimpleChannel CHANNEL;
    private static int id = 0;

    public static void init() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation("phoenix_chronicles", "main"),
                () -> PROTOCOL,
                PROTOCOL::equals,
                PROTOCOL::equals);

        CHANNEL.registerMessage(id++,
                S2CSyncQuestsPacket.class,
                S2CSyncQuestsPacket::encode,
                S2CSyncQuestsPacket::new,
                S2CSyncQuestsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                C2SClaimQuestRewardPacket.class,
                C2SClaimQuestRewardPacket::encode,
                C2SClaimQuestRewardPacket::new,
                C2SClaimQuestRewardPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                C2SSetQuestStatePacket.class,
                C2SSetQuestStatePacket::encode,
                C2SSetQuestStatePacket::new,
                C2SSetQuestStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                S2CSyncPlayerProgressPacket.class,
                S2CSyncPlayerProgressPacket::encode,
                S2CSyncPlayerProgressPacket::new,
                S2CSyncPlayerProgressPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                C2SAcknowledgeInfoTasksPacket.class,
                C2SAcknowledgeInfoTasksPacket::encode,
                C2SAcknowledgeInfoTasksPacket::new,
                C2SAcknowledgeInfoTasksPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                C2SCompleteCheckmarkTaskPacket.class,
                C2SCompleteCheckmarkTaskPacket::encode,
                C2SCompleteCheckmarkTaskPacket::new,
                C2SCompleteCheckmarkTaskPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                S2CReloadQuestsFromDiskPacket.class,
                S2CReloadQuestsFromDiskPacket::encode,
                S2CReloadQuestsFromDiskPacket::new,
                S2CReloadQuestsFromDiskPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                C2STogglePinPacket.class,
                C2STogglePinPacket::encode,
                C2STogglePinPacket::new,
                C2STogglePinPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                C2SPhantasiaTaskCompletePacket.class,
                C2SPhantasiaTaskCompletePacket::encode,
                C2SPhantasiaTaskCompletePacket::new,
                C2SPhantasiaTaskCompletePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
