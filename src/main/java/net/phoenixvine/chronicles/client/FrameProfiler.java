package net.phoenixvine.chronicles.client;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
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
 * tracked two ways: an exponential moving average (so the numbers stay readable frame-to-frame
 * instead of jittering wildly) AND a worst-case max since the last log snapshot, since the EMA
 * alone smooths away exactly the kind of single-frame stutter spike a "why did it hitch just
 * now" investigation actually needs to see.
 */
public final class FrameProfiler {

    /** How often a snapshot gets written to latest.log while the profiler is on. */
    private static final long LOG_INTERVAL_MS = 10_000;

    private static boolean enabled = false;
    private static final Map<String, Long> startNanos = new LinkedHashMap<>();
    private static final Map<String, Long> accumNanos = new LinkedHashMap<>();
    private static final Map<String, Double> avgMs = new LinkedHashMap<>();
    /** Worst single-frame value seen for each section since the last log snapshot. */
    private static final Map<String, Double> maxMs = new LinkedHashMap<>();
    /**
     * Non-timing gauges (edge count, visible node count, zoom%, ...) logged alongside timings,
     * so a jump in ms can be told apart from a jump in workload instead of guessed at.
     */
    private static final Map<String, Integer> counters = new LinkedHashMap<>();
    private static long lastLogMs = 0;

    // ── GC + wall-clock gap tracking ──────────────────────────────────────────
    //
    // Named sections only measure the CPU time actually spent inside our own begin/end pairs.
    // Two real costs live entirely outside that: a GC pause can land in the gap BETWEEN two
    // sections (or between endFrame() calls), and anything the game loop does outside render()
    // entirely (other mods, tick work, vsync wait) never touches our sections at all. Both would
    // otherwise look identical to "our code got slower" in the log. Tracking them explicitly
    // turns "is node:icon/widgets really the bottleneck, or is it something sneaky" from a guess
    // into a direct read: if gcTimeMs or the wall-clock gap spikes on the same frame TOTAL
    // render() does, the named sections aren't the story.
    private static final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    private static long lastGcCount = 0;
    private static long lastGcTimeMs = 0;
    private static long gcCountThisWindow = 0;
    private static long gcTimeMsThisWindow = 0;
    private static long lastFrameEndNanos = 0;
    private static double wallClockGapAvgMs = 0;
    private static double wallClockGapMaxMs = 0;

    private FrameProfiler() {}

    public static void setEnabled(boolean e) {
        enabled = e;
        if (!e) {
            startNanos.clear();
            accumNanos.clear();
            avgMs.clear();
            maxMs.clear();
            counters.clear();
        } else {
            lastLogMs = System.currentTimeMillis();
            lastFrameEndNanos = 0;
            wallClockGapAvgMs = 0;
            wallClockGapMaxMs = 0;
            gcCountThisWindow = 0;
            gcTimeMsThisWindow = 0;
            long[] totals = currentGcTotals();
            lastGcCount = totals[0];
            lastGcTimeMs = totals[1];
        }
    }

    private static long[] currentGcTotals() {
        long count = 0, timeMs = 0;
        for (GarbageCollectorMXBean bean : gcBeans) {
            long c = bean.getCollectionCount();
            long t = bean.getCollectionTime();
            if (c > 0) count += c;
            if (t > 0) timeMs += t;
        }
        return new long[] { count, timeMs };
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
            maxMs.merge(e.getKey(), ms, Math::max);
        }
        accumNanos.clear();

        // Wall-clock time between one endFrame() and the next - includes anything TOTAL
        // render()'s own begin/end pair doesn't cover (a GC pause landing between frames, other
        // mods/game-loop work, vsync wait). Compared side-by-side with TOTAL render() in the log,
        // a frame where this gap is large but TOTAL render() isn't tells you the hitch wasn't in
        // our code at all.
        long nowNanos = System.nanoTime();
        if (lastFrameEndNanos != 0) {
            double gapMs = (nowNanos - lastFrameEndNanos) / 1_000_000.0;
            wallClockGapAvgMs = wallClockGapAvgMs * 0.9 + gapMs * 0.1;
            wallClockGapMaxMs = Math.max(wallClockGapMaxMs, gapMs);
        }
        lastFrameEndNanos = nowNanos;

        long[] totals = currentGcTotals();
        gcCountThisWindow += Math.max(0, totals[0] - lastGcCount);
        gcTimeMsThisWindow += Math.max(0, totals[1] - lastGcTimeMs);
        lastGcCount = totals[0];
        lastGcTimeMs = totals[1];

        long now = System.currentTimeMillis();
        if (now - lastLogMs >= LOG_INTERVAL_MS) {
            lastLogMs = now;
            logSnapshot();
            maxMs.clear(); // start the next window's worst-case tracking fresh
            wallClockGapMaxMs = 0;
            gcCountThisWindow = 0;
            gcTimeMsThisWindow = 0;
        }
    }

    /**
     * Forces an immediate snapshot regardless of the normal 10s interval - for "something felt
     * laggy just now, capture it before the EMA smooths it away" investigation, bound to a
     * keypress rather than waiting out the timer.
     */
    public static void logNow() {
        if (!enabled) return;
        logSnapshot();
        maxMs.clear();
        wallClockGapMaxMs = 0;
        gcCountThisWindow = 0;
        gcTimeMsThisWindow = 0;
        lastLogMs = System.currentTimeMillis();
    }

    /**
     * Writes the current averages (plus this window's worst-case per section, and JVM heap
     * usage) to latest.log so a session can be monitored over time (e.g. across a long pan/zoom
     * stress test) without having to keep the overlay in view. Heap numbers are here specifically
     * so a future native-OOM-style crash log can be cross-referenced against what the mod's own
     * workload looked like in the run-up to it, instead of that being a total blind spot.
     */
    private static void logSnapshot() {
        StringBuilder sb = new StringBuilder("[FrameProfiler] ");
        boolean first = true;

        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        sb.append("heapMB=").append(usedMb).append('/').append(maxMb);
        first = false;

        sb.append(", gcCount=").append(gcCountThisWindow);
        sb.append(", gcTimeMs=").append(gcTimeMsThisWindow);
        sb.append(", wallClockGap=").append(String.format("%.2fms (max %.2fms)", wallClockGapAvgMs, wallClockGapMaxMs));

        for (Map.Entry<String, Integer> e : counters.entrySet()) {
            sb.append(", ");
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        for (Map.Entry<String, Double> e : sortedSections()) {
            sb.append(", ");
            double worst = maxMs.getOrDefault(e.getKey(), e.getValue());
            sb.append(e.getKey()).append('=').append(String.format("%.2fms (max %.2fms)", e.getValue(), worst));
        }
        net.phoenixvine.chronicles.PhoenixChronicles.LOGGER.info(sb.toString());
    }

    /** Sections sorted by average ms/frame, most expensive first. */
    public static List<Map.Entry<String, Double>> sortedSections() {
        List<Map.Entry<String, Double>> list = new ArrayList<>(avgMs.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list;
    }

    /** Worst single-frame value seen for a section since the last log snapshot, or the average if unseen. */
    public static double maxMsFor(String section) {
        return maxMs.getOrDefault(section, avgMs.getOrDefault(section, 0.0));
    }
}
