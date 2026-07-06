package net.phoenixvine.chronicles.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight named-section timer for hunting render-time performance problems directly in the
 * overview screen, instead of needing an external profiler to find out which part of a huge
 * render() method is actually slow. Disabled by default (near-zero overhead when off - every
 * call is a single boolean check); toggled with Ctrl+P while the overview screen is open.
 *
 * Usage: FrameProfiler.begin("lines"); ... FrameProfiler.end("lines"); once per named section
 * per frame, then FrameProfiler.endFrame() once at the very end of render(). Sections are
 * tracked with an exponential moving average so the numbers stay readable frame-to-frame
 * instead of jittering wildly.
 */
public final class FrameProfiler {

    /** How often a snapshot gets written to latest.log while the profiler is on. */
    private static final long LOG_INTERVAL_MS = 10_000;

    private static boolean enabled = false;
    private static final Map<String, Long> startNanos = new LinkedHashMap<>();
    private static final Map<String, Long> accumNanos = new LinkedHashMap<>();
    private static final Map<String, Double> avgMs = new LinkedHashMap<>();
    /**
     * Non-timing gauges (edge count, visible node count, zoom%, ...) logged alongside timings,
     * so a jump in ms can be told apart from a jump in workload instead of guessed at.
     */
    private static final Map<String, Integer> counters = new LinkedHashMap<>();
    private static long lastLogMs = 0;

    private FrameProfiler() {}

    public static void setEnabled(boolean e) {
        enabled = e;
        if (!e) {
            startNanos.clear();
            accumNanos.clear();
            avgMs.clear();
            counters.clear();
        } else {
            lastLogMs = System.currentTimeMillis();
        }
    }

    /** Records a non-timing gauge (e.g. edge count) to report alongside the next log snapshot. */
    public static void setCounter(String name, int value) {
        if (!enabled) return;
        counters.put(name, value);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void begin(String section) {
        if (!enabled) return;
        startNanos.put(section, System.nanoTime());
    }

    public static void end(String section) {
        if (!enabled) return;
        Long start = startNanos.remove(section);
        if (start == null) return;
        accumNanos.merge(section, System.nanoTime() - start, Long::sum);
    }

    /** Call once per frame, after every begin/end pair for that frame has completed. */
    public static void endFrame() {
        if (!enabled) return;
        for (Map.Entry<String, Long> e : accumNanos.entrySet()) {
            double ms = e.getValue() / 1_000_000.0;
            avgMs.merge(e.getKey(), ms, (old, cur) -> old * 0.9 + cur * 0.1);
        }
        accumNanos.clear();

        long now = System.currentTimeMillis();
        if (now - lastLogMs >= LOG_INTERVAL_MS) {
            lastLogMs = now;
            logSnapshot();
        }
    }

    /**
     * Writes the current averages to latest.log so a session can be monitored over time
     * (e.g. across a long pan/zoom stress test) without having to keep the overlay in view.
     */
    private static void logSnapshot() {
        StringBuilder sb = new StringBuilder("[FrameProfiler] ");
        boolean first = true;
        for (Map.Entry<String, Integer> e : counters.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        for (Map.Entry<String, Double> e : sortedSections()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(e.getKey()).append('=').append(String.format("%.2fms", e.getValue()));
        }
        net.phoenixvine.chronicles.PhoenixChronicles.LOGGER.info(sb.toString());
    }

    /** Sections sorted by average ms/frame, most expensive first. */
    public static List<Map.Entry<String, Double>> sortedSections() {
        List<Map.Entry<String, Double>> list = new ArrayList<>(avgMs.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list;
    }
}
