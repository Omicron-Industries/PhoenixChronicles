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

    /**
     * Base toast presentation used by any quest that doesn't have its own custom toast design
     * (see QuestToastConfig - "design your own" from the node context menu overrides this
     * per-quest, this is only the fallback).
     */
    public enum ToastStyle {
        COMPACT,       // small corner banner (current default), anchored via toastPosition
        ABOVE_HOTBAR,  // wider banner centered just above the hotbar
        BIG_CENTER     // large, interruptive center-screen text
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
    /**
     * Gates dev mode off by default even for creative/op players who'd otherwise auto-qualify
     * for it - dev tools (edit affordances, validation badges, right-click authoring menus)
     * should be an explicit opt-in via the Settings screen toggle, not always-on the moment
     * you're creative/op, with no way to preview the plain player-facing view without leaving
     * creative or dropping permissions first.
     */
    private boolean devModeDisabled = true;
    /**
     * The category the overview screen was last showing, so reopening the questbook (including
     * after a full world/game restart) returns to where the player left off instead of always
     * landing back on the first chapter. Empty = no preference yet (falls back to the first
     * chapter, same as before this existed).
     */
    private String lastCategory = "";
    /**
     * Where quest-unlocked/completed toast notifications slide in and stack, independent of
     * the pinned-quest HUD widget's own position.
     */
    private HUDPosition toastPosition = HUDPosition.TOP_RIGHT;
    /** Pack-configurable questbook title shown atop the sidebar, "" = falls back to "Quest Book". */
    private String questbookName = "";
    /** Item resource-location string for the questbook title icon, "" = falls back to a written book. */
    private String questbookIcon = "";
    /** Fallback toast presentation for quests without their own custom design (see QuestToastConfig). */
    private ToastStyle toastStyle = ToastStyle.COMPACT;
    /** Whether unlock/complete toasts also play a sound (see S2CSyncPlayerProgressPacket). */
    private boolean playToastSounds = true;
    /** Whether embedded Phantasia 3D previews auto-rotate (see PhantasiaCompat). */
    private boolean phantasiaAutoSpin = true;
    /** Starting state of the canvas's "Hide Completed" toggle each time the questbook opens. */
    private boolean hideCompletedByDefault = false;
    /** Starting canvas grid-snap size each time the questbook opens. */
    private int defaultGridSnap = 8;
    /** Master toggle - when off, unlock/complete toasts never queue at all (see QuestToastManager.push). */
    private boolean showToasts = true;
    /**
     * Freezes/steadies every blinking or pulsing canvas effect (validation warning borders,
     * unclaimed-reward badges, unlock-path highlight, ACTIVE glow, UNLOCKED ready-dot, dependency
     * line "marching ants") for players sensitive to constant motion/flicker.
     */
    private boolean reduceMotion = false;

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

    public boolean isDevModeDisabled() {
        return devModeDisabled;
    }

    public void setDevModeDisabled(boolean disabled) {
        this.devModeDisabled = disabled;
    }

    public String getLastCategory() {
        return lastCategory == null ? "" : lastCategory;
    }

    public void setLastCategory(String category) {
        this.lastCategory = category == null ? "" : category;
    }

    public void setShowLineArrows(boolean show) {
        this.showLineArrows = show;
    }

    public HUDPosition getToastPosition() {
        return toastPosition != null ? toastPosition : HUDPosition.TOP_RIGHT;
    }

    public void setToastPosition(HUDPosition pos) {
        this.toastPosition = pos != null ? pos : HUDPosition.TOP_RIGHT;
    }

    public ToastStyle getToastStyle() {
        return toastStyle != null ? toastStyle : ToastStyle.COMPACT;
    }

    public void setToastStyle(ToastStyle style) {
        this.toastStyle = style != null ? style : ToastStyle.COMPACT;
    }

    public String getQuestbookName() {
        return (questbookName == null || questbookName.isEmpty()) ? "Quest Book" : questbookName;
    }

    public void setQuestbookName(String name) {
        this.questbookName = name != null ? name.trim() : "";
    }

    public String getQuestbookIcon() {
        return questbookIcon != null ? questbookIcon : "";
    }

    public void setQuestbookIcon(String icon) {
        this.questbookIcon = icon != null ? icon : "";
    }

    /** Resolves the configured icon to an Item, falling back to a written book when unset/invalid. */
    public net.minecraft.world.item.Item getQuestbookIconItem() {
        if (questbookIcon != null && !questbookIcon.isEmpty()) {
            try {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(new net.minecraft.resources.ResourceLocation(questbookIcon));
                if (item != null && item != net.minecraft.world.item.Items.AIR) return item;
            } catch (Exception ignored) {}
        }
        return net.minecraft.world.item.Items.WRITTEN_BOOK;
    }

    public boolean isPlayToastSounds() {
        return playToastSounds;
    }

    public void setPlayToastSounds(boolean play) {
        this.playToastSounds = play;
    }

    public boolean isPhantasiaAutoSpin() {
        return phantasiaAutoSpin;
    }

    public void setPhantasiaAutoSpin(boolean spin) {
        this.phantasiaAutoSpin = spin;
    }

    public boolean isHideCompletedByDefault() {
        return hideCompletedByDefault;
    }

    public void setHideCompletedByDefault(boolean hide) {
        this.hideCompletedByDefault = hide;
    }

    public int getDefaultGridSnap() {
        return defaultGridSnap <= 0 ? 8 : defaultGridSnap;
    }

    public void setDefaultGridSnap(int snap) {
        this.defaultGridSnap = Math.max(1, snap);
    }

    public boolean isShowToasts() {
        return showToasts;
    }

    public void setShowToasts(boolean show) {
        this.showToasts = show;
    }

    public boolean isReduceMotion() {
        return reduceMotion;
    }

    public void setReduceMotion(boolean reduce) {
        this.reduceMotion = reduce;
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
