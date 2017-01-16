package org.medicmobile.webapp.mobile.migrate2crosswalk;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
	public JsonEntity get(String requestPath) throws CouchReplicationTargetException {
		return get(requestPath, NO_QUERY_PARAMS);
	}

	public JsonEntity get(String requestPath, Map<String, List<String>> queryParams) throws CouchReplicationTargetException {
		try {
			if("/".equals(requestPath)) {
				return JsonEntity.of(getDbDetails());
			} else if(matches(requestPath, "/_local")) {
				throw new DocNotFoundException(requestPath);
			} else if(matches(requestPath, "/_changes")) {
				return JsonEntity.of(
						JSON.obj("results", JSON.array(),
								"last_seq", 0));
			}

			if(requestPath.startsWith("/_")) {
				throw new UnsupportedInternalPathException(requestPath);
			}

			return JsonEntity.of(getDoc(requestPath));
		} catch(JSONException ex) {
			throw new RuntimeException(ex);
		}
	}

	public JsonEntity post(String requestPath, JSONObject requestBody) throws CouchReplicationTargetException {
		return post(requestPath, NO_QUERY_PARAMS, requestBody);
	}

	public JsonEntity post(String requestPath, Map<String, List<String>> queryParams, JSONObject requestBody) throws CouchReplicationTargetException {
		try {
			if(matches(requestPath, "/_local")) {
				throw new UnimplementedEndpointException();
			} else if(matches(requestPath, "/_bulk_docs")) {
				return _bulk_docs(requestPath, queryParams, requestBody);
			} else if(matches(requestPath,  "/_revs_diff")) {
				return _revs_diff(requestBody);
			}
		} catch(JSONException ex) {
			throw new RuntimeException(ex);
		}

		if(requestPath.startsWith("/_")) {
			throw new UnsupportedInternalPathException(requestPath);
		}
		throw new RuntimeException("Not yet implemented.");
	}

	public JsonEntity put(String requestPath, JSONObject requestBody) throws CouchReplicationTargetException {
		return put(requestPath, NO_QUERY_PARAMS, requestBody);
	}

	public JsonEntity put(String requestPath, Map<String, List<String>> queryParams, JSONObject requestBody) throws CouchReplicationTargetException {
		try {
			if(matches(requestPath, "/_local")) {
				return _local_PUT(requestPath, requestBody);
			}
		} catch(Exception ex) {
			throw new RuntimeException(ex);
		}

		if(requestPath.startsWith("/_")) {
			throw new UnsupportedInternalPathException(requestPath);
		}
		throw new RuntimeException("Not yet implemented.");
	}

//> SPECIFIC REQUEST HANDLERS
	private JsonEntity _local_PUT(String requestPath, JSONObject doc) throws IllegalDocException, JSONException {
		String docId = requestPath.substring(1);

		String internalDocId = docId.split("/")[1];

		String docRev = "1-" + Math.abs(new Random().nextInt());

		doc.put("_id", docId);
		doc.put("_rev", docRev);
		db.store_local(doc);

		return JsonEntity.of(JSON.obj(
				"ok", true,
				"id", docId,
				"rev", docRev));
	}

	private JsonEntity _revs_diff(JSONObject requestBody) throws JSONException {
		JSONObject response = new JSONObject();

		Iterator<String> docIds = requestBody.keys();
		while(docIds.hasNext()) {
			String docId = docIds.next();
			JSONArray revs = requestBody.getJSONArray(docId);
			JSONArray missing = new JSONArray();
			for(int i=0; i<revs.length(); ++i) {
				String rev = revs.getString(i);
				if(!db.exists(docId, rev))
					missing.put(rev);
			}
			if(missing.length() > 0) {
				response.put(docId, new JSONObject().put("missing", missing));
			}
		}
		return JsonEntity.of(response);
	}

	private JsonEntity _bulk_docs(String requestPath, Map<String, List<String>> queryParams, JSONObject requestBody) throws CouchReplicationTargetException {
		try {
			JSONArray docs = requestBody.optJSONArray("docs");

			if(docs == null) throw new EmptyResponseException();

			JSONArray saved = new JSONArray();
			for(int i=0; i<docs.length(); ++i) {
				JSONObject doc = docs.getJSONObject(i);
				saveDoc(doc);
				saved.put(JSON.obj("ok", true,
						"id", doc.getString("_id"),
						"rev", doc.getString("_rev")));
			}

			return JsonEntity.of(saved);
		} catch(JSONException ex) {
			// TODO this should be handled properly as per whatever
			// couch does
			throw new RuntimeException(ex);
		}
	}

	private JSONObject getDbDetails() throws JSONException {
		return JSON.obj("db_name", "medic",
				"doc_count", 0, // TODO calculate
				"doc_del_count", 0, // TODO calculate
				"update_seq", 0, // TODO calculate
				"purge_seq", 0, // TODO calculate
				"compact_running", false, // TODO what does this mean
				"disk_size", 0, // TODO calculate this, if we really care
				"data_size", 0, // TODO calculate this, if we really care
				"instance_start_time", 0, // TODO is this important?
				"disk_format_version", 0, // TODO what does this mean?
				"committed_update_seq", 0 /* TODO calculate this */);
	}

	private JSONObject getDoc(String requestPath) throws DocNotFoundException, JSONException {
		String id = Uri.parse(requestPath).getPath().substring(1);
		JSONObject doc = db.get(id);
		if(doc == null) throw new DocNotFoundException(id);
		return doc;
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

	private void trace(String method, String message, Object... extras) {
		MedicLog.trace(this, method + "(): " + message, extras);
	}
}

class CouchReplicationTargetException extends Exception {
	CouchReplicationTargetException() {}
	CouchReplicationTargetException(String message) { super(message); }
}

class DocNotFoundException extends CouchReplicationTargetException {
	DocNotFoundException(String id) { super(id); }
}

class EmptyResponseException extends CouchReplicationTargetException {}

class UnimplementedEndpointException extends CouchReplicationTargetException {}

class UnsupportedInternalPathException extends CouchReplicationTargetException {
	UnsupportedInternalPathException(String path) { super(path); }
}

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

class JsonEntity {
	private Object entity;
	private JsonEntity(Object entity) { this.entity = entity; }
	static JsonEntity of(JSONObject obj) { return new JsonEntity(obj); }
	static JsonEntity of(JSONArray arr) { return new JsonEntity(arr); }
	public String toString() { return entity.toString(); }
}
