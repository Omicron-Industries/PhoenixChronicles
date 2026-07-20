package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;
import net.phoenixvine.chronicles.model.QuestGroup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-quest custom toast design ("design your own toast" from the node context menu). A quest
 * with no entry here falls back to whichever preset ToastStyle is currently selected in settings.
 *
 * Stored at: config/phoenix_chronicles/quest_toasts.json, keyed by quest resource location string.
 */
public class QuestToastConfig {

    /** One positioned/styled element of a custom toast (icon, title text, or label text). */
    public static class Element {

        /** Anchor position as a fraction of screen width/height (0..1), element-center anchored. */
        public float x = 0.5f, y = 0.5f;
        public float scale = 1.0f;
        /** ARGB text color - unused for the icon element. */
        public int color = 0xFFFFFFFF;
        public boolean bold = false;

        public Element copy() {
            Element e = new Element();
            e.x = x;
            e.y = y;
            e.scale = scale;
            e.color = color;
            e.bold = bold;
            return e;
        }
    }

    public Element icon = defaultIcon();
    public Element title = defaultTitle();
    public Element label = defaultLabel();
    public int bgColor = 0xB2170D00;
    public int accentColor = 0xFFFFAA00;
    /**
     * Background rectangle half-size in GUI pixels, per axis. When {@link #bgAutoFit} is true
     * (the default, and always true for any config saved before this field existed) this is a
     * PADDING beyond the live union of the icon/title/label positions, so the background always
     * grows/shrinks/shifts to hug wherever those three currently are - simple, but means dragging
     * any ONE of them can visibly balloon or shift the whole background+accent bar along with it.
     * When false, this is instead a fixed HALF-WIDTH/HALF-HEIGHT around the background's own
     * independent anchor ({@link #bgX}, {@link #bgY}), completely decoupled from where the icon/
     * title/label happen to be - the background stays put no matter what you drag.
     */
    public float bgPadX = 26f, bgPadY = 14f;
    /** See {@link #bgPadX} - true (default) reproduces the original auto-fit-to-elements behavior. */
    public boolean bgAutoFit = true;
    /**
     * Independent background anchor, same 0..1-of-screen convention as Element#x/y - only used when {@link #bgAutoFit}
     * is false.
     */
    public float bgX = 0.5f, bgY = 0.5f;

    /**
     * One independently-positioned icon in the toast's custom icon set - each entry drags/scales
     * on its own in the designer, not locked to the others. Used to just be a plain
     * {@link QuestGroup.GroupIcon} (kind+id only) with every entry forced into a single row
     * anchored at {@link #icon}'s position, which meant adding a second icon could only ever be
     * moved together with the first.
     */
    public static class IconEntry {

        public QuestGroup.IconKind kind;
        public String id;
        /** Same 0..1-of-screen convention as Element#x/y. */
        public float x = 0.5f, y = 0.42f;
        public float scale = 1.5f;

        public IconEntry() {}

        public IconEntry(QuestGroup.IconKind kind, String id, float x, float y, float scale) {
            this.kind = kind;
            this.id = id;
            this.x = x;
            this.y = y;
            this.scale = scale;
        }

        public IconEntry copy() {
            return new IconEntry(kind, id, x, y, scale);
        }
    }

    /**
     * Custom icon set for the toast, overriding the quest's own auto icon when non-empty - each
     * entry has its own position/scale (see {@link IconEntry}).
     */
    public final List<IconEntry> icons = new ArrayList<>();

    /**
     * Optional Phantasia machine id - when set, the toast (both the real in-game notification and
     * this designer's live preview, which share the same render path) shows a live ticking 3D
     * preview as its backdrop instead of a flat color fill.
     */
    public String phantasiaMachineId = "";

    /**
     * Public so ToastDesignerScreen's "Reset element" can restore just one of the three without touching the others.
     */
    public static Element defaultIcon() {
        Element e = new Element();
        e.x = 0.5f;
        e.y = 0.42f;
        e.scale = 1.5f;
        return e;
    }

    public static Element defaultTitle() {
        Element e = new Element();
        e.x = 0.5f;
        e.y = 0.53f;
        e.scale = 1.0f;
        e.color = 0xFFFFDD66;
        e.bold = true;
        return e;
    }

    public static Element defaultLabel() {
        Element e = new Element();
        e.x = 0.5f;
        e.y = 0.47f;
        e.scale = 0.8f;
        e.color = 0xFFCCCCCC;
        return e;
    }

    public QuestToastConfig copy() {
        QuestToastConfig c = new QuestToastConfig();
        c.icon = icon.copy();
        c.title = title.copy();
        c.label = label.copy();
        c.bgColor = bgColor;
        c.accentColor = accentColor;
        c.bgPadX = bgPadX;
        c.bgPadY = bgPadY;
        c.bgAutoFit = bgAutoFit;
        c.bgX = bgX;
        c.bgY = bgY;
        c.icons.clear();
        for (IconEntry i : icons) c.icons.add(i.copy());
        c.phantasiaMachineId = phantasiaMachineId;
        return c;
    }

    // ── Disk I/O ──────────────────────────────────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, QuestToastConfig> CACHE = new HashMap<>();
    private static boolean loaded = false;

    /** Null if the quest has no custom toast design - caller should fall back to the preset style. */
    public static QuestToastConfig getOrNull(String questId) {
        if (!loaded) load();
        return CACHE.get(questId);
    }

    public static void put(String questId, QuestToastConfig cfg) {
        if (!loaded) load();
        CACHE.put(questId, cfg);
        save();
    }

    public static void remove(String questId) {
        if (!loaded) load();
        CACHE.remove(questId);
        save();
    }

    public static void load() {
        loaded = true;
        CACHE.clear();
        Path p = configPath();
        if (!Files.exists(p)) return;
        try {
            String raw = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(raw, JsonObject.class);
            if (root == null) return;
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                QuestToastConfig cfg = GSON.fromJson(e.getValue(), QuestToastConfig.class);
                if (cfg != null) CACHE.put(e.getKey(), cfg);
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to load quest_toasts.json: " + e.getMessage());
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, QuestToastConfig> e : CACHE.entrySet())
            root.add(e.getKey(), GSON.toJsonTree(e.getValue()));
        try {
            Path p = configPath();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to save quest_toasts.json: " + e.getMessage());
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles").resolve("quest_toasts.json");
    }
}
