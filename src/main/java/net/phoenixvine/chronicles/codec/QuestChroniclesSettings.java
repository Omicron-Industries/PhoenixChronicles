package net.phoenixvine.chronicles.codec;

import net.minecraft.client.Minecraft;

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

    private TextScale textScale;
    private Theme theme;
    private Density density;
    private boolean showDevInfoByDefault = false;
    private boolean showFlagDisabledChapters = false;
    private boolean showFlagDisabledQuests = true;
    private boolean generateMdSidecarFiles = false;
    private HUDPosition hudPosition;
    private Float hudOpacity;
    private Boolean showHUDTitle;
    private Boolean showHUDProgress;
    private Boolean showHUDRewards;
    private LineStyle lineStyle;
    private LineVisualStyle lineVisualStyle;
    private LineAnimSpeed lineAnimSpeed;
    private Boolean showLineArrows;

    private boolean devModeDisabled = true;

    private String lastChapter = "";

    private HUDPosition toastPosition;

    private String questbookName;

    private String questbookIcon;

    private ToastStyle toastStyle;

    private Boolean playToastSounds;

    private Boolean phantasiaAutoSpin;

    private Boolean hideCompletedByDefault;

    private Integer defaultGridSnap;

    private Boolean showToasts;

    private Boolean reduceMotion;

    private Boolean returnToQuestbookFromRecipeViewer;

    private Boolean showProgressArc;

    private SidebarBehavior sidebarBehavior;

    private int taskInspectorW = -1;
    private int taskRewardW = -1;

    private NodeMoveMode nodeMoveMode;

    private Boolean middleClickPickupPlace;

    private boolean alwaysProfilerEnabled = false;

    private Boolean cascadeHiddenQuests;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path configRoot() {
        Minecraft mc = Minecraft.getInstance();
        Path gameDir = mc != null ? mc.gameDirectory.toPath() : Paths.get("");
        return gameDir.resolve("config");
    }

    private static Path settingsFile() {
        return configRoot().resolve("phoenix_chronicles").resolve("settings.json");
    }

    private static Path legacySettingsFile() {
        return configRoot().resolve("phoenix_chronicles_settings.json");
    }

    private static final class PackDefaults {

        TextScale textScale;
        Theme theme;
        Density density;
        HUDPosition hudPosition;
        Float hudOpacity;
        Boolean showHUDTitle;
        Boolean showHUDProgress;
        Boolean showHUDRewards;
        LineStyle lineStyle;
        LineVisualStyle lineVisualStyle;
        LineAnimSpeed lineAnimSpeed;
        Boolean showLineArrows;
        HUDPosition toastPosition;
        String questbookName;
        String questbookIcon;
        ToastStyle toastStyle;
        Boolean playToastSounds;
        Boolean phantasiaAutoSpin;
        Boolean hideCompletedByDefault;
        Integer defaultGridSnap;
        Boolean showToasts;
        Boolean reduceMotion;
        Boolean returnToQuestbookFromRecipeViewer;
        Boolean showProgressArc;
        SidebarBehavior sidebarBehavior;
        NodeMoveMode nodeMoveMode;
        Boolean middleClickPickupPlace;
        Boolean cascadeHiddenQuests;
    }

    private static Path packDefaultsFile() {
        return configRoot().resolve("phoenix_chronicles").resolve("pack_defaults.json");
    }

    private static Path legacyPackDefaultsFile() {
        return configRoot().resolve("phoenix_chronicles_pack_defaults.json");
    }

    private static PackDefaults packDefaults = null;

    private static PackDefaults packDefaults() {
        if (packDefaults == null) {
            packDefaults = new PackDefaults();
            try {
                Path file = migrateLegacyFile(packDefaultsFile(), legacyPackDefaultsFile());
                if (Files.exists(file)) {
                    PackDefaults loaded = GSON.fromJson(Files.readString(file), PackDefaults.class);
                    if (loaded != null) packDefaults = loaded;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return packDefaults;
    }

    private static Path migrateLegacyFile(Path target, Path legacy) throws java.io.IOException {
        if (!Files.exists(target) && Files.exists(legacy)) {
            Files.createDirectories(target.getParent());
            Files.move(legacy, target);
        }
        return target;
    }

    private static <T> T resolve(T explicit, T packDefault, T hardcoded) {
        if (explicit != null) return explicit;
        if (packDefault != null) return packDefault;
        return hardcoded;
    }

    private static QuestChroniclesSettings INSTANCE = null;

    public static QuestChroniclesSettings get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    public static QuestChroniclesSettings load() {
        QuestChroniclesSettings result;
        try {
            Path file = migrateLegacyFile(settingsFile(), legacySettingsFile());
            if (Files.exists(file)) {
                String json = Files.readString(file);
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
        packDefaults = null;
        net.phoenixvine.wiki.theme.PhoenixTheme.setReduceMotion(result.isReduceMotion());
        return result;
    }

    public void save() {
        try {
            Path file = settingsFile();
            Files.createDirectories(file.getParent());
            String json = GSON.toJson(this);
            Files.writeString(file, json);
            INSTANCE = this;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public TextScale getTextScale() {
        return resolve(textScale, packDefaults().textScale, TextScale.NORMAL);
    }

    public Theme getTheme() {
        return resolve(theme, packDefaults().theme, Theme.DARK);
    }

    public Density getDensity() {
        return resolve(density, packDefaults().density, Density.SPACIOUS);
    }

    public boolean isShowDevInfoByDefault() {
        return showDevInfoByDefault;
    }

    public boolean isShowFlagDisabledChapters() {
        return showFlagDisabledChapters;
    }

    public boolean isShowFlagDisabledQuests() {
        return showFlagDisabledQuests;
    }

    public boolean isGenerateMdSidecarFiles() {
        return generateMdSidecarFiles;
    }

    public HUDPosition getHudPosition() {
        return resolve(hudPosition, packDefaults().hudPosition, HUDPosition.TOP_LEFT);
    }

    public float getHudOpacity() {
        return resolve(hudOpacity, packDefaults().hudOpacity, 1.0f);
    }

    public boolean isShowHUDTitle() {
        return resolve(showHUDTitle, packDefaults().showHUDTitle, true);
    }

    public boolean isShowHUDProgress() {
        return resolve(showHUDProgress, packDefaults().showHUDProgress, true);
    }

    public boolean isShowHUDRewards() {
        return resolve(showHUDRewards, packDefaults().showHUDRewards, true);
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

    public void setShowFlagDisabledChapters(boolean show) {
        this.showFlagDisabledChapters = show;
    }

    public void setGenerateMdSidecarFiles(boolean generate) {
        this.generateMdSidecarFiles = generate;
    }

    public void setShowFlagDisabledQuests(boolean show) {
        this.showFlagDisabledQuests = show;
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
        return resolve(lineStyle, packDefaults().lineStyle, LineStyle.SPLINE);
    }

    public void setLineStyle(LineStyle style) {
        this.lineStyle = style;
    }

    public boolean isSplineLines() {
        return getLineStyle() == LineStyle.SPLINE;
    }

    public LineVisualStyle getLineVisualStyle() {
        return resolve(lineVisualStyle, packDefaults().lineVisualStyle, LineVisualStyle.NORMAL);
    }

    public void setLineVisualStyle(LineVisualStyle s) {
        this.lineVisualStyle = s;
    }

    public LineAnimSpeed getLineAnimSpeed() {
        return resolve(lineAnimSpeed, packDefaults().lineAnimSpeed, LineAnimSpeed.NORMAL);
    }

    public void setLineAnimSpeed(LineAnimSpeed s) {
        this.lineAnimSpeed = s;
    }

    public boolean isShowLineArrows() {
        return resolve(showLineArrows, packDefaults().showLineArrows, true);
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
        return resolve(showProgressArc, packDefaults().showProgressArc, false);
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

    public boolean isCascadeHiddenQuests() {
        return resolve(cascadeHiddenQuests, packDefaults().cascadeHiddenQuests, false);
    }

    public void setCascadeHiddenQuests(boolean cascade) {
        this.cascadeHiddenQuests = cascade;
    }

    public HUDPosition getToastPosition() {
        return resolve(toastPosition, packDefaults().toastPosition, HUDPosition.TOP_RIGHT);
    }

    public void setToastPosition(HUDPosition pos) {
        this.toastPosition = pos != null ? pos : HUDPosition.TOP_RIGHT;
    }

    public ToastStyle getToastStyle() {
        return resolve(toastStyle, packDefaults().toastStyle, ToastStyle.COMPACT);
    }

    public void setToastStyle(ToastStyle style) {
        this.toastStyle = style != null ? style : ToastStyle.COMPACT;
    }

    public String getQuestbookName() {
        String v = resolve(questbookName, packDefaults().questbookName, null);
        return (v == null || v.isEmpty()) ? "Quest Book" : v;
    }

    public void setQuestbookName(String name) {
        this.questbookName = name != null ? name.trim() : "";
    }

    public String getQuestbookIcon() {
        String v = resolve(questbookIcon, packDefaults().questbookIcon, "");
        return v != null ? v : "";
    }

    public void setQuestbookIcon(String icon) {
        this.questbookIcon = icon != null ? icon : "";
    }

    public net.minecraft.world.item.Item getQuestbookIconItem() {
        String icon = getQuestbookIcon();
        if (!icon.isEmpty()) {
            try {
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(net.minecraft.resources.ResourceLocation.parse(icon));
                if (item != null && item != net.minecraft.world.item.Items.AIR) return item;
            } catch (Exception ignored) {}
        }
        return net.minecraft.world.item.Items.WRITTEN_BOOK;
    }

    public boolean isPlayToastSounds() {
        return resolve(playToastSounds, packDefaults().playToastSounds, true);
    }

    public void setPlayToastSounds(boolean play) {
        this.playToastSounds = play;
    }

    public boolean isPhantasiaAutoSpin() {
        return resolve(phantasiaAutoSpin, packDefaults().phantasiaAutoSpin, true);
    }

    public void setPhantasiaAutoSpin(boolean spin) {
        this.phantasiaAutoSpin = spin;
    }

    public NodeMoveMode getNodeMoveMode() {
        return resolve(nodeMoveMode, packDefaults().nodeMoveMode, NodeMoveMode.DRAG);
    }

    public void setNodeMoveMode(NodeMoveMode mode) {
        this.nodeMoveMode = mode != null ? mode : NodeMoveMode.DRAG;
    }

    public boolean isMiddleClickPickupPlace() {
        return resolve(middleClickPickupPlace, packDefaults().middleClickPickupPlace, false);
    }

    public void setMiddleClickPickupPlace(boolean enabled) {
        this.middleClickPickupPlace = enabled;
    }

    public boolean isHideCompletedByDefault() {
        return resolve(hideCompletedByDefault, packDefaults().hideCompletedByDefault, false);
    }

    public void setHideCompletedByDefault(boolean hide) {
        this.hideCompletedByDefault = hide;
    }

    public boolean isReturnToQuestbookFromRecipeViewer() {
        return resolve(returnToQuestbookFromRecipeViewer, packDefaults().returnToQuestbookFromRecipeViewer, true);
    }

    public void setReturnToQuestbookFromRecipeViewer(boolean b) {
        this.returnToQuestbookFromRecipeViewer = b;
    }

    public SidebarBehavior getSidebarBehavior() {
        return resolve(sidebarBehavior, packDefaults().sidebarBehavior, SidebarBehavior.COLLAPSIBLE);
    }

    public void setSidebarBehavior(SidebarBehavior b) {
        this.sidebarBehavior = b != null ? b : SidebarBehavior.COLLAPSIBLE;
    }

    public int getDefaultGridSnap() {
        int v = resolve(defaultGridSnap, packDefaults().defaultGridSnap, 8);
        return v <= 0 ? 8 : v;
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
        return resolve(showToasts, packDefaults().showToasts, true);
    }

    public void setShowToasts(boolean show) {
        this.showToasts = show;
    }

    public boolean isReduceMotion() {
        return resolve(reduceMotion, packDefaults().reduceMotion, false);
    }

    public void setReduceMotion(boolean reduce) {
        this.reduceMotion = reduce;
        net.phoenixvine.wiki.theme.PhoenixTheme.setReduceMotion(reduce);
    }

    public float getTextScaleMultiplier() {
        return switch (getTextScale()) {
            case SMALL -> 0.85f;
            case NORMAL -> 1.0f;
            case LARGE -> 1.2f;
        };
    }

    public int getMarginMultiplier() {
        return getDensity() == Density.COMPACT ? 8 : 12;
    }
}
