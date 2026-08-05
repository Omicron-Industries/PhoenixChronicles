package net.phoenixvine.chronicles.integration.conflux;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.phoenix.core.conflux.research.ResearchTeamHelper;
import net.phoenix.core.conflux.research.WorldResearchData;

public final class ConfluxCompat {

    public static final String PHOENIXCORE_MOD_ID = "phoenixcore";

    public static boolean isAvailable() {
        return ModList.get().isLoaded(PHOENIXCORE_MOD_ID);
    }

    public static boolean isUnlocked(Player player, ResourceLocation nodeId) {
        if (!isAvailable() || !(player instanceof ServerPlayer sp)) return false;
        var team = ResearchTeamHelper.getTeamId(sp);
        return WorldResearchData.get(sp.serverLevel()).isUnlocked(team, nodeId);
    }

    public static boolean hasFlag(Player player, String flag) {
        if (!isAvailable() || !(player instanceof ServerPlayer sp)) return false;
        var team = ResearchTeamHelper.getTeamId(sp);
        return WorldResearchData.get(sp.serverLevel()).hasFlag(team, flag);
    }

    public static void grantUnlock(ServerPlayer player, ResourceLocation nodeId) {
        if (!isAvailable()) return;
        var team = ResearchTeamHelper.getTeamId(player);
        WorldResearchData.get(player.serverLevel()).grantUnlock(team, nodeId);
    }

    public static void grantFlag(ServerPlayer player, String flag) {
        if (!isAvailable()) return;
        var team = ResearchTeamHelper.getTeamId(player);
        WorldResearchData.get(player.serverLevel()).grantFlag(team, flag);
    }

    private ConfluxCompat() {}
}
