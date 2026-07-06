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

/**
 * Renders the pinned quest as a compact tracker widget in the top-right corner of the HUD.
 *
 * Layout (fixed 160px wide, height grows with task count):
 *
 * ┌────────────────────────┐
 * │ ▶ Quest Title 📌 │ ← title row (state colour)
 * │ ───────────────────── │
 * │ ✔ Task one │ ← completed task (dim green)
 * │ ✗ Task two │ ← pending task
 * │ ✗ Task three │
 * │ ══════════ 1/3 │ ← progress bar
 * └────────────────────────┘
 *
 * The widget is only rendered when:
 * - A quest is pinned (pinnedQuestId != null in PlayerQuestData)
 * - The quest exists in the registry
 * - No screen is open (the HUD is hidden while GUIs are open)
 * - The player is alive
 */
@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class QuestHudOverlay {

    private static final int WIDGET_W = 164;
    private static final int MARGIN_R = 6;   // from right edge of screen
    private static final int MARGIN_T = 6;   // from top edge
    private static final int PAD = 5;
    private static final int ROW_H = 11;
    private static final int BAR_H = 4;

    // Colours
    private static final int C_BG = 0xCC0B0B0F;
    private static final int C_BORDER = 0xFF252530;
    private static final int C_TITLE_BG = 0xDD09090D;
    private static final int C_DONE_ROW = 0x220044FF; // slight tint for completed tasks
    private static final int C_PROG_BG = 0xFF141420;
    private static final int C_PROG_FILL = 0xFF00AA55;
    private static final int C_PROG_ACT = 0xFFBB8800;
    private static final int C_TEXT = 0xFFD8D8E4;
    private static final int C_TEXT_DIM = 0xFF888898;
    private static final int C_TEXT_DONE = 0xFF44CC88;
    private static final int C_TEXT_ACT = 0xFFFFBB33;
    private static final int C_PIN = 0xFFAA44FF;

    private static final int STACK_GAP = 4;

    // Rendered widget bounds this frame, one per pinned quest — used by the click handler below.
    private static final List<int[]> lastWidgetBounds = new java.util.ArrayList<>();

    // Fade-in: track which pins are new since last frame so each gets its own brief fade-in
    // instead of one shared timer that only ever matched a single pinned quest.
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

        // Toast notifications (quest unlocked/completed) are independent of whether anything is
        // pinned - this used to be the last statement inside renderPinnedWidget's body, so every
        // one of that method's early-returns (no pin, capability missing, etc.) silently skipped
        // toasts too, meaning they never showed at all unless a quest happened to be pinned.
        QuestToastManager.get().render(g, screenW, screenH);
    }

    /**
     * Renders one stacked widget per pinned quest instead of a single fixed widget - packs with
     * multiple simultaneously-pinned quests (a common ask once players realized only one could
     * be tracked at a time) now get a real stack, not just the last-pinned quest silently
     * replacing the rest.
     */
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

        // Fade-in timer per newly-pinned quest, and drop timers for quests no longer pinned.
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

        // Auto-unpin quests whose node no longer exists (e.g. removed from the pack) as we go,
        // rather than leaving a permanently-broken pin the player can't see or clear.
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

            int taskRows = Math.min(tasks.size(), 6);
            int titleH = showTitle ? PAD + ROW_H + 3 : PAD;
            int barSection = (showProgress && !tasks.isEmpty()) ? BAR_H + 4 : 0;
            int widgetH = titleH + taskRows * ROW_H + (tasks.size() > 6 ? ROW_H : 0) + barSection + PAD;

            int wy = stacksUp ? cursorY - widgetH : cursorY;
            int wx = switch (cfg.getHudPosition()) {
                case TOP_LEFT, BOTTOM_LEFT -> MARGIN_R;
                case TOP_CENTER, BOTTOM_CENTER -> (screenW - WIDGET_W) / 2;
                default -> screenW - WIDGET_W - MARGIN_R; // TOP_RIGHT, BOTTOM_RIGHT
            };
            cursorY += stacksUp ? -(widgetH + STACK_GAP) : (widgetH + STACK_GAP);

            drawWidget(mc, g, font, cfg, node, state, tasks, done, wx, wy, widgetH, pinnedId);
            lastWidgetBounds.add(new int[] { wx, wy, widgetH });
        }

        for (ResourceLocation id : toUnpin) data.unpin(id);
    }

    private static void drawWidget(Minecraft mc, GuiGraphics g, Font font, QuestChroniclesSettings cfg,
                                   QuestNode node, QuestState state, List<QuestTask> tasks, int done,
                                   int wx, int wy, int widgetH, ResourceLocation pinnedId) {
        boolean showTitle = cfg.isShowHUDTitle();
        boolean showProgress = cfg.isShowHUDProgress();
        int taskRows = Math.min(tasks.size(), 6);

        int bgAlpha = (int) (cfg.getHudOpacity() * 0xCC);
        int dynBg = (bgAlpha << 24) | 0x0B0B0F;

        // Background + border
        g.fill(wx, wy, wx + WIDGET_W, wy + widgetH, dynBg);
        g.fill(wx, wy, wx + WIDGET_W, wy + 1, C_BORDER);
        g.fill(wx, wy + widgetH - 1, wx + WIDGET_W, wy + widgetH, C_BORDER);
        g.fill(wx, wy, wx + 1, wy + widgetH, C_BORDER);
        g.fill(wx + WIDGET_W - 1, wy, wx + WIDGET_W, wy + widgetH, C_BORDER);

        int ty = wy + PAD;

        if (showTitle) {
            // Title area background
            g.fill(wx + 1, wy + 1, wx + WIDGET_W - 1, wy + PAD + ROW_H + 1, C_TITLE_BG);

            // State icon + title
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

            // Item icon if set
            if (node.getIconItem() != null && node.getIconItem() != net.minecraft.world.item.Items.AIR) {
                g.renderItem(new ItemStack(node.getIconItem()), wx + PAD, wy + PAD - 2);
                String titleStr = truncate(font, node.getTitle().getString(), WIDGET_W - PAD * 2 - 20);
                g.drawString(font, stateGlyph + " " + titleStr, wx + PAD + 18, wy + PAD + 1, titleColor, false);
            } else {
                String titleStr = truncate(font, node.getTitle().getString(), WIDGET_W - PAD * 2 - 14);
                g.drawString(font, stateGlyph + " " + titleStr, wx + PAD, wy + PAD + 1, titleColor, false);
            }

            // Pin icon (top-right)
            g.drawString(font, "§5📌", wx + WIDGET_W - 14, wy + PAD, C_PIN, false);

            // Divider
            int divY = wy + PAD + ROW_H + 1;
            g.fill(wx + PAD, divY, wx + WIDGET_W - PAD, divY + 1, C_BORDER);
            ty = divY + 3;
        }

        // Task rows
        for (int i = 0; i < taskRows; i++) {
            QuestTask task = tasks.get(i);
            boolean isDone = task.isCompletedFor(mc.player);
            if (isDone) g.fill(wx + 1, ty, wx + WIDGET_W - 1, ty + ROW_H, C_DONE_ROW);
            String check = isDone ? "§a✔" : "§8✗";
            String progress = !isDone ? task.getProgressString(mc.player) : null;
            String rawLabel = task.getDescription().getString();
            String label;
            if (progress != null) {
                // Show "Label (X/Y)" — shrink label to fit the count
                String suffix = " §8(" + progress + ")";
                int suffixW = font.width(suffix.replaceAll("§.", ""));
                int maxLabelW = WIDGET_W - PAD * 2 - 14 - suffixW;
                label = truncate(font, rawLabel, maxLabelW) + suffix;
            } else {
                label = truncate(font, rawLabel, WIDGET_W - PAD * 2 - 14);
            }
            g.drawString(font, check + " §7" + label, wx + PAD, ty + 1, isDone ? C_TEXT_DONE : C_TEXT_DIM, false);
            ty += ROW_H;
        }
        if (tasks.size() > 6) {
            g.drawString(font, "§8+" + (tasks.size() - 6) + " more…", wx + PAD, ty + 1, C_TEXT_DIM, false);
            ty += ROW_H;
        }

        // Segmented pip bar — one square pip per task, colored by completion
        if (showProgress && !tasks.isEmpty()) {
            ty += 3;
            int n = tasks.size();
            int maxPips = Math.min(n, 20); // cap at 20 pips so they don't overflow
            int pipAreaW = WIDGET_W - PAD * 2 - 2;
            int pipW = Math.max(3, Math.min(9, (pipAreaW - (maxPips - 1)) / maxPips));
            int gap = 1;
            int totalPipW = maxPips * pipW + (maxPips - 1) * gap;
            int pipX = wx + PAD + (pipAreaW - totalPipW) / 2;
            int barCol = state == QuestState.COMPLETED ? C_PROG_FILL : C_PROG_ACT;
            for (int pi = 0; pi < maxPips; pi++) {
                boolean pipDone = pi < done;
                int px = pipX + pi * (pipW + gap);
                // Background trough
                g.fill(px, ty, px + pipW, ty + BAR_H, C_PROG_BG);
                // Fill for completed pips
                if (pipDone) g.fill(px, ty, px + pipW, ty + BAR_H, barCol);
                // Top highlight on filled pips for a slightly 3D look
                if (pipDone) g.fill(px, ty, px + pipW, ty + 1, 0x33FFFFFF);
            }
            if (n > maxPips) {
                // Overflow: show "+N" count after pips
                g.drawString(font, "§8+" + (n - maxPips), pipX + totalPipW + 3, ty - 1, C_TEXT_DIM, false);
            } else {
                g.drawString(font, "§8" + done + "/" + n, wx + PAD + pipAreaW - font.width(done + "/" + n) + 1, ty - 1,
                        C_TEXT_DIM, false);
            }
        }

        // Fade-in overlay: dims the widget when first pinned, fades to clear over FADE_MS.
        // Per-quest timer (not one shared timer) so pinning a second quest doesn't replay the
        // fade on the first one too.
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
        return font.plainSubstrByWidth(text, maxW - 6) + "…";
    }
}
