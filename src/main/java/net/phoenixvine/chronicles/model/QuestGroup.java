package net.phoenixvine.chronicles.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A purely cosmetic colored region that visually clusters related quest nodes
 * within a chapter on the canvas — similar to FTB Quests chapter sections.
 * Groups have no effect on quest logic or progression.
 */
public class QuestGroup {

    private String id;
    private String label;
    private int color;        // ARGB fill color
    private int borderColor;  // ARGB border color
    private int x, y, width, height; // logical canvas coords (same space as node customX/customY)
    private String category;

    /** One small icon shown in the group's label bar — an item, a fluid, or an arbitrary texture. */
    public enum IconKind {
        ITEM,
        FLUID,
        TEXTURE
    }

    public static final class GroupIcon {

        public final IconKind kind;
        /** Item/fluid registry id, or a texture resource location string, depending on {@link #kind}. */
        public final String id;

        public GroupIcon(IconKind kind, String id) {
            this.kind = kind;
            this.id = id;
        }
    }

    /** Small icon strip rendered in the group's label bar — addable/removable, any mix of kinds. */
    private final List<GroupIcon> icons = new ArrayList<>();

    /**
     * Optional Phantasia machine id — lets the group editor preview a related build, purely as
     * an editor convenience (does not render live on the canvas; see the group popup editor).
     */
    private String phantasiaMachineId = "";

    private static final int DEFAULT_COLOR = 0x22FFFFFF;
    private static final int DEFAULT_BORDER = 0x44FFFFFF;

    public QuestGroup(String id, String label, String category) {
        this.id = id;
        this.label = label;
        this.category = category;
        this.color = DEFAULT_COLOR;
        this.borderColor = DEFAULT_BORDER;
        this.x = 0;
        this.y = 0;
        this.width = 120;
        this.height = 80;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public int getColor() {
        return color;
    }

    public int getBorderColor() {
        return borderColor;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getCategory() {
        return category;
    }

    public List<GroupIcon> getIcons() {
        return icons;
    }

    public void addIcon(IconKind kind, String id) {
        if (id == null || id.isBlank()) return;
        icons.add(new GroupIcon(kind, id));
    }

    public void removeIcon(int index) {
        if (index >= 0 && index < icons.size()) icons.remove(index);
    }

    public void clearIcons() {
        icons.clear();
    }

    public String getPhantasiaMachineId() {
        return phantasiaMachineId;
    }

    public void setPhantasiaMachineId(String id) {
        this.phantasiaMachineId = id != null ? id : "";
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(String id) {
        this.id = id;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setBorderColor(int borderColor) {
        this.borderColor = borderColor;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }
}
