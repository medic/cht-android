package org.medicmobile.webapp.mobile.p2p;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Orchestrates the full P2P authentication flow.
 * Verifies JWT + checks revocation + validates peer authorization.
 *
 * Guards: G6 (JWT signature), G7 (not expired), G8 (not revoked), G9 (peer allowed)
 */
public final class P2pAuthenticator {

    private final JwtVerifier jwtVerifier;
    private final RevocationList revocationList;

    public P2pAuthenticator(JwtVerifier jwtVerifier, RevocationList revocationList) {
        if (jwtVerifier == null) {
            throw new IllegalArgumentException("jwtVerifier must not be null");
        }
        if (revocationList == null) {
            throw new IllegalArgumentException("revocationList must not be null");
        }
        this.jwtVerifier = jwtVerifier;
        this.revocationList = revocationList;
    }

    /**
     * Full authentication check: verify JWT + check revocation + check permissions.
     *
     * @param jwt      The JWT token string
     * @param deviceId The device ID of the peer
     * @return AuthResult with success/failure details
     */
    public AuthResult authenticate(String jwt, String deviceId) {
        // G6 + G7: Verify JWT signature and expiry
        JSONObject payload;
        try {
            payload = jwtVerifier.verify(jwt);
        } catch (JwtVerifier.JwtVerificationException e) {
            return AuthResult.failure(e.getMessage());
        }

        // G8: Check device revocation
        if (revocationList.isDeviceRevoked(deviceId)) {
            return AuthResult.failure("device_revoked");
        }

        // G8: Check user revocation
        String userId = payload.optString("sub", null);
        if (revocationList.isUserRevoked(userId)) {
            return AuthResult.failure("user_revoked");
        }

        String role = payload.optString("role", null);

        String facilityId = extractFacilityId(payload);

        return AuthResult.success(payload, userId, role, facilityId);
    }

    private String extractFacilityId(JSONObject payload) {
        Object rawFacility = payload.opt("facility_id");
        if (rawFacility instanceof JSONArray) {
            return ((JSONArray) rawFacility).optString(0, null);
        }
        return rawFacility != null ? rawFacility.toString() : null;
    }

    /**
     * G9: Check if a specific peer is in the allowed_relay_peers list.
     *
     * @param tokenPayload The decoded JWT payload
     * @param peerId       The peer user ID or device ID to check
     * @return true if the peer is allowed
     */
    public boolean isPeerAllowed(JSONObject tokenPayload, String peerId) {
        if (tokenPayload == null || peerId == null) {
            return false;
        }

        JSONArray allowedPeers = tokenPayload.optJSONArray("allowed_relay_peers");
        // null or empty = no restrictions, allow all peers
        if (allowedPeers == null || allowedPeers.length() == 0) {
            return true;
        }

        for (int i = 0; i < allowedPeers.length(); i++) {
            if (peerId.equals(allowedPeers.optString(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Result of an authentication attempt.
     */
    public static final class AuthResult {
        private final boolean authenticated;
        private final String error;
        private final JSONObject tokenPayload;
        private final String userId;
        private final String role;
        private final String facilityId;

        private AuthResult(String error) {
            this.authenticated = false;
            this.error = error;
            this.tokenPayload = null;
            this.userId = null;
            this.role = null;
            this.facilityId = null;
        }

        private AuthResult(JSONObject payload, String userId, String role, String facilityId) {
            this.authenticated = true;
            this.error = null;
            this.tokenPayload = payload;
            this.userId = userId;
            this.role = role;
            this.facilityId = facilityId;
        }

        public static AuthResult success(JSONObject payload, String userId, String role, String facilityId) {
            return new AuthResult(payload, userId, role, facilityId);
        }

        public static AuthResult failure(String error) {
            return new AuthResult(error);
        }

        public boolean isAuthenticated() {
            return authenticated;
        }

        public String getError() {
            return error;
        }

        public JSONObject getTokenPayload() {
            return tokenPayload;
        }

        public String getUserId() {
            return userId;
        }

        public String getRole() {
            return role;
        }

        public String getFacilityId() {
            return facilityId;
        }
    }
}
