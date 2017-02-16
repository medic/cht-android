package org.medicmobile.webapp.mobile.migrate2crosswalk;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.medicmobile.webapp.mobile.MedicLog;

import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyMap;

class CouchReplicationTarget {
	private static final Map<String, List<String>> NO_QUERY_PARAMS = emptyMap();

	private TempCouchDb db;

//> Constructor
	public CouchReplicationTarget(Context ctx) {
		this.db = TempCouchDb.getInstance(ctx);
	}

//> JSON handlers
	public FcResponse get(String requestPath) throws CouchReplicationTargetException {
		return get(requestPath, NO_QUERY_PARAMS);
	}

	public FcResponse get(String requestPath, Map<String, List<String>> queryParams) throws CouchReplicationTargetException {
		try {
			if("/".equals(requestPath)) {
				return FcResponse.of(getDbDetails());
			} else if(matches(requestPath, "/_local")) {
				return FcResponse.of(_local_GET(requestPath));
			} else if(matches(requestPath, "/_all_docs")) {
				return FcResponse.of(_all_docs_GET(queryParams));
			} else if(matches(requestPath, "/_changes")) {
				return FcResponse.of(_changes_GET(queryParams));
			}

			if(requestPath.startsWith("/_")) {
				if(requestPath.startsWith("/_design/")) {
					return getDoc(requestPath, queryParams);
				}
				throw new UnsupportedInternalPathException(requestPath);
			}

			if(requestPath.substring(1).contains("/")) {
				return getAttachment(requestPath);
			}

			return getDoc(requestPath, queryParams);
		} catch(JSONException ex) {
			throw new RuntimeException(ex);
		}
	}

	public FcResponse post(String requestPath, JSONObject requestBody) throws CouchReplicationTargetException {
		return post(requestPath, NO_QUERY_PARAMS, requestBody);
	}

	public FcResponse post(String requestPath, Map<String, List<String>> queryParams, JSONObject requestBody) throws CouchReplicationTargetException {
		try {
			if(matches(requestPath, "/_local")) {
				throw new UnimplementedEndpointException();
			} else if(matches(requestPath, "/_all_docs")) {
				return FcResponse.of(_all_docs_POST(queryParams, requestBody));
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

	public FcResponse put(String requestPath, JSONObject requestBody) throws CouchReplicationTargetException {
		return put(requestPath, NO_QUERY_PARAMS, requestBody);
	}

	public FcResponse put(String requestPath, Map<String, List<String>> queryParams, JSONObject requestBody) throws CouchReplicationTargetException {
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
	private JSONObject _all_docs_GET(Map<String, List<String>> queryParams) throws JSONException {
		String key = getFirstString(queryParams, "key");
		if(key != null) {
			key = urlDecode(key);
			if(key.length() > 2 && key.charAt(0) == '"' && key.charAt(key.length()-1) == '"') {
				key = key.substring(1, key.length()-1);
				return db.getAllDocs(key).get();
			}
		}

		String keys = getFirstString(queryParams, "keys");
		if(keys != null) {
			keys = urlDecode(keys);

			return db.getAllDocs(asStrings(new JSONArray(keys))).get();
		}

		return db.getAllDocs().get();
	}

	private JSONObject _all_docs_POST(Map<String, List<String>> queryParams, JSONObject requestBody) throws JSONException {
		JSONArray keys = requestBody.getJSONArray("keys");
		return db.getAllDocs(asStrings(keys)).get();
	}

	private JSONObject _changes_GET(Map<String, List<String>> queryParams) throws JSONException {
		CouchChangesFeed changes;

		if(queryParams.containsKey("since") || queryParams.containsKey("limit")) {
			return db.getChanges(getFirstInt(queryParams, "since"),
					getFirstInt(queryParams, "limit")).get();
		}

		return db.getAllChanges().get();
	}

	private JSONObject _local_GET(String requestPath) throws DocNotFoundException, JSONException {
		String id = requestPath.substring(1);
		JSONObject doc = db.get_local(id);
		if(doc == null) throw new DocNotFoundException(id);
		return doc;
	}

	private FcResponse _local_PUT(String requestPath, JSONObject doc) throws IllegalDocException, JSONException {
		String docId = requestPath.substring(1);

		String internalDocId = docId.split("/")[1];

		String docRev = "1-" + Math.abs(new Random().nextInt());

		doc.put("_id", docId);
		doc.put("_rev", docRev);
		db.store_local(doc);

		return FcResponse.of(JSON.obj(
				"ok", true,
				"id", docId,
				"rev", docRev));
	}

	private FcResponse _revs_diff(JSONObject requestBody) throws JSONException {
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
		return FcResponse.of(response);
	}

	private FcResponse _bulk_docs(String requestPath, Map<String, List<String>> queryParams, JSONObject requestBody) throws CouchReplicationTargetException {
		try {
			JSONArray docs = requestBody.optJSONArray("docs");

			if(docs == null) throw new EmptyResponseException();

			JSONArray saved = new JSONArray();
			for(int i=0; i<docs.length(); ++i) {
				JSONObject doc = docs.getJSONObject(i);

				if(doc.optBoolean("_deleted", false)) storeDeleted(doc);
				else saveDoc(doc);

				saved.put(JSON.obj("ok", true,
						"id", doc.getString("_id"),
						"rev", doc.getString("_rev")));
			}

			return FcResponse.of(saved);
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

	private FcResponse getDoc(String requestPath, Map<String, List<String>> queryParams) throws DocNotFoundException, JSONException {
		String id = requestPath.substring(1);
		JSONObject doc = db.get(id);
		if(doc == null) throw new DocNotFoundException(id);

		if(getFirstString(queryParams, "open_revs") != null) {
			JSONArray array = new JSONArray();
			array.put(JSON.obj("ok", doc));
			for(JSONObject deletedRev : db.getDeletedRevs(id)) {
				array.put(JSON.obj("ok", deletedRev));
			}
			return FcResponse.of(array);
		}

		return FcResponse.of(doc);
	}

	private FcResponse getAttachment(String requestPath) throws CouchReplicationTargetException, JSONException {
		String[] pathParts = requestPath.substring(1).split("/", 2);
		if(pathParts.length != 2 || pathParts[0].length() == 00 || pathParts[1].length() == 1)
			throw new CouchReplicationTargetException("Bad doc or attachment path: " + requestPath);

		String id = pathParts[0];
		JSONObject doc = db.get(id);
		if(doc == null) throw new DocNotFoundException(id);

		String attachmentName = pathParts[1];
		JSONObject attachments = doc.optJSONObject("_attachments");
		if(attachments == null) throw new AttachmentNotFoundException(requestPath);
		JSONObject attachment = attachments.getJSONObject(attachmentName);
		if(attachment == null) throw new AttachmentNotFoundException(requestPath);

		return FcResponse.of(attachment.getString("content_type"), attachment.getString("data"));
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

	private void storeDeleted(JSONObject doc) {
		try {
			db.storeDeleted(doc);
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

	private static Integer getFirstInt(Map<String, List<String>> queryParams, String key) {
		try {
			return parseInt(queryParams.get(key).get(0));
		} catch(Exception _) {
			return null;
		}
	}

	private static String getFirstString(Map<String, List<String>> queryParams, String key) {
		List<String> values = queryParams.get(key);
		if(values == null || values.size() == 0) return null;

		String value = values.get(0).trim();
		if(value.length() == 0) return null;

		return value;
	}

	private static String urlDecode(String encodedString) {
		try {
			return URLDecoder.decode(encodedString, "UTF-8");
		} catch(UnsupportedEncodingException ex) {
			// everyone supports UTF-8, surely?!
			throw new RuntimeException(ex);
		}
	}

	private static String[] asStrings(JSONArray json) throws JSONException {
		String[] strings = new String[json.length()];
		for(int i=0; i<strings.length; ++i) {
			strings[i] = json.getString(i);
		}
		return strings;
	}

	private void trace(String method, String message, Object... extras) {
		MedicLog.trace(this, method + "(): " + message, extras);
	}
}

class CouchReplicationTargetException extends Exception {
	CouchReplicationTargetException() {}
	CouchReplicationTargetException(String message, Object... args) { super(String.format(message, args)); }
}

class DocNotFoundException extends CouchReplicationTargetException {
	DocNotFoundException(String id) { super("_id='%s'", id); }
}

class AttachmentNotFoundException extends CouchReplicationTargetException {
	AttachmentNotFoundException(String requestPath) {
		super(requestPath);
	}
}

class EmptyResponseException extends CouchReplicationTargetException {}

class UnimplementedEndpointException extends CouchReplicationTargetException {}

class UnsupportedInternalPathException extends CouchReplicationTargetException {
	UnsupportedInternalPathException(String path) { super(path); }
}
