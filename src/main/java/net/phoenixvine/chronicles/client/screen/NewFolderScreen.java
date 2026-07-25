package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.registry.CategoryRegistry;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class NewFolderScreen extends Screen {

    private static final int PANEL_W = 200;
    private static final int PANEL_H = 76;
    private static final int MARGIN = 12;
    private static final int FIELD_H = 13;
    private static final int ACCENT = 0xFF4499CC;

    private final Screen parent;
    private final Consumer<String> onCreated;
    private String name = "";

    private EditBox nameBox;
    private int panelLeft, panelTop;

    public NewFolderScreen(Screen parent, Consumer<String> onCreated) {
        super(Component.literal("New Category"));
        this.parent = parent;
        this.onCreated = onCreated;
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_W) / 2;
        panelTop = (height - PANEL_H) / 2;
        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;
        int y = panelTop + 34;

        nameBox = new EditBox(font, fx, y, fw, FIELD_H, Component.empty());
        nameBox.setMaxLength(40);
        nameBox.setHint(Component.literal("§8e.g. Progression"));
        nameBox.setResponder(v -> name = v);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        int btnY = panelTop + PANEL_H - 10 - 18;
        int half = (fw - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal("§aCreate"), b -> create())
                .bounds(fx, btnY, half, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§7Cancel"),
                b -> {
                    if (minecraft != null) minecraft.setScreen(parent);
                })
                .bounds(fx + half + 6, btnY, half, 18).build());
    }

    private void create() {
        String label = name.trim();
        if (label.isEmpty()) return;

        String id = label.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        CategoryRegistry.addCategory(id, label);
        CategoryRegistry.save();
        onCreated.accept(id);
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        if (parent != null) parent.render(g, -1, -1, partial);
        g.flush();

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);

        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelLeft, panelTop, PANEL_W, PANEL_H, 22,
                "§bNew Category", ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
                ACCENT, ChroniclesThemePalette.TEXT);

        g.drawString(font, "§8Category name", panelLeft + MARGIN, panelTop + 24, ChroniclesThemePalette.TEXT_FAINT,
                false);

        g.flush();
        super.render(g, mx, my, partial);
        g.pose().popPose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) {
            create();
            return true;
        }
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
