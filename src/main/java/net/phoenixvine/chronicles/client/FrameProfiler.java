package net.phoenixvine.chronicles.client;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FrameProfiler {

    private static final long LOG_INTERVAL_MS = 10_000;

    private static boolean enabled = false;
    private static final Map<String, Long> startNanos = new LinkedHashMap<>();
    private static final Map<String, Long> accumNanos = new LinkedHashMap<>();
    private static final Map<String, Double> avgMs = new LinkedHashMap<>();

    private static final Map<String, Double> maxMs = new LinkedHashMap<>();

    private static final Map<String, Integer> counters = new LinkedHashMap<>();
    private static long lastLogMs = 0;

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

    public static void endFrame() {
        if (!enabled) return;
        for (Map.Entry<String, Long> e : accumNanos.entrySet()) {
            double ms = e.getValue() / 1_000_000.0;
            avgMs.merge(e.getKey(), ms, (old, cur) -> old * 0.9 + cur * 0.1);
            maxMs.merge(e.getKey(), ms, Math::max);
        }
        accumNanos.clear();

        long nowNanos = System.nanoTime();
        double gapMs = 0;
        if (lastFrameEndNanos != 0) {
            gapMs = (nowNanos - lastFrameEndNanos) / 1_000_000.0;
            wallClockGapAvgMs = wallClockGapAvgMs * 0.9 + gapMs * 0.1;
            wallClockGapMaxMs = Math.max(wallClockGapMaxMs, gapMs);
        }
        lastFrameEndNanos = nowNanos;

        if (ProfilerSession.isActive()) {
            ProfilerSession.onFrame(gapMs);
        }

        long[] totals = currentGcTotals();
        gcCountThisWindow += Math.max(0, totals[0] - lastGcCount);
        gcTimeMsThisWindow += Math.max(0, totals[1] - lastGcTimeMs);
        lastGcCount = totals[0];
        lastGcTimeMs = totals[1];

        long now = System.currentTimeMillis();
        if (now - lastLogMs >= LOG_INTERVAL_MS) {
            lastLogMs = now;
            logSnapshot();
            maxMs.clear();
            wallClockGapMaxMs = 0;
            gcCountThisWindow = 0;
            gcTimeMsThisWindow = 0;
        }
    }

    public static void logNow() {
        if (!enabled) return;
        logSnapshot();
        maxMs.clear();
        wallClockGapMaxMs = 0;
        gcCountThisWindow = 0;
        gcTimeMsThisWindow = 0;
        lastLogMs = System.currentTimeMillis();
    }

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

    public static List<Map.Entry<String, Double>> sortedSections() {
        List<Map.Entry<String, Double>> list = new ArrayList<>(avgMs.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list;
    }

    public static double maxMsFor(String section) {
        return maxMs.getOrDefault(section, avgMs.getOrDefault(section, 0.0));
    }
}
