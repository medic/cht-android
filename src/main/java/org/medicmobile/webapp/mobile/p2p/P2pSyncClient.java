package org.medicmobile.webapp.mobile.p2p;

import android.net.Network;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP sync client for the CHW (peer) side.
 * Connects to the Supervisor's LocalHttpServer endpoints to exchange docs.
 *
 * Flow:
 * 1. authenticate() → POST /_p2p/auth with JWT
 * 2. getIds()       → GET /_p2p/get-ids (supervisor's doc IDs)
 * 3. bulkGet(ids)   → POST /_p2p/bulk-get (download docs from supervisor)
 * 4. acceptDocs()   → POST /_p2p/accept-docs (upload CHW docs to supervisor)
 *
 * All calls are synchronous — caller must run on a background thread.
 */
public class P2pSyncClient {

    private static final String TAG = "P2pSyncClient";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final String baseUrl;
    private String sessionToken; // Set after successful auth
    private String deviceId;
    private Network boundNetwork; // When set, HTTP calls route through this network

    /**
     * @param host supervisor's IP address (e.g., "192.168.43.1")
     * @param port supervisor's HTTP server port (e.g., 8443)
     */
    public P2pSyncClient(String host, int port) {
        this.baseUrl = "http://" + host + ":" + port + "/_p2p";
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * Bind all HTTP connections to a specific network.
     * Required on Android when the hotspot WiFi (no internet) is not the default network.
     * Without this, HttpURLConnection routes through cellular/previous WiFi.
     */
    public void setNetwork(Network network) {
        this.boundNetwork = network;
    }

    /**
     * Step 1: Authenticate with the supervisor.
     * Sends JWT to POST /_p2p/auth.
     *
     * @param jwt the P2P JWT token from /api/v1/p2p/authorize
     * @return null on success, or an error string describing the failure
     */
    public String authenticate(String jwt) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("p2p_token", jwt);
        body.put("device_id", deviceId != null ? deviceId : "unknown");

        JSONObject response = postJson("/auth", body, null);
        boolean ok = response.optBoolean("ok", false);
        if (ok) {
            sessionToken = response.optString("session_id", null);
            Log.i(TAG, "Authenticated with supervisor, session=" + sessionToken
                    + ", user=" + response.optString("user_id", "?"));
            return null;
        } else {
            String error = response.optString("error", "unknown");
            Log.e(TAG, "Auth failed: " + error);
            return error;
        }
    }

    /**
     * Step 2: Get document IDs from the supervisor.
     *
     * @return JSONArray of doc ID strings
     */
    public JSONArray getIds() throws IOException, JSONException {
        JSONObject response = getJson("/get-ids");
        return response.optJSONArray("doc_ids");
    }

    /**
     * Step 3: Download docs from supervisor by ID.
     * Sends in CouchDB _bulk_get format: { "docs": [{"id": "doc1"}, ...] }
     *
     * @param docIds array of doc ID strings to fetch
     * @return JSONArray of doc objects from results
     */
    public JSONArray bulkGet(JSONArray docIds) throws IOException, JSONException {
        // Convert string IDs to {id: "..."} format expected by BulkGetEndpoint
        JSONArray docsArray = new JSONArray();
        for (int i = 0; i < docIds.length(); i++) {
            JSONObject entry = new JSONObject();
            entry.put("id", docIds.getString(i));
            docsArray.put(entry);
        }

        JSONObject body = new JSONObject();
        body.put("docs", docsArray);

        JSONObject response = postJson("/bulk-get", body, sessionToken);

        // Extract actual doc bodies from CouchDB _bulk_get format
        JSONArray results = response.optJSONArray("results");
        JSONArray docs = new JSONArray();
        if (results != null) {
            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.getJSONObject(i);
                JSONArray resultDocs = result.optJSONArray("docs");
                if (resultDocs != null && resultDocs.length() > 0) {
                    JSONObject firstDoc = resultDocs.getJSONObject(0);
                    if (firstDoc.has("ok")) {
                        docs.put(firstDoc.getJSONObject("ok"));
                    }
                }
            }
        }
        return docs;
    }

    /**
     * Step 4: Upload CHW docs to supervisor.
     *
     * @param docs JSONArray of doc objects
     * @return JSONObject with results
     */
    public JSONObject acceptDocs(JSONArray docs) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("docs", docs);

        return postJson("/accept-docs", body, sessionToken);
    }

    /**
     * Step 5: Signal sync completion to the supervisor.
     * POST /_p2p/sync-complete tells the host that the CHW is done pushing.
     *
     * @param docsPushed total docs pushed (in-scope + transit)
     * @param bytesTransferred total bytes transferred
     * @return JSONObject with server response
     */
    public JSONObject syncComplete(int docsPushed, long bytesTransferred)
            throws IOException, JSONException {
        JSONObject body = new JSONObject();
        body.put("docs_pushed", docsPushed);
        body.put("bytes_transferred", bytesTransferred);
        return postJson("/sync-complete", body, sessionToken);
    }

    /**
     * Check server status (no auth required).
     */
    public JSONObject getStatus() throws IOException, JSONException {
        return getJson("/status");
    }

    /**
     * Quick connectivity check with short timeout.
     * Returns true if the server is reachable.
     */
    public boolean isReachable() {
        try {
            URL url = new URL(baseUrl + "/status");
            HttpURLConnection conn = openConnection(url);
            try {
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                int code = conn.getResponseCode();
                return code == 200;
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            Log.d(TAG, "isReachable(" + baseUrl + "): " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            return false;
        }
    }

    // --- HTTP helpers ---

    /**
     * Open an HttpURLConnection, routing through boundNetwork if set.
     * This is critical: without network binding, Android routes through the default
     * network (cellular), not the hotspot WiFi which has no internet.
     */
    private HttpURLConnection openConnection(URL url) throws IOException {
        if (boundNetwork != null) {
            return (HttpURLConnection) boundNetwork.openConnection(url);
        }
        return (HttpURLConnection) url.openConnection();
    }

    private JSONObject getJson(String endpoint) throws IOException, JSONException {
        URL url = new URL(baseUrl + endpoint);
        HttpURLConnection conn = openConnection(url);
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            if (sessionToken != null) {
                conn.setRequestProperty("Authorization", "Bearer " + sessionToken);
            }

            int code = conn.getResponseCode();
            String body = readBody(conn, code);
            Log.d(TAG, "GET " + endpoint + " → " + code);
            return new JSONObject(body);
        } finally {
            conn.disconnect();
        }
    }

    private JSONObject postJson(String endpoint, JSONObject body, String token)
            throws IOException, JSONException {
        URL url = new URL(baseUrl + endpoint);
        HttpURLConnection conn = openConnection(url);
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            if (token != null) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }

            int code = conn.getResponseCode();
            String responseBody = readBody(conn, code);
            Log.d(TAG, "POST " + endpoint + " → " + code);
            return new JSONObject(responseBody);
        } finally {
            conn.disconnect();
        }
    }

    private String readBody(HttpURLConnection conn, int code) throws IOException {
        InputStream stream = (code >= 200 && code < 400)
                ? conn.getInputStream()
                : conn.getErrorStream();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
