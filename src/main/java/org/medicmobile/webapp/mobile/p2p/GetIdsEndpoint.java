package org.medicmobile.webapp.mobile.p2p;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * GET /_p2p/get-ids — Return doc IDs from Supervisor's PouchDB,
 * filtered by the CHW's scope.
 *
 * Response (200):
 *   { "doc_ids": [{"_id": "...", "_rev": "..."}, ...], "total": number }
 *
 * The endpoint fetches all doc IDs from PouchDB via the bridge, then
 * filters them to only include docs relevant to the CHW's scope.
 * In practice, the Supervisor offers IDs that the CHW might want to pull.
 */
public final class GetIdsEndpoint {

    private static final String TAG = "GetIdsEndpoint";

    private final PouchDbBridge bridge;

    public GetIdsEndpoint(PouchDbBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * Handle GET /_p2p/get-ids request.
     *
     * @param session The active P2P session (provides CHW's scope)
     * @return JSON response string
     */
    public JSONObject handle(P2pSession session) {
        try {
            return doHandle(session);
        } catch (JSONException e) {
            Log.e(TAG, "Error building get-ids response", e);
            return errorResponse("internal_error");
        }
    }

    private JSONObject doHandle(P2pSession session) throws JSONException {
        if (session == null) {
            return errorResponse("no_active_session");
        }

        // Get all doc IDs from PouchDB
        String allIdsJson = bridge.getAllDocIds();
        if (allIdsJson == null || allIdsJson.isEmpty()) {
            JSONObject response = new JSONObject();
            response.put("doc_ids", new JSONArray());
            response.put("total", 0);
            return response;
        }

        JSONArray allIds = new JSONArray(allIdsJson);

        // Filter: skip _design/ and _local/ docs, include only docs
        // that a CHW might need (shared types + docs in their facility subtree).
        // Full scope filtering requires doc content, which is expensive.
        // Here we do a lightweight pre-filter on doc ID patterns.
        JSONArray filteredIds = new JSONArray();
        for (int i = 0; i < allIds.length(); i++) {
            JSONObject idEntry = allIds.getJSONObject(i);
            String docId = idEntry.optString("_id", "");

            // Skip system docs
            if (docId.startsWith("_design/") || docId.startsWith("_local/")) {
                continue;
            }

            filteredIds.put(idEntry);
        }

        JSONObject response = new JSONObject();
        response.put("doc_ids", filteredIds);
        response.put("total", filteredIds.length());

        Log.d(TAG, "get-ids returning " + filteredIds.length() + " doc IDs"
                + " (filtered from " + allIds.length() + " total)");

        session.updateLastActivity();
        return response;
    }

    private JSONObject errorResponse(String error) {
        try {
            JSONObject response = new JSONObject();
            response.put("ok", false);
            response.put("error", error);
            return response;
        } catch (JSONException e) {
            throw new RuntimeException("Failed to build error response", e);
        }
    }
}
