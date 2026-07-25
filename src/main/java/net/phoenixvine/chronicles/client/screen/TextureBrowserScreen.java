package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.registry.ChroniclesTheme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TextureBrowserScreen extends Screen {

    private static final int THUMB = 48;
    private static final int THUMB_PAD = 6;
    private static final int HEADER_H = 42;
    private static final int FOOTER_H = 28;
    private static final int COLS = 6;

    private int C_BG, C_PANEL, C_BORDER, C_ACCENT, C_TEXT, C_DIM, C_FAINT;

    private final Screen parent;
    private final Consumer<String> onSelect;

    private EditBox searchBox;
    private String query = "";

    private List<ResourceLocation> allTextures = List.of();
    private List<ResourceLocation> filtered = List.of();

    private int scrollY = 0;
    private int hoveredIdx = -1;

    public TextureBrowserScreen(Screen parent, Consumer<String> onSelect) {
        super(Component.literal("Texture Browser"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();
        ChroniclesTheme t = ChroniclesTheme.current();
        C_BG = t.bg.getColor();
        C_PANEL = t.panel.getColor();
        C_BORDER = t.border.getColor();
        C_ACCENT = t.accent.getColor();
        C_TEXT = t.text.getColor();
        C_DIM = t.textDim.getColor();
        C_FAINT = t.textFaint.getColor();

        allTextures = gatherTextures();
        applyFilter();

        searchBox = new EditBox(font, width / 2 - 100, HEADER_H / 2 - 5, 200, 14, Component.empty());
        searchBox.setHint(Component.literal("§8Search…"));
        searchBox.setResponder(q -> {
            query = q.toLowerCase();
            applyFilter();
            scrollY = 0;
        });
        addRenderableWidget(searchBox);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, C_BG);

        g.fill(0, 0, width, HEADER_H, C_PANEL);
        g.fill(0, HEADER_H - 1, width, HEADER_H, C_BORDER);
        g.drawCenteredString(font, "§fTexture Browser", width / 2, 6, C_TEXT);
        g.drawString(font, "§8Search:", width / 2 - 100 - font.width("Search: "), HEADER_H / 2 - 3, C_FAINT, false);

        int fy = height - FOOTER_H;
        g.fill(0, fy, width, height, C_PANEL);
        g.fill(0, fy, width, fy + 1, C_BORDER);
        g.drawString(font, "§8" + filtered.size() + " Textures  ·  LMB to select  ·  RMB to copy path",
                8, fy + 10, C_FAINT, false);

        g.enableScissor(0, HEADER_H, width, fy);
        int cols = Math.max(1, (width - 16) / (THUMB + THUMB_PAD));
        int startX = 8;
        int startY = HEADER_H + 4 - scrollY;

        hoveredIdx = -1;
        for (int i = 0; i < filtered.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int tx = startX + col * (THUMB + THUMB_PAD);
            int ty = startY + row * (THUMB + THUMB_PAD + 12);
            if (ty + THUMB + 12 < HEADER_H || ty > fy) continue;

            boolean hov = mx >= tx && mx < tx + THUMB && my >= ty && my < ty + THUMB;
            if (hov) hoveredIdx = i;

            ResourceLocation rl = filtered.get(i);

            for (int cy = 0; cy < THUMB; cy += 8) {
                for (int cx = 0; cx < THUMB; cx += 8) {
                    boolean light = ((cx / 8) + (cy / 8)) % 2 == 0;
                    g.fill(tx + cx, ty + cy, tx + cx + 8, ty + cy + 8, light ? 0xFF666666 : 0xFF444444);
                }
            }

            try {
                g.blit(net.phoenixvine.chronicles.client.CustomTextureCache.resolve(rl),
                        tx, ty, 0, 0, THUMB, THUMB, THUMB, THUMB);
            } catch (Exception ignored) {
                g.fill(tx, ty, tx + THUMB, ty + THUMB, 0xFF441144);
                g.drawCenteredString(font, "§c?", tx + THUMB / 2, ty + THUMB / 2 - 4, 0xFFFF4444);
            }

            if (hov) {
                g.fill(tx - 1, ty - 1, tx + THUMB + 1, ty, C_ACCENT);
                g.fill(tx - 1, ty + THUMB, tx + THUMB + 1, ty + THUMB + 1, C_ACCENT);
                g.fill(tx - 1, ty - 1, tx, ty + THUMB + 1, C_ACCENT);
                g.fill(tx + THUMB, ty - 1, tx + THUMB + 1, ty + THUMB + 1, C_ACCENT);
            }

            String name = shortName(rl);
            int nameW = font.width(name);
            if (nameW > THUMB) name = font.plainSubstrByWidth(name, THUMB - 4) + "…";
            g.drawString(font, "§8" + name, tx, ty + THUMB + 2, C_FAINT, false);

            if (hov) g.renderTooltip(font, Component.literal("§f" + rl), mx, my);
        }

        g.disableScissor();
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        if (hoveredIdx >= 0 && hoveredIdx < filtered.size()) {
            String rl = filtered.get(hoveredIdx).toString();
            if (btn == 0) {

                onSelect.accept(rl);
                Minecraft.getInstance().setScreen(parent);
            } else if (btn == 1) {

                Minecraft.getInstance().keyboardHandler.setClipboard(rl);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int cols = Math.max(1, (width - 16) / (THUMB + THUMB_PAD));
        int rows = (filtered.size() + cols - 1) / cols;
        int totalH = rows * (THUMB + THUMB_PAD + 12);
        int visible = height - HEADER_H - FOOTER_H;
        scrollY = (int) Math.max(0, Math.min(scrollY - delta * 20, totalH - visible));
        return true;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == 256) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<ResourceLocation> gatherTextures() {
        List<ResourceLocation> result = new ArrayList<>();

        try {
            var rm = Minecraft.getInstance().getResourceManager();
            rm.listResources("textures", rl -> rl.getPath().endsWith(".png"))
                    .keySet().stream()
                    .sorted(Comparator.comparing(ResourceLocation::toString))
                    .forEach(result::add);
        } catch (Exception ignored) {}

        try {
            Path texDir = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("phoenix_chronicles").resolve("textures");
            if (Files.exists(texDir)) {
                try (Stream<Path> walk = Files.walk(texDir)) {
                    walk.filter(TextureBrowserScreen::isImageFile)
                            .sorted()
                            .forEach(p -> {
                                String rel = texDir.relativize(p).toString()
                                        .replace('\\', '/');
                                try {
                                    result.add(new ResourceLocation("phoenix_chronicles", "textures/custom/" + rel));
                                } catch (Exception ignored2) {}
                            });
                }
            }
        } catch (IOException ignored) {}

        return result.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

    private void applyFilter() {
        if (query.isBlank()) {
            filtered = new ArrayList<>(allTextures);
        } else {
            filtered = allTextures.stream()
                    .filter(rl -> rl.toString().toLowerCase().contains(query))
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    private static boolean isImageFile(Path p) {
        String name = p.toString().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private static String shortName(ResourceLocation rl) {
        String path = rl.getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
