package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.common.codec.QuestFileSaver;
import net.phoenixvine.chronicles.common.model.QuestNode;

import org.jetbrains.annotations.NotNull;

public class SetIconScreen extends Screen {

    private static final int PANEL_W = 160;
    private static final int MARGIN = 10;
    private static final int BTN_H = 18;
    private static final int GAP = 6;

    private final ChronicleOverviewScreen parent;
    private final QuestNode node;
    private int panelLeft, panelTop, panelH;

    public SetIconScreen(ChronicleOverviewScreen parent, QuestNode node) {
        super(Component.translatable("phoenix_chronicles.screen.set_icon.title"));
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

        addRenderableWidget(Button.builder(Component.translatable("phoenix_chronicles.screen.set_icon.item"), b -> {
            minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                applyIcon(stack.getItem(), "", "", "Icon → " + stack.getHoverName().getString());
                close();
            }));
        }).bounds(fx, y, fw, BTN_H).build());
        y += BTN_H + GAP;

        addRenderableWidget(Button.builder(Component.translatable("phoenix_chronicles.screen.set_icon.fluid"), b -> {
            minecraft.setScreen(new FluidPickerScreen(this, fluidId -> {
                ResourceLocation rl = ResourceLocation.tryParse(fluidId);
                applyIcon(null, "", fluidId, "Icon → " + (rl != null ? rl.getPath() : fluidId) + " (fluid)");
                close();
            }));
        }).bounds(fx, y, fw, BTN_H).build());
        y += BTN_H + GAP;

        addRenderableWidget(Button.builder(Component.translatable("phoenix_chronicles.screen.set_icon.texture"), b -> {
            minecraft.setScreen(new TextureBrowserScreen(this, rl -> {
                applyIcon(null, rl, "", "Icon texture → " + rl);
                close();
            }));
        }).bounds(fx, y, fw, BTN_H).build());
        y += BTN_H + GAP;

        addRenderableWidget(Button.builder(Component.translatable("phoenix_chronicles.screen.set_icon.clear"), b -> {
            applyIcon(null, "", "", "Icon cleared");
            close();
        }).bounds(fx, y, fw, BTN_H).build());
    }

    /**
     * Snapshots the node's current icon (item/texture/fluid are mutually exclusive, so all three
     * always need saving/restoring together) before applying the new one, so the change is
     * undoable/redoable like every other quest edit.
     */
    private void applyIcon(net.minecraft.world.item.@org.jetbrains.annotations.Nullable Item newItem,
                           String newTexture, String newFluid, String feedback) {
        net.minecraft.world.item.Item oldItem = node.getIconItem();
        String oldTexture = node.getIconTexture();
        String oldFluid = node.getIconFluid();

        Runnable apply = () -> {
            node.setIconItem(newItem);
            node.setIconTexture(newTexture);
            node.setIconFluid(newFluid);
            QuestFileSaver.updateNodeIconAll(node);
            parent.rebuild();
        };
        Runnable revert = () -> {
            node.setIconItem(oldItem);
            node.setIconTexture(oldTexture);
            node.setIconFluid(oldFluid);
            QuestFileSaver.updateNodeIconAll(node);
            parent.rebuild();
        };

        apply.run();
        parent.setFeedback(feedback);
        parent.pushUndo("Undo: change quest icon", revert, apply);
    }

    private void close() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
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
        if (key == 256) {
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
