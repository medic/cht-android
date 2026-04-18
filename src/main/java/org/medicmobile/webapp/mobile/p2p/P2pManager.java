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
	* 2. initialize()	  -- setup components from cached config
	* 3. startHostMode()	   -- start hotspot + HTTP server + show QR
	* 4. startClientMode()	-- scan QR + connect to host + sync
	* 5. shutdown()		-- tear down everything cleanly
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

	// QR payload constants
	private static final String QR_TYPE = "cht-p2p";
	private static final int QR_VERSION = 1;
	private static final long QR_MAX_AGE_MS = 10L * 60 * 1000; // 10 minutes

	// Emulator detection
	private static final String GENERIC_BRAND = "generic";

	// QR payload keys
	private static final String KEY_SSID = "ssid";
	private static final String KEY_IP = "ip";
	private static final String KEY_PORT = "port";

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
	private volatile String currentQrSessionTs = null; // tracks current QR timestamp
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
		P2pCapability hardBlock = checkHardBlocks();
		if (hardBlock != null) {
			return hardBlock;
		}
		P2pCapability softWarning = checkSoftWarnings();
		if (softWarning != null) {
			return softWarning;
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

	private P2pCapability checkHardBlocks() {
		if (Build.VERSION.SDK_INT < MIN_API_LEVEL) {
			Log.w(TAG, "Unsupported API level: " + Build.VERSION.SDK_INT +
					" (min " + MIN_API_LEVEL + ")");
			return P2pCapability.UNSUPPORTED_API_LEVEL;
		}
		WifiManager wifiManager = (WifiManager) context.getApplicationContext()
				.getSystemService(Context.WIFI_SERVICE);
		if (wifiManager == null) {
			Log.w(TAG, "No WifiManager available");
			return P2pCapability.NO_WIFI_HARDWARE;
		}
		long freeStorageMb = getAvailableStorageMb();
		if (freeStorageMb < MIN_STORAGE_MB) {
			Log.w(TAG, "Low storage: " + freeStorageMb + " MB free");
			return P2pCapability.LOW_STORAGE;
		}
		if (!hasWifiPermissions()) {
			Log.w(TAG, "Missing WiFi permissions");
			return P2pCapability.PERMISSION_NEEDED;
		}
		return checkLocationServices();
	}

	private P2pCapability checkLocationServices() {
		android.location.LocationManager lm = (android.location.LocationManager)
				context.getSystemService(Context.LOCATION_SERVICE);
		if (lm == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !lm.isLocationEnabled())) {
			Log.w(TAG, "Location services disabled (required for hotspot)");
			return P2pCapability.LOCATION_SERVICES_OFF;
		}
		return null;
	}

	private P2pCapability checkSoftWarnings() {
		P2pCapability batteryCheck = checkBatteryRisk();
		if (batteryCheck != null) {
			return batteryCheck;
		}
		long totalRamMb = getTotalRamMb();
		if (totalRamMb > 0 && totalRamMb < LOW_RAM_MB) {
			if (totalRamMb < MIN_RAM_MB) {
				Log.w(TAG, "Very low RAM: " + totalRamMb + " MB");
			}
			Log.w(TAG, "Low RAM warning: " + totalRamMb + " MB");
			return P2pCapability.LOW_RAM_WARNING;
		}
		return null;
	}

	private P2pCapability checkBatteryRisk() {
		int batteryPercent = getBatteryPercent();
		if (batteryPercent >= 0 && batteryPercent < MIN_BATTERY_PERCENT) {
			Log.w(TAG, "Low battery: " + batteryPercent + "%");
			return P2pCapability.LOW_BATTERY_WARNING;
		}
		return null;
	}

	// -----------------------------------------------------------------------
	// Initialize
	// -----------------------------------------------------------------------

	/**
		* Parameters needed to initialize P2P components.
		* Groups the 7 initialize() params to comply with max-params rule.
		*/
	@SuppressWarnings("java:S107") // Data class — groups initialization parameters
	public static class InitParams {
		public final P2pConfig config;
		public final String serverPublicKey;
		public final RevocationList revocationList;
		public final ScopeManifest localScope;
		public final PouchDbBridge bridge;
		public final String deviceId;
		public final String userId;

		public InitParams(P2pConfig config, String serverPublicKey,
							RevocationList revocationList, ScopeManifest localScope,
							PouchDbBridge bridge, String deviceId, String userId) {
			this.config = config;
			this.serverPublicKey = serverPublicKey;
			this.revocationList = revocationList;
			this.localScope = localScope;
			this.bridge = bridge;
			this.deviceId = deviceId;
			this.userId = userId;
		}
	}

	/**
		* Initialize P2P components from cached config.
		* Must be called before startHostMode() or startClientMode().
		*
		* @param config		  P2P config from app_settings
		* @param serverPublicKey PEM-encoded ECDSA P-256 public key for JWT verification
		* @param revocationList  cached device/user revocation list
		* @param localScope	  this device's scope manifest
		* @param bridge		  PouchDB bridge for data access
		* @param deviceId		this device's unique identifier
		* @param userId		  current user's ID
		* @throws P2pInitException if initialization fails
		*/
	@SuppressWarnings("java:S107") // Delegates to InitParams — public API kept for backward compatibility
	public void initialize(P2pConfig config, String serverPublicKey,
							RevocationList revocationList, ScopeManifest localScope,
							PouchDbBridge bridge, String deviceId, String userId)
			throws P2pInitException {

		if (initialized) {
			Log.w(TAG, "Already initialized, re-initializing");
			shutdownInternal();
		}

		validateInitParams(config, serverPublicKey, revocationList, localScope,
				bridge, deviceId, userId);

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
			Log.i(TAG, "P2P components initialized for user=" + userId +
					" device=" + deviceId);

		} catch (JwtVerifier.JwtVerificationException e) {
			throw new P2pInitException("Failed to initialize JWT verifier: " + e.getMessage());
		} catch (RuntimeException e) {
			throw new P2pInitException("Initialization failed: " + e.getMessage());
		}
	}

	@SuppressWarnings("java:S107") // Validates all init params — mirrors the public API
	private static void validateInitParams(P2pConfig config, String serverPublicKey,
											RevocationList revocationList, ScopeManifest localScope,
											PouchDbBridge bridge, String deviceId, String userId)
			throws P2pInitException {
		requireNonNull(config, "config");
		if (!config.isEnabled()) {
			throw new P2pInitException("P2P sync is disabled in config");
		}
		requireNonEmpty(serverPublicKey, "serverPublicKey");
		requireNonNull(revocationList, "revocationList");
		requireNonNull(localScope, "localScope");
		requireNonNull(bridge, "bridge");
		requireNonEmpty(deviceId, "deviceId");
		requireNonEmpty(userId, "userId");
	}

	private static void requireNonNull(Object value, String name) throws P2pInitException {
		if (value == null) {
			throw new P2pInitException(name + " must not be null");
		}
	}

	private static void requireNonEmpty(String value, String name) throws P2pInitException {
		if (value == null || value.isEmpty()) {
			throw new P2pInitException(name + " must not be null or empty");
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
				Log.i(TAG, "Loaded transit doc state, pending=" +
						transitDocManager.getPendingPushCount());
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
		* 3. Acquire sync mutex
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

		String preCheckError = performPreHostChecks(callback);
		if (preCheckError != null) {
			callback.onError(preCheckError);
			return;
		}

		// Acquire sync mutex
		if (!syncMutex.tryAcquire(SyncMutex.SyncType.P2P)) {
			callback.onError("sync_mutex_busy: another sync is already active");
			return;
		}

		try {
			P2pForegroundService.start(context);
			Log.i(TAG, "Foreground service started");

			hostModeActive = true;
			startHotspotForSupervisor(callback);

		} catch (RuntimeException e) {
			Log.e(TAG, "Failed to start supervisor mode", e);
			cleanupFailedHostStart();
			callback.onError("supervisor_start_failed: " + e.getMessage());
		}
	}

	private void cleanupFailedHostStart() {
		hostModeActive = false;
		syncMutex.release();
		try {
			P2pForegroundService.stop(context);
		} catch (RuntimeException ignored) {
			// Best-effort cleanup
		}
	}

	/**
	 * Pre-checks before starting host mode: shutdown conflicts, capability, OEM guidance.
	 * @return error string if a hard block is found, null if all checks pass
	 */
	private String performPreHostChecks(HostModeCallback callback) {
		if (hostModeActive) {
			Log.w(TAG, "Supervisor mode already active, shutting down previous session");
			shutdownInternal();
		}
		if (clientModeActive) {
			Log.w(TAG, "CHW mode active, shutting down before starting supervisor");
			shutdownClientMode();
		}

		P2pCapability capability = checkCapability();
		if (isHardBlock(capability)) {
			return "device_not_capable: " + capability.name();
		}

		if (OemBatteryHelper.needsBatteryGuidance()) {
			String guidance = OemBatteryHelper.getGuidance();
			Log.i(TAG, "OEM battery guidance (" + OemBatteryHelper.getManufacturer() +
					"): " + guidance);
			callback.onBatteryGuidance(OemBatteryHelper.getRiskLevel(), guidance);
		}

		return null;
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
					startHttpServer();

					// Step 7: Generate QR payload
					String qrPayload = buildQrPayload(ssid, password,
							ipAddress, httpServer.getPort());
					currentQrSessionTs = String.valueOf(System.currentTimeMillis());

					Log.i(TAG, "Supervisor mode ready, QR generated");
					callback.onQrCodeReady(qrPayload);

				} catch (IOException | JSONException e) {
					Log.e(TAG, "Failed after hotspot started", e);
					shutdownInternal();
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
				} catch (RuntimeException ignored) {
					// Best-effort cleanup
				}
				callback.onError("hotspot_failed: " + reason);
			}
		});
	}

	/**
		* Internal: create and start the LocalHttpServer.
		*/
	private void startHttpServer() throws IOException {
		LocalHttpServer.ServerDeps deps = createServerDeps();
		httpServer = new LocalHttpServer(deps);

		httpServer.setSessionCompleteCallback(session -> {
			if (tracker != null && session != null) {
				tracker.recordCompletedSession(session);
				Log.i(TAG, "Tracker recorded host-side sync session: " +
						session.getSessionId());
			}
		});

		httpServer.startServer();
		Log.i(TAG, "HTTP server started on port " + httpServer.getPort());
	}

	private LocalHttpServer.ServerDeps createServerDeps() {
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
				Log.d(TAG, "Tracked " + docIds.size() +
						" transit docs in batch " + batchId);
			}
		};

		return new LocalHttpServer.ServerDeps(authenticator, config, localScope,
				pouchDbBridge, transitCallback);
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
			int total = session.getDocsPushed() + session.getDocsPulled() +
					session.getTransitDocs();
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
		* 2. Validate QR (timestamp, type, version)
		* 3. Connect to supervisor's WiFi (out-of-band -- caller handles WiFi connection)
		* 4. Authenticate with supervisor (caller calls authenticateWithSupervisor)
		*
		* Note: actual WiFi connection is handled by the Android system when the user
		* selects the network. This method validates the QR and returns connection info.
		*
		* @param qrPayloadJson the JSON string scanned from the QR code
		* @param callback	  receives lifecycle events
		*/
	public void startClientMode(String qrPayloadJson, final ClientModeCallback callback) {
		if (callback == null) {
			throw new IllegalArgumentException("callback must not be null");
		}
		ensureInitialized();

		String preCheckError = validateClientModeParams();
		if (preCheckError != null) {
			callback.onError(preCheckError);
			return;
		}

		// Parse QR payload
		JSONObject qrPayload;
		try {
			qrPayload = new JSONObject(qrPayloadJson);
		} catch (JSONException e) {
			callback.onError("qr_parse_failed: invalid JSON");
			return;
		}

		// Validate QR payload
		String validationError = validateQrPayload(qrPayload);
		if (validationError != null) {
			callback.onError(validationError);
			return;
		}

		// Acquire sync mutex
		if (!syncMutex.tryAcquire(SyncMutex.SyncType.P2P)) {
			callback.onError("sync_mutex_busy: another sync is already active");
			return;
		}

		clientModeActive = true;

		String ssid = qrPayload.optString(KEY_SSID, "");
		String password = qrPayload.optString("pwd", "");
		String ip = qrPayload.optString(KEY_IP, "");
		int port = qrPayload.optInt(KEY_PORT, 8443);
		String tlsFingerprint = qrPayload.optString("tls", "");

		Log.i(TAG, "CHW mode started: supervisor at " + ip + ":" + port +
				" via SSID=" + ssid + " — attempting auto WiFi connection");

		QrCodeHelper.HotspotCredentials creds =
				new QrCodeHelper.HotspotCredentials(ssid, password, ip, port, tlsFingerprint);
		connectToHotspotWifi(creds, callback);
	}

	/**
	 * Validate preconditions for client mode, handling conflicting active modes.
	 * @return error string if client mode cannot start, null if preconditions are met
	 */
	private String validateClientModeParams() {
		if (clientModeActive) {
			Log.w(TAG, "CHW mode already active, shutting down previous session");
			shutdownClientMode();
		}
		if (hostModeActive) {
			return "host_mode_active: shutdown supervisor mode first";
		}
		return null;
	}

	/**
		* Connect to the supervisor's WiFi hotspot using WifiNetworkSpecifier (API 29+).
		*/
	private void connectToHotspotWifi(final QrCodeHelper.HotspotCredentials creds,
										final ClientModeCallback callback) {
		final String ssid = creds.ssid;
		final String password = creds.password;
		final String ip = creds.ipAddress;
		final int port = creds.port;
		final String tlsFingerprint = creds.tlsFingerprint;
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
				Log.w(TAG, "WiFi auto-connect unavailable for SSID=" + ssid +
						" — falling back to manual connect");
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

		Log.i(TAG, "Requesting WiFi connection to SSID=" + ssid +
				" (no internet capability required)");
		cm.requestNetwork(request, wifiNetworkCallback);
	}

	/**
		* Authenticate with the supervisor's HTTP server (CHW side).
		* Called after the CHW has connected to the supervisor's WiFi network.
		*
		* @param supervisorHost supervisor's IP address
		* @param supervisorPort supervisor's HTTP server port
		* @param jwt			this device's P2P JWT token
		* @param callback	   receives lifecycle events
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
		Log.i(TAG, "CHW authenticating with supervisor at " +
				supervisorHost + ":" + supervisorPort);

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
		shutdownInternal();
	}

	/**
		* Internal shutdown — tears down all P2P components cleanly.
		*/
	private void shutdownInternal() {
		Log.i(TAG, "Shutting down P2P components");

		stopHttpServerSafe();
		stopHotspotSafe();
		stopForegroundServiceSafe();
		releaseMutexSafe();
		failActiveTrackerSession();
		saveTransitState();
		dismissNotificationsSafe();
		unregisterWifiCallbackSafe();

		// Reset state
		hostModeActive = false;
		clientModeActive = false;
		currentQrSessionTs = null;

		Log.i(TAG, "P2P shutdown complete");
	}

	private void stopHttpServerSafe() {
		if (httpServer != null) {
			try {
				httpServer.stopServer();
				Log.d(TAG, "HTTP server stopped");
			} catch (RuntimeException e) {
				Log.e(TAG, "Error stopping HTTP server", e);
			}
			httpServer = null;
		}
	}

	private void stopHotspotSafe() {
		if (hotspotManager != null) {
			try {
				hotspotManager.stopHotspot();
				Log.d(TAG, "Hotspot stopped");
			} catch (RuntimeException e) {
				Log.e(TAG, "Error stopping hotspot", e);
			}
			hotspotManager = null;
		}
	}

	private void stopForegroundServiceSafe() {
		try {
			P2pForegroundService.stop(context);
		} catch (RuntimeException e) {
			Log.e(TAG, "Error stopping foreground service", e);
		}
	}

	private void releaseMutexSafe() {
		if (syncMutex != null && syncMutex.isActive()) {
			try {
				syncMutex.release();
				Log.d(TAG, "Sync mutex released");
			} catch (RuntimeException e) {
				Log.e(TAG, "Error releasing sync mutex", e);
			}
		}
	}

	private void failActiveTrackerSession() {
		if (tracker != null && tracker.hasActiveSession()) {
			tracker.failSession("shutdown");
		}
	}

	private void dismissNotificationsSafe() {
		if (notificationChannel != null) {
			try {
				notificationChannel.dismiss();
			} catch (RuntimeException e) {
				Log.e(TAG, "Error dismissing notification", e);
			}
		}
	}

	private void unregisterWifiCallbackSafe() {
		if (wifiNetworkCallback != null) {
			try {
				ConnectivityManager cm = (ConnectivityManager) context
						.getSystemService(Context.CONNECTIVITY_SERVICE);
				if (cm != null) {
					cm.unregisterNetworkCallback(wifiNetworkCallback);
				}
			} catch (RuntimeException e) {
				Log.e(TAG, "Error cleaning up WiFi callback", e);
			}
			wifiNetworkCallback = null;
		}
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
		* Format matches .
		*/
	String buildQrPayload(String ssid, String password,
							String ipAddress, int port) throws JSONException {
		JSONObject qr = new JSONObject();
		qr.put("type", QR_TYPE);
		qr.put("v", QR_VERSION);
		qr.put(KEY_SSID, ssid);
		qr.put("pwd", password);
		qr.put(KEY_IP, ipAddress);
		qr.put(KEY_PORT, port);
		qr.put("tls", ""); // TLS fingerprint set when HTTPS is configured
		qr.put("ts", System.currentTimeMillis());
		return qr.toString();
	}

	/**
		* Validate a QR payload scanned by the CHW.
		* Enforces guards , , .
		*
		* @return null if valid, or an error string describing the issue
		*/
	String validateQrPayload(JSONObject qr) {
		if (qr == null) {
			return "qr_invalid: null payload";
		}

		String headerError = validateQrHeader(qr);
		if (headerError != null) {
			return headerError;
		}

		String timestampError = validateQrTimestamp(qr);
		if (timestampError != null) {
			return timestampError;
		}

		return checkQrRequiredFields(qr);
	}

	private String checkQrRequiredFields(JSONObject qr) {
		String ssid = qr.optString(KEY_SSID, "");
		if (ssid.isEmpty()) {
			return "qr_missing_ssid";
		}
		String ip = qr.optString(KEY_IP, "");
		if (ip.isEmpty()) {
			return "qr_missing_ip";
		}
		int port = qr.optInt(KEY_PORT, 0);
		if (port <= 0 || port > 65535) {
			return "qr_invalid_port: " + port;
		}
		return null;
	}

	private String validateQrHeader(JSONObject qr) {
		String type = qr.optString("type", "");
		if (!QR_TYPE.equals(type)) {
			return "qr_invalid_type: expected '" + QR_TYPE + "', got '" + type + "'";
		}
		int version = qr.optInt("v", 0);
		if (version < 1) {
			return "qr_invalid_version: " + version;
		}
		return null;
	}

	private String validateQrTimestamp(JSONObject qr) {
		long ts = qr.optLong("ts", 0);
		if (ts <= 0) {
			return "qr_missing_timestamp";
		}
		long age = Math.abs(System.currentTimeMillis() - ts);
		if (age > QR_MAX_AGE_MS) {
			return "qr_expired: age=" + (age / 1000) + "s (max " +
					(QR_MAX_AGE_MS / 1000) + "s)";
		}
		return null;
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

	/** Check if there are stale transit docs needing attention. */
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
			// archive purged batches if oversized
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
	@SuppressWarnings("java:S107") // Callback delivers all connection params at once
	public interface ClientModeCallback {
		/**
			* QR payload validated, connection info ready.
			* The caller should now connect to the WiFi network and verify TLS.
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

	private boolean isHardBlock(P2pCapability capability) {
		return capability == P2pCapability.UNSUPPORTED_API_LEVEL
				|| capability == P2pCapability.NO_WIFI_HARDWARE
				|| capability == P2pCapability.LOW_STORAGE
				|| capability == P2pCapability.PERMISSION_NEEDED
				|| capability == P2pCapability.LOCATION_SERVICES_OFF;
	}

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
		WifiManager wifiManager = (WifiManager) context.getApplicationContext()
				.getSystemService(Context.WIFI_SERVICE);
		return new WifiHotspotProvider(wifiManager);
	}

	/**
		* Best-effort emulator detection.
		*/
	private boolean isEmulator() {
		return Build.FINGERPRINT.startsWith(GENERIC_BRAND)
				|| Build.FINGERPRINT.startsWith("unknown")
				|| Build.MODEL.contains("google_sdk")
				|| Build.MODEL.contains("Emulator")
				|| Build.MODEL.contains("Android SDK built for x86")
				|| Build.MANUFACTURER.contains("Genymotion")
				|| "goldfish".equals(Build.HARDWARE)
				|| "ranchu".equals(Build.HARDWARE)
				|| Build.BRAND.startsWith(GENERIC_BRAND)
				|| Build.DEVICE.startsWith(GENERIC_BRAND);
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
				Log.d(TAG, "Transit state ready for persistence (" +
						transitDocManager.getPendingPushCount() + " pending docs)");
			}
		} catch (RuntimeException e) {
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
		return Build.VERSION.SDK_INT < 33
				|| ContextCompat.checkSelfPermission(context,
					"android.permission.NEARBY_WIFI_DEVICES") == PackageManager.PERMISSION_GRANTED;
	}

	private long getAvailableStorageMb() {
		try {
			StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
			long availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
			return availableBytes / (1024 * 1024);
		} catch (RuntimeException e) {
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
		} catch (RuntimeException e) {
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
		} catch (RuntimeException e) {
			Log.w(TAG, "Failed to check battery", e);
		}
		return -1; // Unknown
	}
}
