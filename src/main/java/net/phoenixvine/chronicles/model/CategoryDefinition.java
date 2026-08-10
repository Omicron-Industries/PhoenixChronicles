package net.phoenixvine.chronicles.model;

import java.util.List;

public record CategoryDefinition(String id, String displayName, List<String> chapters, int color, String icon,
                                 int nameColor) {

    public CategoryDefinition(String id, String displayName, List<String> chapters) {
        this(id, displayName, chapters, 0, "", 0);
    }

    public CategoryDefinition withTheme(int color, String icon, int nameColor) {
        return new CategoryDefinition(id, displayName, chapters, color, icon != null ? icon : "", nameColor);
    }

    public int effectiveNameColor() {
        return nameColor != 0 ? nameColor : color;
    }
}
