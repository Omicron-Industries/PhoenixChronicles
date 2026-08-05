package net.phoenixvine.chronicles.client.render.background;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.ChronicleShaders;
import net.phoenixvine.chronicles.client.render.IQuestBackground;
import net.phoenixvine.chronicles.model.QuestNode;

public class GlitchShearBackground implements IQuestBackground {

    private final float scale;
    private final float speed;

    public GlitchShearBackground() {
        this(1f, 1f);
    }

    public GlitchShearBackground(float scale, float speed) {
        this.scale = scale;
        this.speed = speed;
    }

    @Override
    public void render(GuiGraphics g, QuestNode node, int x, int y, int size, long animTick) {
        ResourceLocation mask = BackgroundRenderUtil.maskTextureFor(node.getShapeType());
        int drawnSize = Math.round(size * scale);
        int offset = (drawnSize - size) / 2;
        BackgroundRenderUtil.drawShaderQuad(g, ChronicleShaders.QUEST_BG_GLITCH, mask,
                x - offset, y - offset, drawnSize, BackgroundRenderUtil.wrappedSeconds(animTick) * speed);
    }
}
