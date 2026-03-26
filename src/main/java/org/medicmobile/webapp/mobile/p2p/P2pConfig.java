package org.medicmobile.webapp.mobile.p2p;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * P2P configuration parsed from the p2p_sync section of app_settings.
 *
 * Default values match CONTRACT.md Section 4.
 * All size getters provide both human-readable (MB/KB) and byte values.
 */
public class P2pConfig {

    private static final boolean DEFAULT_ENABLED = true;
    private static final boolean DEFAULT_WIFI_HOTSPOT_ENABLED = true;
    private static final int DEFAULT_MAX_RELAY_SIZE_MB = 50;
    private static final int DEFAULT_MAX_DOC_SIZE_KB = 256;
    private static final int DEFAULT_MAX_ATTACHMENT_SIZE_MB = 5;
    private static final int DEFAULT_TOKEN_EXPIRY_DAYS = 30;
    private static final int DEFAULT_WIFI_HOTSPOT_IDLE_TIMEOUT_SEC = 300;
    private static final List<String> DEFAULT_ALLOWED_ROLES =
            Collections.unmodifiableList(Arrays.asList("chw", "chw_supervisor"));
    private static final boolean DEFAULT_AUDIT_LOGGING = true;

    private final boolean enabled;
    private final boolean wifiHotspotEnabled;
    private final int maxRelaySizeMb;
    private final int maxDocSizeKb;
    private final int maxAttachmentSizeMb;
    private final int tokenExpiryDays;
    private final int wifiHotspotIdleTimeoutSec;
    private final List<String> allowedRoles;
    private final boolean auditLogging;

    private P2pConfig(boolean enabled, boolean wifiHotspotEnabled, int maxRelaySizeMb,
                      int maxDocSizeKb, int maxAttachmentSizeMb, int tokenExpiryDays,
                      int wifiHotspotIdleTimeoutSec, List<String> allowedRoles,
                      boolean auditLogging) {
        this.enabled = enabled;
        this.wifiHotspotEnabled = wifiHotspotEnabled;
        this.maxRelaySizeMb = maxRelaySizeMb;
        this.maxDocSizeKb = maxDocSizeKb;
        this.maxAttachmentSizeMb = maxAttachmentSizeMb;
        this.tokenExpiryDays = tokenExpiryDays;
        this.wifiHotspotIdleTimeoutSec = wifiHotspotIdleTimeoutSec;
        this.allowedRoles = Collections.unmodifiableList(new ArrayList<>(allowedRoles));
        this.auditLogging = auditLogging;
    }

    /**
     * Parse P2P config from the "p2p_sync" section of app_settings JSON.
     * Missing fields fall back to defaults.
     *
     * Expected JSON structure (CONTRACT.md Section 4):
     * {
     *   "enabled": true,
     *   "transports": { "wifi_hotspot": true },
     *   "max_relay_size_mb": 50,
     *   "max_doc_size_kb": 256,
     *   "max_attachment_size_mb": 5,
     *   "token_expiry_days": 30,
     *   "wifi_hotspot_idle_timeout_sec": 300,
     *   "allowed_roles": ["chw", "chw_supervisor"],
     *   "audit_logging": true
     * }
     */
    public static P2pConfig fromJson(JSONObject p2pSyncSection) {
        if (p2pSyncSection == null) {
            return defaults();
        }

        boolean enabled = p2pSyncSection.optBoolean("enabled", DEFAULT_ENABLED);

        boolean wifiHotspot = DEFAULT_WIFI_HOTSPOT_ENABLED;
        JSONObject transports = p2pSyncSection.optJSONObject("transports");
        if (transports != null) {
            wifiHotspot = transports.optBoolean("wifi_hotspot", DEFAULT_WIFI_HOTSPOT_ENABLED);
        }

        int maxRelaySizeMb = p2pSyncSection.optInt("max_relay_size_mb", DEFAULT_MAX_RELAY_SIZE_MB);
        int maxDocSizeKb = p2pSyncSection.optInt("max_doc_size_kb", DEFAULT_MAX_DOC_SIZE_KB);
        int maxAttachmentSizeMb = p2pSyncSection.optInt("max_attachment_size_mb", DEFAULT_MAX_ATTACHMENT_SIZE_MB);
        int tokenExpiryDays = p2pSyncSection.optInt("token_expiry_days", DEFAULT_TOKEN_EXPIRY_DAYS);
        int idleTimeoutSec = p2pSyncSection.optInt("wifi_hotspot_idle_timeout_sec", DEFAULT_WIFI_HOTSPOT_IDLE_TIMEOUT_SEC);
        boolean auditLogging = p2pSyncSection.optBoolean("audit_logging", DEFAULT_AUDIT_LOGGING);

        List<String> allowedRoles = parseRoles(p2pSyncSection.optJSONArray("allowed_roles"));

        return new P2pConfig(enabled, wifiHotspot, maxRelaySizeMb, maxDocSizeKb,
                maxAttachmentSizeMb, tokenExpiryDays, idleTimeoutSec, allowedRoles, auditLogging);
    }

    /**
     * Create a config with all default values (CONTRACT.md Section 4).
     */
    public static P2pConfig defaults() {
        return new P2pConfig(
                DEFAULT_ENABLED,
                DEFAULT_WIFI_HOTSPOT_ENABLED,
                DEFAULT_MAX_RELAY_SIZE_MB,
                DEFAULT_MAX_DOC_SIZE_KB,
                DEFAULT_MAX_ATTACHMENT_SIZE_MB,
                DEFAULT_TOKEN_EXPIRY_DAYS,
                DEFAULT_WIFI_HOTSPOT_IDLE_TIMEOUT_SEC,
                DEFAULT_ALLOWED_ROLES,
                DEFAULT_AUDIT_LOGGING
        );
    }

    // --- Getters ---

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isWifiHotspotEnabled() {
        return wifiHotspotEnabled;
    }

    public int getMaxRelaySizeMb() {
        return maxRelaySizeMb;
    }

    public int getMaxDocSizeKb() {
        return maxDocSizeKb;
    }

    public int getMaxAttachmentSizeMb() {
        return maxAttachmentSizeMb;
    }

    public int getTokenExpiryDays() {
        return tokenExpiryDays;
    }

    public int getWifiHotspotIdleTimeoutSec() {
        return wifiHotspotIdleTimeoutSec;
    }

    public List<String> getAllowedRoles() {
        return allowedRoles;
    }

    public boolean isAuditLogging() {
        return auditLogging;
    }

    // --- Validation ---

    /**
     * Check if a given role is allowed to participate in P2P sync.
     */
    public boolean isRoleAllowed(String role) {
        if (role == null || role.isEmpty()) {
            return false;
        }
        return allowedRoles.contains(role);
    }

    // --- Size limit helpers (return bytes) ---

    public long getMaxDocSizeBytes() {
        return (long) maxDocSizeKb * 1024;
    }

    public long getMaxAttachmentSizeBytes() {
        return (long) maxAttachmentSizeMb * 1024 * 1024;
    }

    public long getMaxRelaySizeBytes() {
        return (long) maxRelaySizeMb * 1024 * 1024;
    }

    // --- Serialization ---

    /**
     * Convert config back to JSON (useful for caching in _local/p2p-config-cache).
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("enabled", enabled);

        JSONObject transports = new JSONObject();
        transports.put("wifi_hotspot", wifiHotspotEnabled);
        json.put("transports", transports);

        json.put("max_relay_size_mb", maxRelaySizeMb);
        json.put("max_doc_size_kb", maxDocSizeKb);
        json.put("max_attachment_size_mb", maxAttachmentSizeMb);
        json.put("token_expiry_days", tokenExpiryDays);
        json.put("wifi_hotspot_idle_timeout_sec", wifiHotspotIdleTimeoutSec);

        JSONArray rolesArray = new JSONArray();
        for (String role : allowedRoles) {
            rolesArray.put(role);
        }
        json.put("allowed_roles", rolesArray);

        json.put("audit_logging", auditLogging);
        return json;
    }

    // --- Private helpers ---

    private static List<String> parseRoles(JSONArray rolesArray) {
        if (rolesArray == null || rolesArray.length() == 0) {
            return new ArrayList<>(DEFAULT_ALLOWED_ROLES);
        }
        List<String> roles = new ArrayList<>(rolesArray.length());
        for (int i = 0; i < rolesArray.length(); i++) {
            String role = rolesArray.optString(i, null);
            if (role != null && !role.isEmpty()) {
                roles.add(role);
            }
        }
        return roles.isEmpty() ? new ArrayList<>(DEFAULT_ALLOWED_ROLES) : roles;
    }
}
