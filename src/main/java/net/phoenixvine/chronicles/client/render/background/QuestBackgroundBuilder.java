package net.phoenixvine.chronicles.client.render.background;

import net.minecraft.client.gui.GuiGraphics;
import net.phoenixvine.chronicles.client.render.IQuestBackground;
import net.phoenixvine.chronicles.client.render.NodeShapeRenderer;
import net.phoenixvine.chronicles.model.QuestNode;

import java.util.ArrayList;
import java.util.List;

public final class QuestBackgroundBuilder {

    public enum BlendMode {

        NORMAL,

        ADD,

        SCREEN
    }

    private record Layer(BackgroundEffect effect, float opacity, BlendMode mode) {}

    private final List<Layer> layers = new ArrayList<>();
    private int resolution = 12;
    private float speedMultiplier = 1f;

    private QuestBackgroundBuilder() {}

    public static QuestBackgroundBuilder create() {
        return new QuestBackgroundBuilder();
    }

    public QuestBackgroundBuilder layer(BackgroundEffect effect) {
        return layer(effect, 1f, BlendMode.NORMAL);
    }

    public QuestBackgroundBuilder layer(BackgroundEffect effect, float opacity) {
        return layer(effect, opacity, BlendMode.NORMAL);
    }

    public QuestBackgroundBuilder layer(BackgroundEffect effect, float opacity, BlendMode mode) {
        layers.add(new Layer(effect, opacity, mode));
        return this;
    }

    public QuestBackgroundBuilder resolution(int cellsAcross) {
        this.resolution = Math.max(2, cellsAcross);
        return this;
    }

    public QuestBackgroundBuilder speed(float multiplier) {
        this.speedMultiplier = multiplier;
        return this;
    }

    public IQuestBackground build() {
        List<Layer> builtLayers = List.copyOf(layers);
        int cells = resolution;
        float speed = speedMultiplier;
        return (g, node, x, y, size, animTick) -> {

            long wrapped = animTick % 3_600_000L;
            long scaledTick = Math.round(wrapped * speed);
            render(g, node, x, y, size, scaledTick, builtLayers, cells);
        };
    }

    private static void render(GuiGraphics g, QuestNode node, int x, int y, int size, long animTick,
                               List<Layer> layers, int cells) {
        if (layers.isEmpty() || size <= 0) return;
        String shape = node.getShapeType();
        float cellSize = (float) size / cells;

        for (int gy = 0; gy < cells; gy++) {
            for (int gx = 0; gx < cells; gx++) {
                float px0 = gx * cellSize, py0 = gy * cellSize;

                int hits = 0;
                float sumNx = 0, sumNy = 0;
                for (float sy = 0.25f; sy < 1f; sy += 0.5f) {
                    for (float sx = 0.25f; sx < 1f; sx += 0.5f) {
                        float px = px0 + cellSize * sx, py = py0 + cellSize * sy;
                        float nx = (px / size) * 2f - 1f;
                        float ny = (py / size) * 2f - 1f;
                        if (ShapeMask.inside(shape, nx, ny)) hits++;
                        sumNx += nx;
                        sumNy += ny;
                    }
                }
                if (hits == 0) continue;
                float coverage = hits / 4f;
                float nx = sumNx / 4f, ny = sumNy / 4f;
                float dist = (float) Math.sqrt(nx * nx + ny * ny);
                float angle = (float) Math.atan2(ny, nx);

                int color = 0;
                for (Layer layer : layers) {
                    int c = layer.effect().colorAt(nx, ny, dist, angle, animTick);
                    int a = Math.round(((c >>> 24) & 0xFF) * layer.opacity());
                    if (a <= 0) continue;
                    c = (a << 24) | (c & 0x00FFFFFF);
                    color = composite(color, c, layer.mode());
                }

                int baseAlpha = (color >>> 24) & 0xFF;
                int finalAlpha = Math.round(baseAlpha * coverage);
                if (finalAlpha <= 0) continue;
                color = (finalAlpha << 24) | (color & 0x00FFFFFF);

                NodeShapeRenderer.queueFillRect(g, x + Math.round(px0), y + Math.round(py0),
                        x + Math.round(px0 + cellSize), y + Math.round(py0 + cellSize), color);
            }
        }
    }

    private static int composite(int dst, int src, BlendMode mode) {
        int dstA = (dst >>> 24) & 0xFF, srcA = (src >>> 24) & 0xFF;
        if (dstA == 0) return src;
        if (srcA == 0) return dst;

        float da = dstA / 255f, sa = srcA / 255f;
        int dr = (dst >> 16) & 0xFF, dg = (dst >> 8) & 0xFF, db = dst & 0xFF;
        int sr = (src >> 16) & 0xFF, sg = (src >> 8) & 0xFF, sb = src & 0xFF;

        int rr, rg, rb;
        switch (mode) {
            case ADD -> {
                rr = Math.min(255, dr + Math.round(sr * sa));
                rg = Math.min(255, dg + Math.round(sg * sa));
                rb = Math.min(255, db + Math.round(sb * sa));
            }
            case SCREEN -> {
                rr = 255 - Math.round((255 - dr) * (255 - Math.round(sr * sa)) / 255f);
                rg = 255 - Math.round((255 - dg) * (255 - Math.round(sg * sa)) / 255f);
                rb = 255 - Math.round((255 - db) * (255 - Math.round(sb * sa)) / 255f);
            }
            default -> {
                rr = Math.round(dr * (1f - sa) + sr * sa);
                rg = Math.round(dg * (1f - sa) + sg * sa);
                rb = Math.round(db * (1f - sa) + sb * sa);
            }
        }

        float outA = sa + da * (1f - sa);
        int ra = Math.round(outA * 255f);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }
}
