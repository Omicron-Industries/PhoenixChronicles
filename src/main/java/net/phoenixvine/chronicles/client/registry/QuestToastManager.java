package net.phoenixvine.chronicles.client.registry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.chronicles.model.GroupIcon;
import net.phoenixvine.chronicles.model.IconKind;
import net.phoenixvine.chronicles.model.QuestNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuestToastManager {

    public enum ToastType {
        UNLOCKED,
        COMPLETED
    }

    private static final int TOAST_W = 124;
    private static final int TOAST_H = 20;
    private static final int MARGIN = 4;
    private static final int GAP = 3;
    private static final int SLIDE_TICKS = 8;
    private static final int STAY_TICKS = 48;
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

    public void tick() {
        active.removeIf(t -> t.ticksAlive > SLIDE_TICKS + STAY_TICKS + FADE_TICKS);
        while (active.size() < MAX_VISIBLE && !queue.isEmpty()) {
            active.add(new ActiveToast(queue.pollFirst()));
        }
        for (ActiveToast t : active) t.ticksAlive++;
    }

    public void render(GuiGraphics g, int screenW, int screenH) {
        if (active.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        QuestChroniclesSettings.ToastStyle defaultStyle = QuestChroniclesSettings.get().getToastStyle();

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

        int slotY = top ? screenH / 4 : screenH - screenH / 4 - stackH;

        for (ActiveToast t : toasts) {
            float progress = computeX(t);
            int x;
            if (right) {
                x = (int) (screenW - MARGIN - (TOAST_W * progress));
            } else if (left) {
                x = (int) (MARGIN - TOAST_W + (TOAST_W * progress));
            } else {
                x = (screenW - TOAST_W) / 2;
            }
            int y = slotY;
            slotY += TOAST_H + GAP;

            float alpha = computeAlpha(t);
            int a = (int) (alpha * 0xFF) << 24;

            int bg = (t.entry.type == ToastType.COMPLETED) ? C_BG_DONE : C_BG_UNLOCK;
            int bar = (t.entry.type == ToastType.COMPLETED) ? C_BAR_DONE : C_BAR_UNLOCK;
            int titleCol = (t.entry.type == ToastType.COMPLETED) ? C_TITLE_DONE : C_TITLE_UNLOCK;

            g.fill(x, y, x + TOAST_W, y + TOAST_H, (bg & 0x00FFFFFF) | a);

            g.fill(x, y, x + 2, y + TOAST_H, (bar & 0x00FFFFFF) | a);

            QuestNode node = t.entry.node;
            int textX = x + 5;
            if (node.getIconItem() != null && node.getIconItem() != net.minecraft.world.item.Items.AIR) {
                renderFadedItem(g, new ItemStack(node.getIconItem()), x + 4, y + TOAST_H / 2 - 8, alpha);
                textX = x + 22;
            }

            int availW = TOAST_W - (textX - x) - 5;
            String label = (t.entry.type == ToastType.COMPLETED) ? "Quest Complete!" : "Quest Unlocked";
            g.drawString(font, "§7" + label, textX, y + 2, (C_LABEL & 0x00FFFFFF) | a, false);
            String rawTitle = node.getTitle().getString();
            String titleStr = font.width(rawTitle) > availW ?
                    font.plainSubstrByWidth(rawTitle, Math.max(0, availW - 6)) + "…" : rawTitle;
            g.drawString(font, titleStr, textX, y + 11, (titleCol & 0x00FFFFFF) | a, false);
        }
    }

    private static final int BANNER_W = 200;
    private static final int BANNER_H = 26;

    private void renderAboveHotbar(GuiGraphics g, Font font, int screenW, int screenH, List<ActiveToast> toasts) {
        int x = (screenW - BANNER_W) / 2;

        int slotY = screenH - 62 - (toasts.size() - 1) * (BANNER_H + GAP);

        for (ActiveToast t : toasts) {
            float alpha = computeAlpha(t);
            int a = (int) (alpha * 0xFF) << 24;

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
            renderFadedItem(g, new ItemStack(node.getIconItem() != null ? node.getIconItem() :
                    net.minecraft.world.item.Items.BOOK), cx - 8, y + 4, alpha);

            String label = (t.entry.type == ToastType.COMPLETED) ? "Quest Complete!" : "Quest Unlocked";
            g.drawCenteredString(font, "§7" + label, cx, y + 3, (C_LABEL & 0x00FFFFFF) | a);
            String rawTitle = node.getTitle().getString();
            int maxW = BANNER_W - 10;
            String titleStr = font.width(rawTitle) > maxW ?
                    font.plainSubstrByWidth(rawTitle, maxW - 6) + "…" : rawTitle;
            g.drawCenteredString(font, titleStr, cx, y + BANNER_H - 11, (titleCol & 0x00FFFFFF) | a);
        }
    }

    private void renderBigCenter(GuiGraphics g, Font font, int screenW, int screenH, List<ActiveToast> toasts) {
        int cy = screenH / 2 - 40;
        for (ActiveToast t : toasts) {
            float alpha = computeAlpha(t);

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
                renderFadedItem(g, new ItemStack(node.getIconItem()), 0, 0, alpha);
                g.pose().popPose();
            }
            g.drawCenteredString(font, "§l" + rawTitle, 12, 4, (titleCol & 0x00FFFFFF) | a);

            g.pose().popPose();
            cy += 48;
        }
    }

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

        float minX, maxX, minY, maxY;
        if (cfg.bgAutoFit) {

            minX = Math.min(tx, Math.min(lx, ix)) - cfg.bgPadX;
            maxX = Math.max(tx, Math.max(lx, ix)) + cfg.bgPadX;
            minY = Math.min(ty, Math.min(ly, iy)) - cfg.bgPadY;
            maxY = Math.max(ty, Math.max(ly, iy)) + cfg.bgPadY;
        } else {

            float bx = cfg.bgX * screenW, by = cfg.bgY * screenH;
            minX = bx - cfg.bgPadX;
            maxX = bx + cfg.bgPadX;
            minY = by - cfg.bgPadY;
            maxY = by + cfg.bgPadY;
        }

        g.fill((int) minX, (int) minY, (int) maxX, (int) maxY, (cfg.bgColor & 0x00FFFFFF) | a);
        g.fill((int) minX, (int) minY, (int) minX + 2, (int) maxY, (cfg.accentColor & 0x00FFFFFF) | a);

        drawCustomElement(g, font, cfg.title, rawTitle, screenW, screenH, a, cfg.bgPadX);
        drawCustomElement(g, font, cfg.label, label, screenW, screenH, a, cfg.bgPadX);

        if (!cfg.icons.isEmpty()) {
            for (QuestToastConfig.IconEntry entry : cfg.icons) {
                int iconPx = Math.round(16 * entry.scale);
                int ex = Math.round(entry.x * screenW) - iconPx / 2;
                int ey = Math.round(entry.y * screenH) - iconPx / 2;
                renderToastIcon(g, new GroupIcon(entry.kind, entry.id), ex, ey, iconPx, alpha);
            }
        } else if (node.getIconItem() != null && node.getIconItem() != net.minecraft.world.item.Items.AIR) {
            g.pose().pushPose();
            g.pose().translate(ix, iy, 0);
            g.pose().scale(cfg.icon.scale, cfg.icon.scale, 1f);
            renderFadedItem(g, new ItemStack(node.getIconItem()), -8, -8, alpha);
            g.pose().popPose();
        }

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

    public static float[] computeAutoFitCenter(QuestToastConfig cfg) {
        float tx = cfg.title.x, ty = cfg.title.y;
        float lx = cfg.label.x, ly = cfg.label.y;
        float ix = cfg.icon.x, iy = cfg.icon.y;
        float minX = Math.min(tx, Math.min(lx, ix));
        float maxX = Math.max(tx, Math.max(lx, ix));
        float minY = Math.min(ty, Math.min(ly, iy));
        float maxY = Math.max(ty, Math.max(ly, iy));
        return new float[] { (minX + maxX) / 2f, (minY + maxY) / 2f };
    }

    private static final Map<String, Object> PHANTASIA_PREVIEW_CACHE = new HashMap<>();
    private static final Map<String, Long> PHANTASIA_PREVIEW_CREATED_AT = new HashMap<>();

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

    private void renderToastIcon(GuiGraphics g, GroupIcon icon, int x, int y, int size, float alpha) {
        try {
            if (icon.kind == IconKind.ITEM) {
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(icon.id));
                if (item == null || item == net.minecraft.world.item.Items.AIR) return;
                float scale = size / 16f;
                g.pose().pushPose();
                try {
                    g.pose().translate(x + size / 2f, y + size / 2f, 0f);
                    g.pose().scale(scale, scale, scale);
                    renderFadedItem(g, new ItemStack(item), -8, -8, alpha);
                } finally {
                    g.pose().popPose();
                }
            } else if (icon.kind == IconKind.FLUID) {
                Fluid fluid = ForgeRegistries.FLUIDS.getValue(ResourceLocation.parse(icon.id));
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
                try {
                    net.phoenixvine.chronicles.client.render.ChroniclesUIKit.drawFluidIcon(g, fluid, x, y, size);
                } finally {
                    com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                }
            } else if (icon.kind == IconKind.TEXTURE) {
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
                try {
                    g.blit(ResourceLocation.parse(icon.id), x, y, 0, 0, size, size, size, size);
                } finally {
                    com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                }
            }
        } catch (Exception ignored) {

        }
    }

    private static void renderFadedItem(GuiGraphics g, ItemStack stack, int x, int y, float alpha) {
        if (alpha >= 0.999f) {
            g.renderItem(stack, x, y);
            return;
        }
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        try {
            g.renderItem(stack, x, y);
        } finally {
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private void drawCustomElement(GuiGraphics g, Font font, QuestToastConfig.Element el, String text,
                                   int screenW, int screenH, int alpha, float bgHalfWidth) {
        String display = (el.bold ? "§l" : "") + text;
        float x = el.x * screenW, y = el.y * screenH;
        float screenRoomHalf = Math.min(x, screenW - x) - 8f;
        float widthCapLocal = Math.min(bgHalfWidth * 2 - 16, screenRoomHalf * 2);
        int maxWidth = Math.max(20, Math.round(widthCapLocal / el.scale));

        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(el.scale, el.scale, 1f);
        List<net.minecraft.util.FormattedCharSequence> lines = font
                .split(net.minecraft.network.chat.Component.literal(display), maxWidth);
        int totalH = lines.size() * font.lineHeight;
        int ly = -totalH / 2;

        float worldTop = y + ly * el.scale;
        float worldBottom = y + (ly + totalH) * el.scale;
        if (worldTop < 0) ly -= Math.round(worldTop / el.scale);
        else if (worldBottom > screenH) ly -= Math.round((worldBottom - screenH) / el.scale);
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            g.drawString(font, line, -font.width(line) / 2, ly, (el.color & 0x00FFFFFF) | alpha);
            ly += font.lineHeight;
        }
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
