package org.medicmobile.webapp.mobile.p2p;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Ensures only one sync operation (P2P or server) runs at a time.
 *
 * Guards:
 *   G10 — Only one sync active at a time
 *   G11 — Server sync has priority over P2P
 *   G12 — Session timeout after 30 min idle
 *
 * States: IDLE -> SERVER_SYNC, IDLE -> P2P_SYNC
 * If P2P is active and server becomes reachable, P2P finishes first then yields.
 */
public class SyncMutex {

    public enum SyncType { SERVER, P2P }

    private static final int DEFAULT_IDLE_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

    private final AtomicReference<SyncType> activeSyncType = new AtomicReference<>(null);
    private volatile long lastActivityAt;
    private final ServerReachabilityChecker reachabilityChecker;

    /**
     * Callback interface for checking server connectivity.
     * Implementations should perform a lightweight check (e.g. HEAD request).
     */
    public interface ServerReachabilityChecker {
        boolean isServerReachable();
    }

    public SyncMutex(ServerReachabilityChecker reachabilityChecker) {
        this.reachabilityChecker = reachabilityChecker;
        this.lastActivityAt = 0;
    }

    /**
     * Try to acquire the sync lock for the given type.
     * Returns true if acquired, false if another sync is already active.
     *
     * G10: only one sync active at a time.
     */
    public synchronized boolean tryAcquire(SyncType type) {
        if (type == null) {
            throw new IllegalArgumentException("SyncType must not be null");
        }

        // If a timed-out sync is lingering, force-release it (G12)
        if (isTimedOut()) {
            activeSyncType.set(null);
        }

        if (activeSyncType.get() != null) {
            return false;
        }

        activeSyncType.set(type);
        lastActivityAt = System.currentTimeMillis();
        return true;
    }

    /**
     * Release the sync lock.
     */
    public synchronized void release() {
        activeSyncType.set(null);
        lastActivityAt = 0;
    }

    /**
     * Check if any sync is currently active.
     */
    public synchronized boolean isActive() {
        if (isTimedOut()) {
            activeSyncType.set(null);
            lastActivityAt = 0;
            return false;
        }
        return activeSyncType.get() != null;
    }

    /**
     * Get current sync type, or null if idle.
     */
    public synchronized SyncType getCurrentType() {
        if (isTimedOut()) {
            activeSyncType.set(null);
            lastActivityAt = 0;
            return null;
        }
        return activeSyncType.get();
    }

    /**
     * G11: Server sync has priority. Returns true if the current P2P session
     * should yield to server sync after completing its current operation.
     *
     * This does NOT interrupt the active P2P sync. The caller should:
     * 1. Finish the current doc batch
     * 2. Complete the P2P session gracefully
     * 3. Release the mutex
     * 4. Allow server sync to proceed
     */
    public boolean shouldYieldToServer() {
        if (activeSyncType.get() != SyncType.P2P) {
            return false;
        }
        return reachabilityChecker != null && reachabilityChecker.isServerReachable();
    }

    /**
     * Record activity to prevent G12 timeout during long-running syncs.
     * Call this periodically during sync (e.g. after each batch).
     */
    public void touchActivity() {
        lastActivityAt = System.currentTimeMillis();
    }

    /**
     * G12: Check if the current sync has timed out due to inactivity.
     */
    private boolean isTimedOut() {
        if (activeSyncType.get() == null || lastActivityAt == 0) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - lastActivityAt;
        return elapsed > DEFAULT_IDLE_TIMEOUT_MS;
    }
}
