package net.phoenixvine.chronicles.client.screen;

import net.phoenixvine.chronicles.client.ChronicleKeyBindings;

public class CanvasViewport {

    public static final float ZOOM_MIN = 0.12f;
    public static final float ZOOM_MAX = 2.5f;
    public static final float ZOOM_STEP = 0.12f;

    private int viewOffX = 0;
    private int viewOffY = 0;
    private float zoom = 1.0f;

    public int getViewOffX() {
        return viewOffX;
    }

    public void reset() {
        this.zoom = 1.0f;
        this.viewOffX = 0;
        this.viewOffY = 0;
    }

    public float getMinZoom() {
        return ZOOM_MIN;
    }

    public float getMaxZoom() {
        return ZOOM_MAX;
    }

    private int gridSnap = 8;

    public int getGridSnap() {
        return this.gridSnap;
    }

    public void setGridSnap(int gridSnap) {
        this.gridSnap = gridSnap;
    }

    public void cycleGridSnap() {
        if (this.gridSnap <= 1) {
            this.gridSnap = 4;
        } else if (this.gridSnap < 32) {
            this.gridSnap *= 2;
        } else {
            this.gridSnap = 1;
        }
    }

    public void setOffset(int x, int y) {
        this.viewOffX = x;
        this.viewOffY = y;
    }

    public void setViewOffX(int viewOffX) {
        this.viewOffX = viewOffX;
    }

    public int getViewOffY() {
        return viewOffY;
    }

    public void setViewOffY(int viewOffY) {
        this.viewOffY = viewOffY;
    }

    public float getZoom() {
        return zoom;
    }

    public void setZoom(float zoom) {
        this.zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom));
    }

    public void pan(int dx, int dy) {
        this.viewOffX += dx;
        this.viewOffY += dy;
    }

    public int worldToScreenX(int worldX, int sidebarVisualW) {
        return (int) (worldX * zoom) + viewOffX + sidebarVisualW;
    }

    public int worldToScreenY(int worldY, int headerHeight) {
        return (int) (worldY * zoom) + viewOffY + headerHeight;
    }

    public int screenToWorldX(double screenX, int sidebarVisualW) {
        return (int) ((screenX - sidebarVisualW - viewOffX) / zoom);
    }

    public int screenToWorldY(double screenY, int headerHeight) {
        return (int) ((screenY - headerHeight - viewOffY) / zoom);
    }

    public boolean handleZoom(double mx, double my, double delta, int cl, int cr, int height, int headerH) {
        float oldZoom = zoom;
        float newZoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, oldZoom + (float) delta * ZOOM_STEP));

        if (newZoom == oldZoom) {
            return false;
        }

        int canvasW = cr - cl;
        int canvasH = height - headerH;
        boolean cursorAnchored = ChronicleKeyBindings.CURSOR_ZOOM.isDown();
        float anchorX = cursorAnchored ? (float) mx - cl : canvasW / 2f;
        float anchorY = cursorAnchored ? (float) my - headerH : canvasH / 2f;

        float worldCx = (anchorX - viewOffX) / oldZoom;
        float worldCy = (anchorY - viewOffY) / oldZoom;

        this.zoom = newZoom;
        this.viewOffX = (int) (anchorX - worldCx * newZoom);
        this.viewOffY = (int) (anchorY - worldCy * newZoom);

        return true;
    }
}
