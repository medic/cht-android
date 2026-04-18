package org.medicmobile.webapp.mobile.p2p;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * GET /_p2p/status — Health check and session info.
 *
 * This endpoint requires NO authentication (used for discovery/heartbeat).
 *
 * Response (200):
 *   {
 *     "ok": true,
 *     "server_type": "cht-p2p",
 *     "version": 1,
 *     "session_active": boolean,
 *     "connected_peers": number
 *   }
 */
public final class StatusEndpoint {

    private static final String SERVER_TYPE = "cht-p2p";
    private static final int PROTOCOL_VERSION = 1;

    /**
     * Handle GET /_p2p/status request.
     *
     * @param activeSession Current active session, or null if none
     * @param connectedPeers Number of currently connected peers
     * @return JSON response object
     */
    public JSONObject handle(P2pSession activeSession, int connectedPeers) {
        try {
            JSONObject response = new JSONObject();
            response.put("ok", true);
            response.put("server_type", SERVER_TYPE);
            response.put("version", PROTOCOL_VERSION);
            response.put("session_active", activeSession != null
                    && activeSession.getState() == P2pSession.State.ACTIVE);
            response.put("connected_peers", connectedPeers);
            return response;
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to build status response", e);
        }
    }
}
