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
        SPLINE,
        STRAIGHT
    }

    public enum SidebarBehavior {
        COLLAPSIBLE,
        HOVER_TO_EXPAND
    }

    public enum LineVisualStyle {
        THIN,
        NORMAL,
        BOLD,
        THICK,
        WIDE,
        GLOW
    }

    public enum InvButtonPos {
        LEFT,
        RIGHT,
        TOP_LEFT
    }

    public enum ToastStyle {
        COMPACT,
        ABOVE_HOTBAR,
        BIG_CENTER
    }

    public enum NodeMoveMode {
        DRAG,
        PICKUP_PLACE
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

    private boolean devModeDisabled = true;

    private String lastChapter = "";

    private HUDPosition toastPosition = HUDPosition.TOP_RIGHT;

    private String questbookName = "";

    private String questbookIcon = "";

    private ToastStyle toastStyle = ToastStyle.COMPACT;

    private boolean playToastSounds = true;

    private boolean phantasiaAutoSpin = true;

    private boolean hideCompletedByDefault = false;

    private int defaultGridSnap = 8;

    private boolean showToasts = true;

    private boolean reduceMotion = false;

    private boolean returnToQuestbookFromRecipeViewer = true;

    private boolean showProgressArc = false;

    private SidebarBehavior sidebarBehavior = SidebarBehavior.COLLAPSIBLE;

    private int taskInspectorW = -1;
    private int taskRewardW = -1;

    private NodeMoveMode nodeMoveMode = NodeMoveMode.DRAG;

    private boolean middleClickPickupPlace = false;

    private boolean alwaysProfilerEnabled = false;

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

    public String getLastChapter() {
        return lastChapter == null ? "" : lastChapter;
    }

    public void setLastChapter(String chapter) {
        this.lastChapter = chapter == null ? "" : chapter;
    }

    public void setShowLineArrows(boolean show) {
        this.showLineArrows = show;
    }

    public boolean isShowProgressArc() {
        return showProgressArc;
    }

    public void setShowProgressArc(boolean show) {
        this.showProgressArc = show;
    }

    public boolean isAlwaysProfilerEnabled() {
        return alwaysProfilerEnabled;
    }

    public void setAlwaysProfilerEnabled(boolean enabled) {
        this.alwaysProfilerEnabled = enabled;
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

    public NodeMoveMode getNodeMoveMode() {
        return nodeMoveMode == null ? NodeMoveMode.DRAG : nodeMoveMode;
    }

    public void setNodeMoveMode(NodeMoveMode mode) {
        this.nodeMoveMode = mode != null ? mode : NodeMoveMode.DRAG;
    }

    public boolean isMiddleClickPickupPlace() {
        return middleClickPickupPlace;
    }

    public void setMiddleClickPickupPlace(boolean enabled) {
        this.middleClickPickupPlace = enabled;
    }

    public boolean isHideCompletedByDefault() {
        return hideCompletedByDefault;
    }

    public void setHideCompletedByDefault(boolean hide) {
        this.hideCompletedByDefault = hide;
    }

    public boolean isReturnToQuestbookFromRecipeViewer() {
        return returnToQuestbookFromRecipeViewer;
    }

    public void setReturnToQuestbookFromRecipeViewer(boolean b) {
        this.returnToQuestbookFromRecipeViewer = b;
    }

    public SidebarBehavior getSidebarBehavior() {
        return sidebarBehavior != null ? sidebarBehavior : SidebarBehavior.COLLAPSIBLE;
    }

    public void setSidebarBehavior(SidebarBehavior b) {
        this.sidebarBehavior = b != null ? b : SidebarBehavior.COLLAPSIBLE;
    }

    public int getDefaultGridSnap() {
        return defaultGridSnap <= 0 ? 8 : defaultGridSnap;
    }

    public void setDefaultGridSnap(int snap) {
        this.defaultGridSnap = Math.max(1, snap);
    }

    public int getTaskInspectorW() {
        return taskInspectorW;
    }

    public void setTaskInspectorW(int w) {
        this.taskInspectorW = w;
    }

    public int getTaskRewardW() {
        return taskRewardW;
    }

    public void setTaskRewardW(int w) {
        this.taskRewardW = w;
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
