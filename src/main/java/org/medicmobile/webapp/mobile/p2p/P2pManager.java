package org.medicmobile.webapp.mobile.p2p;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

/**
 * P2pManager orchestrates the entire P2P sync lifecycle:
 *
 * 1. checkCapability() -- verify device supports P2P
 * 2. initialize()      -- setup components from cached config
 * 3. startHostMode()       -- start hotspot + HTTP server + show QR
 * 4. startClientMode()    -- scan QR + connect to host + sync
 * 5. shutdown()        -- tear down everything cleanly
 *
 * Host flow: capability check -> OEM guidance -> acquire mutex ->
 *   start foreground service -> start hotspot -> start HTTP server ->
 *   generate QR -> wait for clients -> sync -> teardown
 *
 * Client flow: capability check -> scan QR -> connect WiFi ->
 *   authenticate -> preview -> sync docs -> disconnect
 *
 * This is the single entry point for all P2P operations. All errors are
 * handled gracefully with cleanup -- no unhandled exceptions escape.
 */
public class P2pManager {

    private static final String TAG = "P2pManager";

    // Singleton instance — survives activity recreation
    private static volatile P2pManager instance;

    // QR payload constants (CONTRACT.md Section 9)
    private static final String QR_TYPE = "cht-p2p";
    private static final int QR_VERSION = 1;
    private static final long QR_MAX_AGE_MS = 10 * 60 * 1000; // G18: 10 minutes

    // Device resource thresholds
    private static final int MIN_API_LEVEL = Build.VERSION_CODES.O; // API 26
    private static final long MIN_RAM_MB = 1024; // 1 GB
    private static final long LOW_RAM_MB = 2048; // 2 GB warning threshold
    private static final long MIN_STORAGE_MB = 100; // 100 MB
    private static final int MIN_BATTERY_PERCENT = 15;

    private final Context context;

    // Components -- set during initialize()
    private P2pConfig config;
    private SyncMutex syncMutex;
    private P2pAuthenticator authenticator;
    private ScopeManifest localScope;
    private TransitDocManager transitDocManager;
    private P2pTracker tracker;
    private P2pTelemetryReporter telemetryReporter;
    private WifiHotspotManager hotspotManager;
    private LocalHttpServer httpServer;
    private P2pNotificationChannel notificationChannel;
    private PouchDbBridge pouchDbBridge;
    private String localDeviceId;
    private String localUserId;

    // State
    private volatile boolean initialized = false;
    private volatile boolean hostModeActive = false;
    private volatile boolean clientModeActive = false;
    private volatile String currentQrSessionTs = null; // G21: tracks current QR timestamp
    private ConnectivityManager.NetworkCallback wifiNetworkCallback = null;
    private volatile Network p2pWifiNetwork = null;

    public static P2pManager getInstance(Context context) {
        if (instance == null) {
            synchronized (P2pManager.class) {
                if (instance == null) {
                    instance = new P2pManager(context);
                }
            }
        }
        return instance;
    }

    private P2pManager(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        this.context = context.getApplicationContext();
    }

    // -----------------------------------------------------------------------
    // Capability
    // -----------------------------------------------------------------------

    /**
     * P2pCapability check result -- can this device do P2P?
     */
    public enum P2pCapability {
        FULLY_SUPPORTED,
        SUPPORTED_NO_CAMERA,
        UNSUPPORTED_API_LEVEL,
        NO_WIFI_HARDWARE,
        LOW_RAM_WARNING,
        LOW_STORAGE,
        LOW_BATTERY_WARNING,
        PERMISSION_NEEDED,
        LOCATION_SERVICES_OFF
    }

    /**
     * Check whether the device can participate in P2P sync.
     * Returns the most severe issue found, or FULLY_SUPPORTED / SUPPORTED_NO_CAMERA.
     */
    public P2pCapability checkCapability() {
        // Hard block: API level
        if (Build.VERSION.SDK_INT < MIN_API_LEVEL) {
            Log.w(TAG, "Unsupported API level: " + Build.VERSION.SDK_INT
                    + " (min " + MIN_API_LEVEL + ")");
            return P2pCapability.UNSUPPORTED_API_LEVEL;
        }

        // Hard block: WiFi hardware
        WifiManager wifiManager = (WifiManager) context
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Log.w(TAG, "No WifiManager available");
            return P2pCapability.NO_WIFI_HARDWARE;
        }

        // Hard block: insufficient storage
        long freeStorageMb = getAvailableStorageMb();
        if (freeStorageMb < MIN_STORAGE_MB) {
            Log.w(TAG, "Low storage: " + freeStorageMb + " MB free");
            return P2pCapability.LOW_STORAGE;
        }

        // Hard block: WiFi permission
        if (!hasWifiPermissions()) {
            Log.w(TAG, "Missing WiFi permissions");
            return P2pCapability.PERMISSION_NEEDED;
        }

        // Hard block: Location services must be enabled for LocalOnlyHotspot on all API levels
        android.location.LocationManager lm = (android.location.LocationManager)
                context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null || !lm.isLocationEnabled()) {
            Log.w(TAG, "Location services disabled (required for hotspot)");
            return P2pCapability.LOCATION_SERVICES_OFF;
        }

        // Soft warning: low battery
        int batteryPercent = getBatteryPercent();
        if (batteryPercent >= 0 && batteryPercent < MIN_BATTERY_PERCENT) {
            Log.w(TAG, "Low battery: " + batteryPercent + "%");
            return P2pCapability.LOW_BATTERY_WARNING;
        }

        // Soft warning: low RAM
        long totalRamMb = getTotalRamMb();
        if (totalRamMb > 0 && totalRamMb < LOW_RAM_MB) {
            if (totalRamMb < MIN_RAM_MB) {
                Log.w(TAG, "Very low RAM: " + totalRamMb + " MB");
            }
            Log.w(TAG, "Low RAM warning: " + totalRamMb + " MB");
            return P2pCapability.LOW_RAM_WARNING;
        }

        // Check camera (needed for QR scanning on CHW side, but not a hard block)
        boolean hasCamera = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        if (!hasCamera) {
            Log.i(TAG, "No camera -- Supervisor mode only (no QR scanning)");
            return P2pCapability.SUPPORTED_NO_CAMERA;
        }

        Log.i(TAG, "Device fully supports P2P sync");
        return P2pCapability.FULLY_SUPPORTED;
    }

    // -----------------------------------------------------------------------
    // Initialize
    // -----------------------------------------------------------------------

    /**
     * Initialize P2P components from cached config.
     * Must be called before startHostMode() or startClientMode().
     *
     * @param config          P2P config from app_settings
     * @param serverPublicKey PEM-encoded ECDSA P-256 public key for JWT verification
     * @param revocationList  cached device/user revocation list
     * @param localScope      this device's scope manifest
     * @param bridge          PouchDB bridge for data access
     * @param deviceId        this device's unique identifier
     * @param userId          current user's ID
     * @throws P2pInitException if initialization fails
     */
    public void initialize(P2pConfig config, String serverPublicKey,
                           RevocationList revocationList, ScopeManifest localScope,
                           PouchDbBridge bridge, String deviceId, String userId)
            throws P2pInitException {

        if (initialized) {
            Log.w(TAG, "Already initialized, re-initializing");
            shutdownInternal(false);
        }

        if (config == null) {
            throw new P2pInitException("config must not be null");
        }
        if (!config.isEnabled()) {
            throw new P2pInitException("P2P sync is disabled in config");
        }
        if (serverPublicKey == null || serverPublicKey.isEmpty()) {
            throw new P2pInitException("serverPublicKey must not be null or empty");
        }
        if (revocationList == null) {
            throw new P2pInitException("revocationList must not be null");
        }
        if (localScope == null) {
            throw new P2pInitException("localScope must not be null");
        }
        if (bridge == null) {
            throw new P2pInitException("bridge must not be null");
        }
        if (deviceId == null || deviceId.isEmpty()) {
            throw new P2pInitException("deviceId must not be null or empty");
        }
        if (userId == null || userId.isEmpty()) {
            throw new P2pInitException("userId must not be null or empty");
        }

        try {
            this.config = config;
            this.localScope = localScope;
            this.pouchDbBridge = bridge;
            this.localDeviceId = deviceId;
            this.localUserId = userId;

            // Auth chain: JwtVerifier -> P2pAuthenticator
            JwtVerifier jwtVerifier = new JwtVerifier(serverPublicKey);
            this.authenticator = new P2pAuthenticator(jwtVerifier, revocationList);

            // Transit
            this.transitDocManager = new TransitDocManager();

            // Mutex with server reachability check (always returns false when
            // in P2P mode since we are offline by definition)
            this.syncMutex = new SyncMutex(new SyncMutex.ServerReachabilityChecker() {
                @Override
                public boolean isServerReachable() {
                    // During P2P sync, we assume server is not reachable.
                    // The webapp's normal replication handles server connectivity.
                    return false;
                }
            });

            // Tracking and telemetry
            this.tracker = new P2pTracker();
            this.telemetryReporter = new P2pTelemetryReporter(deviceId, userId, context);

            // Notifications
            this.notificationChannel = new P2pNotificationChannel(context);
            this.notificationChannel.createChannel();

            this.initialized = true;
            Log.i(TAG, "P2P components initialized for user=" + userId
                    + " device=" + deviceId);

        } catch (JwtVerifier.JwtVerificationException e) {
            throw new P2pInitException("Failed to initialize JWT verifier: " + e.getMessage());
        } catch (Exception e) {
            throw new P2pInitException("Initialization failed: " + e.getMessage());
        }
    }

    /**
     * Load persisted transit doc state from PouchDB.
     * Call after initialize() to restore transit tracking across restarts.
     *
     * @param transitDocJson the _local/p2p-transit-docs JSON, or null if none exists
     */
    public void loadTransitState(JSONObject transitDocJson) {
        ensureInitialized();
        if (transitDocJson != null && transitDocManager != null) {
            try {
                transitDocManager.loadFromJson(transitDocJson);
                Log.i(TAG, "Loaded transit doc state, pending="
                        + transitDocManager.getPendingPushCount());
            } catch (JSONException e) {
                Log.e(TAG, "Failed to load transit doc state", e);
            }
        }
    }

    /**
     * Load persisted sync log from PouchDB.
     * Call after initialize() to restore session history across restarts.
     *
     * @param syncLogJson the _local/p2p-sync-log or _local/p2p-relay-log JSON
     */
    public void loadSyncLog(JSONObject syncLogJson) {
        ensureInitialized();
        if (syncLogJson != null && tracker != null) {
            try {
                tracker.loadFromSyncLog(syncLogJson);
            } catch (JSONException e) {
                Log.e(TAG, "Failed to load sync log", e);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Supervisor Mode
    // -----------------------------------------------------------------------

    /**
     * Start Supervisor mode: hotspot + HTTP server + QR code.
     *
     * Flow:
     * 1. Check capability
     * 2. Show OEM battery guidance if needed (via callback)
     * 3. Acquire sync mutex (G10)
     * 4. Start foreground service
     * 5. Start hotspot
     * 6. Start HTTP server on hotspot IP
     * 7. Generate QR code payload
     * 8. Callback with QR data
     *
     * @param callback receives lifecycle events
     */
    public void startHostMode(final HostModeCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        ensureInitialized();

        if (hostModeActive) {
            // Allow re-entry: shutdown previous supervisor mode first
            Log.w(TAG, "Supervisor mode already active, shutting down previous session");
            shutdownInternal(false);
        }
        if (clientModeActive) {
            Log.w(TAG, "CHW mode active, shutting down before starting supervisor");
            shutdownClientMode();
        }

        // Step 1: Capability check
        P2pCapability capability = checkCapability();
        if (capability == P2pCapability.UNSUPPORTED_API_LEVEL
                || capability == P2pCapability.NO_WIFI_HARDWARE
                || capability == P2pCapability.LOW_STORAGE
                || capability == P2pCapability.PERMISSION_NEEDED) {
            callback.onError("device_not_capable: " + capability.name());
            return;
        }

        // Step 2: OEM battery guidance (informational, does not block)
        if (OemBatteryHelper.needsBatteryGuidance()) {
            String guidance = OemBatteryHelper.getGuidance();
            Log.i(TAG, "OEM battery guidance (" + OemBatteryHelper.getManufacturer()
                    + "): " + guidance);
            callback.onBatteryGuidance(OemBatteryHelper.getRiskLevel(), guidance);
        }

        // Step 3: Acquire sync mutex (G10)
        if (!syncMutex.tryAcquire(SyncMutex.SyncType.P2P)) {
            callback.onError("sync_mutex_busy: another sync is already active");
            return;
        }

        boolean mutexAcquired = true;
        try {
            // Step 4: Start foreground service
            P2pForegroundService.start(context);
            Log.i(TAG, "Foreground service started");

            // Step 5: Start hotspot
            hostModeActive = true;
            startHotspotForSupervisor(callback);

        } catch (Exception e) {
            Log.e(TAG, "Failed to start supervisor mode", e);
            hostModeActive = false;
            if (mutexAcquired) {
                syncMutex.release();
            }
            try {
                P2pForegroundService.stop(context);
            } catch (Exception ignored) {
                // Best-effort cleanup
            }
            callback.onError("supervisor_start_failed: " + e.getMessage());
        }
    }

    /**
     * Internal: start the hotspot and wire up the HTTP server on success.
     */
    private void startHotspotForSupervisor(final HostModeCallback callback) {
        // Choose hotspot provider: loopback for emulators, real WiFi otherwise
        HotspotProvider provider = createHotspotProvider();
        hotspotManager = new WifiHotspotManager(provider, config);

        hotspotManager.startHotspot(new HotspotProvider.HotspotCallback() {
            @Override
            public void onStarted(String ssid, String password, String ipAddress) {
                Log.i(TAG, "Hotspot started: SSID=" + ssid + " IP=" + ipAddress);

                try {
                    // Step 6: Start HTTP server
                    startHttpServer(ipAddress, callback);

                    // Step 7: Generate QR payload
                    String qrPayload = buildQrPayload(ssid, password,
                            ipAddress, httpServer.getPort());
                    currentQrSessionTs = String.valueOf(System.currentTimeMillis());

                    Log.i(TAG, "Supervisor mode ready, QR generated");
                    callback.onQrCodeReady(qrPayload);

                } catch (Exception e) {
                    Log.e(TAG, "Failed after hotspot started", e);
                    shutdownInternal(true);
                    callback.onError("server_start_failed: " + e.getMessage());
                }
            }

            @Override
            public void onFailed(String reason) {
                Log.e(TAG, "Hotspot failed to start: " + reason);
                hostModeActive = false;
                syncMutex.release();
                try {
                    P2pForegroundService.stop(context);
                } catch (Exception ignored) {
                    // Best-effort cleanup
                }
                callback.onError("hotspot_failed: " + reason);
            }
        });
    }

    /**
     * Internal: create and start the LocalHttpServer.
     */
    private void startHttpServer(String ipAddress,
                                 final HostModeCallback callback)
            throws IOException {

        // TransitDocCallback bridges AcceptDocsEndpoint -> TransitDocManager
        TransitDocCallback transitCallback = new TransitDocCallback() {
            @Override
            public void trackTransitDocs(List<String> docIds,
                                         String sourceDeviceId,
                                         String sourceUserId) {
                if (transitDocManager == null || docIds == null || docIds.isEmpty()) {
                    return;
                }
                String batchId = transitDocManager.startBatch(sourceDeviceId, sourceUserId);
                for (String docId : docIds) {
                    transitDocManager.trackTransitDoc(batchId, docId);
                }
                Log.d(TAG, "Tracked " + docIds.size()
                        + " transit docs in batch " + batchId);
            }
        };

        httpServer = new LocalHttpServer(
                authenticator, config, localScope, pouchDbBridge,
                transitCallback);

        // Wire up session-complete callback so tracker records the session.
        // Host-side sessions are managed by LocalHttpServer, not tracker,
        // so we use recordCompletedSession() instead of completeSession().
        httpServer.setSessionCompleteCallback(session -> {
            if (tracker != null && session != null) {
                tracker.recordCompletedSession(session);
                Log.i(TAG, "Tracker recorded host-side sync session: "
                        + session.getSessionId());
            }
        });

        httpServer.startServer();
        Log.i(TAG, "HTTP server started on port " + httpServer.getPort());
    }

    /**
     * Notify the manager that a peer has connected (called from HTTP server layer).
     * Updates the tracker and relays to the callback.
     */
    public void onPeerConnected(String peerId, HostModeCallback callback) {
        if (!hostModeActive) {
            return;
        }
        syncMutex.touchActivity();
        if (hotspotManager != null) {
            hotspotManager.recordActivity();
        }
        Log.i(TAG, "Peer connected: " + peerId);
        if (callback != null) {
            callback.onPeerConnected(peerId);
        }
    }

    /**
     * Notify the manager of sync progress (called from HTTP server layer).
     * Updates the notification and relays to the callback.
     */
    public void onSyncProgress(int docsSynced, int totalDocs,
                                HostModeCallback callback) {
        if (!hostModeActive) {
            return;
        }
        syncMutex.touchActivity();
        if (hotspotManager != null) {
            hotspotManager.recordActivity();
        }
        if (notificationChannel != null) {
            notificationChannel.updateProgress(docsSynced, totalDocs);
        }
        if (callback != null) {
            callback.onSyncProgress(docsSynced, totalDocs);
        }
    }

    /**
     * Notify the manager that a sync session completed.
     */
    public void onSyncComplete(P2pSession session, HostModeCallback callback) {
        if (session != null) {
            Log.i(TAG, "Sync session completed: " + session);
        }
        if (notificationChannel != null && session != null) {
            int total = session.getDocsPushed() + session.getDocsPulled()
                    + session.getTransitDocs();
            notificationChannel.showComplete(total);
        }
        if (callback != null) {
            callback.onSyncComplete(session);
        }
    }

    // -----------------------------------------------------------------------
    // CHW Mode
    // -----------------------------------------------------------------------

    /**
     * Start CHW mode: parse QR payload, validate, then connect to supervisor.
     *
     * Flow:
     * 1. Parse QR payload
     * 2. Validate QR (G18: timestamp, G19: type, G21: version)
     * 3. Connect to supervisor's WiFi (out-of-band -- caller handles WiFi connection)
     * 4. Authenticate with supervisor (caller calls authenticateWithSupervisor)
     *
     * Note: actual WiFi connection is handled by the Android system when the user
     * selects the network. This method validates the QR and returns connection info.
     *
     * @param qrPayloadJson the JSON string scanned from the QR code
     * @param callback      receives lifecycle events
     */
    public void startClientMode(String qrPayloadJson, final ClientModeCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        ensureInitialized();

        if (clientModeActive) {
            // Allow re-entry: shutdown previous CHW mode first
            Log.w(TAG, "CHW mode already active, shutting down previous session");
            shutdownClientMode();
        }
        if (hostModeActive) {
            callback.onError("host_mode_active: shutdown supervisor mode first");
            return;
        }

        // Step 1: Parse QR payload
        JSONObject qrPayload;
        try {
            qrPayload = new JSONObject(qrPayloadJson);
        } catch (JSONException e) {
            callback.onError("qr_parse_failed: invalid JSON");
            return;
        }

        // Step 2: Validate QR payload
        String validationError = validateQrPayload(qrPayload);
        if (validationError != null) {
            callback.onError(validationError);
            return;
        }

        // Step 3: Acquire sync mutex (G10)
        if (!syncMutex.tryAcquire(SyncMutex.SyncType.P2P)) {
            callback.onError("sync_mutex_busy: another sync is already active");
            return;
        }

        clientModeActive = true;

        // Extract connection details from QR
        String ssid = qrPayload.optString("ssid", "");
        String password = qrPayload.optString("pwd", "");
        String ip = qrPayload.optString("ip", "");
        int port = qrPayload.optInt("port", 8443);
        String tlsFingerprint = qrPayload.optString("tls", "");

        Log.i(TAG, "CHW mode started: supervisor at " + ip + ":" + port
                + " via SSID=" + ssid + " — attempting auto WiFi connection");

        // Try auto-connect via WifiNetworkSpecifier (API 29+)
        // Falls back to manual if API < 29 or user declines dialog
        connectToHotspotWifi(ssid, password, ip, port, tlsFingerprint, callback);
    }

    /**
     * Connect to the supervisor's WiFi hotspot using WifiNetworkSpecifier (API 29+).
     */
    private void connectToHotspotWifi(final String ssid, final String password,
                                       final String ip, final int port,
                                       final String tlsFingerprint,
                                       final ClientModeCallback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "WifiNetworkSpecifier requires API 29+, skipping auto-connect");
            callback.onConnectionInfoReady(ssid, password, ip, port, tlsFingerprint);
            return;
        }

        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            Log.e(TAG, "No ConnectivityManager, falling back to manual connect");
            callback.onConnectionInfoReady(ssid, password, ip, port, tlsFingerprint);
            return;
        }

        WifiNetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build();

        // Build request WITHOUT internet capability — hotspot has no internet
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build();

        wifiNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                Log.i(TAG, "Connected to hotspot WiFi: " + ssid);
                p2pWifiNetwork = network;
                // Per-connection routing via network.openConnection() in P2pSyncClient
                // DO NOT call bindProcessToNetwork — it breaks WebView
                callback.onConnectionInfoReady(ssid, password, ip, port, tlsFingerprint);
            }

            @Override
            public void onUnavailable() {
                Log.w(TAG, "WiFi auto-connect unavailable for SSID=" + ssid
                        + " — falling back to manual connect");
                // Don't shut down — let user connect manually
                // Provide credentials so webapp can display them
                callback.onError("wifi_connection_failed");
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.w(TAG, "Lost hotspot WiFi connection: " + ssid);
                p2pWifiNetwork = null;
            }
        };

        Log.i(TAG, "Requesting WiFi connection to SSID=" + ssid
                + " (no internet capability required)");
        cm.requestNetwork(request, wifiNetworkCallback);
    }

    /**
     * Authenticate with the supervisor's HTTP server (CHW side).
     * Called after the CHW has connected to the supervisor's WiFi network.
     *
     * @param supervisorHost supervisor's IP address
     * @param supervisorPort supervisor's HTTP server port
     * @param jwt            this device's P2P JWT token
     * @param callback       receives lifecycle events
     */
    public void authenticateWithSupervisor(String supervisorHost, int supervisorPort,
                                            String jwt, final ClientModeCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        if (!clientModeActive) {
            callback.onError("chw_mode_not_active: call startClientMode() first");
            return;
        }
        ensureInitialized();

        if (jwt == null || jwt.isEmpty()) {
            shutdownClientMode();
            callback.onError("jwt_empty");
            return;
        }

        syncMutex.touchActivity();

        // The actual HTTP request to POST /_p2p/auth on the supervisor is
        // handled by the webapp's JavaScript layer via the PouchDB bridge.
        // This method records the intent and prepares the tracker.
        Log.i(TAG, "CHW authenticating with supervisor at "
                + supervisorHost + ":" + supervisorPort);

        callback.onConnected(supervisorHost);
    }

    /**
     * Notify the manager of CHW-side sync progress.
     */
    public void onClientSyncProgress(int docsSynced, int totalDocs,
                                   ClientModeCallback callback) {
        if (!clientModeActive) {
            return;
        }
        syncMutex.touchActivity();
        if (callback != null) {
            callback.onSyncProgress(docsSynced, totalDocs);
        }
    }

    /**
     * Notify the manager that CHW-side sync completed.
     */
    public void onClientSyncComplete(P2pSession session, ClientModeCallback callback) {
        if (session != null) {
            Log.i(TAG, "CHW sync session completed: " + session);
            if (tracker != null) {
                tracker.completeSession();
            }
        }
        clientModeActive = false;
        syncMutex.release();
        if (callback != null) {
            callback.onSyncComplete(session);
        }
    }

    // -----------------------------------------------------------------------
    // Shutdown
    // -----------------------------------------------------------------------

    /**
     * Shutdown all P2P components cleanly.
     * Safe to call at any time, including if not initialized.
     */
    public void shutdown() {
        shutdownInternal(true);
    }

    /**
     * Internal shutdown with option to report telemetry.
     */
    private void shutdownInternal(boolean reportTelemetry) {
        Log.i(TAG, "Shutting down P2P components");

        // 1. Stop HTTP server
        if (httpServer != null) {
            try {
                httpServer.stopServer();
                Log.d(TAG, "HTTP server stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping HTTP server", e);
            }
            httpServer = null;
        }

        // 2. Stop hotspot
        if (hotspotManager != null) {
            try {
                hotspotManager.stopHotspot();
                Log.d(TAG, "Hotspot stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping hotspot", e);
            }
            hotspotManager = null;
        }

        // 3. Stop foreground service
        try {
            P2pForegroundService.stop(context);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping foreground service", e);
        }

        // 4. Release sync mutex
        if (syncMutex != null && syncMutex.isActive()) {
            try {
                syncMutex.release();
                Log.d(TAG, "Sync mutex released");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing sync mutex", e);
            }
        }

        // 5. Fail any in-progress tracker session
        if (tracker != null && tracker.hasActiveSession()) {
            tracker.failSession("shutdown");
        }

        // 6. Save transit doc state (best-effort)
        saveTransitState();

        // 7. Dismiss notifications
        if (notificationChannel != null) {
            try {
                notificationChannel.dismiss();
            } catch (Exception e) {
                Log.e(TAG, "Error dismissing notification", e);
            }
        }

        // 8. Unbind WiFi network and unregister callback
        if (wifiNetworkCallback != null) {
            try {
                ConnectivityManager cm = (ConnectivityManager) context
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.unregisterNetworkCallback(wifiNetworkCallback);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up WiFi callback", e);
            }
            wifiNetworkCallback = null;
        }

        // 9. Reset state
        hostModeActive = false;
        clientModeActive = false;
        currentQrSessionTs = null;

        Log.i(TAG, "P2P shutdown complete");
    }

    /**
     * Shutdown CHW mode only (does not affect supervisor components).
     */
    private void shutdownClientMode() {
        clientModeActive = false;
        p2pWifiNetwork = null;
        if (syncMutex != null && syncMutex.isActive()) {
            syncMutex.release();
        }
        if (tracker != null && tracker.hasActiveSession()) {
            tracker.failSession("chw_mode_shutdown");
        }
    }

    /**
     * Get the WiFi network obtained via auto-connect (WifiNetworkSpecifier).
     * Returns null if auto-connect was not used or the network was lost.
     */
    public Network getP2pWifiNetwork() {
        return p2pWifiNetwork;
    }

    // -----------------------------------------------------------------------
    // QR Payload
    // -----------------------------------------------------------------------

    /**
     * Build the QR code payload JSON string for Supervisor mode.
     * Format matches CONTRACT.md Section 9.
     */
    String buildQrPayload(String ssid, String password,
                           String ipAddress, int port) throws JSONException {
        JSONObject qr = new JSONObject();
        qr.put("type", QR_TYPE);
        qr.put("v", QR_VERSION);
        qr.put("ssid", ssid);
        qr.put("pwd", password);
        qr.put("ip", ipAddress);
        qr.put("port", port);
        qr.put("tls", ""); // TLS fingerprint set when HTTPS is configured
        qr.put("ts", System.currentTimeMillis());
        return qr.toString();
    }

    /**
     * Validate a QR payload scanned by the CHW.
     * Enforces guards G18, G19, G21.
     *
     * @return null if valid, or an error string describing the issue
     */
    String validateQrPayload(JSONObject qr) {
        if (qr == null) {
            return "qr_invalid: null payload";
        }

        // G19: type must be "cht-p2p"
        String type = qr.optString("type", "");
        if (!QR_TYPE.equals(type)) {
            return "qr_invalid_type: expected '" + QR_TYPE + "', got '" + type + "'";
        }

        // Version check
        int version = qr.optInt("v", 0);
        if (version < 1) {
            return "qr_invalid_version: " + version;
        }

        // G18: timestamp must be within 10 minutes
        long ts = qr.optLong("ts", 0);
        if (ts <= 0) {
            return "qr_missing_timestamp";
        }
        long age = Math.abs(System.currentTimeMillis() - ts);
        if (age > QR_MAX_AGE_MS) {
            return "qr_expired: age=" + (age / 1000) + "s (max "
                    + (QR_MAX_AGE_MS / 1000) + "s)";
        }

        // Required fields
        String ssid = qr.optString("ssid", "");
        if (ssid.isEmpty()) {
            return "qr_missing_ssid";
        }
        String ip = qr.optString("ip", "");
        if (ip.isEmpty()) {
            return "qr_missing_ip";
        }
        int port = qr.optInt("port", 0);
        if (port <= 0 || port > 65535) {
            return "qr_invalid_port: " + port;
        }

        return null; // Valid
    }

    // -----------------------------------------------------------------------
    // State Queries
    // -----------------------------------------------------------------------

    /** Check if P2P components have been initialized. */
    public boolean isInitialized() {
        return initialized;
    }

    /** Check if host mode is currently active. */
    public boolean isHostModeActive() {
        return hostModeActive;
    }

    /** Check if client mode is currently active. */
    public boolean isClientModeActive() {
        return clientModeActive;
    }

    /** Get the current P2P config, or null if not initialized. */
    public P2pConfig getConfig() {
        return config;
    }

    /** Get the tracker for session history. */
    public P2pTracker getTracker() {
        return tracker;
    }

    /** Get the transit doc manager. */
    public TransitDocManager getTransitDocManager() {
        return transitDocManager;
    }

    /** Get the telemetry reporter. */
    public P2pTelemetryReporter getTelemetryReporter() {
        return telemetryReporter;
    }

    /** Get connected peer count from HTTP server (0 if no server). */
    public int getConnectedPeerCount() {
        return httpServer != null ? httpServer.getConnectedPeerCount() : 0;
    }

    /** Get active HTTP session (peer authenticated), or null. */
    public P2pSession getHttpSession() {
        return httpServer != null ? httpServer.getActiveSession() : null;
    }

    /** Get local device ID (set during initialize). */
    public String getLocalDeviceId() {
        return localDeviceId;
    }

    /** Get the application context (needed for ConnectivityManager in peer sync). */
    public Context getContext() {
        return context;
    }

    /** Get the PouchDB bridge for direct data access. */
    public PouchDbBridge getBridge() {
        return pouchDbBridge;
    }

    /** Get the hotspot password (for display in UI). */
    public String getHotspotPassword() {
        return hotspotManager != null ? hotspotManager.getActivePassword() : null;
    }

    /** Check if there are stale transit docs needing attention (G27). */
    public boolean hasStaleTransitDocs() {
        return transitDocManager != null && transitDocManager.hasStaleTransitDocs();
    }

    /**
     * Get the current status as a JSON object for the webapp bridge.
     */
    public JSONObject getStatusJson() {
        try {
            JSONObject status = new JSONObject();
            status.put("initialized", initialized);
            status.put("host_mode_active", hostModeActive);
            status.put("client_mode_active", clientModeActive);
            status.put("p2p_enabled", config != null && config.isEnabled());

            if (transitDocManager != null) {
                status.put("pending_transit_docs",
                        transitDocManager.getPendingPushCount());
                status.put("has_stale_transit", transitDocManager.hasStaleTransitDocs());
            }
            if (tracker != null) {
                status.put("session_count", tracker.getSessionCount());
                status.put("has_active_session", tracker.hasActiveSession());
            }
            if (hotspotManager != null) {
                status.put("hotspot_active", hotspotManager.isActive());
                status.put("hotspot_ssid", hotspotManager.getActiveSsid());
            }

            return status;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to build status JSON", e);
            return new JSONObject();
        }
    }

    // -----------------------------------------------------------------------
    // Transit State Persistence
    // -----------------------------------------------------------------------

    /**
     * Save the current transit doc state to JSON.
     * Caller should persist this via PouchDB bridge to _local/p2p-transit-docs.
     *
     * @return the transit doc JSON, or null if no transit state exists
     */
    public JSONObject getTransitStateJson() {
        if (transitDocManager == null) {
            return null;
        }
        try {
            // G26: archive purged batches if oversized
            if (transitDocManager.isOversized()) {
                transitDocManager.archivePurgedBatches();
            }
            return transitDocManager.toJson();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to serialize transit state", e);
            return null;
        }
    }

    /**
     * Build telemetry payload for reporting to server.
     */
    public JSONObject buildTelemetryPayload() {
        if (telemetryReporter == null || tracker == null) {
            return null;
        }
        try {
            return telemetryReporter.buildTelemetryPayload(tracker);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to build telemetry payload", e);
            return null;
        }
    }

    /**
     * Clear completed sessions from tracker after successful telemetry upload.
     */
    public void clearReportedSessions() {
        if (tracker != null) {
            tracker.clearCompletedSessions();
        }
    }

    // -----------------------------------------------------------------------
    // Callbacks
    // -----------------------------------------------------------------------

    /** Callback for host mode events. */
    public interface HostModeCallback {
        /** QR code payload is ready for display. */
        void onQrCodeReady(String qrPayloadJson);

        /** A client peer has connected and authenticated. */
        void onPeerConnected(String peerId);

        /** Sync progress update. */
        void onSyncProgress(int docsSynced, int totalDocs);

        /** Sync session completed successfully. */
        void onSyncComplete(P2pSession session);

        /** An error occurred. */
        void onError(String error);

        /**
         * OEM battery optimization guidance for the user.
         * Called before sync starts if the device manufacturer is known
         * to aggressively kill background services.
         */
        void onBatteryGuidance(OemBatteryHelper.RiskLevel riskLevel, String guidance);
    }

    /** Callback for client mode events. */
    public interface ClientModeCallback {
        /**
         * QR payload validated, connection info ready.
         * The caller should now connect to the WiFi network and verify TLS (G20).
         */
        void onConnectionInfoReady(String ssid, String password,
                                    String host, int port,
                                    String tlsFingerprint);

        /** Successfully connected and authenticated with host. */
        void onConnected(String hostId);

        /** Preview data ready — doc counts fetched from host before sync starts. */
        void onPreviewReady(int contactCount, int reportCount, int totalCount);

        /** Sync progress update. */
        void onSyncProgress(int docsSynced, int totalDocs);

        /** Sync session completed successfully. */
        void onSyncComplete(P2pSession session);

        /** An error occurred. */
        void onError(String error);
    }

    // -----------------------------------------------------------------------
    // Exceptions
    // -----------------------------------------------------------------------

    /** Thrown when P2P initialization fails. */
    public static class P2pInitException extends Exception {
        private static final long serialVersionUID = 1L;
        public P2pInitException(String message) {
            super(message);
        }
    }

    // -----------------------------------------------------------------------
    // Private Helpers
    // -----------------------------------------------------------------------

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                    "P2pManager not initialized. Call initialize() first.");
        }
    }

    /**
     * Create the appropriate HotspotProvider based on the environment.
     * Uses LoopbackHotspotProvider for emulators (no real WiFi hardware),
     * WifiHotspotProvider for real devices.
     */
    private HotspotProvider createHotspotProvider() {
        if (isEmulator()) {
            Log.i(TAG, "Emulator detected, using LoopbackHotspotProvider");
            return new LoopbackHotspotProvider();
        }
        WifiManager wifiManager = (WifiManager) context
                .getSystemService(Context.WIFI_SERVICE);
        return new WifiHotspotProvider(wifiManager);
    }

    /**
     * Best-effort emulator detection.
     */
    private boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || "goldfish".equals(Build.HARDWARE)
                || "ranchu".equals(Build.HARDWARE)
                || Build.BRAND.startsWith("generic")
                || Build.DEVICE.startsWith("generic");
    }

    /**
     * Save transit doc state. Best-effort -- errors are logged, not thrown.
     */
    private void saveTransitState() {
        if (transitDocManager == null) {
            return;
        }
        try {
            JSONObject transitJson = getTransitStateJson();
            if (transitJson != null) {
                Log.d(TAG, "Transit state ready for persistence ("
                        + transitDocManager.getPendingPushCount() + " pending docs)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save transit state", e);
        }
    }

    private boolean hasWifiPermissions() {
        // Basic WiFi permissions (normal/install-time — always granted via manifest)
        PackageManager pm = context.getPackageManager();
        boolean hasWifiState = pm.checkPermission(
                android.Manifest.permission.ACCESS_WIFI_STATE,
                context.getPackageName()) == PackageManager.PERMISSION_GRANTED;
        boolean hasChangeWifi = pm.checkPermission(
                android.Manifest.permission.CHANGE_WIFI_STATE,
                context.getPackageName()) == PackageManager.PERMISSION_GRANTED;

        if (!hasWifiState || !hasChangeWifi) {
            return false;
        }

        // Runtime (dangerous) permissions — must use ContextCompat.checkSelfPermission()
        // ACCESS_FINE_LOCATION is required on ALL versions (no neverForLocation flag)
        if (ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        // API 33+: also needs NEARBY_WIFI_DEVICES
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context,
                    "android.permission.NEARBY_WIFI_DEVICES") != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    private long getAvailableStorageMb() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            return availableBytes / (1024 * 1024);
        } catch (Exception e) {
            Log.w(TAG, "Failed to check storage", e);
            return Long.MAX_VALUE; // Don't block on check failure
        }
    }

    private long getTotalRamMb() {
        try {
            ActivityManager am = (ActivityManager) context
                    .getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(memInfo);
                return memInfo.totalMem / (1024 * 1024);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check RAM", e);
        }
        return 0;
    }

    private int getBatteryPercent() {
        try {
            BatteryManager bm = (BatteryManager) context
                    .getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check battery", e);
        }
        return -1; // Unknown
    }
}
