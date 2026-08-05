package net.phoenixvine.chronicles.client.render.line;

import net.minecraft.client.gui.GuiGraphics;
import net.phoenixvine.chronicles.codec.QuestChroniclesSettings;

public class FlowingParticleLineStyle implements IDependencyLineStyle {

    private static final float DOT_SPACING = 14f;
    private static final float DOT_RADIUS = 1.6f;
    private final QuestChroniclesSettings.LineAnimSpeed speed;

    public FlowingParticleLineStyle() {
        this(QuestChroniclesSettings.LineAnimSpeed.NORMAL);
    }

    public FlowingParticleLineStyle(QuestChroniclesSettings.LineAnimSpeed speed) {
        this.speed = speed;
    }

    @Override
    public void render(GuiGraphics g, int px, int py, int cx, int cy, int color, long animTick) {
        float dx = cx - px;
        float dy = cy - py;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;

        int dimmedColor = (color & 0x00FFFFFF) | 0x55000000;
        LineRenderUtil.drawThickLine(g, px, py, cx, cy, 1.5f, dimmedColor);

        float ux = dx / len;
        float uy = dy / len;

        float phase = (animTick / (float) speed.divisor) % DOT_SPACING;
        for (float d = phase; d < len; d += DOT_SPACING) {
            float x = px + ux * d;
            float y = py + uy * d;
            LineRenderUtil.drawDot(g, x, y, DOT_RADIUS, color);
        }
    }
}
