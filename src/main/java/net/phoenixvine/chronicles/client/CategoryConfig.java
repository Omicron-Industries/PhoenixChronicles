package net.phoenixvine.chronicles.client;

import net.minecraft.client.Minecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-chapter background configuration.
 *
 * Stored at: config/phoenix_chronicles/categories.json
 * Example:
 * {
 * "MAIN": { "style": "DOT_GRID" },
 * "CHAPTER_1": { "style": "HEX_GRID", "color": "#0A120A" },
 * "BOSS": { "style": "SOLID", "color": "#0F0808" },
 * "EXPLORE": { "style": "CUSTOM", "texture": "phoenixcore:textures/gui/bg_forest.png" }
 * }
 *
 * Available styles: DOT_GRID, GRID_LINES, HEX_GRID, DIAGONAL_LINES, SOLID, CUSTOM
 */
public class CategoryConfig {

    public enum BgStyle {
        DOT_GRID,       // default – subtle dot lattice
        GRID_LINES,     // faint square grid
        HEX_GRID,       // hex-tile outline pattern
        DIAGONAL_LINES, // diagonal hatch
        SOLID,          // plain background color only
        CUSTOM          // blits a registered texture (see texture field)
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private BgStyle style = BgStyle.DOT_GRID;
    /** ARGB background tint overlaid on top of the default canvas fill. 0 = use default. */
    private int color = 0;
    /** Minecraft resource-location string for CUSTOM style, e.g. "phoenixcore:textures/gui/bg_forest.png" */
    private String texture = "";
    /** Raw (English) display name override, "" = derive from the category slug (e.g. THE_FACTORY -> "The Factory"). */
    private String displayName = "";
    /**
     * Item resource-location string for the sidebar tab icon, e.g. "minecraft:diamond". "" = no
     * icon configured yet, sidebar falls back to a generic default.
     */
    private String icon = "";
    /**
     * Last canvas view for this chapter (pan/zoom) - 0 zoom means "never saved, use the default
     * 100%/centered view". Canvas pan/zoom used to be purely in-memory screen fields, reset to
     * defaults every time a fresh ChronicleOverviewScreen instance was created (i.e. every world
     * rejoin), which is what "we don't save zoom/scroll settings on world restart" meant.
     */
    private float viewZoom = 0f;
    private int viewOffX = 0, viewOffY = 0;
    /**
     * Parent chapter this one is nested under as a true sub-chapter, "" = top-level (no parent).
     * Distinct from ChapterFolderRegistry's folders, which just visually group sibling chapters
     * under a shared label with no parent/child relationship - this makes one specific chapter
     * genuinely subordinate to another: it inherits the parent's theme (see getEffective()) when
     * it hasn't set its own, shows breadcrumb navigation, and only unlocks once the parent
     * chapter has at least one completed quest.
     */
    private String parentCategory = "";

    // ── Accessors ─────────────────────────────────────────────────────────────

    public BgStyle getStyle() {
        return style;
    }

    public int getColor() {
        return color;
    }

    public String getTexture() {
        return texture;
    }

    /** Raw (untranslated) override, "" if the slug-derived name is being used. */
    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    public float getViewZoom() {
        return viewZoom;
    }

    public int getViewOffX() {
        return viewOffX;
    }

    public int getViewOffY() {
        return viewOffY;
    }

    /** "" = top-level, no parent. */
    public String getParentCategory() {
        return parentCategory;
    }

    public void setParentCategory(String parent) {
        this.parentCategory = parent == null ? "" : parent.trim().toUpperCase();
    }

    /**
     * Resolves the configured icon to an Item, falling back to a generic default (a book, same
     * spirit as FTBQ's chapter default) when nothing's been set or the resource location no
     * longer resolves to a real item.
     */
    public net.minecraft.world.item.Item getIconItem() {
        if (!icon.isEmpty()) {
            try {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(new net.minecraft.resources.ResourceLocation(icon));
                if (item != null && item != net.minecraft.world.item.Items.AIR) return item;
            } catch (Exception ignored) {}
        }
        return net.minecraft.world.item.Items.BOOK;
    }

    public void setStyle(BgStyle s) {
        this.style = s != null ? s : BgStyle.DOT_GRID;
    }

    public void setColor(int c) {
        this.color = c;
    }

    public void setTexture(String t) {
        this.texture = t != null ? t : "";
    }

    public void setDisplayName(String n) {
        this.displayName = n != null ? n : "";
    }

    public void setIcon(String i) {
        this.icon = i != null ? i : "";
    }

    public void setView(float zoom, int offX, int offY) {
        this.viewZoom = zoom;
        this.viewOffX = offX;
        this.viewOffY = offY;
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("style", style.name());
        if (color != 0) o.addProperty("color", String.format("#%06X", color & 0x00FFFFFF));
        if (!texture.isEmpty()) o.addProperty("texture", texture);
        if (!displayName.isEmpty()) o.addProperty("display_name", displayName);
        if (!icon.isEmpty()) o.addProperty("icon", icon);
        if (!parentCategory.isEmpty()) o.addProperty("parent", parentCategory);
        if (viewZoom != 0f) {
            o.addProperty("view_zoom", viewZoom);
            o.addProperty("view_off_x", viewOffX);
            o.addProperty("view_off_y", viewOffY);
        }
        return o;
    }

    public static CategoryConfig fromJson(JsonObject o) {
        CategoryConfig cfg = new CategoryConfig();
        if (o.has("style")) {
            try {
                cfg.style = BgStyle.valueOf(o.get("style").getAsString().toUpperCase());
            } catch (Exception ignored) {}
        }
        if (o.has("color")) {
            try {
                cfg.color = (int) Long.parseLong(o.get("color").getAsString().replace("#", ""), 16);
            } catch (Exception ignored) {}
        }
        if (o.has("texture")) cfg.texture = o.get("texture").getAsString();
        if (o.has("display_name")) cfg.displayName = o.get("display_name").getAsString();
        if (o.has("icon")) cfg.icon = o.get("icon").getAsString();
        if (o.has("parent")) cfg.parentCategory = o.get("parent").getAsString().toUpperCase();
        if (o.has("view_zoom")) cfg.viewZoom = o.get("view_zoom").getAsFloat();
        if (o.has("view_off_x")) cfg.viewOffX = o.get("view_off_x").getAsInt();
        if (o.has("view_off_y")) cfg.viewOffY = o.get("view_off_y").getAsInt();
        return cfg;
    }

    // ── Disk I/O ──────────────────────────────────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, CategoryConfig> CACHE = new HashMap<>();
    private static boolean loaded = false;

    public static CategoryConfig get(String category) {
        if (!loaded) load();
        return CACHE.getOrDefault(category, new CategoryConfig());
    }

    /**
     * Same as get(), but if this category is a sub-chapter (has a parentCategory) AND hasn't set
     * its own style/color/texture, walks up to the parent's config instead - so a sub-chapter
     * inherits its parent's look by default rather than falling back to the DOT_GRID/no-tint
     * global default the way an ordinary unconfigured top-level chapter would. A sub-chapter
     * that HAS set its own style/color/texture keeps them regardless of its parent.
     * Cycle-guarded (a chapter accidentally set as its own ancestor just stops climbing).
     */
    public static CategoryConfig getEffective(String category) {
        return getEffective(category, new java.util.HashSet<>());
    }

    private static CategoryConfig getEffective(String category, java.util.Set<String> visited) {
        CategoryConfig own = get(category);
        boolean hasOwnTheme = own.style != BgStyle.DOT_GRID || own.color != 0 || !own.texture.isEmpty();
        if (hasOwnTheme || own.parentCategory.isEmpty() || !visited.add(category)) return own;
        return getEffective(own.parentCategory, visited);
    }

    /**
     * Resolved (translatable) display name for this category, or null if there's nothing to
     * show beyond the slug-derived title (caller should fall back to its own friendly() logic).
     * Real per-player translation via I18n.exists/Component.translatable, same mechanism as
     * quest title/description - resolves "phoenix_chronicles.category.<slug>.name" through
     * whichever language the player has selected, falling back to the raw stored English
     * display_name (if the packdev set one) when no translation entry exists yet.
     */
    public static String getResolvedDisplayName(String category) {
        if (category == null) return null;
        String key = "phoenix_chronicles.category." + category.toLowerCase() + ".name";
        if (net.minecraft.client.resources.language.I18n.exists(key)) {
            return net.minecraft.network.chat.Component.translatable(key).getString();
        }
        String raw = get(category).getDisplayName();
        return raw.isEmpty() ? null : raw;
    }

    public static void put(String category, CategoryConfig cfg) {
        if (!loaded) load();
        CACHE.put(category, cfg);
    }

    /** Force-reload from disk (call after edits). */
    public static void invalidate() {
        loaded = false;
        CACHE.clear();
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
                if (e.getValue().isJsonObject())
                    CACHE.put(e.getKey().toUpperCase(), fromJson(e.getValue().getAsJsonObject()));
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to load categories.json: " + e.getMessage());
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, CategoryConfig> e : CACHE.entrySet())
            root.add(e.getKey(), e.getValue().toJson());
        try {
            Path p = configPath();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to save categories.json: " + e.getMessage());
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles").resolve("categories.json");
    }
}
