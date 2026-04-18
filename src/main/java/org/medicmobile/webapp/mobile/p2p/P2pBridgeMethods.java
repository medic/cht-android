package org.medicmobile.webapp.mobile.p2p;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;
import android.webkit.JavascriptInterface;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JavaScript bridge methods for P2P sync operations.
 * Bound to the WebView as part of the 'medicmobile_android' interface.
 *
 * The webapp calls these methods to:
 * - Start/stop P2P host or client mode
 * - Get transit doc IDs for UI filtering (G23)
 * - Trigger transit doc purge after server push (G25)
 * - Get P2P sync status and history
 *
 * All methods are called on the WebView JS thread and must return quickly.
 * Async operations (hotspot start, etc.) use CountDownLatch to block until
 * the callback fires, with a timeout to prevent indefinite blocking.
 */
public class P2pBridgeMethods {

    private static final String TAG = "P2pBridgeMethods";
    private static final String KEY_OK = "ok";
    private static final String KEY_ERROR = "error";
    private static final String KEY_STATUS = "status";
    private static final String STATE_IDLE = "idle";
    private static final String STATE_CONNECTING = "connecting";
    private static final String STATE_WAITING_WIFI = "waiting_wifi";
    private static final String STATE_PREVIEW = "preview";
    private static final String STATE_SYNCING = "syncing";
    private static final String STATE_COMPLETED = "completed";
    private static final String STATE_FAILED = "failed";
    private static final long HOST_START_TIMEOUT_SEC = 30;
    private static final long CLIENT_START_TIMEOUT_SEC = 15;

    private final P2pManager p2pManager;
    private final TransitDocManager transitDocManager;
    private final P2pTracker tracker;

    // WebView reference for PouchDB evaluation
    private volatile android.webkit.WebView webView;

    // Bridge callback support for reliable evalPouchDb (Fix 1)
    // JS Promise results are delivered via p2pAsyncCallback() instead of timed polling
    private final ConcurrentHashMap<String, String> asyncCallbackResults = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CountDownLatch> asyncCallbackLatches = new ConcurrentHashMap<>();

    // Cached QR data URL for status polling
    private volatile String cachedQrDataUrl = null;

    // Cached JWT token for peer auth with supervisor
    private volatile String cachedJwt = null;

    // Active sync client (peer side)
    private volatile P2pSyncClient activeSyncClient = null;

    // Sync progress for peer side
    private volatile int clientDocsSynced = 0;
    private volatile int clientTotalDocs = 0;
    private volatile long clientBytesTransferred = 0;
    private volatile String clientSyncState = STATE_IDLE; // idle, waiting_wifi, connecting, syncing, completed, failed
    private volatile String clientSyncError = null;

    // Cached peer connection info (set after QR scan, used when WiFi connects)
    private volatile String cachedPeerHost = null;
    private volatile int cachedPeerPort = 0;

    // Concurrency guard — prevents multiple sync threads
    private volatile boolean clientSyncRunning = false;

    // Preview: pending doc entries from changes feed, awaiting user confirmation
    private volatile JSONArray pendingDocIds = null;
    private volatile int previewContactCount = 0;
    private volatile int previewReportCount = 0;

    // PouchDB sequence at time of preview query — saved as checkpoint after successful push.
    // Used with max(lastServerSeq, p2pLastSeq) to enable incremental sync:
    // only docs created/modified since the last successful server replication or P2P sync.
    private volatile long changesLastSeq = 0;

    public P2pBridgeMethods(P2pManager p2pManager, TransitDocManager transitDocManager,
                            P2pTracker tracker) {
        this.p2pManager = p2pManager;
        this.transitDocManager = transitDocManager;
        this.tracker = tracker;
    }

    public void setWebView(android.webkit.WebView webView) {
        this.webView = webView;
    }

    /**
     * Bridge callback for reliable async JS evaluation.
     * Called from JS via: medicmobile_android.p2pAsyncCallback(id, result)
     * This replaces the 50ms postDelayed timing hack in evalPouchDb.
     */
    @JavascriptInterface
    public void p2pAsyncCallback(String callbackId, String result) {
        if (callbackId == null) return;
        asyncCallbackResults.put(callbackId, result != null ? result : "null");
        CountDownLatch latch = asyncCallbackLatches.get(callbackId);
        if (latch != null) {
            latch.countDown();
        }
    }

    /**
     * Start P2P in supervisor mode.
     * Starts hotspot, HTTP server, and generates QR code payload.
     * Called from webapp when supervisor taps "Start P2P Sync".
     *
     * Blocks until hotspot + server are ready or timeout.
     *
     * @return JSON string: { "ok": true, "qr_payload": "..." }
     *         or { "ok": false, "error": "reason" }
     */
    @JavascriptInterface
    public String p2pStartHostMode() {
        try {
            if (p2pManager == null) {
                return errorJson("p2p_not_initialized");
            }
            if (!p2pManager.isInitialized()) {
                return errorJson("not_initialized");
            }

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<JSONObject> resultRef = new AtomicReference<>();

            p2pManager.startHostMode(new P2pManager.HostModeCallback() {
                @Override
                public void onQrCodeReady(String qrPayloadJson) {
                    try {
                        // Generate QR code as data URL for webapp rendering
                        String qrDataUrl = QrCodeHelper.generateQrDataUrl(qrPayloadJson);
                        cachedQrDataUrl = qrDataUrl;

                        JSONObject response = new JSONObject();
                        response.put("ok", true);
                        response.put("qr_payload", qrPayloadJson);
                        response.put("qr_data_url", qrDataUrl != null ? qrDataUrl : "");
                        resultRef.set(response);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error building QR response", e);
                    }
                    latch.countDown();
                }

                @Override
                public void onPeerConnected(String peerId) {
                    // Not relevant during startup
                }

                @Override
                public void onSyncProgress(int docsSynced, int totalDocs) {
                    // Not relevant during startup
                }

                @Override
                public void onSyncComplete(P2pSession session) {
                    // Not relevant during startup
                }

                @Override
                public void onError(String error) {
                    try {
                        JSONObject response = new JSONObject();
                        response.put("ok", false);
                        response.put("error", error);
                        resultRef.set(response);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error building error response", e);
                    }
                    latch.countDown();
                }

                @Override
                public void onBatteryGuidance(OemBatteryHelper.RiskLevel riskLevel,
                                              String guidance) {
                    // Informational, does not affect startup result.
                    // The webapp can query battery guidance separately.
                    Log.i(TAG, "Battery guidance (" + riskLevel + "): " + guidance);
                }
            });

            boolean completed = latch.await(HOST_START_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!completed) {
                Log.w(TAG, "Host start timed out, shutting down to release mutex");
                p2pManager.shutdown();
                return errorJson("supervisor_start_timeout");
            }

            JSONObject result = resultRef.get();
            return result != null ? result.toString() : errorJson("no_result");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "Host start interrupted", e);
            p2pManager.shutdown();
            return errorJson("interrupted");
        } catch (RuntimeException e) {
            Log.e(TAG, "Error starting supervisor mode", e);
            return errorJson("start_failed: " + e.getMessage());
        }
    }

    /**
     * Start P2P in client mode with scanned QR data.
     * Validates QR, returns WiFi credentials for manual connection.
     * User connects to WiFi manually, then webapp calls p2pCheckConnection() to detect it.
     *
     * @param qrPayloadJson the scanned QR payload JSON string
     * @return JSON: { "ok": true, "ssid": "...", "password": "...", "host": "...", "port": N }
     *         or { "ok": false, "error": "reason" }
     */
    @JavascriptInterface
    public String p2pStartClientMode(String qrPayloadJson) {
        try {
            if (p2pManager == null) {
                return errorJson("p2p_not_initialized");
            }
            if (!p2pManager.isInitialized()) {
                return errorJson("not_initialized");
            }

            if (qrPayloadJson == null || qrPayloadJson.isEmpty()) {
                return errorJson("empty_qr_payload");
            }

            // Validate the QR payload
            ValidationResult validation = QrCodeHelper.validateQrPayload(qrPayloadJson);
            if (!validation.isAccepted()) {
                return errorJson("invalid_qr: " + validation.getReason());
            }

            // Parse QR to extract connection info
            JSONObject qr = new JSONObject(qrPayloadJson);
            String ssid = qr.optString("ssid", "");
            String password = qr.optString("pwd", "");
            String host = qr.optString("ip", "");
            int port = qr.optInt("port", 8443);

            // Store connection info for sync after WiFi connects
            cachedPeerHost = host;
            cachedPeerPort = port;
            clientSyncState = STATE_CONNECTING;

            Log.i(TAG, "Client mode: auto-connecting to SSID=" + ssid
                    + " then sync with " + host + ":" + port);

            // Start client mode — triggers async WiFi auto-connect via WifiNetworkSpecifier
            p2pManager.startClientMode(qrPayloadJson, new P2pManager.ClientModeCallback() {
                @Override
                public void onConnectionInfoReady(String s, String p, String ip, int pt, String tls) {
                    Log.i(TAG, "WiFi connected to host, starting client sync");
                    cachedPeerHost = ip;
                    cachedPeerPort = pt;
                    clientSyncState = STATE_CONNECTING;
                    startClientSync(ip, pt);
                }

                @Override
                public void onError(String error) {
                    if (error != null && error.contains("wifi_connection_failed")) {
                        Log.w(TAG, "WiFi auto-connect failed, falling back to manual");
                        clientSyncState = STATE_WAITING_WIFI;
                    } else {
                        Log.e(TAG, "Client mode error: " + error);
                        clientSyncError = error;
                        clientSyncState = STATE_FAILED;
                    }
                }

                @Override
                public void onConnected(String hostId) {
                    Log.d(TAG, "Client connected to host: " + hostId);
                }

                @Override
                public void onPreviewReady(int contactCount, int reportCount, int totalCount) {
                    Log.d(TAG, "Preview ready: " + totalCount + " docs");
                }

                @Override
                public void onSyncProgress(int docsSynced, int totalDocs) {
                    Log.d(TAG, "Sync progress: " + docsSynced + "/" + totalDocs);
                }

                @Override
                public void onSyncComplete(P2pSession session) {
                    Log.d(TAG, "Sync complete: " + session);
                }
            });

            // Return immediately — WiFi connection is async
            JSONObject result = new JSONObject();
            result.put("ok", true);
            result.put("auto_connecting", true);
            result.put("ssid", ssid);
            result.put("password", password);
            result.put("host", host);
            result.put("port", port);
            return result.toString();

        } catch (JSONException e) {
            Log.e(TAG, "Error starting client mode", e);
            clientSyncState = STATE_FAILED;
            return errorJson("start_failed: " + e.getMessage());
        }
    }

    /**
     * Find the WiFi network from ConnectivityManager.
     * On Android, when connected to a LocalOnlyHotspot (no internet),
     * the default network remains cellular. We must explicitly bind HTTP
     * connections to the WiFi network to reach the hotspot's HTTP server.
     */
    private Network findWifiNetwork() {
        if (p2pManager == null) return null;

        // Primary: exact network from WifiNetworkSpecifier auto-connect
        Network autoNetwork = p2pManager.getP2pWifiNetwork();
        if (autoNetwork != null) {
            Log.d(TAG, "Using auto-connected WiFi network: " + autoNetwork);
            return autoNetwork;
        }

        // Fallback: find WiFi network on same subnet as supervisor
        // Critical for dual-WiFi devices (Pixel has wlan0 + wlan1)
        if (cachedPeerHost == null || cachedPeerHost.isEmpty()) return null;
        String targetPrefix = cachedPeerHost.substring(0, cachedPeerHost.lastIndexOf('.') + 1);

        try {
            Context ctx = p2pManager.getContext();
            if (ctx == null) return null;
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return null;

            Network fallbackWifi = null;
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue;

                // Check if this WiFi network's IP is on the same subnet as supervisor
                android.net.LinkProperties lp = cm.getLinkProperties(network);
                if (lp != null) {
                    for (android.net.LinkAddress addr : lp.getLinkAddresses()) {
                        java.net.InetAddress inetAddr = addr.getAddress();
                        if (inetAddr instanceof java.net.Inet4Address) {
                            String ip = inetAddr.getHostAddress();
                            if (ip != null && ip.startsWith(targetPrefix)) {
                                Log.i(TAG, "Found WiFi on supervisor subnet: " + ip
                                        + " (target=" + targetPrefix + "*) → " + network);
                                return network;
                            }
                        }
                    }
                }
                // Keep first WiFi as last-resort fallback
                if (fallbackWifi == null) {
                    fallbackWifi = network;
                }
            }
            if (fallbackWifi != null) {
                Log.w(TAG, "No subnet match for " + targetPrefix + "*, using fallback WiFi: " + fallbackWifi);
                return fallbackWifi;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to find WiFi network", e);
        }
        Log.w(TAG, "No WiFi network found at all");
        return null;
    }

    /**
     * Check if the peer can reach the supervisor's HTTP server.
     * Called by webapp polling after user manually connects to the hotspot WiFi.
     * When reachable, automatically starts the sync.
     *
     * @return JSON: { "connected": true/false }
     */
    @JavascriptInterface
    public String p2pCheckConnection() {
        try {
            if (cachedPeerHost == null || cachedPeerHost.isEmpty()) {
                Log.d(TAG, "p2pCheckConnection: no host cached");
                return "{\"connected\":false}";
            }

            P2pSyncClient client = new P2pSyncClient(cachedPeerHost, cachedPeerPort);
            // Bind to WiFi network so HTTP calls route through the hotspot, not cellular
            Network wifiNetwork = findWifiNetwork();
            if (wifiNetwork != null) {
                client.setNetwork(wifiNetwork);
            } else {
                Log.d(TAG, "p2pCheckConnection: no WiFi network found, trying default");
            }

            boolean reachable = client.isReachable();
            Log.d(TAG, "p2pCheckConnection: host=" + cachedPeerHost + ":" + cachedPeerPort
                    + " reachable=" + reachable + " wifiNet=" + (wifiNetwork != null));

            if (reachable && STATE_WAITING_WIFI.equals(clientSyncState)) {
                Log.i(TAG, "Host reachable at " + cachedPeerHost + ":" + cachedPeerPort
                        + " — starting sync");
                clientSyncState = STATE_CONNECTING;
                startClientSync(cachedPeerHost, cachedPeerPort);
            }

            JSONObject result = new JSONObject();
            result.put("connected", reachable);
            return result.toString();
        } catch (JSONException e) {
            Log.w(TAG, "p2pCheckConnection error", e);
            return "{\"connected\":false}";
        }
    }

    /**
     * Retry P2P sync using cached connection info from previous QR scan.
     * Does NOT re-scan QR or re-request WiFi — preserves existing WiFi connection.
     */
    @JavascriptInterface
    public String p2pRetrySync() {
        try {
            if (cachedPeerHost == null || cachedPeerHost.isEmpty()) {
                return errorJson("no_cached_connection: scan QR code first");
            }
            if (clientSyncRunning) {
                return errorJson("sync_already_running");
            }

            // Reset state for retry
            clientSyncState = STATE_CONNECTING;
            clientSyncError = null;
            clientDocsSynced = 0;
            clientTotalDocs = 0;
            clientBytesTransferred = 0;
            pendingDocIds = null;
            previewContactCount = 0;
            previewReportCount = 0;

            Log.i(TAG, "Retrying peer sync with cached host: " + cachedPeerHost + ":" + cachedPeerPort);
            startClientSync(cachedPeerHost, cachedPeerPort);

            JSONObject result = new JSONObject();
            result.put("ok", true);
            return result.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Error retrying sync", e);
            return errorJson("retry_failed: " + e.getMessage());
        }
    }

    /**
     * Stop P2P sync (both host and client modes).
     */
    @JavascriptInterface
    public void p2pStop() {
        try {
            // Build log JSON BEFORE shutdown clears state (fixes race condition).
            // The background thread only writes the pre-built data to PouchDB.
            boolean wasHost = p2pManager != null && p2pManager.isHostModeActive();
            boolean wasPeer = p2pManager != null && p2pManager.isClientModeActive();
            if (wasHost || wasPeer) {
                try {
                    final JSONObject logToSave;
                    final String docId;
                    if (wasHost) {
                        logToSave = tracker.buildRelayLog();
                        docId = "_local/p2p-relay-log";
                    } else {
                        logToSave = tracker.buildSyncLog();
                        docId = "_local/p2p-sync-log";
                    }
                    // Save pre-built data on background thread — immune to shutdown race
                    new Thread(() -> {
                        try {
                            savePrebuiltLogToPouchDb(logToSave, docId);
                        } catch (RuntimeException e) {
                            Log.e(TAG, "Error saving sync log on stop", e);
                        }
                    }, "P2pSaveSyncLog").start();
                } catch (JSONException e) {
                    Log.e(TAG, "Error building sync log before shutdown", e);
                }
            }

            // Persist transit state to PouchDB so it survives app restart (Gap 1 fix).
            // Without this, TransitDocManager state only lives in Java memory and is lost
            // on restart, so the webapp's purge service gets a 404 on _local/p2p-transit-docs.
            if (wasHost && p2pManager != null) {
                final JSONObject transitState = p2pManager.getTransitStateJson();
                if (transitState != null) {
                    new Thread(() -> {
                        try {
                            saveTransitStateToPouchDb(transitState);
                        } catch (RuntimeException e) {
                            Log.e(TAG, "Error saving transit state on stop", e);
                        }
                    }, "P2pSaveTransitState").start();
                }
            }

            cachedQrDataUrl = null;
            clientSyncRunning = false;
            clientSyncState = STATE_IDLE;
            clientSyncError = null;
            clientDocsSynced = 0;
            clientTotalDocs = 0;
            clientBytesTransferred = 0;
            activeSyncClient = null;
            cachedPeerHost = null;
            cachedPeerPort = 0;
            pendingDocIds = null;
            previewContactCount = 0;
            previewReportCount = 0;
            if (p2pManager != null) {
                p2pManager.shutdown();
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Error stopping P2P", e);
        }
    }

    /**
     * Start the P2P client flow: connect, auth, fetch IDs, then show preview.
     * Does NOT start downloading docs — waits for user to call p2pProceedSync().
     *
     * Flow: connect → auth → get-ids → preview (STOP) → user confirms → bulk-get
     */
    private void startClientSync(final String host, final int port) {
        if (clientSyncRunning) {
            Log.w(TAG, "startClientSync: already running, skipping");
            return;
        }
        clientSyncRunning = true;
        new Thread(() -> {
            try {
                clientSyncState = STATE_CONNECTING;
                clientSyncError = null;
                P2pSyncClient client = new P2pSyncClient(host, port);
                // Bind to WiFi network — same reason as in p2pCheckConnection
                Network wifiNet = findWifiNetwork();
                if (wifiNet != null) {
                    client.setNetwork(wifiNet);
                    Log.i(TAG, "Client sync: bound to WiFi network " + wifiNet);
                }
                activeSyncClient = client;

                // Probe reachability — L3 routing may need time after WiFi L2 connect
                boolean reachable = false;
                for (int attempt = 1; attempt <= 10; attempt++) {
                    if (client.isReachable()) {
                        reachable = true;
                        Log.i(TAG, "Server reachable on attempt " + attempt);
                        break;
                    }
                    Log.d(TAG, "Server not reachable, attempt " + attempt + "/10, waiting 1s...");
                    Thread.sleep(1000);
                }
                if (!reachable) {
                    Log.e(TAG, "Server unreachable after 10 attempts");
                    clientSyncError = "Server unreachable after 10 attempts at " + host + ":" + port
                            + ". WiFi network=" + (wifiNet != null ? wifiNet.toString() : "none");
                    clientSyncState = STATE_FAILED;
                    return;
                }

                // Step 1: Authenticate
                if (cachedJwt == null || cachedJwt.isEmpty()) {
                    Log.e(TAG, "No JWT token cached, cannot authenticate with host");
                    clientSyncError = "No authentication token. Please re-initialize P2P sync.";
                    clientSyncState = STATE_FAILED;
                    return;
                }

                // Pass device ID for session tracking
                if (p2pManager != null) {
                    client.setDeviceId(p2pManager.getLocalDeviceId());
                }

                Log.i(TAG, "Client sync: authenticating with host at " + host + ":" + port);
                String authError = client.authenticate(cachedJwt);
                if (authError != null) {
                    Log.e(TAG, "Client sync: authentication failed: " + authError);
                    clientSyncError = "Authentication failed: " + authError;
                    clientSyncState = STATE_FAILED;
                    return;
                }

                // Start a tracker session for the peer (CHW) side
                if (tracker != null) {
                    String peerDeviceId = "host-" + host;
                    String peerUserId = "host";
                    tracker.startSession(peerDeviceId, peerUserId, "host", null);
                }

                Log.i(TAG, "Client sync: authenticated, querying unsynced docs");

                // Step 2: Get docs not yet synced to server.
                //
                // Uses only lastServerReplicatedSeq from CHT's db-sync.service.ts.
                // No P2P checkpoint — if Supervisor never syncs online, docs would be lost.
                // Duplicate pushes are harmless (new_edits: false on the host).
                //
                // Returns: { docs: [{_id, _rev}, ...], last_seq: N }
                String changesJs =
                    "(async function() {" +
                    "  var db = window.CHTCore.DB.get();" +
                    "  var since = Number(localStorage.getItem('medic-last-replicated-seq')) || 0;" +
                    "  var result = await db.changes({ since: since });" +
                    "  var docs = result.results" +
                    "    .filter(function(r) { return !r.id.startsWith('_design/') && !r.id.startsWith('_local/') && !r.deleted; })" +
                    "    .map(function(r) { return { _id: r.id, _rev: r.changes[0].rev }; });" +
                    "  return JSON.stringify({ docs: docs, last_seq: result.last_seq });" +
                    "})()";

                String changesJson = evalPouchDb(changesJs);
                JSONObject changesResult = new JSONObject(changesJson);
                JSONArray docsToSync = changesResult.getJSONArray("docs");
                changesLastSeq = changesResult.getLong("last_seq");

                // Count contacts vs reports for preview
                int contacts = 0;
                int reports = 0;
                for (int i = 0; i < docsToSync.length(); i++) {
                    JSONObject entry = docsToSync.getJSONObject(i);
                    String docId = entry.optString("_id", "");
                    if (docId.startsWith("report:") || docId.startsWith("report~")) {
                        reports++;
                    } else {
                        contacts++;
                    }
                }

                clientTotalDocs = docsToSync.length();
                previewContactCount = contacts;
                previewReportCount = reports;
                pendingDocIds = docsToSync;

                Log.i(TAG, "Client sync: CHW has " + docsToSync.length() + " unsynced docs to push ("
                        + contacts + " contacts, " + reports + " reports) — awaiting user confirmation");

                // Set state to preview — webapp will show counts and wait for user
                clientSyncState = STATE_PREVIEW;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "Client sync interrupted during preview phase", e);
                clientSyncError = "Sync interrupted";
                clientSyncState = STATE_FAILED;
            } catch (JSONException | IOException e) {
                Log.e(TAG, "Client sync failed during preview phase", e);
                clientSyncError = "Sync error: " + e.getMessage();
                clientSyncState = STATE_FAILED;
            } finally {
                // Do NOT clear clientSyncRunning here — we're still in preview
                // It will be cleared when proceedClientSync completes or user cancels
                if (!STATE_PREVIEW.equals(clientSyncState)) {
                    clientSyncRunning = false;
                }
            }
        }, "P2pClientSync").start();
    }

    /**
     * Proceed with sync after user confirms preview.
     * Pushes CHW's docs to the Supervisor via POST /_p2p/accept-docs.
     *
     * @return JSON: {"ok": true} or {"ok": false, "error": "..."}
     */
    @JavascriptInterface
    public String p2pProceedSync() {
        try {
            if (pendingDocIds == null || pendingDocIds.length() == 0) {
                return errorJson("no_pending_docs: nothing to sync");
            }
            if (activeSyncClient == null) {
                return errorJson("no_active_client: connection lost");
            }
            if (!STATE_PREVIEW.equals(clientSyncState)) {
                return errorJson("invalid_state: expected preview, got " + clientSyncState);
            }

            // Start push in background thread
            final JSONArray docEntries = pendingDocIds;
            final P2pSyncClient client = activeSyncClient;
            pendingDocIds = null;

            new Thread(() -> {
                try {
                    clientSyncState = STATE_SYNCING;
                    int totalDocs = docEntries.length();
                    clientTotalDocs = totalDocs;

                    // Push docs in batches
                    int batchSize = 50;
                    int pushed = 0;
                    for (int i = 0; i < totalDocs; i += batchSize) {
                        int end = Math.min(i + batchSize, totalDocs);

                        // Collect IDs for this batch
                        JSONArray idStrings = new JSONArray();
                        for (int j = i; j < end; j++) {
                            JSONObject entry = docEntries.getJSONObject(j);
                            idStrings.put(entry.getString("_id"));
                        }

                        // Get full doc bodies from local PouchDB
                        Log.d(TAG, "Client sync: fetching doc IDs from PouchDB: " + idStrings.toString());
                        String docsJson = p2pManager.getBridge().getDocsByIds(idStrings.toString());
                        Log.d(TAG, "Client sync: PouchDB returned " + docsJson.length() + " chars: "
                                + docsJson.substring(0, Math.min(500, docsJson.length())));
                        JSONArray docs = new JSONArray(docsJson);
                        Log.d(TAG, "Client sync: parsed " + docs.length() + " docs from PouchDB response");

                        if (docs.length() > 0) {
                            // Push to Supervisor
                            JSONObject result = client.acceptDocs(docs);
                            Log.d(TAG, "Client sync: accept-docs response: " + result.toString());
                            int accepted = result.optInt("accepted", 0);
                            int transit = result.optInt("transit", 0);
                            int rejected = result.optInt("rejected", 0);
                            if (rejected > 0) {
                                Log.w(TAG, "Client sync: " + rejected + " docs REJECTED by host. Errors: "
                                        + result.optJSONArray("errors"));
                            }
                            pushed += accepted + transit;
                            clientBytesTransferred += docsJson.length();
                        }
                        clientDocsSynced = pushed;
                        Log.d(TAG, "Client sync: pushed " + pushed + "/" + totalDocs);
                    }

                    Log.i(TAG, "Client sync: push complete, "
                            + pushed + " docs sent to Supervisor");

                    // Signal completion to the Supervisor so host transitions to "completed"
                    try {
                        client.syncComplete(pushed, clientBytesTransferred);
                        Log.i(TAG, "Client sync: sent sync-complete to Supervisor");
                    } catch (JSONException | IOException e) {
                        Log.w(TAG, "Client sync: failed to signal sync-complete (non-fatal)", e);
                    }

                    // No P2P checkpoint saved — only server seq matters.
                    // Duplicate pushes are harmless (new_edits: false).

                    // Update tracker session counters and complete
                    if (tracker != null) {
                        P2pSession trackerSession = tracker.getCurrentSession();
                        if (trackerSession != null) {
                            trackerSession.incrementDocsPushed(pushed);
                            trackerSession.addBytesTransferred(clientBytesTransferred);
                        }
                        tracker.completeSession();
                    }

                    clientSyncState = STATE_COMPLETED;
                    Log.i(TAG, "Client sync: completed successfully");

                    // Save sync history to PouchDB (peer/CHW side)
                    try {
                        saveSyncLogToPouchDb(false);
                    } catch (RuntimeException saveErr) {
                        Log.e(TAG, "Client sync: failed to save sync log", saveErr);
                    }

                } catch (JSONException | IOException e) {
                    Log.e(TAG, "Client sync failed", e);
                    clientSyncError = "Sync error: " + e.getMessage();
                    clientSyncState = STATE_FAILED;
                } finally {
                    clientSyncRunning = false;
                }
            }, "P2pClientSyncPush").start();

            JSONObject result = new JSONObject();
            result.put("ok", true);
            return result.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Error proceeding with sync", e);
            return errorJson("proceed_failed: " + e.getMessage());
        }
    }

    /**
     * Get P2P sync status.
     *
     * @return JSON string with status fields from P2pManager and tracker
     */
    @JavascriptInterface
    public String p2pGetStatus() {
        try {
            if (p2pManager == null) {
                return buildIdleStatus();
            }

            JSONObject status = new JSONObject();
            String state = populateTrackerState(status);
            state = populateModeState(status, state);

            status.put("state", state);
            if (clientSyncError != null) {
                status.put(KEY_ERROR, clientSyncError);
            }
            status.put("initialized", p2pManager.isInitialized());

            populateHotspotInfo(status);
            populateConnectedPeers(status);

            if (cachedQrDataUrl != null) {
                status.put("qr_code_data_url", cachedQrDataUrl);
            }

            return status.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Error building status JSON", e);
            return buildIdleStatus();
        }
    }

    private String populateTrackerState(JSONObject status) throws JSONException {
        if (tracker != null && tracker.hasActiveSession()) {
            P2pSession session = tracker.getCurrentSession();
            status.put("docs_synced", session.getDocsPushed() + session.getDocsPulled());
            status.put("total_docs", session.getDocsPushed() + session.getDocsPulled()
                    + session.getTransitDocs());
            status.put("bytes_transferred", session.getBytesTransferred());
            return session.getState().name().toLowerCase();
        }
        status.put("docs_synced", 0);
        status.put("total_docs", 0);
        status.put("bytes_transferred", 0);
        return STATE_IDLE;
    }

    private String populateModeState(JSONObject status, String state) throws JSONException {
        if (p2pManager.isHostModeActive()) {
            return populateHostModeState(status, state);
        }
        if (p2pManager.isClientModeActive()) {
            return populateClientModeState(status);
        }
        return state;
    }

    private String populateHostModeState(JSONObject status, String state) throws JSONException {
        int peerCount = p2pManager.getConnectedPeerCount();
        P2pSession httpSession = p2pManager.getHttpSession();

        if (httpSession != null && httpSession.getState() == P2pSession.State.COMPLETED) {
            int sessionDocs = httpSession.getDocsPulled() + httpSession.getTransitDocs();
            status.put("docs_synced", sessionDocs);
            status.put("total_docs", sessionDocs);
            status.put("bytes_transferred", httpSession.getBytesTransferred());
            state = STATE_COMPLETED;
        } else if (peerCount > 0 && httpSession != null
                && httpSession.getState() == P2pSession.State.ACTIVE) {
            state = deriveActiveHostState(status, httpSession);
        } else if (STATE_IDLE.equals(state)) {
            state = "waiting";
        }

        if (httpSession != null) {
            Log.d(TAG, "Host status: state=" + state
                    + " pulled=" + httpSession.getDocsPulled()
                    + " transit=" + httpSession.getTransitDocs()
                    + " bytes=" + httpSession.getBytesTransferred());
        }
        return state;
    }

    private String deriveActiveHostState(JSONObject status, P2pSession httpSession) throws JSONException {
        if (httpSession.getDocsPulled() > 0 || httpSession.getDocsPushed() > 0
                || httpSession.getTransitDocs() > 0) {
            int sessionDocs = httpSession.getDocsPulled() + httpSession.getTransitDocs();
            status.put("docs_synced", sessionDocs);
            status.put("total_docs", sessionDocs);
            status.put("bytes_transferred", httpSession.getBytesTransferred());
            return STATE_SYNCING;
        }
        return "peer_connected";
    }

    private String populateClientModeState(JSONObject status) throws JSONException {
        String state = clientSyncState;
        status.put("docs_synced", clientDocsSynced);
        status.put("total_docs", clientTotalDocs);
        status.put("bytes_transferred", clientBytesTransferred);
        if (STATE_PREVIEW.equals(state)) {
            status.put("preview_contacts", previewContactCount);
            status.put("preview_reports", previewReportCount);
            status.put("preview_total", clientTotalDocs);
        }
        return state;
    }

    private void populateHotspotInfo(JSONObject status) throws JSONException {
        JSONObject managerStatus = p2pManager.getStatusJson();
        status.put("hotspot_active", managerStatus.optBoolean("hotspot_active", false));
        String ssid = managerStatus.optString("hotspot_ssid", null);
        if (ssid != null) {
            status.put("hotspot_ssid", ssid);
        }
        String pwd = p2pManager.getHotspotPassword();
        if (pwd != null) {
            status.put("hotspot_password", pwd);
        }
    }

    private void populateConnectedPeers(JSONObject status) throws JSONException {
        int peerCount = p2pManager.getConnectedPeerCount();
        JSONArray peersArray = new JSONArray();
        if (peerCount > 0) {
            P2pSession httpSession = p2pManager.getHttpSession();
            if (httpSession != null) {
                peersArray.put(httpSession.getPeerUserId() != null
                        ? httpSession.getPeerUserId() : "peer");
            }
        }
        status.put("connected_peers", peersArray);
    }

    /**
     * G23: Get all transit doc IDs for UI filtering.
     * Returns JSON array of doc IDs that should be hidden from the UI.
     * G24: Must return in <50ms (uses in-memory HashMap lookup).
     *
     * @return JSON array string: ["doc_id_1", "doc_id_2", ...]
     */
    @JavascriptInterface
    public String p2pGetTransitDocIds() {
        try {
            if (transitDocManager == null) {
                return "[]";
            }

            Set<String> transitIds = transitDocManager.getAllTransitDocIds();
            JSONArray array = new JSONArray();
            for (String docId : transitIds) {
                array.put(docId);
            }
            return array.toString();
        } catch (RuntimeException e) {
            Log.e(TAG, "Error getting transit doc IDs", e);
            return "[]";
        }
    }

    /**
     * Check if a specific doc is a transit doc.
     * Used for single-doc checks in the webapp.
     *
     * @param docId the document ID to check
     * @return true if the doc is a transit doc that should be hidden
     */
    @JavascriptInterface
    public boolean p2pIsTransitDoc(String docId) {
        return transitDocManager != null
                && docId != null
                && transitDocManager.isTransitDoc(docId);
    }

    /**
     * G25: Get transit docs that are ready to be purged (pushed to server but not yet purged).
     * Returns JSON with batch info so the webapp can call db.purge() for each doc.
     * MUST use db.purge(), never db.remove().
     *
     * @return JSON string: {
     *   "batches": [
     *     { "batch_id": "uuid", "doc_ids": ["doc1", "doc2", ...] }
     *   ],
     *   "total_docs": number
     * }
     */
    @JavascriptInterface
    public String p2pPurgeTransitDocs() {
        try {
            if (transitDocManager == null) {
                return buildEmptyPurgeResponse();
            }

            Set<String> purgeableBatchIds = transitDocManager.getPurgeableBatchIds();
            if (purgeableBatchIds.isEmpty()) {
                return buildEmptyPurgeResponse();
            }

            JSONArray batchesArray = new JSONArray();
            int totalDocs = 0;

            for (String batchId : purgeableBatchIds) {
                Set<String> docIds = transitDocManager.getDocIdsForBatch(batchId);
                if (docIds.isEmpty()) {
                    continue;
                }

                JSONObject batchObj = new JSONObject();
                batchObj.put("batch_id", batchId);

                JSONArray docIdsArray = new JSONArray();
                for (String docId : docIds) {
                    docIdsArray.put(docId);
                }
                batchObj.put("doc_ids", docIdsArray);
                batchesArray.put(batchObj);
                totalDocs += docIds.size();
            }

            JSONObject response = new JSONObject();
            response.put("batches", batchesArray);
            response.put("total_docs", totalDocs);
            return response.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Error building purge response", e);
            return buildEmptyPurgeResponse();
        }
    }

    /**
     * Confirm that a batch has been purged by the webapp.
     * Called after webapp successfully runs db.purge() for all docs in a batch.
     * G25: The webapp MUST use db.purge(id, rev), never db.remove().
     *
     * @param batchId the batch ID that was purged
     */
    @JavascriptInterface
    public void p2pConfirmBatchPurged(String batchId) {
        if (transitDocManager != null && batchId != null && !batchId.isEmpty()) {
            transitDocManager.markBatchPurged(batchId);
            Log.i(TAG, "Batch purged confirmed: " + batchId);

            // G26: Archive old purged batches if transit doc is oversized
            if (transitDocManager.isOversized()) {
                transitDocManager.archivePurgedBatches();
                Log.i(TAG, "Archived purged batches due to size limit (G26)");
            }
        }
    }

    /**
     * Get P2P sync history (completed sessions).
     * Returns JSON matching _local/p2p-sync-log schema from CONTRACT.md Section 5.
     *
     * @return JSON string with sessions array
     */
    @JavascriptInterface
    public String p2pGetSyncHistory() {
        try {
            if (tracker == null) {
                return buildEmptySyncHistory();
            }

            JSONObject syncLog = tracker.buildSyncLog();
            return syncLog.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Error building sync history", e);
            return buildEmptySyncHistory();
        }
    }

    /**
     * G27: Check if there are stale transit docs (unpushed for >30 days).
     * The webapp should show a notification to the user if true.
     *
     * @return true if there are stale transit docs
     */
    @JavascriptInterface
    public boolean p2pHasStaleTransitDocs() {
        return transitDocManager != null && transitDocManager.hasStaleTransitDocs();
    }

    /**
     * Get device P2P capability.
     * Checks Android API level, WiFi support, storage, battery, etc.
     *
     * @return JSON string: { "capable": boolean, "capability": string, "reason": string | null }
     */
    @JavascriptInterface
    public String p2pGetCapability() {
        try {
            JSONObject result = new JSONObject();

            if (p2pManager == null) {
                result.put("capable", false);
                result.put("capability", "unknown");
                result.put("reason", "P2P manager not initialized");
                return result.toString();
            }

            P2pManager.P2pCapability capability = p2pManager.checkCapability();

            boolean capable = capability == P2pManager.P2pCapability.FULLY_SUPPORTED
                    || capability == P2pManager.P2pCapability.SUPPORTED_NO_CAMERA
                    || capability == P2pManager.P2pCapability.LOW_RAM_WARNING
                    || capability == P2pManager.P2pCapability.LOW_BATTERY_WARNING;

            result.put("capable", capable);
            result.put("capability", capability.name().toLowerCase());
            result.put("reason", getCapabilityReason(capability));

            result.put("api_level", Build.VERSION.SDK_INT);
            result.put("manufacturer", OemBatteryHelper.getManufacturer());
            result.put("model", OemBatteryHelper.getModel());
            result.put("battery_risk", OemBatteryHelper.getRiskLevel().name().toLowerCase());

            return result.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Error checking capability", e);
            return errorJson("capability_check_failed");
        }
    }

    /**
     * Get OEM-specific battery optimization guidance text.
     * Users need to disable battery optimization for reliable P2P sync.
     *
     * @return human-readable guidance string for the current device manufacturer
     */
    @JavascriptInterface
    public String p2pGetBatteryGuidance() {
        return OemBatteryHelper.getGuidance();
    }

    /**
     * Check if the device needs battery optimization guidance.
     *
     * @return true if the OEM is known to aggressively kill background services
     */
    @JavascriptInterface
    public boolean p2pNeedsBatteryGuidance() {
        return OemBatteryHelper.needsBatteryGuidance();
    }

    /**
     * Initialize the P2P manager with config from the webapp.
     * Must be called before startHostMode/startClientMode.
     *
     * Expected JSON:
     * {
     *   "config": { ... p2p_sync config ... },
     *   "scope_manifest": { "facility_subtree_root": "...", "replication_depth": 1, ... },
     *   "server_public_key": "base64-encoded-key",
     *   "revocation_list": { "version": 0, "revoked_devices": [], "revoked_users": [] },
     *   "device_id": "...",
     *   "user_id": "..."
     * }
     *
     * @param configJson full initialization config from webapp
     * @return JSON: {"ok": true} or {"ok": false, "error": "..."}
     */
    @JavascriptInterface
    public String p2pInitialize(String configJson) {
        try {
            JSONObject json = new JSONObject(configJson);

            // Parse P2P config
            JSONObject configObj = json.optJSONObject("config");
            P2pConfig config = configObj != null ? P2pConfig.fromJson(configObj) : P2pConfig.defaults();

            // Parse scope manifest
            JSONObject scopeObj = json.getJSONObject("scope_manifest");
            ScopeManifest scopeManifest = ScopeManifest.fromJson(scopeObj);

            // Server public key for JWT verification
            String serverPublicKey = json.getString("server_public_key");

            // Revocation list (optional, defaults to empty)
            JSONObject revObj = json.optJSONObject("revocation_list");
            RevocationList revocationList = revObj != null
                    ? RevocationList.fromJson(revObj)
                    : RevocationList.empty();

            // Device and user identifiers
            String deviceId = json.getString("device_id");
            String userId = json.getString("user_id");

            // Cache JWT for peer-side auth with supervisor
            String jwt = json.optString("token", null);
            if (jwt != null && !jwt.isEmpty()) {
                cachedJwt = jwt;
                Log.d(TAG, "JWT token cached for P2P auth");
            }

            PouchDbBridge realBridge = createPouchDbBridge();
            p2pManager.initialize(config, serverPublicKey, revocationList,
                    scopeManifest, realBridge, deviceId, userId);

            JSONObject result = new JSONObject();
            result.put("ok", true);
            return result.toString();

        } catch (P2pManager.P2pInitException e) {
            Log.e(TAG, "P2P initialization failed", e);
            return errorJson("init_failed: " + e.getMessage());
        } catch (JSONException e) {
            Log.e(TAG, "Invalid config JSON", e);
            return errorJson("invalid_config: " + e.getMessage());
        } catch (RuntimeException e) {
            Log.e(TAG, "Unexpected error during P2P initialization", e);
            return errorJson("init_error: " + e.getMessage());
        }
    }

    /**
     * Check if P2P sync is currently active (host or client mode).
     * Used by webapp to conditionally pause server replication.
     */
    @JavascriptInterface
    public String p2pIsActive() {
        try {
            JSONObject result = new JSONObject();
            result.put("active", p2pManager.isHostModeActive() || p2pManager.isClientModeActive());
            return result.toString();
        } catch (JSONException e) {
            return errorJson("check_failed: " + e.getMessage());
        }
    }

    // --- Private helpers ---

    private static final long POUCHDB_TIMEOUT_SEC = 30;

    /**
     * Synchronously evaluate a JS expression against PouchDB from a background thread.
     * Uses a bridge callback (p2pAsyncCallback) instead of timed polling —
     * the Promise .then() calls back into Java directly, so there's no race condition.
     */
    private String evalPouchDb(String jsExpression) {
        if (webView == null) {
            Log.e(TAG, "evalPouchDb: webView is null");
            return "[]";
        }

        String callbackId = "__p2p_cb_" + System.nanoTime();
        CountDownLatch latch = new CountDownLatch(1);
        asyncCallbackLatches.put(callbackId, latch);

        webView.post(() -> {
            // Promise resolves → calls bridge callback directly (no timing dependency)
            String js = "Promise.resolve(" + jsExpression + ").then(function(r) {" +
                "  medicmobile_android.p2pAsyncCallback('" + callbackId + "', r);" +
                "}).catch(function(e) {" +
                "  medicmobile_android.p2pAsyncCallback('" + callbackId + "'," +
                "    JSON.stringify({error: e.message || 'unknown'}));" +
                "});";

            webView.evaluateJavascript(js, ignored -> { /* fire and forget */ });
        });

        try {
            if (!latch.await(POUCHDB_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                Log.e(TAG, "evalPouchDb: timed out waiting for callback " + callbackId);
                return "[]";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[]";
        } finally {
            asyncCallbackLatches.remove(callbackId);
        }

        String result = asyncCallbackResults.remove(callbackId);
        return result != null ? result : "[]";
    }

    /**
     * Save sync log to PouchDB so history persists across app restarts.
     * Host (supervisor) saves to _local/p2p-relay-log.
     * Peer (CHW) saves to _local/p2p-sync-log.
     * Merges new sessions with existing ones, keeping last 50.
     */
    private void saveSyncLogToPouchDb(boolean isHost) {
        if (tracker == null || webView == null) {
            Log.w(TAG, "saveSyncLogToPouchDb: tracker or webView is null, skipping");
            return;
        }
        try {
            JSONObject log;
            String docId;
            if (isHost) {
                log = tracker.buildRelayLog();
                docId = "_local/p2p-relay-log";
            } else {
                log = tracker.buildSyncLog();
                docId = "_local/p2p-sync-log";
            }

            JSONArray newSessions = log.optJSONArray("sessions");
            if (newSessions == null || newSessions.length() == 0) {
                Log.d(TAG, "saveSyncLogToPouchDb: no sessions to save");
                return;
            }

            // Escape the sessions JSON for embedding in JS string
            String sessionsJsonStr = newSessions.toString()
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n");

            // JS: get existing doc (or create new), merge sessions, keep last 50, put
            String js =
                "(async function() {" +
                "  var db = window.CHTCore.DB.get();" +
                "  var docId = '" + docId + "';" +
                "  var newSessions = JSON.parse('" + sessionsJsonStr + "');" +
                "  var doc;" +
                "  try { doc = await db.get(docId); } catch(e) { doc = { _id: docId, sessions: [] }; }" +
                "  var existing = doc.sessions || [];" +
                "  var existingIds = new Set(existing.map(function(s) { return s.session_id; }));" +
                "  for (var i = 0; i < newSessions.length; i++) {" +
                "    if (!existingIds.has(newSessions[i].session_id)) {" +
                "      existing.push(newSessions[i]);" +
                "    }" +
                "  }" +
                "  doc.sessions = existing.slice(-50);" +
                "  await db.put(doc);" +
                "  return JSON.stringify({ ok: true, total: doc.sessions.length });" +
                "})()";

            String result = evalPouchDb(js);
            Log.i(TAG, "saveSyncLogToPouchDb: saved " + docId + " result=" + result);
        } catch (JSONException e) {
            Log.e(TAG, "saveSyncLogToPouchDb: failed to save", e);
        }
    }

    /**
     * Save a pre-built log JSON to PouchDB.
     * Used by p2pStop() to avoid race condition — the log is built synchronously
     * before shutdown clears state, then this method just writes the pre-built data.
     */
    private void savePrebuiltLogToPouchDb(JSONObject log, String docId) {
        if (webView == null) {
            Log.w(TAG, "savePrebuiltLogToPouchDb: webView is null, skipping");
            return;
        }
        try {
            JSONArray newSessions = log.optJSONArray("sessions");
            if (newSessions == null || newSessions.length() == 0) {
                Log.d(TAG, "savePrebuiltLogToPouchDb: no sessions to save");
                return;
            }

            String sessionsJsonStr = newSessions.toString()
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n");

            String js =
                "(async function() {" +
                "  var db = window.CHTCore.DB.get();" +
                "  var docId = '" + docId + "';" +
                "  var newSessions = JSON.parse('" + sessionsJsonStr + "');" +
                "  var doc;" +
                "  try { doc = await db.get(docId); } catch(e) { doc = { _id: docId, sessions: [] }; }" +
                "  var existing = doc.sessions || [];" +
                "  var existingIds = new Set(existing.map(function(s) { return s.session_id; }));" +
                "  for (var i = 0; i < newSessions.length; i++) {" +
                "    if (!existingIds.has(newSessions[i].session_id)) {" +
                "      existing.push(newSessions[i]);" +
                "    }" +
                "  }" +
                "  doc.sessions = existing.slice(-50);" +
                "  await db.put(doc);" +
                "  return JSON.stringify({ ok: true, total: doc.sessions.length });" +
                "})()";

            String result = evalPouchDb(js);
            Log.i(TAG, "savePrebuiltLogToPouchDb: saved " + docId + " result=" + result);
        } catch (RuntimeException e) {
            Log.e(TAG, "savePrebuiltLogToPouchDb: failed to save", e);
        }
    }

    /**
     * Persist transit doc state to PouchDB (_local/p2p-transit-docs).
     * Called from p2pStop() on a background thread so the transit index survives
     * app restart. Merges with any existing doc (preserves _rev for update).
     */
    private void saveTransitStateToPouchDb(JSONObject transitJson) {
        if (webView == null) {
            Log.w(TAG, "saveTransitStateToPouchDb: webView is null, skipping");
            return;
        }
        try {
            String docId = TransitDocManager.TRANSIT_DOC_ID;
            String escaped = transitJson.toString()
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n");

            String js =
                "(async function() {" +
                "  var db = window.CHTCore.DB.get();" +
                "  var newDoc = JSON.parse('" + escaped + "');" +
                "  try {" +
                "    var existing = await db.get('" + docId + "');" +
                "    newDoc._rev = existing._rev;" +
                "  } catch(e) {}" +
                "  await db.put(newDoc);" +
                "  return JSON.stringify({ ok: true });" +
                "})()";

            String result = evalPouchDb(js);
            Log.i(TAG, "saveTransitStateToPouchDb: saved " + docId + " result=" + result);
        } catch (RuntimeException e) {
            Log.e(TAG, "saveTransitStateToPouchDb: failed to save", e);
        }
    }

    private PouchDbBridge createPouchDbBridge() {
        return new PouchDbBridge() {
            @Override
            public String getAllDocIds() {
                return evalPouchDb(
                    "window.CHTCore.DB.get().allDocs().then(function(result) {" +
                    "  return JSON.stringify(result.rows.map(function(r) {" +
                    "    return { _id: r.id, _rev: r.value.rev };" +
                    "  }));" +
                    "})"
                );
            }

            @Override
            public String getDocsByIds(String idsJson) {
                String escaped = idsJson.replace("\\", "\\\\").replace("'", "\\'");
                return evalPouchDb(
                    "window.CHTCore.DB.get().allDocs({ keys: JSON.parse('" + escaped + "'), include_docs: true }).then(function(result) {" +
                    "  return JSON.stringify(result.rows.filter(function(r) { return r.doc; }).map(function(r) { return r.doc; }));" +
                    "})"
                );
            }

            @Override
            public String writeDocs(String docsJson) {
                String escaped = docsJson.replace("\\", "\\\\").replace("'", "\\'");
                return evalPouchDb(
                    "window.CHTCore.DB.get().bulkDocs(JSON.parse('" + escaped + "'), { new_edits: false }).then(function(result) {" +
                    "  return JSON.stringify(result);" +
                    "})"
                );
            }

            @Override
            public String queryContactsByDepth(String facilityId, int maxDepth) {
                String escapedFacility = facilityId.replace("\\", "\\\\").replace("'", "\\'");
                return evalPouchDb(
                    "window.CHTCore.DB.get().query('medic-client/contacts_by_depth', {" +
                    "  startkey: ['" + escapedFacility + "']," +
                    "  endkey: ['" + escapedFacility + "', " + maxDepth + ", {}]" +
                    "}).then(function(result) {" +
                    "  var ids = [];" +
                    "  var seen = {};" +
                    "  result.rows.forEach(function(r) {" +
                    "    if (!seen[r.id]) { seen[r.id] = true; ids.push(r.id); }" +
                    "  });" +
                    "  return JSON.stringify(ids);" +
                    "})"
                );
            }
        };
    }

    private Object getCapabilityReason(P2pManager.P2pCapability capability) {
        switch (capability) {
            case FULLY_SUPPORTED:
                return JSONObject.NULL;
            case SUPPORTED_NO_CAMERA:
                return "No camera available. QR scanning disabled; Host mode only.";
            case UNSUPPORTED_API_LEVEL:
                return "Android 8.0 (API 26) or higher required. Current API level: "
                        + Build.VERSION.SDK_INT;
            case NO_WIFI_HARDWARE:
                return "WiFi hardware not available on this device.";
            case LOW_RAM_WARNING:
                return "Low RAM detected. P2P sync may be slow.";
            case LOW_STORAGE:
                return "Insufficient storage. Free up space before syncing.";
            case LOW_BATTERY_WARNING:
                return "Low battery. Charge device before starting P2P sync.";
            case PERMISSION_NEEDED:
                return "WiFi permissions required. Please grant permissions.";
            case LOCATION_SERVICES_OFF:
                return "Location services must be enabled to start WiFi hotspot. "
                        + "Please turn on Location in Settings.";
            default:
                return JSONObject.NULL;
        }
    }

    private String errorJson(String error) {
        try {
            JSONObject json = new JSONObject();
            json.put(KEY_OK, false);
            json.put(KEY_ERROR, error);
            return json.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Error building error JSON", e);
            return "{\"ok\":false,\"error\":\"internal_error\"}";
        }
    }

    private String buildEmptyPurgeResponse() {
        try {
            JSONObject response = new JSONObject();
            response.put("batches", new JSONArray());
            response.put("total_docs", 0);
            return response.toString();
        } catch (JSONException e) {
            return "{\"batches\":[],\"total_docs\":0}";
        }
    }

    private String buildEmptySyncHistory() {
        try {
            JSONObject log = new JSONObject();
            log.put("_id", "_local/p2p-sync-log");
            log.put("sessions", new JSONArray());
            return log.toString();
        } catch (JSONException e) {
            return "{\"_id\":\"_local/p2p-sync-log\",\"sessions\":[]}";
        }
    }

    private String buildIdleStatus() {
        try {
            JSONObject status = new JSONObject();
            status.put("initialized", false);
            status.put("supervisor_mode_active", false);
            status.put("chw_mode_active", false);
            status.put("p2p_enabled", false);
            status.put("pending_transit_docs", 0);
            return status.toString();
        } catch (JSONException e) {
            return "{\"initialized\":false}";
        }
    }
}
