package org.medicmobile.webapp.mobile.migrate2crosswalk;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import fi.iki.elonen.NanoHTTPD;
import static fi.iki.elonen.NanoHTTPD.Response.Status;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
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

public class FakeCouch {
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

		FcResponse fcResponse;
		try {
			requestPath = getRequestPath(session);
			queryParams = getQueryParams(session);

			trace("serve", "Handling request to path: %s", requestPath);

			switch(session.getMethod()) {
				case OPTIONS: {
					fcResponse = FcResponse.of("");
					// TODO in theory this should be responding differently
					// depending on the path.  This hould be good enough for
					// now, though.
					additionalHeaders.put("Allow", ALLOWED_METHODS);
				} break;
				case GET: {
					fcResponse = couch.get(requestPath, queryParams);
				} break;
				case POST: {
					JSONObject requestBody = getBody(session);
					fcResponse = couch.post(requestPath, queryParams, requestBody);
				} break;
				case PUT: {
					JSONObject requestBody = getBody(session);
					fcResponse = couch.put(requestPath, queryParams, requestBody);
				} break;
				default: {
					fcResponse = FcResponse.error(METHOD_NOT_ALLOWED,
							"unsupported_method",
							"Unsupported method: " + session.getMethod());
				}
			}
		} catch(DocNotFoundException ex) {
			fcResponse = FcResponse.error(NOT_FOUND, "not_found", "missing");
		} catch(Exception ex) {
			fcResponse = FcResponse.error(INTERNAL_ERROR, "error", "Exception when trying to handle request: %s", ex);
		}

		byte[] responseBodyBytes = fcResponse.bodyAsBytes();
		Response response = newFixedLengthResponse(fcResponse.status, fcResponse.contentType,
				new ByteArrayInputStream(responseBodyBytes), responseBodyBytes.length);

		addStandardHeadersTo(response);
		addAdditionalHeaders(response, additionalHeaders);

		return response;
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

class FcResponse {
	private static final String MIME_TEXT = "text/plain";
	private static final String MIME_JSON = "application/json";

	final Status status;
	final String contentType;
	private final Object body;

	FcResponse(Status status, String contentType, Object body) {
		this.status = status;
		this.contentType = contentType;
		this.body = body;
	}

	static FcResponse of(String str) { return new FcResponse(OK, MIME_TEXT, str); }
	static FcResponse of(JSONObject obj) { return new FcResponse(OK, MIME_JSON, obj); }
	static FcResponse of(JSONArray arr) { return new FcResponse(OK, MIME_JSON, arr); }
	static FcResponse of(String contentType, String base64body) {
		byte[] binaryData = Base64.decode(base64body, Base64.DEFAULT);
		return new FcResponse(OK, contentType, binaryData);
	}

	static FcResponse error(Status status, String error, String reason, Object... args) {
		Object body;
		try {
			body = new JSONObject()
					.put("error", "error")
					.put("reason", String.format(reason, args))
					.toString();
		} catch(JSONException ex) {
			body = "{ \"error\":\"error\", \"reason\":\"unknown\" }";
		}
		return new FcResponse(status, FcResponse.MIME_JSON, body);
	}

	public JSONArray asArray() { return (JSONArray) body; }
	public JSONObject asObject() { return (JSONObject) body; }

	public byte[] bodyAsBytes() {
		if(body instanceof byte[]) return (byte[]) body;

		try {
			return body.toString().getBytes("UTF-8");
		} catch(UnsupportedEncodingException ex) {
			// surely everyone supports UTF-8?!
			throw new RuntimeException(ex);
		}
	}
}
