package net.phoenixvine.chronicles.codec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class QuestChroniclesSettings {

    public enum TextScale {
        SMALL,
        NORMAL,
        LARGE
    }

    public enum Theme {
        DARK,
        LIGHT
    }

    public enum Density {
        COMPACT,
        SPACIOUS
    }

    public enum HUDPosition {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    }

    public enum LineStyle {
        SPLINE,   // cubic S-curve bezier (default)
        STRAIGHT  // vertex straight lines
    }

    public enum LineVisualStyle {
        THIN,    // 1px hairline
        NORMAL,  // 3px core with soft edge (default)
        BOLD,    // 5px core with soft edge
        THICK,   // 7px core with halo
        WIDE,    // 9px core with strong halo
        GLOW     // 3px core with luminous halo
    }

    /**
     * Where the quest-book button sits in the vanilla inventory screen. LEFT/RIGHT attach a
     * tab to the outside edge of the panel (FTB Quests' side-tab convention); TOP_LEFT instead
     * sits as a small button inside the panel's top-left corner (FTB Quests' OTHER convention -
     * a small in-panel icon rather than a side tab).
     */
    public enum InvButtonPos {
        LEFT,
        RIGHT,
        TOP_LEFT
    }

    public enum LineAnimSpeed {

        SLOWEST(120L),
        SLOW(70L),
        NORMAL(35L),
        FAST(16L),
        VERY_FAST(7L);

        public final long divisor;

        LineAnimSpeed(long d) {
            this.divisor = d;
        }
    }

    private TextScale textScale = TextScale.NORMAL;
    private Theme theme = Theme.DARK;
    private Density density = Density.SPACIOUS;
    private boolean showDevInfoByDefault = false;
    private HUDPosition hudPosition = HUDPosition.TOP_LEFT;
    private float hudOpacity = 1.0f;
    private boolean showHUDTitle = true;
    private boolean showHUDProgress = true;
    private boolean showHUDRewards = true;
    private LineStyle lineStyle = LineStyle.SPLINE;
    private LineVisualStyle lineVisualStyle = LineVisualStyle.NORMAL;
    private LineAnimSpeed lineAnimSpeed = LineAnimSpeed.NORMAL;
    private boolean showInventoryButton = true;
    private InvButtonPos invButtonPos = InvButtonPos.LEFT;
    private boolean showLineArrows = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SETTINGS_FILE = Paths.get("config", "phoenix_chronicles_settings.json");

    private static QuestChroniclesSettings INSTANCE = null;

    public static QuestChroniclesSettings get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    public static QuestChroniclesSettings load() {
        QuestChroniclesSettings result;
        try {
            if (Files.exists(SETTINGS_FILE)) {
                String json = Files.readString(SETTINGS_FILE);
                result = GSON.fromJson(json, QuestChroniclesSettings.class);
                if (result == null) result = new QuestChroniclesSettings();
            } else {
                result = new QuestChroniclesSettings();
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = new QuestChroniclesSettings();
        }
        INSTANCE = result;
        return result;
    }

    public void save() {
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            String json = GSON.toJson(this);
            Files.writeString(SETTINGS_FILE, json);
            INSTANCE = this;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Getters
    public TextScale getTextScale() {
        return textScale;
    }

    public Theme getTheme() {
        return theme;
    }

    public Density getDensity() {
        return density;
    }

    public boolean isShowDevInfoByDefault() {
        return showDevInfoByDefault;
    }

    public HUDPosition getHudPosition() {
        return hudPosition;
    }

    public float getHudOpacity() {
        return hudOpacity;
    }

    public boolean isShowHUDTitle() {
        return showHUDTitle;
    }

    public boolean isShowHUDProgress() {
        return showHUDProgress;
    }

    public boolean isShowHUDRewards() {
        return showHUDRewards;
    }

    // Setters
    public void setTextScale(TextScale scale) {
        this.textScale = scale;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
    }

    public void setDensity(Density density) {
        this.density = density;
    }

    public void setShowDevInfoByDefault(boolean show) {
        this.showDevInfoByDefault = show;
    }

    public void setHudPosition(HUDPosition pos) {
        this.hudPosition = pos;
    }

    public void setHudOpacity(float opacity) {
        this.hudOpacity = Math.max(0.3f, Math.min(1.0f, opacity));
    }

    public void setShowHUDTitle(boolean show) {
        this.showHUDTitle = show;
    }

    public void setShowHUDProgress(boolean show) {
        this.showHUDProgress = show;
    }

    public void setShowHUDRewards(boolean show) {
        this.showHUDRewards = show;
    }

    public LineStyle getLineStyle() {
        return lineStyle != null ? lineStyle : LineStyle.SPLINE;
    }

    public void setLineStyle(LineStyle style) {
        this.lineStyle = style;
    }

    public boolean isSplineLines() {
        return getLineStyle() == LineStyle.SPLINE;
    }

    public LineVisualStyle getLineVisualStyle() {
        return lineVisualStyle != null ? lineVisualStyle : LineVisualStyle.NORMAL;
    }

    public void setLineVisualStyle(LineVisualStyle s) {
        this.lineVisualStyle = s;
    }

    public LineAnimSpeed getLineAnimSpeed() {
        return lineAnimSpeed != null ? lineAnimSpeed : LineAnimSpeed.NORMAL;
    }

    public void setLineAnimSpeed(LineAnimSpeed s) {
        this.lineAnimSpeed = s;
    }

    public boolean isShowInventoryButton() {
        return showInventoryButton;
    }

    public void setShowInventoryButton(boolean show) {
        this.showInventoryButton = show;
    }

    public InvButtonPos getInvButtonPos() {
        return invButtonPos != null ? invButtonPos : InvButtonPos.LEFT;
    }

    public void setInvButtonPos(InvButtonPos pos) {
        this.invButtonPos = pos != null ? pos : InvButtonPos.LEFT;
    }

    public boolean isShowLineArrows() {
        return showLineArrows;
    }

    public void setShowLineArrows(boolean show) {
        this.showLineArrows = show;
    }

    public float getTextScaleMultiplier() {
        return switch (textScale) {
            case SMALL -> 0.85f;
            case NORMAL -> 1.0f;
            case LARGE -> 1.2f;
        };
    }

    public int getMarginMultiplier() {
        return density == Density.COMPACT ? 8 : 12;
    }
}
