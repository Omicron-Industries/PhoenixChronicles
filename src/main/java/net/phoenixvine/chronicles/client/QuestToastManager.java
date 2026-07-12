package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.chronicles.model.QuestGroup;
import net.phoenixvine.chronicles.model.QuestNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages animated toast notifications for quest state changes.
 *
 * Two toast types:
 * UNLOCKED — small blue banner: "Quest Unlocked: <title>"
 * COMPLETED — gold banner: "Quest Complete! <title>"
 *
 * Each toast is rendered under one of three presets (QuestChroniclesSettings.ToastStyle) unless
 * the quest has its own custom design (QuestToastConfig, set via the node context menu's
 * "Design toast…" entry), in which case that freeform layout is used instead.
 *
 * Call from the client HUD overlay renderer (ChronicleClientEvents or QuestHudOverlay).
 */
public class QuestToastManager {

    public enum ToastType {
        UNLOCKED,
        COMPLETED
    }

    // Shrunk from 200x32, then again from 140x24 - these kept reading as more of an interruption
    // than a quiet notification. Smaller footprint, lighter background, thinner accent bar; the
    // position setting (see render()) lets them move out of the way entirely.
    private static final int TOAST_W = 124;
    private static final int TOAST_H = 20;
    private static final int MARGIN = 4;
    private static final int GAP = 3;
    private static final int SLIDE_TICKS = 8;
    private static final int STAY_TICKS = 48; // 2.4 s at 20 tps
    private static final int FADE_TICKS = 12;
    private static final int MAX_VISIBLE = 3;

    private static final int C_BG_UNLOCK = 0xB2091025;
    private static final int C_BG_DONE = 0xB2170D00;
    private static final int C_BAR_UNLOCK = 0xFF3366FF;
    private static final int C_BAR_DONE = 0xFFFFAA00;
    private static final int C_TITLE_UNLOCK = 0xFF99BBFF;
    private static final int C_TITLE_DONE = 0xFFFFDD66;
    private static final int C_LABEL = 0xFFCCCCCC;

    private static final QuestToastManager INSTANCE = new QuestToastManager();

    public static QuestToastManager get() {
        return INSTANCE;
    }

    private final Deque<ToastEntry> queue = new ArrayDeque<>();
    private final List<ActiveToast> active = new ArrayList<>();

    public void push(QuestNode node, ToastType type) {
        if (!net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get().isShowToasts()) return;
        queue.addLast(new ToastEntry(node, type));
    }

    /** Call once per client tick to advance animations and promote queued toasts. */
    public void tick() {
        active.removeIf(t -> t.ticksAlive > SLIDE_TICKS + STAY_TICKS + FADE_TICKS);
        while (active.size() < MAX_VISIBLE && !queue.isEmpty()) {
            active.add(new ActiveToast(queue.pollFirst()));
        }
        for (ActiveToast t : active) t.ticksAlive++;
    }

    /** Render all active toasts — call from HUD overlay post-render. */
    public void render(GuiGraphics g, int screenW, int screenH) {
        if (active.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        QuestChroniclesSettings.ToastStyle defaultStyle = QuestChroniclesSettings.get().getToastStyle();

        // Custom-designed toasts render in their own pass first (each is independently
        // positioned by its quest's design, so they don't participate in the shared stack
        // layout the presets below use).
        List<ActiveToast> presetToasts = new ArrayList<>();
        for (ActiveToast t : active) {
            QuestToastConfig custom = QuestToastConfig.getOrNull(t.entry.node.getId().toString());
            if (custom != null) {
                renderCustom(g, font, screenW, screenH, t, custom);
            } else {
                presetToasts.add(t);
            }
        }
        if (presetToasts.isEmpty()) return;

        switch (defaultStyle) {
            case ABOVE_HOTBAR -> renderAboveHotbar(g, font, screenW, screenH, presetToasts);
            case BIG_CENTER -> renderBigCenter(g, font, screenW, screenH, presetToasts);
            default -> renderCompact(g, font, screenW, screenH, presetToasts);
        }
    }

    // ── Preset: COMPACT (small corner banner) ───────────────────────────────

    private void renderCompact(GuiGraphics g, Font font, int screenW, int screenH, List<ActiveToast> toasts) {
        QuestChroniclesSettings.HUDPosition pos = QuestChroniclesSettings.get().getToastPosition();
        boolean top = pos == QuestChroniclesSettings.HUDPosition.TOP_LEFT ||
                pos == QuestChroniclesSettings.HUDPosition.TOP_CENTER ||
                pos == QuestChroniclesSettings.HUDPosition.TOP_RIGHT;
        boolean left = pos == QuestChroniclesSettings.HUDPosition.TOP_LEFT ||
                pos == QuestChroniclesSettings.HUDPosition.BOTTOM_LEFT;
        boolean right = pos == QuestChroniclesSettings.HUDPosition.TOP_RIGHT ||
                pos == QuestChroniclesSettings.HUDPosition.BOTTOM_RIGHT;

        int stackH = toasts.size() * TOAST_H + Math.max(0, toasts.size() - 1) * GAP;
        // Anchored 1/4 of the way in from whichever edge, same offset either direction, so the
        // stack sits clear of both the hotbar and the pinned-quest widget regardless of corner.
        int slotY = top ? screenH / 4 : screenH - screenH / 4 - stackH;

        for (ActiveToast t : toasts) {
            float progress = computeX(t);
            int x;
            if (right) {
                x = (int) (screenW - MARGIN - (TOAST_W * progress));
            } else if (left) {
                x = (int) (MARGIN - TOAST_W + (TOAST_W * progress));
            } else {
                x = (screenW - TOAST_W) / 2; // center anchors just fade in place, no horizontal slide
            }
            int y = slotY;
            slotY += TOAST_H + GAP;

            float alpha = computeAlpha(t);
            int a = (int) (alpha * 0xFF) << 24;

            int bg = (t.entry.type == ToastType.COMPLETED) ? C_BG_DONE : C_BG_UNLOCK;
            int bar = (t.entry.type == ToastType.COMPLETED) ? C_BAR_DONE : C_BAR_UNLOCK;
            int titleCol = (t.entry.type == ToastType.COMPLETED) ? C_TITLE_DONE : C_TITLE_UNLOCK;

            // Background
            g.fill(x, y, x + TOAST_W, y + TOAST_H, (bg & 0x00FFFFFF) | a);
            // Left accent bar
            g.fill(x, y, x + 2, y + TOAST_H, (bar & 0x00FFFFFF) | a);

            // Icon
            QuestNode node = t.entry.node;
            int textX = x + 5;
            if (node.getIconItem() != null && node.getIconItem() != net.minecraft.world.item.Items.AIR) {
                g.renderItem(new ItemStack(node.getIconItem()), x + 4, y + TOAST_H / 2 - 8);
                textX = x + 22;
            }

            // Labels. Available width must be relative to the toast's OWN left edge (x), not
            // double-subtracted against the absolute screen x too - the old
            // "TOAST_W - textX - x - 8" math went deeply negative for any real x, so
            // plainSubstrByWidth always truncated to nothing and every toast showed just "…".
            int availW = TOAST_W - (textX - x) - 5;
            String label = (t.entry.type == ToastType.COMPLETED) ? "Quest Complete!" : "Quest Unlocked";
            g.drawString(font, "§7" + label, textX, y + 2, (C_LABEL & 0x00FFFFFF) | a, false);
            String rawTitle = node.getTitle().getString();
            String titleStr = font.width(rawTitle) > availW ?
                    font.plainSubstrByWidth(rawTitle, Math.max(0, availW - 6)) + "…" : rawTitle;
            g.drawString(font, titleStr, textX, y + 11, (titleCol & 0x00FFFFFF) | a, false);
        }
    }

    // ── Preset: ABOVE_HOTBAR (wider banner just above the hotbar) ───────────

    private static final int BANNER_W = 200;
    private static final int BANNER_H = 26;

    private void renderAboveHotbar(GuiGraphics g, Font font, int screenW, int screenH, List<ActiveToast> toasts) {
        int x = (screenW - BANNER_W) / 2;
        // Stack upward from just above the hotbar/XP bar, most recent on the bottom.
        int slotY = screenH - 62 - (toasts.size() - 1) * (BANNER_H + GAP);

        for (ActiveToast t : toasts) {
            float alpha = computeAlpha(t);
            int a = (int) (alpha * 0xFF) << 24;
            // Slides straight down into place instead of sideways - reads better centered.
            int slideOff = (int) ((1f - computeX(t)) * 10);
            int y = slotY - slideOff;
            slotY += BANNER_H + GAP;

            int bg = (t.entry.type == ToastType.COMPLETED) ? C_BG_DONE : C_BG_UNLOCK;
            int bar = (t.entry.type == ToastType.COMPLETED) ? C_BAR_DONE : C_BAR_UNLOCK;
            int titleCol = (t.entry.type == ToastType.COMPLETED) ? C_TITLE_DONE : C_TITLE_UNLOCK;

            g.fill(x, y, x + BANNER_W, y + BANNER_H, (bg & 0x00FFFFFF) | a);
            g.fill(x, y, x + BANNER_W, y + 2, (bar & 0x00FFFFFF) | a);

            QuestNode node = t.entry.node;
            int cx = x + BANNER_W / 2;
            g.renderItem(new ItemStack(node.getIconItem() != null ? node.getIconItem() :
                    net.minecraft.world.item.Items.BOOK), cx - 8, y + 4);

            String label = (t.entry.type == ToastType.COMPLETED) ? "Quest Complete!" : "Quest Unlocked";
            g.drawCenteredString(font, "§7" + label, cx, y + 3, (C_LABEL & 0x00FFFFFF) | a);
            String rawTitle = node.getTitle().getString();
            int maxW = BANNER_W - 10;
            String titleStr = font.width(rawTitle) > maxW ?
                    font.plainSubstrByWidth(rawTitle, maxW - 6) + "…" : rawTitle;
            g.drawCenteredString(font, titleStr, cx, y + BANNER_H - 11, (titleCol & 0x00FFFFFF) | a);
        }
    }

    // ── Preset: BIG_CENTER (large, interruptive center-screen text) ─────────

    private void renderBigCenter(GuiGraphics g, Font font, int screenW, int screenH, List<ActiveToast> toasts) {
        int cy = screenH / 2 - 40;
        for (ActiveToast t : toasts) {
            float alpha = computeAlpha(t);
            // Punches in with a quick scale-up instead of sliding, then holds - the sideways
            // slide the other presets use reads as too small a motion to notice mid-screen.
            float scale = 0.6f + 0.4f * Math.min(1f, computeX(t));
            int a = (int) (alpha * 0xFF) << 24;

            QuestNode node = t.entry.node;
            String label = (t.entry.type == ToastType.COMPLETED) ? "QUEST COMPLETE" : "QUEST UNLOCKED";
            int titleCol = (t.entry.type == ToastType.COMPLETED) ? C_TITLE_DONE : C_TITLE_UNLOCK;
            String rawTitle = node.getTitle().getString();

            g.pose().pushPose();
            g.pose().translate(screenW / 2f, cy, 0);
            g.pose().scale(scale, scale, 1f);

            g.drawCenteredString(font, "§l" + label, 0, -14, (C_LABEL & 0x00FFFFFF) | a);
            if (node.getIconItem() != null && node.getIconItem() != net.minecraft.world.item.Items.AIR) {
                g.pose().pushPose();
                g.pose().translate(-12, -4, 0);
                g.pose().scale(1.6f, 1.6f, 1f);
                g.renderItem(new ItemStack(node.getIconItem()), 0, 0);
                g.pose().popPose();
            }
            g.drawCenteredString(font, "§l" + rawTitle, 12, 4, (titleCol & 0x00FFFFFF) | a);

            g.pose().popPose();
            cy += 48;
        }
    }

    // ── Custom per-quest design ──────────────────────────────────────────────

    /**
     * Renders one toast from a freeform per-quest QuestToastConfig - each element (icon, title,
     * label) is independently positioned/scaled/colored, anchored as a fraction of screen size.
     * Shared with ToastDesignerScreen's live preview, so what a pack dev sees while designing is
     * exactly what plays in-game.
     */
    public void renderCustom(GuiGraphics g, Font font, int screenW, int screenH, ActiveToast t,
                             QuestToastConfig cfg) {
        float alpha = computeAlpha(t);
        int a = (int) (alpha * 0xFF) << 24;
        QuestNode node = t.entry.node;
        String label = (t.entry.type == ToastType.COMPLETED) ? "Quest Complete!" : "Quest Unlocked";
        String rawTitle = node.getTitle().getString();

        float tx = cfg.title.x * screenW, ty = cfg.title.y * screenH;
        float lx = cfg.label.x * screenW, ly = cfg.label.y * screenH;
        float ix = cfg.icon.x * screenW, iy = cfg.icon.y * screenH;

        // Background rect spans the union of all three element positions plus padding, so it
        // always visually contains whatever the designer laid out regardless of arrangement.
        float minX = Math.min(tx, Math.min(lx, ix)) - cfg.bgPadX;
        float maxX = Math.max(tx, Math.max(lx, ix)) + cfg.bgPadX;
        float minY = Math.min(ty, Math.min(ly, iy)) - cfg.bgPadY;
        float maxY = Math.max(ty, Math.max(ly, iy)) + cfg.bgPadY;

        g.fill((int) minX, (int) minY, (int) maxX, (int) maxY, (cfg.bgColor & 0x00FFFFFF) | a);
        g.fill((int) minX, (int) minY, (int) minX + 2, (int) maxY, (cfg.accentColor & 0x00FFFFFF) | a);

        drawCustomElement(g, font, cfg.title, rawTitle, screenW, screenH, a);
        drawCustomElement(g, font, cfg.label, label, screenW, screenH, a);

        // Custom icon set overrides the quest's own auto icon when the designer added any;
        // otherwise fall back to the quest's icon like before.
        if (!cfg.icons.isEmpty()) {
            int n = cfg.icons.size();
            int iconPx = Math.round(16 * cfg.icon.scale);
            int gap = 2;
            int totalW = n * iconPx + (n - 1) * gap;
            int sx = Math.round(ix - totalW / 2f);
            int sy = Math.round(iy - iconPx / 2f);
            for (QuestGroup.GroupIcon gi : cfg.icons) {
                renderToastIcon(g, gi, sx, sy, iconPx);
                sx += iconPx + gap;
            }
        } else if (node.getIconItem() != null && node.getIconItem() != net.minecraft.world.item.Items.AIR) {
            g.pose().pushPose();
            g.pose().translate(ix, iy, 0);
            g.pose().scale(cfg.icon.scale, cfg.icon.scale, 1f);
            g.renderItem(new ItemStack(node.getIconItem()), -8, -8);
            g.pose().popPose();
        }

        // Small square Phantasia preview in the toast's top-right corner, on top of everything
        // else - a toast is far too short-lived for the async pattern load to ever finish inside
        // one, so previews are looked up from a shared cache keyed by machine id (see
        // getOrCreatePhantasiaPreview()) instead of being created fresh per toast: the first
        // trigger for a given machine still shows the loading spinner, but every trigger after
        // that reuses the already-loaded preview instead of restarting the load from scratch.
        if (!cfg.phantasiaMachineId.isBlank()) {
            Object preview = getOrCreatePhantasiaPreview(cfg.phantasiaMachineId);
            if (preview != null) {
                int pvSize = 22;
                int pvX = (int) maxX - pvSize - 2;
                int pvY = (int) minY + 2;
                g.fill(pvX - 1, pvY - 1, pvX + pvSize + 1, pvY + pvSize + 1, (0xFF0A0A10 & 0x00FFFFFF) | a);
                PhantasiaCompat.tickPreview(preview);
                PhantasiaCompat.renderPreview(preview, g, pvX, pvY, pvSize, pvSize, 0f);
                logIfStuck(cfg.phantasiaMachineId, preview);
            }
        }
    }

    // Shared cache so repeat toast triggers (and the designer's own preview) reuse an
    // already-loaded preview per machine id, rather than each one starting its own async pattern
    // load that a ~3 second toast has no realistic chance of finishing before it's torn down.
    private static final Map<String, Object> PHANTASIA_PREVIEW_CACHE = new HashMap<>();
    private static final Map<String, Long> PHANTASIA_PREVIEW_CREATED_AT = new HashMap<>();
    // Machine ids we've already logged a "stuck loading" warning for, so it's not spammed once
    // per frame for however long the preview stays unresolved.
    private static final java.util.Set<String> PHANTASIA_LOGGED_STUCK = new java.util.HashSet<>();

    private static Object getOrCreatePhantasiaPreview(String machineId) {
        Object cached = PHANTASIA_PREVIEW_CACHE.get(machineId);
        if (cached != null) return cached;
        Object created = PhantasiaCompat.createPreview(machineId);
        if (created != null) {
            PHANTASIA_PREVIEW_CACHE.put(machineId, created);
            PHANTASIA_PREVIEW_CREATED_AT.put(machineId, System.currentTimeMillis());
        }
        return created;
    }

    /**
     * The whole Phantasia load pipeline (pattern loader thread pool -> onPatternLoaded callback
     * -> RenderSystem.recordRenderCall) is Phantasia's own machinery and isn't something our
     * tick()/render() calls drive or can get stuck waiting on from our side - if a preview still
     * isn't ready (and hasn't reported outright failure either) several seconds after creation,
     * that's surprising enough to be worth a one-time log line pointing at exactly which machine
     * id and how long it's been stuck, instead of silently spinning forever with no trace.
     */
    private static void logIfStuck(String machineId, Object preview) {
        if (PHANTASIA_LOGGED_STUCK.contains(machineId)) return;
        if (PhantasiaCompat.isPreviewReady(preview)) return;
        Long createdAt = PHANTASIA_PREVIEW_CREATED_AT.get(machineId);
        if (createdAt == null) return;
        long age = System.currentTimeMillis() - createdAt;
        if (age < 5000) return;
        PHANTASIA_LOGGED_STUCK.add(machineId);
        System.err.println("[Phoenix Chronicles] Phantasia preview for machine '" + machineId +
                "' still not ready after " + age + "ms (loadFailed=" +
                PhantasiaCompat.isPreviewLoadFailed(preview) + "). This is Phantasia's own async " +
                "load, not something Phoenix Chronicles drives directly - if this machine loads " +
                "quickly from Phantasia's own UI but hangs here, that's worth reporting upstream.");
    }

    /** Draws one custom toast icon (item, fluid swatch, or arbitrary texture) at the given rect. */
    private void renderToastIcon(GuiGraphics g, QuestGroup.GroupIcon icon, int x, int y, int size) {
        try {
            switch (icon.kind) {
                case ITEM -> {
                    Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(icon.id));
                    if (item == null || item == net.minecraft.world.item.Items.AIR) return;
                    float scale = size / 16f;
                    g.pose().pushPose();
                    g.pose().translate(x + size / 2f, y + size / 2f, 0f);
                    g.pose().scale(scale, scale, scale);
                    g.renderItem(new ItemStack(item), -8, -8);
                    g.pose().popPose();
                }
                case FLUID -> {
                    Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(icon.id));
                    if (fluid == null || fluid == Fluids.EMPTY) return;
                    int col = IClientFluidTypeExtensions.of(fluid).getTintColor() | 0xFF000000;
                    g.fill(x, y, x + size, y + size, col);
                }
                case TEXTURE -> g.blit(new ResourceLocation(icon.id), x, y, 0, 0, size, size, size, size);
            }
        } catch (Exception ignored) {
            // Bad/renamed registry id or texture path — skip this icon rather than crash the frame.
        }
    }

    /** Draws one freeform-positioned text element (title or label) of a custom toast design. */
    private void drawCustomElement(GuiGraphics g, Font font, QuestToastConfig.Element el, String text,
                                   int screenW, int screenH, int alpha) {
        String display = (el.bold ? "§l" : "") + text;
        float x = el.x * screenW, y = el.y * screenH;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(el.scale, el.scale, 1f);
        g.drawCenteredString(font, display, 0, -font.lineHeight / 2, (el.color & 0x00FFFFFF) | alpha);
        g.pose().popPose();
    }

    private float computeX(ActiveToast t) {
        if (t.ticksAlive < SLIDE_TICKS) {
            return t.ticksAlive / (float) SLIDE_TICKS;
        }
        return 1.0f;
    }

    private float computeAlpha(ActiveToast t) {
        int fadeStart = SLIDE_TICKS + STAY_TICKS;
        if (t.ticksAlive >= fadeStart) {
            int fadeAge = t.ticksAlive - fadeStart;
            return 1.0f - (fadeAge / (float) FADE_TICKS);
        }
        if (t.ticksAlive < SLIDE_TICKS) {
            return t.ticksAlive / (float) SLIDE_TICKS;
        }
        return 1.0f;
    }

    /** Builds a fully-settled (post-slide-in, pre-fade-out) fake toast for ToastDesignerScreen's live preview. */
    public static ActiveToast makePreviewToast(QuestNode node, ToastType type) {
        ActiveToast t = new ActiveToast(new ToastEntry(node, type));
        t.ticksAlive = SLIDE_TICKS + 1;
        return t;
    }

    private record ToastEntry(QuestNode node, ToastType type) {}

    public static class ActiveToast {

        final ToastEntry entry;
        int ticksAlive = 0;

        ActiveToast(ToastEntry e) {
            this.entry = e;
        }
    }
}
