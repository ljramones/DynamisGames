package org.dynamisengine.games.debugremote;

import org.dynamisengine.ui.debug.builder.DebugViewSnapshot;
import org.dynamisengine.ui.debug.export.DebugSnapshotJson;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP client that receives length-prefixed JSON snapshots from a
 * {@code TcpExporter} running in the engine process.
 *
 * <p>Runs a background thread that reads frames and stores them in a
 * thread-safe ring buffer. The rendering thread polls for the latest
 * snapshot without blocking.
 */
final class TcpSnapshotClient {

    private static final int MAX_HISTORY = 300;

    private final String host;
    private final int port;
    private final ArrayDeque<DebugViewSnapshot> history = new ArrayDeque<>(MAX_HISTORY);
    private volatile DebugViewSnapshot latest = DebugViewSnapshot.EMPTY;
    private volatile boolean connected;
    private volatile int receivedCount;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread readerThread;

    TcpSnapshotClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    void start() {
        running.set(true);
        readerThread = Thread.ofVirtual().name("debug-tcp-reader").start(this::readerLoop);
    }

    void stop() {
        running.set(false);
        if (readerThread != null) readerThread.interrupt();
    }

    /** Get the most recent snapshot (never null). */
    DebugViewSnapshot latest() { return latest; }

    /** Get a copy of the history buffer (oldest first). */
    synchronized List<DebugViewSnapshot> history() {
        return new ArrayList<>(history);
    }

    /** Get a snapshot by index into history (0 = oldest). */
    synchronized DebugViewSnapshot frame(int index) {
        if (index < 0 || index >= history.size()) return DebugViewSnapshot.EMPTY;
        int i = 0;
        for (var s : history) {
            if (i == index) return s;
            i++;
        }
        return DebugViewSnapshot.EMPTY;
    }

    synchronized int historySize() { return history.size(); }
    boolean isConnected() { return connected; }
    int receivedCount() { return receivedCount; }

    private void readerLoop() {
        while (running.get()) {
            try {
                System.out.println("[RemoteViewer] Connecting to " + host + ":" + port + "...");
                try (var socket = new Socket(host, port);
                     var dis = new DataInputStream(socket.getInputStream())) {

                    connected = true;
                    System.out.println("[RemoteViewer] Connected!");

                    while (running.get() && !socket.isClosed()) {
                        int length = dis.readInt();
                        if (length <= 0 || length > 10_000_000) {
                            System.err.println("[RemoteViewer] Invalid frame length: " + length);
                            break;
                        }
                        byte[] payload = new byte[length];
                        dis.readFully(payload);

                        String json = new String(payload, StandardCharsets.UTF_8);
                        try {
                            var snapshot = DebugSnapshotJson.fromJson(json);
                            synchronized (this) {
                                if (history.size() >= MAX_HISTORY) history.removeFirst();
                                history.addLast(snapshot);
                            }
                            latest = snapshot;
                            receivedCount++;
                        } catch (Exception e) {
                            System.err.println("[RemoteViewer] Parse error: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                connected = false;
                if (running.get()) {
                    System.out.println("[RemoteViewer] Disconnected: " + e.getMessage() + ". Retrying in 2s...");
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { break; }
                }
            }
        }
        connected = false;
    }
}
