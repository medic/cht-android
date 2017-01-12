package org.medicmobile.webapp.mobile.migrate2crosswalk;

import android.content.Context;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.medicmobile.webapp.mobile.MedicLog;

import static java.util.Collections.emptyMap;

class CouchReplicationTarget {
	private static final Map<String, List<String>> NO_QUERY_PARAMS = emptyMap();

	private TempCouchDb db;

//> Constructor
	public CouchReplicationTarget(Context ctx) {
		this.db = TempCouchDb.getInstance(ctx);
	}

//> JSON handlers
	public JSONObject get(String requestPath) throws CouchReplicationTargetException {
		return get(requestPath, NO_QUERY_PARAMS);
	}

	public JSONObject get(String requestPath, Map<String, List<String>> queryParams) throws CouchReplicationTargetException {
		if(matches(requestPath, "/_local")) {
			throw new UnimplementedEndpointException();
		} else if(matches(requestPath, "/_changes")) {
			try {
				return JSON.obj("results", JSON.array(),
						"last_seq", 0);
			} catch(JSONException ex) {
				throw new RuntimeException(ex);
			}
		}
		throw new RuntimeException("Not yet implemented.  RequestPath: " + requestPath);
	}

	public JSONObject post(String requestPath, JSONObject requestBody) throws CouchReplicationTargetException {
		return post(requestPath, NO_QUERY_PARAMS, requestBody);
	}

	public JSONObject post(String requestPath, Map<String, List<String>> queryParams, JSONObject requestBody) throws CouchReplicationTargetException {
		if(matches(requestPath, "/_local")) {
			throw new UnimplementedEndpointException();
		} else if(matches(requestPath, "/_bulk_docs")) {
			return _bulk_docs(requestPath, queryParams, requestBody);
		} else if(matches(requestPath,  "/_revs_diff")) {
			return new JSONObject();
		}
		throw new RuntimeException("Not yet implemented.");
	}

//> SPECIFIC REQUEST HANDLERS
	private JSONObject _bulk_docs(String requestPath, Map<String, List<String>> queryParams, JSONObject requestBody) throws CouchReplicationTargetException {
		try {
			JSONArray docs = requestBody.optJSONArray("docs");

			if(docs == null) throw new EmptyResponseException();

			for(int i=0; i<docs.length(); ++i) {
				saveDoc(docs.getJSONObject(i));
			}
			return JSON.obj();
		} catch(JSONException ex) {
			// TODO this should be handled properly as per whatever
			// couch does
			throw new RuntimeException(ex);
		}
	}

//> HELPERS
	private void saveDoc(JSONObject doc) {
		try {
			db.store(doc);
		} catch(Exception ex) {
			MedicLog.warn(ex, "Exception thrown while trying to store doc in db: %s", doc);
			// TODO remove this throw - it's just here for debugging tests
			throw new RuntimeException(ex);
		}
	}

	private static boolean matches(String requestPath, String dir) {
		return requestPath.equals(dir) ||
				requestPath.startsWith(dir + "/");
	}

	private void trace(String message, Object... extras) {
		MedicLog.trace(this, message, extras);
	}
}

class CouchReplicationTargetException extends Exception {}

class EmptyResponseException extends CouchReplicationTargetException {}

class UnimplementedEndpointException extends CouchReplicationTargetException {}

final class JSON {
	static final JSONObject obj(Object... args) throws JSONException {
		assert(args.length % 2 == 0): "Must supply an even number of args.";

		JSONObject o = new JSONObject();

		for(int i=0; i<args.length; i+=2) {
			String key = (String) args[i];
			Object val = args[i+1];
			o.put(key, val);
		}

		return o;
	}

	static final JSONArray array(Object... contents) throws JSONException {
		JSONArray a = new JSONArray();
		for(Object o : contents) a.put(o);
		return a;
	}
}
