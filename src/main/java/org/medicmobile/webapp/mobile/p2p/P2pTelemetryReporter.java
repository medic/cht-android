package org.medicmobile.webapp.mobile.p2p;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Reports P2P telemetry to the server endpoint POST /api/v1/p2p/telemetry.
 *
 * Telemetry is fire-and-forget (async, no retry on failure).
 * Schema from CONTRACT.md Section 6:
 * {
 *   "type": "telemetry",
 *   "metadata": { "device_id": "uuid", "user": "chw-mary" },
 *   "p2p_session": {
 *     "session_id": "uuid",
 *     "role": "sender | receiver",
 *     "transport": "wifi_hotspot",
 *     "peer_count": 1,
 *     "docs_transferred": 47,
 *     "transit_docs": 42,
 *     "bytes_transferred": 245000,
 *     "duration_ms": 300000,
 *     "status": "completed",
 *     "error": null,
 *     "device_info": {
 *       "manufacturer": "tecno",
 *       "model": "Spark 10C",
 *       "android_api": 33,
 *       "ram_mb": 4096
 *     }
 *   }
 * }
 *
 * The server endpoint returns 202 Accepted. We don't retry on failure —
 * telemetry is best-effort.
 */
public class P2pTelemetryReporter {

    private static final String TAG = "P2pTelemetryReporter";

    private final String deviceId;
    private final String userId;
    private final Context context;

    /**
     * @param deviceId the device's unique identifier
     * @param userId   the current user's ID
     * @param context  Android context for reading device info (RAM)
     */
    public P2pTelemetryReporter(String deviceId, String userId, Context context) {
        this.deviceId = deviceId;
        this.userId = userId;
        this.context = context;
    }

    /**
     * Build telemetry payload from completed sessions.
     *
     * Returns an array of telemetry entries (one per session), each matching
     * the CONTRACT.md Section 6 format. The server telemetry endpoint
     * (POST /api/v1/p2p/telemetry) expects:
     * { device_id: string, sessions: P2pSessionLog[] }
     *
     * @param tracker the P2pTracker containing completed sessions
     * @return the full telemetry request payload
     */
    public JSONObject buildTelemetryPayload(P2pTracker tracker) throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("device_id", deviceId);

        JSONArray sessions = new JSONArray();
        List<P2pSession> completedSessions = tracker.getCompletedSessions();

        for (P2pSession session : completedSessions) {
            sessions.put(buildSessionEntry(session));
        }

        payload.put("sessions", sessions);
        Log.d(TAG, "Built telemetry payload with " + sessions.length() + " sessions");
        return payload;
    }

    /**
     * Build a single telemetry entry matching CONTRACT.md Section 6.
     *
     * {
     *   "type": "telemetry",
     *   "metadata": { "device_id": "uuid", "user": "chw-mary" },
     *   "p2p_session": { ... }
     * }
     */
    private JSONObject buildSessionEntry(P2pSession session) throws JSONException {
        JSONObject entry = new JSONObject();
        entry.put("type", "telemetry");

        // metadata
        JSONObject metadata = new JSONObject();
        metadata.put("device_id", deviceId);
        metadata.put("user", userId);
        entry.put("metadata", metadata);

        // p2p_session
        JSONObject p2pSession = new JSONObject();
        p2pSession.put("session_id", session.getSessionId());
        p2pSession.put("role", inferRole(session));
        p2pSession.put("transport", "wifi_hotspot");
        p2pSession.put("peer_count", 1);
        p2pSession.put("docs_transferred",
                session.getDocsPushed() + session.getDocsPulled() + session.getTransitDocs());
        p2pSession.put("transit_docs", session.getTransitDocs());
        p2pSession.put("bytes_transferred", session.getBytesTransferred());
        p2pSession.put("duration_ms", session.getDurationMs());
        p2pSession.put("status", mapStatus(session));
        p2pSession.put("error", session.getError() != null ? session.getError() : JSONObject.NULL);
        p2pSession.put("device_info", getDeviceInfo());

        entry.put("p2p_session", p2pSession);
        return entry;
    }

    /**
     * Get device info for telemetry.
     *
     * Returns: { manufacturer, model, android_api, ram_mb }
     */
    private JSONObject getDeviceInfo() throws JSONException {
        JSONObject info = new JSONObject();
        info.put("manufacturer", Build.MANUFACTURER.toLowerCase());
        info.put("model", Build.MODEL);
        info.put("android_api", Build.VERSION.SDK_INT);
        info.put("ram_mb", getTotalRamMb());
        return info;
    }

    /**
     * Get total device RAM in MB.
     * Uses ActivityManager.MemoryInfo for accurate reporting.
     */
    private long getTotalRamMb() {
        if (context == null) {
            return 0;
        }
        try {
            ActivityManager activityManager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager != null) {
                ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(memoryInfo);
                return memoryInfo.totalMem / (1024 * 1024);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get device RAM", e);
        }
        return 0;
    }

    /**
     * Infer whether this device was sender or receiver based on session data.
     * If docs were pushed > pulled, this device was the sender (CHW).
     * If docs were pulled > pushed (or transit docs present), this was the receiver (Supervisor).
     */
    private String inferRole(P2pSession session) {
        if (session.getTransitDocs() > 0) {
            return "receiver";
        }
        if (session.getDocsPushed() >= session.getDocsPulled()) {
            return "sender";
        }
        return "receiver";
    }

    private String mapStatus(P2pSession session) {
        switch (session.getState()) {
            case COMPLETED:
                return "completed";
            case FAILED:
                return "failed";
            default:
                return "interrupted";
        }
    }
}
