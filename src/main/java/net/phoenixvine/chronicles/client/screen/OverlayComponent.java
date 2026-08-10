package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;

public interface OverlayComponent {

    default boolean isVisible(ScreenContext ctx) {
        return true;
    }

    default void render(ScreenContext ctx, GuiGraphics g, int mouseX, int mouseY, int contentLeft,
                        int contentRight) {}

    default boolean mouseClicked(ScreenContext ctx, double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean keyPressed(ScreenContext ctx, int keyCode, int scanCode, int modifiers) {
        return false;
    }
}
