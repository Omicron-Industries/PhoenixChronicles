package net.phoenixvine.chronicles.client.render.background;

final class ShapeMask {

    private ShapeMask() {}

    static boolean inside(String shape, float nx, float ny) {
        return switch (shape == null ? "SQUARE" : shape.toUpperCase(java.util.Locale.ROOT)) {
            case "CIRCLE" -> nx * nx + ny * ny <= 1f;
            case "DIAMOND" -> Math.abs(nx) <= 1f - Math.abs(ny);
            case "HEXAGON" -> insideHexagon(nx, ny);
            case "TRIANGLE" -> insideTriangle(nx, ny);
            case "STAR" -> insidePolygon(nx, ny, starVerts());
            case "PENTAGON" -> insidePolygon(nx, ny, regularPolygonVerts(5, 1f));
            case "SHIELD" -> insideShield(nx, ny);
            case "CROSS" -> insideCross(nx, ny);
            default -> Math.abs(nx) <= 1f && Math.abs(ny) <= 1f;
        };
    }

    private static boolean insideHexagon(float nx, float ny) {
        float r = 1f / 0.866f;
        float dy = Math.abs(ny);
        float qr = r * 0.866f;
        float hw;
        if (dy <= r / 2f) {
            hw = qr;
        } else {
            float t = 1f - (dy - r / 2f) / (r / 2f);
            hw = t > 0f ? qr * t : 0f;
        }
        return Math.abs(nx) <= hw;
    }

    private static boolean insideTriangle(float nx, float ny) {
        float t = (ny + 1f) / 2f;
        if (t < 0f || t > 1f) return false;
        return Math.abs(nx) <= t;
    }

    private static boolean insideShield(float nx, float ny) {
        float midY = 1f / 3f;
        if (ny < midY) return Math.abs(nx) <= 1f;
        float t = (ny - midY) / (1f - midY);
        float half = (1f - t) * 1f;
        return half > 0f && Math.abs(nx) <= half;
    }

    private static boolean insideCross(float nx, float ny) {
        float halfArm = 1f / 3f;
        boolean vertical = Math.abs(nx) <= halfArm;
        boolean horizontal = Math.abs(ny) <= halfArm;
        return vertical || horizontal;
    }

    private static float[][] starVerts() {
        int points = 5;
        float outerR = 1f, innerR = outerR * 0.4f;
        float[][] verts = new float[points * 2][2];
        for (int i = 0; i < points * 2; i++) {
            double a = -Math.PI / 2 + i * Math.PI / points;
            float r = (i % 2 == 0) ? outerR : innerR;
            verts[i][0] = (float) (Math.cos(a) * r);
            verts[i][1] = (float) (Math.sin(a) * r);
        }
        return verts;
    }

    private static float[][] regularPolygonVerts(int sides, float r) {
        float[][] verts = new float[sides][2];
        for (int i = 0; i < sides; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / sides;
            verts[i][0] = (float) (Math.cos(a) * r);
            verts[i][1] = (float) (Math.sin(a) * r);
        }
        return verts;
    }

    private static boolean insidePolygon(float x, float y, float[][] verts) {
        int n = verts.length;
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = verts[i][0], yi = verts[i][1];
            float xj = verts[j][0], yj = verts[j][1];
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }
}
