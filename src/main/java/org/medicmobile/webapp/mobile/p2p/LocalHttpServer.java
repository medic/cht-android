package org.medicmobile.webapp.mobile.p2p;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Local HTTP server for P2P sync using NanoHTTPD.
 * Runs on the Supervisor's phone, serving sync endpoints to connected CHWs.
 *
 * Endpoints:
 *   POST /_p2p/auth         → AuthEndpoint (no session required)
 *   GET  /_p2p/get-ids      → GetIdsEndpoint
 *   POST /_p2p/bulk-get     → BulkGetEndpoint
 *   POST /_p2p/accept-docs  → AcceptDocsEndpoint
 *   GET  /_p2p/get-deletes  → GetDeletesEndpoint
 *   GET  /_p2p/status       → StatusEndpoint (no auth required)
 *
 * All endpoints except /status require a valid JWT in the
 * Authorization: Bearer header. The /auth endpoint validates the JWT
 * and creates a session; subsequent endpoints check that a session exists.
 */
public class LocalHttpServer extends NanoHTTPD {

    private static final String TAG = "LocalHttpServer";
    private static final int DEFAULT_PORT = 8443;
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String P2P_PREFIX = "/_p2p/";

    private final P2pAuthenticator authenticator;
    private final P2pConfig config;
    private final ScopeManifest supervisorScope;

    // Endpoint handlers
    private final AuthEndpoint authEndpoint;
    private final GetIdsEndpoint getIdsEndpoint;
    private final BulkGetEndpoint bulkGetEndpoint;
    private final AcceptDocsEndpoint acceptDocsEndpoint;
    private final GetDeletesEndpoint getDeletesEndpoint;
    private final StatusEndpoint statusEndpoint;

    // Active session — only one CHW session at a time
    private volatile P2pSession activeSession;
    private volatile int connectedPeers;

    // Callback to notify P2pManager when sync completes (for tracker persistence)
    private volatile SessionCompleteCallback sessionCompleteCallback;

    /**
     * Callback interface for sync session completion notifications.
     * Allows P2pManager to be notified when a peer signals sync-complete,
     * so the tracker can record the session for history persistence.
     */
    public interface SessionCompleteCallback {
        void onSessionComplete(P2pSession session);
    }

    /**
     * Dependencies needed to construct a LocalHttpServer.
     */
    public static class ServerDeps {
        final P2pAuthenticator authenticator;
        final P2pConfig config;
        final ScopeManifest supervisorScope;
        final PouchDbBridge bridge;
        final TransitDocCallback transitCallback;

        public ServerDeps(P2pAuthenticator authenticator, P2pConfig config,
                          ScopeManifest supervisorScope, PouchDbBridge bridge,
                          TransitDocCallback transitCallback) {
            if (authenticator == null) {
                throw new IllegalArgumentException("authenticator must not be null");
            }
            if (config == null) {
                throw new IllegalArgumentException("config must not be null");
            }
            if (supervisorScope == null) {
                throw new IllegalArgumentException("supervisorScope must not be null");
            }
            if (bridge == null) {
                throw new IllegalArgumentException("bridge must not be null");
            }
            this.authenticator = authenticator;
            this.config = config;
            this.supervisorScope = supervisorScope;
            this.bridge = bridge;
            this.transitCallback = transitCallback;
        }
    }

    /**
     * Create a LocalHttpServer with default port (8443).
     */
    public LocalHttpServer(ServerDeps deps) {
        this(DEFAULT_PORT, deps);
    }

    /**
     * Create a LocalHttpServer on a specific port.
     */
    public LocalHttpServer(int port, ServerDeps deps) {
        super(port);

        this.authenticator = deps.authenticator;
        this.config = deps.config;
        this.supervisorScope = deps.supervisorScope;
        this.activeSession = null;
        this.connectedPeers = 0;

        // Initialize endpoint handlers
        this.authEndpoint = new AuthEndpoint(deps.authenticator, deps.config, deps.supervisorScope);
        this.getIdsEndpoint = new GetIdsEndpoint(deps.bridge);
        this.bulkGetEndpoint = new BulkGetEndpoint(deps.bridge);
        this.acceptDocsEndpoint = new AcceptDocsEndpoint(deps.bridge, deps.transitCallback,
                deps.supervisorScope, deps.config);
        this.getDeletesEndpoint = new GetDeletesEndpoint();
        this.statusEndpoint = new StatusEndpoint();
    }

    /**
     * Start the HTTP server.
     *
     * @throws IOException if the server cannot bind to the port
     */
    public void startServer() throws IOException {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        Log.i(TAG, "P2P HTTP server started on port " + getListeningPort());
    }

    /**
     * Stop the HTTP server and clean up the active session.
     */
    public void stopServer() {
        if (activeSession != null && activeSession.getState() == P2pSession.State.ACTIVE) {
            activeSession.fail("server_stopped");
        }
        stop();
        activeSession = null;
        connectedPeers = 0;
        Log.i(TAG, "P2P HTTP server stopped");
    }

    /**
     * Get the currently active P2P session, or null if none.
     */
    public P2pSession getActiveSession() {
        return activeSession;
    }

    /**
     * Set a callback to be notified when a sync session completes.
     */
    public void setSessionCompleteCallback(SessionCompleteCallback callback) {
        this.sessionCompleteCallback = callback;
    }

    /**
     * Get the number of connected peers (0 or 1).
     */
    public int getConnectedPeerCount() {
        return connectedPeers;
    }

    /**
     * Get the port this server is listening on.
     */
    public int getPort() {
        return getListeningPort();
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        Log.d(TAG, method + " " + uri);

        if (isCaptivePortalCheck(uri)) {
            Log.d(TAG, "Captive portal check intercepted: " + uri);
            return newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "");
        }

        if (!uri.startsWith(P2P_PREFIX)) {
            return jsonResponse(Response.Status.NOT_FOUND, errorJson("not_found"));
        }

        return handleP2pRequest(uri, method, session);
    }

    private Response handleP2pRequest(String uri, Method method, IHTTPSession session) {
        String endpoint = uri.substring(P2P_PREFIX.length());

        if ("status".equals(endpoint) && method == Method.GET) {
            return handleStatus();
        }
        if ("auth".equals(endpoint) && method == Method.POST) {
            return handleAuth(session);
        }

        Response sessionCheck = checkActiveSession();
        if (sessionCheck != null) {
            return sessionCheck;
        }

        return routeEndpoint(endpoint, method, session);
    }

    private boolean isCaptivePortalCheck(String uri) {
        return uri.contains("generate_204") || uri.contains("gen_204")
                || uri.contains("connectivitycheck") || uri.contains("captive-portal");
    }

    private Response checkActiveSession() {
        if (activeSession == null || activeSession.getState() != P2pSession.State.ACTIVE) {
            return jsonResponse(Response.Status.UNAUTHORIZED, errorJson("no_active_session"));
        }
        if (activeSession.isTimedOut()) {
            activeSession.fail("session_timeout");
            activeSession = null;
            return jsonResponse(Response.Status.UNAUTHORIZED, errorJson("session_timeout"));
        }
        return null;
    }

    private Response routeEndpoint(String endpoint, Method method, IHTTPSession session) {
        Response response = dispatchEndpoint(endpoint, method, session);
        if (response != null) {
            return response;
        }
        return jsonResponse(Response.Status.METHOD_NOT_ALLOWED,
                errorJson("method_not_allowed: " + method + " " + session.getUri()));
    }

    private Response dispatchEndpoint(String endpoint, Method method, IHTTPSession session) {
        switch (endpoint) {
            case "get-ids":
                return method == Method.GET ? handleGetIds() : null;
            case "bulk-get":
                return method == Method.POST ? handleBulkGet(session) : null;
            case "accept-docs":
                return method == Method.POST ? handleAcceptDocs(session) : null;
            case "get-deletes":
                return method == Method.GET ? handleGetDeletes() : null;
            case "sync-complete":
                return method == Method.POST ? handleSyncComplete(session) : null;
            default:
                return jsonResponse(Response.Status.NOT_FOUND, errorJson("unknown_endpoint"));
        }
    }

    // --- Endpoint handlers ---

    private Response handleStatus() {
        JSONObject body = statusEndpoint.handle(activeSession, connectedPeers);
        return jsonResponse(Response.Status.OK, body);
    }

    private synchronized Response handleAuth(IHTTPSession session) {
        // Only allow one session at a time
        if (activeSession != null && activeSession.getState() == P2pSession.State.ACTIVE) {
            return jsonResponse(Response.Status.CONFLICT,
                    errorJson("session_already_active"));
        }

        String requestBody = readRequestBody(session);
        AuthEndpoint.AuthResponse authResponse = authEndpoint.handle(requestBody);

        Response.IStatus status = authResponse.getStatusCode() == 200
                ? Response.Status.OK : Response.Status.UNAUTHORIZED;

        if (authResponse.isSuccess()) {
            activeSession = authResponse.getSession();
            connectedPeers = 1;
            Log.i(TAG, "Session established: " + activeSession.getSessionId());
        }

        return jsonResponse(status, authResponse.getBody());
    }

    private Response handleGetIds() {
        JSONObject body = getIdsEndpoint.handle(activeSession);
        return jsonResponse(Response.Status.OK, body);
    }

    private Response handleBulkGet(IHTTPSession session) {
        String requestBody = readRequestBody(session);
        JSONObject body = bulkGetEndpoint.handle(requestBody, activeSession);
        return jsonResponse(Response.Status.OK, body);
    }

    private Response handleAcceptDocs(IHTTPSession session) {
        String requestBody = readRequestBody(session);
        JSONObject body = acceptDocsEndpoint.handle(requestBody, activeSession);
        return jsonResponse(Response.Status.OK, body);
    }

    private Response handleGetDeletes() {
        JSONObject body = getDeletesEndpoint.handle();
        return jsonResponse(Response.Status.OK, body);
    }

    private synchronized Response handleSyncComplete(IHTTPSession session) {
        if (activeSession == null) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("no_active_session"));
        }
        try {
            String requestBody = readRequestBody(session);
            JSONObject body = new JSONObject(requestBody);
            int docsPushed = body.optInt("docs_pushed", 0);
            long bytesTransferred = body.optLong("bytes_transferred", 0);
            activeSession.complete();
            Log.i(TAG, "Sync completed by peer: " + docsPushed + " docs, "
                    + bytesTransferred + " bytes");

            notifySessionComplete();

            JSONObject response = new JSONObject();
            response.put("ok", true);
            return jsonResponse(Response.Status.OK, response);
        } catch (JSONException e) {
            Log.e(TAG, "Error handling sync-complete", e);
            return jsonResponse(Response.Status.INTERNAL_ERROR, errorJson("sync_complete_failed"));
        }
    }

    private void notifySessionComplete() {
        if (sessionCompleteCallback != null) {
            try {
                sessionCompleteCallback.onSessionComplete(activeSession);
            } catch (RuntimeException callbackErr) {
                Log.e(TAG, "Error in session complete callback", callbackErr);
            }
        }
    }

    // --- Helpers ---

    /**
     * Read the full request body from a NanoHTTPD session.
     * NanoHTTPD requires calling parseBody() first for POST requests.
     */
    private String readRequestBody(IHTTPSession session) {
        try {
            Map<String, String> bodyMap = new HashMap<>();
            session.parseBody(bodyMap);
            String postData = bodyMap.get("postData");
            if (postData != null) {
                return postData;
            }
            return readFromInputStream(session);
        } catch (IOException | ResponseException e) {
            Log.e(TAG, "Error reading request body", e);
            return "";
        }
    }

    private String readFromInputStream(IHTTPSession session) throws IOException {
        long contentLength = getContentLength(session);
        if (contentLength <= 0) {
            return "";
        }
        BufferedReader reader = new BufferedReader( //NOPMD - CloseResource: stream owned by NanoHTTPD session
                new InputStreamReader(session.getInputStream()));
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        long remaining = contentLength;
        while (remaining > 0 && (read = reader.read(buffer, 0,
                (int) Math.min(buffer.length, remaining))) != -1) {
            sb.append(buffer, 0, read);
            remaining -= read;
        }
        return sb.toString();
    }

    /**
     * Extract Content-Length from request headers.
     */
    private long getContentLength(IHTTPSession session) {
        String contentLengthStr = session.getHeaders().get("content-length");
        if (contentLengthStr != null) {
            try {
                return Long.parseLong(contentLengthStr);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Build a JSON error response body.
     */
    private JSONObject errorJson(String error) {
        try {
            JSONObject json = new JSONObject();
            json.put("ok", false);
            json.put("error", error);
            return json;
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to build error JSON", e);
        }
    }

    /**
     * Build a NanoHTTPD Response with JSON content type.
     */
    private Response jsonResponse(Response.IStatus status, JSONObject body) {
        String bodyStr = body != null ? body.toString() : "{}";
        return newFixedLengthResponse(status, CONTENT_TYPE_JSON, bodyStr);
    }
}
