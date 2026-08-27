package net.phoenixvine.chronicles.client.profiler;

import net.phoenixvine.chronicles.PhoenixChronicles;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public final class ProfilerSession {

    private static final long DURATION_MS = 3 * 60 * 1000L;
    private static final long DETAIL_INTERVAL_MS = 1000L;
    private static final long FLUSH_INTERVAL_MS = 1000L;

    private static volatile ProfilerSession active = null;

    private final long startNanos = System.nanoTime();
    private final long startMs = System.currentTimeMillis();
    private final Path logFile;
    private BufferedWriter writer;
    private final boolean priorFrameProfilerEnabled;

    private long frameCount = 0;
    private double slowestFrameMs = 0;
    private long lastDetailMs;
    private long lastFlushMs;
    private int keyPressCount = 0;

    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    private long lastGcCount;
    private long lastGcTimeMs;
    private long totalGcCount = 0;
    private long totalGcTimeMs = 0;

    private ProfilerSession(Path logFile, boolean priorFrameProfilerEnabled) {
        this.logFile = logFile;
        this.priorFrameProfilerEnabled = priorFrameProfilerEnabled;
        this.lastDetailMs = startMs;
        this.lastFlushMs = startMs;
        long[] totals = gcTotals();
        this.lastGcCount = totals[0];
        this.lastGcTimeMs = totals[1];
    }

    public static boolean isActive() {
        return active != null;
    }

    public static String toggle() {
        if (active != null) {
            return stop("manual toggle");
        }
        return start();
    }

    private static String start() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path dir = Paths.get("config", "phoenix_chronicles", "profiler_sessions", stamp);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            PhoenixChronicles.LOGGER.error("[ProfilerSession] Failed to create session directory", e);
            return "§cProfiler failed to start (see log)";
        }
        Path file = dir.resolve("session.log");
        boolean priorEnabled = FrameProfiler.isEnabled();
        ProfilerSession session = new ProfilerSession(file, priorEnabled);
        try {
            session.writer = Files.newBufferedWriter(file);
            session.writer.write("[ProfilerSession] started " + LocalDateTime.now());
            session.writer.newLine();
        } catch (IOException e) {
            PhoenixChronicles.LOGGER.error("[ProfilerSession] Failed to open session log", e);
            return "§cProfiler failed to start (see log)";
        }
        FrameProfiler.setEnabled(true);
        active = session;
        return "§aProfiler session started -> " + file;
    }

    private static String stop(String reason) {
        ProfilerSession session = active;
        if (session == null) return "§7No active profiler session";
        active = null;
        session.writeSummary(reason);
        session.close();
        if (!session.priorFrameProfilerEnabled) {
            FrameProfiler.setEnabled(false);
        }
        return "§aProfiler session saved -> " + session.logFile;
    }

    private long[] gcTotals() {
        long count = 0, timeMs = 0;
        for (GarbageCollectorMXBean bean : gcBeans) {
            long c = bean.getCollectionCount();
            long t = bean.getCollectionTime();
            if (c > 0) count += c;
            if (t > 0) timeMs += t;
        }
        return new long[] { count, timeMs };
    }

    static void onFrame(double frameMs) {
        ProfilerSession session = active;
        if (session == null) return;
        session.recordFrame(frameMs);
    }

    private void recordFrame(double frameMs) {
        frameCount++;
        slowestFrameMs = Math.max(slowestFrameMs, frameMs);

        long[] totals = gcTotals();
        long gcDeltaCount = Math.max(0, totals[0] - lastGcCount);
        long gcDeltaMs = Math.max(0, totals[1] - lastGcTimeMs);
        if (gcDeltaCount > 0) {
            totalGcCount += gcDeltaCount;
            totalGcTimeMs += gcDeltaMs;
            writeLine(String.format("[gc] count=%d timeMs=%d", gcDeltaCount, gcDeltaMs));
        }
        lastGcCount = totals[0];
        lastGcTimeMs = totals[1];

        long now = System.currentTimeMillis();
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        writeLine(String.format("[frame] t=%d frameMs=%.2f heapMB=%d", now - startMs, frameMs, usedMb));

        if (now - lastDetailMs >= DETAIL_INTERVAL_MS) {
            lastDetailMs = now;
            writeDetailLine(now);
        }

        if (now - lastFlushMs >= FLUSH_INTERVAL_MS) {
            lastFlushMs = now;
            flush();
        }

        if (now - startMs >= DURATION_MS) {
            String msg = stop("3 minute limit reached");
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(msg), true);
            }
        }
    }

    private void writeDetailLine(long now) {
        StringBuilder sb = new StringBuilder("[detail] t=").append(now - startMs);
        for (Map.Entry<String, Double> e : FrameProfiler.sortedSections()) {
            sb.append(", ").append(e.getKey()).append('=')
                    .append(String.format("%.2fms (max %.2fms)", e.getValue(), FrameProfiler.maxMsFor(e.getKey())));
        }
        writeLine(sb.toString());
    }

    public static void logKeyEvent(int key, int mods, String screenName) {
        ProfilerSession session = active;
        if (session == null) return;
        session.keyPressCount++;
        long now = System.currentTimeMillis();
        session.writeLine(String.format("[key] t=%d key=%d mods=%d screen=%s",
                now - session.startMs, key, mods, screenName));
    }

    private void writeLine(String line) {
        if (writer == null) return;
        try {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            PhoenixChronicles.LOGGER.error("[ProfilerSession] Write failed", e);
        }
    }

    private void writeSummary(String reason) {
        long elapsedMs = System.currentTimeMillis() - startMs;
        writeLine("");
        writeLine("[summary] stopped: " + reason);
        writeLine(String.format(
                "[summary] durationMs=%d frames=%d slowestFrameMs=%.2f keyPresses=%d gcCount=%d gcTimeMs=%d",
                elapsedMs, frameCount, slowestFrameMs, keyPressCount, totalGcCount, totalGcTimeMs));
        List<Map.Entry<String, Double>> sections = FrameProfiler.sortedSections();
        int top = Math.min(10, sections.size());
        for (int i = 0; i < top; i++) {
            Map.Entry<String, Double> e = sections.get(i);
            writeLine(String.format("[summary] section=%s avgMs=%.2f maxMs=%.2f", e.getKey(), e.getValue(),
                    FrameProfiler.maxMsFor(e.getKey())));
        }
    }

    private void close() {
        flush();
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException e) {
            PhoenixChronicles.LOGGER.error("[ProfilerSession] Close failed", e);
        } finally {
            writer = null;
        }
    }

    private void flush() {
        if (writer == null) return;
        try {
            writer.flush();
        } catch (IOException e) {
            PhoenixChronicles.LOGGER.error("[ProfilerSession] Flush failed", e);
        }
    }
}
