package net.phoenixvine.chronicles.client;

public class CanvasViewport {

    private static final float ZOOM_MIN = 0.3f;
    private static final float ZOOM_MAX = 2.0f;
    private static final float ZOOM_STEP = 0.05f;

    private double offsetX;
    private double offsetY;
    private float zoom = 1.0f;

    private int pendingPanDX;
    private int pendingPanDY;

    public void pan(double dx, double dy) {
        this.offsetX += dx;
        this.offsetY += dy;
        this.pendingPanDX += dx;
        this.pendingPanDY += dy;
    }

    public void flushPendingPan(java.util.function.BiConsumer<Integer, Integer> worker) {
        if (pendingPanDX != 0 || pendingPanDY != 0) {
            worker.accept(pendingPanDX, pendingPanDY);
            this.pendingPanDX = 0;
            this.pendingPanDY = 0;
        }
    }

    public boolean adjustZoomToAnchor(double scrollDelta, double mouseX, double mouseY, int sidebarW, int headerH) {
        float oldZoom = this.zoom;

        this.zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, this.zoom + (float) scrollDelta * ZOOM_STEP));

        if (this.zoom == oldZoom) return false;

        float ratio = this.zoom / oldZoom;
        int canvasMx = (int) mouseX - sidebarW;
        int canvasMy = (int) mouseY - headerH;

        this.offsetX = (int) (canvasMx - (canvasMx - this.offsetX) * ratio);
        this.offsetY = (int) (canvasMy - (canvasMy - this.offsetY) * ratio);

        return true;
    }

    public int toCanvasX(double screenX, int sidebarW) {
        return (int) ((screenX - sidebarW - offsetX) / zoom);
    }

    public int toCanvasY(double screenY, int headerH) {
        return (int) ((screenY - headerH - offsetY) / zoom);
    }

    public int toScreenX(int canvasX, int sidebarW) {
        return (int) (canvasX * zoom) + sidebarW + (int) offsetX;
    }

    public int toScreenY(int canvasY, int headerH) {
        return (int) (canvasY * zoom) + headerH + (int) offsetY;
    }

    public double getOffsetX() {
        return offsetX;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public float getZoom() {
        return zoom;
    }
}

