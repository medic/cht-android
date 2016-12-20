package org.medicmobile.webapp.mobile.webviewmigrate;

import org.json.JSONObject;
import java.util.Map;

import static java.util.Collections.emptyMap;

class CouchReplicationTarget {
	private static final Map<String, String> NO_QUERY_PARAMS = emptyMap();

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
		if(matches(requestPath, "/_local")) {
			throw new UnimplementedEndpointException();
		} else if(matches(requestPath, "/_bulk_docs")) {
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
}

class CouchReplicationTargetException extends Exception {}

class EmptyResponseException extends CouchReplicationTargetException {}

class UnimplementedEndpointException extends CouchReplicationTargetException {}
