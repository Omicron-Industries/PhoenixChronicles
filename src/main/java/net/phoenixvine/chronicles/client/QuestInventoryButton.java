package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.item.ChronicleItems;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import org.jetbrains.annotations.NotNull;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class QuestInventoryButton {

    private static final int IMAGE_W = 176;
    private static final int IMAGE_H = 166;
    private static final int CREATIVE_IMAGE_W = 195;
    private static final int CREATIVE_IMAGE_H = 136;
    private static final int BTN_SIZE = 20;
    private static final int SCREEN_MARGIN = 4;
    private static final int TOP_MARGIN = 1;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        boolean isCreative = event.getScreen() instanceof CreativeModeInventoryScreen;
        if (!(event.getScreen() instanceof InventoryScreen) && !isCreative) return;
        QuestChroniclesSettings cfg = QuestChroniclesSettings.get();
        if (!cfg.isShowInventoryButton()) return;

        int screenW = event.getScreen().width;
        int screenH = event.getScreen().height;
        int imgW = isCreative ? CREATIVE_IMAGE_W : IMAGE_W;
        int imgH = isCreative ? CREATIVE_IMAGE_H : IMAGE_H;
        int left = (screenW - imgW) / 2;
        int top = (screenH - imgH) / 2;

        int claimable = countClaimable();
        Button.OnPress onPress = b -> {
            if (Minecraft.getInstance().player != null)
                Minecraft.getInstance().setScreen(new ChronicleOverviewScreen());
        };

        Button button = switch (cfg.getInvButtonPos()) {
            case RIGHT -> new BookIconButton(left + imgW, top + (imgH - BTN_SIZE) / 2, onPress, claimable);
            case TOP_LEFT -> new BookIconButton(SCREEN_MARGIN, TOP_MARGIN, onPress, claimable);
            default -> new BookIconButton(left - BTN_SIZE, top + (imgH - BTN_SIZE) / 2, onPress, claimable);
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

    private static final class BookIconButton extends Button {

        private final int claimable;

        BookIconButton(int x, int y, OnPress onPress, int claimable) {
            super(x, y, BTN_SIZE, BTN_SIZE, Component.empty(), onPress, DEFAULT_NARRATION);
            this.claimable = claimable;
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("§fOpen Quest Book")));
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics g, int mx, int my, float partial) {
            g.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() ? 0xFF6E6E6E : 0xFF2B2B2B);
            g.renderItem(new ItemStack(ChronicleItems.CHRONICLE_BOOK.get()), getX() + 2, getY() + 2);
            if (claimable > 0) {
                Minecraft mc = Minecraft.getInstance();
                String badge = claimable > 9 ? "9+" : String.valueOf(claimable);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.drawString(mc.font, badge, getX() + width - mc.font.width(badge) - 1, getY() + height - 9,
                        0xFFFFFF55, true);
                g.pose().popPose();
            }
        }
    }
}
