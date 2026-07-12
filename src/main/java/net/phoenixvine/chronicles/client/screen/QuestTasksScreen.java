package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.model.*;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.C2SAcknowledgeInfoTasksPacket;
import net.phoenixvine.chronicles.network.packet.C2SClaimQuestRewardPacket;
import net.phoenixvine.chronicles.registry.ChroniclesTheme;
import net.phoenixvine.chronicles.tasks.*;

import java.util.List;

/**
 * Quest detail screen — two states:
 * compact (isFullscreen=false): small card overlay on the parent quest graph
 * fullscreen (isFullscreen=true): full-screen panel with rich task inspector
 */
public class QuestTasksScreen extends Screen {

    // Themed color fields — populated from ChroniclesTheme in init()
    private int C_BG = 0xFF0B0B0F;
    private int C_PANEL = 0xFF14141A;
    private int C_HEADER = 0xFF0C0C10;
    private int C_BORDER = 0xFF353548;
    private int C_DONE = 0xFF44CC88;
    private int C_ACTIVE = 0xFFFFBB33;
    private int C_LOCKED = 0xFF606070;
    private int C_TEXT = 0xFFD8D8E4;
    private int C_TEXT_DIM = 0xFF7A7A8A;
    private int C_TEXT_FAINT = 0xFF404050;

    private static final int C_SLOT_BG = 0xFF0E0E14;
    private static final int C_SLOT_HI = 0xFF5A4200;

    // Fullscreen layout constants
    private static final int HEADER_H = 28;
    private static final int ICON_SZ = 18;   // task/reward icon size in the icon strip
    private static final int ICON_STRIP_H = ICON_SZ + 8;  // 26px — slim row below header
    private static final int FOOTER_H = 22;
    private static final int MARGIN = 6;
    private static final int REWARD_W = 185;
    private static final int TASK_ICON_SZ = 24;   // kept for compact card use

    // Compact card constants
    private static final int CARD_MAX_WIDTH = 310;

    /**
     * Compact card width, capped to the window width minus margin. This used to be a bare
     * constant used at every call site - on a narrow window (small resolution, or a high GUI
     * Scale setting shrinking the logical screen width below ~330px) the card simply rendered
     * wider than the screen with no clamp, cutting off both edges instead of shrinking to fit.
     */
    private int cardW() {
        return Math.min(CARD_MAX_WIDTH, Math.max(120, width - 24));
    }

    private static final int CARD_PAD = 6;
    private static final int CARD_TASK_ROW_H = 22;
    private static final int CARD_MAX_TASKS = 6;
    // The description is the main content of the card (info sits above it in the header/icon
    // strip) - give it real room to be useful instead of clipping after a handful of lines.
    private static final int CARD_MAX_DESC = 24;

    // State
    private final Screen parent;
    private final QuestNode node;
    private final FullQuestData content;
    private final PlayerQuestData playerData;
    private final Player player;

    private int descScrollY = 0;
    private java.util.List<net.phoenixvine.chronicles.client.rich.RichSpan.Region> richRegions = java.util.List.of();
    private java.util.List<net.phoenixvine.chronicles.client.rich.RichSpan> richSpans = java.util.List.of();
    private long openTimeMs = -1;
    private static final long OPEN_FADE_MS = 100;
    private int inspectorTab = 2; // default to Tasks tab
    private int inspectorScrollY = 0;
    private int inspectorContentH = 0; // tracked last frame for clamping
    private boolean isFullscreen = false;

    // Embedded live Phantasia build preview — shown as part of the quest's own content when the
    // quest has a preview machine id set (either directly, or implied by a view_machine task).
    // Stored as Object (see PhantasiaCompat) to avoid a hard compile-time Phantasia dependency.
    private Object phantasiaPreview;
    private int previewX, previewY, previewW, previewH;

    public QuestTasksScreen(Screen parent, QuestNode node, FullQuestData content, PlayerQuestData playerData) {
        super(Component.literal("Quest Details"));
        this.parent = parent;
        this.node = node;
        this.content = content;
        this.playerData = playerData;
        this.player = net.minecraft.client.Minecraft.getInstance().player;
    }

    @Override
    protected void init() {
        super.init();
        openTimeMs = System.currentTimeMillis();
        isEditMode = player != null && (player.isCreative() || player.hasPermissions(2));
        phantasiaPreview = net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.createPreviewForNode(node);

        // Auto-acknowledge all InfoTasks client-side immediately (responsive UI),
        // and tell the server to do the same so it can advance quest state.
        boolean hasInfoTasks = node.getTasks().stream().anyMatch(t2 -> t2 instanceof InfoTask);
        if (hasInfoTasks && playerData != null) {
            for (var task : node.getTasks()) {
                if (task instanceof InfoTask) {
                    InfoTask.acknowledge(player, task.getTaskId());
                }
            }
            ChronicleNetwork.CHANNEL.sendToServer(new C2SAcknowledgeInfoTasksPacket(node.getId()));
        }

        ChroniclesTheme t = ChroniclesTheme.current();
        C_BG = t.bg.getColor();
        C_PANEL = t.panel.getColor();
        C_HEADER = t.header.getColor();
        C_BORDER = t.border.getColor();
        C_TEXT = t.text.getColor();
        C_TEXT_DIM = t.textDim.getColor();
        C_TEXT_FAINT = t.textFaint.getColor();
        C_DONE = t.done.getColor();
        C_ACTIVE = t.activeColor.getColor();
        C_LOCKED = t.locked.getColor();
    }

    // ── Top-level render ──────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        // This screen opens on top of the quest canvas mid-frame (from a node click), and the
        // canvas can leave an active scissor rect from whatever was last clipped there (e.g. a
        // scrolled panel) - without clearing it first, the very first fills below
        // (overview.renderForChildScreen()'s background) get clipped to that stale rect instead
        // of covering the whole screen, leaving old canvas pixels showing through elsewhere for
        // the frame, which read as this "non-fullscreen" card bleeding the canvas behind it.
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();

        if (isFullscreen) {
            renderFullscreen(g, mx, my, partial);
        } else {
            renderCompact(g, mx, my, partial);
        }

        if (openTimeMs > 0) {
            long elapsed = System.currentTimeMillis() - openTimeMs;
            if (elapsed < OPEN_FADE_MS) {
                float frac = 1f - (float) elapsed / OPEN_FADE_MS;
                int alpha = (int) (frac * frac * 0xFF) & 0xFF;
                if (alpha > 0) g.fill(0, 0, width, height, (alpha << 24));
            }
        }

        // Rich-text hover tooltips (rendered last so they appear above everything). Popping the
        // pose stack above only resets the matrix, not the depth buffer already written by the
        // panel content (up to z=400 in renderFullscreen/renderCompact) and the parent canvas's
        // node icons (z=100) drawn earlier this frame - without its own elevated z this tooltip
        // lost the depth test against them. Push above the highest z used elsewhere in this screen.
        for (net.phoenixvine.chronicles.client.rich.RichSpan.Region r : richRegions) {
            if (r.contains(mx, my) && r.span() instanceof net.phoenixvine.chronicles.client.rich.RichSpan.Tip t) {
                g.pose().pushPose();
                g.pose().translate(0f, 0f, 500f);
                g.flush();
                g.renderTooltip(font, Component.literal(t.tooltip()), mx, my);
                g.flush();
                g.pose().popPose();
                break;
            }
        }
    }

    // ── Compact card ──────────────────────────────────────────────────────────

    // Compact list rows: each task row = status mark + icon + name + progress + bar
    private static final int TASK_LIST_ROW_H = 24; // 14px text area + 4px bar + 6px gap
    // Reward mini-slots shown inline in the tasks header
    private static final int REWARD_MINI_SZ = 14;

    private static final int PREVIEW_H_COMPACT = 46;

    /** Extra height the embedded Phantasia preview block reserves in the compact card, or 0 if none. */
    private int previewBlockH() {
        return phantasiaPreview != null ? PREVIEW_H_COMPACT + 1 : 0;
    }

    private int compactCardH(List<QuestTask> tasks, List<QuestReward> rewards,
                             java.util.List<net.minecraft.util.FormattedCharSequence> descLines) {
        // header(20) + divider(1) + icon strip + pad(2) + divider(1) + footer(18)
        int fixedH = 20 + 1 + ICON_STRIP_H + 2 + 1 + 18 + previewBlockH();
        int allDescLines = buildAllDescLines(tasks, descLines).size();
        int rawDesc = Math.min(allDescLines, CARD_MAX_DESC);
        int fitted = Math.max(0, Math.min(rawDesc, ((height - 20) - fixedH - 9) / 10));
        // In edit mode, reserve a minimum-size placeholder box even with no description yet,
        // so there's always a click target to add one.
        int descH = fitted > 0 ? 4 + fitted * 10 + 4 : (isEditMode ? 24 : 0);
        return fixedH + descH + (descH > 0 ? 1 : 0);
    }

    /** Combines InfoTask bodies (word-wrapped) with the quest description lines for the bottom section. */
    private java.util.List<net.minecraft.util.FormattedCharSequence> buildAllDescLines(
                                                                                       List<QuestTask> tasks,
                                                                                       java.util.List<net.minecraft.util.FormattedCharSequence> questDescLines) {
        java.util.List<net.minecraft.util.FormattedCharSequence> all = new java.util.ArrayList<>();
        for (QuestTask task : tasks) {
            if (task instanceof InfoTask info) {
                String body = info.getBody();
                if (body != null && !body.isBlank()) {
                    all.addAll(font.split(Component.literal(body), cardW() - CARD_PAD * 2));
                }
            }
        }
        if (!all.isEmpty() && !questDescLines.isEmpty()) {
            // blank separator line between info body and quest description
            all.add(net.minecraft.util.FormattedCharSequence.EMPTY);
        }
        all.addAll(questDescLines);
        return all;
    }

    // Tracked during renderCompact for tooltip and click handling
    private QuestTask hoveredTask = null;
    private QuestReward hoveredReward = null;
    private int hoveredRewardIndex = -1;
    private int hoveredSlotX, hoveredSlotY; // screen position of hovered slot

    // Whether the current player can edit quest content (creative or op) - gates the
    // click-to-edit affordance on the compact card's description box.
    private boolean isEditMode = false;
    private boolean hoveredDescBox = false;
    private int descBoxX, descBoxY, descBoxW, descBoxH;
    // The compact card silently truncated long descriptions to whatever fit in CARD_MAX_DESC
    // lines with no way to see the rest short of noticing the tiny "[+]" expand button - scrolled
    // in place instead, same as the fullscreen view already does via descScrollY.
    private int compactDescScrollLine = 0;
    private int compactDescTotalLines = 0;
    private int compactDescFittedLines = 0;
    // Same edit-box affordance as the compact card's description, for the fullscreen content panel.
    private boolean hoveredFsDescBox = false;
    private int fsDescBoxX, fsDescBoxY, fsDescBoxW, fsDescBoxH;

    // Tracked during renderFullscreen inspector for tooltip and click handling
    private QuestTask hoveredTaskFs = null;
    private QuestReward hoveredRewardFs = null;
    private int hoveredRewardFsIndex = -1;
    private int hoveredFsX, hoveredFsY;

    // Hovered icon in the icon strip (task or reward), for tooltip
    private QuestTask hoveredStripTask = null;
    private QuestReward hoveredStripReward = null;
    private int hoveredStripRewardIdx = -1;
    private int hoveredStripX, hoveredStripY;

    private void renderCompact(GuiGraphics g, int mx, int my, float partial) {
        hoveredTask = null;
        hoveredReward = null;
        hoveredRewardIndex = -1;

        // Render parent graph at base z (no widget buttons, no tooltips, flushed)
        if (parent instanceof ChronicleOverviewScreen overview) {
            overview.renderForChildScreen(g);
        } else if (parent != null) {
            parent.render(g, -9999, -9999, partial);
            g.flush();
            com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        } else {
            g.fill(0, 0, width, height, C_BG);
        }

        // Force everything the parent (including quest node icons, which g.renderItem() writes
        // to the depth buffer at z=100) just queued to actually submit before this card starts
        // drawing - renderForChildScreen() already flushes internally, but this pack's rendering
        // pipeline has repeatedly shown it needs an explicit flush at every layer boundary, not
        // just an upstream one, or content from the layer below can still bleed through what's
        // supposed to be an opaque layer on top of it (this was the clicked quest's own canvas
        // icon showing through above the card instead of staying hidden behind it).
        g.flush();

        // Elevate z so the card is ALWAYS drawn in front of any parent content,
        // regardless of render-buffer ordering or leftover scissor state.
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);
        // Same missing-flush bleed-through bug fixed elsewhere this session - without this, a
        // quest node's own icon (z=100 via g.renderItem()) could still win the depth test
        // against this dim fill and show through it specifically, instead of just reading as
        // part of the deliberately-dimmed parent canvas behind everything else.
        g.flush();
        g.fill(0, 0, width, height, 0x88000000);

        List<QuestTask> tasks = node.getTasks();
        List<QuestReward> rewards = node.getRewards();
        // .md companion content wins if present (matches renderContent's fullscreen behavior),
        // but fall back to the quest's own description instead of showing nothing when there's
        // no .md yet - which was previously every freshly-imported/created quest.
        String mdDescText = (content != null && content.description() != null) ? content.description().getString() : "";
        String descText = !mdDescText.isBlank() ? mdDescText : node.getDescription().getString();
        java.util.List<net.minecraft.util.FormattedCharSequence> questDescLines = descText != null ?
                font.split(Component.literal(descText), cardW() - CARD_PAD * 2) : java.util.List.of();
        java.util.List<net.minecraft.util.FormattedCharSequence> descLines = buildAllDescLines(tasks, questDescLines);

        // Layout constants — icon strip replaces the old per-task row list
        int fixedH = 20 + 1 + ICON_STRIP_H + 2 + 1 + 18 + previewBlockH();
        int rawDesc = Math.min(descLines.size(), CARD_MAX_DESC);
        int fittedDesc = Math.max(0, Math.min(rawDesc, ((height - 20) - fixedH - 9) / 10));
        int descH = fittedDesc > 0 ? 4 + fittedDesc * 10 + 4 : (isEditMode ? 24 : 0);
        int cardH = fixedH + descH + (descH > 0 ? 1 : 0);

        int cardX = (width - cardW()) / 2;
        int cardY = Math.max(10, (height - cardH) / 2);

        // Shadow + body
        g.fill(cardX + 3, cardY + 3, cardX + cardW() + 3, cardY + cardH + 3, 0x66000000);
        g.fill(cardX, cardY, cardX + cardW(), cardY + cardH, C_BG);
        drawBorder(g, cardX, cardY, cardW(), cardH);

        // Everything below is clipped to the card's own bounds so nothing (long descriptions,
        // overflowing icon strips, etc.) can visually bleed out past the card and onto the
        // dimmed overview canvas behind it. Tooltips are drawn after disableScissor further down.
        g.enableScissor(cardX, cardY, cardX + cardW(), cardY + cardH);

        int cy = cardY;

        // ── Header ──────────────────────────────────────────────────────────
        g.fill(cardX, cy, cardX + cardW(), cy + 20, C_HEADER);
        String title = node.getTitle().getString();
        if (font.width(title.replaceAll("§.", "")) > cardW() - 50)
            title = font.plainSubstrByWidth(title, cardW() - 56) + "…";
        g.drawString(font, "§f" + title, cardX + CARD_PAD, cy + 6, C_TEXT, false);
        // [+] expand to fullscreen
        boolean fsHov = mx >= cardX + cardW() - 18 && mx < cardX + cardW() - 4 && my >= cy + 3 && my < cy + 17;
        if (fsHov) g.fill(cardX + cardW() - 18, cy + 3, cardX + cardW() - 4, cy + 17, 0x33FFFFFF);
        g.drawCenteredString(font, fsHov ? "§b[+]" : "§8[+]", cardX + cardW() - 11, cy + 6,
                fsHov ? C_ACTIVE : C_TEXT_FAINT);
        // [x] close card
        boolean closeHov = mx >= cardX + cardW() - 34 && mx < cardX + cardW() - 20 && my >= cy + 3 && my < cy + 17;
        if (closeHov) g.fill(cardX + cardW() - 34, cy + 3, cardX + cardW() - 20, cy + 17, 0x33FFFFFF);
        g.drawCenteredString(font, closeHov ? "§c✕" : "§8✕", cardX + cardW() - 27, cy + 6,
                closeHov ? 0xFFFF6666 : C_TEXT_FAINT);
        cy += 20;
        g.fill(cardX, cy, cardX + cardW(), cy + 1, C_BORDER);
        cy += 1;

        // ── Icon strip (tasks then rewards, FTB-style) ──────────────────────
        {
            int sz = ICON_SZ;
            int gap = 3;
            int ix = cardX + CARD_PAD + 2;
            int iy = cy + (ICON_STRIP_H - sz) / 2;

            for (QuestTask task : tasks) {
                if (ix + sz > cardX + cardW() - CARD_PAD) break;
                boolean done = isTaskDone(task);
                boolean hov = mx >= ix && mx < ix + sz && my >= iy && my < iy + sz;
                int border = done ? C_DONE : (task.isOptional() ? C_TEXT_FAINT : C_ACTIVE);
                int bg = done ? 0xFF0A1A0E : 0xFF0F0F18;
                if (hov) {
                    hoveredTask = task;
                    hoveredSlotX = ix;
                    hoveredSlotY = iy;
                }
                drawIconSlot(g, ix, iy, sz, bg, border, hov);
                ItemStack icon = getTaskIcon(task);
                int off = (sz - 16) / 2;
                if (!icon.isEmpty()) {
                    g.renderItem(icon, ix + off, iy + off);
                    if (done) g.fill(ix, iy, ix + sz, iy + sz, 0x5500CC55);
                } else {
                    g.drawCenteredString(font, done ? "§a✔" : "§c✗", ix + sz / 2, iy + sz / 2 - 4, 0xFFFFFFFF);
                }
                if (done) {
                    g.fill(ix + sz - 7, iy + sz - 8, ix + sz, iy + sz, 0xFF0A2210);
                    g.drawString(font, "§a✔", ix + sz - 7, iy + sz - 8, 0xFFFFFFFF, false);
                }
                ix += sz + gap;
            }

            if (!tasks.isEmpty() && !rewards.isEmpty()) {
                g.fill(ix + 1, iy + 2, ix + 2, iy + sz - 2, C_BORDER);
                ix += 6;
            }

            boolean claimed = rewardsClaimed();
            for (int ri = 0; ri < rewards.size(); ri++) {
                if (ix + sz > cardX + cardW() - CARD_PAD) break;
                QuestReward reward = rewards.get(ri);
                boolean hov = mx >= ix && mx < ix + sz && my >= iy && my < iy + sz;
                int border = claimed ? C_DONE : C_TEXT_FAINT;
                int bg = claimed ? 0xFF1A140A : 0xFF0F0F18;
                if (hov) {
                    hoveredReward = reward;
                    hoveredRewardIndex = ri;
                    hoveredSlotX = ix;
                    hoveredSlotY = iy;
                }
                drawIconSlot(g, ix, iy, sz, bg, border, hov);
                int off = (sz - 16) / 2;
                if (reward instanceof QuestReward.ItemReward ir) {
                    g.renderItem(new ItemStack(ir.getItem(), ir.getCount()), ix + off, iy + off);
                    if (claimed) g.fill(ix, iy, ix + sz, iy + sz, 0x55CC8800);
                } else {
                    String glyph = switch (reward.getType()) {
                        case XP -> "⚡";
                        case COMMAND -> "◆";
                        case LOOT_TABLE -> "📦";
                        case SCRIPT_EVENT -> "✦";
                        default -> "?";
                    };
                    g.drawCenteredString(font, "§7" + glyph, ix + sz / 2, iy + sz / 2 - 4, C_TEXT_DIM);
                    if (claimed) g.fill(ix, iy, ix + sz, iy + sz, 0x55CC8800);
                }
                if (claimed) {
                    g.fill(ix + sz - 7, iy + sz - 8, ix + sz, iy + sz, 0xFF1A1000);
                    g.drawString(font, "§6✔", ix + sz - 7, iy + sz - 8, 0xFFFFFFFF, false);
                }
                ix += sz + gap;
            }
            cy += ICON_STRIP_H;
        }
        cy += 2;
        g.fill(cardX, cy, cardX + cardW(), cy + 1, C_BORDER);
        cy += 1;

        // ── Embedded Phantasia build preview — part of the quest's own content, shown whether
        // or not the quest also has a view_machine task requiring it to be viewed. Rendered as a
        // small square anchored to the card's top-left instead of a full-width bar, so it reads
        // as an inset thumbnail rather than eating the whole row.
        if (phantasiaPreview != null) {
            int pvSz = PREVIEW_H_COMPACT; // square: width == height
            int pvX = cardX + CARD_PAD;
            int pvY = cy + 1;
            g.fill(pvX, pvY, pvX + pvSz, pvY + pvSz - 2, 0xFF0A0A10);
            drawBorder(g, pvX, pvY, pvSz, pvSz - 2);
            previewX = pvX + 1;
            previewY = pvY + 1;
            previewW = pvSz - 2;
            previewH = pvSz - 4;
            net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.tickPreview(phantasiaPreview);
            net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.renderPreview(phantasiaPreview, g,
                    previewX, previewY, previewW, previewH, partial);
            cy += PREVIEW_H_COMPACT;
            g.fill(cardX, cy, cardX + cardW(), cy + 1, C_BORDER);
            cy += 1;
        }

        // ── Description (bottom) - the card's main content area ─────────────
        hoveredDescBox = false;
        if (descH > 0) {
            descBoxX = cardX;
            descBoxY = cy;
            descBoxW = cardW();
            descBoxH = descH;
            hoveredDescBox = isEditMode && mx >= descBoxX && mx < descBoxX + descBoxW && my >= descBoxY &&
                    my < descBoxY + descBoxH;

            if (isEditMode) {
                g.fill(cardX + 2, cy + 1, cardX + cardW() - 2, cy + descH - 1,
                        hoveredDescBox ? 0xFF1C1C26 : 0xFF15151C);
                int dashColor = hoveredDescBox ? C_ACTIVE : C_TEXT_FAINT;
                for (int dx = cardX + 2; dx < cardX + cardW() - 2; dx += 4) {
                    g.fill(dx, cy + 1, dx + 2, cy + 2, dashColor);
                    g.fill(dx, cy + descH - 2, dx + 2, cy + descH - 1, dashColor);
                }
            }

            compactDescTotalLines = descLines.size();
            compactDescFittedLines = fittedDesc;
            int maxScrollLine = Math.max(0, compactDescTotalLines - compactDescFittedLines);
            compactDescScrollLine = Math.max(0, Math.min(compactDescScrollLine, maxScrollLine));

            int dy = cy + 4;
            if (fittedDesc == 0 && isEditMode) {
                g.drawString(font, "§8Click to add a description", cardX + CARD_PAD, dy, C_TEXT_FAINT, false);
            }
            for (int i = 0; i < fittedDesc; i++) {
                int lineIdx = compactDescScrollLine + i;
                if (lineIdx >= descLines.size()) break;
                g.drawString(font, descLines.get(lineIdx), cardX + CARD_PAD, dy, C_TEXT_DIM, false);
                dy += 10;
            }
            // Scroll hint - only shown when there's actually more text than fits, so it doesn't
            // clutter short descriptions.
            if (maxScrollLine > 0) {
                String hint = compactDescScrollLine < maxScrollLine ? "§7▼ scroll for more" : "§7▲ scroll up";
                g.drawString(font, hint, cardX + cardW() - font.width(hint) - CARD_PAD - 2,
                        cy + descH - 11, C_TEXT_FAINT, false);
            } else if (isEditMode) {
                String hint = "§7✎ edit";
                g.drawString(font, hint, cardX + cardW() - font.width(hint) - CARD_PAD - 2,
                        cy + descH - 11, hoveredDescBox ? C_ACTIVE : C_TEXT_FAINT, false);
            }
            cy += descH;
            g.fill(cardX, cy, cardX + cardW(), cy + 1, C_BORDER);
            cy += 1;
        }

        // ── Footer ──────────────────────────────────────────────────────────
        renderCompactFooter(g, cardX, cy, cardW(), 18, mx, my);

        g.disableScissor();

        // ── Tooltip for hovered slot (drawn last, at z+200 so it's above the card) ──
        if (hoveredTask != null) {
            g.pose().translate(0f, 0f, 200f);
            g.renderComponentTooltip(font, buildTaskTooltip(hoveredTask), mx, my);
        } else if (hoveredReward != null) {
            g.pose().translate(0f, 0f, 200f);
            g.renderComponentTooltip(font, buildRewardTooltip(hoveredReward), mx, my);
        }

        g.pose().popPose();
    }

    private void renderCompactTaskRow(GuiGraphics g, int x, int y, int w, QuestTask task, int mx, int my) {
        boolean done = isTaskDone(task);
        String progress = taskProgressString(task);
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + TASK_LIST_ROW_H - 6;
        if (hov) {
            hoveredTask = task;
            hoveredSlotX = x;
            hoveredSlotY = y;
        }

        // Row hover highlight
        if (hov) g.fill(x, y, x + w, y + TASK_LIST_ROW_H - 6, 0x18FFFFFF);

        // Left accent bar: green=done, orange=active, grey=optional
        int accent = done ? C_DONE : (task.isOptional() ? C_TEXT_FAINT : C_ACTIVE);
        g.fill(x, y + 1, x + 2, y + TASK_LIST_ROW_H - 7, accent);

        // Status mark
        String mark = done ? "§a✔" : (task.isOptional() ? "§8○" : "§c✗");
        g.drawString(font, mark, x + 4, y + 3, 0xFFFFFFFF, false);

        // Item icon (16x16) or type glyph
        int textX = x + 16;
        ItemStack icon = getTaskIcon(task);
        if (!icon.isEmpty()) {
            g.renderItem(icon, textX, y);
            textX += 18;
        } else {
            g.drawString(font, getTaskGlyph(task), textX, y + 3, 0xFFFFFFFF, false);
            textX += 10;
        }

        // Task description — use getTaskDetail() if the description looks like a raw id, else description
        String desc = task.getDescription().getString();
        String detail = getTaskDetail(task);
        // Prefer detail when description is blank or contains only lowercase+underscores (raw id)
        boolean descIsId = desc.isEmpty() || desc.matches("[a-z0-9_]+");
        String primary = (descIsId && detail != null) ? detail : desc;

        // Right-aligned progress counter
        String prog = done ? "§a✔" : (progress != null ? "§8" + progress : "");
        int progW = prog.isEmpty() ? 0 : font.width(prog) + 2;
        int labelW = w - (textX - x) - progW - 2;
        if (font.width(primary) > labelW)
            primary = font.plainSubstrByWidth(primary, labelW - 5) + "…";
        g.drawString(font, (done ? "§7" : "§f") + primary, textX, y + 3, done ? C_TEXT_DIM : C_TEXT, false);
        if (!prog.isEmpty()) {
            g.drawString(font, prog, x + w - progW, y + 3, C_TEXT_FAINT, false);
        }

        // Thin progress bar spanning full row width below the text
        float pct = done ? 1f : parseProgress(progress);
        int barY = y + 14;
        g.fill(x, barY, x + w, barY + 3, 0xFF1A1A22);
        if (pct > 0) g.fill(x, barY, x + (int) (w * pct), barY + 3, done ? C_DONE : C_ACTIVE);
    }

    private void renderCompactFooter(GuiGraphics g, int cardX, int cy, int cardW, int h, int mx, int my) {
        QuestState state = playerData != null ? playerData.getQuestState(node.getId(), QuestState.LOCKED) :
                QuestState.LOCKED;
        boolean canClaim = state == QuestState.COMPLETED && !rewardsClaimed() && !node.getRewards().isEmpty();

        if (canClaim) {
            int btnW = 120;
            int btnX = cardX + (cardW - btnW) / 2;
            int btnY = cy + 1;
            boolean hov = mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + h - 2;
            g.fill(btnX, btnY, btnX + btnW, btnY + h - 2, hov ? 0xFF2A4A2A : 0xFF1A2A1A);
            g.fill(btnX, btnY, btnX + btnW, btnY + 1, hov ? C_DONE : 0xFF333333);
            g.drawCenteredString(font, "§a✓ Claim Rewards", btnX + btnW / 2, btnY + 4, hov ? C_DONE : C_TEXT);
        } else if (rewardsClaimed()) {
            g.drawCenteredString(font, "§8Rewards Claimed", cardX + cardW / 2, cy + 5, C_TEXT_FAINT);
        } else {
            g.drawCenteredString(font, "§8Complete Tasks to Claim", cardX + cardW / 2, cy + 5, C_TEXT_FAINT);
        }
    }

    // ── Fullscreen ────────────────────────────────────────────────────────────

    private void renderFullscreen(GuiGraphics g, int mx, int my, float partial) {
        hoveredTaskFs = null;
        hoveredRewardFs = null;

        // Guard against a scissor rect left active from a previous frame (the compact card and
        // its sub-panels push/pop several) - an unbalanced one here would clip this fill and let
        // whatever was rendered last (the overview screen) show through at the edges instead of
        // a clean black backdrop.
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        g.fill(0, 0, width, height, 0xFF000000);
        renderHeader(g, mx, my);
        renderRequirementsBar(g, mx, my);

        // Adapt inspector/reward widths so the content panel never shrinks below ~180px.
        // Rewards get their own always-visible column on the far right instead of a tab
        // sharing space with tasks - so you never have to leave the Tasks tab to see them.
        int inspW = calcInspW();
        int rewardW = calcRewardW();
        int contentTop = HEADER_H + reqBarH() + MARGIN;
        int contentRight = width - inspW - rewardW - MARGIN * 3;
        int contentH = height - contentTop - FOOTER_H - MARGIN;

        g.fill(MARGIN, contentTop, contentRight, contentTop + contentH, C_PANEL);
        drawBorder(g, MARGIN, contentTop, contentRight - MARGIN, contentH);
        renderContent(g, MARGIN + 6, contentTop + 6, contentRight - MARGIN - 12, contentH - 12, mx, my, partial);

        int inspX = contentRight + MARGIN;
        renderInspector(g, inspX, contentTop, inspW, contentH, mx, my);

        int rewardX = inspX + inspW + MARGIN;
        renderRewardsPanel(g, rewardX, contentTop, rewardW, contentH, mx, my);

        renderFooter(g, mx, my);

        // Fullscreen tooltips rendered after everything else
        g.pose().pushPose();
        g.pose().translate(0f, 0f, 400f);
        if (hoveredStripTask != null) {
            g.renderComponentTooltip(font, buildTaskTooltip(hoveredStripTask), mx, my);
        } else if (hoveredStripReward != null) {
            g.renderComponentTooltip(font, buildRewardTooltip(hoveredStripReward), mx, my);
        } else if (hoveredTaskFs != null) {
            g.renderComponentTooltip(font, buildTaskTooltip(hoveredTaskFs), mx, my);
        } else if (hoveredRewardFs != null) {
            g.renderComponentTooltip(font, buildRewardTooltip(hoveredRewardFs), mx, my);
        }
        g.pose().popPose();
    }

    private void renderHeader(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, HEADER_H, C_HEADER);
        g.fill(0, HEADER_H - 1, width, HEADER_H, C_BORDER);

        if (mx >= 4 && mx < 20 && my >= 6 && my < 22) g.fill(4, 6, 20, 22, 0x22FFFFFF);
        g.drawCenteredString(font, "§7←", 12, 10, C_TEXT_DIM);
        // Right-side buttons (right → left): pin, edit, collapse
        boolean pinned = playerData != null && playerData.isPinned(node.getId());
        int pinX = width - 20;
        int editX = pinX - 18;
        int fsX = editX - 20;
        int titleMaxW = fsX - 32;

        String titleStr = node.getTitle().getString();
        if (font.width(titleStr.replaceAll("§.", "")) > titleMaxW)
            titleStr = font.plainSubstrByWidth(titleStr, titleMaxW - 6) + "…";
        g.drawString(font, "§f" + titleStr, 28, 10, C_TEXT, false);

        // [-] collapse to compact
        if (mx >= fsX && mx < fsX + 16 && my >= 6 && my < 22) g.fill(fsX, 6, fsX + 16, 22, 0x22FFFFFF);
        g.drawCenteredString(font, "§d[-]", fsX + 8, 10, 0xFFAA44FF);

        // ✎ style editor
        boolean editHov = mx >= editX && mx < editX + 14 && my >= 6 && my < 22;
        if (editHov) g.fill(editX, 6, editX + 14, 22, 0x22FFFFFF);
        g.drawCenteredString(font, editHov ? "§e✎" : "§8✎", editX + 7, 10, editHov ? 0xFFFFDD44 : C_TEXT_FAINT);

        // 📌 pin
        if (mx >= pinX && mx < width - 4 && my >= 6 && my < 22) g.fill(pinX, 6, width - 4, 22, 0x22FFFFFF);
        g.drawCenteredString(font, pinned ? "§d📌" : "§8📌", width - 12, 10, pinned ? 0xFFAA44FF : C_TEXT_FAINT);
    }

    /** Actual height of the requirements bar — collapses to 1 when the quest has no tasks. */
    private int reqBarH() {
        boolean hasIcons = !node.getTasks().isEmpty() || !node.getRewards().isEmpty();
        return hasIcons ? ICON_STRIP_H : 1;
    }

    /** Slim FTB-style icon strip: task icons | divider | reward icons. Hover for tooltip, click to jump to tab. */
    private void renderRequirementsBar(GuiGraphics g, int mx, int my) {
        hoveredStripTask = null;
        hoveredStripReward = null;
        hoveredStripRewardIdx = -1;

        int barH = reqBarH();
        g.fill(0, HEADER_H, width, HEADER_H + barH, C_PANEL);
        g.fill(0, HEADER_H, width, HEADER_H + 1, C_BORDER);
        g.fill(0, HEADER_H + barH - 1, width, HEADER_H + barH, C_BORDER);
        if (barH <= 1) return;

        int iconY = HEADER_H + (ICON_STRIP_H - ICON_SZ) / 2;
        int iconX = MARGIN + 2;
        int sz = ICON_SZ;
        int gap = 3;

        List<QuestTask> tasks = node.getTasks();
        List<QuestReward> rewards = node.getRewards();

        // ── Task icons ───────────────────────────────────────────────────────
        for (QuestTask task : tasks) {
            boolean done = isTaskDone(task);
            boolean hov = mx >= iconX && mx < iconX + sz && my >= iconY && my < iconY + sz;
            int border = done ? C_DONE : (task.isOptional() ? C_TEXT_FAINT : C_ACTIVE);
            int bg = done ? 0xFF0A1A0E : 0xFF0F0F18;

            if (hov) {
                hoveredStripTask = task;
                hoveredStripX = iconX;
                hoveredStripY = iconY;
            }

            drawIconSlot(g, iconX, iconY, sz, bg, border, hov);

            ItemStack icon = getTaskIcon(task);
            int off = (sz - 16) / 2;
            if (!icon.isEmpty()) {
                g.renderItem(icon, iconX + off, iconY + off);
                if (done) g.fill(iconX, iconY, iconX + sz, iconY + sz, 0x5500CC55);
            } else {
                g.drawCenteredString(font, done ? "§a✔" : "§c✗", iconX + sz / 2, iconY + sz / 2 - 4, 0xFFFFFFFF);
            }
            // Checkmark badge bottom-right corner
            if (done) {
                g.fill(iconX + sz - 7, iconY + sz - 8, iconX + sz, iconY + sz, 0xFF0A2210);
                g.drawString(font, "§a✔", iconX + sz - 7, iconY + sz - 8, 0xFFFFFFFF, false);
            }
            iconX += sz + gap;
        }

        // ── Divider between tasks and rewards ────────────────────────────────
        if (!tasks.isEmpty() && !rewards.isEmpty()) {
            g.fill(iconX + 1, iconY + 2, iconX + 2, iconY + sz - 2, C_BORDER);
            iconX += 6;
        }

        // ── Reward icons ─────────────────────────────────────────────────────
        boolean claimed = rewardsClaimed();
        for (int ri = 0; ri < rewards.size(); ri++) {
            QuestReward reward = rewards.get(ri);
            boolean hov = mx >= iconX && mx < iconX + sz && my >= iconY && my < iconY + sz;
            int border = claimed ? C_DONE : C_TEXT_FAINT;
            int bg = claimed ? 0xFF1A140A : 0xFF0F0F18;

            if (hov) {
                hoveredStripReward = reward;
                hoveredStripRewardIdx = ri;
                hoveredStripX = iconX;
                hoveredStripY = iconY;
            }

            drawIconSlot(g, iconX, iconY, sz, bg, border, hov);

            int off = (sz - 16) / 2;
            if (reward instanceof QuestReward.ItemReward ir) {
                g.renderItem(new ItemStack(ir.getItem(), ir.getCount()), iconX + off, iconY + off);
                if (claimed) g.fill(iconX, iconY, iconX + sz, iconY + sz, 0x55CC8800);
            } else {
                String glyph = switch (reward.getType()) {
                    case XP -> "⚡";
                    case COMMAND -> "◆";
                    case LOOT_TABLE -> "📦";
                    case SCRIPT_EVENT -> "✦";
                    default -> "?";
                };
                g.drawCenteredString(font, "§7" + glyph, iconX + sz / 2, iconY + sz / 2 - 4, C_TEXT_DIM);
                if (claimed) g.fill(iconX, iconY, iconX + sz, iconY + sz, 0x55CC8800);
            }
            // Gold checkmark badge if claimed
            if (claimed) {
                g.fill(iconX + sz - 7, iconY + sz - 8, iconX + sz, iconY + sz, 0xFF1A1000);
                g.drawString(font, "§6✔", iconX + sz - 7, iconY + sz - 8, 0xFFFFFFFF, false);
            }
            iconX += sz + gap;
        }
    }

    private void drawIconSlot(GuiGraphics g, int x, int y, int sz, int bg, int border, boolean hov) {
        g.fill(x, y, x + sz, y + sz, bg);
        if (hov) g.fill(x, y, x + sz, y + sz, 0x22FFFFFF);
        g.fill(x, y, x + sz, y + 1, border);
        g.fill(x, y + sz - 1, x + sz, y + sz, border);
        g.fill(x, y, x + 1, y + sz, border);
        g.fill(x + sz - 1, y, x + sz, y + sz, border);
    }

    private void renderContent(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float partial) {
        // Edit-mode affordance for the description, matching the compact card's dashed edit box.
        fsDescBoxX = x - 6;
        fsDescBoxY = y - 6;
        fsDescBoxW = w + 12;
        fsDescBoxH = h + 12;
        hoveredFsDescBox = isEditMode && mx >= fsDescBoxX && mx < fsDescBoxX + fsDescBoxW && my >= fsDescBoxY &&
                my < fsDescBoxY + fsDescBoxH;
        if (isEditMode) {
            int dashColor = hoveredFsDescBox ? C_ACTIVE : C_TEXT_FAINT;
            for (int dx = fsDescBoxX; dx < fsDescBoxX + fsDescBoxW; dx += 4) {
                g.fill(dx, fsDescBoxY, dx + 2, fsDescBoxY + 1, dashColor);
                g.fill(dx, fsDescBoxY + fsDescBoxH - 1, dx + 2, fsDescBoxY + fsDescBoxH, dashColor);
            }
            String hint = "§7✎ click to edit";
            g.drawString(font, hint, fsDescBoxX + fsDescBoxW - font.width(hint) - 2, fsDescBoxY - 9,
                    hoveredFsDescBox ? C_ACTIVE : C_TEXT_FAINT, false);
        }

        g.enableScissor(x - 8, y - 8, x + w + 8, y + h + 8);

        // Parse description lazily — .md file content takes priority; fall back to SNBT description
        String mdDesc = (content != null && content.description() != null) ? content.description().getString() : "";
        String descRaw = mdDesc.isBlank() ? node.getDescription().getString() : mdDesc;
        if (richSpans.isEmpty() && !descRaw.isEmpty()) {
            richSpans = net.phoenixvine.chronicles.client.rich.ChronicleTextParser.parse(descRaw);
        }
        if (isEditMode && descRaw.isEmpty() && richSpans.isEmpty()) {
            g.drawString(font, "§8Click to add a description", x, y, C_TEXT_FAINT, false);
        }

        richRegions = net.phoenixvine.chronicles.client.rich.ChronicleRichTextRenderer.render(
                g, font, richSpans, x, y, w, descScrollY, y, y + h);

        int ly = y +
                net.phoenixvine.chronicles.client.rich.ChronicleRichTextRenderer.measureHeight(font, richSpans, w) -
                descScrollY;

        // Embedded Phantasia build preview — part of the quest's own content, appended after the
        // description text in the same scrollable flow as the prerequisites list below it. Rendered
        // as a small square anchored to the left edge instead of a full-width bar, so it reads as an
        // inset thumbnail rather than a divider spanning the whole content column.
        if (phantasiaPreview != null) {
            if (ly > y) ly += 8;
            int pvSz = 64; // square: width == height
            if (ly + pvSz >= y - 10 && ly < y + h) {
                g.fill(x, ly, x + pvSz, ly + pvSz, 0xFF0A0A10);
                drawBorder(g, x, ly, pvSz, pvSz);
                previewX = x + 1;
                previewY = ly + 1;
                previewW = pvSz - 2;
                previewH = pvSz - 2;
                net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.tickPreview(phantasiaPreview);
                net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.renderPreview(phantasiaPreview, g,
                        previewX, previewY, previewW, previewH, partial);
            }
            ly += pvSz + 8;
        }

        List<QuestNode> prereqs = node.getPrerequisites();
        if (!prereqs.isEmpty()) {
            if (ly > y) ly += 8;
            if (ly < y + h) {
                if (ly >= y - 10) g.drawString(font, "§8PREREQUISITES:", x, ly, C_TEXT_FAINT, false);
                ly += 12;
            }
            for (QuestNode req : prereqs) {
                if (ly >= y + h) break;
                QuestState state = playerData != null ? playerData.getQuestState(req.getId(), QuestState.LOCKED) :
                        QuestState.LOCKED;
                String reqTitle = req.getTitle().getString();
                if (font.width(reqTitle.replaceAll("§.", "")) > w - 12)
                    reqTitle = font.plainSubstrByWidth(reqTitle, Math.max(0, w - 18)) + "…";
                if (ly >= y - 10) g.drawString(font, (state == QuestState.COMPLETED ? "§a●" : "§8○") + " §7" + reqTitle,
                        x, ly, C_TEXT_DIM, false);
                ly += 10;
            }
        }
        g.disableScissor();
    }

    // ── Inspector panel ───────────────────────────────────────────────────────

    private static final String[] INSP_TABS = { "Info", "Prereqs", "Tasks" };
    private static final int INSP_TAB_H = 16;

    private void renderInspector(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        g.enableScissor(x, y, x + w, y + h);
        g.fill(x, y, x + w, y + h, C_PANEL);
        drawBorder(g, x, y, w, h);

        int tabX = x + 6;
        int tabY = y + 4;
        for (int i = 0; i < INSP_TABS.length; i++) {
            String label = INSP_TABS[i];
            int tabW = font.width(label) + 6;
            boolean active = inspectorTab == i;
            boolean hov = mx >= tabX && mx < tabX + tabW && my >= tabY && my < tabY + INSP_TAB_H;
            if (active) {
                g.fill(tabX, tabY, tabX + tabW, tabY + INSP_TAB_H, 0xFF1A1A26);
                g.fill(tabX, tabY + INSP_TAB_H - 1, tabX + tabW, tabY + INSP_TAB_H, C_DONE);
            } else if (hov) {
                g.fill(tabX, tabY, tabX + tabW, tabY + INSP_TAB_H, 0x22FFFFFF);
            }
            g.drawString(font, (active ? "§a" : "§8") + label, tabX + 3, tabY + 3, C_TEXT_DIM, false);
            tabX += tabW + 2;
        }

        int cY = y + INSP_TAB_H + 6;
        int cH = h - INSP_TAB_H - 12;
        // Clamp scroll: content must not scroll past the bottom
        int maxScroll = Math.max(0, inspectorContentH - cH + 8);
        inspectorScrollY = Math.max(0, Math.min(inspectorScrollY, maxScroll));

        g.enableScissor(x, cY, x + w, cY + cH);
        switch (inspectorTab) {
            case 0 -> renderInfoTab(g, x, cY, w, cH);
            case 1 -> renderPrereqsTab(g, x, cY, w, cH);
            case 2 -> renderTasksTab(g, x, cY, w, cH, mx, my);
        }
        g.disableScissor(); // pop inner (content area) scissor
        g.disableScissor(); // pop outer (full inspector panel) scissor
    }

    private void renderInfoTab(GuiGraphics g, int x, int y, int w, int h) {
        int m = 6;
        int cy = y - inspectorScrollY + 4;
        g.drawString(font, "§8Category:", x + m, cy, C_TEXT_FAINT, false);
        g.drawString(font, "§7" + (node.getCategory() != null ? node.getCategory() : "(none)"), x + m, cy + 10, C_TEXT,
                false);
        cy += 24;
        g.drawString(font, "§8Visibility:", x + m, cy, C_TEXT_FAINT, false);
        g.drawString(font, "§7" + (node.getVisibility() != null ? node.getVisibility() : "NORMAL"), x + m, cy + 10,
                C_TEXT, false);
        cy += 24;
        g.drawString(font, "§8ID:", x + m, cy, C_TEXT_FAINT, false);
        g.drawString(font, "§7" + (node.getId() != null ? node.getId() : "unknown"), x + m, cy + 10, C_TEXT, false);
        cy += 24;

        // InfoTask bodies — displayed here instead of as a task row
        for (QuestTask task : node.getTasks()) {
            if (!(task instanceof InfoTask info)) continue;
            String body = info.getBody();
            if (body == null || body.isBlank()) continue;
            if (cy > y + h) break;
            g.fill(x + m, cy, x + w - m, cy + 1, C_BORDER);
            cy += 6;
            for (var line : font.split(Component.literal(body), w - m * 2)) {
                if (cy > y + h) break;
                g.drawString(font, line, x + m, cy, C_TEXT_DIM, false);
                cy += 10;
            }
            cy += 4;
        }
    }

    private void renderPrereqsTab(GuiGraphics g, int x, int y, int w, int h) {
        List<QuestNode> prereqs = node.getPrerequisites();
        int m = 6;
        int cy = y - inspectorScrollY + 4;
        if (prereqs.isEmpty()) {
            g.drawString(font, "§8(none)", x + m, cy, C_TEXT_FAINT, false);
            return;
        }
        for (QuestNode req : prereqs) {
            if (cy > y + h) break;
            QuestState state = playerData != null ? playerData.getQuestState(req.getId(), QuestState.LOCKED) :
                    QuestState.LOCKED;
            String reqTitle = req.getTitle().getString();
            int titleMaxW = w - m * 2 - 12;
            if (font.width(reqTitle.replaceAll("§.", "")) > titleMaxW)
                reqTitle = font.plainSubstrByWidth(reqTitle, Math.max(0, titleMaxW - 6)) + "…";
            g.drawString(font, (state == QuestState.COMPLETED ? "§a●" : "§8○") + " §7" + reqTitle,
                    x + m, cy, C_TEXT_DIM, false);
            cy += 10;
        }
    }

    private void renderTasksTab(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        List<QuestTask> tasks = node.getTasks();
        int m = 6;
        int cy = y - inspectorScrollY + 4;
        if (tasks.isEmpty()) {
            g.drawString(font, "§8(none)", x + m, cy, C_TEXT_FAINT, false);
            return;
        }
        for (QuestTask task : tasks) {
            if (task instanceof InfoTask) continue; // body shown in Info tab
            int rowEndY = renderRichTaskRow(g, x + m, cy, w - m * 2, task, mx, my);
            if (mx >= x + m && mx < x + w - m && my >= cy && my < rowEndY - 5) {
                hoveredTaskFs = task;
                hoveredFsX = (int) mx;
                hoveredFsY = (int) my;
            }
            cy = rowEndY;
        }
        inspectorContentH = (cy - (y - inspectorScrollY + 4));
    }

    /** Returns the y position after the rendered row (including gap). */
    private int renderRichTaskRow(GuiGraphics g, int x, int y, int w, QuestTask task, int mx, int my) {
        boolean done = isTaskDone(task);
        String progress = taskProgressString(task);

        // Hover highlight
        boolean rowHov = mx >= x && mx < x + w && my >= y && my < y + 30;
        if (rowHov) g.fill(x, y, x + w, y + 30, 0x14FFFFFF);

        // Status mark
        g.drawString(font, done ? "§a✔" : (task.isOptional() ? "§8○" : "§c✗"), x, y + 1, 0xFFFFFFFF, false);

        // Icon or glyph
        int cx = x + 10;
        ItemStack icon = getTaskIcon(task);
        if (!icon.isEmpty()) {
            g.renderItem(icon, cx, y);
            if (done) g.fill(cx, y, cx + 16, y + 16, 0x5500AA44);
            cx += 18;
        } else {
            g.drawString(font, getTaskGlyph(task), cx, y + 1, 0xFFFFFFFF, false);
            cx += 10;
        }

        // Description — truncate to leave room for progress counter on the right
        String desc = task.getDescription().getString();
        int progW = (progress != null && !done) ? font.width(progress) + 4 : 0;
        int descAvailW = w - (cx - x) - progW;
        if (font.width(desc) > descAvailW) desc = font.plainSubstrByWidth(desc, Math.max(0, descAvailW - 6)) + "…";
        g.drawString(font, "§f" + desc, cx, y + 1, done ? C_DONE : C_TEXT, false);

        // Detail line
        String detail = getTaskDetail(task);
        if (detail != null) {
            int detailAvailW = w - (cx - x);
            if (font.width(detail) > detailAvailW)
                detail = font.plainSubstrByWidth(detail, Math.max(0, detailAvailW - 6)) + "…";
            g.drawString(font, "§8" + detail, cx, y + 11, C_TEXT_FAINT, false);
        }

        // Progress bar + text
        float pct = done ? 1f : parseProgress(progress);
        int barY = y + (detail != null ? 22 : 14);
        int barW = w - 12;
        g.fill(x + 10, barY, x + 10 + barW, barY + 3, 0xFF1A1A22);
        if (pct > 0) g.fill(x + 10, barY, x + 10 + (int) (barW * pct), barY + 3, done ? C_DONE : C_ACTIVE);
        if (progress != null) {
            g.drawString(font, "§8" + progress, x + w - font.width(progress), barY - 1, C_TEXT_FAINT, false);
        }

        return barY + 3 + 5; // 5px gap between tasks
    }

    /**
     * Always-visible rewards column, separate from the Info/Prereqs/Tasks tabs so you never
     * have to switch away from Tasks to see what a quest grants. Has its own frame/header and
     * clips via scissor rather than sharing the tabs' scroll state.
     */
    private void renderRewardsPanel(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        g.enableScissor(x, y, x + w, y + h);
        g.fill(x, y, x + w, y + h, C_PANEL);
        drawBorder(g, x, y, w, h);

        int m = 6;
        g.drawString(font, "§8Rewards", x + m, y + 5, C_TEXT_FAINT, false);
        g.fill(x + m, y + 15, x + w - m, y + 16, C_BORDER);

        List<QuestReward> rewards = node.getRewards();
        int cy = y + 21;
        if (rewards.isEmpty()) {
            g.drawString(font, "§8(none)", x + m, cy, C_TEXT_FAINT, false);
            g.disableScissor();
            return;
        }

        if (node.isRewardChoice()) {
            String choiceHdr = node.getRewardChoiceCount() == 1 ? "§6Pick 1 reward:" :
                    "§6Pick " + node.getRewardChoiceCount() + " reward(s):";
            g.drawString(font, choiceHdr, x + m, cy, C_TEXT, false);
            cy += 12;
        }

        int slotSz = 18;
        for (int ri = 0; ri < rewards.size(); ri++) {
            QuestReward reward = rewards.get(ri);
            if (cy > y + h) break;
            renderRewardSlot(g, x + m, cy, slotSz, reward, mx, my);

            boolean rowHov = mx >= x + m && mx < x + m + w - m * 2 && my >= cy && my < cy + slotSz;
            if (rowHov) {
                hoveredRewardFs = reward;
                hoveredRewardFsIndex = ri;
                hoveredFsX = (int) mx;
                hoveredFsY = (int) my;
            }

            String label;
            if (reward instanceof QuestReward.ItemReward ir) {
                label = ir.getItem().getDefaultInstance().getHoverName().getString() + " ×" + ir.getCount();
            } else {
                label = switch (reward.getType()) {
                    case XP -> "XP Reward";
                    case COMMAND -> "Command";
                    case LOOT_TABLE -> "Loot Table";
                    case SCRIPT_EVENT -> "Script Event";
                    default -> reward.getType().name();
                };
            }
            int labelMaxW = w - m * 2 - slotSz - 8;
            if (font.width(label.replaceAll("§.", "")) > labelMaxW)
                label = font.plainSubstrByWidth(label, Math.max(0, labelMaxW - 6)) + "…";
            String prefix = node.isRewardChoice() ? (rowHov ? "§e► §f" : "§e○ §7") : (rowHov ? "§f" : "§7");
            g.drawString(font, prefix + label, x + m + slotSz + 4, cy + 4, rowHov ? C_TEXT : C_TEXT_DIM, false);
            cy += slotSz + 4;
        }
        g.disableScissor();
    }

    private void renderRewardSlot(GuiGraphics g, int x, int y, int sz, QuestReward reward, int mx, int my) {
        boolean hov = mx >= x && mx < x + sz && my >= y && my < y + sz;
        g.fill(x, y, x + sz, y + sz, hov ? C_SLOT_HI : C_SLOT_BG);
        g.fill(x, y, x + sz, y + 1, C_BORDER);
        g.fill(x, y + sz - 1, x + sz, y + sz, C_BORDER);
        g.fill(x, y, x + 1, y + sz, C_BORDER);
        g.fill(x + sz - 1, y, x + sz, y + sz, C_BORDER);

        if (reward instanceof QuestReward.ItemReward ir) {
            int off = (sz - 16) / 2;
            g.renderItem(new ItemStack(ir.getItem(), ir.getCount()), x + off, y + off);
            if (sz >= 18) g.renderItemDecorations(font, new ItemStack(ir.getItem(), ir.getCount()), x + off, y + off);
        } else {
            String glyph = switch (reward.getType()) {
                case XP -> "⚡";
                case COMMAND -> "◆";
                case LOOT_TABLE -> "📦";
                case SCRIPT_EVENT -> "✦";
                default -> "?";
            };
            g.drawCenteredString(font, "§7" + glyph, x + sz / 2, y + sz / 2 - 4, C_TEXT_DIM);
        }
    }

    private void renderFooter(GuiGraphics g, int mx, int my) {
        int footerY = height - FOOTER_H;
        g.fill(0, footerY, width, height, C_HEADER);
        g.fill(0, footerY, width, footerY + 1, C_BORDER);

        QuestState state = playerData != null ? playerData.getQuestState(node.getId(), QuestState.LOCKED) :
                QuestState.LOCKED;
        boolean canClaim = state == QuestState.COMPLETED && !rewardsClaimed() && !node.getRewards().isEmpty();

        if (canClaim) {
            int btnW = 140, btnX = (width - btnW) / 2, btnY = footerY + 2;
            if (node.isRewardChoice()) {
                String msg = node.getRewardChoiceCount() == 1 ? "§6Pick a reward ↑" :
                        "§6Pick " + node.getRewardChoiceCount() + " rewards ↑";
                g.drawCenteredString(font, msg, width / 2, btnY + 6, C_TEXT);
            } else {
                boolean hov = mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + 18;
                g.fill(btnX, btnY, btnX + btnW, btnY + 18, hov ? 0xFF2A4A2A : 0xFF1A2A1A);
                g.fill(btnX, btnY, btnX + btnW, btnY + 1, hov ? C_DONE : 0xFF333333);
                g.drawCenteredString(font, "§a✓ Claim Rewards", btnX + btnW / 2, btnY + 6, hov ? C_DONE : C_TEXT);
            }
        } else if (rewardsClaimed()) {
            g.drawCenteredString(font, "§8Rewards claimed", width / 2, footerY + 10, C_TEXT_FAINT);
        } else {
            String footerMsg = width < 220 ? "§8Complete tasks first" : "§8Complete all tasks to claim rewards";
            g.drawCenteredString(font, footerMsg, width / 2, footerY + 10, C_TEXT_FAINT);
        }
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h) {
        ChroniclesUIKit.drawBorder(g, x, y, w, h, C_BORDER);
    }

    // ── Task display helpers ──────────────────────────────────────────────────

    private ItemStack getTaskIcon(QuestTask task) {
        ResourceLocation id = task.getDisplayItemId();
        if (id == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return (item != null && item != net.minecraft.world.item.Items.AIR) ? new ItemStack(item) : ItemStack.EMPTY;
    }

    private String getTaskGlyph(QuestTask task) {
        if (task instanceof ItemRequirementTask) return "§6■";
        if (task instanceof CraftItemTask) return "§e⚒";
        if (task instanceof KillEntityTask) return "§c⚔";
        if (task instanceof FluidRequirementTask) return "§3≋";
        if (task instanceof ExperienceTask) return "§a✦";
        if (task instanceof StatTrackerTask) return "§9≡";
        if (task instanceof AdvancementTask) return "§d★";
        if (task instanceof CheckmarkTask) return "§7☑";
        if (task instanceof InfoTask) return "§7✎";
        if (task instanceof TagItemTask) return "§e◈";
        if (task instanceof EnergyStorageTask) return "§6⚡";
        if (task instanceof FilterItemTask) return "§e◈";
        if (task instanceof FilterFluidTask) return "§3◈";
        if (task instanceof net.phoenixvine.chronicles.tasks.ViewMachineTask) return "§b⬡";
        if (task instanceof net.phoenixvine.chronicles.tasks.ViewSceneTask) return "§b⬢";
        return "§8◇";
    }

    private String getTaskDetail(QuestTask task) {
        if (task instanceof ItemRequirementTask t) {
            String name = t.getItem() != null ? t.getItem().getDefaultInstance().getHoverName().getString() : "item";
            return name + (t.getRequiredCount() > 1 ? "  ×" + t.getRequiredCount() : "") +
                    (t.shouldConsume() ? "  (consumed)" : "");
        }
        if (task instanceof CraftItemTask t) {
            Item item = t.getItemId() != null ? ForgeRegistries.ITEMS.getValue(t.getItemId()) : null;
            String name = item != null ? item.getDefaultInstance().getHoverName().getString() :
                    (t.getItemId() != null ? t.getItemId().getPath() : "item");
            return "Craft: " + name + (t.getRequiredCount() > 1 ? " ×" + t.getRequiredCount() : "");
        }
        if (task instanceof KillEntityTask t) {
            String entity = t.getEntityId() != null ? prettifyId(t.getEntityId()) : "entity";
            return "Kill: " + entity + " ×" + t.getRequiredCount();
        }
        if (task instanceof FluidRequirementTask t) {
            String fluid = t.getFluidId() != null ? prettifyId(t.getFluidId()) : "fluid";
            return fluid + " — " + t.getRequiredAmount() + " mB";
        }
        if (task instanceof ExperienceTask t) {
            return "Reach Level " + t.getRequiredLevel();
        }
        if (task instanceof StatTrackerTask t) {
            String stat = t.getStatId() != null ? prettifyId(t.getStatId()) : "stat";
            return stat + " → " + t.getTargetValue();
        }
        if (task instanceof AdvancementTask t) {
            String adv = t.getAdvancementId() != null ?
                    t.getAdvancementId().getPath().replace('/', ' ').replace('_', ' ') : "advancement";
            return "Unlock: " + adv;
        }
        if (task instanceof InfoTask) {
            return null; // body text is rendered in the description section, not the task row
        }
        if (task instanceof TagItemTask t) {
            String tag = t.getTag() != null ? "#" + t.getTag().location().getPath() : "#unknown";
            return tag + " ×" + t.getRequired();
        }
        if (task instanceof FilterItemTask t) {
            return t.getFilter().describe() + " ×" + t.getCount() + (t.isConsume() ? "  (consumed)" : "");
        }
        if (task instanceof FilterFluidTask t) {
            return t.getFilter().describe() + " — " + String.format("%,d", t.getAmount()) + " mB" +
                    (t.isConsume() ? "  (consumed)" : "");
        }
        if (task instanceof net.phoenixvine.chronicles.tasks.ViewMachineTask t) {
            return "Phantasia: " + t.getMachineId();
        }
        if (task instanceof net.phoenixvine.chronicles.tasks.ViewSceneTask t) {
            return "Phantasia scene: " + t.getSceneId();
        }
        return null;
    }

    private static String prettifyId(ResourceLocation id) {
        return id.getPath().replace('_', ' ');
    }

    private java.util.List<Component> buildTaskTooltip(QuestTask task) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        boolean done = isTaskDone(task);
        String status = done ? "§a✔ Complete" : (task.isOptional() ? "§8Optional" : "§c✗ Incomplete");
        lines.add(Component.literal(status + "  §7" + task.getDescription().getString()));
        String detail = getTaskDetail(task);
        if (detail != null) lines.add(Component.literal("§8" + detail));
        String prog = taskProgressString(task);
        if (prog != null && !done) lines.add(Component.literal("§7Progress: §f" + prog));
        ItemStack icon = getTaskIcon(task);
        if (!icon.isEmpty()) lines.add(Component.literal("§8[Click to view in recipe browser]"));
        return lines;
    }

    private java.util.List<Component> buildRewardTooltip(QuestReward reward) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        if (reward instanceof QuestReward.ItemReward ir) {
            lines.add(Component.literal("§fReward: " + ir.getItem().getDefaultInstance().getHoverName().getString() +
                    " §8×" + ir.getCount()));
            lines.add(Component.literal("§8[Click to view in recipe browser]"));
        } else {
            lines.add(Component.literal("§f" + reward.getType().name() + " Reward"));
        }
        return lines;
    }

    /** Tries to open EMI or JEI for the given ItemStack via reflection (no hard dependency). */
    private void tryOpenInRecipeViewer(ItemStack stack) {
        if (stack.isEmpty() || minecraft == null) return;
        // EMI — displayRecipes() takes the EmiIngredient interface, not the concrete EmiStack
        // class, so getMethod(esClass) never matched (NoSuchMethodException, silently swallowed)
        // and this always fell through to the fullscreen-expand fallback below.
        try {
            Class<?> api = Class.forName("dev.emi.emi.api.EmiApi");
            Class<?> esClass = Class.forName("dev.emi.emi.api.stack.EmiStack");
            Class<?> ingredientClass = Class.forName("dev.emi.emi.api.stack.EmiIngredient");
            Object es = esClass.getMethod("of", ItemStack.class).invoke(null, stack);
            api.getMethod("displayRecipes", ingredientClass).invoke(null, es);
            return;
        } catch (Exception ignored) {}
        // JEI fallback
        try {
            Class<?> jeiApi = Class.forName("mezz.jei.api.runtime.IJeiRuntime");
            // JEI requires more complex setup; just fall through for now
        } catch (Exception ignored) {}
        // Nothing found — expand to fullscreen so the user can see details
        isFullscreen = true;
    }

    private float parseProgress(String prog) {
        if (prog == null) return 0f;
        // Strip thousands separators and trailing unit suffixes (e.g. "1,000 / 4,000 mB" or "5 / 10 XP")
        String cleaned = prog.replaceAll(",", "").replaceAll("(?<=\\d)\\s+[a-zA-Z]+", "");
        int slash = cleaned.indexOf('/');
        if (slash < 0) return 0f;
        try {
            float cur = Float.parseFloat(cleaned.substring(0, slash).trim());
            float tot = Float.parseFloat(cleaned.substring(slash + 1).trim());
            return tot > 0 ? Math.min(1f, cur / tot) : 0f;
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (phantasiaPreview != null && previewW > 0 &&
                net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.previewMouseClicked(phantasiaPreview,
                        mx, my, previewX, previewY, previewW, previewH, this)) {
            return true;
        }
        return isFullscreen ? handleFullscreenClick(mx, my, btn) : handleCompactClick(mx, my, btn);
    }

    private boolean handleCompactClick(double mx, double my, int btn) {
        if (btn == 1 && isEditMode && minecraft != null && (hoveredTask != null || hoveredReward != null)) {
            minecraft.setScreen(new TaskRewardEditorScreen(this, node));
            return true;
        }
        if (btn != 0) return super.mouseClicked(mx, my, btn);

        List<QuestTask> tasks = node.getTasks();
        List<QuestReward> rewards = node.getRewards();
        // .md companion content wins if present (matches renderContent's fullscreen behavior),
        // but fall back to the quest's own description instead of showing nothing when there's
        // no .md yet - which was previously every freshly-imported/created quest.
        String mdDescText = (content != null && content.description() != null) ? content.description().getString() : "";
        String descText = !mdDescText.isBlank() ? mdDescText : node.getDescription().getString();
        java.util.List<net.minecraft.util.FormattedCharSequence> descLines2 = descText != null ?
                font.split(Component.literal(descText), cardW() - CARD_PAD * 2) : java.util.List.of();
        int cardH = compactCardH(tasks, rewards, descLines2);
        int cardX = (width - cardW()) / 2;
        int cardY = Math.max(10, (height - cardH) / 2);

        // Click outside card → close
        if (mx < cardX || mx >= cardX + cardW() || my < cardY || my >= cardY + cardH) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }

        // [x] close button
        if (mx >= cardX + cardW() - 34 && mx < cardX + cardW() - 20 && my >= cardY + 3 && my < cardY + 17) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        // [+] expand button
        if (mx >= cardX + cardW() - 18 && mx < cardX + cardW() - 4 && my >= cardY + 3 && my < cardY + 17) {
            isFullscreen = true;
            return true;
        }

        // Description box — click to open the rich-text editor (edit mode only)
        // Prefills with the raw (untranslated) default: a live lang-registry override should
        // stay in lang/en_us.json, not get shown here and silently re-baked as the new SNBT
        // default the instant this dialog is confirmed without actually changing anything.
        if (hoveredDescBox && isEditMode && minecraft != null) {
            minecraft.setScreen(new QuestTextInputScreen(this, "Description",
                    node.getDescriptionRaw().getString(), 1024,
                    v -> {
                        node.setDescription(Component.literal(v));
                        net.phoenixvine.chronicles.codec.QuestFileSaver.saveAllQuestsToDisk();
                    }));
            return true;
        }

        // Task / reward slot clicks (use hovered slot tracked during last render)
        if (hoveredTask != null) {
            if (tryCompleteCheckmark(hoveredTask)) return true;
            if (net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.canOpenForTask(hoveredTask)) {
                net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.openForTask(hoveredTask, this);
                return true;
            }
            ItemStack icon = getTaskIcon(hoveredTask);
            if (!icon.isEmpty()) tryOpenInRecipeViewer(icon);
            else isFullscreen = true; // non-item tasks → expand for detail
            return true;
        }
        if (hoveredReward != null) {
            if (tryClaimReward(hoveredRewardIndex)) return true;
            if (hoveredReward instanceof QuestReward.ItemReward ir) {
                tryOpenInRecipeViewer(new ItemStack(ir.getItem(), ir.getCount()));
            }
            return true;
        }

        // Claim button in footer
        int footerY = cardY + cardH - 18;
        if (my >= footerY && my < footerY + 18) {
            QuestState state = playerData != null ? playerData.getQuestState(node.getId(), QuestState.LOCKED) :
                    QuestState.LOCKED;
            if (state == QuestState.COMPLETED && !rewardsClaimed() && !rewards.isEmpty()) {
                ChronicleNetwork.CHANNEL.sendToServer(new C2SClaimQuestRewardPacket(node.getId(), -1));
            }
        }
        return true;
    }

    private boolean handleFullscreenClick(double mx, double my, int btn) {
        if (btn == 1 && isEditMode && minecraft != null &&
                (hoveredStripTask != null || hoveredStripReward != null ||
                        hoveredTaskFs != null || hoveredRewardFs != null)) {
            minecraft.setScreen(new TaskRewardEditorScreen(this, node));
            return true;
        }
        // Icon strip — task icon click jumps to the Tasks tab. Rewards are always visible
        // in their own column now, so a reward icon click doesn't need to switch anything.
        if (hoveredStripTask != null) {
            if (tryCompleteCheckmark(hoveredStripTask)) return true;
            inspectorTab = 2; // Tasks
            inspectorScrollY = 0;
            return true;
        }
        if (hoveredStripReward != null) {
            tryClaimReward(hoveredStripRewardIdx);
            return true;
        }

        // ← Back → close
        if (mx >= 4 && mx < 20 && my >= 6 && my < 22) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        // Header right-side buttons (mirror renderHeader layout)
        int pinX2 = width - 20;
        int editX2 = pinX2 - 18;
        int fsX2 = editX2 - 20;
        // [-] collapse
        if (mx >= fsX2 && mx < fsX2 + 16 && my >= 6 && my < 22 && btn == 0) {
            isFullscreen = false;
            return true;
        }
        // ✎ style editor
        if (mx >= editX2 && mx < editX2 + 14 && my >= 6 && my < 22 && btn == 0) {
            if (minecraft != null) minecraft.setScreen(new QuestStyleEditorScreen(this, node));
            return true;
        }
        // Pin — toggles just this quest, leaving any other pinned quests untouched
        if (mx >= pinX2 && mx < width - 4 && my >= 6 && my < 22 && btn == 0) {
            if (playerData != null) {
                playerData.togglePin(node.getId());
                net.phoenixvine.chronicles.network.ChronicleNetwork.CHANNEL.sendToServer(
                        new net.phoenixvine.chronicles.network.packet.C2STogglePinPacket(node.getId()));
            }
            return true;
        }
        // Inspector tabs
        int contentTop = HEADER_H + reqBarH() + MARGIN;
        int inspW2 = calcInspW();
        int contentRight = width - inspW2 - calcRewardW() - MARGIN * 3;
        int rightX = contentRight + MARGIN;
        if (mx >= rightX && mx < rightX + inspW2 && my >= contentTop && my < contentTop + INSP_TAB_H + 8) {
            int tabX = rightX + 6;
            for (int i = 0; i < INSP_TABS.length; i++) {
                int tabW = font.width(INSP_TABS[i]) + 6;
                if (mx >= tabX && mx < tabX + tabW && my >= contentTop + 4 && my < contentTop + 4 + INSP_TAB_H) {
                    inspectorTab = i;
                    inspectorScrollY = 0;
                    return true;
                }
                tabX += tabW + 2;
            }
        }
        // Inspector content clicks (tasks and rewards tabs)
        if (hoveredTaskFs != null) {
            if (tryCompleteCheckmark(hoveredTaskFs)) return true;
            if (net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.canOpenForTask(hoveredTaskFs)) {
                net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.openForTask(hoveredTaskFs, this);
                return true;
            }
            ItemStack icon = getTaskIcon(hoveredTaskFs);
            if (!icon.isEmpty()) tryOpenInRecipeViewer(icon);
            return true;
        }
        if (hoveredRewardFs != null) {
            if (tryClaimReward(hoveredRewardFsIndex)) return true;
            if (hoveredRewardFs instanceof QuestReward.ItemReward ir) {
                tryOpenInRecipeViewer(new ItemStack(ir.getItem(), ir.getCount()));
            }
            return true;
        }

        // Claim
        int footerY = height - FOOTER_H;
        if (my >= footerY + 2 && my < footerY + 20) {
            QuestState state = playerData != null ? playerData.getQuestState(node.getId(), QuestState.LOCKED) :
                    QuestState.LOCKED;
            if (state == QuestState.COMPLETED && !rewardsClaimed() && !node.getRewards().isEmpty() &&
                    !node.isRewardChoice()) {
                ChronicleNetwork.CHANNEL.sendToServer(new C2SClaimQuestRewardPacket(node.getId(), -1));
                return true;
            }
        }
        // Rich text link/tooltip clicks
        for (net.phoenixvine.chronicles.client.rich.RichSpan.Region r : richRegions) {
            if (r.contains(mx, my)) {
                if (r.span() instanceof net.phoenixvine.chronicles.client.rich.RichSpan.Link l) {
                    try {
                        java.awt.Desktop.getDesktop().browse(java.net.URI.create(l.url()));
                    } catch (Exception ignored) {}
                    return true;
                }
                // Tip spans show tooltip on hover (not click) — handled in render()
            }
        }

        // Description box — click to open the rich-text editor (edit mode only), same as
        // the compact card's description box.
        if (hoveredFsDescBox && isEditMode && minecraft != null) {
            minecraft.setScreen(new QuestTextInputScreen(this, "Description",
                    node.getDescriptionRaw().getString(), 1024,
                    v -> {
                        node.setDescription(Component.literal(v));
                        net.phoenixvine.chronicles.codec.QuestFileSaver.saveAllQuestsToDisk();
                    }));
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!isFullscreen) {
            int maxScrollLine = Math.max(0, compactDescTotalLines - compactDescFittedLines);
            if (maxScrollLine == 0) return false;
            if (mx < descBoxX || mx >= descBoxX + descBoxW || my < descBoxY || my >= descBoxY + descBoxH) {
                return false;
            }
            compactDescScrollLine = Math.max(0, Math.min(maxScrollLine, compactDescScrollLine - (int) (delta * 2)));
            return true;
        }
        int contentTop = HEADER_H + reqBarH() + MARGIN;
        int inspW3 = calcInspW();
        int contentRight = width - inspW3 - calcRewardW() - MARGIN * 3;
        int rightX = contentRight + MARGIN;
        if (mx >= rightX && mx < rightX + inspW3 && my >= contentTop && my < height - FOOTER_H - MARGIN) {
            inspectorScrollY = Math.max(0, (int) (inspectorScrollY - delta * 12));
        } else {
            descScrollY = Math.max(0, (int) (descScrollY - delta * 12));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { // ESC
            if (isFullscreen) {
                isFullscreen = false;
                return true;
            }
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        if (phantasiaPreview != null) {
            net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.closePreview(phantasiaPreview);
            phantasiaPreview = null;
        }
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int calcInspW() {
        int budget = Math.max(140, width - 180 - MARGIN * 4);
        return Math.max(90, Math.min(160, budget * 3 / 5));
    }

    /** Width of the always-visible rewards column, separate from the Info/Prereqs/Tasks tabs. */
    private int calcRewardW() {
        int budget = Math.max(140, width - 180 - MARGIN * 4);
        return Math.max(70, Math.min(REWARD_W, budget * 2 / 5));
    }

    /** Progress string — shows max/max when quest is already COMPLETED so the display stays honest. */
    private String taskProgressString(QuestTask task) {
        if (player == null) return null;
        if (playerData != null && playerData.getQuestState(node.getId(), QuestState.LOCKED) == QuestState.COMPLETED) {
            // Derive "N/N" from the live progress string's denominator so we don't hardcode task types
            String live = task.getProgressString(player);
            if (live != null) {
                int slash = live.indexOf('/');
                if (slash >= 0) {
                    String denom = live.substring(slash + 1).trim();
                    return denom + "/" + denom;
                }
            }
            return null;
        }
        return task.getProgressString(player);
    }

    private boolean isTaskDone(QuestTask task) {
        // If the quest is already marked COMPLETED server-side, all tasks are done.
        // Don't re-run live inventory checks — they're wrong after items are consumed
        // or when the screen opens before the first sync tick.
        if (playerData != null && playerData.getQuestState(node.getId(), QuestState.LOCKED) == QuestState.COMPLETED) {
            return true;
        }
        return player != null && task.isCompletedFor(player);
    }

    private boolean rewardsClaimed() {
        return playerData != null && playerData.hasClaimedRewards(node.getId());
    }

    /**
     * Attempts to claim reward(s) by clicking a reward icon directly, instead of requiring the
     * separate footer "Claim Rewards" button. For a reward_choice quest this claims specifically
     * the clicked reward (index); for a normal multi-reward quest there's no per-reward claim
     * state, so any reward icon claims all of them - same result as the footer button.
     * Returns true if a claim packet was sent.
     */
    private boolean tryClaimReward(int rewardIndex) {
        QuestState state = playerData != null ? playerData.getQuestState(node.getId(), QuestState.LOCKED) :
                QuestState.LOCKED;
        if (state != QuestState.COMPLETED || rewardsClaimed() || node.getRewards().isEmpty()) return false;
        if (node.isRewardChoice()) {
            if (rewardIndex < 0) return false;
            ChronicleNetwork.CHANNEL.sendToServer(new C2SClaimQuestRewardPacket(node.getId(), rewardIndex));
        } else {
            ChronicleNetwork.CHANNEL.sendToServer(new C2SClaimQuestRewardPacket(node.getId(), -1));
        }
        return true;
    }

    /**
     * Checkmark tasks have no external condition to poll — clicking them IS the completion
     * action. Returns true (click handled) if the task was a not-yet-done checkmark task.
     */
    private boolean tryCompleteCheckmark(QuestTask task) {
        if (!(task instanceof net.phoenixvine.chronicles.tasks.CheckmarkTask)) return false;
        if (task.isCompletedFor(minecraft.player)) return true;
        ChronicleNetwork.CHANNEL.sendToServer(
                new net.phoenixvine.chronicles.network.packet.C2SCompleteCheckmarkTaskPacket(task.getTaskId()));
        return true;
    }
}
