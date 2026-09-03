package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.client.event.ChronicleKeyBindings;
import net.phoenixvine.chronicles.client.registry.QuestToastManager;
import net.phoenixvine.chronicles.client.rich.ItemLookup;
import net.phoenixvine.chronicles.client.rich.MultilineTextArea;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestReward;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import java.util.List;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class QuestHudOverlay {

    private static final int WIDGET_W = 148;
    private static final int MARGIN_R = 6;
    private static final int MARGIN_T = 6;

    private static final int MARGIN_BOTTOM = 68;
    private static final int PAD = 5;
    private static final int ROW_H = 11;
    private static final int BAR_H = 4;

    private static final int C_BG = 0xCC0B0B0F;
    private static final int C_BORDER = 0xFF252530;
    private static final int C_TITLE_BG = 0xDD09090D;
    private static final int C_DONE_ROW = 0x220044FF;
    private static final int C_PROG_BG = 0xFF141420;
    private static final int C_PROG_FILL = 0xFF00AA55;
    private static final int C_PROG_ACT = 0xFFBB8800;
    private static final int C_TEXT = 0xFFD8D8E4;
    private static final int C_TEXT_DIM = 0xFF888898;
    private static final int C_TEXT_DONE = 0xFF44CC88;
    private static final int C_TEXT_ACT = 0xFFFFBB33;
    private static final int C_PIN = 0xFFAA44FF;

    private static final int STACK_GAP = 4;
    private static final int REWARD_ICON_SZ = 14;
    private static final int REWARD_ICON_GAP = 3;

    private static final List<int[]> lastWidgetBounds = new java.util.ArrayList<>();

    private static final java.util.Set<ResourceLocation> lastPinnedIds = new java.util.HashSet<>();
    private static final java.util.Map<ResourceLocation, Long> pinChangeTimes = new java.util.HashMap<>();
    private static final long FADE_MS = 200;

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton event) {
        if (event.getButton() != 0 || event.getAction() != 1) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || lastWidgetBounds.isEmpty()) return;
        double scale = mc.getWindow().getGuiScale();
        int mx = (int) (mc.mouseHandler.xpos() / scale);
        int my = (int) (mc.mouseHandler.ypos() / scale);
        for (int[] b : lastWidgetBounds) {
            if (mx >= b[0] && mx <= b[0] + WIDGET_W && my >= b[1] && my <= b[1] + b[2]) {
                mc.setScreen(new ChronicleOverviewScreen());
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        QuestToastManager.get().tick();

        while (ChronicleKeyBindings.OPEN_QUESTBOOK.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new ChronicleOverviewScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS) return;
        int boundKey = ChronicleKeyBindings.ITEM_LOOKUP.getKey().getValue();
        if (boundKey == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) return;
        if (event.getKey() != boundKey) return;
        if (isTypingInTextField()) return;
        ItemLookup.performLookup();
    }

    private static boolean isTypingInTextField() {
        net.minecraft.client.gui.screens.Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return false;
        net.minecraft.client.gui.components.events.GuiEventListener focused = screen.getFocused();
        return focused instanceof net.minecraft.client.gui.components.EditBox ||
                focused instanceof MultilineTextArea;
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isDeadOrDying()) return;

        boolean ownScreenOpen = mc.screen != null &&
                mc.screen.getClass().getName().startsWith("net.phoenixvine.chronicles");
        if (ownScreenOpen) return;

        GuiGraphics g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        renderPinnedWidgets(mc, g, screenW, screenH);

        QuestToastManager.get().render(g, screenW, screenH);
    }

    private static void renderPinnedWidgets(Minecraft mc, GuiGraphics g, int screenW, int screenH) {
        lastWidgetBounds.clear();
        PlayerQuestData data = mc.player.getCapability(QuestCapabilityProvider.PLAYER_QUESTS).orElse(null);
        if (data == null) return;

        java.util.Set<ResourceLocation> pinnedIds = data.getPinnedQuestIds();
        if (pinnedIds.isEmpty()) {
            lastPinnedIds.clear();
            pinChangeTimes.clear();
            return;
        }

        for (ResourceLocation id : pinnedIds) {
            if (!lastPinnedIds.contains(id)) pinChangeTimes.put(id, System.currentTimeMillis());
        }
        pinChangeTimes.keySet().retainAll(pinnedIds);
        lastPinnedIds.clear();
        lastPinnedIds.addAll(pinnedIds);

        QuestChroniclesSettings cfg = QuestChroniclesSettings.get();
        Font font = mc.font;
        boolean stacksUp = cfg.getHudPosition() == QuestChroniclesSettings.HUDPosition.BOTTOM_LEFT ||
                cfg.getHudPosition() == QuestChroniclesSettings.HUDPosition.BOTTOM_CENTER ||
                cfg.getHudPosition() == QuestChroniclesSettings.HUDPosition.BOTTOM_RIGHT;
        int cursorY = stacksUp ? screenH - MARGIN_BOTTOM : MARGIN_T;

        List<ResourceLocation> toUnpin = new java.util.ArrayList<>();

        for (ResourceLocation pinnedId : pinnedIds) {
            QuestNode node = QuestTreeRegistry.getQuest(pinnedId);
            if (node == null) {
                toUnpin.add(pinnedId);
                continue;
            }

            QuestState state = data.getQuestState(pinnedId, QuestState.LOCKED);
            List<QuestTask> tasks = node.getTasks();

            int done = 0;
            for (QuestTask t : tasks) if (t.isCompletedFor(mc.player)) done++;

            boolean showTitle = cfg.isShowHUDTitle();
            boolean showProgress = cfg.isShowHUDProgress();

            List<net.minecraft.util.FormattedCharSequence> remainingTaskLines = wrapTaskLines(font, tasks, mc);
            List<QuestReward> rewards = node.getRewards();
            boolean showRewards = cfg.isShowHUDRewards() && !rewards.isEmpty();

            int titleH = showTitle ? PAD + ROW_H + 3 : PAD;
            int tasksSection = remainingTaskLines.isEmpty() ? 0 : (remainingTaskLines.size() * ROW_H) + 3;
            int rewardRows = showRewards ? rewardRowCount(rewards.size()) : 0;
            int rewardsSection = rewardRows > 0 ?
                    rewardRows * REWARD_ICON_SZ + (rewardRows - 1) * REWARD_ICON_GAP + 6 : 0;
            int widgetH = titleH + tasksSection + rewardsSection + PAD;

            int wy = stacksUp ? cursorY - widgetH : cursorY;
            int wx = switch (cfg.getHudPosition()) {
                case TOP_LEFT, BOTTOM_LEFT -> MARGIN_R;
                case TOP_CENTER, BOTTOM_CENTER -> (screenW - WIDGET_W) / 2;
                default -> screenW - WIDGET_W - MARGIN_R;
            };
            cursorY += stacksUp ? -(widgetH + STACK_GAP) : (widgetH + STACK_GAP);

            drawWidget(mc, g, font, cfg, node, state, done, tasks.size(), wx, wy, widgetH, pinnedId,
                    remainingTaskLines, showProgress, showRewards ? rewards : List.of());
            lastWidgetBounds.add(new int[] { wx, wy, widgetH });
        }

        for (ResourceLocation id : toUnpin) {
            data.unpin(id);
            net.phoenixvine.chronicles.network.ChronicleNetwork.CHANNEL.sendToServer(
                    new net.phoenixvine.chronicles.network.packet.C2STogglePinPacket(id));
        }
    }

    private static int rewardIconsPerRow() {
        return Math.max(1, (WIDGET_W - PAD * 2) / (REWARD_ICON_SZ + REWARD_ICON_GAP));
    }

    private static int rewardRowCount(int rewardCount) {
        return (int) Math.ceil(rewardCount / (double) rewardIconsPerRow());
    }

    private static final int MAX_TASK_LINES = 8;

    private static List<net.minecraft.util.FormattedCharSequence> wrapTaskLines(Font font, List<QuestTask> tasks,
                                                                                Minecraft mc) {
        List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>();
        if (mc.player == null) return lines;
        int maxW = WIDGET_W - PAD * 2;
        for (QuestTask t : tasks) {
            if (t.isCompletedFor(mc.player)) continue;
            String desc = t.getDescription().getString();
            if (desc.isBlank()) continue;
            net.minecraft.network.chat.Component text = net.minecraft.network.chat.Component.literal("- " + desc)
                    .withStyle(net.minecraft.ChatFormatting.GRAY);
            lines.addAll(font.split(text, maxW));
            if (lines.size() >= MAX_TASK_LINES) break;
        }
        if (lines.size() > MAX_TASK_LINES) {
            lines = new java.util.ArrayList<>(lines.subList(0, MAX_TASK_LINES));
        }
        return lines;
    }

    private static void drawWidget(Minecraft mc, GuiGraphics g, Font font, QuestChroniclesSettings cfg,
                                   QuestNode node, QuestState state, int done, int total,
                                   int wx, int wy, int widgetH, ResourceLocation pinnedId,
                                   List<net.minecraft.util.FormattedCharSequence> remainingTaskLines,
                                   boolean showProgress, List<QuestReward> rewards) {
        boolean showTitle = cfg.isShowHUDTitle();

        g.enableScissor(wx, wy, wx + WIDGET_W, wy + widgetH);

        int bgAlpha = (int) (cfg.getHudOpacity() * 0xCC);
        int dynBg = (bgAlpha << 24) | 0x0B0B0F;

        g.fill(wx, wy, wx + WIDGET_W, wy + widgetH, dynBg);
        g.fill(wx, wy, wx + WIDGET_W, wy + 1, C_BORDER);
        g.fill(wx, wy + widgetH - 1, wx + WIDGET_W, wy + widgetH, C_BORDER);
        g.fill(wx, wy, wx + 1, wy + widgetH, C_BORDER);
        g.fill(wx + WIDGET_W - 1, wy, wx + WIDGET_W, wy + widgetH, C_BORDER);

        int ty = wy + PAD;

        if (showTitle) {

            g.fill(wx + 1, wy + 1, wx + WIDGET_W - 1, wy + PAD + ROW_H + 1, C_TITLE_BG);

            String stateGlyph = switch (state) {
                case COMPLETED -> "§a✔";
                case ACTIVE -> "§6▶";
                case LOCKED -> "§8✕";
                default -> "§7○";
            };
            int titleColor = switch (state) {
                case COMPLETED -> C_TEXT_DONE;
                case ACTIVE -> C_TEXT_ACT;
                default -> C_TEXT;
            };

            String counter = (showProgress && total > 0) ? done + "/" + total : null;
            int rightReserve = 14 + (counter != null ? font.width(counter) + 4 : 0);
            if (node.getIconItem() != null && node.getIconItem() != net.minecraft.world.item.Items.AIR) {
                g.renderItem(new ItemStack(node.getIconItem()), wx + PAD, wy + PAD - 2);
                String titleStr = truncate(font, node.getTitle().getString(), WIDGET_W - PAD * 2 - 20 - rightReserve);
                g.drawString(font, stateGlyph + " " + titleStr, wx + PAD + 18, wy + PAD + 1, titleColor, false);
            } else {
                String titleStr = truncate(font, node.getTitle().getString(), WIDGET_W - PAD * 2 - 14 - rightReserve);
                g.drawString(font, stateGlyph + " " + titleStr, wx + PAD, wy + PAD + 1, titleColor, false);
            }

            if (counter != null) {
                g.drawString(font, "§8" + counter, wx + WIDGET_W - PAD - 14 - font.width(counter), wy + PAD + 1,
                        C_TEXT_DIM, false);
            }

            g.drawString(font, "§5📌", wx + WIDGET_W - 14, wy + PAD, C_PIN, false);

            int divY = wy + PAD + ROW_H + 1;
            g.fill(wx + PAD, divY, wx + WIDGET_W - PAD, divY + 1, C_BORDER);
            ty = divY + 3;
        }

        if (!remainingTaskLines.isEmpty()) {
            ty += 3;
            for (net.minecraft.util.FormattedCharSequence line : remainingTaskLines) {
                g.drawString(font, line, wx + PAD, ty, C_TEXT_DIM, false);
                ty += ROW_H;
            }
        }

        if (!rewards.isEmpty()) {
            ty += 3;
            int perRow = rewardIconsPerRow();
            for (int i = 0; i < rewards.size(); i++) {
                int rx = wx + PAD + (i % perRow) * (REWARD_ICON_SZ + REWARD_ICON_GAP);
                int ry = ty + (i / perRow) * (REWARD_ICON_SZ + REWARD_ICON_GAP);
                drawRewardIcon(g, rewards.get(i), rx, ry, REWARD_ICON_SZ);
            }
        }

        Long pinChangeTimeMs = pinChangeTimes.get(pinnedId);
        if (pinChangeTimeMs != null) {
            long elapsed = System.currentTimeMillis() - pinChangeTimeMs;
            if (elapsed < FADE_MS) {
                float t = 1f - (float) elapsed / FADE_MS;
                int fadeAlpha = (int) (t * t * 0xEE) & 0xFF;
                g.fill(wx, wy, wx + WIDGET_W, wy + widgetH, (fadeAlpha << 24) | 0x000000);
            }
        }

        g.disableScissor();
    }

    private static void drawRewardIcon(GuiGraphics g, QuestReward reward, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, 0xFF0F0F18);
        g.fill(x, y, x + size, y + 1, 0xFF333344);
        g.fill(x, y + size - 1, x + size, y + size, 0xFF333344);
        g.fill(x, y, x + 1, y + size, 0xFF333344);
        g.fill(x + size - 1, y, x + size, y + size, 0xFF333344);

        if (reward instanceof QuestReward.ItemReward ir) {
            float scale = size / 16f;
            g.pose().pushPose();
            try {
                g.pose().translate(x + size / 2f, y + size / 2f, 0f);
                g.pose().scale(scale, scale, 1f);
                g.renderItem(new ItemStack(ir.getItem(), ir.getCount()), -8, -8);
            } catch (Exception ignored) {} finally {
                g.pose().popPose();
            }
        } else {
            String glyph = switch (reward.getType()) {
                case XP -> "⚡";
                case COMMAND -> "◆";
                case LOOT_TABLE, LOOT_CRATE -> "📦";
                case SCRIPT_EVENT -> "✦";
                default -> "★";
            };
            Font font = Minecraft.getInstance().font;
            g.drawString(font, "§7" + glyph, x + size / 2 - font.width(glyph) / 2, y + size / 2 - 4, 0xFF888898,
                    false);
        }
    }

    private static String truncate(Font font, String text, int maxW) {
        if (font.width(text) <= maxW) return text;
        return font.plainSubstrByWidth(text, maxW - 6) + "…";
    }
}
