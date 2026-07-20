package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.codec.QuestFileSaver;
import net.phoenixvine.chronicles.model.QuestNode;

import org.jetbrains.annotations.NotNull;

/**
 * Single entry point for setting a quest node's icon - opened via the node context menu's one
 * "Set Icon…" row instead of that menu carrying three separate "Set Icon by Item/Fluid/Texture"
 * rows plus a "Clear Icon" row of its own. Keeping the right-click menu to one row per concern
 * (however many choices that concern has) is deliberate - it's the same reasoning as "Resize
 * (scroll + drag)…" replacing what used to be size cycling directly in the menu.
 *
 * Item/fluid/texture are mutually exclusive: picking any one clears the other two (see
 * QuestNode#getIconFluid's doc) and writes all three together in one disk patch
 * (QuestFileSaver#updateNodeIconAll).
 */
public class SetIconScreen extends Screen {

    private static final int PANEL_W = 160;
    private static final int MARGIN = 10;
    private static final int BTN_H = 18;
    private static final int GAP = 6;

    private final ChronicleOverviewScreen parent;
    private final QuestNode node;
    private int panelLeft, panelTop, panelH;

    public SetIconScreen(ChronicleOverviewScreen parent, QuestNode node) {
        super(Component.literal("Set Icon"));
        this.parent = parent;
        this.node = node;
    }

    @Override
    protected void init() {
        panelH = 26 + BTN_H * 4 + GAP * 3 + 16;
        panelLeft = (width - PANEL_W) / 2;
        panelTop = (height - panelH) / 2;

        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;
        int y = panelTop + 26;

        addRenderableWidget(Button.builder(Component.literal("§eItem…"), b -> {
            minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                node.setIconItem(stack.getItem());
                node.setIconTexture("");
                node.setIconFluid("");
                QuestFileSaver.updateNodeIconAll(node);
                parent.setFeedback("Icon → " + stack.getHoverName().getString());
                parent.rebuild();
                close();
            }));
        }).bounds(fx, y, fw, BTN_H).build());
        y += BTN_H + GAP;

        addRenderableWidget(Button.builder(Component.literal("§bFluid…"), b -> {
            minecraft.setScreen(new FluidPickerScreen(this, fluidId -> {
                node.setIconItem(null);
                node.setIconTexture("");
                node.setIconFluid(fluidId);
                QuestFileSaver.updateNodeIconAll(node);
                ResourceLocation rl = ResourceLocation.tryParse(fluidId);
                parent.setFeedback("Icon → " + (rl != null ? rl.getPath() : fluidId) + " (fluid)");
                parent.rebuild();
                close();
            }));
        }).bounds(fx, y, fw, BTN_H).build());
        y += BTN_H + GAP;

        addRenderableWidget(Button.builder(Component.literal("§dTexture…"), b -> {
            minecraft.setScreen(new TextureBrowserScreen(this, rl -> {
                node.setIconItem(null);
                node.setIconFluid("");
                node.setIconTexture(rl);
                QuestFileSaver.updateNodeIconAll(node);
                parent.setFeedback("Icon texture → " + rl);
                parent.rebuild();
                close();
            }));
        }).bounds(fx, y, fw, BTN_H).build());
        y += BTN_H + GAP;

        addRenderableWidget(Button.builder(Component.literal("§8Clear Icon"), b -> {
            node.setIconItem(null);
            node.setIconTexture("");
            node.setIconFluid("");
            QuestFileSaver.updateNodeIconAll(node);
            parent.setFeedback("Icon cleared");
            parent.rebuild();
            close();
        }).bounds(fx, y, fw, BTN_H).build());
    }

    private void close() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) { /* parent renders behind us */ }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        // renderForChildScreen(), not a plain render(g, -1, -1, partial) - the latter still runs
        // the overview screen's OWN interactive bits (context menu, tooltips, hover states) keyed
        // off whatever the real mouse position/open-menu state happens to be, which is "the actual
        // UI" bleeding through behind this modal, not just inert scenery. renderForChildScreen()
        // is built specifically to skip all of that and already flushes + clears its own scissor.
        parent.renderForChildScreen(g);

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);

        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelLeft, panelTop, PANEL_W, panelH, 22,
                "§fSet Icon", ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
                ChroniclesThemePalette.BORDER, ChroniclesThemePalette.TEXT);

        g.flush();
        super.render(g, mx, my, partial);
        g.pose().popPose();
        g.flush();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (mx < panelLeft || mx >= panelLeft + PANEL_W || my < panelTop || my >= panelTop + panelH) {
            close();
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { // ESC
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
