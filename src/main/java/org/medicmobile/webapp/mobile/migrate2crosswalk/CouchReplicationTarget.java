package org.medicmobile.webapp.mobile.migrate2crosswalk;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.util.Map;

import org.json.JSONObject;

import org.medicmobile.webapp.mobile.MedicLog;

import static java.util.Collections.emptyMap;

class CouchReplicationTarget {
	private static final Map<String, String> NO_QUERY_PARAMS = emptyMap();

//> General handler
	public WebResourceResponse handle(WebResourceRequest req) throws CouchReplicationTargetException {
		trace("handle() :: req=%s", req);

		switch(req.getMethod()) {
			case "GET":
				// TODO extract query params
				return jsonResponse(req, get(req));
			case "POST":
				// TODO extract query params
				return jsonResponse(req, post(req));
		}
		return null;
	}

//> convenience TODO rename this 'block'
	private WebResourceResponse jsonResponse(WebResourceRequest req, JSONObject responseBody) {
		// TODO implement
		return null;
	}

	private JSONObject get(WebResourceRequest req) throws CouchReplicationTargetException {
		return get(pathFrom(req), paramsFrom(req));
	}

	private JSONObject post(WebResourceRequest req) throws CouchReplicationTargetException {
		return post(pathFrom(req), paramsFrom(req), bodyFrom(req));
	}

	private String pathFrom(WebResourceRequest req) {
		// TODO implement
		return null;
	}

	private Map<String, String> paramsFrom(WebResourceRequest req) {
		// TODO implement
		return null;
	}

	private JSONObject bodyFrom(WebResourceRequest req) {
		// TODO implement
		return null;
	}

//> JSON handlers
	public JSONObject get(String requestPath) throws CouchReplicationTargetException {
		return get(requestPath, NO_QUERY_PARAMS);
	}

	public JSONObject get(String requestPath, Map<String, String> queryParams) throws CouchReplicationTargetException {
		if(matches(requestPath, "/_local")) {
			throw new UnimplementedEndpointException();
		} else if(matches(requestPath, "/_changes")) {
			return new JSONObject();
		}
		throw new RuntimeException("Not yet implemented.");
	}

	public JSONObject post(String requestPath, JSONObject requestBody) throws CouchReplicationTargetException {
		return post(requestPath, NO_QUERY_PARAMS, requestBody);
	}

	public JSONObject post(String requestPath, Map<String, String> queryParams, JSONObject requestBody) throws CouchReplicationTargetException {
		if(matches(requestPath, "/_local")) {
			throw new UnimplementedEndpointException();
		} else if(matches(requestPath, "/_bulk_docs")) {
			if(true) throw new RuntimeException("TODO handle request: " + requestBody);
			throw new EmptyResponseException();
		} else if(matches(requestPath,  "/_revs_diff")) {
			return new JSONObject();
		}
		throw new RuntimeException("Not yet implemented.");
	}

//> HELPERS
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
