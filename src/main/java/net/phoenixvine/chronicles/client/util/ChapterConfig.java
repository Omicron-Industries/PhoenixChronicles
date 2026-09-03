package net.phoenixvine.chronicles.client.util;

import net.minecraft.client.Minecraft;
import net.phoenixvine.chronicles.client.event.ClientTextOverrides;
import net.phoenixvine.chronicles.flag.PhoenixQuestFlags;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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

public class ChapterConfig {

    public enum BgStyle {
        DOT_GRID,
        GRID_LINES,
        HEX_GRID,
        DIAGONAL_LINES,
        SOLID,
        CUSTOM,
        SHADER
    }

    public static final class CanvasOverride {

        public String condition = "";
        public BgStyle style = BgStyle.DOT_GRID;
        public String texture = "";
        public String shaderId = "";

        public CanvasOverride() {}

        public CanvasOverride(String condition, BgStyle style, String texture, String shaderId) {
            this.condition = condition != null ? condition : "";
            this.style = style != null ? style : BgStyle.DOT_GRID;
            this.texture = texture != null ? texture : "";
            this.shaderId = shaderId != null ? shaderId : "";
        }

        public CanvasOverride copy() {
            return new CanvasOverride(condition, style, texture, shaderId);
        }
    }

    public static final class SidebarOverride {

        public String condition = "";
        public String sidebarShaderId = "";

        public SidebarOverride() {}

        public SidebarOverride(String condition, String sidebarShaderId) {
            this.condition = condition != null ? condition : "";
            this.sidebarShaderId = sidebarShaderId != null ? sidebarShaderId : "";
        }

        public SidebarOverride copy() {
            return new SidebarOverride(condition, sidebarShaderId);
        }
    }

    private BgStyle style = BgStyle.DOT_GRID;

    private int color = 0;

    private int colorAlpha = 0xCC;

    private int nameColor = 0;

    private String texture = "";

    private String shaderId = "";

    private String sidebarShaderId = "";

    private final List<CanvasOverride> canvasOverrides = new ArrayList<>();
    private final List<SidebarOverride> sidebarOverrides = new ArrayList<>();

    private String displayName = "";

    private String icon = "";

    private String parentChapter = "";

    public BgStyle getStyle() {
        return style;
    }

    public int getColor() {
        return color;
    }

    public int getColorAlpha() {
        return colorAlpha;
    }

    public void setColorAlpha(int a) {
        this.colorAlpha = Math.max(0, Math.min(255, a));
    }

    public int getEffectiveNameColor() {
        return nameColor != 0 ? nameColor : color;
    }

    public int getNameColor() {
        return nameColor;
    }

    public String getTexture() {
        return texture;
    }

    public String getShaderId() {
        return shaderId;
    }

    public void setShaderId(String s) {
        this.shaderId = s != null ? s : "";
    }

    public String getSidebarShaderId() {
        return sidebarShaderId;
    }

    public void setSidebarShaderId(String s) {
        this.sidebarShaderId = s != null ? s : "";
    }

    public List<CanvasOverride> getCanvasOverrides() {
        return java.util.Collections.unmodifiableList(canvasOverrides);
    }

    public void setCanvasOverrides(List<CanvasOverride> overrides) {
        canvasOverrides.clear();
        if (overrides == null) return;
        for (CanvasOverride o : overrides) {
            if (o != null) canvasOverrides.add(o.copy());
        }
    }

    public List<SidebarOverride> getSidebarOverrides() {
        return java.util.Collections.unmodifiableList(sidebarOverrides);
    }

    public void setSidebarOverrides(List<SidebarOverride> overrides) {
        sidebarOverrides.clear();
        if (overrides == null) return;
        for (SidebarOverride o : overrides) {
            if (o != null) sidebarOverrides.add(o.copy());
        }
    }

    public CanvasOverride resolveCanvas() {
        for (CanvasOverride o : canvasOverrides) {
            if (PhoenixQuestFlags.evaluate(o.condition, null, "chapter canvas theme override")) return o;
        }
        return new CanvasOverride("", style, texture, shaderId);
    }

    public String resolveSidebarShaderId() {
        for (SidebarOverride o : sidebarOverrides) {
            if (PhoenixQuestFlags.evaluate(o.condition, null, "chapter sidebar shader override")) {
                return o.sidebarShaderId;
            }
        }
        return sidebarShaderId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    public String getParentChapter() {
        return parentChapter;
    }

    public void setParentChapter(String parent) {
        this.parentChapter = parent == null ? "" : parent.trim().toUpperCase();
    }

    public net.minecraft.world.item.Item getIconItem() {
        if (!icon.isEmpty()) {
            try {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(net.minecraft.resources.ResourceLocation.parse(icon));
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

    public void setNameColor(int c) {
        this.nameColor = c;
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

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("style", style.name());
        if (color != 0) o.addProperty("color", String.format("#%06X", color & 0x00FFFFFF));
        if (colorAlpha != 0xCC) o.addProperty("color_alpha", colorAlpha);
        if (nameColor != 0) o.addProperty("name_color", String.format("#%06X", nameColor & 0x00FFFFFF));
        if (!texture.isEmpty()) o.addProperty("texture", texture);
        if (!shaderId.isEmpty()) o.addProperty("shader_id", shaderId);
        if (!sidebarShaderId.isEmpty()) o.addProperty("sidebar_shader_id", sidebarShaderId);
        if (!canvasOverrides.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (CanvasOverride ov : canvasOverrides) {
                JsonObject ovo = new JsonObject();
                ovo.addProperty("condition", ov.condition);
                ovo.addProperty("style", ov.style.name());
                if (!ov.texture.isEmpty()) ovo.addProperty("texture", ov.texture);
                if (!ov.shaderId.isEmpty()) ovo.addProperty("shader_id", ov.shaderId);
                arr.add(ovo);
            }
            o.add("canvas_overrides", arr);
        }
        if (!sidebarOverrides.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (SidebarOverride ov : sidebarOverrides) {
                JsonObject ovo = new JsonObject();
                ovo.addProperty("condition", ov.condition);
                if (!ov.sidebarShaderId.isEmpty()) ovo.addProperty("sidebar_shader_id", ov.sidebarShaderId);
                arr.add(ovo);
            }
            o.add("sidebar_overrides", arr);
        }
        if (!displayName.isEmpty()) o.addProperty("display_name", displayName);
        if (!icon.isEmpty()) o.addProperty("icon", icon);
        if (!parentChapter.isEmpty()) o.addProperty("parent", parentChapter);
        return o;
    }

    public static ChapterConfig fromJson(JsonObject o) {
        ChapterConfig cfg = new ChapterConfig();
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
        if (o.has("color_alpha")) {
            try {
                cfg.colorAlpha = Math.max(0, Math.min(255, o.get("color_alpha").getAsInt()));
            } catch (Exception ignored) {}
        }
        if (o.has("name_color")) {
            try {
                cfg.nameColor = (int) Long.parseLong(o.get("name_color").getAsString().replace("#", ""), 16);
            } catch (Exception ignored) {}
        }
        if (o.has("texture")) cfg.texture = o.get("texture").getAsString();
        if (o.has("shader_id")) cfg.shaderId = o.get("shader_id").getAsString();
        if (o.has("sidebar_shader_id")) cfg.sidebarShaderId = o.get("sidebar_shader_id").getAsString();
        if (o.has("canvas_overrides") && o.get("canvas_overrides").isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray("canvas_overrides")) {
                if (!el.isJsonObject()) continue;
                JsonObject ovo = el.getAsJsonObject();
                String cond = ovo.has("condition") ? ovo.get("condition").getAsString() : "";
                BgStyle ovStyle = BgStyle.DOT_GRID;
                if (ovo.has("style")) {
                    try {
                        ovStyle = BgStyle.valueOf(ovo.get("style").getAsString().toUpperCase());
                    } catch (Exception ignored) {}
                }
                String tex = ovo.has("texture") ? ovo.get("texture").getAsString() : "";
                String sid = ovo.has("shader_id") ? ovo.get("shader_id").getAsString() : "";
                cfg.canvasOverrides.add(new CanvasOverride(cond, ovStyle, tex, sid));
            }
        }
        if (o.has("sidebar_overrides") && o.get("sidebar_overrides").isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray("sidebar_overrides")) {
                if (!el.isJsonObject()) continue;
                JsonObject ovo = el.getAsJsonObject();
                String cond = ovo.has("condition") ? ovo.get("condition").getAsString() : "";
                String ssid = ovo.has("sidebar_shader_id") ? ovo.get("sidebar_shader_id").getAsString() : "";
                cfg.sidebarOverrides.add(new SidebarOverride(cond, ssid));
            }
        }
        if (o.has("display_name")) cfg.displayName = o.get("display_name").getAsString();
        if (o.has("icon")) cfg.icon = o.get("icon").getAsString();
        if (o.has("parent")) cfg.parentChapter = o.get("parent").getAsString().toUpperCase();
        return cfg;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, ChapterConfig> CACHE = new HashMap<>();
    private static boolean loaded = false;

    public static ChapterConfig get(String chapter) {
        if (!loaded) load();
        return CACHE.getOrDefault(chapter, new ChapterConfig());
    }

    public static ChapterConfig getEffective(String chapter) {
        return getEffective(chapter, new java.util.HashSet<>());
    }

    private static ChapterConfig getEffective(String chapter, java.util.Set<String> visited) {
        ChapterConfig own = get(chapter);
        boolean hasOwnTheme = own.style != BgStyle.DOT_GRID || own.color != 0 || own.colorAlpha != 0xCC ||
                !own.texture.isEmpty() || !own.shaderId.isEmpty() || !own.sidebarShaderId.isEmpty() ||
                !own.canvasOverrides.isEmpty() || !own.sidebarOverrides.isEmpty();
        if (hasOwnTheme || own.parentChapter.isEmpty() || !visited.add(chapter)) return own;
        return getEffective(own.parentChapter, visited);
    }

    public static String getResolvedDisplayName(String chapter) {
        if (chapter == null) return null;
        String key = "phoenix_chronicles.chapter." + chapter.toLowerCase() + ".name";
        String legacyKey = "phoenix_chronicles.category." + chapter.toLowerCase() + ".name";

        String override = ClientTextOverrides.get(key);
        if (override == null) override = ClientTextOverrides.get(legacyKey);
        if (override != null) return override;
        if (net.minecraft.client.resources.language.I18n.exists(key)) {
            return net.minecraft.network.chat.Component.translatable(key).getString();
        }

        if (net.minecraft.client.resources.language.I18n.exists(legacyKey)) {
            return net.minecraft.network.chat.Component.translatable(legacyKey).getString();
        }
        String raw = get(chapter).getDisplayName();
        if (!raw.isEmpty()) return raw;

        try {
            Path p = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles")
                    .resolve("quests").resolve(chapter.toLowerCase())
                    .resolve(chapter.toLowerCase() + ".json");
            if (Files.exists(p)) {
                com.google.gson.JsonObject o = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8),
                        com.google.gson.JsonObject.class);
                if (o != null && o.has("name")) {
                    String fileName = o.get("name").getAsString();
                    if (!fileName.isBlank()) return fileName;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void put(String chapter, ChapterConfig cfg) {
        if (!loaded) load();
        CACHE.put(chapter, cfg);
    }

    public static void invalidate() {
        loaded = false;
        CACHE.clear();
    }

    public static void remove(String chapter) {
        if (!loaded) load();
        CACHE.remove(chapter);
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
            System.err.println("[Phoenix Chronicles] Failed to load chapters.json: " + e.getMessage());
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, ChapterConfig> e : CACHE.entrySet())
            root.add(e.getKey(), e.getValue().toJson());
        try {
            Path p = configPath();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Phoenix Chronicles] Failed to save chapters.json: " + e.getMessage());
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles").resolve("chapters.json");
    }
}
