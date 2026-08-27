package net.phoenixvine.chronicles.client.screen.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.client.event.ChronicleKeyBindings;

import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.client.screen.widgets.SidebarPanel;
import net.phoenixvine.chronicles.client.screen.widgets.ToolbarPanel;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ScreenClickSmokeTest {

    private ScreenClickSmokeTest() {}

    private record Result(String label, boolean passed, Throwable error) {}

    public static void run(ChronicleOverviewScreen screen) {
        List<Result> results = new ArrayList<>();

        String origChapter = screen.selectedChapter();
        String origStateFilter = screen.stateFilter;

        probeToolbarButtons(screen, results);
        probeFilterPills(screen, results, origStateFilter);
        probeHeaderBar(screen, results);
        probeSidebar(screen, results, origChapter);
        probeMinimap(screen, results);
        probeCanvasNodes(screen, results);
        probeCanvasEmptyArea(screen, results);
        probeKeyBattery(screen, results);

        try {
            screen.setSelectedChapter(origChapter);
            screen.stateFilter = origStateFilter;
        } catch (Throwable ignored) {

        }
        screen.rebuild();

        report(screen, results);
    }

    private static void probeToolbarButtons(ChronicleOverviewScreen screen, List<Result> results) {
        probe(results, "toolbar: fit-to-canvas", () -> clickToolbarButton(screen, "fit"));

        probe(results, "toolbar: hide-done toggle (x2, restores)", () -> {
            clickToolbarButton(screen, "hideDone");
            clickToolbarButton(screen, "hideDone");
        });

        probe(results, "toolbar: minimap toggle (x2, restores)", () -> {
            clickToolbarButton(screen, "map");
            clickToolbarButton(screen, "map");
        });

        probe(results, "toolbar: settings screen (open + back)", () -> {
            clickToolbarButton(screen, "settings");
            returnToScreen(screen);
        });

        if (screen.isDevMode()) {
            probe(results, "toolbar: wiki screen (open + back)", () -> {
                clickToolbarButton(screen, "wiki");
                returnToScreen(screen);
            });
        }
    }

    private static void clickToolbarButton(ChronicleOverviewScreen screen, String key) {
        int[] pt = findToolbarButtonCenter(screen, key);
        if (pt == null) throw new IllegalStateException("Toolbar button not found/visible: " + key);
        screen.mouseClicked(pt[0], pt[1], 0);
    }

    private static int[] findToolbarButtonCenter(ChronicleOverviewScreen screen, String key) {
        ToolbarPanel tp = screen.toolbarPanelInstance();
        int my = ChronicleOverviewScreen.TOOLBAR_Y + ChronicleOverviewScreen.TOOLBAR_H / 2;
        int width = screen.width();
        int start = -1, end = -1;
        for (int x = 0; x < width; x++) {
            if (tp.hits(key, x, my)) {
                if (start == -1) start = x;
                end = x;
            } else if (start != -1) {
                break;
            }
        }
        if (start == -1) return null;
        return new int[] { (start + end) / 2, my };
    }

    private static void probeFilterPills(ChronicleOverviewScreen screen, List<Result> results,
                                         String originalFilter) {
        probe(results, "filter pills: click through all, then restore", () -> {
            ToolbarPanel tp = screen.toolbarPanelInstance();
            int cl = screen.sidebarW();
            int cr = screen.width();
            int[][] pills = tp.filterPillBounds(cl, cr, ChronicleOverviewScreen.TOOLBAR_Y,
                    ChronicleOverviewScreen.TOOLBAR_H, screen.font(), screen.isDevMode());
            for (int i = 0; i < tp.filterKeyCount(); i++) {
                int[] b = pills[i];
                if (b[2] <= b[0]) continue;
                int cx = (b[0] + b[2]) / 2;
                int cy = (b[1] + b[3]) / 2;
                screen.mouseClicked(cx, cy, 0);
            }

            screen.stateFilter = originalFilter;
            screen.rebuild();
        });
    }

    private static void probeHeaderBar(ChronicleOverviewScreen screen, List<Result> results) {
        probe(results, "header: grid-snap-cycle click", () -> {
            int[] gridBtn = screen.computeHeaderBarLayout(screen.width())[1];
            clickRectCenter(screen, gridBtn);
        });

        if (screen.isDevMode()) {
            probe(results, "header: subgraph-mode toggle (x2, restores)", () -> {
                int[] subgraphBtn = screen.computeHeaderBarLayout(screen.width())[2];
                if (subgraphBtn == null) {
                    throw new IllegalStateException("Subgraph header button unexpectedly absent in dev mode");
                }
                clickRectCenter(screen, subgraphBtn);

                int[] subgraphBtn2 = screen.computeHeaderBarLayout(screen.width())[2];
                clickRectCenter(screen, subgraphBtn2);
            });
        }

        if (screen.unclaimedRewardCount() > 0) {
            probe(results, "header: claim-rewards (open + back)", () -> {
                int[] claimBtn = screen.computeHeaderBarLayout(screen.width())[0];
                clickRectCenter(screen, claimBtn);
                returnToScreen(screen);
            });
        }
    }

    private static void clickRectCenter(ChronicleOverviewScreen screen, int[] rect) {
        int cx = (rect[0] + rect[2]) / 2;
        int cy = (rect[1] + rect[3]) / 2;
        screen.mouseClicked(cx, cy, 0);
    }

    private static void probeSidebar(ChronicleOverviewScreen screen, List<Result> results, String origChapter) {
        probe(results, "sidebar: collapse toggle (x2, restores)", () -> {
            SidebarPanel sp = screen.sidebarPanelInstance();
            boolean origCollapsed = sp.collapsed();
            clickSidebarCollapseToggle(screen);
            clickSidebarCollapseToggle(screen);

            sp.setCollapsed(origCollapsed);
        });

        probe(results, "sidebar: row navigation (chapter clicks, restores selectedChapter)", () -> {
            int clicked = 0;
            for (SidebarRow row : screen.buildSidebarRows()) {
                if (row.isFolder() || row.locked()) continue;
                int x = Math.min(Math.max(4, screen.sidebarW() - 8), 20);
                int y = row.y() + row.height() / 2;
                screen.mouseClicked(x, y, 0);
                if (++clicked >= 3) break;
            }
            screen.setSelectedChapter(origChapter);
            screen.rebuild();
        });
    }

    private static void clickSidebarCollapseToggle(ChronicleOverviewScreen screen) {
        int x = Math.max(2, screen.sidebarW() / 2);
        int y = ChronicleOverviewScreen.HEADER_H + 1 + SidebarPanel.SIDEBAR_COLLAPSE_TOGGLE_H / 2;
        screen.mouseClicked(x, y, 0);
    }

    private static void probeMinimap(ChronicleOverviewScreen screen, List<Result> results) {
        probe(results, "minimap: open, click inside, close", () -> {
            boolean wasOpen = screen.minimapOpen();
            if (!wasOpen) clickToolbarButton(screen, "map");

            int[] b = screen.minimapBounds(screen.width());
            int cx = (b[0] + b[2]) / 2;
            int cy = (b[1] + b[3]) / 2;
            screen.mouseClicked(cx, cy, 0);
            screen.mouseReleased(cx, cy, 0);

            if (!wasOpen) clickToolbarButton(screen, "map");
        });
    }

    private static void probeCanvasNodes(ChronicleOverviewScreen screen, List<Result> results) {
        List<Map.Entry<ResourceLocation, ChronicleOverviewScreen.NodeHitbox>> visible = new ArrayList<>();
        for (Map.Entry<ResourceLocation, ChronicleOverviewScreen.NodeHitbox> e : screen.nodeButtons().entrySet()) {
            if (e.getValue().visible) {
                visible.add(e);
                if (visible.size() >= 5) break;
            }
        }

        int idx = 0;
        for (Map.Entry<ResourceLocation, ChronicleOverviewScreen.NodeHitbox> e : visible) {
            idx++;
            int n = idx;
            ChronicleOverviewScreen.NodeHitbox hb = e.getValue();
            int cx = hb.x + hb.w / 2;
            int cy = hb.y + hb.h / 2;
            ResourceLocation nodeId = e.getKey();

            probe(results, "canvas node[" + n + "] (" + nodeId + "): left-click open", () -> {
                screen.mouseClicked(cx, cy, 0);
                returnToScreen(screen);
            });

            probe(results, "canvas node[" + n + "] (" + nodeId + "): multi-select toggle (x2, restores)", () -> {
                Set<ResourceLocation> multiSel = screen.editorStateInstance().multiSelection;
                boolean origSelected = multiSel.contains(nodeId);
                toggleMultiSelect(multiSel, nodeId);
                toggleMultiSelect(multiSel, nodeId);
                if (multiSel.contains(nodeId) != origSelected) {
                    throw new IllegalStateException("Multi-select toggle left node in wrong state: " + nodeId);
                }
            });

            probe(results, "canvas node[" + n + "] (" + nodeId + "): right-click menu + Escape (no item clicked)",
                    () -> {
                        screen.mouseClicked(cx, cy, 1);
                        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                        if (screen.ctxOpen()) {
                            throw new IllegalStateException("Node context menu still open after Escape: " + nodeId);
                        }
                    });
        }
    }

    private static void toggleMultiSelect(Set<ResourceLocation> multiSelection, ResourceLocation id) {
        if (multiSelection.contains(id)) multiSelection.remove(id);
        else multiSelection.add(id);
    }

    private static void probeCanvasEmptyArea(ChronicleOverviewScreen screen, List<Result> results) {
        int[] pt = findEmptyCanvasPoint(screen);
        if (pt == null) {
            results.add(new Result("canvas empty-area: right-click menu + Escape (skipped, no empty spot found)",
                    true, null));
        } else {
            int ex = pt[0], ey = pt[1];
            probe(results, "canvas empty-area: right-click menu + Escape (no item clicked)", () -> {
                screen.mouseClicked(ex, ey, 1);
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                if (screen.ctxOpen()) {
                    throw new IllegalStateException("Empty-area context menu still open after Escape");
                }
            });
        }

        probe(results, "canvas empty-area: pan (click + small drag + release)", () -> {
            int[] p2 = findEmptyCanvasPoint(screen);
            int px = p2 != null ? p2[0] : (screen.sidebarW() + 40);
            int py = p2 != null ? p2[1] : (ChronicleOverviewScreen.HEADER_H + 40);
            screen.mouseClicked(px, py, 0);
            screen.mouseDragged(px + 4, py + 3, 0, 4, 3);
            screen.mouseReleased(px + 4, py + 3, 0);
        });
    }

    private static int[] findEmptyCanvasPoint(ChronicleOverviewScreen screen) {
        int cl = screen.sidebarW();
        int cr = screen.width();
        int top = ChronicleOverviewScreen.HEADER_H + 5;
        int bottom = screen.height() - 5;
        for (int y = top; y < bottom; y += 17) {
            for (int x = cl + 5; x < cr - 5; x += 23) {
                if (!coveredByNode(screen, x, y)) return new int[] { x, y };
            }
        }
        return null;
    }

    private static boolean coveredByNode(ChronicleOverviewScreen screen, int x, int y) {
        for (ChronicleOverviewScreen.NodeHitbox hb : screen.nodeButtons().values()) {
            if (hb.visible && hb.x <= x && x < hb.x + hb.w && hb.y <= y && y < hb.y + hb.h) return true;
        }
        return false;
    }

    private static void probeKeyBattery(ChronicleOverviewScreen screen, List<Result> results) {
        int ctrlMods = GLFW.GLFW_MOD_CONTROL;

        probe(results, "key: fit-to-canvas", () -> pressKeybind(screen, ChronicleKeyBindings.FIT_TO_CANVAS));

        probe(results, "key: toggle-minimap (x2, restores)", () -> {
            boolean wasOpen = screen.minimapOpen();
            pressKeybind(screen, ChronicleKeyBindings.TOGGLE_MINIMAP);
            pressKeybind(screen, ChronicleKeyBindings.TOGGLE_MINIMAP);
            if (screen.minimapOpen() != wasOpen) {
                throw new IllegalStateException("Minimap open state not restored after double toggle");
            }
        });

        probe(results, "key: toggle-subgraph (x2, restores)", () -> {
            pressKeybind(screen, ChronicleKeyBindings.TOGGLE_SUBGRAPH);
            pressKeybind(screen, ChronicleKeyBindings.TOGGLE_SUBGRAPH);
        });

        if (screen.isDevMode()) {
            probe(results, "key: toggle-stats (x2, restores)", () -> {
                pressKeybind(screen, ChronicleKeyBindings.TOGGLE_STATS);
                pressKeybind(screen, ChronicleKeyBindings.TOGGLE_STATS);
            });

            probe(results, "key: toggle-validation (x2, restores)", () -> {
                pressKeybind(screen, ChronicleKeyBindings.TOGGLE_VALIDATION);
                pressKeybind(screen, ChronicleKeyBindings.TOGGLE_VALIDATION);
            });
        }

        probe(results, "key: Escape x3 with nothing open (falls through safely)", () -> {
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        });

        probe(results, "key: Ctrl+Z (undo, possibly empty stack)",
                () -> screen.keyPressed(GLFW.GLFW_KEY_Z, 0, ctrlMods));

        probe(results, "key: Ctrl+Y (redo, possibly empty stack)",
                () -> screen.keyPressed(GLFW.GLFW_KEY_Y, 0, ctrlMods));
    }

    private static void pressKeybind(ChronicleOverviewScreen screen, net.minecraft.client.KeyMapping mapping) {
        screen.keyPressed(mapping.getKey().getValue(), 0, 0);
    }

    private interface Probe {

        void run() throws Exception;
    }

    private static void probe(List<Result> results, String label, Probe action) {
        try {
            action.run();
            results.add(new Result(label, true, null));
        } catch (Throwable t) {
            results.add(new Result(label, false, t));
        }
    }

    private static void returnToScreen(ChronicleOverviewScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != screen) {
            mc.setScreen(screen);
        }
    }

    private static void report(ChronicleOverviewScreen screen, List<Result> results) {
        int total = results.size();
        int passCount = 0;
        List<String> failureLabels = new ArrayList<>();

        for (Result r : results) {
            if (r.passed()) {
                passCount++;
                PhoenixChronicles.LOGGER.info("Smoke test PASS: {}", r.label());
            } else {
                failureLabels.add(r.label());
                PhoenixChronicles.LOGGER.error("Smoke test FAIL: {}", r.label(), r.error());
            }
        }

        if (passCount == total) {
            PhoenixChronicles.LOGGER.info("PHOENIX_SMOKE_TEST_RESULT: PASS ({} / {})", passCount, total);
            screen.setFeedback("§aSmoke test: %d/%d passed", passCount, total);
        } else {
            String failureSummary = String.join(", ", failureLabels);
            PhoenixChronicles.LOGGER.error("PHOENIX_SMOKE_TEST_RESULT: FAIL ({} / {} passed) - failures: {}",
                    passCount, total, failureSummary);
            screen.setFeedback("§cSmoke test: %d/%d FAILED: see log", total - passCount, total);
        }
    }
}
