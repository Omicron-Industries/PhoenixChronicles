package net.phoenixvine.chronicles.client.render.background;

@FunctionalInterface
public interface BackgroundEffect {

    int colorAt(float nx, float ny, float dist, float angle, long animTick);
}
