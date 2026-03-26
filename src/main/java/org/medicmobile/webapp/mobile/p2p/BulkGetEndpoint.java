package org.medicmobile.webapp.mobile.p2p;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * POST /_p2p/bulk-get — Return doc bodies by ID from Supervisor's PouchDB.
 *
 * Request:
 *   { "docs": [{"id": "doc1"}, {"id": "doc2"}] }
 *
 * Response (CouchDB _bulk_get format):
 *   {
 *     "results": [
 *       { "id": "doc1", "docs": [{"ok": {...doc body...}}] },
 *       { "id": "doc2", "docs": [{"ok": {...doc body...}}] }
 *     ]
 *   }
 *
 * Docs not found return:
 *   { "id": "missing1", "docs": [{"error": {"id": "missing1", "error": "not_found", "reason": "missing"}}] }
 */
public final class BulkGetEndpoint {

    private static final String TAG = "BulkGetEndpoint";
    private static final int MAX_BATCH_SIZE = 500;

    private final PouchDbBridge bridge;

    public BulkGetEndpoint(PouchDbBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * Handle POST /_p2p/bulk-get request.
     *
     * @param requestBody Raw JSON request body
     * @param session     Active P2P session
     * @return JSON response object
     */
    public JSONObject handle(String requestBody, P2pSession session) {
        try {
            return doHandle(requestBody, session);
        } catch (JSONException e) {
            Log.e(TAG, "Error processing bulk-get", e);
            return errorResponse("malformed_request");
        }
    }

    private JSONObject doHandle(String requestBody, P2pSession session) throws JSONException {
        if (requestBody == null || requestBody.isEmpty()) {
            return errorResponse("empty_request");
        }

        JSONObject body = new JSONObject(requestBody);
        JSONArray requestedDocs = body.optJSONArray("docs");
        if (requestedDocs == null || requestedDocs.length() == 0) {
            JSONObject response = new JSONObject();
            response.put("results", new JSONArray());
            return response;
        }

        // Guard: limit batch size to prevent memory issues
        if (requestedDocs.length() > MAX_BATCH_SIZE) {
            return errorResponse("batch_too_large: max " + MAX_BATCH_SIZE + " docs per request");
        }

        // Extract IDs from request
        JSONArray idsArray = new JSONArray();
        for (int i = 0; i < requestedDocs.length(); i++) {
            JSONObject docReq = requestedDocs.getJSONObject(i);
            String id = docReq.optString("id", null);
            if (id != null) {
                idsArray.put(id);
            }
        }

        // Fetch docs from PouchDB via bridge
        String docsJson = bridge.getDocsByIds(idsArray.toString());
        JSONArray fetchedDocs = (docsJson != null && !docsJson.isEmpty())
                ? new JSONArray(docsJson) : new JSONArray();

        // Build a lookup map: _id -> doc
        JSONObject docMap = new JSONObject();
        for (int i = 0; i < fetchedDocs.length(); i++) {
            JSONObject doc = fetchedDocs.getJSONObject(i);
            String docId = doc.optString("_id", null);
            if (docId != null) {
                docMap.put(docId, doc);
            }
        }

        // Build CouchDB _bulk_get format response
        JSONArray results = new JSONArray();
        long totalBytes = 0;

        for (int i = 0; i < idsArray.length(); i++) {
            String requestedId = idsArray.getString(i);
            JSONObject result = new JSONObject();
            result.put("id", requestedId);

            JSONArray docsArray = new JSONArray();

            if (docMap.has(requestedId)) {
                JSONObject doc = docMap.getJSONObject(requestedId);
                JSONObject okWrapper = new JSONObject();
                okWrapper.put("ok", doc);
                docsArray.put(okWrapper);

                totalBytes += doc.toString().length();
            } else {
                // Doc not found
                JSONObject errorWrapper = new JSONObject();
                JSONObject errorDetail = new JSONObject();
                errorDetail.put("id", requestedId);
                errorDetail.put("error", "not_found");
                errorDetail.put("reason", "missing");
                errorWrapper.put("error", errorDetail);
                docsArray.put(errorWrapper);
            }

            result.put("docs", docsArray);
            results.put(result);
        }

        // Update session counters
        if (session != null) {
            session.incrementDocsPushed(docMap.length());
            session.addBytesTransferred(totalBytes);
            session.updateLastActivity();
        }

        Log.d(TAG, "bulk-get returning " + docMap.length() + " docs"
                + " (" + (idsArray.length() - docMap.length()) + " not found)");

        JSONObject response = new JSONObject();
        response.put("results", results);
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
