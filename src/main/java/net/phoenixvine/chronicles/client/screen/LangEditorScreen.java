package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.CategoryConfig;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.codec.QuestContentLoader;
import net.phoenixvine.chronicles.codec.QuestFileLoader;
import net.phoenixvine.chronicles.model.QuestNode;
import net.phoenixvine.chronicles.model.QuestTask;
import net.phoenixvine.chronicles.registry.QuestTreeRegistry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * In-game text / lang editor. Shows all quest text (titles, descriptions,
 * subtitles, task descriptions) and lets packdevs edit it without touching
 * any file. Ctrl+S / "Save all" writes back to SNBT + lang/en_us.json.
 */
public class LangEditorScreen extends Screen {

    // ── Palette ───────────────────────────────────────────────────────────────
    // Core panel/header/border/text colors come from ChroniclesThemePalette.
    // Screen-specific accents (sidebar shading, row striping, dirty-state dot) stay local.
    private static final int C_SIDEBAR = 0xFF0E0E12;
    private static final int C_ACCENT = 0xFF884499;
    private static final int C_ROW_A = 0xFF131318;
    private static final int C_ROW_B = 0xFF101015;
    private static final int C_GROUP_BG = 0xFF1A1A22;
    private static final int C_SEL_ACCENT = 0xFF00AA55;
    private static final int C_DIRTY_DOT = 0xFFBB8800;

    private static final int SIDEBAR_W = 120;
    private static final int HEADER_H = 36;
    private static final int GROUP_H = 16;   // quest-name header row height
    private static final int ROW_H = 38;   // label + key hint + edit box (stacked)
    // Descriptions get a real multi-line editor instead of a single-line EditBox (which has no
    // concept of Enter-as-newline at all) - tall enough for several wrapped lines/paragraphs so
    // markdown blank-line spacing is actually visible while typing, not just after saving.
    private static final int DESC_ROW_H = 132;
    private static final int FIELD_H = 13;
    private static final int FOOTER_H = 20;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Screen parent;
    private String selectedCategory = "";
    private String searchQuery = "";
    private int sidebarScrollPx = 0;

    /** Visible entries for current category + search. */
    private final List<TextEntry> entries = new ArrayList<>();
    /** key → edited value (not yet written to disk). */
    private final Map<String, String> dirty = new LinkedHashMap<>();

    private int scrollPx = 0;    // pixel scroll offset into the content area
    private EditBox searchBox;
    private String statusMsg = "";
    private int statusTimer = 0;

    /** Subset of all widgets: the per-row EditBox/MultilineTextArea currently on screen. */
    private final List<net.minecraft.client.gui.components.AbstractWidget> rowBoxes = new ArrayList<>();

    /** fieldType: "title" | "description" | "subtitle" | "task_N" */
    private record TextEntry(
                             ResourceLocation questId,
                             String key,
                             String label,
                             String value,
                             String fieldType) {}

    // ── Constructor ───────────────────────────────────────────────────────────

    public LangEditorScreen(Screen parent) {
        super(Component.literal("Text Editor"));
        this.parent = parent;
    }

    /** Opens directly to the given quest's category, with the quest ID pre-searched. */
    public LangEditorScreen(Screen parent, QuestNode focusQuest) {
        super(Component.literal("Text Editor"));
        this.parent = parent;
        this.selectedCategory = focusQuest.getCategory() != null ? focusQuest.getCategory() : "";
        this.searchQuery = focusQuest.getId().getPath();
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        clearWidgets();
        rowBoxes.clear();

        // Sidebar: computed at render time (manual draw + mouseClicked), not Button widgets
        List<String> cats = buildCategoryList();
        if (!cats.isEmpty() && !cats.contains(selectedCategory)) selectedCategory = cats.get(0);

        // Search box
        int listX = SIDEBAR_W + 4;
        searchBox = new EditBox(font, listX, HEADER_H + 11, (width - SIDEBAR_W) / 2 - 8, 13, Component.empty());
        searchBox.setHint(Component.literal("§8Search text…"));
        searchBox.setMaxLength(128);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(v -> {
            searchQuery = v;
            scrollPx = 0;
            rebuildEntries();
            init();
        });
        addRenderableWidget(searchBox);

        // Save-all button
        addRenderableWidget(Button.builder(Component.literal("§a✔ Save all"),
                b -> saveAll()).bounds(width - 100, HEADER_H + 11, 96, 13).build());

        // Back button
        addRenderableWidget(Button.builder(Component.literal("§7‹ Back"),
                b -> {
                    if (minecraft != null) minecraft.setScreen(parent);
                })
                .bounds(listX, height - 16, 56, 12).build());

        rebuildEntries();
        buildRowBoxes();
    }

    private void rebuildEntries() {
        entries.clear();
        String q = searchQuery.toLowerCase();
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            if (!selectedCategory.equals(node.getCategory())) continue;

            // Raw (untranslated) text - this screen edits the baked English default stored in
            // SNBT/.md, so it must show that regardless of whatever locale happens to be
            // active client-side, or editing here could silently overwrite a live translation.
            String title = node.getTitleRaw().getString();
            String desc = node.getDescriptionRaw().getString();
            String sub = node.getSubtitleRaw() != null ? node.getSubtitleRaw() : "";
            String p = node.getId().getPath();

            boolean matchesSearch = q.isEmpty() || title.toLowerCase().contains(q) || desc.toLowerCase().contains(q) ||
                    sub.toLowerCase().contains(q) || p.contains(q);
            if (!matchesSearch) {
                for (QuestTask t : node.getTasks())
                    if (t.getDescriptionRaw().getString().toLowerCase().contains(q)) {
                        matchesSearch = true;
                        break;
                    }
            }
            if (!matchesSearch) continue;

            entries.add(new TextEntry(node.getId(), p + ".title", "Title", title, "title"));
            entries.add(new TextEntry(node.getId(), p + ".description", "Description", desc, "description"));
            if (!sub.isBlank())
                entries.add(new TextEntry(node.getId(), p + ".subtitle", "Subtitle", sub, "subtitle"));

            int i = 0;
            for (QuestTask task : node.getTasks()) {
                entries.add(new TextEntry(
                        node.getId(),
                        p + ".task_" + i,
                        "Task " + (i + 1),
                        task.getDescriptionRaw().getString(),
                        "task_" + i));
                i++;
            }
        }
    }

    private int listTop() {
        return HEADER_H + 28;
    }

    private int listBott() {
        return height - FOOTER_H;
    }

    /** Descriptions get the taller multi-line editor row; everything else stays single-line. */
    private static int rowHeightFor(TextEntry e) {
        return e.fieldType().equals("description") ? DESC_ROW_H : ROW_H;
    }

    /**
     * Returns the Y coordinate (relative to the top of the content area, before scroll)
     * of the START of entry[ei], accounting for group-header rows above it.
     */
    private int entryY(int ei) {
        int y = 0;
        ResourceLocation lastQuest = null;
        for (int i = 0; i <= ei && i < entries.size(); i++) {
            TextEntry e = entries.get(i);
            if (!e.questId().equals(lastQuest)) {
                lastQuest = e.questId();
                y += GROUP_H;
            }
            if (i < ei) y += rowHeightFor(e);
        }
        return y;
    }

    private int totalContentHeight() {
        if (entries.isEmpty()) return 0;
        int last = entries.size() - 1;
        return entryY(last) + rowHeightFor(entries.get(last));
    }

    private int maxScrollPx() {
        return Math.max(0, totalContentHeight() - (listBott() - listTop()));
    }

    /** Creates EditBox widgets for the rows visible in the current scroll window. */
    private void buildRowBoxes() {
        rowBoxes.forEach(this::removeWidget);
        rowBoxes.clear();

        int listX = SIDEBAR_W + 4;
        int listW = width - SIDEBAR_W - 8;
        int top = listTop();
        int bott = listBott();

        // EditBox spans the full row width, positioned at the bottom of each row
        int fieldX = listX + 4;
        int fieldW = listW - 8;

        for (int ei = 0; ei < entries.size(); ei++) {
            TextEntry entry = entries.get(ei);
            int rh = rowHeightFor(entry);
            int rowY = top + entryY(ei) - scrollPx;
            if (rowY + rh <= top) continue;  // above viewport
            if (rowY >= bott) break;          // below viewport

            String key = entry.key();
            if (entry.fieldType().equals("description")) {
                // Multi-line editor spans most of the taller row, below label and key hint
                int boxY = rowY + 16;
                int boxH = rh - 16 - 4;
                MultilineTextArea box = new MultilineTextArea(font, fieldX, boxY, fieldW, boxH, 8192);
                box.setValue(dirty.getOrDefault(key, entry.value()));
                box.setResponder(v -> dirty.put(key, v));
                addRenderableWidget(box);
                rowBoxes.add(box);
            } else {
                // Box sits at the bottom of the row, below label and key hint
                int boxY = rowY + rh - FIELD_H - 3;
                EditBox box = new EditBox(font, fieldX, boxY, fieldW, FIELD_H, Component.empty());
                box.setMaxLength(512);
                box.setValue(dirty.getOrDefault(key, entry.value()));
                box.setResponder(v -> dirty.put(key, v));
                addRenderableWidget(box);
                rowBoxes.add(box);
            }
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {
        g.fill(0, 0, width, height, ChroniclesThemePalette.BG);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        if (statusTimer > 0) statusTimer--;

        // ── Header ────────────────────────────────────────────────────────────
        g.fill(0, 0, width, HEADER_H, ChroniclesThemePalette.HEADER);
        g.fill(0, HEADER_H - 1, width, HEADER_H, ChroniclesThemePalette.BORDER);
        g.drawString(font, "§dText Editor  §8│  §7" + friendly(selectedCategory),
                SIDEBAR_W + 6, 6, ChroniclesThemePalette.TEXT);
        g.drawString(font, "§8Ctrl+S saves  ·  primary: quests/*.md  ·  also exports lang/en_us.json",
                SIDEBAR_W + 6, 16, ChroniclesThemePalette.TEXT_FAINT);

        // ── Sidebar ───────────────────────────────────────────────────────────
        g.fill(0, 0, SIDEBAR_W, height, C_SIDEBAR);
        g.fill(SIDEBAR_W, 0, SIDEBAR_W + 1, height, ChroniclesThemePalette.BORDER);
        g.drawCenteredString(font, "§8CHAPTERS", SIDEBAR_W / 2, HEADER_H - 10, ChroniclesThemePalette.TEXT_FAINT);

        List<String> cats = buildCategoryList();
        int sidebarContentH = cats.size() * 15;
        int sidebarViewH = height - HEADER_H - 4;
        int maxSidebarScroll = Math.max(0, sidebarContentH - sidebarViewH);
        sidebarScrollPx = Math.max(0, Math.min(maxSidebarScroll, sidebarScrollPx));

        g.enableScissor(0, HEADER_H, SIDEBAR_W, height);
        int ty = HEADER_H + 4 - sidebarScrollPx;
        for (String cat : cats) {
            boolean sel = cat.equals(selectedCategory);
            boolean hov = mx >= 2 && mx < SIDEBAR_W - 2 && my >= ty && my < ty + 13;
            if (sel) {
                g.fill(0, ty - 1, SIDEBAR_W - 1, ty + 14, 0xFF1A1A26);
                g.fill(0, ty - 1, 3, ty + 14, C_SEL_ACCENT);
            } else if (hov) {
                g.fill(2, ty - 1, SIDEBAR_W - 2, ty + 14, 0xFF161620);
            }
            g.drawString(font, sel ? "§f" + friendly(cat) : "§8" + friendly(cat), 6, ty + 2,
                    ChroniclesThemePalette.TEXT_DIM);
            ty += 15;
        }
        g.disableScissor();

        // Sidebar scrollbar
        if (maxSidebarScroll > 0) {
            int thumbH = Math.max(12, sidebarViewH * sidebarViewH / (sidebarViewH + maxSidebarScroll));
            int thumbY = HEADER_H + 4 + (int) ((long) sidebarScrollPx * (sidebarViewH - thumbH) / maxSidebarScroll);
            g.fill(SIDEBAR_W - 3, HEADER_H + 4, SIDEBAR_W - 1, height - 4, 0x22FFFFFF);
            g.fill(SIDEBAR_W - 3, thumbY, SIDEBAR_W - 1, thumbY + thumbH, 0x88AAAACC);
        }

        // ── Content area ──────────────────────────────────────────────────────
        int listX = SIDEBAR_W + 4;
        int listW = width - SIDEBAR_W - 8;
        int top = listTop();
        int bott = listBott();

        g.enableScissor(listX, top, listX + listW, bott);

        // Group headers (computed from same layout as buildRowBoxes)
        ResourceLocation lastGroupRendered = null;
        for (int ei = 0; ei < entries.size(); ei++) {
            TextEntry entry = entries.get(ei);
            int rowY = top + entryY(ei) - scrollPx;

            // Group header appears just above entry[ei] when it starts a new quest
            if (!entry.questId().equals(lastGroupRendered)) {
                lastGroupRendered = entry.questId();
                int gy = rowY - GROUP_H;
                if (gy + GROUP_H > top && gy < bott) {
                    g.fill(listX, gy, listX + listW, gy + GROUP_H, C_GROUP_BG);
                    g.fill(listX, gy, listX + 3, gy + GROUP_H, C_ACCENT);
                    g.drawString(font, "§d▸ §7quests/" + entry.questId().getPath() + ".md",
                            listX + 8, gy + 3, ChroniclesThemePalette.TEXT);
                }
            }

            int rh = rowHeightFor(entry);
            if (rowY + rh <= top || rowY >= bott) continue;

            // Alternating row bg
            g.fill(listX, rowY, listX + listW, rowY + rh, ei % 2 == 0 ? C_ROW_A : C_ROW_B);

            // Field label (top-left of row)
            g.drawString(font, "§7" + entry.label(), listX + 6, rowY + 3, ChroniclesThemePalette.TEXT_DIM);

            // Lang key hint — truncated to prevent bleeding into the edit box. Task rows resolve
            // by the task's own stable task_id (see QuestTask.getDescription()), not by index
            // within this quest, so their key looks different from title/description/subtitle.
            String langKey;
            if (entry.fieldType().startsWith("task_")) {
                int idx = Integer.parseInt(entry.fieldType().substring(5));
                QuestNode owner = QuestTreeRegistry.getQuest(entry.questId());
                langKey = owner != null && idx < owner.getTasks().size() ?
                        "phoenix_chronicles.task." + owner.getTasks().get(idx).getTaskId().getPath().replace('/', '.') :
                        "phoenix_chronicles.task.?";
            } else {
                langKey = "phoenix_chronicles.quest." + entry.questId().getPath().replace('/', '.') + "." +
                        entry.fieldType();
            }
            int maxKeyW = listW - 16;
            String langKeyDisplay = font.width(langKey) > maxKeyW ?
                    font.plainSubstrByWidth(langKey, maxKeyW - font.width("…")) + "…" : langKey;
            g.drawString(font, langKeyDisplay, listX + 6, rowY + 13, ChroniclesThemePalette.TEXT_FAINT);

            // Dirty indicator strip
            if (dirty.containsKey(entry.key()))
                g.fill(listX + listW - 3, rowY, listX + listW, rowY + rh, C_DIRTY_DOT);
        }

        g.disableScissor();

        // Scrollbar track + thumb
        if (maxScrollPx() > 0) {
            int trackX = listX + listW + 1;
            int trackH = bott - top;
            g.fill(trackX, top, trackX + 3, bott, 0x22FFFFFF);
            int thumbH = Math.max(16, trackH * trackH / (trackH + maxScrollPx()));
            int thumbY = top + (int) ((long) scrollPx * (trackH - thumbH) / maxScrollPx());
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0x88AAAACC);
        }

        // ── Footer ────────────────────────────────────────────────────────────
        g.fill(SIDEBAR_W, height - 18, width, height, ChroniclesThemePalette.PANEL);
        g.fill(SIDEBAR_W, height - 19, width, height - 18, ChroniclesThemePalette.BORDER);

        int dirtyCount = dirty.size();
        if (dirtyCount > 0)
            g.drawString(font, "§6" + dirtyCount + " unsaved change(s)",
                    listX + 64, height - 13, C_DIRTY_DOT);

        // Status message
        if (statusTimer > 0)
            g.drawString(font, statusMsg, listX + 64, height - 13, ChroniclesThemePalette.TEXT);

        // Entry count
        g.drawString(font, "§8" + entries.size() + " fields  ·  " + countQuests() + " quests",
                width - 140, height - 13, ChroniclesThemePalette.TEXT_FAINT);

        // Widgets (buttons + editboxes)
        super.render(g, mx, my, partial);
    }

    private int countQuests() {
        Set<ResourceLocation> seen = new HashSet<>();
        for (TextEntry e : entries) seen.add(e.questId());
        return seen.size();
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Sidebar category click
        if (mx < SIDEBAR_W && my > HEADER_H) {
            List<String> cats = buildCategoryList();
            int ty = HEADER_H + 4 - sidebarScrollPx;
            for (String cat : cats) {
                if (my >= ty && my < ty + 13) {
                    if (!cat.equals(selectedCategory)) {
                        selectedCategory = cat;
                        scrollPx = 0;
                        rebuildEntries();
                        init();
                    }
                    return true;
                }
                ty += 15;
            }
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < SIDEBAR_W) {
            // Scroll sidebar
            List<String> cats = buildCategoryList();
            int sidebarContentH = cats.size() * 15;
            int sidebarViewH = height - HEADER_H - 4;
            int maxSidebarScroll = Math.max(0, sidebarContentH - sidebarViewH);
            sidebarScrollPx = (int) Math.max(0, Math.min(maxSidebarScroll, sidebarScrollPx - delta * 15));
            return true;
        }
        int prev = scrollPx;
        scrollPx = (int) Math.max(0, Math.min(maxScrollPx(), scrollPx - delta * ROW_H * 2));
        if (scrollPx != prev) buildRowBoxes();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { // Escape
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        boolean ctrl = (mods & 2) != 0;
        if (ctrl && key == 83) {
            saveAll();
            return true;
        } // Ctrl+S

        // Enter/newline routed explicitly to whichever description box is focused, rather than
        // trusting vanilla's getFocused()-based dispatch alone - this screen has dozens of
        // sibling EditBox/MultilineTextArea widgets on screen at once (one pair per visible
        // row), and container-level focus tracking across that many widgets is exactly the kind
        // of thing that silently breaks. Doing it here guarantees the newline lands regardless.
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            for (net.minecraft.client.gui.components.AbstractWidget w : rowBoxes) {
                if (w instanceof MultilineTextArea mta && mta.isFocused()) {
                    mta.forceInsert("\n");
                    return true;
                }
            }
        }

        return super.keyPressed(key, scan, mods);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveAll() {
        if (dirty.isEmpty()) {
            setStatus("§8Nothing to save.");
            return;
        }

        Path base = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles");
        Path questsDir = base.resolve("quests");

        // Group dirty entries by quest path
        Map<String, List<TextEntry>> byQuest = new LinkedHashMap<>();
        for (TextEntry entry : entries) {
            if (!dirty.containsKey(entry.key())) continue;
            byQuest.computeIfAbsent(entry.questId().getPath(), k -> new ArrayList<>()).add(entry);
        }

        int saved = 0;
        for (Map.Entry<String, List<TextEntry>> qe : byQuest.entrySet()) {
            String questPath = qe.getKey();
            List<TextEntry> fields = qe.getValue();

            // ── Primary: write / update the .md file ──────────────────────────
            Path mdFile = questsDir.resolve(questPath + ".md");
            try {
                Files.createDirectories(mdFile.getParent());

                // Collect new values (fall back to current entry value if not dirtied)
                String newTitle = null;
                String newDesc = null;
                for (TextEntry e : fields) {
                    String v = dirty.get(e.key());
                    if ("title".equals(e.fieldType())) newTitle = v;
                    if ("description".equals(e.fieldType())) newDesc = v;
                }

                if (Files.exists(mdFile)) {
                    // Patch in place — rewrite front matter title and/or body
                    String existing = Files.readString(mdFile, StandardCharsets.UTF_8);
                    String patched = patchMdFile(existing, newTitle, newDesc);
                    Files.writeString(mdFile, patched, StandardCharsets.UTF_8);
                } else {
                    // Create a new .md file from scratch
                    String t = newTitle != null ? newTitle : questPath;
                    String d = newDesc != null ? newDesc : "";
                    Files.writeString(mdFile, buildMdFile(t, d), StandardCharsets.UTF_8);
                }
                saved++;
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            // ── Secondary: patch .snbt for subtitle + task descriptions ───────
            Path snbt = base.resolve(questPath + ".snbt");
            if (Files.exists(snbt)) {
                try {
                    // Use TagParser for task descs; regex for subtitle (avoids reformatting when only subtitle changed)
                    boolean hasTaskChanges = fields.stream().anyMatch(e -> e.fieldType().startsWith("task_"));

                    if (hasTaskChanges) {
                        // Full NBT round-trip to patch task descriptions
                        net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(
                                Files.readString(snbt, StandardCharsets.UTF_8));
                        // Apply subtitle via NBT too
                        for (TextEntry e : fields) {
                            String v = dirty.get(e.key());
                            if (v == null) continue;
                            if ("subtitle".equals(e.fieldType())) {
                                tag.putString("subtitle", v);
                            } else if (e.fieldType().startsWith("task_")) {
                                int idx = Integer.parseInt(e.fieldType().substring(5));
                                if (tag.contains("tasks")) {
                                    net.minecraft.nbt.ListTag taskList = tag.getList("tasks",
                                            net.minecraft.nbt.Tag.TAG_COMPOUND);
                                    if (idx < taskList.size()) {
                                        net.minecraft.nbt.CompoundTag tTag = taskList.getCompound(idx).copy();
                                        tTag.putString("description",
                                                net.minecraft.network.chat.Component.Serializer.toJson(
                                                        net.minecraft.network.chat.Component.literal(v)));
                                        taskList.set(idx, tTag);
                                    }
                                }
                                // Update in-memory task so HUD/detail screens reflect the change immediately
                                QuestNode qNode = QuestTreeRegistry.getQuest(
                                        new net.minecraft.resources.ResourceLocation("phoenixcore", questPath));
                                if (qNode != null) {
                                    int idx2 = Integer.parseInt(e.fieldType().substring(5));
                                    if (idx2 < qNode.getTasks().size())
                                        qNode.getTasks().get(idx2).setDescription(
                                                net.minecraft.network.chat.Component.literal(v));
                                }
                            }
                        }
                        Files.writeString(snbt, tag.toString(), StandardCharsets.UTF_8);
                    } else {
                        // Subtitle-only: regex patch (preserves formatting)
                        String content = Files.readString(snbt, StandardCharsets.UTF_8);
                        for (TextEntry e : fields) {
                            String v = dirty.get(e.key());
                            if ("subtitle".equals(e.fieldType()) && v != null) {
                                if (content.contains("subtitle:"))
                                    content = content.replaceAll("subtitle:\\s*\"[^\"]*\"",
                                            "subtitle: \"" + esc(v) + "\"");
                                else {
                                    int last = content.lastIndexOf('}');
                                    if (last >= 0)
                                        content = content.substring(0, last) + "  subtitle: \"" + esc(v) + "\"\n" +
                                                content.substring(last);
                                }
                            }
                        }
                        Files.writeString(snbt, content, StandardCharsets.UTF_8);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        // Also export the compiled JSON lang file as a convenience
        writeEnUsJson(base);

        dirty.clear();
        // Reload md content into registry, then re-inject snbt structure
        QuestContentLoader.reloadAllQuestsFromDisk();
        QuestFileLoader.loadAdditiveFromDisk(base);
        rebuildEntries();
        buildRowBoxes();
        setStatus("§a✔ Saved " + saved + " quest(s)  →  quests/*.md  +  lang/en_us.json");
    }

    /**
     * Patches title and/or body in an existing .md file while preserving all other content.
     * Handles files with or without front-matter.
     */
    static String patchMdFile(String original, String newTitle, String newDesc) {
        // Split into front-matter and body
        boolean hasFrontMatter = original.startsWith("---");
        String frontMatter = "";
        String body = original;

        if (hasFrontMatter) {
            int second = original.indexOf("---", 3);
            if (second >= 0) {
                frontMatter = original.substring(0, second + 3);
                body = original.substring(second + 3).stripLeading();
            }
        }

        // Patch title in front matter
        if (newTitle != null) {
            if (hasFrontMatter && frontMatter.contains("title:")) {
                frontMatter = frontMatter.replaceAll("(?m)^title:.*$",
                        "title: \"" + newTitle.replace("\"", "\\\"") + "\"");
            } else if (hasFrontMatter) {
                // Insert title before closing ---
                frontMatter = frontMatter.substring(0, frontMatter.lastIndexOf("---")) + "title: \"" +
                        newTitle.replace("\"", "\\\"") + "\"\n---";
            } else {
                // No front matter at all — prepend it
                frontMatter = "---\ntitle: \"" + newTitle.replace("\"", "\\\"") + "\"\n---\n";
                hasFrontMatter = true;
            }
        }

        // Patch body
        if (newDesc != null) {
            body = newDesc;
        }

        return hasFrontMatter ? frontMatter + "\n" + body : body;
    }

    /** Builds a new .md file from scratch. */
    private static String buildMdFile(String title, String body) {
        return "---\ntitle: \"" + title.replace("\"", "\\\"") + "\"\n---\n\n" + body;
    }

    /**
     * Writes config/phoenix_chronicles/assets/phoenix_chronicles/lang/en_us.json from the full
     * registry, then back-fills any missing keys in sibling lang files with the English value,
     * and reloads the client's resource packs so the change (and any newly-added keys) take
     * effect immediately. Reads the RAW baked text, not the resolved/translated text - this
     * function's job is to snapshot the English baseline, and if some other locale happens to
     * be active client-side, the resolved getters would return THAT text instead of English.
     */
    public static void writeEnUsJson(Path base) {
        Map<String, String> lang = new LinkedHashMap<>();
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            String p = node.getId().getPath().replace('/', '.');
            lang.put("phoenix_chronicles.quest." + p + ".title", node.getTitleRaw().getString());
            lang.put("phoenix_chronicles.quest." + p + ".description", node.getDescriptionRaw().getString());
            if (node.getSubtitleRaw() != null && !node.getSubtitleRaw().isBlank())
                lang.put("phoenix_chronicles.quest." + p + ".subtitle", node.getSubtitleRaw());
            // Keyed by the task's own stable task_id, not its index within the quest - matches
            // QuestTask.getDescription()'s lookup key, and survives task reordering/insertion.
            for (QuestTask task : node.getTasks()) {
                String taskKey = "phoenix_chronicles.task." + task.getTaskId().getPath().replace('/', '.');
                lang.put(taskKey, task.getDescriptionRaw().getString());
            }
        }
        try {
            Path langDir = net.phoenixvine.chronicles.registry.QuestLangRegistry.langDir(base);
            Files.createDirectories(langDir);
            net.phoenixvine.chronicles.registry.QuestLangRegistry.ensurePackStructure(base);
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            Path enUsFile = langDir.resolve("en_us.json");
            // Only reload resource packs (a multi-second stall on a heavily modded pack, same
            // concern CategoryThemeScreen already guards against for its own lang key) if the
            // lang content actually changed - this used to fire unconditionally on every save,
            // which is what made even a pure position/shape/task edit in QuestCreatorScreen pop
            // the "Reloading Resources" screen.
            String newJson = gson.toJson(lang);
            boolean changed = !Files.exists(enUsFile) ||
                    !Files.readString(enUsFile, StandardCharsets.UTF_8).equals(newJson);
            Files.writeString(enUsFile, newJson, StandardCharsets.UTF_8);
            syncOtherLangFiles(langDir, lang, gson);
            if (changed) net.phoenixvine.chronicles.client.ChroniclesLangPack.reload();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * For every *.json in langDir that is NOT en_us.json, adds any keys from
     * {@code enUs} that are absent in that file. Existing translations are untouched.
     */
    @SuppressWarnings("unchecked")
    private static void syncOtherLangFiles(Path langDir, Map<String, String> enUs, Gson gson) {
        try (java.util.stream.Stream<Path> files = Files.list(langDir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json") &&
                    !p.getFileName().toString().equals("en_us.json"))
                    .forEach(langFile -> {
                        try {
                            Map<String, String> existing;
                            if (Files.exists(langFile)) {
                                String raw = Files.readString(langFile, java.nio.charset.StandardCharsets.UTF_8);
                                existing = gson.fromJson(raw, LinkedHashMap.class);
                                if (existing == null) existing = new LinkedHashMap<>();
                            } else {
                                existing = new LinkedHashMap<>();
                            }
                            boolean changed = false;
                            for (Map.Entry<String, String> e : enUs.entrySet()) {
                                if (!existing.containsKey(e.getKey())) {
                                    existing.put(e.getKey(), e.getValue());
                                    changed = true;
                                }
                            }
                            if (changed)
                                Files.writeString(langFile, gson.toJson(existing),
                                        java.nio.charset.StandardCharsets.UTF_8);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    });
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setStatus(String msg) {
        statusMsg = msg;
        statusTimer = 80;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private List<String> buildCategoryList() {
        List<String> cats = new ArrayList<>();
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            String c = n.getCategory();
            if (c != null && !cats.contains(c)) cats.add(c);
        }
        return cats;
    }

    private String friendly(String cat) {
        if (cat == null || cat.isBlank()) return "All";
        String resolved = CategoryConfig.getResolvedDisplayName(cat);
        if (resolved != null) return resolved;
        StringBuilder sb = new StringBuilder();
        for (String w : cat.toLowerCase().replace("_", " ").split(" "))
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }
}
