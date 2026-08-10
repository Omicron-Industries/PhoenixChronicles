package net.phoenixvine.chronicles.client.screen;

public interface TogglePanel extends OverlayComponent {

    boolean isOpen();

    void close();

    @Override
    default boolean isVisible(ScreenContext ctx) {
        return isOpen();
    }
}
