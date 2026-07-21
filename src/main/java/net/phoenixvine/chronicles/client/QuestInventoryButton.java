package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class QuestInventoryButton {

    private static final int IMAGE_W = 176;
    private static final int IMAGE_H = 166;
    private static final int BTN_SIZE = 20;
    private static final int SCREEN_MARGIN = 4;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen)) return;
        QuestChroniclesSettings cfg = QuestChroniclesSettings.get();
        if (!cfg.isShowInventoryButton()) return;

        int screenW = event.getScreen().width;
        int screenH = event.getScreen().height;
        int left = (screenW - IMAGE_W) / 2;
        int top = (screenH - IMAGE_H) / 2;

        int claimable = countClaimable();
        Component label = Component.literal(claimable > 0 ? "ðŸ“– " + (claimable > 9 ? "9+" : claimable) : "ðŸ“–");
        Button.OnPress onPress = b -> {
            if (Minecraft.getInstance().player != null)
                Minecraft.getInstance().setScreen(new ChronicleOverviewScreen());
        };

        Button button = switch (cfg.getInvButtonPos()) {
            case RIGHT -> Button.builder(label, onPress)
                    .bounds(left + IMAGE_W, top + (IMAGE_H - BTN_SIZE) / 2, BTN_SIZE, BTN_SIZE).build();

            case TOP_LEFT -> Button.builder(label, onPress)
                    .bounds(SCREEN_MARGIN, SCREEN_MARGIN, BTN_SIZE, BTN_SIZE).build();
            default -> Button.builder(label, onPress) 
                    .bounds(left - BTN_SIZE, top + (IMAGE_H - BTN_SIZE) / 2, BTN_SIZE, BTN_SIZE).build();
        };
        event.addListener(button);
    }

    private static int countClaimable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        PlayerQuestData data = mc.player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).orElse(null);
        if (data == null) return 0;
        int count = 0;
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            if (node.isLinkStub()) continue;
            if (data.getQuestState(node.getId(), QuestState.LOCKED) == QuestState.COMPLETED &&
                    !node.getRewards().isEmpty() && !data.hasClaimedRewards(node.getId())) {
                count++;
            }
        }
        return count;
    }
}

