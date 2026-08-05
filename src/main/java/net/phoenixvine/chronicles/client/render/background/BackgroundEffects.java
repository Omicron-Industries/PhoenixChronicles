package net.phoenixvine.chronicles.client.render.background;

public final class BackgroundEffects {

    private BackgroundEffects() {}

    public static int argb(int a, int r, int g, int b) {
        int ca = Math.max(0, Math.min(255, a));
        int cr = Math.max(0, Math.min(255, r));
        int cg = Math.max(0, Math.min(255, g));
        int cb = Math.max(0, Math.min(255, b));
        return (ca << 24) | (cr << 16) | (cg << 8) | cb;
    }

    public static BackgroundEffect solid(int argb) {
        return (nx, ny, dist, angle, animTick) -> argb;
    }

    public static BackgroundEffect solid(int argb, float pulseSpeedHz) {
        if (pulseSpeedHz == 0f) return solid(argb);
        return (nx, ny, dist, angle, animTick) -> {
            float phase = animTick / 1000f * pulseSpeedHz * (float) (2 * Math.PI);
            return withAlphaScale(argb, 0.6f + 0.4f * (0.5f + 0.5f * (float) Math.sin(phase)));
        };
    }

    public static BackgroundEffect radialGradient(int innerColor, int outerColor) {
        return radialGradient(innerColor, outerColor, 0f);
    }

    public static BackgroundEffect radialGradient(int innerColor, int outerColor, float pulseSpeedHz) {
        if (pulseSpeedHz == 0f) {
            return (nx, ny, dist, angle, animTick) -> mix(innerColor, outerColor, clamp01(dist));
        }
        return (nx, ny, dist, angle, animTick) -> {
            float phase = animTick / 1000f * pulseSpeedHz * (float) (2 * Math.PI);
            float scale = 0.8f + 0.2f * (0.5f + 0.5f * (float) Math.sin(phase));
            return mix(innerColor, outerColor, clamp01(dist / scale));
        };
    }

    public static BackgroundEffect ring(int color, float radius, float thickness) {
        return ring(color, radius, thickness, 0f);
    }

    public static BackgroundEffect ring(int color, float radius, float thickness, float rotationSpeedHz) {
        float half = thickness / 2f;
        return (nx, ny, dist, angle, animTick) -> {
            float d = Math.abs(dist - radius);
            float a = 1f - smoothstep(half * 0.5f, half, d);
            if (rotationSpeedHz != 0f) {
                float t = animTick / 1000f * rotationSpeedHz * (float) (2 * Math.PI);
                float sweep = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.cos(angle - t));
                a *= sweep;
            }
            return withAlphaScale(color, a);
        };
    }

    public static BackgroundEffect pulse(int innerColor, int outerColor, float speedHz) {
        return (nx, ny, dist, angle, animTick) -> {
            float phase = animTick / 1000f * speedHz * (float) (2 * Math.PI);
            float pulseAmt = 0.5f + 0.5f * (float) Math.sin(phase);
            float radius = 0.35f + pulseAmt * 0.08f;
            float core = 1f - smoothstep(0f, radius, dist);
            return withAlphaScale(mix(outerColor, innerColor, core), core);
        };
    }

    public static BackgroundEffect rotatingRays(int color, int rayCount, float speedHz, float sharpness) {
        return (nx, ny, dist, angle, animTick) -> {
            float t = animTick / 1000f * speedHz * (float) (2 * Math.PI);
            float raw = (float) Math.cos((angle + t) * rayCount);
            float rays = (float) Math.pow(Math.max(0f, raw), sharpness);
            float fade = smoothstep(1f, 0.15f, dist);
            float a = rays * fade;
            return withAlphaScale(color, a);
        };
    }

    public static BackgroundEffect colorCycle(float speedHz, int... colors) {
        if (colors.length == 0) return solid(0);
        if (colors.length == 1) return solid(colors[0]);
        return (nx, ny, dist, angle, animTick) -> {
            float t = (animTick / 1000f * speedHz) % 1f;
            if (t < 0f) t += 1f;
            float scaled = t * colors.length;
            int i = (int) Math.floor(scaled) % colors.length;
            int j = (i + 1) % colors.length;
            return mix(colors[i], colors[j], scaled - (float) Math.floor(scaled));
        };
    }

    public static BackgroundEffect glitchShear(int colorA, int colorB, float speedHz, float intensity) {
        return (nx, ny, dist, angle, animTick) -> {
            float t = animTick / 1000f * speedHz;
            float half = ny >= 0f ? 1f : 0f;
            float glitchStep = (float) Math.floor(t * 4f);
            float shift = (hash(glitchStep + half * 13f) - 0.5f) * intensity * 2f;
            float shiftedNx = nx + (half > 0.5f ? shift : -shift);
            boolean band = (Math.floorMod((int) Math.floor((shiftedNx + 1f) * 4f), 2)) == 0;
            int base = band ? colorA : colorB;

            float seamGlitch = smoothstep(0.3f, 0f, Math.abs(ny)) * (0.5f + 0.5f * (float) Math.sin(t * 20f));
            return blend(base, 0xFFFFFFFF, seamGlitch * 0.5f);
        };
    }

    public static BackgroundEffect sparkle(int color, float density, float speedHz) {
        float cells = Math.max(2f, 12f * clamp01(density));
        return (nx, ny, dist, angle, animTick) -> {
            float step = (float) Math.floor(animTick / 1000f * speedHz);
            float cx = (float) Math.floor((nx + 1f) * 0.5f * cells);
            float cy = (float) Math.floor((ny + 1f) * 0.5f * cells);
            float h = hash(cx * 13.1f + cy * 7.7f + step * 31.7f);
            float lit = h > (1f - 0.35f * density) ? (h - (1f - 0.35f * density)) / (0.35f * density) : 0f;
            return withAlphaScale(color, lit);
        };
    }

    public static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01(edge1 == edge0 ? (x < edge0 ? 0f : 1f) : (x - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    public static float hash(float n) {
        float s = (float) Math.sin(n) * 43758.5453f;
        return s - (float) Math.floor(s);
    }

    public static int mix(int colorA, int colorB, float t) {
        t = clamp01(t);
        int aa = (colorA >>> 24) & 0xFF, ab = (colorB >>> 24) & 0xFF;
        int ar = (colorA >> 16) & 0xFF, br = (colorB >> 16) & 0xFF;
        int ag = (colorA >> 8) & 0xFF, bg = (colorB >> 8) & 0xFF;
        int abl = colorA & 0xFF, bbl = colorB & 0xFF;
        int a = Math.round(aa + (ab - aa) * t);
        int r = Math.round(ar + (br - ar) * t);
        int gr = Math.round(ag + (bg - ag) * t);
        int b = Math.round(abl + (bbl - abl) * t);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    public static int withAlphaScale(int argb, float scale) {
        int a = (argb >>> 24) & 0xFF;
        int newA = Math.round(a * clamp01(scale));
        return (newA << 24) | (argb & 0x00FFFFFF);
    }

    public static int blend(int base, int overlay, float amount) {
        int mixed = mix(base, overlay, amount);
        return ((base >>> 24) << 24) | (mixed & 0x00FFFFFF);
    }
}
