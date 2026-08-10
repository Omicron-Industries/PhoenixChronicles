package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.phoenixvine.chronicles.client.render.*;

class TutorialOverlay implements OverlayComponent {

    private final TutorialOverlayRenderer renderer = new TutorialOverlayRenderer();

    @Override
    public boolean isVisible(ScreenContext ctx) {
        return !ctx.isRenderingAsBackdrop();
    }

    @Override
    public void render(ScreenContext ctx, GuiGraphics g, int mouseX, int mouseY, int contentLeft, int contentRight) {
        renderer.render(g, mouseX, mouseY, ctx.font(),
                new TutorialOverlayRenderer.Layout(ctx.width(), ctx.height(), ctx.sidebarW(),
                        ChronicleOverviewScreen.HEADER_H, ChronicleOverviewScreen.TOOLBAR_Y,
                        ChronicleOverviewScreen.TOOLBAR_H),
                new TutorialOverlayRenderer.Colors(ctx.colorSelectAccent(), ctx.colorBorder(), ctx.colorTextFaint(),
                        ctx.colorTextDim(), ctx.colorText(), ctx.colorNodeBorderDone()),
                ctx.nodeScreenPos(), ctx::scaledNodeSize, ctx::scaledNodeSize, ctx::getState);
    }

    @Override
    public boolean mouseClicked(ScreenContext ctx, double mouseX, double mouseY, int button) {
        return renderer.handleClick(mouseX, mouseY, ctx::getState);
    }
}
