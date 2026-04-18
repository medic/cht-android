package org.medicmobile.webapp.mobile.p2p;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
	* GET /_p2p/get-deletes — Always returns an empty list.
	*
	* Per RFC: No purging during P2P sync. Deletions are not propagated
	* via P2P to avoid data loss. Rule 5: P2P is ADDITIVE ONLY.
	*
	* Response (200):
	*   { "doc_ids": [] }
	*/
public final class GetDeletesEndpoint {

	/**
		* Handle GET /_p2p/get-deletes request.
		* Always returns empty — no deletions via P2P.
		*
		* @return JSON response object
		*/
	public JSONObject handle() {
		try {
			JSONObject response = new JSONObject();
			response.put("doc_ids", new JSONArray());
			return response;
		} catch (JSONException e) {
			throw new IllegalStateException("Failed to build get-deletes response", e);
		}
	}
}
