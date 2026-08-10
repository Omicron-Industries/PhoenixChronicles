package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.*;
import net.phoenixvine.chronicles.client.render.*;
import net.phoenixvine.chronicles.model.*;

import org.jetbrains.annotations.Nullable;

import java.util.List;

class PictureContextMenu implements TogglePanel {

    private static final int[] RESIZE_PRESETS = { 32, 64, 128, 256, 512, 1024 };
    private static final int[] OPACITY_PRESETS = { 100, 75, 50, 25, 10 };
    private static final String[] TINT_NAMES = { "None (white)", "Warm sepia", "Cool blue", "Faded gray",
            "Ghostly" };
    private static final int[] TINT_PRESETS = { 0xFFFFFF, 0xE0C088, 0x88AAE0, 0xAAAAAA, 0x99CCFF };
    private static final int CTX_W = ChronicleOverviewScreen.CTX_W;
    private static final int CTX_ROW = ChronicleOverviewScreen.CTX_ROW;
    private static final int CTX_SEP = ChronicleOverviewScreen.CTX_SEP;
    private static final int MENU_H = 4 + CTX_ROW * 7 + CTX_SEP;

    private final ScreenContext ctx;

    private boolean open = false;
    private long openTimeMs = 0;
    private int x, y;
    @Nullable
    private BackgroundPictureConfig.Picture target = null;
    private boolean resizeOpen = false;
    private boolean moveCatOpen = false;
    private boolean opacityOpen = false;
    private boolean tintOpen = false;

    PictureContextMenu(ScreenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public boolean isVisible(ScreenContext ctx) {
        return ctx.isDevMode() && isOpen();
    }

    void open(int x, int y, BackgroundPictureConfig.Picture pic) {
        open = true;
        openTimeMs = System.currentTimeMillis();
        resizeOpen = false;
        moveCatOpen = false;
        opacityOpen = false;
        tintOpen = false;
        target = pic;
        this.x = x;
        this.y = y;
        if (this.y + MENU_H > ctx.height() - 4) this.y = ctx.height() - MENU_H - 4;
        if (this.x + CTX_W > ctx.width() - 4) this.x = ctx.width() - CTX_W - 4;

        this.x = Math.max(4, this.x);
        this.y = Math.max(4, this.y);
    }

    @Override
    public void close() {
        open = false;
        resizeOpen = false;
        moveCatOpen = false;
        opacityOpen = false;
        tintOpen = false;
        target = null;
    }

    private int drawRow(GuiGraphics g, int x, int iy, String label, String color, boolean danger,
                        int mx, int my) {
        boolean hov = mx >= x + 1 && mx <= x + CTX_W - 1 && my >= iy && my <= iy + CTX_ROW;
        if (hov) g.fill(x + 1, iy, x + CTX_W - 1, iy + CTX_ROW, ChronicleOverviewScreen.C_CTX_HOVER);
        g.drawString(ctx.font(), color + label, x + 8, iy + 4,
                danger ? ChronicleOverviewScreen.C_CTX_DANGER : ChronicleOverviewScreen.C_CTX_TEXT);
        return iy + CTX_ROW;
    }

    @Override
    public void render(ScreenContext ctx, GuiGraphics g, int mouseX, int mouseY, int contentLeft, int contentRight) {
        render(g, mouseX, mouseY);
    }

    private void render(GuiGraphics g, int mx, int my) {
        if (target == null) {
            open = false;
            return;
        }
        int x = this.x, y = this.y;
        int menuH = MENU_H;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.flush();

        int ctxAlpha = (int) Math.min(255,
                (System.currentTimeMillis() - openTimeMs) * 255 / ChronicleOverviewScreen.OPEN_FADE_MS);
        int fadedBg = (ctxAlpha << 24) | (ChronicleOverviewScreen.C_CTX_BG & 0x00FFFFFF);
        g.fill(x + 3, y + 3, x + CTX_W + 3, y + menuH + 3, (Math.min(0x55, ctxAlpha / 3)) << 24);
        g.fill(x, y, x + CTX_W, y + menuH, fadedBg);
        g.fill(x, y, x + CTX_W, y + 1, ChronicleOverviewScreen.C_CTX_BORDER);
        g.fill(x, y + menuH - 1, x + CTX_W, y + menuH, ChronicleOverviewScreen.C_CTX_BORDER);
        g.fill(x, y, x + 1, y + menuH, ChronicleOverviewScreen.C_CTX_BORDER);
        g.fill(x + CTX_W - 1, y, x + CTX_W, y + menuH, ChronicleOverviewScreen.C_CTX_BORDER);

        int iy = y + 2;
        iy = drawRow(g, x, iy, "Move  §8(shift+drag)", "§7", false, mx, my);
        iy = drawRow(g, x, iy, "Resize  ▸", "§7", false, mx, my);
        iy = drawRow(g, x, iy, "Resize (scroll + drag)…", "§7", false, mx, my);
        iy = drawRow(g, x, iy, "Opacity  ▸", "§7", false, mx, my);
        iy = drawRow(g, x, iy, "Tint  ▸", "§7", false, mx, my);
        iy = drawRow(g, x, iy, "Move to Chapter  ▸", "§7", false, mx, my);
        g.fill(x + 6, iy + 2, x + CTX_W - 6, iy + 3, ChronicleOverviewScreen.C_CTX_SEP);
        iy += CTX_SEP;
        drawRow(g, x, iy, "Delete picture", "§c", true, mx, my);

        if (resizeOpen) renderResizeSubmenu(g, x, y + 2 + CTX_ROW, mx, my);
        if (opacityOpen) renderOpacitySubmenu(g, x, y + 2 + CTX_ROW * 3, mx, my);
        if (tintOpen) renderTintSubmenu(g, x, y + 2 + CTX_ROW * 4, mx, my);
        if (moveCatOpen) renderMoveCatSubmenu(g, x, y + 2 + CTX_ROW * 5, mx, my);

        g.pose().popPose();
    }

    private void renderResizeSubmenu(GuiGraphics g, int x, int subY, int mx, int my) {
        int subX = x + CTX_W + 2;
        ResourceLocation nativeLoc = texture();
        int[] nativeSz = nativeLoc != null ? CustomTextureCache.nativeSize(nativeLoc) : null;
        int rows = RESIZE_PRESETS.length + (nativeSz != null ? 1 : 0);
        int subH = rows * CTX_ROW + 4;
        g.fill(subX + 2, subY + 2, subX + CTX_W + 2, subY + subH + 2, 0x55000000);
        g.fill(subX, subY, subX + CTX_W, subY + subH, ChronicleOverviewScreen.C_CTX_BG);
        ChroniclesUIKit.drawBorder(g, subX, subY, CTX_W, subH, ChronicleOverviewScreen.C_CTX_BORDER);
        int sy = subY + 2;
        if (nativeSz != null) {
            drawRow(g, subX, sy, "§b" + nativeSz[0] + "x" + nativeSz[1] + " §8(native)", "", false, mx, my);
            sy += CTX_ROW;
        }
        for (int size : RESIZE_PRESETS) {
            boolean isCurrent = target != null && Math.round(target.w) == size;
            String mark = isCurrent ? "§a● §7" : "§8  §7";
            drawRow(g, subX, sy, mark + size + "px", "", false, mx, my);
            sy += CTX_ROW;
        }
    }

    @Nullable
    private ResourceLocation texture() {
        if (target == null || target.texture == null || target.texture.isBlank()) return null;
        try {
            return new ResourceLocation(target.texture);
        } catch (Exception e) {
            return null;
        }
    }

    private void renderOpacitySubmenu(GuiGraphics g, int x, int subY, int mx, int my) {
        int subX = x + CTX_W + 2;
        int subH = OPACITY_PRESETS.length * CTX_ROW + 4;
        g.fill(subX + 2, subY + 2, subX + CTX_W + 2, subY + subH + 2, 0x55000000);
        g.fill(subX, subY, subX + CTX_W, subY + subH, ChronicleOverviewScreen.C_CTX_BG);
        ChroniclesUIKit.drawBorder(g, subX, subY, CTX_W, subH, ChronicleOverviewScreen.C_CTX_BORDER);
        int sy = subY + 2;
        for (int pct : OPACITY_PRESETS) {
            boolean isCurrent = target != null && Math.round(target.opacity * 100) == pct;
            String mark = isCurrent ? "§a● §7" : "§8  §7";
            drawRow(g, subX, sy, mark + pct + "%", "", false, mx, my);
            sy += CTX_ROW;
        }
    }

    private void renderTintSubmenu(GuiGraphics g, int x, int subY, int mx, int my) {
        int subX = x + CTX_W + 2;
        int subH = TINT_PRESETS.length * CTX_ROW + 4;
        g.fill(subX + 2, subY + 2, subX + CTX_W + 2, subY + subH + 2, 0x55000000);
        g.fill(subX, subY, subX + CTX_W, subY + subH, ChronicleOverviewScreen.C_CTX_BG);
        ChroniclesUIKit.drawBorder(g, subX, subY, CTX_W, subH, ChronicleOverviewScreen.C_CTX_BORDER);
        int sy = subY + 2;
        for (int i = 0; i < TINT_PRESETS.length; i++) {
            boolean isCurrent = target != null && target.color == TINT_PRESETS[i];
            String mark = isCurrent ? "§a● §7" : "§8  §7";
            drawRow(g, subX, sy, mark + TINT_NAMES[i], "", false, mx, my);
            sy += CTX_ROW;
        }
    }

    private void renderMoveCatSubmenu(GuiGraphics g, int x, int subY, int mx, int my) {
        List<String> cats = ctx.buildChapterList();
        cats.remove("ALL");
        cats.remove(ctx.selectedChapter());
        int subX = x + CTX_W + 2;
        int subH = Math.max(CTX_ROW, cats.size() * CTX_ROW) + 4;
        g.fill(subX + 2, subY + 2, subX + CTX_W + 2, subY + subH + 2, 0x55000000);
        g.fill(subX, subY, subX + CTX_W, subY + subH, ChronicleOverviewScreen.C_CTX_BG);
        ChroniclesUIKit.drawBorder(g, subX, subY, CTX_W, subH, ChronicleOverviewScreen.C_CTX_BORDER);
        int sy = subY + 2;
        if (cats.isEmpty()) {
            g.drawString(ctx.font(), "§8(no other chapters)", subX + 6, sy + 4,
                    ChronicleOverviewScreen.C_CTX_TEXT);
        }
        for (String cat : cats) {
            drawRow(g, subX, sy, "§7" + ctx.friendly(cat), "", false, mx, my);
            sy += CTX_ROW;
        }
    }

    @Override
    public boolean mouseClicked(ScreenContext ctx, double mouseX, double mouseY, int button) {
        return handleClick((int) mouseX, (int) mouseY);
    }

    private boolean handleClick(int mx, int my) {
        if (target == null) return false;
        BackgroundPictureConfig.Picture pic = target;
        int x = this.x, y = this.y;

        if (resizeOpen) {
            ResourceLocation nativeLoc = texture();
            int[] nativeSz = nativeLoc != null ? CustomTextureCache.nativeSize(nativeLoc) : null;
            int subX = x + CTX_W + 2, subY = y + 2 + CTX_ROW;
            int sy = subY;
            if (nativeSz != null) {
                if (mx >= subX && mx <= subX + CTX_W && my >= sy && my <= sy + CTX_ROW) {
                    applyResize(pic, nativeSz[0], nativeSz[1]);
                    return true;
                }
                sy += CTX_ROW;
            }
            for (int i = 0; i < RESIZE_PRESETS.length; i++) {
                int ry = sy + i * CTX_ROW;
                if (mx >= subX && mx <= subX + CTX_W && my >= ry && my <= ry + CTX_ROW) {
                    int size = RESIZE_PRESETS[i];
                    applyResize(pic, size, size);
                    return true;
                }
            }
            if (mx < x || mx > x + CTX_W + 2 + CTX_W || my < y || my > y + MENU_H) {
                close();
                return true;
            }
        }
        if (opacityOpen) {
            int subX = x + CTX_W + 2, subY = y + 2 + CTX_ROW * 3;
            int sy = subY;
            for (int pct : OPACITY_PRESETS) {
                if (mx >= subX && mx <= subX + CTX_W && my >= sy && my <= sy + CTX_ROW) {
                    final float oldOpacity = pic.opacity;
                    ctx.undoRedo().push(() -> {
                        pic.opacity = oldOpacity;
                        BackgroundPictureConfig.save();
                        ctx.setFeedback("Undo: picture opacity reverted");
                    });
                    pic.opacity = pct / 100f;
                    BackgroundPictureConfig.save();
                    ctx.setFeedback("Picture opacity set to %d%%  (Ctrl+Z to undo)", pct);
                    close();
                    return true;
                }
                sy += CTX_ROW;
            }
            if (mx < x || mx > x + CTX_W + 2 + CTX_W || my < y || my > y + MENU_H) {
                close();
                return true;
            }
        }
        if (tintOpen) {
            int subX = x + CTX_W + 2, subY = y + 2 + CTX_ROW * 4;
            int sy = subY;
            for (int i = 0; i < TINT_PRESETS.length; i++) {
                if (mx >= subX && mx <= subX + CTX_W && my >= sy && my <= sy + CTX_ROW) {
                    final int oldColor = pic.color;
                    final int newColor = TINT_PRESETS[i];
                    ctx.undoRedo().push(() -> {
                        pic.color = oldColor;
                        BackgroundPictureConfig.save();
                        ctx.setFeedback("Undo: picture tint reverted");
                    });
                    pic.color = newColor;
                    BackgroundPictureConfig.save();
                    ctx.setFeedback("Picture tint set to %s  (Ctrl+Z to undo)", TINT_NAMES[i]);
                    close();
                    return true;
                }
                sy += CTX_ROW;
            }
            if (mx < x || mx > x + CTX_W + 2 + CTX_W || my < y || my > y + MENU_H) {
                close();
                return true;
            }
        }
        if (moveCatOpen) {
            List<String> cats = ctx.buildChapterList();
            cats.remove("ALL");
            cats.remove(ctx.selectedChapter());
            int subX = x + CTX_W + 2, subY = y + 2 + CTX_ROW * 5;
            int sy = subY + 2;
            for (String cat : cats) {
                if (mx >= subX && mx <= subX + CTX_W && my >= sy && my <= sy + CTX_ROW) {
                    final String oldCat = ctx.selectedChapter();
                    final String newCat = cat;
                    ctx.undoRedo().push(() -> {
                        BackgroundPictureConfig.remove(newCat, pic);
                        BackgroundPictureConfig.add(oldCat, pic);
                        ctx.setFeedback("Undo: picture moved back to %s", ctx.friendly(oldCat));
                    });
                    BackgroundPictureConfig.remove(oldCat, pic);
                    BackgroundPictureConfig.add(newCat, pic);
                    ctx.setFeedback("Picture moved to %s  (Ctrl+Z to undo)", ctx.friendly(newCat));
                    close();
                    return true;
                }
                sy += CTX_ROW;
            }
            if (mx < x || mx > x + CTX_W + 2 + CTX_W || my < y || my > y + MENU_H) {
                close();
                return true;
            }
        }

        int rowY0 = y + 2;
        int rowY1 = rowY0 + CTX_ROW;
        int rowY2 = rowY1 + CTX_ROW;
        int rowY3 = rowY2 + CTX_ROW;
        int rowY4 = rowY3 + CTX_ROW;
        int rowY5 = rowY4 + CTX_ROW;
        int rowY6 = rowY5 + CTX_ROW + CTX_SEP;

        if (mx < x || mx > x + CTX_W) {
            close();
            return true;
        }
        if (my >= rowY0 && my < rowY0 + CTX_ROW) {

            ctx.setFeedback("Shift-click and drag the picture");
            close();
            return true;
        }
        if (my >= rowY1 && my < rowY1 + CTX_ROW) {
            resizeOpen = !resizeOpen;
            opacityOpen = false;
            tintOpen = false;
            moveCatOpen = false;
            return true;
        }
        if (my >= rowY2 && my < rowY2 + CTX_ROW) {

            final BackgroundPictureConfig.Picture editedPic = pic;
            final float ux = pic.x, uy = pic.y, uw = pic.w, uh = pic.h;
            ctx.undoRedo().push(() -> {
                editedPic.x = ux;
                editedPic.y = uy;
                editedPic.w = uw;
                editedPic.h = uh;
                BackgroundPictureConfig.save();
                ctx.setFeedback("Undo: picture edit reverted");
            });
            ctx.setPictureEditMode(pic);
            ctx.setFeedback("§eScroll to resize, drag to move - right-click or Esc to finish");
            close();
            return true;
        }
        if (my >= rowY3 && my < rowY3 + CTX_ROW) {
            opacityOpen = !opacityOpen;
            resizeOpen = false;
            tintOpen = false;
            moveCatOpen = false;
            return true;
        }
        if (my >= rowY4 && my < rowY4 + CTX_ROW) {
            tintOpen = !tintOpen;
            resizeOpen = false;
            opacityOpen = false;
            moveCatOpen = false;
            return true;
        }
        if (my >= rowY5 && my < rowY5 + CTX_ROW) {
            moveCatOpen = !moveCatOpen;
            resizeOpen = false;
            opacityOpen = false;
            tintOpen = false;
            return true;
        }
        if (my >= rowY6 && my < rowY6 + CTX_ROW) {
            final BackgroundPictureConfig.Picture deleted = pic;
            final String cat = ctx.selectedChapter();
            ctx.undoRedo().push(() -> {
                BackgroundPictureConfig.add(cat, deleted);
                ctx.setFeedback("Undo: picture restored");
            });
            BackgroundPictureConfig.remove(cat, deleted);
            ctx.setFeedback("Picture deleted  (Ctrl+Z to undo)");
            close();
            return true;
        }
        close();
        return true;
    }

    private void applyResize(BackgroundPictureConfig.Picture pic, float w, float h) {
        final float oldW = pic.w, oldH = pic.h;
        ctx.undoRedo().push(() -> {
            pic.w = oldW;
            pic.h = oldH;
            BackgroundPictureConfig.save();
            ctx.setFeedback("Undo: picture resized");
        });
        pic.w = w;
        pic.h = h;
        BackgroundPictureConfig.save();
        ctx.setFeedback("Picture resized  (Ctrl+Z to undo)");
        close();
    }
}
