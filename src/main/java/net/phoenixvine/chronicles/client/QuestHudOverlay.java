package net.phoenixvine.chronicles.client;

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
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestState;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import java.util.List;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class QuestHudOverlay {

    private static final int WIDGET_W = 148;
    private static final int MARGIN_R = 6;   
    private static final int MARGIN_T = 6;   
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
        while (ChronicleKeyBindings.ITEM_LOOKUP.consumeClick()) {
            ItemLookup.performLookup();
        }
        while (ChronicleKeyBindings.OPEN_QUESTBOOK.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new ChronicleOverviewScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.player.isDeadOrDying()) return;

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
        int cursorY = stacksUp ? screenH - MARGIN_T : MARGIN_T;

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

            int titleH = showTitle ? PAD + ROW_H + 3 : PAD;
            int barSection = (showProgress && !tasks.isEmpty()) ? BAR_H + 4 : 0;
            int widgetH = titleH + barSection + PAD;

            int wy = stacksUp ? cursorY - widgetH : cursorY;
            int wx = switch (cfg.getHudPosition()) {
                case TOP_LEFT, BOTTOM_LEFT -> MARGIN_R;
                case TOP_CENTER, BOTTOM_CENTER -> (screenW - WIDGET_W) / 2;
                default -> screenW - WIDGET_W - MARGIN_R; 
            };
            cursorY += stacksUp ? -(widgetH + STACK_GAP) : (widgetH + STACK_GAP);

            drawWidget(mc, g, font, cfg, node, state, tasks, done, wx, wy, widgetH, pinnedId);
            lastWidgetBounds.add(new int[] { wx, wy, widgetH });
        }

        for (ResourceLocation id : toUnpin) {
            data.unpin(id);
            net.phoenixvine.chronicles.network.ChronicleNetwork.CHANNEL.sendToServer(
                    new net.phoenixvine.chronicles.network.packet.C2STogglePinPacket(id));
        }
    }

    private static void drawWidget(Minecraft mc, GuiGraphics g, Font font, QuestChroniclesSettings cfg,
                                   QuestNode node, QuestState state, List<QuestTask> tasks, int done,
                                   int wx, int wy, int widgetH, ResourceLocation pinnedId) {
        boolean showTitle = cfg.isShowHUDTitle();
        boolean showProgress = cfg.isShowHUDProgress();

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
                case COMPLETED -> "§aâœ”";
                case ACTIVE -> "§6â–¶";
                case LOCKED -> "§8âœ•";
                default -> "§7â—‹";
            };
            int titleColor = switch (state) {
                case COMPLETED -> C_TEXT_DONE;
                case ACTIVE -> C_TEXT_ACT;
                default -> C_TEXT;
            };

            if (node.getIconItem() != null && node.getIconItem() != net.minecraft.world.item.Items.AIR) {
                g.renderItem(new ItemStack(node.getIconItem()), wx + PAD, wy + PAD - 2);
                String titleStr = truncate(font, node.getTitle().getString(), WIDGET_W - PAD * 2 - 20);
                g.drawString(font, stateGlyph + " " + titleStr, wx + PAD + 18, wy + PAD + 1, titleColor, false);
            } else {
                String titleStr = truncate(font, node.getTitle().getString(), WIDGET_W - PAD * 2 - 14);
                g.drawString(font, stateGlyph + " " + titleStr, wx + PAD, wy + PAD + 1, titleColor, false);
            }

            g.drawString(font, "§5ðŸ“Œ", wx + WIDGET_W - 14, wy + PAD, C_PIN, false);

            int divY = wy + PAD + ROW_H + 1;
            g.fill(wx + PAD, divY, wx + WIDGET_W - PAD, divY + 1, C_BORDER);
            ty = divY + 3;
        }

        if (showProgress && !tasks.isEmpty()) {
            ty += 3;
            int n = tasks.size();
            int maxPips = Math.min(n, 20); 
            int pipAreaW = WIDGET_W - PAD * 2 - 2;
            int pipW = Math.max(3, Math.min(9, (pipAreaW - (maxPips - 1)) / maxPips));
            int gap = 1;
            int totalPipW = maxPips * pipW + (maxPips - 1) * gap;
            int pipX = wx + PAD + (pipAreaW - totalPipW) / 2;
            int barCol = state == QuestState.COMPLETED ? C_PROG_FILL : C_PROG_ACT;
            for (int pi = 0; pi < maxPips; pi++) {
                boolean pipDone = pi < done;
                int px = pipX + pi * (pipW + gap);
                
                g.fill(px, ty, px + pipW, ty + BAR_H, C_PROG_BG);
                
                if (pipDone) g.fill(px, ty, px + pipW, ty + BAR_H, barCol);
                
                if (pipDone) g.fill(px, ty, px + pipW, ty + 1, 0x33FFFFFF);
            }
            if (n > maxPips) {
                
                g.drawString(font, "§8+" + (n - maxPips), pipX + totalPipW + 3, ty - 1, C_TEXT_DIM, false);
            } else {
                g.drawString(font, "§8" + done + "/" + n, wx + PAD + pipAreaW - font.width(done + "/" + n) + 1, ty - 1,
                        C_TEXT_DIM, false);
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
    }

    private static String truncate(Font font, String text, int maxW) {
        if (font.width(text) <= maxW) return text;
        return font.plainSubstrByWidth(text, maxW - 6) + "â€¦";
    }
}

