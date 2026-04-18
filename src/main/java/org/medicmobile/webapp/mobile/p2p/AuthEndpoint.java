package org.medicmobile.webapp.mobile.p2p;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
	* POST /_p2p/auth — Verify CHW's JWT and establish a P2P session.
	*
	* Request body:
	*   { "p2p_token": "jwt...", "device_id": "uuid" }
	*
	* Success response (200):
	*   { "ok": true, "session_id": "uuid", "user_id": "chw_alice", "scope": {...} }
	*
	* Failure response (401):
	*   { "ok": false, "error": "token_expired | peer_not_allowed | token_invalid | device_revoked" }
	*
	* Validates JWT signature, expiry, revocation, and peer authorization
	*/
public final class AuthEndpoint {

	private static final String TAG = "AuthEndpoint";

	private final P2pAuthenticator authenticator;
	private final P2pConfig config;
	private final ScopeManifest supervisorScope;

	public AuthEndpoint(P2pAuthenticator authenticator, P2pConfig config,
						ScopeManifest supervisorScope) {
		this.authenticator = authenticator;
		this.config = config;
		this.supervisorScope = supervisorScope;
	}

	/**
		* Handle POST /_p2p/auth request.
		*
		* @param requestBody The raw JSON request body string
		* @return AuthResponse containing the HTTP status code and JSON body
		*/
	public AuthResponse handle(String requestBody) {
		try {
			return doHandle(requestBody);
		} catch (JSONException e) {
			Log.e(TAG, "Malformed auth request", e);
			return AuthResponse.error(400, "malformed_request");
		}
	}

	private AuthResponse doHandle(String requestBody) throws JSONException {
		AuthResponse validationError = validateAuthRequest(requestBody);
		if (validationError != null) {
			return validationError;
		}

		JSONObject body = new JSONObject(requestBody);
		String p2pToken = body.optString("p2p_token", null);
		String deviceId = body.optString("device_id", null);

		AuthResponse authCheckResult = verifyAuthAndPermissions(p2pToken, deviceId);
		if (authCheckResult != null) {
			return authCheckResult;
		}

		P2pAuthenticator.AuthResult authResult = authenticator.authenticate(p2pToken, deviceId);
		return buildSuccessResponse(authResult, deviceId);
	}

	private AuthResponse validateAuthRequest(String requestBody) throws JSONException {
		if (requestBody == null || requestBody.isEmpty()) {
			return AuthResponse.error(400, "empty_request");
		}
		JSONObject body = new JSONObject(requestBody);

		AuthResponse tokenCheck = requireField(body, "p2p_token", "missing_token");
		if (tokenCheck != null) {
			return tokenCheck;
		}

		return requireField(body, "device_id", "missing_device_id");
	}

	private AuthResponse requireField(JSONObject body, String fieldName, String errorCode) {
		String value = body.optString(fieldName, null);
		if (value == null || value.isEmpty()) {
			return AuthResponse.error(400, errorCode);
		}
		return null;
	}

	private AuthResponse verifyAuthAndPermissions(String p2pToken, String deviceId) {
		P2pAuthenticator.AuthResult authResult = authenticator.authenticate(p2pToken, deviceId);

		if (!authResult.isAuthenticated()) {
			Log.w(TAG, "Auth failed for device " + deviceId + ": " + authResult.getError());
			return AuthResponse.error(401, authResult.getError());
		}

		String userId = authResult.getUserId();
		if (!authenticator.isPeerAllowed(authResult.getTokenPayload(), deviceId)) {
			Log.w(TAG, "Peer not allowed: " + userId + " (device: " + deviceId + ")");
			return AuthResponse.error(401, "peer_not_allowed");
		}

		String role = authResult.getRole();
		if (!config.isRoleAllowed(role)) {
			Log.w(TAG, "Role not allowed for P2P: " + role);
			return AuthResponse.error(401, "role_not_allowed");
		}

		return null;
	}

	private AuthResponse buildSuccessResponse(P2pAuthenticator.AuthResult authResult,
												String deviceId) throws JSONException {
		String userId = authResult.getUserId();
		String role = authResult.getRole();
		ScopeManifest peerScope = buildPeerScope(authResult);

		P2pSession session = new P2pSession(deviceId, userId, role, peerScope);
		session.setState(P2pSession.State.ACTIVE);

		Log.i(TAG, "Auth successful: user=" + userId + " session=" + session.getSessionId());

		JSONObject responseBody = new JSONObject();
		responseBody.put("ok", true);
		responseBody.put("session_id", session.getSessionId());
		responseBody.put("user_id", userId);
		responseBody.put("facility_id", authResult.getFacilityId());

		JSONObject scopeJson = new JSONObject();
		scopeJson.put("facility_subtree_root", supervisorScope.getFacilitySubtreeRoot());
		scopeJson.put("replication_depth", supervisorScope.getReplicationDepth());
		responseBody.put("scope", scopeJson);

		return AuthResponse.success(responseBody, session);
	}

	/**
		* Build the CHW peer's scope manifest from their JWT payload.
		*/
	private ScopeManifest buildPeerScope(P2pAuthenticator.AuthResult authResult) {
		JSONObject payload = authResult.getTokenPayload();

		String facilityId = authResult.getFacilityId();
		int replicationDepth = payload.optInt("replication_depth", 2);

		// Use supervisor's shared doc types as baseline
		return new ScopeManifest(
				facilityId,
				replicationDepth,
				supervisorScope.getSharedDocTypes(),
				supervisorScope.getScopeVersion()
		);
	}

	/**
		* Response from the auth endpoint, carrying HTTP status + JSON body + optional session.
		*/
	public static final class AuthResponse {
		private final int statusCode;
		private final JSONObject body;
		private final P2pSession session; // non-null only on success

		private AuthResponse(int statusCode, JSONObject body, P2pSession session) {
			this.statusCode = statusCode;
			this.body = body;
			this.session = session;
		}

		static AuthResponse success(JSONObject body, P2pSession session) {
			return new AuthResponse(200, body, session);
		}

		static AuthResponse error(int statusCode, String errorCode) {
			try {
				JSONObject body = new JSONObject();
				body.put("ok", false);
				body.put("error", errorCode);
				return new AuthResponse(statusCode, body, null);
			} catch (JSONException e) {
				// Should never happen with simple string puts
				throw new IllegalStateException("Failed to build error response", e);
			}
		}

		public int getStatusCode() {
			return statusCode;
		}

		public JSONObject getBody() {
			return body;
		}

		public P2pSession getSession() {
			return session;
		}

		public boolean isSuccess() {
			return session != null;
		}
	}
}
