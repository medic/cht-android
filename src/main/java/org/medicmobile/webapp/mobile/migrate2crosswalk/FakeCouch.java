package org.medicmobile.webapp.mobile.migrate2crosswalk;

import android.content.Context;
import android.net.Uri;

import fi.iki.elonen.NanoHTTPD;
import static fi.iki.elonen.NanoHTTPD.Response.Status;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashMap;
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
import static fi.iki.elonen.NanoHTTPD.Response.Status.NOT_FOUND;
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
		trace("start", "Starting server...");
		//System.setProperty("http.keepAlive", "false");
		try {
			server = new FakeCouchDaemon(ctx, 8000, appHost);
			server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
			trace("start", "Server started.");
		} catch(Exception ex) {
			// TODO perhaps this should be more than a warning!
			MedicLog.warn(ex, "Failed to start server.");
		}
	}

	public void stop() {
		server.stop();
		server = null;
	}

	private void trace(String method, String message, String... extras) {
		MedicLog.trace(this, method + "(): " + message, extras);
	}
}

class FakeCouchDaemon extends NanoHTTPD {
	private static final String MIME_JSON = "application/json";
	private static final String ALLOWED_METHODS = "OPTIONS,GET,POST,PUT";

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

		Map<String, String> additionalHeaders = new HashMap<String, String>();

		Object responseBody;
		Status responseStatus;
		try {
			requestPath = getRequestPath(session);
			queryParams = getQueryParams(session);

			trace("serve", "Handling request to path: %s", requestPath);

			switch(session.getMethod()) {
				case OPTIONS: {
					responseBody = "";
					responseStatus = OK;
					// TODO in theory this should be responding differently
					// depending on the path.  This hould be good enough for
					// now, though.
					additionalHeaders.put("Allow", ALLOWED_METHODS);
				} break;
				case GET: {
					responseBody = couch.get(requestPath, queryParams);
					responseStatus = OK;
				} break;
				case POST: {
					JSONObject requestBody = getBody(session);
					responseBody = couch.post(requestPath, queryParams, requestBody);
					responseStatus = OK;
				} break;
				case PUT: {
					JSONObject requestBody = getBody(session);
					responseBody = couch.put(requestPath, queryParams, requestBody);
					responseStatus = OK;
				} break;
				default: {
					responseStatus = METHOD_NOT_ALLOWED;
					responseBody = error("unsupported_method",
							"Unsupported method: " + session.getMethod());
				}
			}
		} catch(DocNotFoundException ex) {
			responseStatus = NOT_FOUND;
			responseBody = error("not_found", "missing");
		} catch(Exception ex) {
			responseStatus = INTERNAL_ERROR;
			responseBody = error("error", "Exception when trying to handle request: %s", ex);
		}

		byte[] responseBodyBytes = responseBodyBytes(responseBody);
		Response response = newFixedLengthResponse(responseStatus, MIME_JSON,
				new ByteArrayInputStream(responseBodyBytes), responseBodyBytes.length);

		addStandardHeadersTo(response);
		addAdditionalHeaders(response, additionalHeaders);

		return response;
	}

	private String error(String error, String reason, Object... args) {
		try {
			return new JSONObject()
					.put("error", "error")
					.put("reason", String.format(reason, args))
					.toString();
		} catch(JSONException ex) {
			return "{ \"error\":\"error\", \"reason\":\"unknown\" }";
		}
	}

	private void addAdditionalHeaders(Response response, Map<String, String> additionalHeaders) {
		for(Map.Entry<String, String> header : additionalHeaders.entrySet()) {
			response.addHeader(header.getKey(), header.getValue());
		}
	}

	private void addStandardHeadersTo(Response response) {
		response.addHeader("Access-Control-Allow-Credentials", "true");
		response.addHeader("Access-Control-Allow-Headers", "Content-Type");
		response.addHeader("Access-Control-Allow-Methods", ALLOWED_METHODS);
		response.addHeader("Access-Control-Allow-Origin", appHost);
	}

	private byte[] responseBodyBytes(Object responseBody) {
		try {
			// TODO maybe we should be supporting other charsets
			return responseBody.toString().getBytes("UTF-8");
		} catch(Exception ex) {
			// Everyone supports UTF-8!
			throw new RuntimeException(ex);
		}
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

	/**
	 * @throws RuntimeException if {@code Content-Length} header is not set or not an integer
	 * @throws java.net.SocketTimeoutException if content is less than Content-Length
	 */
	private JSONObject getBody(IHTTPSession session) throws IOException, JSONException {
		trace("getBody", "ENTRY");

		InputStream inputStream = session.getInputStream();

		int contentLength = Integer.parseInt(session.getHeaders().get("content-length"));

		ByteArrayOutputStream buffer = readFully(inputStream, contentLength);

		trace("getBody", "Read complete.");

		String jsonString = buffer.toString("UTF-8"); // should probably get the encoding from request headers
		trace("getBody", "Buffer content: %s", jsonString);
		return new JSONObject(jsonString);

		// N.B. do NOT close InputStream here under any circumstances -
		// it will be managed by underlying NanoHTTPD.
	}

	private ByteArrayOutputStream readFully(InputStream inputStream, int contentLength) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int r, readCount = 0;
		while((readCount++ < contentLength) && ((r = inputStream.read()) != -1)) {
			buffer.write(r);
		}
		return buffer;
	}

	private JSONObject asJson(String jsonString) throws JSONException {
		return (JSONObject) new JSONTokener(jsonString).nextValue();
	}

	private void trace(String methodName, String message, Object... args) {
		MedicLog.trace(this, methodName + "(): " + message, args);
	}
}
