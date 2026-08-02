package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;

import org.jetbrains.annotations.NotNull;

public class NewChapterChoiceScreen extends Screen {

    private static final int PANEL_W = 200;
    private static final int PANEL_H = 90;
    private static final int MARGIN = 12;
    private static final int ACCENT = 0xFF4499CC;

    private final ChronicleOverviewScreen parent;

    private int panelLeft, panelTop;

    public NewChapterChoiceScreen(ChronicleOverviewScreen parent) {
        super(Component.literal("New Chapter"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_W) / 2;
        panelTop = (height - PANEL_H) / 2;
        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;

        int btnY = panelTop + 32;
        addRenderableWidget(Button.builder(Component.literal("§aNew Chapter"), b -> {
            parent.openNewChapterForm();
        }).bounds(fx, btnY, fw, 18).build());

        addRenderableWidget(Button.builder(Component.literal("§bNew Category"), b -> {
            if (minecraft != null) {
                minecraft.setScreen(new NewFolderScreen(parent, id -> parent.rebuild()));
            }
        }).bounds(fx, btnY + 24, fw, 18).build());

        addRenderableWidget(Button.builder(Component.literal("§7Cancel"), b -> {
            if (minecraft != null) minecraft.setScreen(parent);
        }).bounds(fx, btnY + 48, fw, 18).build());
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        g.flush();

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);

        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelLeft, panelTop, PANEL_W, PANEL_H, 22,
                "§bAdd to Sidebar", ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
                ACCENT, ChroniclesThemePalette.TEXT);

        g.flush();
        super.render(g, mx, my, partial);
        g.pose().popPose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && (mx < panelLeft || mx >= panelLeft + PANEL_W || my < panelTop || my >= panelTop + PANEL_H)) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
