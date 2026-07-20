package net.phoenixvine.chronicles.registry;

import net.minecraft.util.Mth;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ChroniclesTheme {

    public static class ThemeColor {

        public String hex;
        private transient Integer cached = null;
        /**
         * Per-field animation phase offset (ms), so e.g. bg/accent/border all cycling RAINBOW
         * don't all show the exact same hue at the exact same moment - same trick Phantasia's
         * PhantasiaTheme uses for its own animated keyword colors. Assigned by the owning
         * ChroniclesTheme constructor, not persisted.
         */
        private transient long animOffset = 0L;

        public ThemeColor() {
            this.hex = "FFFFFFFF";
        }

        public ThemeColor(String hex) {
            this.hex = hex;
        }

        void setAnimOffset(long offset) {
            this.animOffset = offset;
        }

        public int getColor() {
            if (hex == null) return 0xFFFFFFFF;
            String clean = hex.trim().toUpperCase(Locale.ROOT);
            if (clean.startsWith("#")) clean = clean.substring(1);

            // Animated keyword colors - evaluated fresh every call (never cached) since the whole
            // point is that they change over time. Reduce Motion pins the clock at 0 so they still
            // resolve to a real, stable color instead of flickering.
            switch (clean) {
                case "RAINBOW":
                    return animatedHue(6000L, 0.75f, 0.90f, 0f, 1f);
                case "PASTEL_RAINBOW":
                    return animatedHue(12000L, 0.38f, 0.95f, 0f, 1f);
                case "MAGMA":
                    return animatedWave(5000.0, 0.95f, 0.50f, 0.45f, 0f, 0.10f);
                case "AURORA":
                    return animatedWave(3200.0, 0.90f, 0.85f, 0f, 0.32f, 0.18f);
                case "GALAXY":
                    return animatedWave(4000.0, 0.85f, 0.90f, 0f, 0.75f, 0.14f);
            }

            if (cached == null) {
                try {
                    cached = (int) Long.parseUnsignedLong(clean, 16);
                } catch (Exception e) {
                    cached = 0xFFFFFFFF;
                }
            }
            return cached;
        }

        private static long animClock() {
            return QuestChroniclesSettings.get().isReduceMotion() ? 0L : System.currentTimeMillis();
        }

        /** A hue that cycles linearly through the full color wheel every {@code periodMs}. */
        private int animatedHue(long periodMs, float sat, float val, float hueMin, float hueMax) {
            float hue = (float) ((animClock() + animOffset) % periodMs) / periodMs;
            return hsvToRgb(hueMin + hue * (hueMax - hueMin), sat, val);
        }

        /** A hue/value that oscillates back and forth (sine wave) rather than cycling linearly. */
        private int animatedWave(double periodMs, float sat, float valBase, float valAmp, float hueBase,
                                 float hueAmp) {
            double t = (animClock() + animOffset) / periodMs;
            float wave = (float) (Math.sin(t) * 0.5 + 0.5);
            return hsvToRgb(hueBase + wave * hueAmp, sat, valBase + wave * valAmp);
        }

        private static int hsvToRgb(float h, float s, float v) {
            int i = (int) (h * 6);
            float f = h * 6 - i;
            float p = v * (1f - s);
            float q = v * (1f - s * f);
            float t = v * (1f - s * (1f - f));
            float r, g, b;
            switch (((i % 6) + 6) % 6) {
                case 0 -> {
                    r = v;
                    g = t;
                    b = p;
                }
                case 1 -> {
                    r = q;
                    g = v;
                    b = p;
                }
                case 2 -> {
                    r = p;
                    g = v;
                    b = t;
                }
                case 3 -> {
                    r = p;
                    g = q;
                    b = v;
                }
                case 4 -> {
                    r = t;
                    g = p;
                    b = v;
                }
                default -> {
                    r = v;
                    g = p;
                    b = q;
                }
            }
            return (0xFF << 24) | (Mth.clamp((int) (r * 255), 0, 255) << 16) |
                    (Mth.clamp((int) (g * 255), 0, 255) << 8) | Mth.clamp((int) (b * 255), 0, 255);
        }

        public void set(String newHex) {
            this.hex = newHex;
            this.cached = null;
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────
    public ThemeColor bg, panel, header, border, accent, text, textDim, textFaint, done, activeColor, locked;

    // ── Registry ──────────────────────────────────────────────────────────────
    public static final Map<String, ChroniclesTheme> REGISTRY = new LinkedHashMap<>();
    private static ChroniclesTheme active = null;
    private static String activeName = "DARK";

    private static final Set<String> BUILTINS = Set.of("DARK", "LIGHT", "CRIMSON", "OCEAN", "PHANTOM", "EMBER",
            "RAINBOW", "MAGMA");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT)
            .create();
    private static final Path THEMES_FILE = Paths.get("config", "phoenix_chronicles_themes.json");

    // ── Constructors ──────────────────────────────────────────────────────────

    public ChroniclesTheme() {}

    public ChroniclesTheme(String bg, String panel, String header, String border, String accent,
                           String text, String textDim, String textFaint, String done, String activeCol,
                           String locked) {
        this.bg = new ThemeColor(bg);
        this.panel = new ThemeColor(panel);
        this.header = new ThemeColor(header);
        this.border = new ThemeColor(border);
        this.accent = new ThemeColor(accent);
        this.text = new ThemeColor(text);
        this.textDim = new ThemeColor(textDim);
        this.textFaint = new ThemeColor(textFaint);
        this.done = new ThemeColor(done);
        this.activeColor = new ThemeColor(activeCol);
        this.locked = new ThemeColor(locked);
        assignAnimOffsets();
    }

    /**
     * Staggers each field's animated-keyword phase (see {@link ThemeColor#getColor()}) so a theme
     * with several RAINBOW/MAGMA fields doesn't have them all showing the identical hue at the
     * identical instant. Must also run after Gson deserialization, since Gson populates fields via
     * reflection and never calls this constructor - see {@link #loadThemes()}.
     */
    private void assignAnimOffsets() {
        if (bg != null) bg.setAnimOffset(0);
        if (panel != null) panel.setAnimOffset(500);
        if (header != null) header.setAnimOffset(1000);
        if (border != null) border.setAnimOffset(1500);
        if (accent != null) accent.setAnimOffset(2000);
        if (text != null) text.setAnimOffset(2500);
        if (textDim != null) textDim.setAnimOffset(3000);
        if (textFaint != null) textFaint.setAnimOffset(3500);
        if (done != null) done.setAnimOffset(4000);
        if (activeColor != null) activeColor.setAnimOffset(4500);
        if (locked != null) locked.setAnimOffset(5000);
    }

    /** Returns a deep copy so edits in the theme editor never corrupt the live registry entry. */
    public ChroniclesTheme copy() {
        return new ChroniclesTheme(
                bg.hex, panel.hex, header.hex, border.hex, accent.hex,
                text.hex, textDim.hex, textFaint.hex, done.hex, activeColor.hex, locked.hex);
    }

    // ── Static API ────────────────────────────────────────────────────────────

    public static ChroniclesTheme current() {
        if (REGISTRY.isEmpty()) loadThemes();
        return active != null ? active : REGISTRY.get("DARK");
    }

    public static String getActiveName() {
        if (REGISTRY.isEmpty()) loadThemes();
        return activeName;
    }

    public static boolean isBuiltin(String name) {
        return BUILTINS.contains(name.toUpperCase(Locale.ROOT));
    }

    public static void setCurrent(String name) {
        ChroniclesTheme t = REGISTRY.get(name);
        if (t == null) t = REGISTRY.get(name.toUpperCase(Locale.ROOT));
        if (t != null) {
            active = t;
            activeName = name;
            // This used to be purely in-memory - none of the three callers (theme editor's
            // picker, its "reset to DARK" button, the terminal's `theme` command) ever followed
            // up with a save, so the selection lived only for the current session and silently
            // reverted to whatever was last actually written to disk (or DARK, if nothing ever
            // had been) on every relaunch/rejoin. Persisting here means every caller gets this
            // for free instead of needing to remember it individually.
            saveAll();
        }
    }

    /** Adds or replaces a custom theme, then persists everything to disk. */
    public static void saveCustomTheme(String name, ChroniclesTheme theme) {
        REGISTRY.put(name, theme);
        saveAll();
    }

    /** Deletes a custom theme (no-op for builtins). */
    public static boolean deleteCustom(String name) {
        if (isBuiltin(name)) return false;
        REGISTRY.remove(name);
        if (name.equals(activeName)) {
            activeName = "DARK";
            active = REGISTRY.get("DARK");
        }
        saveAll();
        return true;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static class ThemeSave {

        String active = "DARK";
        Map<String, ChroniclesTheme> custom = new LinkedHashMap<>();
    }

    public static void saveAll() {
        try {
            Files.createDirectories(THEMES_FILE.getParent());
            ThemeSave save = new ThemeSave();
            save.active = activeName;
            for (Map.Entry<String, ChroniclesTheme> e : REGISTRY.entrySet()) {
                if (!isBuiltin(e.getKey())) save.custom.put(e.getKey(), e.getValue());
            }
            Files.writeString(THEMES_FILE, GSON.toJson(save));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadThemes() {
        REGISTRY.clear();

        // ── Prebuilt themes ───────────────────────────────────────────────────

        REGISTRY.put("DARK", new ChroniclesTheme(
                "FF0B0B0F", "FF14141A", "FF0C0C10", "FF353548", "FF00AA55",
                "FFD8D8E4", "FF7A7A8A", "FF404050", "FF44CC88", "FFFFBB33", "FF606070"));

        REGISTRY.put("LIGHT", new ChroniclesTheme(
                "FFF0F0F4", "FFE4E4EC", "FFD8D8E0", "FFA0A0B0", "FF0088CC",
                "FF1A1A2A", "FF555565", "FF888898", "FF22AA55", "FFCC6600", "FF808090"));

        REGISTRY.put("CRIMSON", new ChroniclesTheme(
                "FF0F0808", "FF1A0C0C", "FF0D0606", "FF3A1818", "FFCC2233",
                "FFE4D0D0", "FF8A6A6A", "FF503838", "FF44CC66", "FFFFAA33", "FF705555"));

        REGISTRY.put("OCEAN", new ChroniclesTheme(
                "FF080C12", "FF0E1520", "FF080C14", "FF1E2A3C", "FF0099CC",
                "FFCCE0F0", "FF6080A0", "FF304060", "FF33CC88", "FFFFBB33", "FF506888"));

        REGISTRY.put("PHANTOM", new ChroniclesTheme(
                "FF0A080F", "FF130E1C", "FF0A0810", "FF2E2040", "FF8833CC",
                "FFD8CCF0", "FF7060A0", "FF3C2C5C", "FF44BB88", "FFFFAA44", "FF604880"));

        REGISTRY.put("EMBER", new ChroniclesTheme(
                "FF100A06", "FF1C120A", "FF100A04", "FF382210", "FFCC6600",
                "FFF0E0CC", "FFA0785A", "FF604830", "FF44CC77", "FFFFCC22", "FF806040"));

        // ── Animated presets ───────────────────────────────────────────────────
        // "RAINBOW"/"MAGMA" etc are keyword hex values (see ThemeColor#getColor()) rather than
        // static hex - any field, in any theme (including custom ones typed into the Theme
        // Editor's hex boxes), can use them. These two are just built-in presets that lean on it.
        REGISTRY.put("RAINBOW", new ChroniclesTheme(
                "FF09090C", "FF111116", "FF0A0A0E", "RAINBOW", "RAINBOW",
                "FFEEEEEE", "FF888888", "FF505050", "FF44CC88", "RAINBOW", "FF606070"));

        REGISTRY.put("MAGMA", new ChroniclesTheme(
                "FF120806", "FF1C0E0A", "FF100604", "MAGMA", "MAGMA",
                "FFF0E0D0", "FFA07860", "FF604838", "FF44CC77", "MAGMA", "FF705040"));

        // ── Load custom themes from disk ──────────────────────────────────────
        String loadedActive = "DARK";
        try {
            if (Files.exists(THEMES_FILE)) {
                String json = Files.readString(THEMES_FILE);
                Type type = new TypeToken<ThemeSave>() {}.getType();
                ThemeSave save = GSON.fromJson(json, type);
                if (save != null) {
                    if (save.custom != null) {
                        // Gson populates fields via reflection, bypassing the constructor entirely
                        // - without this, any custom theme saved with an animated keyword color
                        // would silently animate at animOffset=0 for every field instead of the
                        // staggered phase assignAnimOffsets() normally assigns.
                        for (ChroniclesTheme theme : save.custom.values()) theme.assignAnimOffsets();
                        REGISTRY.putAll(save.custom);
                    }
                    if (save.active != null) loadedActive = save.active;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Apply active — fall back to DARK if the saved name no longer exists
        activeName = loadedActive;
        active = REGISTRY.getOrDefault(activeName, REGISTRY.get("DARK"));
    }

    /** @deprecated Use {@link #saveAll()} for custom themes. */
    @Deprecated
    public static void saveCustom() {
        saveAll();
    }
}
