package org.medicmobile.webapp.mobile.p2p;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
	* P2P configuration parsed from the p2p_sync section of app_settings.
	*
	* Default values match .
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
			Collections.unmodifiableList(Collections.emptyList());
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

	private P2pConfig(Builder builder) {
		this.enabled = builder.enabled;
		this.wifiHotspotEnabled = builder.wifiHotspotEnabled;
		this.maxRelaySizeMb = builder.maxRelaySizeMb;
		this.maxDocSizeKb = builder.maxDocSizeKb;
		this.maxAttachmentSizeMb = builder.maxAttachmentSizeMb;
		this.tokenExpiryDays = builder.tokenExpiryDays;
		this.wifiHotspotIdleTimeoutSec = builder.wifiHotspotIdleTimeoutSec;
		this.allowedRoles = Collections.unmodifiableList(new ArrayList<>(builder.allowedRoles));
		this.auditLogging = builder.auditLogging;
	}

	static class Builder {
		boolean enabled = DEFAULT_ENABLED;
		boolean wifiHotspotEnabled = DEFAULT_WIFI_HOTSPOT_ENABLED;
		int maxRelaySizeMb = DEFAULT_MAX_RELAY_SIZE_MB;
		int maxDocSizeKb = DEFAULT_MAX_DOC_SIZE_KB;
		int maxAttachmentSizeMb = DEFAULT_MAX_ATTACHMENT_SIZE_MB;
		int tokenExpiryDays = DEFAULT_TOKEN_EXPIRY_DAYS;
		int wifiHotspotIdleTimeoutSec = DEFAULT_WIFI_HOTSPOT_IDLE_TIMEOUT_SEC;
		List<String> allowedRoles = new ArrayList<>(DEFAULT_ALLOWED_ROLES);
		boolean auditLogging = DEFAULT_AUDIT_LOGGING;

		P2pConfig build() {
			return new P2pConfig(this);
		}
	}

	/**
		* Parse P2P config from the "p2p_sync" section of app_settings JSON.
		* Missing fields fall back to defaults.
		*
		* Expected JSON structure:
		* {
		*   "enabled": true,
		*   "transports": { "wifi_hotspot": true },
		*   "max_relay_size_mb": 50,
		*   "max_doc_size_kb": 256,
		*   "max_attachment_size_mb": 5,
		*   "token_expiry_days": 30,
		*   "wifi_hotspot_idle_timeout_sec": 300,
		*   "host_roles": ["community_health_assistant"],
		*   "peer_roles": ["community_health_volunteer"],
		*   "audit_logging": true
		* }
		*/
	public static P2pConfig fromJson(JSONObject p2pSyncSection) {
		if (p2pSyncSection == null) {
			return defaults();
		}

		Builder builder = new Builder();
		builder.enabled = p2pSyncSection.optBoolean("enabled", DEFAULT_ENABLED);

		JSONObject transports = p2pSyncSection.optJSONObject("transports");
		if (transports != null) {
			builder.wifiHotspotEnabled = transports.optBoolean("wifi_hotspot", DEFAULT_WIFI_HOTSPOT_ENABLED);
		}

		builder.maxRelaySizeMb = p2pSyncSection.optInt("max_relay_size_mb", DEFAULT_MAX_RELAY_SIZE_MB);
		builder.maxDocSizeKb = p2pSyncSection.optInt("max_doc_size_kb", DEFAULT_MAX_DOC_SIZE_KB);
		builder.maxAttachmentSizeMb = p2pSyncSection.optInt("max_attachment_size_mb", DEFAULT_MAX_ATTACHMENT_SIZE_MB);
		builder.tokenExpiryDays = p2pSyncSection.optInt("token_expiry_days", DEFAULT_TOKEN_EXPIRY_DAYS);
		builder.wifiHotspotIdleTimeoutSec = p2pSyncSection.optInt("wifi_hotspot_idle_timeout_sec", DEFAULT_WIFI_HOTSPOT_IDLE_TIMEOUT_SEC);
		builder.auditLogging = p2pSyncSection.optBoolean("audit_logging", DEFAULT_AUDIT_LOGGING);
		builder.allowedRoles = parseRoles(p2pSyncSection.optJSONArray("allowed_roles"));

		return builder.build();
	}

	/**
		* Create a config with all default values.
		*/
	public static P2pConfig defaults() {
		return new Builder().build();
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
		List<String> roles = extractNonEmptyStrings(rolesArray);
		return roles.isEmpty() ? new ArrayList<>(DEFAULT_ALLOWED_ROLES) : roles;
	}

	private static List<String> extractNonEmptyStrings(JSONArray array) {
		List<String> result = new ArrayList<>(array.length());
		for (int i = 0; i < array.length(); i++) {
			String value = array.optString(i, null);
			if (value != null && !value.isEmpty()) {
				result.add(value);
			}
		}
		return result;
	}
}
