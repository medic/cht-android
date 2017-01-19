package org.medicmobile.webapp.mobile.migrate2crosswalk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

class CouchChangesFeed {
	private int last_seq;
	private JSONArray results = new JSONArray();

	public void addDoc(int seq, JSONObject doc) throws JSONException {
		last_seq = Math.max(seq, last_seq);
		results.put(JSON.obj(
			"changes", JSON.array(
				JSON.obj("rev", doc.getString("_rev"))
			),
			"id", doc.getString("_id"),
			"seq", seq
		));
	}

	public JSONObject get() throws JSONException {
		return JSON.obj("results", results,
				"last_seq", last_seq);
	}
}
