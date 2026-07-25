package net.phoenixvine.chronicles.model;

import java.util.ArrayList;
import java.util.List;

public class QuestGroup {

    private String id;
    private String label;
    private int color;
    private int borderColor;
    private int x, y, width, height;
    private String chapter;

    public enum IconKind {
        ITEM,
        FLUID,
        TEXTURE
    }

    public static final class GroupIcon {

        public final IconKind kind;

        public final String id;

        public GroupIcon(IconKind kind, String id) {
            this.kind = kind;
            this.id = id;
        }
    }

    private final List<GroupIcon> icons = new ArrayList<>();

    private String phantasiaMachineId = "";

    private static final int DEFAULT_COLOR = 0x22FFFFFF;
    private static final int DEFAULT_BORDER = 0x44FFFFFF;

    public QuestGroup(String id, String label, String chapter) {
        this.id = id;
        this.label = label;
        this.chapter = chapter;
        this.color = DEFAULT_COLOR;
        this.borderColor = DEFAULT_BORDER;
        this.x = 0;
        this.y = 0;
        this.width = 120;
        this.height = 80;
    }

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

    public String getChapter() {
        return chapter;
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

    public void setChapter(String chapter) {
        this.chapter = chapter;
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
