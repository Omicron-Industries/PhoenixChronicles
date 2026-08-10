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
import net.phoenixvine.chronicles.client.rich.RichSpan;
import net.phoenixvine.chronicles.model.*;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.C2SAcknowledgeInfoTasksPacket;
import net.phoenixvine.chronicles.network.packet.C2SClaimQuestRewardPacket;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.tasks.*;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import java.util.ArrayList;
import java.util.List;

public class QuestTasksScreen extends Screen {

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

    private static final int HEADER_H = 38;
    private static final int ICON_SZ = 18;
    private static final int ICON_STRIP_H = ICON_SZ + 8;
    private static final int FOOTER_H = 22;
    private static final int MARGIN = 6;
    private static final int REWARD_W = 185;
    private static final int TASK_ICON_SZ = 24;

    private static final int CARD_MAX_WIDTH = 310;

    private int cardW() {
        return Math.min(CARD_MAX_WIDTH, Math.max(120, width - 24));
    }

    private static final int CARD_PAD = 6;
    private static final int CARD_TASK_ROW_H = 22;
    private static final int CARD_MAX_TASKS = 6;

    private static final int CARD_MAX_DESC = 24;

    private final Screen parent;

    Screen getParentScreen() {
        return parent;
    }

    private final QuestNode node;
    private final FullQuestData content;
    private final PlayerQuestData playerData;
    private final Player player;

    private int descScrollY = 0;
    private java.util.List<net.phoenixvine.chronicles.client.rich.RichSpan.Region> richRegions = java.util.List.of();
    private java.util.List<net.phoenixvine.chronicles.client.rich.RichBlock> descBlocks = java.util.List.of();
    private final java.util.Set<String> descExpandedKeys = new java.util.HashSet<>();

    private static final java.util.regex.Pattern DESC_PAGE_BREAK = java.util.regex.Pattern
            .compile("(?m)^[ \\t]*-{3,}[ \\t]*$");
    private int descPage = 0;
    private int richSpansPage = -1;
    private int descPagerX, descPagerY, descPagerW, descPagerH, descPagerPageCount;

    private String liveDescOverride = null;
    private long openTimeMs = -1;
    private static final long OPEN_FADE_MS = 100;
    private InspTab inspectorTab = InspTab.TASKS;
    private int inspectorScrollY = 0;
    private int inspectorContentH = 0;
    private boolean isFullscreen = false;

    private Object phantasiaPreview;
    private int previewX, previewY, previewW, previewH;

    private boolean draggingInspDivider = false;
    private boolean draggingRewardDivider = false;
    private int dividerDragStartMx = 0;
    private int dividerDragStartInspW = 0;
    private int dividerDragStartRewardW = 0;
    private static final int DIVIDER_HIT_PAD = 2;

    public QuestTasksScreen(Screen parent, QuestNode node, FullQuestData content, PlayerQuestData playerData) {
        this(parent, node, content, playerData, false);
    }

    public QuestTasksScreen(Screen parent, QuestNode node, FullQuestData content, PlayerQuestData playerData,
                            boolean startFullscreen) {
        super(Component.literal("Quest Details"));
        this.parent = parent;
        this.node = node;
        this.content = content;
        this.playerData = playerData;
        this.player = net.minecraft.client.Minecraft.getInstance().player;
        this.isFullscreen = startFullscreen;
    }

    @Override
    protected void init() {
        super.init();
        openTimeMs = System.currentTimeMillis();
        isEditMode = player != null && (player.isCreative() || player.hasPermissions(2));
        phantasiaPreview = net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.createPreviewForNode(node);

        boolean hasInfoTasks = node.getTasks().stream().anyMatch(t2 -> t2 instanceof InfoTask);
        if (hasInfoTasks && playerData != null) {
            for (var task : node.getTasks()) {
                if (task instanceof InfoTask) {
                    InfoTask.acknowledge(player, task.getTaskId());
                }
            }
            ChronicleNetwork.CHANNEL.sendToServer(new C2SAcknowledgeInfoTasksPacket(node.getId()));
        }

        PhoenixTheme t = PhoenixTheme.current();
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

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        hoveredHeaderTooltip = null;

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

        for (net.phoenixvine.chronicles.client.rich.RichSpan.Region r : richRegions) {
            if (!r.contains(mx, my)) continue;
            if (r.span() instanceof net.phoenixvine.chronicles.client.rich.RichSpan.Tip t) {
                g.pose().pushPose();
                g.pose().translate(0f, 0f, 500f);
                g.flush();
                List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(t.tooltip()), 240);
                g.renderTooltip(font, lines, mx, my);
                g.flush();
                g.pose().popPose();
                break;
            }
            if (r.span() instanceof net.phoenixvine.chronicles.client.rich.RichSpan.ItemIcon icon) {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(icon.itemId());
                if (item != null) {
                    g.pose().pushPose();
                    g.pose().translate(0f, 0f, 500f);
                    g.flush();
                    g.renderTooltip(font, new net.minecraft.world.item.ItemStack(item).getHoverName(), mx, my);
                    g.flush();
                    g.pose().popPose();
                }
                break;
            }
        }

        if (hoveredHeaderTooltip != null) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 500f);
            g.flush();
            g.renderTooltip(font, Component.literal(hoveredHeaderTooltip), mx, my);
            g.flush();
            g.pose().popPose();
        }
    }

    private static final int TASK_LIST_ROW_H = 24;

    private static final int REWARD_MINI_SZ = 14;

    private static final int PREVIEW_H_COMPACT = 46;

    private int previewBlockH() {
        return phantasiaPreview != null ? PREVIEW_H_COMPACT + 1 : 0;
    }

    private int compactCardH(List<QuestTask> tasks, List<QuestReward> rewards,
                             java.util.List<net.minecraft.util.FormattedCharSequence> descLines, int pageCount) {
        String subtitleForH = node.getSubtitle();
        int headerHForH = subtitleForH != null && !subtitleForH.isBlank() ? 30 : 20;
        int fixedH = headerHForH + 1 + ICON_STRIP_H + 2 + 1 + 18 + previewBlockH();
        int allDescLines = buildAllDescLines(tasks, descLines).size();
        int rawDesc = Math.min(allDescLines, CARD_MAX_DESC);
        int fitted = Math.max(0, Math.min(rawDesc, ((height - headerHForH) - fixedH - 9) / 10));

        int descH = fitted > 0 ? 4 + fitted * 10 + 4 : (isEditMode ? 24 : 0);

        int pagerStripH = pageCount > 1 ? 17 : 0;
        return fixedH + descH + (descH > 0 ? 1 : 0) + pagerStripH + (pagerStripH > 0 ? 1 : 0);
    }

    private java.util.List<net.minecraft.util.FormattedCharSequence> buildAllDescLines(
                                                                                       List<QuestTask> tasks,
                                                                                       java.util.List<net.minecraft.util.FormattedCharSequence> questDescLines) {
        java.util.List<net.minecraft.util.FormattedCharSequence> all = new java.util.ArrayList<>();
        for (QuestTask task : tasks) {
            if (task instanceof InfoTask info) {
                String body = info.getBody();
                if (body != null && !body.isBlank()) {
                    all.addAll(splitRespectingNewlines(font, body, cardW() - CARD_PAD * 2));
                }
            }
        }
        if (!all.isEmpty() && !questDescLines.isEmpty()) {

            all.add(net.minecraft.util.FormattedCharSequence.EMPTY);
        }
        all.addAll(questDescLines);
        return all;
    }

    private java.util.List<net.minecraft.util.FormattedCharSequence> splitRespectingNewlines(net.minecraft.client.gui.Font font,
                                                                                             String text, int maxW) {
        java.util.List<net.minecraft.util.FormattedCharSequence> out = new java.util.ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) {
                out.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            } else {
                out.addAll(font.split(Component.literal(line), maxW));
            }
        }
        return out;
    }

    private QuestTask hoveredTask = null;
    private QuestReward hoveredReward = null;
    private int hoveredRewardIndex = -1;
    private int hoveredSlotX, hoveredSlotY;

    private boolean isEditMode = false;
    private boolean hoveredDescBox = false;
    private int descBoxX, descBoxY, descBoxW, descBoxH;

    private int compactDescScrollLine = 0;
    private int compactDescTotalLines = 0;
    private int compactDescFittedLines = 0;

    private boolean hoveredFsDescBox = false;
    private int fsDescBoxX, fsDescBoxY, fsDescBoxW, fsDescBoxH;
    private int fsDescMaxScrollY = 0;

    private boolean usesPopupOpen = false;
    private final List<int[]> usesPopupRowRects = new ArrayList<>();
    private final List<QuestNode> usesPopupRowTargets = new ArrayList<>();

    private final List<int[]> prereqRowRects = new ArrayList<>();
    private final List<QuestNode> prereqRowTargets = new ArrayList<>();

    private QuestTask hoveredTaskFs = null;
    private QuestReward hoveredRewardFs = null;
    private int hoveredRewardFsIndex = -1;
    private int hoveredFsX, hoveredFsY;

    private int[] addTaskBoxRect = null;
    private int[] addRewardBoxRect = null;

    private QuestTask hoveredStripTask = null;
    private QuestReward hoveredStripReward = null;
    private int hoveredStripRewardIdx = -1;
    private int hoveredStripX, hoveredStripY;

    private String hoveredHeaderTooltip = null;

    private void renderCompact(GuiGraphics g, int mx, int my, float partial) {
        hoveredTask = null;
        hoveredReward = null;
        hoveredRewardIndex = -1;

        if (parent instanceof ChronicleOverviewScreen overview) {
            overview.renderForChildScreen(g);
        } else if (parent != null) {
            parent.render(g, -9999, -9999, partial);
            g.flush();
            com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        } else {
            g.fill(0, 0, width, height, C_BG);
        }

        g.flush();

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);
        g.flush();
        g.fill(0, 0, width, height, 0x88000000);

        List<QuestTask> tasks = node.getTasks();
        List<QuestReward> rewards = node.getRewards();

        int compactDescPageCount = splitDescPages(currentDisplayDescriptionText()).size();
        String descText = currentDescriptionPageText(currentDisplayDescriptionText());

        List<net.phoenixvine.chronicles.client.rich.RichSpan> spans = net.phoenixvine.chronicles.client.rich.ChronicleTextParser
                .parse(descText);
        float compactTextScale = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get()
                .getTextScaleMultiplier();
        int compactLineH = Math.max(1, Math.round(10 * compactTextScale));

        int questDescLineCount = spans.isEmpty() ? 0 :
                (net.phoenixvine.chronicles.client.rich.ChronicleRichTextRenderer.measureHeight(
                        font, spans, cardW() - CARD_PAD * 2, compactTextScale) + compactLineH - 1) / compactLineH;

        java.util.List<net.minecraft.util.FormattedCharSequence> questDescLines = java.util.Collections
                .nCopies(questDescLineCount, net.minecraft.util.FormattedCharSequence.EMPTY);
        java.util.List<net.minecraft.util.FormattedCharSequence> descLines = buildAllDescLines(tasks, questDescLines);

        String compactSubtitle = node.getSubtitle();
        boolean hasCompactSubtitle = compactSubtitle != null && !compactSubtitle.isBlank();
        int compactHeaderH = hasCompactSubtitle ? 30 : 20;

        int fixedH = compactHeaderH + 1 + ICON_STRIP_H + 2 + 1 + 18 + previewBlockH();
        int rawDesc = Math.min(descLines.size(), CARD_MAX_DESC);
        int fittedDesc = Math.max(0, Math.min(rawDesc, ((height - compactHeaderH) - fixedH - 9) / compactLineH));

        int descH = fittedDesc > 0 ? 4 + fittedDesc * compactLineH + 6 : (isEditMode ? 24 : 0);

        int pagerStripH = compactDescPageCount > 1 ? 17 : 0;
        int cardH = fixedH + descH + (descH > 0 ? 1 : 0) + pagerStripH + (pagerStripH > 0 ? 1 : 0);

        int cardX = (width - cardW()) / 2;
        int cardY = Math.max(10, (height - cardH) / 2);

        g.fill(cardX + 3, cardY + 3, cardX + cardW() + 3, cardY + cardH + 3, 0x66000000);
        g.fill(cardX, cardY, cardX + cardW(), cardY + cardH, C_BG);
        drawBorder(g, cardX, cardY, cardW(), cardH);

        g.enableScissor(cardX, cardY, cardX + cardW(), cardY + cardH);

        int cy = cardY;

        g.fill(cardX, cy, cardX + cardW(), cy + compactHeaderH, C_HEADER);
        String title = (node.isOptional() ? "§d[Optional] §f" : "") + node.getTitle().getString();
        if (font.width(title.replaceAll("§.", "")) > cardW() - 50)
            title = font.plainSubstrByWidth(title, cardW() - 56) + "…";
        g.drawString(font, "§f" + title, cardX + CARD_PAD, cy + 6, C_TEXT, false);

        if (hasCompactSubtitle) {
            String subtitleShown = compactSubtitle;
            if (font.width(subtitleShown.replaceAll("§.", "")) > cardW() - 12)
                subtitleShown = font.plainSubstrByWidth(subtitleShown, cardW() - 18) + "…";
            g.drawString(font, "§7" + subtitleShown, cardX + CARD_PAD, cy + 18, C_TEXT_DIM, false);
        }

        boolean fsHov = mx >= cardX + cardW() - 18 && mx < cardX + cardW() - 4 && my >= cy + 3 && my < cy + 17;
        if (fsHov) {
            g.fill(cardX + cardW() - 18, cy + 3, cardX + cardW() - 4, cy + 17, 0x33FFFFFF);
            hoveredHeaderTooltip = "Expand to fullscreen";
        }
        g.drawCenteredString(font, fsHov ? "§b[+]" : "§8[+]", cardX + cardW() - 11, cy + 6,
                fsHov ? C_ACTIVE : C_TEXT_FAINT);

        boolean closeHov = mx >= cardX + cardW() - 34 && mx < cardX + cardW() - 20 && my >= cy + 3 && my < cy + 17;
        if (closeHov) {
            g.fill(cardX + cardW() - 34, cy + 3, cardX + cardW() - 20, cy + 17, 0x33FFFFFF);
            hoveredHeaderTooltip = "Close";
        }
        g.drawCenteredString(font, closeHov ? "§c✕" : "§8✕", cardX + cardW() - 27, cy + 6,
                closeHov ? 0xFFFF6666 : C_TEXT_FAINT);

        cy += compactHeaderH;
        g.fill(cardX, cy, cardX + cardW(), cy + 1, C_BORDER);
        cy += 1;

        renderIconStrip(g, cardX, cy, mx, my, tasks, rewards);
        cy += ICON_STRIP_H;

        cy += 2;
        g.fill(cardX, cy, cardX + cardW(), cy + 1, C_BORDER);
        cy += 1;

        if (phantasiaPreview != null) {
            int pvSz = PREVIEW_H_COMPACT;
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

            net.phoenixvine.chronicles.client.rich.ChronicleRichTextRenderer.render(
                    g, font, spans,
                    cardX + CARD_PAD, cy + 4, cardW() - CARD_PAD * 2,

                    compactDescScrollLine * Math.round(10 * compactTextScale),
                    cy + 1, cy + descH - 1,

                    compactTextScale);

            if (descLines.size() > questDescLines.size()) {
                for (int i = questDescLines.size(); i < descLines.size(); i++) {
                    int renderY = cy + 4 + (i * 10) - (compactDescScrollLine * 10);
                    if (renderY >= cy + 4 && renderY + 10 <= cy + descH - 4) {
                        g.drawString(font, descLines.get(i), cardX + CARD_PAD, renderY, C_TEXT_DIM, false);
                    }
                }
            }

            if (maxScrollLine > 0) {
                String hint = compactDescScrollLine < maxScrollLine ? "§7▼ scroll for more" : "§7▲ scroll up";
                g.drawString(font, hint, cardX + cardW() - font.width(hint) - CARD_PAD - 2, cy + descH - 11,
                        C_TEXT_FAINT, false);
            } else if (isEditMode) {
                String hint = "§7✎ edit";
                g.drawString(font, hint, cardX + cardW() - font.width(hint) - CARD_PAD - 2, cy + descH - 11,
                        hoveredDescBox ? C_ACTIVE : C_TEXT_FAINT, false);
            }
            cy += descH;
            g.fill(cardX, cy, cardX + cardW(), cy + 1, C_BORDER);
            cy += 1;

            if (pagerStripH > 0) {
                renderDescPager(g, cardX, cy, cardW(), pagerStripH, mx, my, compactDescPageCount);
                cy += pagerStripH;
                g.fill(cardX, cy, cardX + cardW(), cy + 1, C_BORDER);
                cy += 1;
            }
        }

        renderCompactFooter(g, cardX, cy, cardW(), 18, mx, my);

        g.disableScissor();

        if (hoveredTask != null) {
            g.pose().translate(0f, 0f, 200f);
            g.renderComponentTooltip(font, buildTaskTooltip(hoveredTask), mx, my);
        } else if (hoveredReward != null) {
            g.pose().translate(0f, 0f, 200f);
            g.renderComponentTooltip(font, buildRewardTooltip(hoveredReward), mx, my);
        }

        g.pose().popPose();
    }

    private void renderIconStrip(GuiGraphics g, int cardX, int cy, int mx, int my, List<QuestTask> tasks,
                                 List<QuestReward> rewards) {
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
                g.renderItemDecorations(font, icon, ix + off, iy + off);
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

        int rix = cardX + cardW() - CARD_PAD - sz;
        boolean claimed = rewardsClaimed();
        for (int ri = 0; ri < rewards.size(); ri++) {
            if (rix < cardX + CARD_PAD) break;
            QuestReward reward = rewards.get(ri);
            boolean hov = mx >= rix && mx < rix + sz && my >= iy && my < iy + sz;
            int border = claimed ? C_DONE : C_TEXT_FAINT;
            int bg = claimed ? 0xFF1A140A : 0xFF0F0F18;
            if (hov) {
                hoveredReward = reward;
                hoveredRewardIndex = ri;
                hoveredSlotX = rix;
                hoveredSlotY = iy;
            }
            drawIconSlot(g, rix, iy, sz, bg, border, hov);
            int off = (sz - 16) / 2;
            if (reward instanceof QuestReward.ItemReward ir) {
                g.renderItem(new ItemStack(ir.getItem(), ir.getCount()), rix + off, iy + off);
                if (claimed) g.fill(rix, iy, rix + sz, iy + sz, 0x55CC8800);
            } else {
                String glyph = switch (reward.getType()) {
                    case XP -> "⚡";
                    case COMMAND -> "◆";
                    case LOOT_TABLE -> "📦";
                    case SCRIPT_EVENT -> "✦";
                    case REWARD_TABLE -> "⊞";
                    default -> "?";
                };
                g.drawCenteredString(font, "§7" + glyph, rix + sz / 2, iy + sz / 2 - 4, C_TEXT_DIM);
                if (claimed) g.fill(rix, iy, rix + sz, iy + sz, 0x55CC8800);
            }
            if (claimed) {
                g.fill(rix + sz - 7, iy + sz - 8, rix + sz, iy + sz, 0xFF1A1000);
                g.drawString(font, "§6✔", rix + sz - 7, iy + sz - 8, 0xFFFFFFFF, false);
            }
            rix -= sz + gap;
        }
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

        if (hov) g.fill(x, y, x + w, y + TASK_LIST_ROW_H - 6, 0x18FFFFFF);

        int accent = done ? C_DONE : (task.isOptional() ? C_TEXT_FAINT : C_ACTIVE);
        g.fill(x, y + 1, x + 2, y + TASK_LIST_ROW_H - 7, accent);

        String mark = done ? "§a✔" : (task.isOptional() ? "§8○" : "§c✗");
        g.drawString(font, mark, x + 4, y + 3, 0xFFFFFFFF, false);

        int textX = x + 16;
        ItemStack icon = getTaskIcon(task);
        if (!icon.isEmpty()) {
            g.renderItem(icon, textX, y);
            g.renderItemDecorations(font, icon, textX, y);
            textX += 18;
        } else {
            g.drawString(font, getTaskGlyph(task), textX, y + 3, 0xFFFFFFFF, false);
            textX += 10;
        }

        String desc = task.getDescription().getString();
        String detail = getTaskDetail(task);

        boolean descIsId = desc.isEmpty() || desc.matches("[a-z0-9_]+");
        String primary = (descIsId && detail != null) ? detail : desc;

        float rowTs = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get().getTextScaleMultiplier();

        String prog = done ? "§a✔" : (progress != null ? "§8" + progress : "");
        int progW = prog.isEmpty() ? 0 : Math.round(font.width(prog) * rowTs) + 2;
        int labelW = w - (textX - x) - progW - 2;

        float labelAvailPreScale = labelW / rowTs;
        if (font.width(primary) > labelAvailPreScale)
            primary = font.plainSubstrByWidth(primary, (int) (labelAvailPreScale - 5)) + "…";
        ChroniclesUIKit.drawScaledString(g, font, (done ? "§7" : "§f") + primary, textX, y + 3,
                done ? C_TEXT_DIM : C_TEXT, rowTs);
        if (!prog.isEmpty()) {
            ChroniclesUIKit.drawScaledString(g, font, prog, x + w - progW, y + 3, C_TEXT_FAINT, rowTs);
        }

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

    private void renderFullscreen(GuiGraphics g, int mx, int my, float partial) {
        hoveredTaskFs = null;
        hoveredRewardFs = null;

        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        g.fill(0, 0, width, height, 0xFF000000);
        renderHeader(g, mx, my);
        renderRequirementsBar(g, mx, my);

        int[] pw = fullscreenPanelWidths();
        int inspW = pw[0];
        int rewardW = pw[1];
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

        renderPanelDividers(g, contentRight, inspX + inspW, contentTop, contentH, mx, my);

        renderFooter(g, mx, my);

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

        boolean backHov = mx >= 4 && mx < 20 && my >= 6 && my < 22;
        if (backHov) {
            g.fill(4, 6, 20, 22, 0x22FFFFFF);
            hoveredHeaderTooltip = "Close";
        }
        g.drawCenteredString(font, "§7←", 12, 10, C_TEXT_DIM);

        boolean pinned = playerData != null && playerData.isPinned(node.getId());
        int pinX = width - 20;
        int editX = pinX - 18;
        int fsX = editX - 20;
        int usesX = fsX - 20;
        int titleMaxW = usesX - 32;

        float headerTextScale = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get().getTextScaleMultiplier();

        float titleAvailPreScale = titleMaxW / headerTextScale;
        String titleStr = (node.isOptional() ? "§d[Optional] §f" : "") + node.getTitle().getString();
        if (font.width(titleStr.replaceAll("§.", "")) > titleAvailPreScale)
            titleStr = font.plainSubstrByWidth(titleStr, (int) (titleAvailPreScale - 6)) + "…";
        ChroniclesUIKit.drawScaledString(g, font, "§f" + titleStr, 28, 10, C_TEXT, headerTextScale);

        String subtitleStr = node.getSubtitle();
        if (subtitleStr != null && !subtitleStr.isBlank()) {
            if (font.width(subtitleStr.replaceAll("§.", "")) > titleMaxW)
                subtitleStr = font.plainSubstrByWidth(subtitleStr, titleMaxW - 6) + "…";
            g.drawString(font, "§7" + subtitleStr, 28, 24, C_TEXT_DIM, false);
        }

        boolean usesHasAny = !node.getPrerequisites().isEmpty() || !node.getChildren().isEmpty();
        boolean usesHov = usesHasAny && mx >= usesX && mx < usesX + 16 && my >= 6 && my < 22;
        if (usesHov || usesPopupOpen) g.fill(usesX, 6, usesX + 16, 22, 0x22FFFFFF);

        g.drawCenteredString(font, "§b≡", usesX + 8, 10,
                !usesHasAny ? C_TEXT_FAINT : (usesHov || usesPopupOpen) ? 0xFF55CCFF : C_TEXT_DIM);

        boolean fsToggleHov = mx >= fsX && mx < fsX + 16 && my >= 6 && my < 22;
        if (fsToggleHov) {
            g.fill(fsX, 6, fsX + 16, 22, 0x22FFFFFF);
            hoveredHeaderTooltip = "Shrink to compact view";
        }
        g.drawCenteredString(font, "§d[-]", fsX + 8, 10, 0xFFAA44FF);

        boolean editHov = mx >= editX && mx < editX + 14 && my >= 6 && my < 22;
        if (editHov) g.fill(editX, 6, editX + 14, 22, 0x22FFFFFF);
        g.drawCenteredString(font, editHov ? "§e✎" : "§8✎", editX + 7, 10, editHov ? 0xFFFFDD44 : C_TEXT_FAINT);

        if (mx >= pinX && mx < width - 4 && my >= 6 && my < 22) g.fill(pinX, 6, width - 4, 22, 0x22FFFFFF);
        g.drawCenteredString(font, pinned ? "§d📌" : "§8📌", width - 12, 10, pinned ? 0xFFAA44FF : C_TEXT_FAINT);

        if (usesPopupOpen) renderUsesPopup(g, usesX, mx, my);
    }

    private void renderUsesPopup(GuiGraphics g, int anchorX, int mx, int my) {
        usesPopupRowRects.clear();
        usesPopupRowTargets.clear();

        List<QuestNode> requires = node.getPrerequisites();
        List<QuestNode> unlocks = node.getChildren();

        int rowH = 12;
        int headerH = 12;
        int padW = 6, padTop = 4, padBot = 6;
        int innerW = 160;
        int contentRows = requires.size() + unlocks.size();
        int sectionHeaders = (requires.isEmpty() ? 0 : 1) + (unlocks.isEmpty() ? 0 : 1);
        int popupH = padTop + sectionHeaders * headerH + contentRows * rowH + padBot;
        if (contentRows == 0) popupH = padTop + 14 + padBot;

        int popupX = Math.max(4, Math.min(anchorX + 16 - innerW, width - innerW - 4));
        int popupY = HEADER_H + 2;

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 250f);
        g.fill(popupX, popupY, popupX + innerW, popupY + popupH, 0xF00A0A0E);
        drawBorder(g, popupX, popupY, innerW, popupH);

        int ry = popupY + padTop;
        if (contentRows == 0) {
            g.drawString(font, "§8(no connections)", popupX + padW, ry + 2, C_TEXT_FAINT, false);
        } else {
            ry = renderUsesPopupSection(g, popupX, ry, innerW, padW, rowH, "§8Requires:", requires, mx, my);
            ry = renderUsesPopupSection(g, popupX, ry, innerW, padW, rowH, "§8Unlocks:", unlocks, mx, my);
        }
        g.pose().popPose();
    }

    private int renderUsesPopupSection(GuiGraphics g, int popupX, int ry, int innerW, int padW, int rowH,
                                       String header, List<QuestNode> list, int mx, int my) {
        if (list.isEmpty()) return ry;
        g.drawString(font, header, popupX + padW, ry + 2, C_TEXT_FAINT, false);
        ry += rowH;
        for (QuestNode target : list) {
            boolean hov = mx >= popupX && mx < popupX + innerW && my >= ry && my < ry + rowH;
            if (hov) g.fill(popupX + 2, ry, popupX + innerW - 2, ry + rowH, 0x22FFFFFF);
            QuestState state = playerData != null ? playerData.getQuestState(target.getId(), QuestState.LOCKED) :
                    QuestState.LOCKED;
            String title = target.getTitle().getString();
            int maxW = innerW - padW * 2 - 10;
            if (font.width(title.replaceAll("§.", "")) > maxW)
                title = font.plainSubstrByWidth(title, Math.max(0, maxW - 6)) + "…";
            g.drawString(font, (state == QuestState.COMPLETED ? "§a●" : "§8○") + " " + (hov ? "§f" : "§7") + title,
                    popupX + padW + 8, ry + 2, hov ? C_TEXT : C_TEXT_DIM, false);
            usesPopupRowRects.add(new int[] { popupX, ry, innerW, rowH });
            usesPopupRowTargets.add(target);
            ry += rowH;
        }
        return ry;
    }

    private int reqBarH() {
        boolean hasIcons = !node.getTasks().isEmpty() || !node.getRewards().isEmpty();
        return hasIcons ? ICON_STRIP_H : 1;
    }

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

                g.renderItemDecorations(font, icon, iconX + off, iconY + off);
                if (done) g.fill(iconX, iconY, iconX + sz, iconY + sz, 0x5500CC55);
            } else {
                g.drawCenteredString(font, done ? "§a✔" : "§c✗", iconX + sz / 2, iconY + sz / 2 - 4, 0xFFFFFFFF);
            }

            if (done) {
                g.fill(iconX + sz - 7, iconY + sz - 8, iconX + sz, iconY + sz, 0xFF0A2210);
                g.drawString(font, "§a✔", iconX + sz - 7, iconY + sz - 8, 0xFFFFFFFF, false);
            }
            iconX += sz + gap;
        }

        int rIconX = width - MARGIN - 2 - rewards.size() * sz - Math.max(0, rewards.size() - 1) * gap;

        boolean claimed = rewardsClaimed();
        for (int ri = 0; ri < rewards.size(); ri++) {
            QuestReward reward = rewards.get(ri);
            boolean hov = mx >= rIconX && mx < rIconX + sz && my >= iconY && my < iconY + sz;
            int border = claimed ? C_DONE : C_TEXT_FAINT;
            int bg = claimed ? 0xFF1A140A : 0xFF0F0F18;

            if (hov) {
                hoveredStripReward = reward;
                hoveredStripRewardIdx = ri;
                hoveredStripX = rIconX;
                hoveredStripY = iconY;
            }

            drawIconSlot(g, rIconX, iconY, sz, bg, border, hov);

            int off = (sz - 16) / 2;
            if (reward instanceof QuestReward.ItemReward ir) {
                g.renderItem(new ItemStack(ir.getItem(), ir.getCount()), rIconX + off, iconY + off);
                if (claimed) g.fill(rIconX, iconY, rIconX + sz, iconY + sz, 0x55CC8800);
            } else {
                String glyph = switch (reward.getType()) {
                    case XP -> "⚡";
                    case COMMAND -> "◆";
                    case LOOT_TABLE -> "📦";
                    case SCRIPT_EVENT -> "✦";
                    case REWARD_TABLE -> "⊞";
                    default -> "?";
                };
                g.drawCenteredString(font, "§7" + glyph, rIconX + sz / 2, iconY + sz / 2 - 4, C_TEXT_DIM);
                if (claimed) g.fill(rIconX, iconY, rIconX + sz, iconY + sz, 0x55CC8800);
            }

            if (claimed) {
                g.fill(rIconX + sz - 7, iconY + sz - 8, rIconX + sz, iconY + sz, 0xFF1A1000);
                g.drawString(font, "§6✔", rIconX + sz - 7, iconY + sz - 8, 0xFFFFFFFF, false);
            }
            rIconX += sz + gap;
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

    private int renderAddBox(GuiGraphics g, int x, int y, int w, String label, int mx, int my) {
        int boxH = 16;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + boxH;
        int border = hov ? C_ACTIVE : C_BORDER;
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + boxH - 1, x + w, y + boxH, border);
        g.fill(x, y, x + 1, y + boxH, border);
        g.fill(x + w - 1, y, x + w, y + boxH, border);
        if (hov) g.fill(x + 1, y + 1, x + w - 1, y + boxH - 1, 0x14FFFFFF);
        g.drawCenteredString(font, (hov ? "§f" : "§8") + label, x + w / 2, y + 4, hov ? C_TEXT : C_TEXT_FAINT);
        return boxH;
    }

    private void renderContent(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float partial) {
        prereqRowRects.clear();
        prereqRowTargets.clear();
        addTaskBoxRect = null;
        addRewardBoxRect = null;

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

        String descRawFull = currentDisplayDescriptionText();
        java.util.List<String> descPages = splitDescPages(descRawFull);
        String descRaw = currentDescriptionPageText(descRawFull);

        int pagerH = descPages.size() > 1 ? 17 : 0;
        int textBottom = y + h - pagerH;

        g.enableScissor(x - 8, y - 8, x + w + 8, textBottom + 8);

        if (richSpansPage != descPage) {
            descBlocks = descRaw.isEmpty() ? java.util.List.of() :
                    net.phoenixvine.chronicles.client.rich.ChronicleMarkdownParser.parse(descRaw);
            richSpansPage = descPage;
        }
        if (isEditMode && descRaw.isEmpty() && descBlocks.isEmpty()) {
            g.drawString(font, "§8Click to add a description", x, y, C_TEXT_FAINT, false);
        }

        float textScale = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get().getTextScaleMultiplier();

        int descContentH = net.phoenixvine.chronicles.client.rich.ChronicleRichTextRenderer.measureBlocksHeight(font,
                descBlocks, w, textScale, descExpandedKeys);
        fsDescMaxScrollY = Math.max(0, descContentH - (textBottom - y));
        if (descScrollY > fsDescMaxScrollY) descScrollY = fsDescMaxScrollY;

        richRegions = net.phoenixvine.chronicles.client.rich.ChronicleRichTextRenderer.renderBlocks(
                g, font, descBlocks, x, y, w, descScrollY, y, textBottom, textScale, C_ACTIVE, descExpandedKeys);

        int ly = y + descContentH - descScrollY;

        if (phantasiaPreview != null) {
            if (ly > y) ly += 8;
            int pvSz = 64;
            if (ly + pvSz >= y - 10 && ly < textBottom) {
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

        g.disableScissor();

        if (pagerH > 0) renderDescPager(g, x, textBottom, w, pagerH, mx, my, descPages.size());
    }

    private static java.util.List<String> splitDescPages(String raw) {
        if (raw == null || raw.isEmpty()) return java.util.List.of("");
        String[] parts = DESC_PAGE_BREAK.split(raw);
        return parts.length == 0 ? java.util.List.of("") : java.util.List.of(parts);
    }

    private void renderDescPager(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int pageCount) {
        String pageLabel = "Page " + (descPage + 1) + "/" + pageCount;
        int arrowW = font.width("◀") + 8;
        int labelW = font.width(pageLabel) + 10;
        int pw = arrowW * 2 + labelW;
        int ph = 14;
        int px = x + (w - pw) / 2;
        int py = y + h - ph - 2;

        boolean canPrev = descPage > 0;
        boolean canNext = descPage < pageCount - 1;
        boolean overLeft = mx >= px && mx < px + arrowW && my >= py && my < py + ph;
        boolean overRight = mx >= px + pw - arrowW && mx < px + pw && my >= py && my < py + ph;

        g.fill(px, py, px + pw, py + ph, 0xCC0A0A0E);
        drawBorder(g, px, py, pw, ph);

        if (canPrev && overLeft) g.fill(px + 1, py + 1, px + arrowW, py + ph - 1, 0x33FFFFFF);
        if (canNext && overRight) g.fill(px + pw - arrowW, py + 1, px + pw - 1, py + ph - 1, 0x33FFFFFF);

        g.fill(px + arrowW, py + 2, px + arrowW + 1, py + ph - 2, 0x33FFFFFF);
        g.fill(px + pw - arrowW - 1, py + 2, px + pw - arrowW, py + ph - 2, 0x33FFFFFF);

        int dimColor = 0xFF3A3A42;
        int leftColor = !canPrev ? dimColor : (overLeft ? C_TEXT : C_TEXT_DIM);
        int rightColor = !canNext ? dimColor : (overRight ? C_TEXT : C_TEXT_DIM);
        g.drawCenteredString(font, "◀", px + arrowW / 2, py + 3, leftColor);
        g.drawCenteredString(font, pageLabel, px + arrowW + labelW / 2, py + 3, C_TEXT_DIM);
        g.drawCenteredString(font, "▶", px + pw - arrowW / 2, py + 3, rightColor);

        descPagerX = px;
        descPagerY = py;
        descPagerW = pw;
        descPagerH = ph;
        descPagerPageCount = pageCount;
    }

    private enum InspTab {
        INFO,
        TASKS
    }

    private InspTab[] visibleInspTabs() {
        return isEditMode ? new InspTab[] { InspTab.INFO, InspTab.TASKS } : new InspTab[] { InspTab.TASKS };
    }

    private String inspTabLabel(InspTab t) {
        return switch (t) {
            case INFO -> "Info";
            case TASKS -> "Tasks";
        };
    }

    private static final int INSP_TAB_H = 16;

    private void renderInspector(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        g.enableScissor(x, y, x + w, y + h);
        g.fill(x, y, x + w, y + h, C_PANEL);
        drawBorder(g, x, y, w, h);

        InspTab[] tabs = visibleInspTabs();
        if (!java.util.Arrays.asList(tabs).contains(inspectorTab)) inspectorTab = tabs[tabs.length - 1];

        int cY;
        if (tabs.length > 1) {
            int tabX = x + 6;
            int tabY = y + 4;
            for (InspTab t : tabs) {
                String label = inspTabLabel(t);
                int tabW = font.width(label) + 6;
                boolean active = inspectorTab == t;
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
            cY = y + INSP_TAB_H + 6;
        } else {
            cY = y + 6;
        }
        int cH = (y + h - 6) - cY;

        int maxScroll = Math.max(0, inspectorContentH - cH + 8);
        inspectorScrollY = Math.max(0, Math.min(inspectorScrollY, maxScroll));

        g.enableScissor(x, cY, x + w, cY + cH);
        switch (inspectorTab) {
            case INFO -> renderInfoTab(g, x, cY, w, cH);
            case TASKS -> renderTasksTab(g, x, cY, w, cH, mx, my);
        }
        g.disableScissor();
        g.disableScissor();
    }

    private static boolean lineFullyVisible(int lineY, int viewTop, int viewBot) {
        return lineY >= viewTop && lineY + 9 <= viewBot;
    }

    private void renderInfoTab(GuiGraphics g, int x, int y, int w, int h) {
        int m = 6;
        int cy = y - inspectorScrollY + 4;
        int viewBot = y + h;
        if (lineFullyVisible(cy, y, viewBot)) g.drawString(font, "§8Chapter:", x + m, cy, C_TEXT_FAINT, false);
        if (lineFullyVisible(cy + 10, y, viewBot))
            g.drawString(font, "§7" + (node.getChapter() != null ? node.getChapter() : "(none)"), x + m, cy + 10,
                    C_TEXT, false);
        cy += 24;
        if (lineFullyVisible(cy, y, viewBot)) g.drawString(font, "§8Visibility:", x + m, cy, C_TEXT_FAINT, false);
        if (lineFullyVisible(cy + 10, y, viewBot))
            g.drawString(font, "§7" + (node.getVisibility() != null ? node.getVisibility() : "NORMAL"), x + m,
                    cy + 10, C_TEXT, false);
        cy += 24;
        if (lineFullyVisible(cy, y, viewBot)) g.drawString(font, "§8ID:", x + m, cy, C_TEXT_FAINT, false);
        if (lineFullyVisible(cy + 10, y, viewBot))
            g.drawString(font, "§7" + (node.getId() != null ? node.getId() : "unknown"), x + m, cy + 10, C_TEXT,
                    false);
        cy += 24;

        for (QuestTask task : node.getTasks()) {
            if (!(task instanceof InfoTask info)) continue;
            String body = info.getBody();
            if (body == null || body.isBlank()) continue;
            if (cy > y + h) break;
            if (cy >= y) g.fill(x + m, cy, x + w - m, cy + 1, C_BORDER);
            cy += 6;
            for (var line : splitRespectingNewlines(font, body, w - m * 2)) {
                if (cy > y + h) break;
                if (lineFullyVisible(cy, y, viewBot)) g.drawString(font, line, x + m, cy, C_TEXT_DIM, false);
                cy += 10;
            }
            cy += 4;
        }
    }

    private List<QuestNode> dependentsOf(QuestNode target) {
        List<QuestNode> out = new ArrayList<>();
        for (QuestNode candidate : QuestTreeRegistry.getAllQuests().values()) {
            if (candidate != target && candidate.getPrerequisites().contains(target)) out.add(candidate);
        }
        return out;
    }

    private void renderTasksTab(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        List<QuestTask> tasks = node.getTasks();
        List<QuestNode> prereqs = node.getPrerequisites();
        List<QuestNode> dependents = dependentsOf(node);
        int m = 6;
        int cy = y - inspectorScrollY + 4;
        int viewBot = y + h;
        if (tasks.isEmpty() && prereqs.isEmpty() && dependents.isEmpty()) {
            if (lineFullyVisible(cy, y, viewBot)) g.drawString(font, "§8(none)", x + m, cy, C_TEXT_FAINT, false);
            if (isEditMode) {
                int addY = lineFullyVisible(cy, y, viewBot) ? cy + 12 : cy;
                if (lineFullyVisible(addY, y, viewBot)) {
                    int boxH = renderAddBox(g, x + m, addY, w - m * 2, "+ Add Task", mx, my);
                    addTaskBoxRect = new int[] { x + m, addY, w - m * 2, boxH };
                }
                cy = addY + 16;
            }
            inspectorContentH = Math.max(0, cy - (y - inspectorScrollY + 4));
            return;
        }
        for (QuestTask task : tasks) {
            if (task instanceof InfoTask) continue;

            String detail = getTaskDetail(task);
            int rowH = (detail != null ? 22 : 14) + 3 + 5;
            if (cy < y) {
                cy += rowH;
                continue;
            }
            int rowEndY = renderRichTaskRow(g, x + m, cy, w - m * 2, task, mx, my);
            if (mx >= x + m && mx < x + w - m && my >= cy && my < rowEndY - 5) {
                hoveredTaskFs = task;
                hoveredFsX = (int) mx;
                hoveredFsY = (int) my;
            }
            cy = rowEndY;
        }

        if (isEditMode) {
            cy += 3;
            if (lineFullyVisible(cy, y, viewBot)) {
                int boxH = renderAddBox(g, x + m, cy, w - m * 2, "+ Add Task", mx, my);
                addTaskBoxRect = new int[] { x + m, cy, w - m * 2, boxH };
            }
            cy += 16 + 3;
        }

        if (!prereqs.isEmpty()) {
            cy += 6;
            if (lineFullyVisible(cy, y, viewBot)) g.fill(x + m, cy, x + w - m, cy + 1, C_BORDER);
            cy += 8;
            if (lineFullyVisible(cy, y, viewBot))
                g.drawString(font, "§8PREREQUISITES:", x + m, cy, C_TEXT_FAINT, false);
            cy += 12;
            for (QuestNode req : prereqs) {
                QuestState state = playerData != null ? playerData.getQuestState(req.getId(), QuestState.LOCKED) :
                        QuestState.LOCKED;
                String reqTitle = req.getTitle().getString();
                int titleMaxW = w - m * 2 - 12;
                if (font.width(reqTitle.replaceAll("§.", "")) > titleMaxW)
                    reqTitle = font.plainSubstrByWidth(reqTitle, Math.max(0, titleMaxW - 6)) + "…";
                boolean reqHov = mx >= x + m && mx < x + w - m && my >= cy && my < cy + 10;
                if (lineFullyVisible(cy, y, viewBot)) {
                    if (reqHov) g.fill(x + m - 2, cy - 1, x + w - m, cy + 9, 0x22FFFFFF);
                    g.drawString(font, (state == QuestState.COMPLETED ? "§a●" : "§8○") + " " +
                            (reqHov ? "§f" : "§7") + reqTitle, x + m, cy, reqHov ? C_TEXT : C_TEXT_DIM, false);
                    prereqRowRects.add(new int[] { x + m, cy - 1, w - m * 2, 10 });
                    prereqRowTargets.add(req);
                }
                cy += 10;
            }
        }

        if (!dependents.isEmpty()) {
            cy += 6;
            if (lineFullyVisible(cy, y, viewBot)) g.fill(x + m, cy, x + w - m, cy + 1, C_BORDER);
            cy += 8;
            if (lineFullyVisible(cy, y, viewBot)) g.drawString(font, "§8UNLOCKS:", x + m, cy, C_TEXT_FAINT, false);
            cy += 12;
            for (QuestNode dep : dependents) {
                QuestState state = playerData != null ? playerData.getQuestState(dep.getId(), QuestState.LOCKED) :
                        QuestState.LOCKED;
                String depTitle = dep.getTitle().getString();
                int titleMaxW = w - m * 2 - 12;
                if (font.width(depTitle.replaceAll("§.", "")) > titleMaxW)
                    depTitle = font.plainSubstrByWidth(depTitle, Math.max(0, titleMaxW - 6)) + "…";
                boolean depHov = mx >= x + m && mx < x + w - m && my >= cy && my < cy + 10;
                if (lineFullyVisible(cy, y, viewBot)) {
                    if (depHov) g.fill(x + m - 2, cy - 1, x + w - m, cy + 9, 0x22FFFFFF);
                    g.drawString(font, (state == QuestState.COMPLETED ? "§a●" : "§8○") + " " +
                            (depHov ? "§f" : "§7") + depTitle, x + m, cy, depHov ? C_TEXT : C_TEXT_DIM, false);
                    prereqRowRects.add(new int[] { x + m, cy - 1, w - m * 2, 10 });
                    prereqRowTargets.add(dep);
                }
                cy += 10;
            }
        }
        inspectorContentH = (cy - (y - inspectorScrollY + 4));
    }

    private int renderRichTaskRow(GuiGraphics g, int x, int y, int w, QuestTask task, int mx, int my) {
        boolean done = isTaskDone(task);
        String progress = taskProgressString(task);

        boolean rowHov = mx >= x && mx < x + w && my >= y && my < y + 30;
        if (rowHov) g.fill(x, y, x + w, y + 30, 0x14FFFFFF);

        g.drawString(font, done ? "§a✔" : (task.isOptional() ? "§8○" : "§c✗"), x, y + 1, 0xFFFFFFFF, false);

        int cx = x + 10;
        ItemStack icon = getTaskIcon(task);
        if (!icon.isEmpty()) {
            g.renderItem(icon, cx, y);
            g.renderItemDecorations(font, icon, cx, y);
            if (done) g.fill(cx, y, cx + 16, y + 16, 0x5500AA44);
            cx += 18;
        } else {
            g.drawString(font, getTaskGlyph(task), cx, y + 1, 0xFFFFFFFF, false);
            cx += 10;
        }

        float taskTs = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get().getTextScaleMultiplier();

        String desc = task.getDescription().getString();
        int progW = (progress != null && !done) ? Math.round(font.width(progress) * taskTs) + 4 : 0;
        int descAvailW = w - (cx - x) - progW;
        float descAvailPreScale = descAvailW / taskTs;
        if (font.width(desc) > descAvailPreScale)
            desc = font.plainSubstrByWidth(desc, Math.max(0, (int) (descAvailPreScale - 6))) + "…";
        ChroniclesUIKit.drawScaledString(g, font, "§f" + desc, cx, y + 1, done ? C_DONE : C_TEXT, taskTs);

        String detail = getTaskDetail(task);
        if (detail != null) {
            int detailAvailW = w - (cx - x);
            float detailAvailPreScale = detailAvailW / taskTs;
            if (font.width(detail) > detailAvailPreScale)
                detail = font.plainSubstrByWidth(detail, Math.max(0, (int) (detailAvailPreScale - 6))) + "…";
            ChroniclesUIKit.drawScaledString(g, font, "§8" + detail, cx, y + 11, C_TEXT_FAINT, taskTs);
        }

        float pct = done ? 1f : parseProgress(progress);
        int barY = y + (detail != null ? 22 : 14);
        int barW = w - 12;
        g.fill(x + 10, barY, x + 10 + barW, barY + 3, 0xFF1A1A22);
        if (pct > 0) g.fill(x + 10, barY, x + 10 + (int) (barW * pct), barY + 3, done ? C_DONE : C_ACTIVE);
        if (progress != null) {
            int progTextW = Math.round(font.width(progress) * taskTs);
            ChroniclesUIKit.drawScaledString(g, font, "§8" + progress, x + w - progTextW, barY - 1, C_TEXT_FAINT,
                    taskTs);
        }

        return barY + 3 + 5;
    }

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
            if (isEditMode) {
                int addY = cy + 12;
                int boxH = renderAddBox(g, x + m, addY, w - m * 2, "+ Add Reward", mx, my);
                addRewardBoxRect = new int[] { x + m, addY, w - m * 2, boxH };
            }
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
                label = rewardStack(ir).getHoverName().getString() + " ×" + ir.getCount();
            } else {
                label = switch (reward.getType()) {
                    case XP -> "XP Reward";
                    case COMMAND -> "Command";
                    case LOOT_TABLE -> "Loot Table";
                    case SCRIPT_EVENT -> "Script Event";
                    case REWARD_TABLE -> "Reward Table";
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
        if (isEditMode && cy + 16 <= y + h) {
            int boxH = renderAddBox(g, x + m, cy, w - m * 2, "+ Add Reward", mx, my);
            addRewardBoxRect = new int[] { x + m, cy, w - m * 2, boxH };
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
            ItemStack stack = rewardStack(ir);
            g.renderItem(stack, x + off, y + off);
            if (sz >= 18) g.renderItemDecorations(font, stack, x + off, y + off);
        } else {
            String glyph = switch (reward.getType()) {
                case XP -> "⚡";
                case COMMAND -> "◆";
                case LOOT_TABLE -> "📦";
                case SCRIPT_EVENT -> "✦";
                case REWARD_TABLE -> "⊞";
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

    private ItemStack rewardStack(QuestReward.ItemReward reward) {
        ItemStack stack = new ItemStack(reward.getItem(), reward.getCount());
        if (reward.getNbt() != null && !reward.getNbt().isEmpty()) stack.setTag(reward.getNbt().copy());
        return stack;
    }

    private ItemStack getTaskIcon(QuestTask task) {
        ResourceLocation id = task.getDisplayItemId();
        if (id == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, taskRequiredCount(task));

        if (task instanceof net.phoenixvine.chronicles.tasks.ItemRequirementTask t && t.getNbtFilter() != null &&
                !t.getNbtFilter().isEmpty()) {
            stack.setTag(t.getNbtFilter().copy());
        }
        return stack;
    }

    private int taskRequiredCount(QuestTask task) {
        if (task instanceof ItemRequirementTask t) return Math.max(1, t.getRequiredCount());
        if (task instanceof CraftItemTask t) return Math.max(1, t.getRequiredCount());
        if (task instanceof TagItemTask t) return Math.max(1, t.getRequired());
        if (task instanceof FilterItemTask t) return Math.max(1, t.getCount());
        return 1;
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
        if (task instanceof net.phoenixvine.chronicles.tasks.TimerTask) return "§b⏱";
        if (task instanceof TagItemTask) return "§e◈";
        if (task instanceof EnergyStorageTask) return "§6⚡";
        if (task instanceof FilterItemTask) return "§e◈";
        if (task instanceof FilterFluidTask) return "§3◈";
        if (task instanceof net.phoenixvine.chronicles.tasks.ViewMachineTask) return "§b⬡";
        if (task instanceof net.phoenixvine.chronicles.tasks.ViewSceneTask) return "§b⬢";
        if (task instanceof net.phoenixvine.chronicles.tasks.ViewGuideTask) return "§b📖";
        return "§8◇";
    }

    private String getTaskDetail(QuestTask task) {
        if (task instanceof ItemRequirementTask t) {

            String name = "item";
            if (t.getItem() != null) {
                ItemStack display = t.getItem().getDefaultInstance();
                if (t.getNbtFilter() != null && !t.getNbtFilter().isEmpty()) display.setTag(t.getNbtFilter().copy());
                name = display.getHoverName().getString();
            }
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
            return null;
        }
        if (task instanceof net.phoenixvine.chronicles.tasks.TimerTask t) {
            return "Wait " + t.getDurationSeconds() + "s";
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
        if (task instanceof net.phoenixvine.chronicles.tasks.ViewGuideTask t) {
            return "Phantasia guide: " + t.getGuideId();
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
            lines.add(Component.literal("§fReward: " + rewardStack(ir).getHoverName().getString() +
                    " §8×" + ir.getCount()));
            lines.add(Component.literal("§8[Click to view in recipe browser]"));
        } else {
            lines.add(Component.literal("§f" + reward.getType().name() + " Reward"));
        }
        return lines;
    }

    private void tryOpenInRecipeViewer(ItemStack stack) {
        if (stack.isEmpty() || minecraft == null) return;
        try {
            Class<?> api = Class.forName("dev.emi.emi.api.EmiApi");
            Class<?> esClass = Class.forName("dev.emi.emi.api.stack.EmiStack");
            Class<?> ingredientClass = Class.forName("dev.emi.emi.api.stack.EmiIngredient");
            Object es = esClass.getMethod("of", ItemStack.class).invoke(null, stack);

            api.getMethod("displayRecipes", ingredientClass).invoke(null, es);

            try {
                if (!net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get()
                        .isReturnToQuestbookFromRecipeViewer())
                    return;
                Class<?> recipeScreenClass = Class.forName("dev.emi.emi.screen.RecipeScreen");
                Object current = minecraft.screen;
                if (recipeScreenClass.isInstance(current)) {
                    Object ephemeral = recipeScreenClass.getField("old").get(current);
                    if (ephemeral instanceof net.minecraft.client.gui.screens.Screen ephemeralScreen &&
                            ephemeralScreen != this) {
                        net.phoenixvine.chronicles.client.EmiReturnScreenFix.armReturnTo(ephemeralScreen, this);
                    }
                }
            } catch (Exception ignored) {}
            return;
        } catch (Exception ignored) {}

        try {
            Class<?> jeiApi = Class.forName("mezz.jei.api.runtime.IJeiRuntime");

        } catch (Exception ignored) {}

        isFullscreen = true;
    }

    private float parseProgress(String prog) {
        if (prog == null) return 0f;

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

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (phantasiaPreview != null && previewW > 0 &&
                net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.previewMouseClicked(phantasiaPreview,
                        mx, my, previewX, previewY, previewW, previewH, this)) {
            return true;
        }
        if (isFullscreen && btn == 0 && tryStartDividerDrag(mx, my)) return true;
        return isFullscreen ? handleFullscreenClick(mx, my, btn) : handleCompactClick(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingInspDivider) {
            int delta = (int) mx - dividerDragStartMx;
            int rewardW = calcRewardW();
            int maxInspW = Math.max(MIN_INSP_W, width - MARGIN * 3 - MIN_CONTENT_W - rewardW);
            int newInspW = Math.max(MIN_INSP_W, Math.min(maxInspW, dividerDragStartInspW - delta));
            net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get().setTaskInspectorW(newInspW);
            return true;
        }
        if (draggingRewardDivider) {
            int delta = (int) mx - dividerDragStartMx;
            int pairTotal = dividerDragStartInspW + dividerDragStartRewardW;
            int newInspW = Math.max(MIN_INSP_W, Math.min(pairTotal - MIN_REWARD_W, dividerDragStartInspW + delta));
            int newRewardW = pairTotal - newInspW;
            var settings = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get();
            settings.setTaskInspectorW(newInspW);
            settings.setTaskRewardW(newRewardW);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (draggingInspDivider || draggingRewardDivider) {
            draggingInspDivider = false;
            draggingRewardDivider = false;
            net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get().save();
            return true;
        }
        return super.mouseReleased(mx, my, btn);
    }

    private boolean handleCompactClick(double mx, double my, int btn) {
        if (btn == 1 && isEditMode && minecraft != null && (hoveredTask != null || hoveredReward != null)) {
            minecraft.setScreen(new TaskRewardEditorScreen(this, node));
            return true;
        }
        if (btn == 2 && isEditMode && hoveredTask != null) {
            forceCompleteTask(hoveredTask);
            return true;
        }
        if (btn != 0) return super.mouseClicked(mx, my, btn);

        List<QuestTask> tasks = node.getTasks();
        List<QuestReward> rewards = node.getRewards();

        String fullDescText = currentDisplayDescriptionText();
        int pageCount = splitDescPages(fullDescText).size();
        String descText = currentDescriptionPageText(fullDescText);
        java.util.List<net.minecraft.util.FormattedCharSequence> descLines2 = descText != null ?
                font.split(Component.literal(descText), cardW() - CARD_PAD * 2) : java.util.List.of();
        int cardH = compactCardH(tasks, rewards, descLines2, pageCount);
        int cardX = (width - cardW()) / 2;
        int cardY = Math.max(10, (height - cardH) / 2);

        if (mx < cardX || mx >= cardX + cardW() || my < cardY || my >= cardY + cardH) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }

        if (mx >= cardX + cardW() - 34 && mx < cardX + cardW() - 20 && my >= cardY + 3 && my < cardY + 17) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }

        if (mx >= cardX + cardW() - 18 && mx < cardX + cardW() - 4 && my >= cardY + 3 && my < cardY + 17) {
            isFullscreen = true;
            return true;
        }

        if (descPagerPageCount > 1 && mx >= descPagerX && mx < descPagerX + descPagerW &&
                my >= descPagerY && my < descPagerY + descPagerH) {
            boolean leftHalf = mx < descPagerX + descPagerW / 2.0;
            if (leftHalf && descPage > 0) descPage--;
            else if (!leftHalf && descPage < descPagerPageCount - 1) descPage++;
            compactDescScrollLine = 0;
            return true;
        }

        if (hoveredDescBox && isEditMode && minecraft != null) {
            minecraft.setScreen(new QuestTextInputScreen(this, "Description",
                    currentFullDescriptionRaw(), 8192,
                    v -> {
                        node.setDescription(Component.literal(v));
                        liveDescOverride = v;
                        richSpansPage = -1;
                        net.phoenixvine.chronicles.codec.QuestFileSaver.saveOneQuestToDisk(node);
                        net.phoenixvine.chronicles.client.LangSyncScheduler.markDirty();
                    }));
            return true;
        }

        if (hoveredTask != null) {
            if (tryCompleteCheckmark(hoveredTask)) return true;
            if (net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.canOpenForTask(hoveredTask)) {
                net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.openForTask(hoveredTask, this);
                return true;
            }
            ItemStack icon = getTaskIcon(hoveredTask);
            if (!icon.isEmpty()) tryOpenInRecipeViewer(icon);
            else isFullscreen = true;
            return true;
        }
        if (hoveredReward != null) {
            if (tryClaimReward(hoveredRewardIndex)) return true;
            if (hoveredReward instanceof QuestReward.ItemReward ir) {
                tryOpenInRecipeViewer(new ItemStack(ir.getItem(), ir.getCount()));
            }
            return true;
        }

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
        if (btn == 0 && !usesPopupOpen) {
            for (int i = 0; i < prereqRowRects.size(); i++) {
                int[] r = prereqRowRects.get(i);
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    QuestNode target = prereqRowTargets.get(i);
                    if (minecraft != null && parent instanceof ChronicleOverviewScreen overview) {
                        overview.navigateToNode(target);
                    }
                    return true;
                }
            }
        }

        if (usesPopupOpen && btn == 0) {
            for (int i = 0; i < usesPopupRowRects.size(); i++) {
                int[] r = usesPopupRowRects.get(i);
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    QuestNode target = usesPopupRowTargets.get(i);
                    usesPopupOpen = false;
                    if (minecraft != null && parent instanceof ChronicleOverviewScreen overview) {
                        overview.navigateToNode(target);
                    }
                    return true;
                }
            }
            usesPopupOpen = false;
            return true;
        }

        if (btn == 0 && descPagerPageCount > 1 && mx >= descPagerX && mx < descPagerX + descPagerW &&
                my >= descPagerY && my < descPagerY + descPagerH) {
            boolean leftHalf = mx < descPagerX + descPagerW / 2.0;
            if (leftHalf && descPage > 0) descPage--;
            else if (!leftHalf && descPage < descPagerPageCount - 1) descPage++;
            descScrollY = 0;
            return true;
        }
        if (btn == 1 && isEditMode && minecraft != null &&
                (hoveredStripTask != null || hoveredStripReward != null ||
                        hoveredTaskFs != null || hoveredRewardFs != null)) {
            minecraft.setScreen(new TaskRewardEditorScreen(this, node));
            return true;
        }
        if (btn == 2 && isEditMode && (hoveredStripTask != null || hoveredTaskFs != null)) {
            forceCompleteTask(hoveredStripTask != null ? hoveredStripTask : hoveredTaskFs);
            return true;
        }

        if (btn == 0 && hoveredStripTask != null) {
            if (tryCompleteCheckmark(hoveredStripTask)) return true;
            inspectorTab = InspTab.TASKS;
            inspectorScrollY = 0;
            return true;
        }
        if (hoveredStripReward != null) {
            tryClaimReward(hoveredStripRewardIdx);
            return true;
        }

        if (mx >= 4 && mx < 20 && my >= 6 && my < 22) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }

        int pinX2 = width - 20;
        int editX2 = pinX2 - 18;
        int fsX2 = editX2 - 20;
        int usesX2 = fsX2 - 20;

        boolean usesHasAny = !node.getPrerequisites().isEmpty() || !node.getChildren().isEmpty();
        if (usesHasAny && mx >= usesX2 && mx < usesX2 + 16 && my >= 6 && my < 22 && btn == 0) {
            usesPopupOpen = true;
            return true;
        }

        if (mx >= fsX2 && mx < fsX2 + 16 && my >= 6 && my < 22 && btn == 0) {
            isFullscreen = false;
            return true;
        }

        if (mx >= editX2 && mx < editX2 + 14 && my >= 6 && my < 22 && btn == 0) {
            if (minecraft != null) minecraft.setScreen(new QuestCreatorScreen(this, node));
            return true;
        }

        if (mx >= pinX2 && mx < width - 4 && my >= 6 && my < 22 && btn == 0) {
            if (playerData != null) {
                playerData.togglePin(node.getId());
                net.phoenixvine.chronicles.network.ChronicleNetwork.CHANNEL.sendToServer(
                        new net.phoenixvine.chronicles.network.packet.C2STogglePinPacket(node.getId()));
            }
            return true;
        }

        int contentTop = HEADER_H + reqBarH() + MARGIN;
        int[] pw2 = fullscreenPanelWidths();
        int inspW2 = pw2[0];
        int contentRight = width - inspW2 - pw2[1] - MARGIN * 3;
        int rightX = contentRight + MARGIN;
        if (mx >= rightX && mx < rightX + inspW2 && my >= contentTop && my < contentTop + INSP_TAB_H + 8) {
            int tabX = rightX + 6;
            for (InspTab t : visibleInspTabs()) {
                int tabW = font.width(inspTabLabel(t)) + 6;
                if (mx >= tabX && mx < tabX + tabW && my >= contentTop + 4 && my < contentTop + 4 + INSP_TAB_H) {
                    inspectorTab = t;
                    inspectorScrollY = 0;
                    return true;
                }
                tabX += tabW + 2;
            }
        }

        if (btn == 0 && isEditMode && minecraft != null && addTaskBoxRect != null &&
                mx >= addTaskBoxRect[0] && mx < addTaskBoxRect[0] + addTaskBoxRect[2] &&
                my >= addTaskBoxRect[1] && my < addTaskBoxRect[1] + addTaskBoxRect[3]) {
            minecraft.setScreen(new TaskRewardEditorScreen(this, node));
            return true;
        }
        if (btn == 0 && isEditMode && minecraft != null && addRewardBoxRect != null &&
                mx >= addRewardBoxRect[0] && mx < addRewardBoxRect[0] + addRewardBoxRect[2] &&
                my >= addRewardBoxRect[1] && my < addRewardBoxRect[1] + addRewardBoxRect[3]) {
            minecraft.setScreen(new TaskRewardEditorScreen(this, node));
            return true;
        }

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

        for (net.phoenixvine.chronicles.client.rich.RichSpan.Region r : richRegions) {
            if (r.contains(mx, my)) {
                if (r.span() instanceof net.phoenixvine.chronicles.client.rich.RichSpan.Link l) {
                    if (!l.url().startsWith("wiki:")) {
                        try {
                            java.awt.Desktop.getDesktop().browse(java.net.URI.create(l.url()));
                        } catch (Exception ignored) {}
                    }
                    return true;
                }
                if (r.span() instanceof net.phoenixvine.chronicles.client.rich.RichSpan.CodeCopy cc) {
                    if (minecraft != null) minecraft.keyboardHandler.setClipboard(cc.code());
                    return true;
                }
                if (r.span() instanceof net.phoenixvine.chronicles.client.rich.RichSpan.DetailsToggle dt) {
                    if (!descExpandedKeys.remove(dt.key())) descExpandedKeys.add(dt.key());
                    return true;
                }
                if (r.span() instanceof net.phoenixvine.chronicles.client.rich.RichSpan.ChecklistToggle ct) {
                    boolean current = descExpandedKeys.contains("CL1:" + ct.key()) ? true :
                            !descExpandedKeys.contains("CL0:" + ct.key()) && ct.checkedDefault();
                    boolean next = !current;
                    descExpandedKeys.remove("CL1:" + ct.key());
                    descExpandedKeys.remove("CL0:" + ct.key());
                    descExpandedKeys.add((next ? "CL1:" : "CL0:") + ct.key());
                    return true;
                }
            }
        }

        if (hoveredFsDescBox && isEditMode && minecraft != null) {
            minecraft.setScreen(new QuestTextInputScreen(this, "Description",
                    currentFullDescriptionRaw(), 8192,
                    v -> {
                        node.setDescription(Component.literal(v));
                        liveDescOverride = v;
                        richSpansPage = -1;
                        net.phoenixvine.chronicles.codec.QuestFileSaver.saveOneQuestToDisk(node);
                        net.phoenixvine.chronicles.client.LangSyncScheduler.markDirty();
                    }));
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    private String currentFullDescriptionRaw() {
        if (liveDescOverride != null) return liveDescOverride;
        String mdDesc = (content != null && content.description() != null) ? content.description().getString() : "";
        return mdDesc.isBlank() ? node.getDescriptionRaw().getString() : mdDesc;
    }

    private String currentDisplayDescriptionText() {
        if (liveDescOverride != null) return liveDescOverride;
        String mdDesc = (content != null && content.description() != null) ? content.description().getString() : "";
        return mdDesc.isBlank() ? node.getDescription().getString() : mdDesc;
    }

    private String currentDescriptionPageText(String fullText) {
        java.util.List<String> pages = splitDescPages(fullText);
        descPage = Math.max(0, Math.min(descPage, pages.size() - 1));
        return pages.get(descPage);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (descPagerPageCount > 1 && mx >= descPagerX && mx < descPagerX + descPagerW &&
                my >= descPagerY && my < descPagerY + descPagerH) {
            if (delta > 0 && descPage > 0) descPage--;
            else if (delta < 0 && descPage < descPagerPageCount - 1) descPage++;
            if (isFullscreen) descScrollY = 0;
            else compactDescScrollLine = 0;
            return true;
        }
        if (!isFullscreen) {
            if (mx < descBoxX || mx >= descBoxX + descBoxW || my < descBoxY || my >= descBoxY + descBoxH) {
                return false;
            }
            int maxScrollLine = Math.max(0, compactDescTotalLines - compactDescFittedLines);

            if (delta < 0 && compactDescScrollLine >= maxScrollLine && descPagerPageCount > 1 &&
                    descPage < descPagerPageCount - 1) {
                descPage++;
                compactDescScrollLine = 0;
                return true;
            }
            if (delta > 0 && compactDescScrollLine <= 0 && descPagerPageCount > 1 && descPage > 0) {
                descPage--;
                compactDescScrollLine = 0;
                return true;
            }

            if (maxScrollLine == 0) return false;
            compactDescScrollLine = Math.max(0, Math.min(maxScrollLine, compactDescScrollLine - (int) (delta * 2)));
            return true;
        }
        int contentTop = HEADER_H + reqBarH() + MARGIN;
        int[] pw3 = fullscreenPanelWidths();
        int inspW3 = pw3[0];
        int contentRight = width - inspW3 - pw3[1] - MARGIN * 3;
        int rightX = contentRight + MARGIN;
        if (mx >= rightX && mx < rightX + inspW3 && my >= contentTop && my < height - FOOTER_H - MARGIN) {
            inspectorScrollY = Math.max(0, (int) (inspectorScrollY - delta * 12));
        } else {

            if (delta < 0 && descScrollY >= fsDescMaxScrollY && descPagerPageCount > 1 &&
                    descPage < descPagerPageCount - 1) {
                descPage++;
                descScrollY = 0;
            } else if (delta > 0 && descScrollY <= 0 && descPagerPageCount > 1 && descPage > 0) {
                descPage--;
                descScrollY = 0;
            } else {
                descScrollY = Math.max(0, Math.min(fsDescMaxScrollY, (int) (descScrollY - delta * 12)));
            }
        }
        return true;
    }

    private boolean toggleViewKeyDown = false;

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            if (isFullscreen) {
                isFullscreen = false;
                return true;
            }
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        if (net.phoenixvine.chronicles.client.ChronicleKeyBindings.TOGGLE_QUEST_VIEW.matches(key, scan)) {
            if (!toggleViewKeyDown) {
                isFullscreen = !isFullscreen;
                toggleViewKeyDown = true;
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (net.phoenixvine.chronicles.client.ChronicleKeyBindings.TOGGLE_QUEST_VIEW.matches(key, scan)) {
            toggleViewKeyDown = false;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public void onClose() {
        if (phantasiaPreview != null) {
            net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.closePreview(phantasiaPreview);
            phantasiaPreview = null;
        }
        net.phoenixvine.chronicles.client.LangSyncScheduler.flushNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final int MIN_INSP_W = 90;
    private static final int MIN_REWARD_W = 70;
    private static final int MIN_CONTENT_W = 150;

    private int calcInspW() {
        int stored = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get().getTaskInspectorW();
        if (stored > 0) return Math.max(MIN_INSP_W, stored);
        int budget = Math.max(140, width - 180 - MARGIN * 4);
        return Math.max(MIN_INSP_W, Math.min(160, budget * 3 / 5));
    }

    private int calcRewardW() {
        int stored = net.phoenixvine.chronicles.codec.QuestChroniclesSettings.get().getTaskRewardW();
        if (stored > 0) return Math.max(MIN_REWARD_W, stored);
        int budget = Math.max(140, width - 180 - MARGIN * 4);
        return Math.max(MIN_REWARD_W, Math.min(REWARD_W, budget * 2 / 5));
    }

    private int[] fullscreenPanelWidths() {
        int inspW = calcInspW();
        int rewardW = calcRewardW();
        int avail = width - MARGIN * 3 - MIN_CONTENT_W;
        if (avail > 0 && inspW + rewardW > avail) {
            float scale = (float) avail / (inspW + rewardW);
            inspW = Math.max(MIN_INSP_W, Math.round(inspW * scale));
            rewardW = Math.max(MIN_REWARD_W, Math.round(rewardW * scale));
        }
        return new int[] { inspW, rewardW };
    }

    private void renderPanelDividers(GuiGraphics g, int d1x, int d2x, int contentTop, int contentH, int mx, int my) {
        boolean hovD1 = !draggingRewardDivider && isOverDivider(d1x, contentTop, contentH, mx, my);
        boolean hovD2 = !draggingInspDivider && isOverDivider(d2x, contentTop, contentH, mx, my);

        int c1 = draggingInspDivider ? 0xFFFFCC33 : hovD1 ? 0xFF88CCFF : 0;
        int c2 = draggingRewardDivider ? 0xFFFFCC33 : hovD2 ? 0xFF88CCFF : 0;
        if (c1 != 0) {
            int hx = d1x + MARGIN / 2;
            g.fill(hx - 1, contentTop, hx + 1, contentTop + contentH, c1);
        }
        if (c2 != 0) {
            int hx = d2x + MARGIN / 2;
            g.fill(hx - 1, contentTop, hx + 1, contentTop + contentH, c2);
        }
    }

    private boolean isOverDivider(int dx, int contentTop, int contentH, int mx, int my) {
        return mx >= dx - DIVIDER_HIT_PAD && mx < dx + MARGIN + DIVIDER_HIT_PAD &&
                my >= contentTop && my < contentTop + contentH;
    }

    private boolean tryStartDividerDrag(double mx, double my) {
        int contentTop = HEADER_H + reqBarH() + MARGIN;
        int contentH = height - contentTop - FOOTER_H - MARGIN;
        int[] pw = fullscreenPanelWidths();
        int inspW = pw[0], rewardW = pw[1];
        int contentRight = width - inspW - rewardW - MARGIN * 3;
        int inspX = contentRight + MARGIN;
        int d1x = contentRight;
        int d2x = inspX + inspW;

        if (isOverDivider(d1x, contentTop, contentH, (int) mx, (int) my)) {
            draggingInspDivider = true;
            dividerDragStartMx = (int) mx;
            dividerDragStartInspW = inspW;
            return true;
        }
        if (isOverDivider(d2x, contentTop, contentH, (int) mx, (int) my)) {
            draggingRewardDivider = true;
            dividerDragStartMx = (int) mx;
            dividerDragStartInspW = inspW;
            dividerDragStartRewardW = rewardW;
            return true;
        }
        return false;
    }

    private String taskProgressString(QuestTask task) {
        if (player == null) return null;
        if (playerData != null && playerData.getQuestState(node.getId(), QuestState.LOCKED) == QuestState.COMPLETED) {

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
        if (playerData != null && playerData.getQuestState(node.getId(), QuestState.LOCKED) == QuestState.COMPLETED) {
            return true;
        }
        return player != null && task.isCompletedFor(player);
    }

    private boolean rewardsClaimed() {
        return playerData != null && playerData.hasClaimedRewards(node.getId());
    }

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

    private void forceCompleteTask(QuestTask task) {
        if (minecraft == null || minecraft.player == null || task == null) return;
        minecraft.player.connection.sendCommand("chronicles forcetask " + task.getTaskId().getPath());
    }

    private boolean tryCompleteCheckmark(QuestTask task) {
        if (!(task instanceof net.phoenixvine.chronicles.tasks.CheckmarkTask)) return false;
        if (task.isCompletedFor(minecraft.player)) return true;
        ChronicleNetwork.CHANNEL.sendToServer(
                new net.phoenixvine.chronicles.network.packet.C2SCompleteCheckmarkTaskPacket(task.getTaskId()));
        return true;
    }
}
