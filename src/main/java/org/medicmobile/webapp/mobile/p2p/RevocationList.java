package org.medicmobile.webapp.mobile.p2p;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Cached revocation list for P2P authentication.
 * Checked during auth to enforce G8 (device/user not revoked).
 *
 * Format from CONTRACT.md:
 * {
 *   "version": number,
 *   "revoked_devices": string[],
 *   "revoked_users": string[],
 *   "updated_at": ISO8601
 * }
 */
@SuppressWarnings("java:S6206") // Records require Java 16+; project targets Java 8
public final class RevocationList {

    private final Set<String> revokedDevices;
    private final Set<String> revokedUsers;
    private final int version;

    public RevocationList(int version, Set<String> revokedDevices, Set<String> revokedUsers) {
        this.version = version;
        this.revokedDevices = Collections.unmodifiableSet(new HashSet<>(revokedDevices));
        this.revokedUsers = Collections.unmodifiableSet(new HashSet<>(revokedUsers));
    }

    /**
     * Parse from the server's revocation-list response.
     */
    public static RevocationList fromJson(JSONObject json) throws JSONException {
        int ver = json.optInt("version", 0);
        Set<String> devices = parseStringSet(json.optJSONArray("revoked_devices"));
        Set<String> users = parseStringSet(json.optJSONArray("revoked_users"));
        return new RevocationList(ver, devices, users);
    }

    private static Set<String> parseStringSet(JSONArray array) throws JSONException {
        Set<String> result = new HashSet<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                result.add(array.getString(i));
            }
        }
        return result;
    }

    /** Returns an empty revocation list for first-time use. */
    public static RevocationList empty() {
        return new RevocationList(0, Collections.emptySet(), Collections.emptySet());
    }

    /** G8: Check if device is revoked. */
    public boolean isDeviceRevoked(String deviceId) {
        return deviceId != null && revokedDevices.contains(deviceId);
    }

    /** G8: Check if user is revoked. */
    public boolean isUserRevoked(String userId) {
        return userId != null && revokedUsers.contains(userId);
    }

    public int getVersion() {
        return version;
    }

    public Set<String> getRevokedDevices() {
        return revokedDevices;
    }

    public Set<String> getRevokedUsers() {
        return revokedUsers;
    }
}
