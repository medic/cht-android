package org.medicmobile.webapp.mobile.migrate2crosswalk;

import android.content.Context;
import android.net.Uri;

import fi.iki.elonen.NanoHTTPD;
import static fi.iki.elonen.NanoHTTPD.Response.Status;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.medicmobile.webapp.mobile.MedicLog;
import org.medicmobile.webapp.mobile.SettingsStore;

import static fi.iki.elonen.NanoHTTPD.Method.GET;
import static fi.iki.elonen.NanoHTTPD.Method.POST;
import static fi.iki.elonen.NanoHTTPD.Response.Status.INTERNAL_ERROR;
import static fi.iki.elonen.NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED;
import static fi.iki.elonen.NanoHTTPD.Response.Status.OK;

class FakeCouch {
	private final String appHost;

	private FakeCouchDaemon server;

	public FakeCouch(SettingsStore settings) {
		this.appHost = Uri.parse(settings.getAppUrl()).buildUpon()
				.path("")
				.clearQuery()
				.build()
				.toString();
	}

	public void start(Context ctx) {
		// TODO something with threads?
		// TODO worry about finding a free port?
		trace("Starting server...");
		try {
			server = new FakeCouchDaemon(ctx, 8000, appHost);
			server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
			trace("Server started.");
		} catch(Exception ex) {
			// TODO perhaps this should be more than a warning!
			MedicLog.warn(ex, "Failed to start server.");
		}
	}

	public void stop() {
		server.stop();
		server = null;
	}

	private void trace(String message, String... extras) {
		MedicLog.trace(this, message, extras);
	}
}

class FakeCouchDaemon extends NanoHTTPD {
	private final CouchReplicationTarget couch;
	private final String appHost;

	FakeCouchDaemon(Context ctx, int port, String appHost) {
		super(port);
		this.couch = new CouchReplicationTarget(ctx);
		this.appHost = appHost;
	}

	@Override public Response serve(IHTTPSession session) {
		String requestPath;
		Map<String, List<String>> queryParams;

		Object responseBody;
		Status responseStatus;
		try {
			requestPath = getRequestPath(session);
			queryParams = getQueryParams(session);

			switch(session.getMethod()) {
				case GET:
					responseBody = couch.get(requestPath, queryParams);
					responseStatus = OK;
					MedicLog.log("GET handled.  responseBody=%s", responseBody);
					break;
				case POST:
					JSONObject requestBody = getBody(session);
					MedicLog.log("POST body read.  requestBody=%s", requestBody);
					responseBody = couch.post(requestPath, queryParams, requestBody);
					MedicLog.log("POST handled.  responseBody=%s", responseBody);
					responseStatus = OK;
					break;
				default:
					responseBody = new JSONObject()
							.put("error", "unsupported_method")
							.put("reason", "Unsupported method: " + session.getMethod());
					responseStatus = METHOD_NOT_ALLOWED;
			}
		} catch(Exception ex) {
			responseStatus = INTERNAL_ERROR;
			try {
				responseBody = new JSONObject()
						.put("error", "error")
						.put("reason", "Exception when trying to handle request: " + ex);
			} catch(Exception _) {
				// TODO log this
				responseBody = "{ \"error\":\"error\", \"reason\":\"unknown\" }";
			}
		}

		// TODO link up to the CouchReplicationTarget
		Response response = newFixedLengthResponse(responseBody.toString());
		response.setStatus(responseStatus);
		response.addHeader("Access-Control-Allow-Origin", appHost);
		response.addHeader("Access-Control-Allow-Credentials", "true");
		return response;
	}

	private static String getRequestPath(IHTTPSession session) {
		String path = Uri.parse(session.getUri()).getPath();
		String[] pathParts = path.split("/", 3);

		if(pathParts.length != 3) throw new RuntimeException("Path too short: " + path);

		if(!"medic".equals(pathParts[1])) throw new RuntimeException("Unrecognised DB name in path: " + path + "; compared " + pathParts[1] + " to 'medic'");

		return "/" + pathParts[2];
	}

	private Map<String, List<String>> getQueryParams(IHTTPSession session) {
		return session.getParameters();
	}

	private JSONObject getBody(IHTTPSession session) throws IOException, JSONException {
		InputStream inputStream = session.getInputStream();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8); // TODO check what the default encoding for couch is, check if the encoding in use is sent in a request header, etc.
			StringBuilder bob = new StringBuilder();

			String line = null;
			while((line = reader.readLine()) != null) {
				bob.append(line + "\n");
			}
			String jsonString = bob.toString();
			return new JSONObject(jsonString);
		} finally {
			if(inputStream != null) try {
				inputStream.close();
			} catch(Exception ex) {
				// TODO log it!
			}
		}
	}

	private JSONObject asJson(String jsonString) throws JSONException {
		return (JSONObject) new JSONTokener(jsonString).nextValue();
	}
}
