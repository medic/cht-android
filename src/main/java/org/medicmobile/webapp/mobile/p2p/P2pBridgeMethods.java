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
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
	* JavaScript bridge methods for P2P sync operations.
	* Bound to the WebView as part of the 'medicmobile_android' interface.
	*
	* The webapp calls these methods to:
	* - Start/stop P2P host or client mode
	* - Get transit doc IDs for UI filtering
	* - Trigger transit doc purge after server push
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
	private static final String KEY_SESSIONS = "sessions";
	private static final String KEY_DOCS_SYNCED = "docs_synced";
	private static final String KEY_TOTAL_DOCS = "total_docs";
	private static final String KEY_BYTES_TRANSFERRED = "bytes_transferred";
	private static final String KEY_HOST = "host";
	private static final String KEY_DOC_ID = "_id";
	private static final String SYNC_LOG_DOC_ID = "_local/p2p-sync-log";
	private static final String RELAY_LOG_DOC_ID = "_local/p2p-relay-log";
	private static final String LOG_RESULT_PREFIX = " result=";
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
	private final AtomicLong clientBytesTransferred = new AtomicLong(0);
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
		*		 or { "ok": false, "error": "reason" }
		*/
	@JavascriptInterface
	public String p2pStartHostMode() {
		String initError = checkManagerInitialized();
		if (initError != null) {
			return initError;
		}
		try {
			return doStartHostMode();
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

	private String doStartHostMode() throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicReference<JSONObject> resultRef = new AtomicReference<>();

		p2pManager.startHostMode(createHostModeCallback(latch, resultRef));

		boolean completed = latch.await(HOST_START_TIMEOUT_SEC, TimeUnit.SECONDS);
		if (!completed) {
			Log.w(TAG, "Host start timed out, shutting down to release mutex");
			p2pManager.shutdown();
			return errorJson("supervisor_start_timeout");
		}

		JSONObject result = resultRef.get();
		return result != null ? result.toString() : errorJson("no_result");
	}

	private String checkManagerInitialized() {
		if (p2pManager == null) {
			return errorJson("p2p_not_initialized");
		}
		if (!p2pManager.isInitialized()) {
			return errorJson("not_initialized");
		}
		return null;
	}

	private P2pManager.HostModeCallback createHostModeCallback(
			final CountDownLatch latch, final AtomicReference<JSONObject> resultRef) {
		return new P2pManager.HostModeCallback() {
			@Override
			public void onQrCodeReady(String qrPayloadJson) {
				resultRef.set(buildQrResponse(qrPayloadJson));
				latch.countDown();
			}

			@Override
			public void onPeerConnected(String peerId) {
				// No-op: callback not used in this context
			}

			@Override
			public void onSyncProgress(int docsSynced, int totalDocs) {
				// No-op: callback not used in this context
			}

			@Override
			public void onSyncComplete(P2pSession session) {
				// No-op: callback not used in this context
			}

			@Override
			public void onError(String error) {
				try {
					JSONObject response = new JSONObject();
					response.put(KEY_OK, false);
					response.put(KEY_ERROR, error);
					resultRef.set(response);
				} catch (JSONException e) {
					Log.e(TAG, "Error building error response", e);
				}
				latch.countDown();
			}

			@Override
			public void onBatteryGuidance(OemBatteryHelper.RiskLevel riskLevel,
											String guidance) {
				Log.i(TAG, "Battery guidance (" + riskLevel + "): " + guidance);
			}
		};
	}

	private JSONObject buildQrResponse(String qrPayloadJson) {
		try {
			String qrDataUrl = QrCodeHelper.generateQrDataUrl(qrPayloadJson);
			cachedQrDataUrl = qrDataUrl;

			JSONObject response = new JSONObject();
			response.put(KEY_OK, true);
			response.put("qr_payload", qrPayloadJson);
			response.put("qr_data_url", qrDataUrl != null ? qrDataUrl : "");
			return response;
		} catch (JSONException e) {
			Log.e(TAG, "Error building QR response", e);
			return null;
		}
	}

	/**
		* Start P2P in client mode with scanned QR data.
		* Validates QR, returns WiFi credentials for manual connection.
		* User connects to WiFi manually, then webapp calls p2pCheckConnection() to detect it.
		*
		* @param qrPayloadJson the scanned QR payload JSON string
		* @return JSON: { "ok": true, "ssid": "...", "password": "...", "host": "...", "port": N }
		*		 or { "ok": false, "error": "reason" }
		*/
	@JavascriptInterface
	public String p2pStartClientMode(String qrPayloadJson) {
		String initError = checkManagerInitialized();
		if (initError != null) {
			return initError;
		}
		String validationError = validateQrInput(qrPayloadJson);
		if (validationError != null) {
			return validationError;
		}
		try {
			return doStartClientMode(qrPayloadJson);
		} catch (JSONException e) {
			Log.e(TAG, "Error starting client mode", e);
			clientSyncState = STATE_FAILED;
			return errorJson("start_failed: " + e.getMessage());
		}
	}

	private String validateQrInput(String qrPayloadJson) {
		if (qrPayloadJson == null || qrPayloadJson.isEmpty()) {
			return errorJson("empty_qr_payload");
		}
		ValidationResult validation = QrCodeHelper.validateQrPayload(qrPayloadJson);
		if (!validation.isAccepted()) {
			return errorJson("invalid_qr: " + validation.getReason());
		}
		return null;
	}

	private String doStartClientMode(String qrPayloadJson) throws JSONException {
		JSONObject qr = new JSONObject(qrPayloadJson);
		String ssid = qr.optString("ssid", "");
		String password = qr.optString("pwd", "");
		String host = qr.optString("ip", "");
		int port = qr.optInt("port", 8443);

		cachedPeerHost = host;
		cachedPeerPort = port;
		clientSyncState = STATE_CONNECTING;

		Log.i(TAG, "Client mode: auto-connecting to SSID=" + ssid +
				" then sync with " + host + ":" + port);

		p2pManager.startClientMode(qrPayloadJson, createClientModeCallback());

		JSONObject result = new JSONObject();
		result.put(KEY_OK, true);
		result.put("auto_connecting", true);
		result.put("ssid", ssid);
		result.put("password", password);
		result.put(KEY_HOST, host);
		result.put("port", port);
		return result.toString();
	}

	/**
		* Find the WiFi network from ConnectivityManager.
		* On Android, when connected to a LocalOnlyHotspot (no internet),
		* the default network remains cellular. We must explicitly bind HTTP
		* connections to the WiFi network to reach the hotspot's HTTP server.
		*/
	private P2pManager.ClientModeCallback createClientModeCallback() {
		return new P2pManager.ClientModeCallback() {
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
		};
	}

	private Network findWifiNetwork() {
		if (p2pManager == null) return null;

		// Primary: exact network from WifiNetworkSpecifier auto-connect
		Network autoNetwork = p2pManager.getP2pWifiNetwork();
		if (autoNetwork != null) {
			Log.d(TAG, "Using auto-connected WiFi network: " + autoNetwork);
			return autoNetwork;
		}

		// Fallback: find WiFi network on same subnet as supervisor
		if (cachedPeerHost == null || cachedPeerHost.isEmpty()) return null;
		return findWifiNetworkBySubnet();
	}

	/**
		* Find WiFi network on the same subnet as the supervisor.
		* Critical for dual-WiFi devices (Pixel has wlan0 + wlan1).
		*/
	private Network findWifiNetworkBySubnet() {
		String targetPrefix = cachedPeerHost.substring(0, cachedPeerHost.lastIndexOf('.') + 1);
		try {
			Context ctx = p2pManager.getContext();
			if (ctx == null) return null;
			ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
			if (cm == null) return null;

			return scanNetworksForSubnetMatch(cm, targetPrefix);
		} catch (RuntimeException e) {
			Log.w(TAG, "Failed to find WiFi network", e);
		}
		Log.w(TAG, "No WiFi network found at all");
		return null;
	}

	@SuppressWarnings("deprecation") // getAllNetworks deprecated API 31; NetworkCallback alternative is async and complex
	private Network scanNetworksForSubnetMatch(ConnectivityManager cm, String targetPrefix) {
		Network[] wifiNetworks = getWifiNetworks(cm);
		Network fallbackWifi = null;
		for (Network network : wifiNetworks) {
			Network result = findSubnetMatch(cm, network, targetPrefix);
			if (result != null) {
				return result;
			}
			if (fallbackWifi == null) {
				fallbackWifi = network;
			}
		}
		return logAndReturnFallback(fallbackWifi, targetPrefix);
	}

	@SuppressWarnings("deprecation")
	private Network[] getWifiNetworks(ConnectivityManager cm) {
		java.util.List<Network> wifi = new java.util.ArrayList<>();
		for (Network network : cm.getAllNetworks()) {
			if (isWifiNetwork(cm, network)) {
				wifi.add(network);
			}
		}
		return wifi.toArray(new Network[0]);
	}

	private static boolean isWifiNetwork(ConnectivityManager cm, Network network) {
		NetworkCapabilities caps = cm.getNetworkCapabilities(network);
		return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
	}

	private Network logAndReturnFallback(Network fallbackWifi, String targetPrefix) {
		if (fallbackWifi != null) {
			Log.w(TAG, "No subnet match for " + targetPrefix + "*, using fallback WiFi: " + fallbackWifi);
		}
		return fallbackWifi;
	}

	private Network findSubnetMatch(ConnectivityManager cm, Network network, String targetPrefix) {
		android.net.LinkProperties lp = cm.getLinkProperties(network);
		if (lp == null) return null;

		for (android.net.LinkAddress addr : lp.getLinkAddresses()) {
			if (isIpv4OnSubnet(addr, targetPrefix)) {
				Log.i(TAG, "Found WiFi on supervisor subnet: " +
						addr.getAddress().getHostAddress() +
						" (target=" + targetPrefix + "*) → " + network);
				return network;
			}
		}
		return null;
	}

	private static boolean isIpv4OnSubnet(android.net.LinkAddress addr, String targetPrefix) {
		java.net.InetAddress inetAddr = addr.getAddress();
		if (!(inetAddr instanceof java.net.Inet4Address)) return false;
		String ip = inetAddr.getHostAddress();
		return ip != null && ip.startsWith(targetPrefix);
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
		if (cachedPeerHost == null || cachedPeerHost.isEmpty()) {
			Log.d(TAG, "p2pCheckConnection: no host cached");
			return "{\"connected\":false}";
		}
		try {
			boolean reachable = checkPeerReachability();
			JSONObject result = new JSONObject();
			result.put("connected", reachable);
			return result.toString();
		} catch (JSONException e) {
			Log.w(TAG, "p2pCheckConnection error", e);
			return "{\"connected\":false}";
		}
	}

	private boolean checkPeerReachability() {
		P2pSyncClient client = new P2pSyncClient(cachedPeerHost, cachedPeerPort);
		Network wifiNetwork = findWifiNetwork();
		if (wifiNetwork != null) {
			client.setNetwork(wifiNetwork);
		} else {
			Log.d(TAG, "p2pCheckConnection: no WiFi network found, trying default");
		}

		boolean reachable = client.isReachable();
		Log.d(TAG, "p2pCheckConnection: host=" + cachedPeerHost + ":" + cachedPeerPort +
				" reachable=" + reachable + " wifiNet=" + (wifiNetwork != null));

		if (reachable && STATE_WAITING_WIFI.equals(clientSyncState)) {
			Log.i(TAG, "Host reachable at " + cachedPeerHost + ":" + cachedPeerPort +
					" — starting sync");
			clientSyncState = STATE_CONNECTING;
			startClientSync(cachedPeerHost, cachedPeerPort);
		}
		return reachable;
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
			clientBytesTransferred.set(0);
			pendingDocIds = null;
			previewContactCount = 0;
			previewReportCount = 0;

			Log.i(TAG, "Retrying peer sync with cached host: " + cachedPeerHost + ":" + cachedPeerPort);
			startClientSync(cachedPeerHost, cachedPeerPort);

			JSONObject result = new JSONObject();
			result.put(KEY_OK, true);
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
			saveStateBeforeShutdown();
			resetClientState();
			if (p2pManager != null) {
				p2pManager.shutdown();
			}
		} catch (RuntimeException e) {
			Log.e(TAG, "Error stopping P2P", e);
		}
	}

	private void saveStateBeforeShutdown() {
		boolean wasHost = isManagerInMode(true);
		boolean wasPeer = isManagerInMode(false);
		if (wasHost || wasPeer) {
			saveSyncLogBeforeShutdown(wasHost);
		}
		if (wasHost) {
			persistTransitStateAsync();
		}
	}

	private boolean isManagerInMode(boolean hostMode) {
		if (p2pManager == null) {
			return false;
		}
		return hostMode ? p2pManager.isHostModeActive() : p2pManager.isClientModeActive();
	}

	private void resetClientState() {
		cachedQrDataUrl = null;
		clientSyncRunning = false;
		clientSyncState = STATE_IDLE;
		clientSyncError = null;
		clientDocsSynced = 0;
		clientTotalDocs = 0;
		clientBytesTransferred.set(0);
		activeSyncClient = null;
		cachedPeerHost = null;
		cachedPeerPort = 0;
		pendingDocIds = null;
		previewContactCount = 0;
		previewReportCount = 0;
	}

	/**
		* Build and save sync log to PouchDB on a background thread before shutdown.
		*/
	private void saveSyncLogBeforeShutdown(boolean wasHost) {
		try {
			final JSONObject logToSave;
			final String docId;
			if (wasHost) {
				logToSave = tracker.buildRelayLog();
				docId = RELAY_LOG_DOC_ID;
			} else {
				logToSave = tracker.buildSyncLog();
				docId = SYNC_LOG_DOC_ID;
			}
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

	/**
		* Persist transit state to PouchDB asynchronously.
		*/
	private void persistTransitStateAsync() {
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

	/**
		* Start the P2P client flow: connect, auth, fetch IDs, then show preview.
		* Does NOT start downloading docs — waits for user to call p2pProceedSync().
		*
		* Flow: connect → auth → get-ids → preview (STOP) → user confirms → bulk-get
		*/
	private static final int REACHABILITY_MAX_ATTEMPTS = 10;
	private static final long REACHABILITY_RETRY_MS = 1000;

	private void startClientSync(final String host, final int port) {
		if (clientSyncRunning) {
			Log.w(TAG, "startClientSync: already running, skipping");
			return;
		}
		clientSyncRunning = true;
		new Thread(() -> runClientSyncThread(host, port), "P2pClientSync").start();
	}

	private void runClientSyncThread(String host, int port) {
		try {
			doClientSync(host, port);
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
	}

	private void doClientSync(String host, int port)
			throws InterruptedException, JSONException, IOException {
		clientSyncState = STATE_CONNECTING;
		clientSyncError = null;

		P2pSyncClient client = createAndBindClient(host, port);
		activeSyncClient = client;

		if (!waitForServerReachable(client, host, port)) {
			return;
		}
		if (!authenticateClient(client, host, port)) {
			return;
		}

		startTrackerSession(host);
		Log.i(TAG, "Client sync: authenticated, querying unsynced docs");

		queryAndSetPreview();
	}

	private P2pSyncClient createAndBindClient(String host, int port) {
		P2pSyncClient client = new P2pSyncClient(host, port);
		Network wifiNet = findWifiNetwork();
		if (wifiNet != null) {
			client.setNetwork(wifiNet);
			Log.i(TAG, "Client sync: bound to WiFi network " + wifiNet);
		}
		return client;
	}

	private boolean waitForServerReachable(P2pSyncClient client, String host, int port)
			throws InterruptedException {
		for (int attempt = 1; attempt <= REACHABILITY_MAX_ATTEMPTS; attempt++) {
			if (client.isReachable()) {
				Log.i(TAG, "Server reachable on attempt " + attempt);
				return true;
			}
			Log.d(TAG, "Server not reachable, attempt " + attempt + "/" + REACHABILITY_MAX_ATTEMPTS + ", waiting 1s...");
			Thread.sleep(REACHABILITY_RETRY_MS);
		}
		Log.e(TAG, "Server unreachable after " + REACHABILITY_MAX_ATTEMPTS + " attempts");
		Network wifiNet = findWifiNetwork();
		clientSyncError = "Server unreachable after " + REACHABILITY_MAX_ATTEMPTS + " attempts at " + host + ":" + port +
				". WiFi network=" + (wifiNet != null ? wifiNet.toString() : "none");
		clientSyncState = STATE_FAILED;
		return false;
	}

	private boolean authenticateClient(P2pSyncClient client, String host, int port)
			throws IOException, JSONException {
		if (cachedJwt == null || cachedJwt.isEmpty()) {
			Log.e(TAG, "No JWT token cached, cannot authenticate with host");
			clientSyncError = "No authentication token. Please re-initialize P2P sync.";
			clientSyncState = STATE_FAILED;
			return false;
		}
		if (p2pManager != null) {
			client.setDeviceId(p2pManager.getLocalDeviceId());
		}
		Log.i(TAG, "Client sync: authenticating with host at " + host + ":" + port);
		String authError = client.authenticate(cachedJwt);
		if (authError != null) {
			Log.e(TAG, "Client sync: authentication failed: " + authError);
			clientSyncError = "Authentication failed: " + authError;
			clientSyncState = STATE_FAILED;
			return false;
		}
		return true;
	}

	private void startTrackerSession(String host) {
		if (tracker != null) {
			tracker.startSession("host-" + host, "host", "host", null);
		}
	}

	private void queryAndSetPreview() throws JSONException {
		// Get docs not yet synced to server.
		// Uses only lastServerReplicatedSeq from CHT's db-sync.service.ts.
		// Duplicate pushes are harmless (new_edits: false on the host).
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

		int[] counts = countDocTypes(docsToSync);
		clientTotalDocs = docsToSync.length();
		previewContactCount = counts[0];
		previewReportCount = counts[1];
		pendingDocIds = docsToSync;

		Log.i(TAG, "Client sync: CHW has " + docsToSync.length() + " unsynced docs to push (" +
				counts[0] + " contacts, " + counts[1] + " reports) — awaiting user confirmation");
		clientSyncState = STATE_PREVIEW;
	}

	private static int[] countDocTypes(JSONArray docsToSync) throws JSONException {
		int contacts = 0;
		int reports = 0;
		for (int i = 0; i < docsToSync.length(); i++) {
			JSONObject entry = docsToSync.getJSONObject(i);
			String docId = entry.optString(KEY_DOC_ID, "");
			if (docId.startsWith("report:") || docId.startsWith("report~")) {
				reports++;
			} else {
				contacts++;
			}
		}
		return new int[]{contacts, reports};
	}

	/**
		* Proceed with sync after user confirms preview.
		* Pushes CHW's docs to the Supervisor via POST /_p2p/accept-docs.
		*
		* @return JSON: {"ok": true} or {"ok": false, "error": "..."}
		*/
	@JavascriptInterface
	public String p2pProceedSync() {
		String precondError = checkProceedPreconditions();
		if (precondError != null) {
			return precondError;
		}
		try {
			final JSONArray docEntries = pendingDocIds;
			final P2pSyncClient client = activeSyncClient;
			pendingDocIds = null;

			new Thread(() -> runPushThread(docEntries, client), "P2pClientSyncPush").start();

			JSONObject result = new JSONObject();
			result.put(KEY_OK, true);
			return result.toString();
		} catch (JSONException e) {
			Log.e(TAG, "Error proceeding with sync", e);
			return errorJson("proceed_failed: " + e.getMessage());
		}
	}

	private String checkProceedPreconditions() {
		if (pendingDocIds == null || pendingDocIds.length() == 0) {
			return errorJson("no_pending_docs: nothing to sync");
		}
		if (activeSyncClient == null) {
			return errorJson("no_active_client: connection lost");
		}
		if (!STATE_PREVIEW.equals(clientSyncState)) {
			return errorJson("invalid_state: expected preview, got " + clientSyncState);
		}
		return null;
	}

	private void runPushThread(JSONArray docEntries, P2pSyncClient client) {
		try {
			clientSyncState = STATE_SYNCING;
			clientTotalDocs = docEntries.length();

			int pushed = pushDocsInBatches(docEntries, client);

			Log.i(TAG, "Client sync: push complete, " +
					pushed + " docs sent to Supervisor");

			signalSyncComplete(client, pushed);
			updateTrackerAfterPush(pushed);

			clientSyncState = STATE_COMPLETED;
			Log.i(TAG, "Client sync: completed successfully");

			saveSyncLogSafe();
		} catch (JSONException | IOException e) {
			Log.e(TAG, "Client sync failed", e);
			clientSyncError = "Sync error: " + e.getMessage();
			clientSyncState = STATE_FAILED;
		} finally {
			clientSyncRunning = false;
		}
	}

	private static final int PUSH_BATCH_SIZE = 50;

	/**
		* Push docs to the Supervisor in batches.
		* @return total number of docs accepted by the host
		*/
	private int pushDocsInBatches(JSONArray docEntries, P2pSyncClient client)
			throws JSONException, IOException {
		int totalDocs = docEntries.length();
		int pushed = 0;
		for (int i = 0; i < totalDocs; i += PUSH_BATCH_SIZE) {
			int end = Math.min(i + PUSH_BATCH_SIZE, totalDocs);
			pushed += pushOneBatch(docEntries, i, end, client);
			clientDocsSynced = pushed;
			Log.d(TAG, "Client sync: pushed " + pushed + "/" + totalDocs);
		}
		return pushed;
	}

	private int pushOneBatch(JSONArray docEntries, int start, int end, P2pSyncClient client)
			throws JSONException, IOException {
		JSONArray idStrings = new JSONArray();
		for (int j = start; j < end; j++) {
			idStrings.put(docEntries.getJSONObject(j).getString(KEY_DOC_ID));
		}

		String docsJson = p2pManager.getBridge().getDocsByIds(idStrings.toString());
		JSONArray docs = new JSONArray(docsJson);

		if (docs.length() == 0) {
			return 0;
		}

		JSONObject result = client.acceptDocs(docs);
		int accepted = result.optInt("accepted", 0);
		int transit = result.optInt("transit", 0);
		int rejected = result.optInt("rejected", 0);
		if (rejected > 0) {
			Log.w(TAG, "Client sync: " + rejected + " docs REJECTED by host. Errors: " +
					result.optJSONArray("errors"));
		}
		clientBytesTransferred.addAndGet(docsJson.length());
		return accepted + transit;
	}

	/**
		* Signal sync completion to the Supervisor (non-fatal on failure).
		*/
	private void signalSyncComplete(P2pSyncClient client, int pushed) {
		try {
			client.syncComplete(pushed, clientBytesTransferred.get());
			Log.i(TAG, "Client sync: sent sync-complete to Supervisor");
		} catch (JSONException | IOException e) {
			Log.w(TAG, "Client sync: failed to signal sync-complete (non-fatal)", e);
		}
	}

	/**
		* Update tracker session counters after a successful push.
		*/
	private void updateTrackerAfterPush(int pushed) {
		if (tracker != null) {
			P2pSession trackerSession = tracker.getCurrentSession();
			if (trackerSession != null) {
				trackerSession.incrementDocsPushed(pushed);
				trackerSession.addBytesTransferred(clientBytesTransferred.get());
			}
			tracker.completeSession();
		}
	}

	/**
		* Save sync log to PouchDB, logging errors without throwing.
		*/
	private void saveSyncLogSafe() {
		try {
			saveSyncLogToPouchDb(false);
		} catch (RuntimeException saveErr) {
			Log.e(TAG, "Client sync: failed to save sync log", saveErr);
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
			status.put(KEY_DOCS_SYNCED, session.getDocsPushed() + session.getDocsPulled());
			status.put(KEY_TOTAL_DOCS, session.getDocsPushed() + session.getDocsPulled() +
					session.getTransitDocs());
			status.put(KEY_BYTES_TRANSFERRED, session.getBytesTransferred());
			return session.getState().name().toLowerCase(Locale.ROOT);
		}
		status.put(KEY_DOCS_SYNCED, 0);
		status.put(KEY_TOTAL_DOCS, 0);
		status.put(KEY_BYTES_TRANSFERRED, 0);
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

		state = resolveHostState(status, state, httpSession, peerCount);
		logHostStatus(state, httpSession);
		return state;
	}

	private String resolveHostState(JSONObject status, String state,
			P2pSession httpSession, int peerCount) throws JSONException {
		if (httpSession != null && httpSession.getState() == P2pSession.State.COMPLETED) {
			populateSessionDocs(status, httpSession);
			return STATE_COMPLETED;
		}
		if (peerCount > 0 && httpSession != null
				&& httpSession.getState() == P2pSession.State.ACTIVE) {
			return deriveActiveHostState(status, httpSession);
		}
		return STATE_IDLE.equals(state) ? "waiting" : state;
	}

	private void populateSessionDocs(JSONObject status, P2pSession httpSession) throws JSONException {
		int sessionDocs = httpSession.getDocsPulled() + httpSession.getTransitDocs();
		status.put(KEY_DOCS_SYNCED, sessionDocs);
		status.put(KEY_TOTAL_DOCS, sessionDocs);
		status.put(KEY_BYTES_TRANSFERRED, httpSession.getBytesTransferred());
	}

	private void logHostStatus(String state, P2pSession httpSession) {
		if (httpSession != null) {
			Log.d(TAG, "Host status: state=" + state +
					" pulled=" + httpSession.getDocsPulled() +
					" transit=" + httpSession.getTransitDocs() +
					" bytes=" + httpSession.getBytesTransferred());
		}
	}

	private String deriveActiveHostState(JSONObject status, P2pSession httpSession) throws JSONException {
		if (httpSession.getDocsPulled() > 0 || httpSession.getDocsPushed() > 0
				|| httpSession.getTransitDocs() > 0) {
			int sessionDocs = httpSession.getDocsPulled() + httpSession.getTransitDocs();
			status.put(KEY_DOCS_SYNCED, sessionDocs);
			status.put(KEY_TOTAL_DOCS, sessionDocs);
			status.put(KEY_BYTES_TRANSFERRED, httpSession.getBytesTransferred());
			return STATE_SYNCING;
		}
		return "peer_connected";
	}

	private String populateClientModeState(JSONObject status) throws JSONException {
		String state = clientSyncState;
		status.put(KEY_DOCS_SYNCED, clientDocsSynced);
		status.put(KEY_TOTAL_DOCS, clientTotalDocs);
		status.put(KEY_BYTES_TRANSFERRED, clientBytesTransferred.get());
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
		JSONArray peersArray = buildConnectedPeersArray();
		status.put("connected_peers", peersArray);
	}

	private JSONArray buildConnectedPeersArray() {
		JSONArray peersArray = new JSONArray();
		int peerCount = p2pManager.getConnectedPeerCount();
		if (peerCount == 0) return peersArray;

		P2pSession httpSession = p2pManager.getHttpSession();
		if (httpSession != null) {
			String userId = httpSession.getPeerUserId();
			peersArray.put(userId != null ? userId : "peer");
		}
		return peersArray;
	}

	/**
		* Get all transit doc IDs for UI filtering.
		* Returns JSON array of doc IDs that should be hidden from the UI.
		* Must return in <50ms (uses in-memory HashMap lookup).
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
		* Get transit docs that are ready to be purged (pushed to server but not yet purged).
		* Returns JSON with batch info so the webapp can call db.purge() for each doc.
		* MUST use db.purge(), never db.remove().
		*
		* @return JSON string: {
		*   "batches": [
		*	 { "batch_id": "uuid", "doc_ids": ["doc1", "doc2", ...] }
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

			return buildPurgeResponse(purgeableBatchIds);
		} catch (JSONException e) {
			Log.e(TAG, "Error building purge response", e);
			return buildEmptyPurgeResponse();
		}
	}

	private String buildPurgeResponse(Set<String> purgeableBatchIds) throws JSONException {
		JSONArray batchesArray = new JSONArray();
		int totalDocs = 0;

		for (String batchId : purgeableBatchIds) {
			Set<String> docIds = transitDocManager.getDocIdsForBatch(batchId);
			if (docIds.isEmpty()) continue;

			batchesArray.put(buildBatchObject(batchId, docIds));
			totalDocs += docIds.size();
		}

		JSONObject response = new JSONObject();
		response.put("batches", batchesArray);
		response.put(KEY_TOTAL_DOCS, totalDocs);
		return response.toString();
	}

	private static JSONObject buildBatchObject(String batchId, Set<String> docIds) throws JSONException {
		JSONObject batchObj = new JSONObject();
		batchObj.put("batch_id", batchId);

		JSONArray docIdsArray = new JSONArray();
		for (String docId : docIds) {
			docIdsArray.put(docId);
		}
		batchObj.put("doc_ids", docIdsArray);
		return batchObj;
	}

	/**
		* Confirm that a batch has been purged by the webapp.
		* Called after webapp successfully runs db.purge() for all docs in a batch.
		* The webapp MUST use db.purge(id, rev), never db.remove().
		*
		* @param batchId the batch ID that was purged
		*/
	@JavascriptInterface
	public void p2pConfirmBatchPurged(String batchId) {
		if (transitDocManager != null && batchId != null && !batchId.isEmpty()) {
			transitDocManager.markBatchPurged(batchId);
			Log.i(TAG, "Batch purged confirmed: " + batchId);

			// Archive old purged batches if transit doc is oversized
			if (transitDocManager.isOversized()) {
				transitDocManager.archivePurgedBatches();
				Log.i(TAG, "Archived purged batches due to size limit ()");
			}
		}
	}

	/**
		* Get P2P sync history (completed sessions).
		* Returns JSON matching _local/p2p-sync-log schema from .
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
		* Check if there are stale transit docs (unpushed for >30 days).
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
			result.put("capability", capability.name().toLowerCase(Locale.ROOT));
			result.put("reason", getCapabilityReason(capability));

			result.put("api_level", Build.VERSION.SDK_INT);
			result.put("manufacturer", OemBatteryHelper.getManufacturer());
			result.put("model", OemBatteryHelper.getModel());
			result.put("battery_risk", OemBatteryHelper.getRiskLevel().name().toLowerCase(Locale.ROOT));

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
			return doInitialize(new JSONObject(configJson));
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

	private String doInitialize(JSONObject json) throws JSONException, P2pManager.P2pInitException {
		JSONObject configObj = json.optJSONObject("config");
		P2pConfig config = configObj != null ? P2pConfig.fromJson(configObj) : P2pConfig.defaults();

		ScopeManifest scopeManifest = ScopeManifest.fromJson(json.getJSONObject("scope_manifest"));
		String serverPublicKey = json.getString("server_public_key");

		JSONObject revObj = json.optJSONObject("revocation_list");
		RevocationList revocationList = revObj != null
				? RevocationList.fromJson(revObj)
				: RevocationList.empty();

		String deviceId = json.getString("device_id");
		String userId = json.getString("user_id");

		cacheJwtIfPresent(json);

		PouchDbBridge realBridge = createPouchDbBridge();
		p2pManager.initialize(config, serverPublicKey, revocationList,
				scopeManifest, realBridge, deviceId, userId);

		JSONObject result = new JSONObject();
		result.put(KEY_OK, true);
		return result.toString();
	}

	private void cacheJwtIfPresent(JSONObject json) {
		String jwt = json.optString("token", null);
		if (jwt != null && !jwt.isEmpty()) {
			cachedJwt = jwt;
			Log.d(TAG, "JWT token cached for P2P auth");
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
			JSONObject log = isHost ? tracker.buildRelayLog() : tracker.buildSyncLog();
			String docId = isHost ? RELAY_LOG_DOC_ID : SYNC_LOG_DOC_ID;
			saveMergedSessionsToPouch(log, docId, "saveSyncLogToPouchDb");
		} catch (JSONException e) {
			Log.e(TAG, "saveSyncLogToPouchDb: failed to save", e);
		}
	}

	private void saveMergedSessionsToPouch(JSONObject log, String docId, String callerTag) {
		JSONArray newSessions = log.optJSONArray(KEY_SESSIONS);
		if (newSessions == null || newSessions.length() == 0) {
			Log.d(TAG, callerTag + ": no sessions to save");
			return;
		}

		String sessionsJsonStr = newSessions.toString()
				.replace("\\", "\\\\")
				.replace("'", "\\'")
				.replace("\n", "\\n");

		String js = buildMergeSessionsJs(docId, sessionsJsonStr);
		String result = evalPouchDb(js);
		Log.i(TAG, callerTag + ": saved " + docId + LOG_RESULT_PREFIX + result);
	}

	private static String buildMergeSessionsJs(String docId, String sessionsJsonStr) {
		return "(async function() {" +
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
			saveMergedSessionsToPouch(log, docId, "savePrebuiltLogToPouchDb");
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
			Log.i(TAG, "saveTransitStateToPouchDb: saved " + docId + LOG_RESULT_PREFIX + result);
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
				return "Android 8.0 (API 26) or higher required. Current API level: " +
						Build.VERSION.SDK_INT;
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
				return "Location services must be enabled to start WiFi hotspot. " +
						"Please turn on Location in Settings.";
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
			response.put(KEY_TOTAL_DOCS, 0);
			return response.toString();
		} catch (JSONException e) {
			return "{\"batches\":[],\"total_docs\":0}";
		}
	}

	private String buildEmptySyncHistory() {
		try {
			JSONObject log = new JSONObject();
			log.put(KEY_DOC_ID, SYNC_LOG_DOC_ID);
			log.put(KEY_SESSIONS, new JSONArray());
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
